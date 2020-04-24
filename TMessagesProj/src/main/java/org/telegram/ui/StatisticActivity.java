package org.telegram.ui;

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
import android.view.Gravity;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LruCache;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StatisticPostInfoCell;
import org.telegram.ui.Charts.BarChartView;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Charts.DoubleLinearChartView;
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
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlatCheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class StatisticActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final TLRPC.ChatFull chat;

    private OverviewData overviewData;
    private ChartViewData growthData;
    private ChartViewData followersData;
    private ChartViewData topHoursData;
    private ChartViewData interactionsData;
    private ChartViewData ivInteractionsData;
    private ChartViewData viewsBySourceData;
    private ChartViewData newFollowersBySourceData;
    private ChartViewData languagesData;
    private ChartViewData notificationsData;

    ChatAvatarContainer avatarContainer;

    private RecyclerListView recyclerListView;
    private LinearLayoutManager layoutManager;
    private LruCache<ChartData> childDataCache = new LruCache<>(50);
    private RLottieImageView imageView;

    private Adapter adapter;
    private ZoomCancelable lastCancelable;

    private BaseChartView.SharedUiComponents sharedUi;
    private LinearLayout progressLayout;

    public StatisticActivity(Bundle args) {
        super(args);
        int chatId = args.getInt("chat_id");
        this.chat = getMessagesController().getChatFull(chatId);
    }


    private int loadFromId = -1;
    private final SparseIntArray recentPostIdtoIndexMap = new SparseIntArray();
    private final ArrayList<RecentPostInfo> recentPostsAll = new ArrayList<>();
    private final ArrayList<RecentPostInfo> recentPostsLoaded = new ArrayList<>();
    private boolean messagesIsLoading;
    private boolean initialLoading = true;
    private DiffUtilsCallback diffUtilsCallback;

    private final Runnable showProgressbar = new Runnable() {
        @Override
        public void run() {
            progressLayout.animate().alpha(1f).setDuration(230);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);

        TLRPC.TL_stats_getBroadcastStats req = new TLRPC.TL_stats_getBroadcastStats();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            final ChartViewData[] chartsViewData = new ChartViewData[9];

            if (response instanceof TLRPC.TL_stats_broadcastStats) {
                TLRPC.TL_stats_broadcastStats stats = (TLRPC.TL_stats_broadcastStats) response;

                chartsViewData[0] = createViewData(stats.iv_interactions_graph, LocaleController.getString("IVInteractionsChartTitle", R.string.IVInteractionsChartTitle), 1);
                chartsViewData[1] = createViewData(stats.followers_graph, LocaleController.getString("FollowersChartTitle", R.string.FollowersChartTitle), 0);
                chartsViewData[2] = createViewData(stats.top_hours_graph, LocaleController.getString("TopHoursChartTitle", R.string.TopHoursChartTitle), 0);
                chartsViewData[3] = createViewData(stats.interactions_graph, LocaleController.getString("InteractionsChartTitle", R.string.InteractionsChartTitle), 1);
                chartsViewData[4] = createViewData(stats.growth_graph, LocaleController.getString("GrowthChartTitle", R.string.GrowthChartTitle), 0);
                chartsViewData[5] = createViewData(stats.views_by_source_graph, LocaleController.getString("ViewsBySourceChartTitle", R.string.ViewsBySourceChartTitle), 2);
                chartsViewData[6] = createViewData(stats.new_followers_by_source_graph, LocaleController.getString("NewFollowersBySourceChartTitle", R.string.NewFollowersBySourceChartTitle), 2);
                chartsViewData[7] = createViewData(stats.languages_graph, LocaleController.getString("LanguagesChartTitle", R.string.LanguagesChartTitle), 4);
                chartsViewData[8] = createViewData(stats.mute_graph, LocaleController.getString("NotificationsChartTitle", R.string.NotificationsChartTitle), 0);

                overviewData = new OverviewData(stats);

                recentPostsAll.clear();
                for (int i = 0; i < stats.recent_message_interactions.size(); i++) {
                    RecentPostInfo recentPostInfo = new RecentPostInfo();
                    recentPostInfo.counters = stats.recent_message_interactions.get(i);
                    recentPostsAll.add(recentPostInfo);
                    recentPostIdtoIndexMap.put(recentPostInfo.counters.msg_id, i);
                }

                if (recentPostsAll.size() > 0) {
                    int lastPostId = recentPostsAll.get(0).counters.msg_id;
                    int count = recentPostsAll.size();
                    getMessagesStorage().getMessages(-chat.id, count, lastPostId, 0, 0, classGuid, 0, true, false, 0);
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

                    if (adapter != null) {
                        adapter.update();
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
                                data.load(currentAccount, classGuid, chat.stats_dc, recyclerListView, adapter, diffUtilsCallback);
                            }
                        }
                    }
                });

            }
        }, null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);

        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                ArrayList<RecentPostInfo> deletedMessages = new ArrayList<>();
                int n = messArr.size();
                for (int i = 0; i < n; i++) {
                    MessageObject messageObjectFormCache = messArr.get(i);
                    int index = recentPostIdtoIndexMap.get(messageObjectFormCache.getId(), -1);
                    if (index >= 0 && recentPostsAll.get(index).counters.msg_id == messageObjectFormCache.getId()) {
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
                        loadFromId = postInfo.counters.msg_id;
                        break;
                    } else {
                        recentPostsLoaded.add(postInfo);
                    }
                }

                if (recentPostsLoaded.size() < 20) {
                    loadMessages();
                }
                if (adapter != null) {
                    diffUtilsCallback.update();
                }
            }
        }
    }

    @Override
    public View createView(Context context) {
        sharedUi = new BaseChartView.SharedUiComponents();
        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
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
        loadingTitle.setTextSize(20);
        loadingTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        loadingTitle.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        loadingTitle.setTag(Theme.key_player_actionBarTitle);
        loadingTitle.setText(LocaleController.getString("LoadingStats", R.string.LoadingStats));
        loadingTitle.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView loadingSubtitle = new TextView(context);
        loadingSubtitle.setTextSize(15);
        loadingSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        loadingSubtitle.setTag(Theme.key_player_actionBarSubtitle);
        loadingSubtitle.setText(LocaleController.getString("LoadingStatsDescription", R.string.LoadingStatsDescription));
        loadingSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);

        progressLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        progressLayout.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        progressLayout.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        frameLayout.addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));


        if (adapter == null) {
            adapter = new Adapter();
        }
        recyclerListView.setAdapter(adapter);
        layoutManager = new LinearLayoutManager(context);
        recyclerListView.setLayoutManager(layoutManager);
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
                MessageObject messageObject = recentPostsLoaded.get(position - adapter.recentPostsStartRow).message;

                Bundle bundle = new Bundle();
                bundle.putInt("chat_id", chat.id);
                bundle.putInt("message_id", messageObject.getId());
                bundle.putBoolean("need_remove_previous_same_chat_activity", false);
                ChatActivity chatActivity = new ChatActivity(bundle);
                presentFragment(chatActivity, false);
            }

        });
        frameLayout.addView(recyclerListView);

        avatarContainer = new ChatAvatarContainer(context, null, false);
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 40, 0));

        TLRPC.Chat chatLocal = getMessagesController().getChat(chat.id);

        avatarContainer.setChatAvatar(chatLocal);
        avatarContainer.setTitle(chatLocal.title);
        avatarContainer.setSubtitle(LocaleController.getString("Statistics", R.string.Statistics));

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
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
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

    private ChartViewData createViewData(TLRPC.StatsGraph graph, String title, int graphType) {
        if (graph == null || graph instanceof TLRPC.TL_statsGraphError) {
            return null;
        }
        ChartViewData viewData = new ChartViewData(title, graphType);
        if (graph instanceof TLRPC.TL_statsGraph) {
            String json = ((TLRPC.TL_statsGraph) graph).json.data;
            try {
                viewData.chartData = createChartData(new JSONObject(json), graphType);
                viewData.zoomToken = ((TLRPC.TL_statsGraph) graph).zoom_token;
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
        } else if (graph instanceof TLRPC.TL_statsGraphAsync) {
            viewData.token = ((TLRPC.TL_statsGraphAsync) graph).token;
        }

        return viewData;
    }

    private static ChartData createChartData(JSONObject jsonObject, int graphType) throws JSONException {
        if (graphType == 0) {
            return new ChartData(jsonObject);
        } else if (graphType == 1) {
            return new DoubleLinearChartData(jsonObject);
        } else if (graphType == 2) {
            return new StackBarChartData(jsonObject);
        } else if (graphType == 4) {
            return new StackLinearChartData(jsonObject);
        }
        return null;
    }

    class Adapter extends RecyclerListView.SelectionAdapter {

        int overviewHeaderCell = -1;
        int overviewCell;
        int growCell = -1;
        int folowersCell = -1;
        int topHourseCell = -1;
        int interactionsCell = -1;
        int ivInteractionsCell = -1;
        int viewsBySourceCell = -1;
        int newFollowersBySourceCell = -1;
        int languagesCell = -1;
        int notificationsCell = -1;

        int recentPostsHeaderCell = -1;
        int recentPostsStartRow = -1;
        int recentPostsEndRow = -1;
        int progressCell = -1;
        int emptyCell = -1;

        ArraySet<Integer> shadowDivideCells = new ArraySet<>();

        int count;

        @Override
        public int getItemViewType(int position) {
            if (position == growCell || position == folowersCell || position == topHourseCell || position == notificationsCell) {
                return 0;
            } else if (position == interactionsCell || position == ivInteractionsCell) {
                return 1;
            } else if (position == viewsBySourceCell || position == newFollowersBySourceCell) {
                return 2;
            } else if (position == languagesCell) {
                return 4;
            } else if (position >= recentPostsStartRow && position <= recentPostsEndRow) {
                return 9;
            } else if (position == progressCell) {
                return 11;
            } else if (position == emptyCell) {
                return 12;
            } else if (position == recentPostsHeaderCell || position == overviewHeaderCell) {
                return 13;
            } else if (position == overviewCell) {
                return 14;
            } else {
                return 10;
            }
        }

        @Override
        public long getItemId(int position) {
            if (position >= recentPostsStartRow && position <= recentPostsEndRow) {
                return recentPostsLoaded.get(position - recentPostsStartRow).counters.msg_id;
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
            }
            return super.getItemId(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v;
            if (viewType >= 0 && viewType <= 4) {
                v = new ChartCell(parent.getContext(), viewType) {
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
                v = new StatisticPostInfoCell(parent.getContext(), chat) {
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
                v = new EmptyCell(parent.getContext(), AndroidUtilities.dp(15));
            } else if (viewType == 13) {
                HeaderCell headerCell = new HeaderCell(parent.getContext(), Theme.key_dialogTextBlack, 16, 15, false) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (getTranslationY() != 0) {
                            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        }
                        super.onDraw(canvas);
                    }
                };
                headerCell.setWillNotDraw(false);
                headerCell.setPadding(headerCell.getPaddingLeft(), headerCell.getTop(), headerCell.getRight(), AndroidUtilities.dp(8));
                v = headerCell;
            } else if (viewType == 14) {
                v = new OverviewCell(parent.getContext());
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
                } else {
                    data = languagesData;
                }
                ((ChartCell) holder.itemView).updateData(data, false);
            } else if (type == 9) {
                int i = position - recentPostsStartRow;
                ((StatisticPostInfoCell) holder.itemView).setData(recentPostsLoaded.get(i));
            } else if (type == 13) {
                HeaderCell headerCell = (HeaderCell) holder.itemView;
                if (position == overviewHeaderCell) {
                    headerCell.setText(LocaleController.getString("StatisticOverview", R.string.StatisticOverview));
                } else {
                    headerCell.setText(LocaleController.getString("RecentPosts", R.string.RecentPosts));
                }
            } else if (type == 14) {
                OverviewCell overviewCell = (OverviewCell) holder.itemView;
                overviewCell.setData(overviewData);
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
            emptyCell = -1;
            recentPostsHeaderCell = -1;
            ivInteractionsCell = -1;
            topHourseCell = -1;
            notificationsCell = -1;

            count = 0;
            shadowDivideCells.clear();

            if (overviewData != null) {
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

            shadowDivideCells.add(count++);

            if (recentPostsAll.size() > 0) {
                recentPostsHeaderCell = count++;
                recentPostsStartRow = count++;
                count = recentPostsEndRow = recentPostsStartRow + recentPostsLoaded.size() - 1;
                count++;

                if (recentPostsLoaded.size() != recentPostsAll.size()) {
                    progressCell = count++;
                } else {
                    emptyCell = count++;
                }

                shadowDivideCells.add(count++);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 9;
        }
    }

    public class ChartCell extends FrameLayout {
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
        public ChartCell(@NonNull Context context, int type) {
            super(context);
            setWillNotDraw(false);
            chartType = type;
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            checkboxContainer = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    int currentW = 0;
                    int currentH = 0;
                    int n = getChildCount();
                    int firstH = n > 0 ? getChildAt(0).getMeasuredHeight() : 0;
                    for (int i = 0; i < n; i++) {
                        if (currentW + getChildAt(i).getMeasuredWidth() > getMeasuredWidth()) {
                            currentW = 0;
                            currentH += getChildAt(i).getMeasuredHeight();
                        }
                        currentW += getChildAt(i).getMeasuredWidth();
                    }
                    setMeasuredDimension(getMeasuredWidth(), firstH + currentH + AndroidUtilities.dp(16));
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
            chartHeaderView = new ChartHeaderView(getContext());
            chartHeaderView.back.setOnTouchListener(new RecyclerListView.FoucsableOnTouchListener());
            chartHeaderView.back.setOnClickListener(v -> zoomOut(true));

            switch (type) {
                case 1:
                    chartView = new DoubleLinearChartView(getContext());
                    zoomedChartView = new DoubleLinearChartView(getContext());
                    zoomedChartView.legendSignatureView.useHour = true;
                    break;
                case 2:
                    chartView = new StackBarChartView(getContext());
                    zoomedChartView = new StackBarChartView(getContext());
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
            errorTextView.setTextSize(15);
            frameLayout.addView(errorTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));
            progressView.setVisibility(View.GONE);

            errorTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));


            chartView.setDateSelectionListener(date -> {
                cancelZoom();
                chartView.legendSignatureView.showProgress(false, false);
            });

            chartView.legendSignatureView.showProgress(false, false);
            chartView.legendSignatureView.setOnTouchListener(new RecyclerListView.FoucsableOnTouchListener());
            chartView.legendSignatureView.setOnClickListener(v -> {
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

                TLRPC.TL_stats_loadAsyncGraph request = new TLRPC.TL_stats_loadAsyncGraph();
                request.token = data.zoomToken;
                if (x != 0) {
                    request.x = x;
                    request.flags |= 1;
                }
                ZoomCancelable finalCancelabel;
                lastCancelable = finalCancelabel = new ZoomCancelable();
                finalCancelabel.adapterPosition = recyclerListView.getChildAdapterPosition(ChartCell.this);

                chartView.legendSignatureView.showProgress(true, false);

                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                    ChartData childData = null;
                    if (response instanceof TLRPC.TL_statsGraph) {
                        String json = ((TLRPC.TL_statsGraph) response).json.data;
                        try {
                            childData = createChartData(new JSONObject(json), data.graphType);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (response instanceof TLRPC.TL_statsGraphError) {
                        Toast.makeText(context, ((TLRPC.TL_statsGraphError) response).error, Toast.LENGTH_LONG).show();
                    }

                    ChartData finalChildData = childData;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (finalChildData != null) {
                            childDataCache.put(cacheKey, finalChildData);
                        }
                        if (finalChildData != null && !finalCancelabel.canceled && finalCancelabel.adapterPosition >= 0) {
                            View view = layoutManager.findViewByPosition(finalCancelabel.adapterPosition);
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

            });
            zoomedChartView.legendSignatureView.setOnClickListener(v -> {
                zoomedChartView.animateLegend(false);
            });
            chartView.setVisibility(VISIBLE);
            zoomedChartView.setVisibility(INVISIBLE);
            chartView.setHeader(chartHeaderView);

            linearLayout.addView(chartHeaderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52));
            linearLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            linearLayout.addView(checkboxContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 16, 0, 16, 0));

            if (chartType == 4) {
                frameLayout.setClipChildren(false);
                frameLayout.setClipToPadding(false);
                linearLayout.setClipChildren(false);
                linearLayout.setClipToPadding(false);
            }

            addView(linearLayout);
        }

        private void zoomChart(boolean skipTransition) {
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

                        chartView.legendShowing = true;
                        chartView.moveLegend();
                        chartView.animateLegend(true);
                        chartView.invalidate();
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
            params.xPercentage = data.chartData.xPercentage[dateIndex];


            zoomedChartView.setVisibility(VISIBLE);
            zoomedChartView.transitionParams = params;
            chartView.transitionParams = params;

            int max = 0;
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < data.chartData.lines.size(); i++) {
                if (data.chartData.lines.get(i).y[dateIndex] > max)
                    max = data.chartData.lines.get(i).y[dateIndex];
                if (data.chartData.lines.get(i).y[dateIndex] < min)
                    min = data.chartData.lines.get(i).y[dateIndex];
            }
            final float pYPercentage = (((float) min + (max - min)) - chartView.currentMinHeight) / (chartView.currentMaxHeight - chartView.currentMinHeight);


            ValueAnimator animator = ValueAnimator.ofFloat(in ? 0f : 1f, in ? 1f : 0f);
            animator.addUpdateListener(animation -> {
                float fullWidth = (chartView.chartWidth / (chartView.pickerDelegate.pickerEnd - chartView.pickerDelegate.pickerStart));
                float offset = fullWidth * (chartView.pickerDelegate.pickerStart) - chartView.HORIZONTAL_PADDING;

                params.pY = chartView.chartArea.top + (1f - pYPercentage) * chartView.chartArea.height();
                params.pX = chartView.chartFullWidth * params.xPercentage - offset;

                params.progress = (float) animation.getAnimatedValue();
                zoomedChartView.invalidate();
                chartView.invalidate();
            });

            animator.setDuration(chartType == 4 ? 600 : 400);
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
                chartView.setData(null);
                return;
            }

            errorTextView.setVisibility(View.GONE);
            chartView.legendSignatureView.isTopHourChart = viewData == topHoursData;
            chartHeaderView.showDate(viewData != topHoursData);

            if (viewData.chartData == null && viewData.token != null) {
                progressView.setAlpha(1f);
                progressView.setVisibility(View.VISIBLE);
                viewData.load(currentAccount, classGuid, chat.stats_dc, recyclerListView, adapter, diffUtilsCallback);
                return;
            } else if (!enterTransition) {
                progressView.setVisibility(View.GONE);
            }

            chartView.setData(viewData.chartData);

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
                    if (data.chartData.lines.get(i).colorKey != null && Theme.hasThemeKey(data.chartData.lines.get(i).colorKey)) {
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

                checkBox.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
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
                    cancelZoom();
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
                    cancelZoom();
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


    private static class ZoomCancelable {
        int adapterPosition;
        boolean canceled;
    }

    private static class ChartViewData {

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

        public ChartViewData(String title, int grahType) {
            this.title = title;
            this.graphType = grahType;
        }

        public void load(int accountId, int classGuid, int dc, RecyclerListView recyclerListView, Adapter adapter, DiffUtilsCallback difCallback) {
            if (!loading) {
                loading = true;
                TLRPC.TL_stats_loadAsyncGraph request = new TLRPC.TL_stats_loadAsyncGraph();
                request.token = token;
                int reqId = ConnectionsManager.getInstance(accountId).sendRequest(request, (response, error) -> {
                    ChartData chartData = null;
                    String zoomToken = null;
                    if (error == null) {
                        if (response instanceof TLRPC.TL_statsGraph) {
                            String json = ((TLRPC.TL_statsGraph) response).json.data;
                            try {
                                chartData = createChartData(new JSONObject(json), graphType);
                                zoomToken = ((TLRPC.TL_statsGraph) response).zoom_token;
                                if (graphType == 4 && chartData.x != null && chartData.x.length > 0) {
                                    long x = chartData.x[chartData.x.length - 1];
                                    childChartData = new StackLinearChartData(chartData, x);
                                    activeZoom = x;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        if (response instanceof TLRPC.TL_statsGraphError) {
                            isEmpty = false;
                            isError = true;
                            errorMessage = ((TLRPC.TL_statsGraphError) response).error;
                        }
                    }

                    ChartData finalChartData = chartData;
                    String finalZoomToken = zoomToken;
                    AndroidUtilities.runOnUIThread(() -> {
                        loading = false;
                        this.chartData = finalChartData;
                        this.zoomToken = finalZoomToken;

                        int n = recyclerListView.getChildCount();
                        boolean found = false;
                        for (int i = 0; i < n; i++) {
                            View child = recyclerListView.getChildAt(i);
                            if (child instanceof ChartCell && ((ChartCell) child).data == this) {
                                ((ChartCell) child).updateData(this, true);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            difCallback.update();
                        }
                    });
                }, null, null, 0, dc, ConnectionsManager.ConnectionTypeGeneric, true);
                ConnectionsManager.getInstance(accountId).bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public static class RecentPostInfo {
        public TLRPC.TL_messageInteractionCounters counters;
        public MessageObject message;
    }

    private void loadMessages() {
        TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
        req.id = new ArrayList<>();
        int index = recentPostIdtoIndexMap.get(loadFromId);
        int n = recentPostsAll.size();
        int count = 0;
        for (int i = index; i < n; i++) {
            if (recentPostsAll.get(i).message == null) {
                req.id.add(recentPostsAll.get(i).counters.msg_id);
                count++;
                if (count > 50) {
                    break;
                }
            }
        }
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
        messagesIsLoading = true;

        getConnectionsManager().sendRequest(req, (response, error) -> {
            final ArrayList<MessageObject> messageObjects = new ArrayList<>();

            if (response instanceof TLRPC.messages_Messages) {
                ArrayList<TLRPC.Message> messages = ((TLRPC.messages_Messages) response).messages;
                for (int i = 0; i < messages.size(); i++) {
                    messageObjects.add(new MessageObject(currentAccount, messages.get(i), false));
                }
                getMessagesStorage().putMessages(messages, false, true, true, 0, false);
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
                    if (localIndex >= 0 && recentPostsAll.get(localIndex).counters.msg_id == messageObjectFormCache.getId()) {
                        recentPostsAll.get(localIndex).message = messageObjectFormCache;
                    }
                }

                recentPostsLoaded.clear();
                size = recentPostsAll.size();
                for (int i = 0; i < size; i++) {
                    RecentPostInfo postInfo = recentPostsAll.get(i);
                    if (postInfo.message == null) {
                        loadFromId = postInfo.counters.msg_id;
                        break;
                    } else {
                        recentPostsLoaded.add(postInfo);
                    }
                }
                diffUtilsCallback.update();
            });
        });
    }

    private void recolorRecyclerItem(View child) {
        if (child instanceof ChartCell) {
            ((ChartCell) child).recolor();
        }

        if (child instanceof ShadowSectionCell) {
            Drawable shadowDrawable = Theme.getThemedDrawable(ApplicationLoader.applicationContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
            Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
            CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
            combinedDrawable.setFullsize(true);
            child.setBackground(combinedDrawable);
        }

        if (child instanceof OverviewCell) {
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
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"shares"}, null, null, null, Theme.key_dialogTextGray4));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{StatisticPostInfoCell.class}, new String[]{"date"}, null, null, null, Theme.key_dialogTextGray4));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlack));

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
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundWhiteRedText5));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        arrayList.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getSubtitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_player_actionBarSubtitle, null));


        for (int i = 0; i < 9; i++) {
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
            } else {
                chartViewData = languagesData;
            }

            if (chartViewData != null && chartViewData.chartData != null) {
                for (ChartData.Line l : chartViewData.chartData.lines) {
                    if (l.colorKey != null) {
                        if (!Theme.hasThemeKey(l.colorKey)) {
                            Theme.setColor(l.colorKey, Theme.isCurrentThemeNight() ? l.colorDark : l.color, false);
                            Theme.setDefaultColor(l.colorKey, l.color);
                        }
                        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, l.colorKey));
                    }
                }
            }
        }

        return arrayList;
    }

    public static class OverviewData {

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

        public OverviewData(TLRPC.TL_stats_broadcastStats stats) {
            int dif = (int) (stats.followers.current - stats.followers.previous);
            float difPercent = Math.abs(dif / (float) stats.followers.previous * 100f);
            followersTitle = LocaleController.getString("FollowersChartTitle", R.string.FollowersChartTitle);
            followersPrimary = AndroidUtilities.formatWholeNumber((int) stats.followers.current, 0);

            if (dif == 0) {
                followersSecondary = "";
            } else if (difPercent == (int) difPercent) {
                followersSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                followersSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            followersUp = dif >= 0;

            dif = (int) (stats.shares_per_post.current - stats.shares_per_post.previous);
            difPercent = Math.abs(dif / (float) stats.shares_per_post.previous * 100f);
            sharesTitle = LocaleController.getString("SharesPerPost", R.string.SharesPerPost);
            sharesPrimary = AndroidUtilities.formatWholeNumber((int) stats.shares_per_post.current, 0);

            if (dif == 0) {
                sharesSecondary = "";
            } else if (difPercent == (int) difPercent) {
                sharesSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                sharesSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            sharesUp = dif >= 0;

            dif = (int) (stats.views_per_post.current - stats.views_per_post.previous);
            difPercent = Math.abs(dif / (float) stats.views_per_post.previous * 100f);
            viewsTitle = LocaleController.getString("ViewsPerPost", R.string.ViewsPerPost);
            viewsPrimary = AndroidUtilities.formatWholeNumber((int) stats.views_per_post.current, 0);
            if (dif == 0) {
                viewsSecondary = "";
            } else if (difPercent == (int) difPercent) {
                viewsSecondary = String.format(Locale.ENGLISH, "%s (%d%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), (int) difPercent, "%");
            } else {
                viewsSecondary = String.format(Locale.ENGLISH, "%s (%.1f%s)", (dif > 0 ? "+" : "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%");
            }
            viewsUp = dif >= 0;

            difPercent = (float) (stats.enabled_notifications.part / stats.enabled_notifications.total * 100f);
            notificationsTitle = LocaleController.getString("EnabledNotifications", R.string.EnabledNotifications);
            if (difPercent == (int) difPercent) {
                notificationsPrimary = String.format(Locale.ENGLISH, "%d%s", (int) difPercent, "%");
            } else {
                notificationsPrimary = String.format(Locale.ENGLISH, "%.1f%s", difPercent, "%");
            }
        }
    }

    public static class OverviewCell extends LinearLayout {

        TextView[] primary = new TextView[4];
        TextView[] secondary = new TextView[4];
        TextView[] title = new TextView[4];


        public OverviewCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            for (int i = 0; i < 2; i++) {
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

                    primary[i * 2 + j].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    primary[i * 2 + j].setTextSize(17);
                    title[i * 2 + j].setTextSize(13);
                    secondary[i * 2 + j].setTextSize(13);

                    secondary[i * 2 + j].setPadding(AndroidUtilities.dp(4), 0, 0, 0);

                    infoLayout.addView(primary[i * 2 + j]);
                    infoLayout.addView(secondary[i * 2 + j]);

                    contentCell.addView(infoLayout);
                    contentCell.addView(title[i * 2 + j]);
                    linearLayout.addView(contentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));
                }
                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 16));
            }
        }

        public void setData(OverviewData data) {
            primary[0].setText(data.followersPrimary);
            primary[1].setText(data.notificationsPrimary);
            primary[2].setText(data.viewsPrimary);
            primary[3].setText(data.sharesPrimary);

            secondary[0].setText(data.followersSecondary);
            secondary[0].setTag(data.followersUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_windowBackgroundWhiteRedText5);
            secondary[1].setText("");
            secondary[2].setText(data.viewsSecondary);
            secondary[2].setTag(data.viewsUp ? Theme.key_windowBackgroundWhiteGreenText2: Theme.key_windowBackgroundWhiteRedText5);
            secondary[3].setText(data.sharesSecondary);
            secondary[3].setTag(data.sharesUp ? Theme.key_windowBackgroundWhiteGreenText2 : Theme.key_windowBackgroundWhiteRedText5);

            title[0].setText(data.followersTitle);
            title[1].setText(data.notificationsTitle);
            title[2].setText(data.viewsTitle);
            title[3].setText(data.sharesTitle);

            updateColors();
        }

        private void updateColors() {
            for (int i = 0; i < 4; i++) {
                primary[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));

                String colorKey = (String) secondary[i].getTag();
                if (colorKey != null) {
                    secondary[i].setTextColor(Theme.getColor(colorKey));
                }
            }

        }
    }
}
