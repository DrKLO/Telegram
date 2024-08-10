package org.telegram.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LruCache;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StatisticPostInfoCell;
import org.telegram.ui.Charts.BarChartView;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Charts.DoubleLinearChartView;
import org.telegram.ui.Charts.LinearBarChartView;
import org.telegram.ui.Charts.LinearChartView;
import org.telegram.ui.Charts.PieChartView;
import org.telegram.ui.Charts.StackBarChartView;
import org.telegram.ui.Charts.StackLinearChartView;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.DoubleLinearChartData;
import org.telegram.ui.Charts.data.StackBarChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.ChartHeaderView;
import org.telegram.ui.Charts.view_data.LineViewData;
import org.telegram.ui.Charts.view_data.TransitionParams;
import org.telegram.ui.Components.BottomPagerTabs;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlatCheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StatisticActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static BaseFragment create(TLRPC.Chat chat) {
        return create(chat, true);
    }

    public static BaseFragment create(TLRPC.Chat chat, boolean startFromBoosts) {
        Bundle args = new Bundle();
        args.putLong("chat_id", chat.id);
        args.putBoolean("is_megagroup", chat.megagroup);
        args.putBoolean("start_from_boosts", startFromBoosts);
        TLRPC.ChatFull chatInfo = MessagesController.getInstance(UserConfig.selectedAccount).getChatFull(chat.id);
        if (chatInfo == null || !chatInfo.can_view_stats) {
            return new BoostsActivity(-chat.id);
        }
        return new StatisticActivity(args);
    }

    private TLRPC.ChatFull chat;
    private final long chatId;

    //mutual
    private ChartViewData growthData;
    private ChartViewData topHoursData;

    //channels
    private OverviewChannelData overviewChannelData;
    private ChartViewData followersData;
    private ChartViewData interactionsData;
    private ChartViewData ivInteractionsData;
    private ChartViewData viewsBySourceData;
    private ChartViewData newFollowersBySourceData;
    private ChartViewData languagesData;
    private ChartViewData notificationsData;
    private ChartViewData reactionsByEmotionData;
    private ChartViewData storyInteractionsData;
    private ChartViewData storyReactionsByEmotionData;

    //chats
    private OverviewChatData overviewChatData;
    private ChartViewData groupMembersData;
    private ChartViewData newMembersBySourceData;
    private ChartViewData membersLanguageData;
    private ChartViewData messagesData;
    private ChartViewData actionsData;
    private ChartViewData topDayOfWeeksData;
    private ArrayList<MemberData> topMembersAll = new ArrayList<>();
    private ArrayList<MemberData> topMembersVisible = new ArrayList<>();
    private ArrayList<MemberData> topInviters = new ArrayList<>();
    private ArrayList<MemberData> topAdmins = new ArrayList<>();

    ChatAvatarContainer avatarContainer;

    private RecyclerListView recyclerListView;
    private LinearLayoutManager layoutManager;
    private LruCache<ChartData> childDataCache = new LruCache<>(50);
    private RLottieImageView imageView;

    private Adapter adapter;
    private RecyclerView.ItemAnimator animator;
    private ZoomCancelable lastCancelable;

    private BaseChartView.SharedUiComponents sharedUi;
    private LinearLayout progressLayout;
    private final boolean isMegagroup;
    private boolean startFromBoosts;
    private long maxDateOverview;
    private long minDateOverview;

    private AlertDialog[] progressDialog = new AlertDialog[1];
    private ViewPagerFixed viewPagerFixed;
    private ChannelBoostLayout boostLayout;
    private ChannelMonetizationLayout monetizationLayout;
    private boolean onlyBoostsStat;

    public StatisticActivity(Bundle args) {
        super(args);
        chatId = args.getLong("chat_id");
        isMegagroup = args.getBoolean("is_megagroup", false);
        startFromBoosts = args.getBoolean("start_from_boosts", false);
        onlyBoostsStat = args.getBoolean("only_boosts", false);
        this.chat = getMessagesController().getChatFull(chatId);
    }


    private int loadFromId = -1;
    private final SparseIntArray recentPostIdtoIndexMap = new SparseIntArray();
    private final SparseIntArray recentStoriesIdtoIndexMap = new SparseIntArray();
    private final ArrayList<RecentPostInfo> recentPostsAll = new ArrayList<>();
    private final ArrayList<RecentPostInfo> recentPostsLoaded = new ArrayList<>();
    private final ArrayList<RecentPostInfo> recentStoriesAll = new ArrayList<>();
    private final ArrayList<RecentPostInfo> recentStoriesLoaded = new ArrayList<>();
    private final ArrayList<RecentPostInfo> recentAllSortedDataLoaded = new ArrayList<>();
    private boolean messagesIsLoading;
    private boolean initialLoading = true;
    private DiffUtilsCallback diffUtilsCallback;
    private StoriesController.StoriesList storiesList;
    private int storiesListId;

    private final Runnable showProgressbar = new Runnable() {
        @Override
        public void run() {
            progressLayout.animate().alpha(1f).setDuration(230);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().addObserver(this, NotificationCenter.storiesListUpdated);
        StoriesController storiesController = getMessagesController().getStoriesController();
        storiesList = storiesController.getStoriesList(-chatId, StoriesController.StoriesList.TYPE_STATISTICS);
        if (storiesList != null) {
            storiesListId = storiesList.link();
        }
        if (chat != null) {
            loadStatistic();
        } else {
            MessagesController.getInstance(currentAccount).loadFullChat(chatId, classGuid, true);
        }
        return super.onFragmentCreate();
    }

    private void sortAllLoadedData() {
        recentAllSortedDataLoaded.clear();
        recentAllSortedDataLoaded.addAll(recentPostsLoaded);
        recentAllSortedDataLoaded.addAll(recentStoriesLoaded);
        Collections.sort(recentAllSortedDataLoaded, Collections.reverseOrder(Comparator.comparingLong(RecentPostInfo::getDate)));
    }

    private void loadStatistic() {
        if (onlyBoostsStat) {
            return;
        }
        TLObject req;
        if (isMegagroup) {
            TL_stats.TL_getMegagroupStats getMegagroupStats = new TL_stats.TL_getMegagroupStats();
            req = getMegagroupStats;
            getMegagroupStats.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        } else {
            TL_stats.TL_getBroadcastStats getBroadcastStats = new TL_stats.TL_getBroadcastStats();
            req = getBroadcastStats;
            getBroadcastStats.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        }

        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TL_stats.TL_broadcastStats) {
                final ChartViewData[] chartsViewData = new ChartViewData[12];
                TL_stats.TL_broadcastStats stats = (TL_stats.TL_broadcastStats) response;

                chartsViewData[0] = createViewData(stats.iv_interactions_graph, getString("IVInteractionsChartTitle", R.string.IVInteractionsChartTitle), 1);
                chartsViewData[1] = createViewData(stats.followers_graph, getString("FollowersChartTitle", R.string.FollowersChartTitle), 0);
                chartsViewData[2] = createViewData(stats.top_hours_graph, getString("TopHoursChartTitle", R.string.TopHoursChartTitle), 0);
                chartsViewData[3] = createViewData(stats.interactions_graph, getString("ViewsAndSharesChartTitle", R.string.ViewsAndSharesChartTitle), 1);
                chartsViewData[4] = createViewData(stats.growth_graph, getString("GrowthChartTitle", R.string.GrowthChartTitle), 0);
                chartsViewData[5] = createViewData(stats.views_by_source_graph, getString("ViewsBySourceChartTitle", R.string.ViewsBySourceChartTitle), 2);
                chartsViewData[6] = createViewData(stats.new_followers_by_source_graph, getString("NewFollowersBySourceChartTitle", R.string.NewFollowersBySourceChartTitle), 2);
                chartsViewData[7] = createViewData(stats.languages_graph, getString("LanguagesChartTitle", R.string.LanguagesChartTitle), 4, true);
                chartsViewData[8] = createViewData(stats.mute_graph, getString("NotificationsChartTitle", R.string.NotificationsChartTitle), 0);
                chartsViewData[9] = createViewData(stats.reactions_by_emotion_graph, getString("ReactionsByEmotionChartTitle", R.string.ReactionsByEmotionChartTitle), 2);
                chartsViewData[10] = createViewData(stats.story_interactions_graph, getString("StoryInteractionsChartTitle", R.string.StoryInteractionsChartTitle), 1);
                chartsViewData[11] = createViewData(stats.story_reactions_by_emotion_graph, getString("StoryReactionsByEmotionChartTitle", R.string.StoryReactionsByEmotionChartTitle), 2);

                if (chartsViewData[2] != null) {
                    chartsViewData[2].useHourFormat = true;
                }

                overviewChannelData = new OverviewChannelData(stats);
                maxDateOverview = stats.period.max_date * 1000L;
                minDateOverview = stats.period.min_date * 1000L;

                recentPostsAll.clear();

                int msgPos = 0;
                int storiesPos = 0;
                List<Integer> storiesIds = new ArrayList<>();
                for (TL_stats.PostInteractionCounters interactionCounters : stats.recent_posts_interactions) {
                    RecentPostInfo recentPostInfo = new RecentPostInfo();
                    recentPostInfo.counters = interactionCounters;

                    if (interactionCounters instanceof TL_stats.TL_postInteractionCountersMessage) {
                        recentPostsAll.add(recentPostInfo);
                        recentPostIdtoIndexMap.put(recentPostInfo.getId(), msgPos);
                        msgPos++;
                    }
                    if (interactionCounters instanceof TL_stats.TL_postInteractionCountersStory) {
                        storiesIds.add(recentPostInfo.getId());
                        recentStoriesAll.add(recentPostInfo);
                        recentStoriesIdtoIndexMap.put(recentPostInfo.getId(), storiesPos);
                        storiesPos++;
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    if (!storiesList.load(storiesIds)) {
                        prepareStoriesLoadedItems();
                        sortAllLoadedData();
                    }
                });

                if (recentPostsAll.size() > 0) {
                    int lastPostId = recentPostsAll.get(0).getId();
                    int count = recentPostsAll.size();
                    getMessagesStorage().getMessages(-chatId, 0, false, count, lastPostId, 0, 0, classGuid, 0, 0, 0, 0, true, false, null);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    ivInteractionsData = chartsViewData[0];
                    followersData = chartsViewData[1];
                    topHoursData = chartsViewData[2];
                    interactionsData = chartsViewData[3];
                    growthData = chartsViewData[4];

                    viewsBySourceData = chartsViewData[5];
                    newFollowersBySourceData = chartsViewData[6];
                    languagesData = chartsViewData[7];
                    notificationsData = chartsViewData[8];

                    reactionsByEmotionData = chartsViewData[9];
                    storyInteractionsData = chartsViewData[10];
                    storyReactionsByEmotionData = chartsViewData[11];

                    dataLoaded(chartsViewData);
                });

            }

            if (response instanceof TL_stats.TL_megagroupStats) {
                final ChartViewData[] chartsViewData = new ChartViewData[8];
                TL_stats.TL_megagroupStats stats = (TL_stats.TL_megagroupStats) response;

                chartsViewData[0] = createViewData(stats.growth_graph, getString("GrowthChartTitle", R.string.GrowthChartTitle), 0);
                chartsViewData[1] = createViewData(stats.members_graph, getString("GroupMembersChartTitle", R.string.GroupMembersChartTitle), 0);
                chartsViewData[2] = createViewData(stats.new_members_by_source_graph, getString("NewMembersBySourceChartTitle", R.string.NewMembersBySourceChartTitle), 2);
                chartsViewData[3] = createViewData(stats.languages_graph, getString("MembersLanguageChartTitle", R.string.MembersLanguageChartTitle), 4, true);
                chartsViewData[4] = createViewData(stats.messages_graph, getString("MessagesChartTitle", R.string.MessagesChartTitle), 2);
                chartsViewData[5] = createViewData(stats.actions_graph, getString("ActionsChartTitle", R.string.ActionsChartTitle), 1);
                chartsViewData[6] = createViewData(stats.top_hours_graph, getString("TopHoursChartTitle", R.string.TopHoursChartTitle), 0);
                chartsViewData[7] = createViewData(stats.weekdays_graph, getString("TopDaysOfWeekChartTitle", R.string.TopDaysOfWeekChartTitle), 4);

                if (chartsViewData[6] != null) {
                    chartsViewData[6].useHourFormat = true;
                }
                if (chartsViewData[7] != null) {
                    chartsViewData[7].useWeekFormat = true;
                }

                overviewChatData = new OverviewChatData(stats);
                maxDateOverview = stats.period.max_date * 1000L;
                minDateOverview = stats.period.min_date * 1000L;

                if (stats.top_posters != null && !stats.top_posters.isEmpty()) {
                    for (int i = 0; i < stats.top_posters.size(); i++) {
                        MemberData data = MemberData.from(stats.top_posters.get(i), stats.users);
                        if (topMembersVisible.size() < 10) {
                            topMembersVisible.add(data);
                        }
                        topMembersAll.add(data);
                    }
                    if (topMembersAll.size() - topMembersVisible.size() < 2) {
                        topMembersVisible.clear();
                        topMembersVisible.addAll(topMembersAll);
                    }
                }

                if (stats.top_admins != null && !stats.top_admins.isEmpty()) {
                    for (int i = 0; i < stats.top_admins.size(); i++) {
                        topAdmins.add(MemberData.from(stats.top_admins.get(i), stats.users));
                    }
                }

                if (stats.top_inviters != null && !stats.top_inviters.isEmpty()) {
                    for (int i = 0; i < stats.top_inviters.size(); i++) {
                        topInviters.add(MemberData.from(stats.top_inviters.get(i), stats.users));
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    growthData = chartsViewData[0];
                    groupMembersData = chartsViewData[1];
                    newMembersBySourceData = chartsViewData[2];
                    membersLanguageData = chartsViewData[3];
                    messagesData = chartsViewData[4];
                    actionsData = chartsViewData[5];
                    topHoursData = chartsViewData[6];
                    topDayOfWeeksData = chartsViewData[7];

                    dataLoaded(chartsViewData);
                });
            }
        }, null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);

        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void dataLoaded(ChartViewData[] chartsViewData) {
        if (adapter != null) {
            adapter.update();
            recyclerListView.setItemAnimator(null);
            adapter.notifyDataSetChanged();
        }
        initialLoading = false;
        if (progressLayout != null && progressLayout.getVisibility() == View.VISIBLE) {
            AndroidUtilities.cancelRunOnUIThread(showProgressbar);
            progressLayout.animate().alpha(0).setDuration(230).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    progressLayout.setVisibility(View.GONE);
                }
            });
            recyclerListView.setVisibility(View.VISIBLE);
            recyclerListView.setAlpha(0);
            recyclerListView.animate().alpha(1).setDuration(230).start();

            for (ChartViewData data : chartsViewData) {
                if (data != null && data.chartData == null && data.token != null) {
                    data.load(currentAccount, classGuid, chat.stats_dc, getFindChartCell(data));
                }
            }
        }
    }

    private Utilities.Callback0Return<BaseChartCell> getFindChartCell(ChartViewData data) {
        return () -> {
            int n = recyclerListView.getChildCount();
            for (int i = 0; i < n; i++) {
                View child = recyclerListView.getChildAt(i);
                if (child instanceof ChartCell && ((ChartCell) child).data == data) {
                    return (ChartCell) child;
                }
            }
            recyclerListView.setItemAnimator(null);
            diffUtilsCallback.update();
            return null;
        };
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.boostByChannelCreated);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.storiesListUpdated);

        if (progressDialog[0] != null) {
            progressDialog[0].dismiss();
            progressDialog[0] = null;
        }
        if (storiesList != null) {
            storiesList.unlink(storiesListId);
        }
        super.onFragmentDestroy();
    }

    private void prepareStoriesLoadedItems() {
        recentStoriesLoaded.clear();
        for (RecentPostInfo recentPostInfo : recentStoriesAll) {
            MessageObject messageObject = storiesList.findMessageObject(recentPostInfo.getId());
            if (messageObject != null) {
                recentPostInfo.message = messageObject;
                recentStoriesLoaded.add(recentPostInfo);
            }
        }
        recentStoriesIdtoIndexMap.clear();
        recentStoriesAll.clear();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            StoriesController.StoriesList list = (StoriesController.StoriesList) args[0];
            if (list == storiesList) {
                prepareStoriesLoadedItems();
                sortAllLoadedData();
                if (adapter != null) {
                    recyclerListView.setItemAnimator(null);
                    diffUtilsCallback.update();
                }
            }
        } else if (id == NotificationCenter.boostByChannelCreated) {
            TLRPC.Chat chat = (TLRPC.Chat) args[0];
            boolean isGiveaway = (boolean) args[1];
            List<BaseFragment> fragmentStack = getParentLayout().getFragmentStack();
            BaseFragment chatEditFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
            if (chatEditFragment instanceof ChatEditActivity) {
                getParentLayout().removeFragmentFromStack(chatEditFragment);
            }
            fragmentStack = getParentLayout().getFragmentStack();
            BaseFragment profileFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
            if (isGiveaway) {
                BaseFragment chatFragment = fragmentStack.size() >= 3 ? fragmentStack.get(fragmentStack.size() - 3) : null;
                if (profileFragment instanceof ProfileActivity) {
                    getParentLayout().removeFragmentFromStack(profileFragment);
                }
                finishFragment();
                if (chatFragment instanceof ChatActivity) {
                    BoostDialogs.showBulletin(chatFragment, chat, true);
                }
            } else {
                finishFragment();
                if (profileFragment instanceof ProfileActivity) {
                    BoostDialogs.showBulletin(profileFragment, chat, false);
                }
            }
        } else if (id == NotificationCenter.messagesDidLoad) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                ArrayList<RecentPostInfo> deletedMessages = new ArrayList<>();
                int n = messArr.size();
                for (int i = 0; i < n; i++) {
                    MessageObject messageObjectFormCache = messArr.get(i);
                    int index = recentPostIdtoIndexMap.get(messageObjectFormCache.getId(), -1);
                    if (index >= 0 && recentPostsAll.get(index).getId() == messageObjectFormCache.getId()) {
                        if (messageObjectFormCache.deleted) {
                            deletedMessages.add(recentPostsAll.get(index));
                        } else {
                            recentPostsAll.get(index).message = messageObjectFormCache;
                        }
                    }
                }

                recentPostsAll.removeAll(deletedMessages);

                recentPostsLoaded.clear();
                n = recentPostsAll.size();
                for (int i = 0; i < n; i++) {
                    RecentPostInfo postInfo = recentPostsAll.get(i);
                    if (postInfo.message == null) {
                        loadFromId = postInfo.getId();
                        break;
                    } else {
                        recentPostsLoaded.add(postInfo);
                    }
                }

                if (recentPostsLoaded.size() < 20) {
                    loadMessages();
                }
                sortAllLoadedData();
                if (adapter != null) {
                    recyclerListView.setItemAnimator(null);
                    diffUtilsCallback.update();
                }
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (chat == null) {
                    chat = chatFull;
                    loadStatistic();
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        sharedUi = new BaseChartView.SharedUiComponents();
        TLRPC.Chat currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chatId);
        boolean isBoostSupported = ChatObject.isBoostSupported(currentChat);
        final boolean hasMonetization = ChatObject.isChannelAndNotMegaGroup(currentChat) && chatFull != null && chatFull.can_view_revenue;
        BottomPagerTabs storiesTabsView = new BottomPagerTabs(context, getResourceProvider()) {
            @Override
            public Tab[] createTabs() {
                ArrayList<Tab> tabs = new ArrayList<>();
                tabs.add(new Tab(0, R.raw.stats, 25, 49, getString(R.string.Statistics)).customFrameInvert());
                tabs.add(new Tab(1, R.raw.boosts, 25, 49, getString(R.string.Boosts)));
                if (hasMonetization) {
                    tabs.add(new Tab(2, R.raw.monetize, 19, 45, getString(R.string.Monetization)));
                }
                return tabs.toArray(new Tab[0]);
            }
        };

        viewPagerFixed = new ViewPagerFixed(getContext()) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                if (manual) {
                    return;
                }
                storiesTabsView.setScrolling(true);
                storiesTabsView.setProgress(viewPagerFixed.getPositionAnimated());
            }
        };

        storiesTabsView.setOnTabClick(position -> {
            if (viewPagerFixed.scrollToPosition(position)) {
                storiesTabsView.setScrolling(false);
                storiesTabsView.setProgress(position);
            }
        });
        FrameLayout statisticLayout = new FrameLayout(context);
        if (isBoostSupported) {
            boostLayout = new ChannelBoostLayout(StatisticActivity.this, -chatId, getResourceProvider());
        }
        if (hasMonetization) {
            monetizationLayout = new ChannelMonetizationLayout(getContext(), StatisticActivity.this, currentAccount, -chatId, getResourceProvider());
        }
        boolean showTabs = isBoostSupported && !onlyBoostsStat;
        if (showTabs && startFromBoosts) {
            viewPagerFixed.setPosition(1);
        }
        viewPagerFixed.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                int count = onlyBoostsStat ? 1 : 1 + (isBoostSupported ? 1 : 0);
                if (hasMonetization) {
                    count++;
                }
                return count;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == 0) {
                    return statisticLayout;
                }
                viewType--;
                if (viewType == 0) {
                    if (!onlyBoostsStat && isBoostSupported) {
                        return boostLayout;
                    } else {
                        return monetizationLayout;
                    }
                } else {
                    viewType--;
                }
                if (viewType == 0) {
                    return monetizationLayout;
                }
                return statisticLayout;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }
        });


        FrameLayout contentLayout = new SizeNotifierFrameLayout(getContext());
        contentLayout.addView(viewPagerFixed, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, showTabs ? 64 : 0));
        if (showTabs) {
            contentLayout.addView(storiesTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return dp(64);
                }
            });
        }
        new KeyboardNotifier(contentLayout, keyboardHeight -> {
            if (storiesTabsView != null) {
                storiesTabsView.setVisibility(keyboardHeight > dp(20) ? View.GONE : View.VISIBLE);
            }
        });
        fragmentView = contentLayout;
        recyclerListView = new RecyclerListView(context) {
            int lastH;

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                if (lastH != getMeasuredHeight() && adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                lastH = getMeasuredHeight();
            }
        };

        progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.VERTICAL);

        imageView = new RLottieImageView(context);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.statistic_preload, 120, 120);
        imageView.playAnimation();

        TextView loadingTitle = new TextView(context);
        loadingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        loadingTitle.setTypeface(AndroidUtilities.bold());
        loadingTitle.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        loadingTitle.setTag(Theme.key_player_actionBarTitle);
        loadingTitle.setText(getString("LoadingStats", R.string.LoadingStats));
        loadingTitle.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView loadingSubtitle = new TextView(context);
        loadingSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        loadingSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        loadingSubtitle.setTag(Theme.key_player_actionBarSubtitle);
        loadingSubtitle.setText(getString("LoadingStatsDescription", R.string.LoadingStatsDescription));
        loadingSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);

        progressLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        progressLayout.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        progressLayout.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        statisticLayout.addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));


        if (adapter == null) {
            adapter = new Adapter();
        }
        recyclerListView.setAdapter(adapter);
        layoutManager = new LinearLayoutManager(context);
        recyclerListView.setLayoutManager(layoutManager);
        animator = new DefaultItemAnimator() {
            @Override
            protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
                return removeDuration;
            }
        };
        recyclerListView.setItemAnimator(null);

        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recentPostsAll.size() != recentPostsLoaded.size()) {
                    if (!messagesIsLoading && layoutManager.findLastVisibleItemPosition() > adapter.getItemCount() - 20) {
                        loadMessages();
                    }
                }
            }
        });

        recyclerListView.setOnItemClickListener((view, position) -> {
            if (position >= adapter.recentPostsStartRow && position <= adapter.recentPostsEndRow) {
                RecentPostInfo recentPostInfo = recentAllSortedDataLoaded.get(position - adapter.recentPostsStartRow);
                MessageStatisticActivity activity = new MessageStatisticActivity(recentPostInfo, chatId, true);
                presentFragment(activity);
            } else if (position >= adapter.topAdminsStartRow && position <= adapter.topAdminsEndRow) {
                int i = position - adapter.topAdminsStartRow;
                topAdmins.get(i).onClick(this);
            } else if (position >= adapter.topMembersStartRow && position <= adapter.topMembersEndRow) {
                int i = position - adapter.topMembersStartRow;
                topMembersVisible.get(i).onClick(this);
            } else if (position >= adapter.topInviterStartRow && position <= adapter.topInviterEndRow) {
                int i = position - adapter.topInviterStartRow;
                topInviters.get(i).onClick(this);
            } else if (position == adapter.expandTopMembersRow) {
                int newCount = topMembersAll.size() - topMembersVisible.size();
                int p = adapter.expandTopMembersRow;
                topMembersVisible.clear();
                topMembersVisible.addAll(topMembersAll);
                if (adapter != null) {
                    adapter.update();
                    recyclerListView.setItemAnimator(animator);
                    adapter.notifyItemRangeInserted(p + 1, newCount);
                    adapter.notifyItemRemoved(p);
                }
            }
        });

        recyclerListView.setOnItemLongClickListener((view, position) -> {
            if (position >= adapter.recentPostsStartRow && position <= adapter.recentPostsEndRow) {
                MessageObject messageObject = recentAllSortedDataLoaded.get(position - adapter.recentPostsStartRow).message;

                if (messageObject.isStory()) {
                    return false;
                }

                final ArrayList<String> items = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();
                final ArrayList<Integer> icons = new ArrayList<>();

                items.add(getString("ViewMessageStatistic", R.string.ViewMessageStatistic));
                actions.add(0);
                icons.add(R.drawable.msg_stats);

                items.add(getString("ViewMessage", R.string.ViewMessage));
                actions.add(1);
                icons.add(R.drawable.msg_msgbubble3);


                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setItems(items.toArray(new CharSequence[actions.size()]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                    if (i == 0) {
                        MessageStatisticActivity activity = new MessageStatisticActivity(messageObject);
                        presentFragment(activity);
                    } else if (i == 1) {
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id", chatId);
                        bundle.putInt("message_id", messageObject.getId());
                        bundle.putBoolean("need_remove_previous_same_chat_activity", false);
                        ChatActivity chatActivity = new ChatActivity(bundle);
                        presentFragment(chatActivity, false);
                    }
                });

                showDialog(builder.create());

            } else if (position >= adapter.topAdminsStartRow && position <= adapter.topAdminsEndRow) {
                int i = position - adapter.topAdminsStartRow;
                topAdmins.get(i).onLongClick(chat, this, progressDialog);
                return true;
            } else if (position >= adapter.topMembersStartRow && position <= adapter.topMembersEndRow) {
                int i = position - adapter.topMembersStartRow;
                topMembersVisible.get(i).onLongClick(chat, this, progressDialog);
                return true;
            } else if (position >= adapter.topInviterStartRow && position <= adapter.topInviterEndRow) {
                int i = position - adapter.topInviterStartRow;
                topInviters.get(i).onLongClick(chat, this, progressDialog);
                return true;
            }
            return false;
        });

        statisticLayout.addView(recyclerListView);

        avatarContainer = new ChatAvatarContainer(context, null, false);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        avatarContainer.getAvatarImageView().setScaleX(0.9f);
        avatarContainer.getAvatarImageView().setScaleY(0.9f);
        avatarContainer.setRightAvatarPadding(-dp(3));
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 50 : 0, 0, 40, 0));

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);

        avatarContainer.setChatAvatar(chatLocal);
        avatarContainer.setTitle(chatLocal == null ? "" : chatLocal.title);
        avatarContainer.hideSubtitle();

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        avatarContainer.setTitleColors(Theme.getColor(Theme.key_player_actionBarTitle), Theme.getColor(Theme.key_player_actionBarSubtitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarTitle), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarTitle), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        if (initialLoading) {
            progressLayout.setAlpha(0f);
            AndroidUtilities.runOnUIThread(showProgressbar, 500);
            progressLayout.setVisibility(View.VISIBLE);
            recyclerListView.setVisibility(View.GONE);
        } else {
            AndroidUtilities.cancelRunOnUIThread(showProgressbar);
            progressLayout.setVisibility(View.GONE);
            recyclerListView.setVisibility(View.VISIBLE);
        }


        diffUtilsCallback = new DiffUtilsCallback(adapter, layoutManager);
        return fragmentView;
    }

    public static ChartViewData createViewData(TL_stats.StatsGraph graph, String title, int graphType, boolean isLanguages) {
        if (graph == null || graph instanceof TL_stats.TL_statsGraphError) {
            return null;
        }
        ChartViewData viewData = new ChartViewData(title, graphType);
        viewData.isLanguages = isLanguages;
        if (graph instanceof TL_stats.TL_statsGraph) {
            String json = ((TL_stats.TL_statsGraph) graph).json.data;
            try {
                viewData.chartData = createChartData(new JSONObject(json), graphType, isLanguages);
                if (viewData.chartData != null) {
                    viewData.chartData.yRate = graph.rate;
                }
                viewData.zoomToken = ((TL_stats.TL_statsGraph) graph).zoom_token;
                if (viewData.chartData == null || viewData.chartData.x == null || viewData.chartData.x.length < 2) {
                    viewData.isEmpty = true;
                }
                if (graphType == 4 && viewData.chartData != null && viewData.chartData.x != null && viewData.chartData.x.length > 0) {
                    long x = viewData.chartData.x[viewData.chartData.x.length - 1];
                    viewData.childChartData = new StackLinearChartData(viewData.chartData, x);
                    viewData.activeZoom = x;
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else if (graph instanceof TL_stats.TL_statsGraphAsync) {
            viewData.token = ((TL_stats.TL_statsGraphAsync) graph).token;
        }

        return viewData;
    }

    public static ChartViewData createViewData(TL_stats.StatsGraph graph, String title, int graphType) {
        return createViewData(graph, title, graphType, false);
    }

    public static ChartData createChartData(JSONObject jsonObject, int graphType, boolean isLanguages) throws JSONException {
        if (graphType == 0) {
            return new ChartData(jsonObject);
        } else if (graphType == 1) {
            return new DoubleLinearChartData(jsonObject);
        } else if (graphType == 2) {
            return new StackBarChartData(jsonObject);
        } else if (graphType == 4) {
            return new StackLinearChartData(jsonObject, isLanguages);
        }
        return null;
    }

    public static final int VIEW_TYPE_LINEAR = 0;
    public static final int VIEW_TYPE_DOUBLE_LINEAR = 1;
    public static final int VIEW_TYPE_STACKBAR = 2;
    public static final int VIEW_TYPE_BAR = 3;
    public static final int VIEW_TYPE_STACKLINEAR = 4;
    public static final int VIEW_TYPE_BAR_LINEAR = 5;

    class Adapter extends RecyclerListView.SelectionAdapter {

        int overviewHeaderCell = -1;
        int overviewCell;
        int growCell = -1;
        int progressCell = -1;

        // channels
        int folowersCell = -1;
        int topHourseCell = -1;
        int interactionsCell = -1;
        int ivInteractionsCell = -1;
        int viewsBySourceCell = -1;
        int newFollowersBySourceCell = -1;
        int languagesCell = -1;
        int notificationsCell = -1;
        int reactionsByEmotionCell = -1;
        int storyInteractionsCell = -1;
        int storyReactionsByEmotionCell = -1;

        int recentPostsHeaderCell = -1;
        int recentPostsStartRow = -1;
        int recentPostsEndRow = -1;

        //megagroup
        int groupMembersCell = -1;
        int newMembersBySourceCell = -1;
        int membersLanguageCell = -1;
        int messagesCell = -1;
        int actionsCell = -1;
        int topDayOfWeeksCell = -1;
        int topMembersHeaderCell = -1;
        int topMembersStartRow = -1;
        int topMembersEndRow = -1;
        int topAdminsHeaderCell = -1;
        int topAdminsStartRow = -1;
        int topAdminsEndRow = -1;
        int topInviterHeaderCell = -1;
        int topInviterStartRow = -1;
        int topInviterEndRow = -1;
        int expandTopMembersRow = -1;


        ArraySet<Integer> shadowDivideCells = new ArraySet<>();
        ArraySet<Integer> emptyCells = new ArraySet<>();

        int count;

        @Override
        public int getItemViewType(int position) {
            if (position == growCell || position == folowersCell || position == topHourseCell || position == notificationsCell || position == actionsCell || position == groupMembersCell) {
                return VIEW_TYPE_LINEAR;
            } else if (position == interactionsCell || position == ivInteractionsCell || position == storyInteractionsCell) {
                return VIEW_TYPE_DOUBLE_LINEAR;
            } else if (position == viewsBySourceCell || position == newFollowersBySourceCell || position == newMembersBySourceCell || position == messagesCell || position == reactionsByEmotionCell || position == storyReactionsByEmotionCell) {
                return VIEW_TYPE_STACKBAR;
            } else if (position == languagesCell || position == membersLanguageCell || position == topDayOfWeeksCell) {
                return VIEW_TYPE_STACKLINEAR;
            } else if (position >= recentPostsStartRow && position <= recentPostsEndRow) {
                return 9;
            } else if (position == progressCell) {
                return 11;
            } else if (emptyCells.contains(position)) {
                return 12;
            } else if (position == recentPostsHeaderCell || position == overviewHeaderCell ||
                    position == topAdminsHeaderCell || position == topMembersHeaderCell || position == topInviterHeaderCell) {
                return 13;
            } else if (position == overviewCell) {
                return 14;
            } else if ((position >= topAdminsStartRow && position <= topAdminsEndRow) ||
                    (position >= topMembersStartRow && position <= topMembersEndRow) ||
                    (position >= topInviterStartRow && position <= topInviterEndRow)) {
                return 9;
            } else if (position == expandTopMembersRow) {
                return 15;
            } else {
                return 10;
            }
        }

        @Override
        public long getItemId(int position) {
            if (position >= recentPostsStartRow && position < recentPostsEndRow) {
                return recentAllSortedDataLoaded.get(position - recentPostsStartRow).getId();
            }
            if (position == growCell) {
                return 1;
            } else if (position == folowersCell) {
                return 2;
            } else if (position == topHourseCell) {
                return 3;
            } else if (position == interactionsCell) {
                return 4;
            } else if (position == notificationsCell) {
                return 5;
            } else if (position == ivInteractionsCell) {
                return 6;
            } else if (position == viewsBySourceCell) {
                return 7;
            } else if (position == newFollowersBySourceCell) {
                return 8;
            } else if (position == languagesCell) {
                return 9;
            } else if (position == groupMembersCell) {
                return 10;
            } else if (position == newMembersBySourceCell) {
                return 11;
            } else if (position == membersLanguageCell) {
                return 12;
            } else if (position == messagesCell) {
                return 13;
            } else if (position == actionsCell) {
                return 14;
            } else if (position == topDayOfWeeksCell) {
                return 15;
            } else if (position == reactionsByEmotionCell) {
                return 16;
            } else if (position == storyInteractionsCell) {
                return 17;
            } else if (position == storyReactionsByEmotionCell) {
                return 18;
            }
            return super.getItemId(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v;
            if (viewType >= 0 && viewType <= 4) {
                v = new ChartCell(parent.getContext(), currentAccount, viewType, sharedUi) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (getTranslationY() != 0) {
                            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        }
                        super.onDraw(canvas);
                    }
                };
                v.setWillNotDraw(false);
            } else if (viewType == 9) {
                v = new StatisticPostInfoCell(parent.getContext(), chat, getResourceProvider()) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (getTranslationY() != 0) {
                            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        }
                        super.onDraw(canvas);
                    }
                };
                v.setWillNotDraw(false);
            } else if (viewType == 11) {
                v = new LoadingCell(parent.getContext());
                v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == 12) {
                v = new EmptyCell(parent.getContext(), dp(15));
            } else if (viewType == 13) {
                ChartHeaderView headerCell = new ChartHeaderView(parent.getContext()) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (getTranslationY() != 0) {
                            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        }
                        super.onDraw(canvas);
                    }
                };
                headerCell.setWillNotDraw(false);
                headerCell.setPadding(headerCell.getPaddingLeft(), dp(16), headerCell.getRight(), dp(16));
                v = headerCell;
            } else if (viewType == 14) {
                v = new OverviewCell(parent.getContext(), isMegagroup ? 2 : 4);
            } else if (viewType == 15) {
                v = new ManageChatTextCell(parent.getContext());
                v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                ((ManageChatTextCell) v).setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            } else {
                v = new ShadowSectionCell(parent.getContext(), 12, Theme.getColor(Theme.key_windowBackgroundGray));
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            if (type >= 0 && type <= 4) {
                ChartViewData data;
                if (growCell == position) {
                    data = growthData;
                } else if (folowersCell == position) {
                    data = followersData;
                } else if (interactionsCell == position) {
                    data = interactionsData;
                } else if (viewsBySourceCell == position) {
                    data = viewsBySourceData;
                } else if (newFollowersBySourceCell == position) {
                    data = newFollowersBySourceData;
                } else if (ivInteractionsCell == position) {
                    data = ivInteractionsData;
                } else if (topHourseCell == position) {
                    data = topHoursData;
                } else if (notificationsCell == position) {
                    data = notificationsData;
                } else if (reactionsByEmotionCell == position) {
                    data = reactionsByEmotionData;
                } else if (storyInteractionsCell == position) {
                    data = storyInteractionsData;
                } else if (storyReactionsByEmotionCell == position) {
                    data = storyReactionsByEmotionData;
                } else if (groupMembersCell == position) {
                    data = groupMembersData;
                } else if (newMembersBySourceCell == position) {
                    data = newMembersBySourceData;
                } else if (membersLanguageCell == position) {
                    data = membersLanguageData;
                } else if (messagesCell == position) {
                    data = messagesData;
                } else if (actionsCell == position) {
                    data = actionsData;
                } else if (topDayOfWeeksCell == position) {
                    data = topDayOfWeeksData;
                } else {
                    data = languagesData;
                }
                ((ChartCell) holder.itemView).updateData(data, false);
            } else if (type == 9) {
                if (isMegagroup) {
                    if (position >= topAdminsStartRow && position <= topAdminsEndRow) {
                        int i = position - topAdminsStartRow;
                        ((StatisticPostInfoCell) holder.itemView).setData(topAdmins.get(i));
                    } else if (position >= topMembersStartRow && position <= topMembersEndRow) {
                        int i = position - topMembersStartRow;
                        ((StatisticPostInfoCell) holder.itemView).setData(topMembersVisible.get(i));
                    } else if (position >= topInviterStartRow && position <= topInviterEndRow) {
                        int i = position - topInviterStartRow;
                        ((StatisticPostInfoCell) holder.itemView).setData(topInviters.get(i));
                    }
                } else {
                    int i = position - recentPostsStartRow;
                    RecentPostInfo recentPostInfo = recentAllSortedDataLoaded.get(i);
                    StatisticPostInfoCell cell = ((StatisticPostInfoCell) holder.itemView);
                    cell.setData(recentPostInfo, i == recentAllSortedDataLoaded.size() - 1);
                    if (recentPostInfo.isStory()) {
                        cell.setImageViewAction(v -> getOrCreateStoryViewer().open(getContext(), recentPostInfo.getId(), storiesList, StoriesListPlaceProvider.of(recyclerListView)));
                    } else {
                        cell.setImageViewAction(null);
                    }
                }
            } else if (type == 13) {
                ChartHeaderView headerCell = (ChartHeaderView) holder.itemView;
                headerCell.showDate(true);
                headerCell.setDates(minDateOverview, maxDateOverview);
                headerCell.setPadding(0, dp(16), 0, dp(16));
                if (position == overviewHeaderCell) {
                    headerCell.setTitle(getString("StatisticOverview", R.string.StatisticOverview));
                } else if (position == topAdminsHeaderCell) {
                    headerCell.setTitle(getString("TopAdmins", R.string.TopAdmins));
                } else if (position == topInviterHeaderCell) {
                    headerCell.setTitle(getString("TopInviters", R.string.TopInviters));
                } else if (position == topMembersHeaderCell) {
                    headerCell.setTitle(getString("TopMembers", R.string.TopMembers));
                } else {
                    headerCell.showDate(false);
                    headerCell.setPadding(dp(2), dp(15), dp(2), dp(6));
                    headerCell.setTitle(getString("RecentPostsCapitalize", R.string.RecentPostsCapitalize));
                }
            } else if (type == 14) {
                OverviewCell overviewCell = (OverviewCell) holder.itemView;
                if (isMegagroup) {
                    overviewCell.setData(overviewChatData);
                } else {
                    overviewCell.setData(overviewChannelData, chat);
                }
            } else if (type == 15) {
                ManageChatTextCell manageChatTextCell = (ManageChatTextCell) holder.itemView;
                manageChatTextCell.setText(LocaleController.formatPluralString("ShowVotes", topMembersAll.size() - topMembersVisible.size()), null, R.drawable.arrow_more, false);
            }
        }

        @Override
        public int getItemCount() {
            return count;
        }

        public void update() {
            growCell = -1;
            folowersCell = -1;
            interactionsCell = -1;
            viewsBySourceCell = -1;
            newFollowersBySourceCell = -1;
            languagesCell = -1;
            recentPostsStartRow = -1;
            recentPostsEndRow = -1;
            progressCell = -1;
            recentPostsHeaderCell = -1;
            ivInteractionsCell = -1;
            topHourseCell = -1;
            notificationsCell = -1;
            storyReactionsByEmotionCell = -1;
            storyInteractionsCell = -1;
            reactionsByEmotionCell = -1;
            groupMembersCell = -1;
            newMembersBySourceCell = -1;
            membersLanguageCell = -1;
            messagesCell = -1;
            actionsCell = -1;
            topDayOfWeeksCell = -1;
            topMembersHeaderCell = -1;
            topMembersStartRow = -1;
            topMembersEndRow = -1;
            topAdminsHeaderCell = -1;
            topAdminsStartRow = -1;
            topAdminsEndRow = -1;
            topInviterHeaderCell = -1;
            topInviterStartRow = -1;
            topInviterEndRow = -1;
            expandTopMembersRow = -1;

            count = 0;
            emptyCells.clear();
            shadowDivideCells.clear();

            if (isMegagroup) {
                if (overviewChatData != null) {
                    overviewHeaderCell = count++;
                    overviewCell = count++;
                }

                if (growthData != null && !growthData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    growCell = count++;
                }
                if (groupMembersData != null && !groupMembersData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    groupMembersCell = count++;
                }
                if (newMembersBySourceData != null && !newMembersBySourceData.isEmpty && !newMembersBySourceData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    newMembersBySourceCell = count++;
                }
                if (membersLanguageData != null && !membersLanguageData.isEmpty && !membersLanguageData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    membersLanguageCell = count++;
                }
                if (messagesData != null && !messagesData.isEmpty && !messagesData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    messagesCell = count++;
                }
                if (actionsData != null && !actionsData.isEmpty && !actionsData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    actionsCell = count++;
                }
                if (topHoursData != null && !topHoursData.isEmpty && !topHoursData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topHourseCell = count++;
                }

                if (topDayOfWeeksData != null && !topDayOfWeeksData.isEmpty && !topDayOfWeeksData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topDayOfWeeksCell = count++;
                }

                if (topMembersVisible.size() > 0) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topMembersHeaderCell = count++;
                    topMembersStartRow = count++;
                    count = topMembersEndRow = topMembersStartRow + topMembersVisible.size() - 1;
                    count++;
                    if (topMembersVisible.size() != topMembersAll.size()) {
                        expandTopMembersRow = count++;
                    } else {
                        emptyCells.add(count++);
                    }
                }

                if (topAdmins.size() > 0) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topAdminsHeaderCell = count++;
                    topAdminsStartRow = count++;
                    count = topAdminsEndRow = topAdminsStartRow + topAdmins.size() - 1;
                    count++;
                    emptyCells.add(count++);
                }

                if (topInviters.size() > 0) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topInviterHeaderCell = count++;
                    topInviterStartRow = count++;
                    count = topInviterEndRow = topInviterStartRow + topInviters.size() - 1;
                    count++;
                }

                if (count > 0) {
                    emptyCells.add(count++);
                    shadowDivideCells.add(count++);
                }
            } else {
                if (overviewChannelData != null) {
                    overviewHeaderCell = count++;
                    overviewCell = count++;
                }

                if (growthData != null && !growthData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    growCell = count++;

                }
                if (followersData != null && !followersData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    folowersCell = count++;
                }
                if (notificationsData != null && !notificationsData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    notificationsCell = count++;
                }
                if (topHoursData != null && !topHoursData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    topHourseCell = count++;
                }
                if (viewsBySourceData != null && !viewsBySourceData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    viewsBySourceCell = count++;
                }
                if (newFollowersBySourceData != null && !newFollowersBySourceData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    newFollowersBySourceCell = count++;
                }
                if (languagesData != null && !languagesData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    languagesCell = count++;
                }
                if (interactionsData != null && !interactionsData.isEmpty) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    interactionsCell = count++;
                }
                if ((ivInteractionsData != null && !ivInteractionsData.loading && !ivInteractionsData.isError)) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    ivInteractionsCell = count++;
                }
                if (reactionsByEmotionData != null && !reactionsByEmotionData.isEmpty && !reactionsByEmotionData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    reactionsByEmotionCell = count++;
                }
                if (storyInteractionsData != null && !storyInteractionsData.isEmpty && !storyInteractionsData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    storyInteractionsCell = count++;
                }
                if (storyReactionsByEmotionData != null && !storyReactionsByEmotionData.isEmpty && !storyReactionsByEmotionData.isError) {
                    if (count > 0) {
                        shadowDivideCells.add(count++);
                    }
                    storyReactionsByEmotionCell = count++;
                }

                shadowDivideCells.add(count++);

                if (recentAllSortedDataLoaded.size() > 0) {
                    recentPostsHeaderCell = count++;
                    recentPostsStartRow = count++;
                    count = recentPostsEndRow = recentPostsStartRow + recentAllSortedDataLoaded.size() - 1;
                    count++;

                    if (recentPostsLoaded.size() != recentPostsAll.size()) {
                        progressCell = count++;
                    } else {
                        emptyCells.add(count++);
                    }

                    shadowDivideCells.add(count++);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 9 || holder.getItemViewType() == 15;
        }
    }

    public static class UniversalChartCell extends BaseChartCell {

        private final int currentAccount;
        private final int classGuid;

        public UniversalChartCell(
            @NonNull Context context,
            int currentAccount,
            int type,
            BaseChartView.SharedUiComponents sharedUi,
            int classGuid
        ) {
            super(context, type, sharedUi);
            this.currentAccount = currentAccount;
            this.classGuid = classGuid;
        }


        @Override
        public void onZoomed() {

        }

        @Override
        public void zoomCanceled() {

        }

        private int stats_dc;
        private Utilities.Callback0Return<BaseChartCell> findCell;

        public void set(int stats_dc, ChartViewData viewData, Utilities.Callback0Return<BaseChartCell> findCell) {
            this.stats_dc = stats_dc;
            this.findCell = findCell;
//            loadData(viewData);
            updateData(viewData, false);
        }

        @Override
        public void loadData(ChartViewData data) {
            if (data == null || stats_dc < 0) return;
            data.load(currentAccount, classGuid, stats_dc, findCell);
        }
    }

    public class ChartCell extends BaseChartCell {

        private final int currentAccount;

        public ChartCell(
            @NonNull Context context,
            int currentAccount,
            int type,
            BaseChartView.SharedUiComponents sharedUi
        ) {
            super(context, type, sharedUi);
            this.currentAccount = currentAccount;
        }

        @Override
        public void zoomCanceled() {
            cancelZoom();
        }

        @Override
        public void onZoomed() {
            if (data.activeZoom > 0) {
                return;
            }
            performClick();
            if (!chartView.legendSignatureView.canGoZoom) {
                return;
            }
            final long x = chartView.getSelectedDate();
            if (chartType == 4) {
                data.childChartData = new StackLinearChartData(data.chartData, x);
                zoomChart(false);
                return;
            }

            if (data.zoomToken == null) {
                return;
            }

            cancelZoom();
            final String cacheKey = data.zoomToken + "_" + x;
            ChartData dataFromCache = childDataCache.get(cacheKey);
            if (dataFromCache != null) {
                data.childChartData = dataFromCache;
                zoomChart(false);
                return;
            }

            TL_stats.TL_loadAsyncGraph request = new TL_stats.TL_loadAsyncGraph();
            request.token = data.zoomToken;
            if (x != 0) {
                request.x = x;
                request.flags |= 1;
            }
            ZoomCancelable finalCancelable;
            lastCancelable = finalCancelable = new ZoomCancelable();
            finalCancelable.adapterPosition = recyclerListView.getChildAdapterPosition(ChartCell.this);

            chartView.legendSignatureView.showProgress(true, false);

            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                ChartData childData = null;
                if (response instanceof TL_stats.TL_statsGraph) {
                    String json = ((TL_stats.TL_statsGraph) response).json.data;
                    try {
                        childData = createChartData(new JSONObject(json), data.graphType, data == languagesData);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else if (response instanceof TL_stats.TL_statsGraphError) {
                    Toast.makeText(getContext(), ((TL_stats.TL_statsGraphError) response).error, Toast.LENGTH_LONG).show();
                }

                ChartData finalChildData = childData;
                AndroidUtilities.runOnUIThread(() -> {
                    if (finalChildData != null) {
                        childDataCache.put(cacheKey, finalChildData);
                    }
                    if (finalChildData != null && !finalCancelable.canceled && finalCancelable.adapterPosition >= 0) {
                        View view = layoutManager.findViewByPosition(finalCancelable.adapterPosition);
                        if (view instanceof ChartCell) {
                            data.childChartData = finalChildData;
                            ((ChartCell) view).chartView.legendSignatureView.showProgress(false, false);
                            ((ChartCell) view).zoomChart(false);
                        }
                    }
                    cancelZoom();
                });
            }, null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }

        @Override
        public void loadData(ChartViewData viewData) {
            viewData.load(currentAccount, classGuid, chat.stats_dc, getFindChartCell(data));
        }
    }

    public static abstract class BaseChartCell extends FrameLayout {
        BaseChartView chartView;
        BaseChartView zoomedChartView;
        ChartHeaderView chartHeaderView;

        RadialProgressView progressView;
        TextView errorTextView;

        ViewGroup checkboxContainer;
        ArrayList<CheckBoxHolder> checkBoxes = new ArrayList<>();
        ChartViewData data;

        int chartType;

        @SuppressLint("ClickableViewAccessibility")
        public BaseChartCell(@NonNull Context context, int type, BaseChartView.SharedUiComponents sharedUi) {
            this(context, type, sharedUi, null);
        }

        @SuppressLint("ClickableViewAccessibility")
        public BaseChartCell(@NonNull Context context, int type, BaseChartView.SharedUiComponents sharedUi, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setWillNotDraw(false);
            chartType = type;
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            checkboxContainer = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final int width = MeasureSpec.getSize(widthMeasureSpec);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                    int currentW = 0;
                    int currentH = 0;
                    int n = getChildCount();
                    int firstH = n > 0 ? getChildAt(0).getMeasuredHeight() : 0;
                    for (int i = 0; i < n; i++) {
                        if (currentW + getChildAt(i).getMeasuredWidth() > width) {
                            currentW = 0;
                            currentH += getChildAt(i).getMeasuredHeight();
                        }
                        currentW += getChildAt(i).getMeasuredWidth();
                    }
                    setMeasuredDimension(getMeasuredWidth(), firstH + currentH + dp(16));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int currentW = 0;
                    int currentH = 0;
                    int n = getChildCount();
                    for (int i = 0; i < n; i++) {
                        if (currentW + getChildAt(i).getMeasuredWidth() > getMeasuredWidth()) {
                            currentW = 0;
                            currentH += getChildAt(i).getMeasuredHeight();
                        }
                        getChildAt(i).layout(currentW, currentH,
                                currentW + getChildAt(i).getMeasuredWidth(),
                                currentH + getChildAt(i).getMeasuredHeight());

                        currentW += getChildAt(i).getMeasuredWidth();
                    }
                }
            };
            chartHeaderView = new ChartHeaderView(getContext(), resourcesProvider);
            chartHeaderView.back.setOnTouchListener(new RecyclerListView.FoucsableOnTouchListener());
            chartHeaderView.back.setOnClickListener(v -> zoomOut(true));

            switch (type) {
                case 1:
                    chartView = new DoubleLinearChartView(getContext(), resourcesProvider);
                    zoomedChartView = new DoubleLinearChartView(getContext(), resourcesProvider);
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
                case 2:
                    chartView = new StackBarChartView(getContext(), resourcesProvider);
                    zoomedChartView = new StackBarChartView(getContext(), resourcesProvider);
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
                case 3:
                    chartView = new BarChartView(getContext());
                    zoomedChartView = new LinearChartView(getContext());
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
                case 4:
                    chartView = new StackLinearChartView(getContext());
                    chartView.legendSignatureView.showPercentage = true;
                    zoomedChartView = new PieChartView(getContext());
                    break;
                case 5:
                    chartView = new LinearBarChartView(getContext());
                    zoomedChartView = new LinearBarChartView(getContext());
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
                case 0:
                default:
                    chartView = new LinearChartView(getContext());
                    zoomedChartView = new LinearChartView(getContext());
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
            }

            FrameLayout frameLayout = new FrameLayout(context);

            chartView.sharedUiComponents = sharedUi;
            zoomedChartView.sharedUiComponents = sharedUi;
            progressView = new RadialProgressView(context);
            frameLayout.addView(chartView);
            frameLayout.addView(chartView.legendSignatureView, WRAP_CONTENT, WRAP_CONTENT);
            frameLayout.addView(zoomedChartView);
            frameLayout.addView(zoomedChartView.legendSignatureView, WRAP_CONTENT, WRAP_CONTENT);
            frameLayout.addView(progressView, LayoutHelper.createFrame(44, 44, Gravity.CENTER, 0, 0, 0, 60));

            errorTextView = new TextView(context);
            errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            frameLayout.addView(errorTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));
            progressView.setVisibility(View.GONE);

            errorTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray4, resourcesProvider));


            chartView.setDateSelectionListener(date -> {
                zoomCanceled();
                chartView.legendSignatureView.showProgress(false, false);
            });

            chartView.legendSignatureView.showProgress(false, false);
            chartView.legendSignatureView.setOnTouchListener(new RecyclerListView.FoucsableOnTouchListener());
            chartView.legendSignatureView.setOnClickListener(v -> onZoomed());
            zoomedChartView.legendSignatureView.setOnClickListener(v -> zoomedChartView.animateLegend(false));
            chartView.setVisibility(VISIBLE);
            zoomedChartView.setVisibility(INVISIBLE);
            chartView.setHeader(chartHeaderView);

            linearLayout.addView(chartHeaderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52));
            linearLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            linearLayout.addView(checkboxContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 16, 0, 16, 0));

            if (chartType == 4) {
                frameLayout.setClipChildren(false);
                frameLayout.setClipToPadding(false);
                linearLayout.setClipChildren(false);
                linearLayout.setClipToPadding(false);
            }

            addView(linearLayout);
        }

        public abstract void onZoomed();

        public abstract void zoomCanceled();

        protected abstract void loadData(ChartViewData viewData);

        public void zoomChart(boolean skipTransition) {
            long d = chartView.getSelectedDate();
            ChartData childData = data.childChartData;
            // if (childData == null) return;

            if (!skipTransition || zoomedChartView.getVisibility() != View.VISIBLE) {
                zoomedChartView.updatePicker(childData, d);
            }
            zoomedChartView.setData(childData);


            if (data.chartData.lines.size() > 1) {
                int enabledCount = 0;
                for (int i = 0; i < data.chartData.lines.size(); i++) {
                    boolean found = false;
                    for (int j = 0; j < childData.lines.size(); j++) {
                        ChartData.Line line = childData.lines.get(j);
                        if (line.id.equals(data.chartData.lines.get(i).id)) {
                            boolean check = checkBoxes.get(i).checkBox.checked;
                            ((LineViewData) zoomedChartView.lines.get(j)).enabled = check;
                            ((LineViewData) zoomedChartView.lines.get(j)).alpha = check ? 1f : 0f;
                            checkBoxes.get(i).checkBox.enabled = true;
                            checkBoxes.get(i).checkBox.animate().alpha(1).start();
                            if (check) enabledCount++;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        checkBoxes.get(i).checkBox.enabled = false;
                        checkBoxes.get(i).checkBox.animate().alpha(0).start();
                    }
                }

                if (enabledCount == 0) {
                    for (int i = 0; i < data.chartData.lines.size(); i++) {
                        checkBoxes.get(i).checkBox.enabled = true;
                        checkBoxes.get(i).checkBox.animate().alpha(1).start();
                    }
                    return;
                }
            }

            data.activeZoom = d;

            chartView.legendSignatureView.setAlpha(0f);
            chartView.selectionA = 0;
            chartView.legendShowing = false;
            chartView.animateLegentTo = false;

            zoomedChartView.updateColors();

            if (!skipTransition) {
                zoomedChartView.clearSelection();
                chartHeaderView.zoomTo(zoomedChartView, d, true);
            }

            zoomedChartView.setHeader(chartHeaderView);
            chartView.setHeader(null);

            if (skipTransition) {
                chartView.setVisibility(INVISIBLE);
                zoomedChartView.setVisibility(VISIBLE);

                chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                chartView.enabled = false;
                zoomedChartView.enabled = true;
                chartHeaderView.zoomTo(zoomedChartView, d, false);
            } else {
                ValueAnimator animator = createTransitionAnimator(d, true);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        chartView.setVisibility(INVISIBLE);

                        chartView.enabled = false;
                        zoomedChartView.enabled = true;
                        chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                        zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                    }
                });
                animator.start();
            }
        }

        private void zoomOut(boolean animated) {
            if (data == null || data.chartData == null || data.chartData.x == null) {
                return;
            }
            chartHeaderView.zoomOut(chartView, animated);
            chartView.legendSignatureView.chevron.setAlpha(1f);
            zoomedChartView.setHeader(null);

            long d = chartView.getSelectedDate();
            data.activeZoom = 0;

            chartView.setVisibility(VISIBLE);
            zoomedChartView.clearSelection();

            zoomedChartView.setHeader(null);
            chartView.setHeader(chartHeaderView);

            if (!animated) {
                zoomedChartView.setVisibility(INVISIBLE);
                chartView.enabled = true;
                zoomedChartView.enabled = false;
                chartView.invalidate();
                ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                for (CheckBoxHolder checkbox : checkBoxes) {
                    checkbox.checkBox.setAlpha(1);
                    checkbox.checkBox.enabled = true;
                }
            } else {
                ValueAnimator animator = createTransitionAnimator(d, false);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        zoomedChartView.setVisibility(INVISIBLE);

                        chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                        zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;

                        chartView.enabled = true;
                        zoomedChartView.enabled = false;

                        if (!(chartView instanceof StackLinearChartView)) {
                            chartView.legendShowing = true;
                            chartView.moveLegend();
                            chartView.animateLegend(true);
                            chartView.invalidate();
                        } else {
                            chartView.legendShowing = false;
                            chartView.clearSelection();
                        }
                        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                    }
                });
                for (CheckBoxHolder checkbox : checkBoxes) {
                    checkbox.checkBox.animate().alpha(1f).start();
                    checkbox.checkBox.enabled = true;
                }
                animator.start();
            }


        }

        private ValueAnimator createTransitionAnimator(long d, boolean in) {
            ((Activity) getContext()).getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

            chartView.enabled = false;
            zoomedChartView.enabled = false;
            chartView.transitionMode = BaseChartView.TRANSITION_MODE_PARENT;
            zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_CHILD;

            final TransitionParams params = new TransitionParams();
            params.pickerEndOut = chartView.pickerDelegate.pickerEnd;
            params.pickerStartOut = chartView.pickerDelegate.pickerStart;

            params.date = d;

            int dateIndex = Arrays.binarySearch(data.chartData.x, d);
            if (dateIndex < 0) {
                dateIndex = data.chartData.x.length - 1;
            }
            params.xPercentage = data.chartData.xPercentage[dateIndex];


            zoomedChartView.setVisibility(VISIBLE);
            zoomedChartView.transitionParams = params;
            chartView.transitionParams = params;

            long max = 0;
            long min = Integer.MAX_VALUE;
            for (int i = 0; i < data.chartData.lines.size(); i++) {
                if (data.chartData.lines.get(i).y[dateIndex] > max)
                    max = data.chartData.lines.get(i).y[dateIndex];
                if (data.chartData.lines.get(i).y[dateIndex] < min)
                    min = data.chartData.lines.get(i).y[dateIndex];
            }
            final float pYPercentage = (((float) min + (max - min)) - chartView.currentMinHeight) / (chartView.currentMaxHeight - chartView.currentMinHeight);


            chartView.fillTransitionParams(params);
            zoomedChartView.fillTransitionParams(params);
            ValueAnimator animator = ValueAnimator.ofFloat(in ? 0f : 1f, in ? 1f : 0f);
            animator.addUpdateListener(animation -> {
                float fullWidth = (chartView.chartWidth / (chartView.pickerDelegate.pickerEnd - chartView.pickerDelegate.pickerStart));
                float offset = fullWidth * (chartView.pickerDelegate.pickerStart) - chartView.HORIZONTAL_PADDING;

                params.pY = chartView.chartArea.top + (1f - pYPercentage) * chartView.chartArea.height();
                params.pX = chartView.chartFullWidth * params.xPercentage - offset;

                params.progress = (float) animation.getAnimatedValue();
                zoomedChartView.invalidate();
                zoomedChartView.fillTransitionParams(params);
                chartView.invalidate();
            });

            animator.setDuration(400);
            animator.setInterpolator(new FastOutSlowInInterpolator());

            return animator;
        }

        public void updateData(ChartViewData viewData, boolean enterTransition) {
            if (viewData == null) {
                return;
            }
            chartHeaderView.setTitle(viewData.title);

            Configuration configuration = getContext().getResources().getConfiguration();
            boolean land = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
            chartView.setLandscape(land);
            viewData.viewShowed = true;
            zoomedChartView.setLandscape(land);
            data = viewData;

            if (viewData.isEmpty || viewData.isError) {
                progressView.setVisibility(View.GONE);
                if (viewData.errorMessage != null) {
                    errorTextView.setText(viewData.errorMessage);
                    if (errorTextView.getVisibility() == View.GONE) {
                        errorTextView.setAlpha(0f);
                        errorTextView.animate().alpha(1f);
                    }
                    errorTextView.setVisibility(View.VISIBLE);
                }
                checkboxContainer.removeAllViews();
                checkBoxes.clear();
                chartView.setData(null);
                return;
            }

            errorTextView.setVisibility(View.GONE);
            chartView.legendSignatureView.isTopHourChart = viewData.useHourFormat;
            chartHeaderView.showDate(!viewData.useHourFormat);

            if (viewData.chartData == null && viewData.token != null) {
                progressView.setAlpha(1f);
                progressView.setVisibility(View.VISIBLE);
                loadData(viewData);
                chartView.setData(null);
                return;
            } else if (!enterTransition) {
                progressView.setVisibility(View.GONE);
            }

            chartView.setData(viewData.chartData);
            chartHeaderView.setUseWeekInterval(viewData.useWeekFormat);
            chartView.legendSignatureView.setUseWeek(viewData.useWeekFormat);

            chartView.legendSignatureView.zoomEnabled = !(data.zoomToken == null && chartType != 4);
            zoomedChartView.legendSignatureView.zoomEnabled = false;

            chartView.legendSignatureView.setEnabled(chartView.legendSignatureView.zoomEnabled);
            zoomedChartView.legendSignatureView.setEnabled(zoomedChartView.legendSignatureView.zoomEnabled);

            int n = chartView.lines.size();
            checkboxContainer.removeAllViews();
            checkBoxes.clear();
            if (n > 1) {
                for (int i = 0; i < n; i++) {
                    LineViewData l = (LineViewData) chartView.lines.get(i);
                    new CheckBoxHolder(i).setData(l);
                }
            }

            if (data.activeZoom > 0) {
                chartView.selectDate(data.activeZoom);
                zoomChart(true);
            } else {
                zoomOut(false);
                chartView.invalidate();
            }

            recolor();

            if (enterTransition) {
                chartView.transitionMode = BaseChartView.TRANSITION_MODE_ALPHA_ENTER;
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                chartView.transitionParams = new TransitionParams();
                chartView.transitionParams.progress = 0;
                animator.addUpdateListener(animation -> {
                    float a = (float) animation.getAnimatedValue();
                    progressView.setAlpha(1f - a);
                    chartView.transitionParams.progress = a;
                    zoomedChartView.invalidate();
                    chartView.invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE;
                        progressView.setVisibility(View.GONE);
                    }
                });
                animator.start();
            }
        }

        public void recolor() {
            chartView.updateColors();
            chartView.invalidate();

            zoomedChartView.updateColors();
            zoomedChartView.invalidate();

            chartHeaderView.recolor();
            chartHeaderView.invalidate();

            if (data != null && data.chartData != null && data.chartData.lines != null && data.chartData.lines.size() > 1) {
                for (int i = 0; i < data.chartData.lines.size(); i++) {
                    int color;
                    if (data.chartData.lines.get(i).colorKey >= 0 && Theme.hasThemeKey(data.chartData.lines.get(i).colorKey)) {
                        color = Theme.getColor(data.chartData.lines.get(i).colorKey);
                    } else {
                        boolean darkBackground = ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) < 0.5f;
                        color = darkBackground ? data.chartData.lines.get(i).colorDark :
                                data.chartData.lines.get(i).color;
                    }
                    if (i < checkBoxes.size()) {
                        checkBoxes.get(i).recolor(color);
                    }
                }
            }
            progressView.setProgressColor(Theme.getColor(Theme.key_progressCircle));
            errorTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));
        }

        class CheckBoxHolder {
            final FlatCheckBox checkBox;
            LineViewData line;
            final int position;

            CheckBoxHolder(int position) {
                this.position = position;
                checkBox = new FlatCheckBox(getContext());

                checkBox.setPadding(dp(16), 0, dp(16), 0);
                checkboxContainer.addView(checkBox);
                checkBoxes.add(this);
            }

            public void setData(final LineViewData l) {
                this.line = l;
                checkBox.setText(l.line.name);
                checkBox.setChecked(l.enabled, false);
                checkBox.setOnTouchListener(new RecyclerListView.FoucsableOnTouchListener());
                checkBox.setOnClickListener(v -> {
                    if (!checkBox.enabled) {
                        return;
                    }
                    boolean allDisabled = true;
                    int n = checkBoxes.size();
                    for (int i = 0; i < n; i++) {
                        if (i != position && checkBoxes.get(i).checkBox.enabled && checkBoxes.get(i).checkBox.checked) {
                            allDisabled = false;
                            break;
                        }
                    }
                    zoomCanceled();
                    if (allDisabled) {
                        checkBox.denied();
                        return;
                    }
                    checkBox.setChecked(!checkBox.checked);
                    l.enabled = checkBox.checked;
                    chartView.onCheckChanged();

                    if (data.activeZoom > 0) {
                        if (position < zoomedChartView.lines.size()) {
                            ((LineViewData) zoomedChartView.lines.get(position)).enabled = checkBox.checked;
                            zoomedChartView.onCheckChanged();
                        }
                    }
                });

                checkBox.setOnLongClickListener(v -> {
                    if (!checkBox.enabled) {
                        return false;
                    }
                    zoomCanceled();
                    int n = checkBoxes.size();
                    for (int i = 0; i < n; i++) {
                        checkBoxes.get(i).checkBox.setChecked(false);
                        checkBoxes.get(i).line.enabled = false;

                        if (data.activeZoom > 0 && i < zoomedChartView.lines.size()) {
                            ((LineViewData) zoomedChartView.lines.get(i)).enabled = false;
                        }
                    }

                    checkBox.setChecked(true);
                    l.enabled = true;
                    chartView.onCheckChanged();

                    if (data.activeZoom > 0) {
                        ((LineViewData) zoomedChartView.lines.get(position)).enabled = true;
                        zoomedChartView.onCheckChanged();
                    }
                    return true;
                });
            }

            public void recolor(int c) {
                checkBox.recolor(c);
            }
        }
    }

    private void cancelZoom() {
        if (lastCancelable != null) {
            lastCancelable.canceled = true;
        }
        int n = recyclerListView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof ChartCell) {
                ((ChartCell) child).chartView.legendSignatureView.showProgress(false, true);
            }
        }
    }


    public static class ZoomCancelable {
        int adapterPosition;
        boolean canceled;
    }

    public static class ChartViewData {

        public boolean isError;
        public String errorMessage;
        public long activeZoom;
        public boolean viewShowed;
        ChartData chartData;
        ChartData childChartData;
        String token;
        String zoomToken;

        final int graphType;
        final String title;

        boolean loading;
        boolean isEmpty;
        boolean isLanguages;
        boolean useHourFormat;
        boolean useWeekFormat;

        public ChartViewData(String title, int grahType) {
            this.title = title;
            this.graphType = grahType;
        }

        public void load(int accountId, int classGuid, int dc, Utilities.Callback0Return<BaseChartCell> findMe) {
            if (!loading) {
                loading = true;
                TL_stats.TL_loadAsyncGraph request = new TL_stats.TL_loadAsyncGraph();
                request.token = token;
                int reqId = ConnectionsManager.getInstance(accountId).sendRequest(request, (response, error) -> {
                    ChartData chartData = null;
                    String zoomToken = null;
                    if (error == null) {
                        if (response instanceof TL_stats.TL_statsGraph) {
                            String json = ((TL_stats.TL_statsGraph) response).json.data;
                            try {
                                chartData = createChartData(new JSONObject(json), graphType, isLanguages);
                                zoomToken = ((TL_stats.TL_statsGraph) response).zoom_token;
                                if (graphType == 4 && chartData.x != null && chartData.x.length > 0) {
                                    long x = chartData.x[chartData.x.length - 1];
                                    childChartData = new StackLinearChartData(chartData, x);
                                    activeZoom = x;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        if (response instanceof TL_stats.TL_statsGraphError) {
                            isEmpty = false;
                            isError = true;
                            errorMessage = ((TL_stats.TL_statsGraphError) response).error;
                        }
                    }

                    ChartData finalChartData = chartData;
                    String finalZoomToken = zoomToken;
                    AndroidUtilities.runOnUIThread(() -> {
                        loading = false;
                        this.chartData = finalChartData;
                        this.zoomToken = finalZoomToken;

                        BaseChartCell me = findMe.run();
                        if (me != null) {
                            me.updateData(this, true);
                        }
                    });
                }, null, null, 0, dc, ConnectionsManager.ConnectionTypeGeneric, true);
                ConnectionsManager.getInstance(accountId).bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public static class RecentPostInfo {
        public TL_stats.PostInteractionCounters counters;
        public MessageObject message;

        public long getDate() {
            if (message == null) {
                return 0;
            }
            return message.messageOwner.date;
        }

        public boolean isStory() {
            return counters instanceof TL_stats.TL_postInteractionCountersStory;
        }

        public int getViews() {
            if (counters instanceof TL_stats.TL_postInteractionCountersMessage) {
                return ((TL_stats.TL_postInteractionCountersMessage) counters).views;
            }
            if (counters instanceof TL_stats.TL_postInteractionCountersStory) {
                return ((TL_stats.TL_postInteractionCountersStory) counters).views;
            }
            return 0;
        }

        public int getReactions() {
            if (counters instanceof TL_stats.TL_postInteractionCountersMessage) {
                return ((TL_stats.TL_postInteractionCountersMessage) counters).reactions;
            }
            if (counters instanceof TL_stats.TL_postInteractionCountersStory) {
                return ((TL_stats.TL_postInteractionCountersStory) counters).reactions;
            }
            return 0;
        }

        public int getForwards() {
            if (counters instanceof TL_stats.TL_postInteractionCountersMessage) {
                return ((TL_stats.TL_postInteractionCountersMessage) counters).forwards;
            }
            if (counters instanceof TL_stats.TL_postInteractionCountersStory) {
                return ((TL_stats.TL_postInteractionCountersStory) counters).forwards;
            }
            return 0;
        }

        public int getId() {
            if (counters instanceof TL_stats.TL_postInteractionCountersMessage) {
                return ((TL_stats.TL_postInteractionCountersMessage) counters).msg_id;
            }
            if (counters instanceof TL_stats.TL_postInteractionCountersStory) {
                return ((TL_stats.TL_postInteractionCountersStory) counters).story_id;
            }
            return 0;
        }
    }

    private void loadMessages() {
        TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
        req.id = new ArrayList<>();
        int index = recentPostIdtoIndexMap.get(loadFromId);
        int n = recentPostsAll.size();
        int count = 0;
        for (int i = index; i < n; i++) {
            if (recentPostsAll.get(i).message == null) {
                req.id.add(recentPostsAll.get(i).getId());
                count++;
                if (count > 50) {
                    break;
                }
            }
        }
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        messagesIsLoading = true;

        getConnectionsManager().sendRequest(req, (response, error) -> {
            final ArrayList<MessageObject> messageObjects = new ArrayList<>();

            if (response instanceof TLRPC.messages_Messages) {
                ArrayList<TLRPC.Message> messages = ((TLRPC.messages_Messages) response).messages;
                for (int i = 0; i < messages.size(); i++) {
                    messageObjects.add(new MessageObject(currentAccount, messages.get(i), false, true));
                }
                getMessagesStorage().putMessages(messages, false, true, true, 0, 0, 0);
            }

            AndroidUtilities.runOnUIThread(() -> {
                messagesIsLoading = false;
                if (messageObjects.isEmpty()) {
                    return;
                }
                int size = messageObjects.size();
                for (int i = 0; i < size; i++) {
                    MessageObject messageObjectFormCache = messageObjects.get(i);
                    int localIndex = recentPostIdtoIndexMap.get(messageObjectFormCache.getId(), -1);
                    if (localIndex >= 0 && recentPostsAll.get(localIndex).getId() == messageObjectFormCache.getId()) {
                        recentPostsAll.get(localIndex).message = messageObjectFormCache;
                    }
                }

                recentPostsLoaded.clear();
                size = recentPostsAll.size();
                for (int i = 0; i < size; i++) {
                    RecentPostInfo postInfo = recentPostsAll.get(i);
                    if (postInfo.message == null) {
                        loadFromId = postInfo.getId();
                        break;
                    } else {
                        recentPostsLoaded.add(postInfo);
                    }
                }
                sortAllLoadedData();
                recyclerListView.setItemAnimator(null);
                diffUtilsCallback.update();
            });
        });
    }

    private void recolorRecyclerItem(View child) {
        if (child instanceof ChartCell) {
            ((ChartCell) child).recolor();
        } else if (child instanceof ShadowSectionCell) {
            Drawable shadowDrawable = Theme.getThemedDrawableByKey(ApplicationLoader.applicationContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
            Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
            CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
            combinedDrawable.setFullsize(true);
            child.setBackground(combinedDrawable);
        } else if (child instanceof ChartHeaderView) {
            ((ChartHeaderView) child).recolor();
        } else if (child instanceof OverviewCell) {
            ((OverviewCell) child).updateColors();
        }
    }

    private static class DiffUtilsCallback extends DiffUtil.Callback {
        int count;
        private final Adapter adapter;
        private final LinearLayoutManager layoutManager;
        SparseIntArray positionToTypeMap = new SparseIntArray();


        int growCell = -1;
        int folowersCell = -1;
        int interactionsCell = -1;
        int ivInteractionsCell = -1;
        int viewsBySourceCell = -1;
        int newFollowersBySourceCell = -1;
        int languagesCell = -1;
        int topHourseCell = -1;
        int notificationsCell = -1;
        int reactionsByEmotionCell = -1;
        int storyInteractionsCell = -1;
        int storyReactionsByEmotionCell = -1;

        int groupMembersCell = -1;
        int newMembersBySourceCell = -1;
        int membersLanguageCell = -1;
        int messagesCell = -1;
        int actionsCell = -1;
        int topDayOfWeeksCell = -1;

        int startPosts = -1;
        int endPosts = -1;

        private DiffUtilsCallback(Adapter adapter, LinearLayoutManager layoutManager) {
            this.adapter = adapter;
            this.layoutManager = layoutManager;
        }

        public void saveOldState() {
            positionToTypeMap.clear();
            count = adapter.getItemCount();
            for (int i = 0; i < count; i++) {
                positionToTypeMap.put(i, adapter.getItemViewType(i));
            }
            growCell = adapter.growCell;
            folowersCell = adapter.folowersCell;
            interactionsCell = adapter.interactionsCell;
            ivInteractionsCell = adapter.ivInteractionsCell;
            viewsBySourceCell = adapter.viewsBySourceCell;
            newFollowersBySourceCell = adapter.newFollowersBySourceCell;
            languagesCell = adapter.languagesCell;
            topHourseCell = adapter.topHourseCell;
            notificationsCell = adapter.notificationsCell;
            startPosts = adapter.recentPostsStartRow;
            endPosts = adapter.recentPostsEndRow;
            reactionsByEmotionCell = adapter.reactionsByEmotionCell;
            storyInteractionsCell = adapter.storyInteractionsCell;
            storyReactionsByEmotionCell = adapter.storyReactionsByEmotionCell;

            groupMembersCell = adapter.groupMembersCell;
            newMembersBySourceCell = adapter.newMembersBySourceCell;
            membersLanguageCell = adapter.membersLanguageCell;
            messagesCell = adapter.messagesCell;
            actionsCell = adapter.actionsCell;
            topDayOfWeeksCell = adapter.topDayOfWeeksCell;
        }

        @Override
        public int getOldListSize() {
            return count;
        }

        @Override
        public int getNewListSize() {
            return adapter.count;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (positionToTypeMap.get(oldItemPosition) == 13 && adapter.getItemViewType(newItemPosition) == 13) {
                return true;
            }
            if (positionToTypeMap.get(oldItemPosition) == 10 && adapter.getItemViewType(newItemPosition) == 10) {
                return true;
            }
            if (oldItemPosition >= startPosts && oldItemPosition <= endPosts) {
                return oldItemPosition - startPosts == newItemPosition - adapter.recentPostsStartRow;
            }
            if (oldItemPosition == growCell && newItemPosition == adapter.growCell) {
                return true;
            } else if (oldItemPosition == folowersCell && newItemPosition == adapter.folowersCell) {
                return true;
            } else if (oldItemPosition == interactionsCell && newItemPosition == adapter.interactionsCell) {
                return true;
            } else if (oldItemPosition == ivInteractionsCell && newItemPosition == adapter.ivInteractionsCell) {
                return true;
            } else if (oldItemPosition == viewsBySourceCell && newItemPosition == adapter.viewsBySourceCell) {
                return true;
            } else if (oldItemPosition == newFollowersBySourceCell && newItemPosition == adapter.newFollowersBySourceCell) {
                return true;
            } else if (oldItemPosition == languagesCell && newItemPosition == adapter.languagesCell) {
                return true;
            } else if (oldItemPosition == topHourseCell && newItemPosition == adapter.topHourseCell) {
                return true;
            } else if (oldItemPosition == notificationsCell && newItemPosition == adapter.notificationsCell) {
                return true;
            } else if (oldItemPosition == groupMembersCell && newItemPosition == adapter.groupMembersCell) {
                return true;
            } else if (oldItemPosition == newMembersBySourceCell && newItemPosition == adapter.newMembersBySourceCell) {
                return true;
            } else if (oldItemPosition == membersLanguageCell && newItemPosition == adapter.membersLanguageCell) {
                return true;
            } else if (oldItemPosition == messagesCell && newItemPosition == adapter.messagesCell) {
                return true;
            } else if (oldItemPosition == actionsCell && newItemPosition == adapter.actionsCell) {
                return true;
            } else if (oldItemPosition == topDayOfWeeksCell && newItemPosition == adapter.topDayOfWeeksCell) {
                return true;
            } else if (oldItemPosition == reactionsByEmotionCell && newItemPosition == adapter.reactionsByEmotionCell) {
                return true;
            } else if (oldItemPosition == storyInteractionsCell && newItemPosition == adapter.storyInteractionsCell) {
                return true;
            } else if (oldItemPosition == storyReactionsByEmotionCell && newItemPosition == adapter.storyReactionsByEmotionCell) {
                return true;
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            int oldType = positionToTypeMap.get(oldItemPosition);
            return oldType == adapter.getItemViewType(newItemPosition);
        }

        public void update() {
            saveOldState();
            adapter.update();
            int start = layoutManager.findFirstVisibleItemPosition();
            int end = layoutManager.findLastVisibleItemPosition();
            long scrollToItemId = RecyclerView.NO_ID;
            int offset = 0;
            for (int i = start; i <= end; i++) {
                if (adapter.getItemId(i) != RecyclerView.NO_ID) {
                    View v = layoutManager.findViewByPosition(i);
                    if (v != null) {
                        scrollToItemId = adapter.getItemId(i);
                        offset = v.getTop();
                        break;
                    }
                }
            }
            DiffUtil.calculateDiff(this).dispatchUpdatesTo(adapter);
            if (scrollToItemId != RecyclerView.NO_ID) {
                int position = -1;

                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.getItemId(i) == scrollToItemId) {
                        position = i;
                        break;
                    }
                }
                if (position > 0) {
                    layoutManager.scrollToPositionWithOffset(position, offset);
                }
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate themeDelegate = () -> {
            if (recyclerListView != null) {
                int count = recyclerListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(recyclerListView.getChildAt(a));
                }
                count = recyclerListView.getHiddenChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(recyclerListView.getHiddenChildAt(a));
                }
                count = recyclerListView.getCachedChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(recyclerListView.getCachedChildAt(a));
                }
                count = recyclerListView.getAttachedScrapChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(recyclerListView.getAttachedScrapChildAt(a));
                }
                recyclerListView.getRecycledViewPool().clear();
            }
            if (sharedUi != null) {
                sharedUi.invalidate();
            }
        };

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"message"}, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"views"}, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"shares"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"likes"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"date"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{ChartHeaderView.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlack));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartSignature));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartSignatureAlpha));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartHintLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartActiveLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartInactivePickerChart));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_statisticChartActivePickerChart));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, themeDelegate, Theme.key_actionBarActionModeDefaultSelector));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundWhiteGreenText2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_text_RedRegular));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        arrayList.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getSubtitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_player_actionBarSubtitle, null));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_statisticChartLineEmpty));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_text_RedRegular));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class, HeaderCell.class, TextView.class, PeopleNearbyActivity.HintInnerCell.class}, null, null, null, Theme.key_windowBackgroundWhite));

        if (isMegagroup) {
            for (int i = 0; i < 6; i++) {
                ChartViewData chartViewData;
                if (i == 0) {
                    chartViewData = growthData;
                } else if (i == 1) {
                    chartViewData = groupMembersData;
                } else if (i == 2) {
                    chartViewData = newMembersBySourceData;
                } else if (i == 3) {
                    chartViewData = membersLanguageData;
                } else if (i == 4) {
                    chartViewData = messagesData;
                } else {
                    chartViewData = actionsData;
                }
                putColorFromData(chartViewData, arrayList, themeDelegate);
            }
        } else {
            for (int i = 0; i < 12; i++) {
                ChartViewData chartViewData;
                if (i == 0) {
                    chartViewData = growthData;
                } else if (i == 1) {
                    chartViewData = followersData;
                } else if (i == 2) {
                    chartViewData = interactionsData;
                } else if (i == 3) {
                    chartViewData = ivInteractionsData;
                } else if (i == 4) {
                    chartViewData = viewsBySourceData;
                } else if (i == 5) {
                    chartViewData = newFollowersBySourceData;
                } else if (i == 6) {
                    chartViewData = notificationsData;
                } else if (i == 7) {
                    chartViewData = topHoursData;
                } else if (i == 8) {
                    chartViewData = languagesData;
                } else if (i == 9) {
                    chartViewData = reactionsByEmotionData;
                } else if (i == 10) {
                    chartViewData = storyInteractionsData;
                } else {
                    chartViewData = storyReactionsByEmotionData;
                }

                putColorFromData(chartViewData, arrayList, themeDelegate);
            }
        }

        return arrayList;
    }

    public static void putColorFromData(ChartViewData chartViewData, ArrayList<ThemeDescription> arrayList, ThemeDescription.ThemeDescriptionDelegate themeDelegate) {
        if (chartViewData != null && chartViewData.chartData != null) {
            for (ChartData.Line l : chartViewData.chartData.lines) {
                if (l.colorKey >= 0) {
                    if (!Theme.hasThemeKey(l.colorKey)) {
                        Theme.setColor(l.colorKey, Theme.isCurrentThemeNight() ? l.colorDark : l.color, false);
                        Theme.setDefaultColor(l.colorKey, l.color);
                    }
                    arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, l.colorKey));
                }
            }
        }
    }

    public static class OverviewChannelData {

        String followersTitle;
        String followersPrimary;
        String followersSecondary;
        boolean followersUp;

        String viewsTitle;
        String viewsPrimary;
        String viewsSecondary;
        boolean viewsUp;

        String sharesTitle;
        String sharesPrimary;
        String sharesSecondary;
        boolean sharesUp;

        String notificationsTitle;
        String notificationsPrimary;

        String reactionsPerPostTitle;
        String reactionsPerPostPrimary;
        String reactionsPerPostSecondary;
        boolean reactionsPerPostUp;
        boolean reactionsPerPostVisible;

        String reactionsPerStoryTitle;
        String reactionsPerStoryPrimary;
        String reactionsPerStorySecondary;
        boolean reactionsPerStoryUp;
        boolean reactionsPerStoryVisible;

        String viewsPerStoryTitle;
        String viewsPerStoryPrimary;
        String viewsPerStorySecondary;
        boolean viewsPerStoryUp;
        boolean viewsPerStoryVisible;

        String sharesPerStoryTitle;
        String sharesPerStoryPrimary;
        String sharesPerStorySecondary;
        boolean sharesPerStoryUp;
        boolean sharesPerStoryVisible;

        public static class Quadruple<A, B, C, D> {
            public Quadruple(A fist, B second, C third, D fourth) {
                this.fist = fist;
                this.second = second;
                this.third = third;
                this.fourth = fourth;
            }

            public A fist;
            public B second;
            public C third;
            public D fourth;
        }

        private Quadruple<String, String, Boolean, Boolean> prepare(TL_stats.TL_statsAbsValueAndPrev valueAndPrev) {
            int dif = (int) (valueAndPrev.current - valueAndPrev.previous);
            float difPercent = valueAndPrev.previous == 0 ? 0 : Math.abs(dif / (float) valueAndPrev.previous * 100f);
            String primary = AndroidUtilities.formatWholeNumber((int) valueAndPrev.current, 0);
            String secondary;
            if (dif == 0 || difPercent == 0) {
                secondary = "";
            } else if (difPercent == (int) difPercent) {
                secondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                secondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            boolean up = dif >= 0;
            boolean isSectionVisible = dif != 0 || valueAndPrev.current != 0;
            return new Quadruple<>(primary, secondary, up, isSectionVisible);
        }

        public OverviewChannelData(TL_stats.TL_broadcastStats stats) {
            Quadruple<String, String, Boolean, Boolean> quadrupleData = prepare(stats.reactions_per_post);
            reactionsPerPostTitle = getString("ReactionsPerPost", R.string.ReactionsPerPost);
            reactionsPerPostPrimary = quadrupleData.fist;
            reactionsPerPostSecondary = quadrupleData.second;
            reactionsPerPostUp = quadrupleData.third;
            reactionsPerPostVisible = quadrupleData.fourth;

            quadrupleData = prepare(stats.reactions_per_story);
            reactionsPerStoryTitle = getString("ReactionsPerStory", R.string.ReactionsPerStory);
            reactionsPerStoryPrimary = quadrupleData.fist;
            reactionsPerStorySecondary = quadrupleData.second;
            reactionsPerStoryUp = quadrupleData.third;
            reactionsPerStoryVisible = quadrupleData.fourth;

            quadrupleData = prepare(stats.views_per_story);
            viewsPerStoryTitle = getString("ViewsPerStory", R.string.ViewsPerStory);
            viewsPerStoryPrimary = quadrupleData.fist;
            viewsPerStorySecondary = quadrupleData.second;
            viewsPerStoryUp = quadrupleData.third;
            viewsPerStoryVisible = quadrupleData.fourth;

            quadrupleData = prepare(stats.shares_per_story);
            sharesPerStoryTitle = getString("SharesPerStory", R.string.SharesPerStory);
            sharesPerStoryPrimary = quadrupleData.fist;
            sharesPerStorySecondary = quadrupleData.second;
            sharesPerStoryUp = quadrupleData.third;
            sharesPerStoryVisible = quadrupleData.fourth;

            int dif = (int) (stats.followers.current - stats.followers.previous);
            float difPercent = stats.followers.previous == 0 ? 0 : Math.abs(dif / (float) stats.followers.previous * 100f);
            followersTitle = getString("FollowersChartTitle", R.string.FollowersChartTitle);
            followersPrimary = AndroidUtilities.formatWholeNumber((int) stats.followers.current, 0);

            if (dif == 0 || difPercent == 0) {
                followersSecondary = "";
            } else if (difPercent == (int) difPercent) {
                followersSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                followersSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            followersUp = dif >= 0;

            dif = (int) (stats.shares_per_post.current - stats.shares_per_post.previous);
            difPercent = stats.shares_per_post.previous == 0 ? 0 : Math.abs(dif / (float) stats.shares_per_post.previous * 100f);
            sharesTitle = getString("SharesPerPost", R.string.SharesPerPost);
            sharesPrimary = AndroidUtilities.formatWholeNumber((int) stats.shares_per_post.current, 0);

            if (dif == 0 || difPercent == 0) {
                sharesSecondary = "";
            } else if (difPercent == (int) difPercent) {
                sharesSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                sharesSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            sharesUp = dif >= 0;

            dif = (int) (stats.views_per_post.current - stats.views_per_post.previous);
            difPercent = stats.views_per_post.previous == 0 ? 0 : Math.abs(dif / (float) stats.views_per_post.previous * 100f);
            viewsTitle = getString("ViewsPerPost", R.string.ViewsPerPost);
            viewsPrimary = AndroidUtilities.formatWholeNumber((int) stats.views_per_post.current, 0);
            if (dif == 0 || difPercent == 0) {
                viewsSecondary = "";
            } else if (difPercent == (int) difPercent) {
                viewsSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                viewsSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            viewsUp = dif >= 0;

            difPercent = (float) (stats.enabled_notifications.part / stats.enabled_notifications.total * 100f);
            notificationsTitle = getString("EnabledNotifications", R.string.EnabledNotifications);
            if (difPercent == (int) difPercent) {
                notificationsPrimary = String.format(Locale.ENGLISH, "%d%s", (int) difPercent, "%");
            } else {
                notificationsPrimary = String.format(Locale.ENGLISH, "%.2f%s", difPercent, "%");
            }
        }
    }

    public static class OverviewChatData {

        String membersTitle;
        String membersPrimary;
        String membersSecondary;
        boolean membersUp;

        String messagesTitle;
        String messagesPrimary;
        String messagesSecondary;
        boolean messagesUp;

        String viewingMembersTitle;
        String viewingMembersPrimary;
        String viewingMembersSecondary;
        boolean viewingMembersUp;

        String postingMembersTitle;
        String postingMembersPrimary;
        String postingMembersSecondary;
        boolean postingMembersUp;

        public OverviewChatData(TL_stats.TL_megagroupStats stats) {
            int dif = (int) (stats.members.current - stats.members.previous);
            float difPercent = stats.members.previous == 0 ? 0 : Math.abs(dif / (float) stats.members.previous * 100f);
            membersTitle = getString("MembersOverviewTitle", R.string.MembersOverviewTitle);
            membersPrimary = AndroidUtilities.formatWholeNumber((int) stats.members.current, 0);

            if (dif == 0 || difPercent == 0) {
                membersSecondary = "";
            } else if (difPercent == (int) difPercent) {
                membersSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                membersSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            membersUp = dif >= 0;

            dif = (int) (stats.viewers.current - stats.viewers.previous);
            difPercent = stats.viewers.previous == 0 ? 0 : Math.abs(dif / (float) stats.viewers.previous * 100f);
            viewingMembersTitle = getString("ViewingMembers", R.string.ViewingMembers);
            viewingMembersPrimary = AndroidUtilities.formatWholeNumber((int) stats.viewers.current, 0);

            if (dif == 0 || difPercent == 0) {
                viewingMembersSecondary = "";
            } else {
                viewingMembersSecondary = String.format(Locale.ENGLISH, "%s", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0));
            }
            viewingMembersUp = dif >= 0;


            dif = (int) (stats.posters.current - stats.posters.previous);
            difPercent = stats.posters.previous == 0 ? 0 : Math.abs(dif / (float) stats.posters.previous * 100f);
            postingMembersTitle = getString("PostingMembers", R.string.PostingMembers);
            postingMembersPrimary = AndroidUtilities.formatWholeNumber((int) stats.posters.current, 0);
            if (dif == 0 || difPercent == 0) {
                postingMembersSecondary = "";
            } else {
                postingMembersSecondary = String.format(Locale.ENGLISH, "%s", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0));
            }
            postingMembersUp = dif >= 0;

            dif = (int) (stats.messages.current - stats.messages.previous);
            difPercent = stats.messages.previous == 0 ? 0 : Math.abs(dif / (float) stats.messages.previous * 100f);
            messagesTitle = getString("MessagesOverview", R.string.MessagesOverview);
            messagesPrimary = AndroidUtilities.formatWholeNumber((int) stats.messages.current, 0);
            if (dif == 0 || difPercent == 0) {
                messagesSecondary = "";
            } else {
                messagesSecondary = String.format(Locale.ENGLISH, "%s", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0));
            }
            messagesUp = dif >= 0;
        }
    }

    public static class OverviewCell extends LinearLayout {

        TextView[] primary;
        TextView[] secondary;
        TextView[] title;

        public OverviewCell(Context context) {
            this(context, 2);
        }

        public OverviewCell(Context context, int maxRows) {
            super(context);
            primary = new TextView[maxRows * 2];
            secondary = new TextView[maxRows * 2];
            title = new TextView[maxRows * 2];
            setOrientation(VERTICAL);
            setPadding(dp(16), 0, dp(16), 0);
            for (int i = 0; i < maxRows; i++) {
                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(HORIZONTAL);

                for (int j = 0; j < 2; j++) {
                    LinearLayout contentCell = new LinearLayout(context);
                    contentCell.setOrientation(VERTICAL);

                    LinearLayout infoLayout = new LinearLayout(context);
                    infoLayout.setOrientation(HORIZONTAL);
                    primary[i * 2 + j] = new TextView(context);
                    secondary[i * 2 + j] = new TextView(context);
                    title[i * 2 + j] = new TextView(context);

                    primary[i * 2 + j].setTypeface(AndroidUtilities.bold());
                    primary[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                    title[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    title[i * 2 + j].setGravity(Gravity.LEFT);
                    secondary[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);

                    secondary[i * 2 + j].setPadding(dp(4), 0, 0, 0);

                    infoLayout.addView(primary[i * 2 + j]);
                    infoLayout.addView(secondary[i * 2 + j]);

                    contentCell.addView(infoLayout);
                    contentCell.addView(title[i * 2 + j]);
                    linearLayout.addView(contentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));
                }
                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 16));
            }
        }

        public void setData(OverviewChannelData data, TLRPC.ChatFull chatFull) {
            int k = 0;
            for (int i = 0; i < primary.length; i++) {
                switch (i) {
                    case 0:
                        primary[k].setText(data.followersPrimary);
                        secondary[k].setText(data.followersSecondary);
                        secondary[k].setTag(data.followersUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.followersTitle);
                        k++;
                        break;
                    case 1:
                        primary[k].setText(data.notificationsPrimary);
                        secondary[k].setText("");
                        title[k].setText(data.notificationsTitle);
                        k++;
                        break;
                    case 2:
                        primary[k].setText(data.viewsPrimary);
                        secondary[k].setText(data.viewsSecondary);
                        secondary[k].setTag(data.viewsUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.viewsTitle);
                        k++;
                        break;
                    case 3:
                        primary[k].setText(data.viewsPerStoryPrimary);
                        secondary[k].setText(data.viewsPerStorySecondary);
                        secondary[k].setTag(data.viewsPerStoryUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.viewsPerStoryTitle);
                        if (data.viewsPerStoryVisible) {
                            k++;
                        }
                        break;
                    case 4:
                        primary[k].setText(data.sharesPrimary);
                        secondary[k].setText(data.sharesSecondary);
                        secondary[k].setTag(data.sharesUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.sharesTitle);
                        k++;
                        break;
                    case 5:
                        primary[k].setText(data.sharesPerStoryPrimary);
                        secondary[k].setText(data.sharesPerStorySecondary);
                        secondary[k].setTag(data.sharesPerStoryUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.sharesPerStoryTitle);
                        if (data.sharesPerStoryVisible) {
                            k++;
                        }
                        break;
                    case 6:
                        primary[k].setText(data.reactionsPerPostPrimary);
                        secondary[k].setText(data.reactionsPerPostSecondary);
                        secondary[k].setTag(data.reactionsPerPostUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.reactionsPerPostTitle);
                        if (data.reactionsPerPostVisible) {
                            k++;
                        }
                        break;
                    case 7:
                        primary[k].setText(data.reactionsPerStoryPrimary);
                        secondary[k].setText(data.reactionsPerStorySecondary);
                        secondary[k].setTag(data.reactionsPerStoryUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
                        title[k].setText(data.reactionsPerStoryTitle);
                        if (data.reactionsPerStoryVisible) {
                            k++;
                        }
                        break;
                }
            }
            for (int i = k; i < primary.length; i++) {
                ((ViewGroup) title[i].getParent()).setVisibility(GONE);
            }
            for (int i = 0; i < getChildCount(); i++) {
                ViewGroup viewGroup = (ViewGroup) getChildAt(i);
                if (viewGroup.getChildAt(0).getVisibility() == GONE && viewGroup.getChildAt(1).getVisibility() == GONE) {
                    viewGroup.setVisibility(GONE);
                }
            }
            updateColors();
        }

        public void setData(OverviewChatData data) {
            primary[0].setText(data.membersPrimary);
            primary[1].setText(data.messagesPrimary);
            primary[2].setText(data.viewingMembersPrimary);
            primary[3].setText(data.postingMembersPrimary);

            secondary[0].setText(data.membersSecondary);
            secondary[0].setTag(data.membersUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);

            secondary[1].setText(data.messagesSecondary);
            secondary[1].setTag(data.messagesUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);

            secondary[2].setText(data.viewingMembersSecondary);
            secondary[2].setTag(data.viewingMembersUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);
            secondary[3].setText(data.postingMembersSecondary);
            secondary[3].setTag(data.postingMembersUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_text_RedRegular);

            title[0].setText(data.membersTitle);
            title[1].setText(data.messagesTitle);
            title[2].setText(data.viewingMembersTitle);
            title[3].setText(data.postingMembersTitle);

            updateColors();
        }

        public void setData(int index, String primary, String secondary, String title) {
            this.primary[index].setText(primary);
            this.secondary[index].setText(secondary);
            this.title[index].setText(title);

            updateColors();
        }

        private void updateColors() {
            for (int i = 0; i < primary.length; i++) {
                primary[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));

                Integer colorKey = (Integer) secondary[i].getTag();
                if (colorKey != null) {
                    secondary[i].setTextColor(Theme.getColor(colorKey));
                } else {
                    secondary[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                }
            }
        }
    }

    public static class MemberData {
        public TLRPC.User user;
        long user_id;
        public String description;

        public static MemberData from(TL_stats.TL_statsGroupTopPoster poster, ArrayList<TLRPC.User> users) {
            MemberData data = new MemberData();
            data.user_id = poster.user_id;
            data.user = find(data.user_id, users);
            StringBuilder stringBuilder = new StringBuilder();
            if (poster.messages > 0) {
                stringBuilder.append(LocaleController.formatPluralString("messages", poster.messages));
            }
            if (poster.avg_chars > 0) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(LocaleController.formatString("CharactersPerMessage", R.string.CharactersPerMessage, LocaleController.formatPluralString("Characters", poster.avg_chars)));
            }
            data.description = stringBuilder.toString();
            return data;
        }

        public static MemberData from(TL_stats.TL_statsGroupTopAdmin admin, ArrayList<TLRPC.User> users) {
            MemberData data = new MemberData();
            data.user_id = admin.user_id;
            data.user = find(data.user_id, users);
            StringBuilder stringBuilder = new StringBuilder();
            if (admin.deleted > 0) {
                stringBuilder.append(LocaleController.formatPluralString("Deletions", admin.deleted));
            }
            if (admin.banned > 0) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(LocaleController.formatPluralString("Bans", admin.banned));
            }
            if (admin.kicked > 0) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(LocaleController.formatPluralString("Restrictions", admin.kicked));
            }
            data.description = stringBuilder.toString();
            return data;
        }

        public static MemberData from(TL_stats.TL_statsGroupTopInviter inviter, ArrayList<TLRPC.User> users) {
            MemberData data = new MemberData();
            data.user_id = inviter.user_id;
            data.user = find(data.user_id, users);
            if (inviter.invitations > 0) {
                data.description = LocaleController.formatPluralString("Invitations", inviter.invitations);
            } else {
                data.description = "";
            }
            return data;
        }

        public static TLRPC.User find(long user_id, ArrayList<TLRPC.User> users) {
            for (TLRPC.User user : users) {
                if (user.id == user_id) {
                    return user;
                }
            }
            return null;
        }

        public void onClick(BaseFragment fragment) {
            Bundle bundle = new Bundle();
            bundle.putLong("user_id", user.id);
            MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
            fragment.presentFragment(new ProfileActivity(bundle));
        }

        public void onLongClick(TLRPC.ChatFull chat, StatisticActivity fragment, AlertDialog[] progressDialog) {
            onLongClick(chat, fragment, progressDialog, true);
        }

        private void onLongClick(TLRPC.ChatFull chat, StatisticActivity fragment, AlertDialog[] progressDialog, boolean userIsPracticant) {
            MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);

            final ArrayList<String> items = new ArrayList<>();
            final ArrayList<Integer> actions = new ArrayList<>();
            final ArrayList<Integer> icons = new ArrayList<>();

            TLRPC.TL_chatChannelParticipant currentParticipant = null;
            TLRPC.TL_chatChannelParticipant currentUser = null;

            if (userIsPracticant && chat.participants.participants != null) {
                int n = chat.participants.participants.size();
                for (int i = 0; i < n; i++) {
                    TLRPC.ChatParticipant participant = chat.participants.participants.get(i);
                    if (participant.user_id == user.id) {
                        if (participant instanceof TLRPC.TL_chatChannelParticipant) {
                            currentParticipant = (TLRPC.TL_chatChannelParticipant) participant;
                        }
                    }
                    if (participant.user_id == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
                        if (participant instanceof TLRPC.TL_chatChannelParticipant) {
                            currentUser = (TLRPC.TL_chatChannelParticipant) participant;
                        }
                    }
                }
            }

            items.add(getString("StatisticOpenProfile", R.string.StatisticOpenProfile));
            icons.add(R.drawable.msg_openprofile);
            actions.add(2);
            items.add(getString("StatisticSearchUserHistory", R.string.StatisticSearchUserHistory));
            icons.add(R.drawable.msg_msgbubble3);
            actions.add(1);

            if (userIsPracticant && currentParticipant == null) {
                if (progressDialog[0] == null) {
                    progressDialog[0] = new AlertDialog(fragment.getFragmentView().getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog[0].showDelayed(300);
                }
                TLRPC.TL_channels_getParticipant request = new TLRPC.TL_channels_getParticipant();
                request.channel = MessagesController.getInstance(UserConfig.selectedAccount).getInputChannel(chat.id);
                request.participant = MessagesController.getInputPeer(user);
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (fragment.isFinishing() || fragment.getFragmentView() == null) {
                        return;
                    }
                    if (progressDialog[0] == null) {
                        return;
                    }
                    if (error == null) {
                        TLRPC.TL_channels_channelParticipant participant = (TLRPC.TL_channels_channelParticipant) response;
                        TLRPC.TL_chatChannelParticipant chatChannelParticipant = new TLRPC.TL_chatChannelParticipant();
                        chatChannelParticipant.channelParticipant = participant.participant;
                        chatChannelParticipant.user_id = user.id;
                        chat.participants.participants.add(0, chatChannelParticipant);
                        onLongClick(chat, fragment, progressDialog);
                    } else {
                        onLongClick(chat, fragment, progressDialog, false);
                    }
                }));
                return;
            }

            if (userIsPracticant && currentUser == null) {
                if (progressDialog[0] == null) {
                    progressDialog[0] = new AlertDialog(fragment.getFragmentView().getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog[0].showDelayed(300);
                }
                TLRPC.TL_channels_getParticipant request = new TLRPC.TL_channels_getParticipant();
                request.channel = MessagesController.getInstance(UserConfig.selectedAccount).getInputChannel(chat.id);
                request.participant = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(UserConfig.getInstance(UserConfig.selectedAccount).clientUserId);
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (fragment.isFinishing() || fragment.getFragmentView() == null) {
                        return;
                    }
                    if (progressDialog[0] == null) {
                        return;
                    }
                    if (error == null) {
                        TLRPC.TL_channels_channelParticipant participant = (TLRPC.TL_channels_channelParticipant) response;
                        TLRPC.TL_chatChannelParticipant chatChannelParticipant = new TLRPC.TL_chatChannelParticipant();
                        chatChannelParticipant.channelParticipant = participant.participant;
                        chatChannelParticipant.user_id = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
                        chat.participants.participants.add(0, chatChannelParticipant);
                        onLongClick(chat, fragment, progressDialog);
                    } else {
                        onLongClick(chat, fragment, progressDialog, false);
                    }
                }));
                return;
            }

            if (progressDialog[0] != null) {
                progressDialog[0].dismiss();
                progressDialog[0] = null;
            }

            boolean isAdmin = false;
            if (currentUser != null && currentParticipant != null && currentUser.user_id != currentParticipant.user_id) {
                TLRPC.ChannelParticipant channelParticipant = currentParticipant.channelParticipant;
                boolean canEditAdmin = currentUser.channelParticipant.admin_rights != null && currentUser.channelParticipant.admin_rights.add_admins;
                if (canEditAdmin && (channelParticipant instanceof TLRPC.TL_channelParticipantCreator || channelParticipant instanceof TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
                    canEditAdmin = false;
                }
                if (canEditAdmin) {
                    isAdmin = channelParticipant.admin_rights == null;
                    items.add(isAdmin ? getString("SetAsAdmin", R.string.SetAsAdmin) : getString("EditAdminRights", R.string.EditAdminRights));
                    icons.add(isAdmin ? R.drawable.msg_admins : R.drawable.msg_permissions);
                    actions.add(0);
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            TLRPC.TL_chatChannelParticipant finalCurrentParticipant = currentParticipant;
            boolean finalIsAdmin = isAdmin;
            builder.setItems(items.toArray(new CharSequence[actions.size()]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                if (actions.get(i) == 0) {
                    boolean[] needShowBulletin = new boolean[1];
                    ChatRightsEditActivity newFragment = new ChatRightsEditActivity(user.id, chat.id, finalCurrentParticipant.channelParticipant.admin_rights, null, finalCurrentParticipant.channelParticipant.banned_rights, finalCurrentParticipant.channelParticipant.rank, ChatRightsEditActivity.TYPE_ADMIN, true, finalIsAdmin, null) {
                        @Override
                        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                            if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(fragment)) {
                                BulletinFactory.createPromoteToAdminBulletin(fragment, user.first_name).show();
                            }
                        }
                    };
                    newFragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                        @Override
                        public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                            if (rights == 0) {
                                finalCurrentParticipant.channelParticipant.admin_rights = null;
                                finalCurrentParticipant.channelParticipant.rank = "";
                            } else {
                                finalCurrentParticipant.channelParticipant.admin_rights = rightsAdmin;
                                finalCurrentParticipant.channelParticipant.rank = rank;
                                if (finalIsAdmin) {
                                    needShowBulletin[0] = true;
                                }
                            }
                        }

                        @Override
                        public void didChangeOwner(TLRPC.User user) {

                        }
                    });
                    fragment.presentFragment(newFragment);
                } else if (actions.get(i) == 2) {
                    onClick(fragment);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putLong("chat_id", chat.id);
                    bundle.putLong("search_from_user_id", user.id);
                    fragment.presentFragment(new ChatActivity(bundle));
                }
            });
            AlertDialog alertDialog = builder.create();
            fragment.showDialog(alertDialog);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (viewPagerFixed != null && (viewPagerFixed.currentPosition != 0 || viewPagerFixed.currentProgress != 1f)) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }
}
