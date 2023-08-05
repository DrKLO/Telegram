package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;

public class SelfStoryViewsPage extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final static int TOP_PADDING = 46;
    private static final int FIRST_PADDING_ITEM = 0;
    private static final int USER_ITEM = 1;
    private static final int LAST_ITEM = 2;
    private static final int BUTTON_PADDING = 3;
    private static final int FLICKER_LOADING_ITEM = 4;
    private static final int EMPTY_VIEW = 5;

    private final TextView titleView;
    private int measuerdHeight;

    RecyclerListView recyclerListView;
    Theme.ResourcesProvider resourcesProvider;
    int currentAccount;
    ListAdapter listAdapter;

    public LinearLayoutManager layoutManager;
    SelfStoryViewsView.StoryItemInternal storyItem;
    ViewsModel model;
    private boolean isAttachedToWindow;
    RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;

    public SelfStoryViewsPage(StoryViewer storyViewer, @NonNull Context context) {
        super(context);
        this.resourcesProvider = storyViewer.resourcesProvider;
        currentAccount = storyViewer.currentAccount;

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));

        recyclerListView = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                measuerdHeight = MeasureSpec.getSize(heightSpec);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        recyclerListView.setClipToPadding(false);
        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        recyclerListView.setNestedScrollingEnabled(true);
        recyclerListView.setAdapter(listAdapter = new ListAdapter());

        addView(recyclerListView);


        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMore();
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            TLRPC.TL_storyView user = listAdapter.items.get(position).user;
            if (user != null) {
                storyViewer.presentFragment(ProfileActivity.of(user.user_id));
            }
        });

        listAdapter.updateRows();

        View shadowView = new View(getContext());
        shadowView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ Theme.getColor(Theme.key_dialogBackground, resourcesProvider), Color.TRANSPARENT }));
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8, 0, 0, TOP_PADDING - 8, 0, 0));

        View shadowView2 = new View(getContext());
        shadowView2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        addView(shadowView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 10, 0, 0, TOP_PADDING - 17, 0, 0));

        addView(titleView);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == recyclerListView) {
            canvas.save();
            canvas.clipRect(0, AndroidUtilities.dp(TOP_PADDING), getMeasuredWidth(), getMeasuredHeight());
            super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void checkLoadMore() {
        if (model != null && layoutManager.findLastVisibleItemPosition() > listAdapter.getItemCount() - 10) {
            model.loadNext();
        }
    }

    public void setStoryItem(SelfStoryViewsView.StoryItemInternal storyItem) {
        this.storyItem = storyItem;
        if (storyItem.storyItem != null) {
            TLRPC.StoryItem serverItem = storyItem.storyItem;
            model = MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.get(serverItem.id);
            int totalCount = serverItem.views == null ? 0 : serverItem.views.views_count;
            if (model == null || model.totalCount != totalCount) {
                if (model != null) {
                    model.release();
                }
                model = new ViewsModel(currentAccount, serverItem);
                model.loadNext();
                MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.put(serverItem.id, model);
            }
            if (serverItem.views == null || serverItem.views.views_count == 0) {
                titleView.setText(LocaleController.getString("NobodyViewsTitle", R.string.NobodyViewsTitle));
            } else {
                titleView.setText(LocaleController.formatPluralStringComma("Views", serverItem.views.views_count));
            }
        } else {
            titleView.setText(LocaleController.getString("UploadingStory", R.string.UploadingStory));
        }
    }

    public static void preload(int currentAccount, TLRPC.StoryItem storyItem) {
        if (storyItem == null) {
            return;
        }
        ViewsModel model = MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.get(storyItem.id);
        int totalCount = storyItem.views == null ? 0 : storyItem.views.views_count;
        if (model == null || model.totalCount != totalCount) {
            if (model != null) {
                model.release();
            }
            model = new ViewsModel(currentAccount, storyItem);
            model.loadNext();
            MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.put(storyItem.id, model);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (model != null) {
            model.addListener(this);
            model.animateDateForUsers.clear();
        }
        listAdapter.updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (model != null) {
            model.removeListener(this);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
    }

    public void onDataRecieved() {
        NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            int oldCount = listAdapter.getItemCount();
            listAdapter.updateRows();
            recyclerItemsEnterAnimator.showItemsAnimated(oldCount);
            checkLoadMore();
        });
    }

    public void setListBottomPadding(float bottomPadding) {
        if (bottomPadding != recyclerListView.getPaddingBottom()) {
            recyclerListView.setPadding(0, 0, 0, (int) bottomPadding);
            recyclerListView.requestLayout();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesUpdated) {
            if (storyItem.uploadingStory != null) {
                TLRPC.TL_userStories stories = MessagesController.getInstance(currentAccount).storiesController.getStories(UserConfig.getInstance(currentAccount).clientUserId);
                if (stories != null) {
                    for (int i = 0; i < stories.stories.size(); i++) {
                        TLRPC.StoryItem storyItem = stories.stories.get(i);
                        if (storyItem.attachPath != null && storyItem.attachPath.equals(this.storyItem.uploadingStory.path)) {
                            this.storyItem.uploadingStory = null;
                            this.storyItem.storyItem = storyItem;
                            setStoryItem(this.storyItem);
                            break;
                        }
                    }
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        ArrayList<Item> items = new ArrayList<>();

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case FIRST_PADDING_ITEM:
                    view = new View(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(TOP_PADDING), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case BUTTON_PADDING:
                    view = new FixedHeightEmptyCell(getContext(), 70);
                    break;
                case USER_ITEM:
                    view = new ReactedUserHolderView(ReactedUserHolderView.STYLE_STORY, currentAccount, getContext(), resourcesProvider) {
                        @Override
                        public void openStory(long dialogId, Runnable onDone) {
                            LaunchActivity.getLastFragment().getOrCreateOverlayStoryViewer().doOnAnimationReady(onDone);
                            LaunchActivity.getLastFragment().getOrCreateOverlayStoryViewer().open(getContext(), dialogId, StoriesListPlaceProvider.of(recyclerListView));
                        }
                    };
                    break;
                case FLICKER_LOADING_ITEM:
                    FlickerLoadingView loadingView = new FlickerLoadingView(getContext(), resourcesProvider);
                    loadingView.setIsSingleCell(true);
                    loadingView.setViewType(FlickerLoadingView.SOTRY_VIEWS_USER_TYPE);
                    loadingView.showDate(false);
                    view = loadingView;
                    break;
                default:
                case LAST_ITEM:
                    view = new View(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        }
                    };
                    break;
                case EMPTY_VIEW:
                    StickerEmptyView emptyView = new StickerEmptyView(getContext(), null, model.isExpiredViews ? StickerEmptyView.STICKER_TYPE_PRIVACY : StickerEmptyView.STICKER_TYPE_NO_CONTACTS, resourcesProvider) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measuerdHeight - recyclerListView.getPaddingBottom(), MeasureSpec.EXACTLY));
                        }
                    };
                    emptyView.title.setVisibility(View.GONE);
                    if (model.isExpiredViews) {
                        emptyView.subtitle.setText(AndroidUtilities.replaceTags(LocaleController.getString("ExpiredViewsStub", R.string.ExpiredViewsStub)));
                    } else {
                        emptyView.subtitle.setText(LocaleController.getString("NoViewsStub", R.string.NoViewsStub));
                    }
                    emptyView.showProgress(false, false);
                    view = emptyView;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == USER_ITEM) {
                ReactedUserHolderView view = (ReactedUserHolderView) holder.itemView;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(items.get(position).user.user_id);

                boolean animated = model.animateDateForUsers.remove(items.get(position).user.user_id);
                view.setUserReaction(user, null, null, items.get(position).user.date, true, animated);
                //   items.get(position + 1).viewType == USER_ITEM
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == USER_ITEM;
        }

        public void updateRows() {
            items.clear();
            if (model != null && model.views.isEmpty() && (model.isExpiredViews || (!model.loading && !model.hasNext))) {
                items.add(new Item(EMPTY_VIEW));
            } else {
                items.add(new Item(FIRST_PADDING_ITEM));
                if (model != null) {
                    for (int i = 0; i < model.views.size(); i++) {
                        items.add(new Item(USER_ITEM, model.views.get(i)));
                    }
                }
                if (model != null && (model.loading || model.hasNext)) {
                    items.add(new Item(FLICKER_LOADING_ITEM));
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }
    }

    private class Item {
        final int viewType;
        TLRPC.TL_storyView user;

        private Item(int viewType) {
            this.viewType = viewType;
        }

        private Item(int viewType, TLRPC.TL_storyView user) {
            this.viewType = viewType;
            this.user = user;
        }
    }

    public static class ViewsModel {

        public int totalCount;
        TLRPC.StoryItem storyItem;
        int currentAccount;
        boolean loading;
        ArrayList<TLRPC.TL_storyView> views = new ArrayList<>();
        boolean isExpiredViews;
        boolean initial;
        boolean hasNext = true;
        long offsetId;
        int offsetDate;
        int reqId = -1;
        HashSet<Long> animateDateForUsers = new HashSet<>();

        ArrayList<SelfStoryViewsPage> listeners = new ArrayList<>();

        public ViewsModel(int currentAccount, TLRPC.StoryItem storyItem) {
            this.currentAccount = currentAccount;
            this.storyItem = storyItem;
            this.totalCount = storyItem.views == null ? 0 : storyItem.views.views_count;
            isExpiredViews = StoriesUtilities.hasExpiredViews(storyItem);
            if (!isExpiredViews) {
                initial = true;
                if (storyItem.views != null) {
                    for (int i = 0; i < storyItem.views.recent_viewers.size(); i++) {
                        TLRPC.TL_storyView storyView = new TLRPC.TL_storyView();
                        storyView.user_id = storyItem.views.recent_viewers.get(i);
                        storyView.date = 0;
                        views.add(storyView);
                    }
                }
            }
        }

        public void loadNext() {
            if (loading || !hasNext || isExpiredViews) {
                return;
            }
            TLRPC.TL_stories_getStoryViewsList req = new TLRPC.TL_stories_getStoryViewsList();
            req.id = storyItem.id;
            req.limit = initial ? 20 : 100;
            req.offset_id = offsetId;
            req.offset_date = offsetDate;

            loading = true;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                reqId = -1;
                if (response != null) {
                    TLRPC.TL_stories_storyViewsList res = (TLRPC.TL_stories_storyViewsList) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    if (initial) {
                        initial = false;
                        for (int i = 0; i < views.size(); i++) {
                            animateDateForUsers.add(views.get(i).user_id);
                        }
                        views.clear();
                    }
                    views.addAll(res.views);

                    if (!res.views.isEmpty()) {
                        TLRPC.TL_storyView last = res.views.get(res.views.size() - 1);
                        offsetDate = last.date;
                        offsetId = last.user_id;
                        hasNext = res.views.size() == req.limit;
                    } else {
                        hasNext = false;
                    }

                    if (storyItem.views == null) {
                        storyItem.views = new TLRPC.TL_storyViews();
                    }
                    if (res.count > storyItem.views.views_count) {
                        storyItem.views.recent_viewers.clear();
                        for (int i = 0; i < (Math.min(3, res.users.size())); i++) {
                            storyItem.views.recent_viewers.add(res.users.get(i).id);
                        }
                        storyItem.views.views_count = res.count;
                    }
                } else {
                    hasNext = false;
                }
                for (int i = 0; i < listeners.size(); i++) {
                    listeners.get(i).onDataRecieved();
                }
            }));

        }

        public void addListener(SelfStoryViewsPage listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        public void removeListener(SelfStoryViewsPage listener) {
            listeners.remove(listener);
        }

        public void release() {
            if (reqId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false);
            }
            reqId = -1;
        }
    }
}
