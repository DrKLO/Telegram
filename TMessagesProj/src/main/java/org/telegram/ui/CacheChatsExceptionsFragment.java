package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CacheByChatsController;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class CacheChatsExceptionsFragment extends BaseFragment {

    private final int VIEW_TYPE_ADD_EXCEPTION = 1;
    private final int VIEW_TYPE_CHAT= 2;
    private final int VIEW_TYPE_DIVIDER = 3;
    private final int VIEW_TYPE_DELETE_ALL = 4;

    Adapter adapter;

    RecyclerListView recyclerListView;

    ArrayList<Item> items = new ArrayList<>();
    ArrayList<CacheByChatsController.KeepMediaException> exceptionsDialogs = new ArrayList<>();
    int currentType;

    public CacheChatsExceptionsFragment(Bundle bundle) {
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
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            if (items.get(position).viewType == VIEW_TYPE_ADD_EXCEPTION) {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("checkCanWrite", false);
                if (currentType == CacheControlActivity.KEEP_MEDIA_TYPE_GROUP) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY);
                } else if (currentType == CacheControlActivity.KEEP_MEDIA_TYPE_CHANNEL) {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY);
                } else {
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
                }
                args.putBoolean("allowGlobalSearch", false);
                DialogsActivity activity = new DialogsActivity(args);
                activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                    activity.finishFragment();
                    CacheByChatsController.KeepMediaException newException = null;
                    for (int i = 0; i < dids.size(); i++) {
                        boolean contains = false;
                        for (int k = 0; k < exceptionsDialogs.size(); k++) {
                            if (exceptionsDialogs.get(k).dialogId == dids.get(i).dialogId) {
                                newException = exceptionsDialogs.get(k);
                                contains = true;
                                break;
                            }
                        }
                        if (!contains) {
                            int startFrom = CacheByChatsController.KEEP_MEDIA_FOREVER;
                            if (getMessagesController().getCacheByChatsController().getKeepMedia(currentType) == CacheByChatsController.KEEP_MEDIA_FOREVER) {
                                startFrom = CacheByChatsController.KEEP_MEDIA_ONE_DAY;
                            }
                            exceptionsDialogs.add(newException = new CacheByChatsController.KeepMediaException(dids.get(i).dialogId, startFrom));
                        }
                    }
                    getMessagesController().getCacheByChatsController().saveKeepMediaExceptions(currentType, exceptionsDialogs);
                    updateRows();
                    if (newException != null) {
                        int p = 0;
                        for (int i = 0; i < items.size(); i++) {
                            if (items.get(i).exception != null && items.get(i).exception.dialogId == newException.dialogId) {
                                p = i;
                                break;
                            }
                        }
                        recyclerListView.scrollToPosition(p);
                        int finalP = p;
                        showPopupFor(newException);
                    }
                    return true;
                });
                presentFragment(activity);
            } else if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                CacheByChatsController.KeepMediaException keepMediaException = items.get(position).exception;
                KeepMediaPopupView windowLayout = new KeepMediaPopupView(CacheChatsExceptionsFragment.this, view.getContext());
                windowLayout.updateForDialog(false);
                ActionBarPopupWindow popupWindow = AlertsCreator.createSimplePopup(CacheChatsExceptionsFragment.this, windowLayout, view, x, y);
                windowLayout.setParentWindow(popupWindow);
                windowLayout.setCallback((type, keepMedia) -> {
                    if (keepMedia == CacheByChatsController.KEEP_MEDIA_DELETE) {
                        exceptionsDialogs.remove(keepMediaException);
                        updateRows();
                    } else {
                        keepMediaException.keepMedia = keepMedia;
                        AndroidUtilities.updateVisibleRows(recyclerListView);
                    }

                    getMessagesController().getCacheByChatsController().saveKeepMediaExceptions(currentType, exceptionsDialogs);

                });
            } else if (items.get(position).viewType == VIEW_TYPE_DELETE_ALL) {
                AlertDialog alertDialog = AlertsCreator.createSimpleAlert(getContext(),
                        LocaleController.getString(R.string.NotificationsDeleteAllExceptionTitle),
                        LocaleController.getString(R.string.NotificationsDeleteAllExceptionAlert),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            exceptionsDialogs.clear();
                            getMessagesController().getCacheByChatsController().saveKeepMediaExceptions(currentType, exceptionsDialogs);
                            updateRows();
                            finishFragment();
                        }, null).create();
                alertDialog.show();
                alertDialog.redPositive();
            }
        });
        frameLayout.addView(recyclerListView);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        updateRows();
        return fragmentView;
    }

    public void showPopupFor(CacheByChatsController.KeepMediaException newException) {
        AndroidUtilities.runOnUIThread(() -> {
            int p = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).exception != null && items.get(i).exception.dialogId == newException.dialogId) {
                    p = i;
                    break;
                }
            }
            RecyclerView.ViewHolder viewHolder = recyclerListView.findViewHolderForAdapterPosition(p);
            if (viewHolder != null) {
                KeepMediaPopupView windowLayout = new KeepMediaPopupView(CacheChatsExceptionsFragment.this, getContext());
                windowLayout.updateForDialog(true);
                ActionBarPopupWindow popupWindow = AlertsCreator.createSimplePopup(CacheChatsExceptionsFragment.this, windowLayout, viewHolder.itemView, viewHolder.itemView.getMeasuredWidth() / 2f, viewHolder.itemView.getMeasuredHeight() / 2f);
                windowLayout.setParentWindow(popupWindow);
                windowLayout.setCallback((type, keepMedia) -> {
                    newException.keepMedia = keepMedia;
                    getMessagesController().getCacheByChatsController().saveKeepMediaExceptions(currentType, exceptionsDialogs);
                    AndroidUtilities.updateVisibleRows(recyclerListView);
                });
            }
        }, 150);
    }

    @Override
    public boolean onFragmentCreate() {
        currentType = getArguments().getInt("type");
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
        boolean added = false;
        for (CacheByChatsController.KeepMediaException exception : exceptionsDialogs) {
            items.add(new Item(VIEW_TYPE_CHAT, exception));
            added = true;
        }

        if (added) {
            items.add(new Item(VIEW_TYPE_DIVIDER, null));
            items.add(new Item(VIEW_TYPE_DELETE_ALL, null));
        }
        items.add(new Item(VIEW_TYPE_DIVIDER, null));

        if (adapter != null) {
            if (oldItems != null) {
                adapter.setItems(oldItems, items);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    public void setExceptions(ArrayList<CacheByChatsController.KeepMediaException> notificationsExceptionTopics) {
        exceptionsDialogs = notificationsExceptionTopics;
        updateRows();
    }

    private class Adapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case VIEW_TYPE_CHAT:
                    view = new UserCell(parent.getContext(), 4, 0, false, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_ADD_EXCEPTION:
                    TextCell textCell = new TextCell(parent.getContext());
                    textCell.setTextAndIcon(LocaleController.getString(R.string.NotificationsAddAnException), R.drawable.msg_contact_add, true);
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_DIVIDER:
                    view = new ShadowSectionCell(parent.getContext());
                    break;
                case VIEW_TYPE_DELETE_ALL:
                    textCell = new TextCell(parent.getContext());
                    textCell.setText(LocaleController.getString(R.string.NotificationsDeleteAllException), false);
                    textCell.setColors(-1, Theme.key_text_RedRegular);
                    view = textCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (items.get(position).viewType == VIEW_TYPE_CHAT) {
                UserCell cell = (UserCell) holder.itemView;
                CacheByChatsController.KeepMediaException exception = items.get(position).exception;
                TLObject object = getMessagesController().getUserOrChat(exception.dialogId);
                String title = null;
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.self) {
                        title = LocaleController.getString(R.string.SavedMessages);
                    } else {
                        title = ContactsController.formatName(user.first_name, user.last_name);
                    }
                } else if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) object;
                    title = chat.title;
                }
                cell.setSelfAsSavedMessages(true);
                cell.setData(object, title, CacheByChatsController.getKeepMediaString(exception.keepMedia), 0, !(position != items.size() - 1 && items.get(position + 1).viewType != VIEW_TYPE_CHAT));
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
            return holder.getItemViewType() == VIEW_TYPE_ADD_EXCEPTION || holder.getItemViewType() == VIEW_TYPE_CHAT || holder.getItemViewType() == VIEW_TYPE_DELETE_ALL;
        }
    }

    private class Item extends AdapterWithDiffUtils.Item {
        final CacheByChatsController.KeepMediaException exception;

        private Item(int viewType, CacheByChatsController.KeepMediaException exception) {
            super(viewType, false);
            this.exception = exception;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            if (viewType != item.viewType) {
                return false;
            }
            if (exception != null && item.exception != null) {
                return exception.dialogId == item.exception.dialogId;
            }
            return true;
        }
    }
}
