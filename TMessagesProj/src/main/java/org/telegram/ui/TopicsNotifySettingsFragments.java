package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TopicExceptionCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class TopicsNotifySettingsFragments extends BaseFragment {

    private final int VIEW_TYPE_ADD_EXCEPTION = 1;
    private final int VIEW_TYPE_TOPIC = 2;
    private final int VIEW_TYPE_DIVIDER = 3;
    private final int VIEW_TYPE_DELETE_ALL = 4;

    Adapter adapter;

    RecyclerListView recyclerListView;
    long dialogId;

    ArrayList<Item> items = new ArrayList<>();
    HashSet<Integer> exceptionsTopics = new HashSet<>();

    public TopicsNotifySettingsFragments(Bundle bundle) {
        super(bundle);
    }

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    return;
                }
            }
        });
        actionBar.setTitle(LocaleController.getString(R.string.NotificationsExceptions));
        recyclerListView = new RecyclerListView(context);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(defaultItemAnimator);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context));
        recyclerListView.setAdapter(adapter = new Adapter());
        recyclerListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (items.get(position).viewType == VIEW_TYPE_ADD_EXCEPTION) {
                    Bundle bundle = new Bundle();
                    bundle.putLong("chat_id", -dialogId);
                    bundle.putBoolean("for_select", true);
                    TopicsFragment topicsFragment = new TopicsFragment(bundle);
                    topicsFragment.setExcludeTopics(exceptionsTopics);
                    topicsFragment.setOnTopicSelectedListener((topic) -> {
                        Bundle bundle2 = new Bundle();
                        bundle2.putLong("dialog_id", dialogId);
                        bundle2.putInt("topic_id", topic.id);
                        bundle2.putBoolean("exception", true);
                        ProfileNotificationsActivity fragment = new ProfileNotificationsActivity(bundle2);
                        fragment.setDelegate(exception -> {
                            exceptionsTopics.add(topic.id);
                            updateRows();
                        });
                        presentFragment(fragment);
                    });
                    presentFragment(topicsFragment);
                }

                if (items.get(position).viewType == VIEW_TYPE_TOPIC) {
                    TLRPC.TL_forumTopic topic = (TLRPC.TL_forumTopic) items.get(position).topic;
                    Bundle bundle = new Bundle();
                    bundle.putLong("dialog_id", dialogId);
                    bundle.putInt("topic_id", topic.id);
                    bundle.putBoolean("exception", false);
                    ProfileNotificationsActivity topicsFragment = new ProfileNotificationsActivity(bundle);
                    topicsFragment.setDelegate(new ProfileNotificationsActivity.ProfileNotificationsActivityDelegate() {
                        @Override
                        public void didCreateNewException(NotificationsSettingsActivity.NotificationException exception) {

                        }

                        @Override
                        public void didRemoveException(long dialog_id) {
                            removeException(topic.id);
                            AndroidUtilities.runOnUIThread(() -> {
                                exceptionsTopics.remove(topic.id);
                                updateRows();
                            }, 300);

                        }
                    });
                    presentFragment(topicsFragment);
                }

                if (items.get(position).viewType == VIEW_TYPE_DELETE_ALL) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("NotificationsDeleteAllExceptionTitle", R.string.NotificationsDeleteAllExceptionTitle));
                    builder.setMessage(LocaleController.getString("NotificationsDeleteAllExceptionAlert", R.string.NotificationsDeleteAllExceptionAlert));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                        Iterator<Integer> iterator = exceptionsTopics.iterator();
                        while (iterator.hasNext()) {
                            int topicId = iterator.next();
                            removeException(topicId);
                        }
                        exceptionsTopics.clear();
                        updateRows();
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                }
            }
        });
        frameLayout.addView(recyclerListView);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        return fragmentView;
    }

    private void removeException(int topicId) {
        getNotificationsController().getNotificationsSettingsFacade().clearPreference(dialogId, topicId);
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        TLRPC.TL_inputNotifyForumTopic topicPeer = new TLRPC.TL_inputNotifyForumTopic();
        topicPeer.peer = getMessagesController().getInputPeer(dialogId);
        topicPeer.top_msg_id = topicId;
        req.peer = topicPeer;
        getConnectionsManager().sendRequest(req, (response, error) -> {
        });
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = arguments.getLong("dialog_id");
        updateRows();
        return super.onFragmentCreate();
    }

    private void updateRows() {
        boolean animated = !isPaused && adapter != null;
        ArrayList<Item> oldItems = null;
        if (animated) {
            oldItems = new ArrayList();
            oldItems.addAll(items);
        }

        items.clear();
        items.add(new Item(VIEW_TYPE_ADD_EXCEPTION, null));
        ArrayList<TLRPC.TL_forumTopic> topics = getMessagesController().getTopicsController().getTopics(-dialogId);
        boolean added = false;
        if (topics != null) {
            for (int i = 0; i < topics.size(); i++) {
                if (exceptionsTopics.contains(topics.get(i).id)) {
                    added = true;
                    items.add(new Item(VIEW_TYPE_TOPIC, topics.get(i)));
                }
            }
        }
        if (added) {
            items.add(new Item(VIEW_TYPE_DIVIDER, null));
            items.add(new Item(VIEW_TYPE_DELETE_ALL, null));
        }
        items.add(new Item(VIEW_TYPE_DIVIDER, null));

        if (adapter != null) {
            adapter.setItems(oldItems, items);
        }
    }

    public void setExceptions(HashSet<Integer> notificationsExceptionTopics) {
        exceptionsTopics = notificationsExceptionTopics;
    }

    private class Adapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case VIEW_TYPE_TOPIC:
                    view = new TopicExceptionCell(parent.getContext());
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_ADD_EXCEPTION:
                    TextCell textCell = new TextCell(parent.getContext());
                    textCell.setTextAndIcon(LocaleController.getString("NotificationsAddAnException", R.string.NotificationsAddAnException), R.drawable.msg_contact_add, true);
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_DIVIDER:
                    view = new ShadowSectionCell(parent.getContext());
                    break;
                case VIEW_TYPE_DELETE_ALL:
                    textCell = new TextCell(parent.getContext());
                    textCell.setText(LocaleController.getString("NotificationsDeleteAllException", R.string.NotificationsDeleteAllException), false);
                    textCell.setColors(null, Theme.key_text_RedRegular);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (items.get(position).viewType == VIEW_TYPE_TOPIC) {
                TopicExceptionCell cell = (TopicExceptionCell) holder.itemView;
                cell.setTopic(dialogId, items.get(position).topic);
                cell.drawDivider = !(position != items.size() - 1 && items.get(position + 1).viewType != VIEW_TYPE_TOPIC);
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

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_ADD_EXCEPTION || holder.getItemViewType() == VIEW_TYPE_TOPIC || holder.getItemViewType() == VIEW_TYPE_DELETE_ALL;
        }
    }

    private class Item extends AdapterWithDiffUtils.Item {
        final TLRPC.TL_forumTopic topic;

        private Item(int viewType, TLRPC.TL_forumTopic object) {
            super(viewType, false);
            this.topic = object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            if (viewType != item.viewType) {
                return false;
            }
            if (topic != null && item.topic != null) {
                return topic.id == item.topic.id;
            }
            return true;
        }
    }
}
