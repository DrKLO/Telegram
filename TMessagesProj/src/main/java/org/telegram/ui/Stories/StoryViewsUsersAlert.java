package org.telegram.ui.Stories;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UsersAlertBase;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

//TODO stories
//add pagination
public class StoryViewsUsersAlert extends UsersAlertBase {

    private static final int FIRST_PADDING_ITEM = 0;
    private static final int USER_ITEM = 1;
    private static final int LAST_ITEM = 2;
    private static final int BUTTON_PADDING = 3;


    ListAdapter listAdapter;
    boolean showSearch = false;
    ArrayList<TLRPC.TL_storyView> users = new ArrayList<>();

    StoryViewer storyViewer;

    public StoryViewsUsersAlert(Context context, StoryViewer storyViewer, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, storyViewer.currentAccount, resourcesProvider);
        showSearch(showSearch = false);
        searchListViewAdapter = searchListViewAdapter = new SearchAdapter();
        listView.setAdapter(listViewAdapter = listAdapter = new ListAdapter());
        FrameLayout buttonContainer = new FrameLayout(getContext());
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);

        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 10, 10, 10, 10));
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        button.setText(LocaleController.getString("Close", R.string.Close), false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0  || position > listAdapter.items.size() - 1) {
                return;
            }
            TLRPC.TL_storyView user = listAdapter.items.get(position).user;

            if (user != null) {
                dismiss();

                Bundle args = new Bundle();
                args.putLong("user_id", user.user_id);
                ProfileActivity profileActivity = new ProfileActivity(args);

                storyViewer.presentFragment(profileActivity);
            }
        });
    }

    @Override
    protected int measurePadding(int availableHeight) {
        int padding = super.measurePadding(availableHeight);
        if (!showSearch) {
            int h = availableHeight - AndroidUtilities.dp(ReactedUserHolderView.STORY_ITEM_HEIGHT_DP) * users.size() - AndroidUtilities.dp(36) - AndroidUtilities.dp(80);
            if (h > padding) {
                return h;
            }
        }
        return padding;
    }

    public void setViews(TLRPC.StoryItem storyItem) {
        int viewsCount = storyItem.views != null ? storyItem.views.views_count : 0;
        ArrayList<Long> recentViewers = storyItem.views != null ? storyItem.views.recent_viewers : new ArrayList<>();
        setTitle(LocaleController.formatPluralStringComma("Views", viewsCount));
        showSearch(viewsCount > 20);
        users.clear();
        for (int i = 0; i < recentViewers.size(); i++) {
            TLRPC.TL_storyView storyView = new TLRPC.TL_storyView();
            storyView.user_id = recentViewers.get(i);
            users.add(storyView);
        }

        listAdapter.updateRows();
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
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
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36 + (showSearch ? 58 : 0)), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case BUTTON_PADDING:
                    view = new FixedHeightEmptyCell(getContext(), 64);
                    break;
                case USER_ITEM:
                    view = new ReactedUserHolderView(ReactedUserHolderView.STYLE_STORY, currentAccount, getContext(), resourcesProvider);
                    break;
                default:
                case LAST_ITEM:
                    view = new View(getContext());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == USER_ITEM) {
                ReactedUserHolderView view = (ReactedUserHolderView) holder.itemView;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(items.get(position).user.user_id);

                view.setUserReaction(user, null, null, items.get(position).user.date, true, false);
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
            items.add(new Item(FIRST_PADDING_ITEM));
            for (int i = 0; i < users.size(); i++) {
                items.add(new Item(USER_ITEM, users.get(i)));
            }
            items.add(new Item(BUTTON_PADDING));
            if (showSearch) {
                items.add(new Item(LAST_ITEM));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }
    }

    public static Runnable preload(int currentAccount, Context context, Theme.ResourcesProvider resourcesProvider, TLRPC.StoryItem storyItem, StoryViewer storyViewer, Consumer<StoryViewsUsersAlert> alertConsumer) {
        TLRPC.TL_stories_getStoryViewsList req = new TLRPC.TL_stories_getStoryViewsList();
        req.id = storyItem.id;
        req.limit = 100;
        req.offset_id = 0;
        req.offset_date = 0;
        boolean[] canceled = new boolean[]{false};
        int id = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (canceled[0]) {
                return;
            }
            if (response != null) {
                TLRPC.TL_stories_storyViewsList res = (TLRPC.TL_stories_storyViewsList) response;
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                StoryViewsUsersAlert alert = new StoryViewsUsersAlert(context, storyViewer, resourcesProvider);
                alert.setTitle(LocaleController.formatPluralStringComma("Views", res.count));
                alert.showSearch(res.count > 20);
                alert.users.clear();
                for (int i = 0; i < res.views.size(); i++) {
                    alert.users.add(res.views.get(i));
                }
                alert.listAdapter.updateRows();
                if (res.count > storyItem.views.views_count) {
                    storyItem.views.recent_viewers.clear();
                    for (int i = 0; i < (Math.min(3, res.users.size())); i++) {
                        storyItem.views.recent_viewers.add(res.users.get(i).id);
                    }
                    storyItem.views.views_count = res.count;
                }
                alertConsumer.accept(alert);
            } else {
                alertConsumer.accept(null);
            }
        }));
        return new Runnable() {
            @Override
            public void run() {
                canceled[0] = true;
                ConnectionsManager.getInstance(currentAccount).cancelRequest(id, false);
            }
        };
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
}
