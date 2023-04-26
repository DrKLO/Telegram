/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.ArraySet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LruCache;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.ChartHeaderView;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class MessageStatisticActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.ChatFull chat;
    private final long chatId;
    private final int messageId;
    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;

    private MessageObject messageObject;
    private StatisticActivity.ChartViewData interactionsViewData;
    private LruCache<ChartData> childDataCache = new LruCache<>(15);
    private StatisticActivity.ZoomCancelable lastCancelable;

    private ArrayList<TLRPC.Message> messages = new ArrayList<>();
    private boolean statsLoaded;
    private boolean loading;
    private boolean firstLoaded;

    private int headerRow;
    private int startRow;
    private int endRow;
    private int loadingRow;
    private int interactionsChartRow;
    private int overviewRow;
    private int overviewHeaderRow;
    private int emptyRow;
    private int rowCount;

    ArraySet<Integer> shadowDivideCells = new ArraySet<>();

    private RLottieImageView imageView;
    private LinearLayout progressLayout;

    private int nextRate;
    private int publicChats;
    private boolean endReached;

    ImageReceiver thumbImage;
    boolean drawPlay;

    private final Runnable showProgressbar = new Runnable() {
        @Override
        public void run() {
            progressLayout.animate().alpha(1f).setDuration(230);
        }
    };
    private FrameLayout listContainer;
    private ChatAvatarContainer avatarContainer;
    private BaseChartView.SharedUiComponents sharedUi;

    public MessageStatisticActivity(MessageObject message) {
        messageObject = message;

        if (messageObject.messageOwner.fwd_from == null) {
            chatId = messageObject.getChatId();
            messageId = messageObject.getId();
        } else {
            chatId = -messageObject.getFromChatId();
            messageId = messageObject.messageOwner.fwd_msg_id;
        }
        this.chat = getMessagesController().getChatFull(chatId);
    }

    private void updateRows() {
        shadowDivideCells.clear();
        headerRow = -1;
        startRow = -1;
        endRow = -1;
        loadingRow = -1;
        interactionsChartRow = -1;
        overviewHeaderRow = -1;
        overviewRow = -1;

        rowCount = 0;
        if (firstLoaded && statsLoaded) {
            AndroidUtilities.cancelRunOnUIThread(showProgressbar);
            if (listContainer.getVisibility() == View.GONE) {
                progressLayout.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressLayout.setVisibility(View.GONE);
                    }
                });
                listContainer.setVisibility(View.VISIBLE);
                listContainer.setAlpha(0f);
                listContainer.animate().alpha(1f).start();
            }

            overviewHeaderRow = rowCount++;
            overviewRow = rowCount++;
            shadowDivideCells.add(rowCount++);
            if (interactionsViewData != null) {
                interactionsChartRow = rowCount++;
                shadowDivideCells.add(rowCount++);
            }

            if (!messages.isEmpty()) {
                headerRow = rowCount++;
                startRow = rowCount;
                rowCount += messages.size();
                endRow = rowCount;
                emptyRow = rowCount++;
                shadowDivideCells.add(rowCount++);

                if (!endReached) {
                    loadingRow = rowCount++;
                }
            }
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (chat != null) {
            loadStat();
            loadChats(100);
        } else {
            MessagesController.getInstance(currentAccount).loadFullChat(chatId, classGuid, true);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chat == null &&  chatFull.id == chatId) {
                TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
                if (chatLocal != null) {
                    avatarContainer.setChatAvatar(chatLocal);
                    avatarContainer.setTitle(chatLocal.title);
                }
                chat = chatFull;
                loadStat();
                loadChats(100);
                updateMenu();
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.setVisibility(View.GONE);

        progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.VERTICAL);

        imageView = new RLottieImageView(context);
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
        progressLayout.setAlpha(0);

        frameLayout.addView(progressLayout, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 30));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        ((SimpleItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);

        listView.setOnItemClickListener((view, position) -> {
            if (position >= startRow && position < endRow) {
                TLRPC.Message message = messages.get(position - startRow);
                long did =  MessageObject.getDialogId(message);
                Bundle args = new Bundle();
                if (DialogObject.isUserDialog(did)) {
                    args.putLong("user_id", did);
                } else {
                    args.putLong("chat_id", -did);
                }
                args.putInt("message_id", message.id);
                args.putBoolean("need_remove_previous_same_chat_activity", false);
                if (getMessagesController().checkCanOpenChat(args, this)) {
                    presentFragment(new ChatActivity(args));
                }
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();

                if (visibleItemCount > 0) {
                    if (!endReached && !loading && !messages.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5 && statsLoaded) {
                        loadChats(100);
                    }
                }
            }
        });
        emptyView.showTextView();

        listContainer = new FrameLayout(context);
        listContainer.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listContainer.setVisibility(View.GONE);
        frameLayout.addView(listContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        AndroidUtilities.runOnUIThread(showProgressbar, 300);

        updateRows();
        listView.setEmptyView(emptyView);

        avatarContainer = new ChatAvatarContainer(context, null, false) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                thumbImage.setImageCoords(avatarContainer.getSubtitleTextView().getX(), avatarContainer.getSubtitleTextView().getY(), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
                thumbImage.draw(canvas);
                if (drawPlay) {
                    int x = (int) (thumbImage.getCenterX() - Theme.dialogs_playDrawable.getIntrinsicWidth() / 2);
                    int y = (int) (thumbImage.getCenterY() - Theme.dialogs_playDrawable.getIntrinsicHeight() / 2);
                    Theme.dialogs_playDrawable.setBounds(x, y, x + Theme.dialogs_playDrawable.getIntrinsicWidth(), y + Theme.dialogs_playDrawable.getIntrinsicHeight());
                    Theme.dialogs_playDrawable.draw(canvas);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                thumbImage.onAttachedToWindow();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                thumbImage.onDetachedFromWindow();
            }
        };

        thumbImage = new ImageReceiver();
        thumbImage.setParentView(avatarContainer);
        thumbImage.setRoundRadius(AndroidUtilities.dp(2));
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 40, 0));

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        if (chatLocal != null) {
            avatarContainer.setChatAvatar(chatLocal);
            avatarContainer.setTitle(chatLocal.title);
        }

        boolean hasThumb = false;

        if (!messageObject.needDrawBluredPreview() && (messageObject.isPhoto() || messageObject.isNewGif() || messageObject.isVideo())) {
            String type = messageObject.isWebpage() ? messageObject.messageOwner.media.webpage.type : null;
            if (!("app".equals(type) || "profile".equals(type) || "article".equals(type) || type != null && type.startsWith("telegram_"))) {
                TLRPC.PhotoSize smallThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40);
                TLRPC.PhotoSize bigThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                if (smallThumb == bigThumb) {
                    bigThumb = null;
                }
                if (smallThumb != null) {
                    hasThumb = true;
                    drawPlay = messageObject.isVideo();
                    String fileName = FileLoader.getAttachFileName(bigThumb);
                    if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                        int size;
                        if (messageObject.type == MessageObject.TYPE_PHOTO) {
                            size = bigThumb != null ? bigThumb.size : 0;
                        } else {
                            size = 0;
                        }
                        thumbImage.setImage(ImageLocation.getForObject(bigThumb, messageObject.photoThumbsObject), "20_20", ImageLocation.getForObject(smallThumb, messageObject.photoThumbsObject), "20_20", size, null, messageObject, 0);
                    } else {
                        thumbImage.setImage(null, null, ImageLocation.getForObject(smallThumb, messageObject.photoThumbsObject), "20_20", (Drawable) null, messageObject, 0);
                    }
                }
            }
        }

        CharSequence message;
        if (!TextUtils.isEmpty(messageObject.caption)) {
            message = messageObject.caption;
        } else if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
            message = messageObject.messageText;
            if (message.length() > 150) {
                message = message.subSequence(0, 150);
            }
            message = Emoji.replaceEmoji(message, avatarContainer.getSubtitleTextView().getTextPaint().getFontMetricsInt(), AndroidUtilities.dp(17), false);
        } else {
            message = messageObject.messageText;
        }

        if (hasThumb) {
            SpannableStringBuilder builder = new SpannableStringBuilder(message);
            builder.insert(0, " ");
            builder.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(18 + 6)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            avatarContainer.setSubtitle(builder);
        } else {
            avatarContainer.setSubtitle(messageObject.messageText);
        }
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    Bundle args = new Bundle();
                    if (messageObject.messageOwner.fwd_from == null) {
                        args.putLong("chat_id", messageObject.getChatId());
                    } else {
                        args.putLong("chat_id", -messageObject.getFromChatId());
                    }
                    presentFragment(new StatisticActivity(args));
                }
            }
        });

        avatarContainer.setTitleColors(Theme.getColor(Theme.key_player_actionBarTitle), Theme.getColor(Theme.key_player_actionBarSubtitle));
        avatarContainer.getSubtitleTextView().setLinkTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        avatarContainer.setOnClickListener(view -> {
            if (getParentLayout().getFragmentStack().size() > 1) {
                BaseFragment previousFragemnt = getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2);
                if (previousFragemnt instanceof ChatActivity &&  ((ChatActivity) previousFragemnt).getCurrentChat().id  == chatId) {
                    finishFragment();
                    return;
                }
            }
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putInt("message_id", messageId);
            args.putBoolean("need_remove_previous_same_chat_activity", false);
            ChatActivity a = new ChatActivity(args);
            presentFragment(a);
        });

        updateMenu();
        return fragmentView;
    }

    private void updateMenu() {
        if (chat != null && chat.can_view_stats) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.clearItems();
            ActionBarMenuItem headerItem = menu.addItem(0, R.drawable.ic_ab_other);
            headerItem.addSubItem(1, R.drawable.msg_stats, LocaleController.getString("ViewChannelStats", R.string.ViewChannelStats));
        }
    }

    private void loadChats(int count) {
        if (loading) {
            return;
        }
        loading = true;
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        TLRPC.TL_stats_getMessagePublicForwards req = new TLRPC.TL_stats_getMessagePublicForwards();
        req.limit = count;
        if (messageObject.messageOwner.fwd_from != null) {
            req.msg_id = messageObject.messageOwner.fwd_from.saved_from_msg_id;
            req.channel = getMessagesController().getInputChannel(-messageObject.getFromChatId());
        } else {
            req.msg_id = messageObject.getId();
            req.channel = getMessagesController().getInputChannel(-messageObject.getDialogId());
        }
        if (!messages.isEmpty()) {
            TLRPC.Message message = messages.get(messages.size() - 1);
            req.offset_id = message.id;
            req.offset_peer = getMessagesController().getInputPeer(MessageObject.getDialogId(message));
            req.offset_rate = nextRate;
        } else {
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                if ((res.flags & 1) != 0) {
                    nextRate = res.next_rate;
                }
                if (res.count != 0) {
                    publicChats = res.count;
                } else if (publicChats == 0) {
                    publicChats = res.messages.size();
                }
                endReached = !(res instanceof TLRPC.TL_messages_messagesSlice);
                getMessagesController().putChats(res.chats, false);
                getMessagesController().putUsers(res.users, false);
                messages.addAll(res.messages);
                if (emptyView != null) {
                    emptyView.showTextView();
                }
            }
            firstLoaded = true;
            loading = false;
            updateRows();
        }), null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void loadStat() {
        TLRPC.TL_stats_getMessageStats req = new TLRPC.TL_stats_getMessageStats();
        if (messageObject.messageOwner.fwd_from != null) {
            req.msg_id = messageObject.messageOwner.fwd_from.saved_from_msg_id;
            req.channel = getMessagesController().getInputChannel(-messageObject.getFromChatId());
        } else {
            req.msg_id = messageObject.getId();
            req.channel = getMessagesController().getInputChannel(-messageObject.getDialogId());
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            statsLoaded = true;
            if (error != null) {
                updateRows();
                return;
            }
            TLRPC.TL_stats_messageStats res = (TLRPC.TL_stats_messageStats) response;
            interactionsViewData = StatisticActivity.createViewData(res.views_graph, LocaleController.getString("InteractionsChartTitle", R.string.InteractionsChartTitle), 1, false);
            if (interactionsViewData != null && interactionsViewData.chartData.x.length <= 5) {
                statsLoaded = false;
                TLRPC.TL_stats_loadAsyncGraph request = new TLRPC.TL_stats_loadAsyncGraph();
                request.token = interactionsViewData.zoomToken;
                request.x = interactionsViewData.chartData.x[interactionsViewData.chartData.x.length - 1];
                request.flags |= 1;

                final String cacheKey = interactionsViewData.zoomToken + "_" + request.x;
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response1, error1) -> {
                    ChartData childData = null;
                    if (response1 instanceof TLRPC.TL_statsGraph) {
                        String json = ((TLRPC.TL_statsGraph) response1).json.data;
                        try {
                            childData = StatisticActivity.createChartData(new JSONObject(json), 1, false);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else if (response1 instanceof TLRPC.TL_statsGraphError) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() != null) {
                                Toast.makeText(getParentActivity(), ((TLRPC.TL_statsGraphError) response1).error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    ChartData finalChildData = childData;
                    AndroidUtilities.runOnUIThread(() -> {
                        statsLoaded = true;
                        if (error1 != null || finalChildData == null) {
                            updateRows();
                            return;
                        }
                        childDataCache.put(cacheKey, finalChildData);
                        interactionsViewData.childChartData = finalChildData;
                        interactionsViewData.activeZoom = request.x;
                        updateRows();
                    });
                }, null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
            } else {
                updateRows();
            }
        }), null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 0) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                return cell.getCurrentObject() instanceof TLObject;
            }
            return false;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, 6, 2, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    HeaderCell headerCell = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 16, 11, false);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell.setHeight(43);
                    view = headerCell;
                    break;
                case 4:
                    view = new StatisticActivity.BaseChartCell(mContext, 1, sharedUi = new BaseChartView.SharedUiComponents()) {

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

                            zoomCanceled();
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
                            StatisticActivity.ZoomCancelable finalCancelabel;
                            lastCancelable = finalCancelabel = new StatisticActivity.ZoomCancelable();
                            finalCancelabel.adapterPosition = listView.getChildAdapterPosition(this);

                            chartView.legendSignatureView.showProgress(true, false);

                            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                                ChartData childData = null;
                                if (response instanceof TLRPC.TL_statsGraph) {
                                    String json = ((TLRPC.TL_statsGraph) response).json.data;
                                    try {
                                        childData = StatisticActivity.createChartData(new JSONObject(json), data.graphType, false);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                } else if (response instanceof TLRPC.TL_statsGraphError) {
                                    Toast.makeText(getContext(), ((TLRPC.TL_statsGraphError) response).error, Toast.LENGTH_LONG).show();
                                }

                                ChartData finalChildData = childData;
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (finalChildData != null) {
                                        childDataCache.put(cacheKey, finalChildData);
                                    }
                                    if (finalChildData != null && !finalCancelabel.canceled && finalCancelabel.adapterPosition >= 0) {
                                        View view = layoutManager.findViewByPosition(finalCancelabel.adapterPosition);
                                        if (view instanceof StatisticActivity.BaseChartCell) {
                                            data.childChartData = finalChildData;
                                            ((StatisticActivity.BaseChartCell) view).chartView.legendSignatureView.showProgress(false, false);
                                            ((StatisticActivity.BaseChartCell) view).zoomChart(false);
                                        }
                                    }
                                    zoomCanceled();
                                });
                            }, null, null, 0, chat.stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
                            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
                        }

                        @Override
                        public void zoomCanceled() {
                            if (lastCancelable != null) {
                                lastCancelable.canceled = true;
                            }
                            int n = listView.getChildCount();
                            for (int i = 0; i < n; i++) {
                                View child = listView.getChildAt(i);
                                if (child instanceof StatisticActivity.BaseChartCell) {
                                    ((StatisticActivity.BaseChartCell) child).chartView.legendSignatureView.showProgress(false, true);
                                }
                            }
                        }

                        @Override
                        void loadData(StatisticActivity.ChartViewData viewData) {
                            //  viewData.load(currentAccount, classGuid, );
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new OverviewCell(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new EmptyCell(mContext, 16);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16));
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(40), AndroidUtilities.dp(120));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    TLRPC.Message item = getItem(position);
                    long did = MessageObject.getDialogId(item);
                    TLObject object;
                    String status = null;
                    if (DialogObject.isUserDialog(did)) {
                        object = getMessagesController().getUser(did);
                    } else {
                        object = getMessagesController().getChat(-did);
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        if (chat.participants_count != 0) {
                            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                status = LocaleController.formatPluralString("Subscribers", chat.participants_count);
                            } else {
                                status = LocaleController.formatPluralString("Members", chat.participants_count);
                            }
                            status = String.format("%1$s, %2$s", status, LocaleController.formatPluralString("Views", item.views));
                        }
                    }
                    if (object != null) {
                        userCell.setData(object, null, status, position != endRow - 1);
                    }
                    break;
                case 1:
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == overviewHeaderRow) {
                        headerCell.setText(LocaleController.formatString("StatisticOverview", R.string.StatisticOverview));
                    } else {
                        headerCell.setText(LocaleController.formatPluralString("PublicSharesCount", publicChats));
                    }
                    break;
                case 4:
                    StatisticActivity.BaseChartCell chartCell = (StatisticActivity.BaseChartCell) holder.itemView;
                    chartCell.updateData(interactionsViewData, false);
                    chartCell.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                    break;
                case 5:
                    OverviewCell overviewCell = (OverviewCell) holder.itemView;
                    overviewCell.setData();
                    break;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (shadowDivideCells.contains(position)) {
                return 1;
            } else if (position == headerRow || position == overviewHeaderRow) {
                return 2;
            } else if (position == loadingRow) {
                return 3;
            } else if (position == interactionsChartRow) {
                return 4;
            } else if (position == overviewRow) {
                return 5;
            } else if (position == emptyRow) {
                return 6;
            }
            return 0;
        }

        public TLRPC.Message getItem(int position) {
            if (position >= startRow && position < endRow) {
                return messages.get(position - startRow);
            }
            return null;
        }
    }

    public class OverviewCell extends LinearLayout {

        TextView[] primary = new TextView[3];
        TextView[] title = new TextView[3];
        View[] cell = new View[3];

        public OverviewCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);

            for (int j = 0; j < 3; j++) {
                LinearLayout contentCell = new LinearLayout(context);
                cell[j] = contentCell;
                contentCell.setOrientation(VERTICAL);

                primary[j] = new TextView(context);
                title[j] = new TextView(context);

                primary[j].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                primary[j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                title[j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);

                contentCell.addView(primary[j]);
                contentCell.addView(title[j]);
                linearLayout.addView(contentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));
            }
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        public void setData() {
            primary[0].setText(AndroidUtilities.formatWholeNumber(messageObject.messageOwner.views, 0));
            title[0].setText(LocaleController.getString("StatisticViews", R.string.StatisticViews));

            if (publicChats > 0) {
                cell[1].setVisibility(View.VISIBLE);
                primary[1].setText(AndroidUtilities.formatWholeNumber(publicChats, 0));
                title[1].setText(LocaleController.formatString("PublicShares", R.string.PublicShares));
            } else {
                cell[1].setVisibility(View.GONE);
            }

            int privateChats = messageObject.messageOwner.forwards - publicChats;
            if (privateChats > 0) {
                cell[2].setVisibility(View.VISIBLE);
                primary[2].setText(AndroidUtilities.formatWholeNumber(privateChats, 0));
                title[2].setText(LocaleController.formatString("PrivateShares", R.string.PrivateShares));
            } else {
                cell[2].setVisibility(View.GONE);
            }

            updateColors();
        }

        private void updateColors() {
            for (int i = 0; i < 3; i++) {
                primary[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(listView.getChildAt(a));
                }
                count = listView.getHiddenChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(listView.getHiddenChildAt(a));
                }
                count = listView.getCachedChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(listView.getCachedChildAt(a));
                }
                count = listView.getAttachedScrapChildCount();
                for (int a = 0; a < count; a++) {
                    recolorRecyclerItem(listView.getAttachedScrapChildAt(a));
                }
                listView.getRecycledViewPool().clear();
            }
            if (sharedUi != null) {
                sharedUi.invalidate();
            }

            avatarContainer.getSubtitleTextView().setLinkTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ManageChatUserCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
        themeDescriptions.add(new ThemeDescription(avatarContainer != null ? avatarContainer.getSubtitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_player_actionBarSubtitle, null));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_statisticChartLineEmpty));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        StatisticActivity.putColorFromData(interactionsViewData, themeDescriptions, cellDelegate);
        return themeDescriptions;
    }

    private void recolorRecyclerItem(View child) {
        if (child instanceof ManageChatUserCell) {
            ((ManageChatUserCell) child).update(0);
        } else if (child instanceof StatisticActivity.BaseChartCell) {
            ((StatisticActivity.BaseChartCell) child).recolor();
            child.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
            child.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        if (child instanceof EmptyCell) {
            child.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
    }
}
