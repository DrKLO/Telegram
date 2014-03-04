/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Views.BaseFragment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView messagesListView;
    private MessagesAdapter messagesListViewAdapter;
    private TextView searchEmptyView;
    private View progressView;
    private SupportMenuItem searchItem;
    private View empryView;
    private SearchView searchView;
    public int selectAlertString = 0;
    private boolean serverOnly = false;

    private static boolean dialogsLoaded = false;
    private boolean searching = false;
    private boolean searchWas = false;
    private boolean onlySelect = false;
    private int activityToken = (int)(MessagesController.random.nextDouble() * Integer.MAX_VALUE);
    private long selectedDialog;

    private Timer searchDialogsTimer;
    public ArrayList<TLObject> searchResult;
    public ArrayList<CharSequence> searchResultNames;

    public MessagesActivityDelegate delegate;

    public static interface MessagesActivityDelegate {
        public abstract void didSelectDialog(MessagesActivity fragment, long dialog_id);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.Instance.addObserver(this, 999);
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, 1234);
        if (getArguments() != null) {
            onlySelect = getArguments().getBoolean("onlySelect", false);
            serverOnly = getArguments().getBoolean("serverOnly", false);
        }
        if (!dialogsLoaded) {
            MessagesController.Instance.loadDialogs(0, 0, 100, true);
            ContactsController.Instance.checkAppAccount();
            dialogsLoaded = true;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.Instance.removeObserver(this, 999);
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.Instance.removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.removeObserver(this, 1234);
        delegate = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.messages_list, container, false);

            messagesListViewAdapter = new MessagesAdapter(parentActivity);

            messagesListView = (ListView)fragmentView.findViewById(R.id.messages_list_view);
            messagesListView.setAdapter(messagesListViewAdapter);

            progressView = fragmentView.findViewById(R.id.progressLayout);
            messagesListViewAdapter.notifyDataSetChanged();
            searchEmptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            empryView = fragmentView.findViewById(R.id.list_empty_view);

            if (MessagesController.Instance.loadingDialogs && MessagesController.Instance.dialogs.isEmpty()) {
                messagesListView.setEmptyView(null);
                searchEmptyView.setVisibility(View.GONE);
                empryView.setVisibility(View.GONE);
                progressView.setVisibility(View.VISIBLE);
            } else {
                if (searching && searchWas) {
                    messagesListView.setEmptyView(searchEmptyView);
                    empryView.setVisibility(View.GONE);
                } else {
                    messagesListView.setEmptyView(empryView);
                    searchEmptyView.setVisibility(View.GONE);
                }
                progressView.setVisibility(View.GONE);
            }

            messagesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    long dialog_id = 0;
                    if (searching && searchWas) {
                        if (i >= searchResult.size()) {
                            return;
                        }
                        TLObject obj = searchResult.get(i);
                        if (obj instanceof TLRPC.User) {
                            dialog_id = ((TLRPC.User) obj).id;
                        } else if (obj instanceof TLRPC.Chat) {
                            dialog_id = -((TLRPC.Chat) obj).id;
                        } else if (obj instanceof TLRPC.EncryptedChat) {
                            dialog_id = ((long)((TLRPC.EncryptedChat) obj).id) << 32;
                        }
                    } else {
                        if (serverOnly) {
                            if (i >= MessagesController.Instance.dialogsServerOnly.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.Instance.dialogsServerOnly.get(i);
                            dialog_id = dialog.id;
                        } else {
                            if (i >= MessagesController.Instance.dialogs.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.Instance.dialogs.get(i);
                            dialog_id = dialog.id;
                        }
                    }
                    if (onlySelect) {
                        didSelectResult(dialog_id, true);
                    } else {
                        ChatActivity fragment = new ChatActivity();
                        Bundle bundle = new Bundle();
                        int lower_part = (int)dialog_id;
                        if (lower_part != 0) {
                            if (lower_part > 0) {
                                bundle.putInt("user_id", lower_part);
                                fragment.setArguments(bundle);
                                ((LaunchActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                            } else if (lower_part < 0) {
                                bundle.putInt("chat_id", -lower_part);
                                fragment.setArguments(bundle);
                                ((LaunchActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                            }
                        } else {
                            int id = (int)(dialog_id >> 32);
                            bundle.putInt("enc_id", id);
                            fragment.setArguments(bundle);
                            ((LaunchActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                        }
                    }
                }
            });

            messagesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (onlySelect) {
                        return false;
                    }
                    TLRPC.TL_dialog dialog;
                    if (serverOnly) {
                        if (i >= MessagesController.Instance.dialogsServerOnly.size()) {
                            return false;
                        }
                        dialog = MessagesController.Instance.dialogsServerOnly.get(i);
                    } else {
                        if (i >= MessagesController.Instance.dialogs.size()) {
                            return false;
                        }
                        dialog = MessagesController.Instance.dialogs.get(i);
                    }
                    selectedDialog = dialog.id;

                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(getStringEntry(R.string.AppName));

                    if ((int)selectedDialog < 0) {
                        builder.setItems(new CharSequence[]{getStringEntry(R.string.ClearHistory), getStringEntry(R.string.DeleteChat)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    MessagesController.Instance.deleteDialog(selectedDialog, 0, true);
                                } else if (which == 1) {
                                    MessagesController.Instance.deleteUserFromChat((int) -selectedDialog, MessagesController.Instance.users.get(UserConfig.clientUserId), null);
                                    MessagesController.Instance.deleteDialog(selectedDialog, 0, false);
                                }
                            }
                        });
                    } else {
                        builder.setMessage(getStringEntry(R.string.DeleteChatQuestion));
                        builder.setPositiveButton(getStringEntry(R.string.Delete), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.Instance.deleteDialog(selectedDialog, 0, false);
                            }
                        });
                    }
                    builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                    builder.show().setCanceledOnTouchOutside(true);
                    return true;
                }
            });

            messagesListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (searching && searchWas) {
                        return;
                    }
                    if (visibleItemCount > 0) {
                        if (absListView.getLastVisiblePosition() == MessagesController.Instance.dialogs.size() && !serverOnly || absListView.getLastVisiblePosition() == MessagesController.Instance.dialogsServerOnly.size() && serverOnly) {
                            MessagesController.Instance.loadDialogs(MessagesController.Instance.dialogs.size(), MessagesController.Instance.dialogsServerOnly.size(), 100, true);
                        }
                    }
                }
            });

            if (MessagesController.Instance.loadingDialogs) {
                progressView.setVisibility(View.VISIBLE);
            }

        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        final ActionBar actionBar = parentActivity.getSupportActionBar();
        if (onlySelect) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setDisplayShowCustomEnabled(false);
            actionBar.setSubtitle(null);
            actionBar.setCustomView(null);
            actionBar.setTitle(getStringEntry(R.string.SelectChat));
            ((LaunchActivity)parentActivity).fixBackButton();
        } else {
            ImageView view = (ImageView)parentActivity.findViewById(16908332);
            if (view == null) {
                view = (ImageView)parentActivity.findViewById(R.id.home);
            }
            if (view != null) {
                view.setPadding(Utilities.dp(6), 0, Utilities.dp(6), 0);
            }
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setDisplayShowCustomEnabled(false);
            actionBar.setCustomView(null);
            actionBar.setSubtitle(null);
            actionBar.setTitle(getStringEntry(R.string.AppName));
        }

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        if (messagesListViewAdapter != null) {
            messagesListViewAdapter.notifyDataSetChanged();
        }
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.dialogsNeedReload) {
            if (messagesListViewAdapter != null) {
                messagesListViewAdapter.notifyDataSetChanged();
            }
            if (messagesListView != null) {
                if (MessagesController.Instance.loadingDialogs && MessagesController.Instance.dialogs.isEmpty()) {
                    if (messagesListView.getEmptyView() != null) {
                        messagesListView.setEmptyView(null);
                    }
                    searchEmptyView.setVisibility(View.GONE);
                    empryView.setVisibility(View.GONE);
                    progressView.setVisibility(View.VISIBLE);
                } else {
                    if (messagesListView.getEmptyView() == null) {
                        if (searching && searchWas) {
                            messagesListView.setEmptyView(searchEmptyView);
                            empryView.setVisibility(View.GONE);
                        } else {
                            messagesListView.setEmptyView(empryView);
                            searchEmptyView.setVisibility(View.GONE);
                        }
                    }
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == 999) {
            if (messagesListView != null) {
                updateVisibleRows(0);
            }
        } else if (id == MessagesController.updateInterfaces) {
            updateVisibleRows((Integer)args[0]);
        } else if (id == MessagesController.reloadSearchResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                updateSearchResults((ArrayList<TLObject>)args[1], (ArrayList<CharSequence>)args[2], (ArrayList<TLRPC.User>)args[3]);
            }
        } else if (id == 1234) {
            dialogsLoaded = false;
        } else if (id == MessagesController.encryptedChatUpdated) {
            updateVisibleRows(0);
        } else if (id == MessagesController.contactsDidLoaded) {
            updateVisibleRows(0);
        }
    }

    private void updateVisibleRows(int mask) {
        if (messagesListView == null) {
            return;
        }
        int count = messagesListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = messagesListView.getChildAt(a);
            if (child instanceof DialogCell) {
                ((DialogCell) child).update(mask);
            } else if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
        }
    }

    @Override
    public void willBeHidden() {
        if (searchItem != null) {
            if (searchItem.isActionViewExpanded()) {
                searchItem.collapseActionView();
            }
        }
    }

    private void didSelectResult(final long dialog_id, boolean useAlert) {
        if (useAlert && selectAlertString != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(R.string.AppName);
            int lower_part = (int)dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    TLRPC.User user = MessagesController.Instance.users.get(lower_part);
                    if (user == null) {
                        return;
                    }
                    builder.setMessage(String.format(getStringEntry(selectAlertString), Utilities.formatName(user.first_name, user.last_name)));
                } else if (lower_part < 0) {
                    TLRPC.Chat chat = MessagesController.Instance.chats.get(-lower_part);
                    if (chat == null) {
                        return;
                    }
                    builder.setMessage(String.format(getStringEntry(selectAlertString), chat.title));
                }
            } else {
                int chat_id = (int)(dialog_id >> 32);
                TLRPC.EncryptedChat chat = MessagesController.Instance.encryptedChats.get(chat_id);
                TLRPC.User user = MessagesController.Instance.users.get(chat.user_id);
                if (user == null) {
                    return;
                }
                builder.setMessage(String.format(getStringEntry(selectAlertString), Utilities.formatName(user.first_name, user.last_name)));
            }
            builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(dialog_id, false);
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            builder.show().setCanceledOnTouchOutside(true);
        } else {
            if (delegate != null) {
                delegate.didSelectDialog(MessagesActivity.this, dialog_id);
                delegate = null;
            } else {
                finishFragment();
            }
        }
    }

    public void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (TLObject obj : result) {
                    if (obj instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User)obj;
                        MessagesController.Instance.users.putIfAbsent(user.id, user);
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat)obj;
                        MessagesController.Instance.chats.putIfAbsent(chat.id, chat);
                    } else if (obj instanceof TLRPC.EncryptedChat) {
                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)obj;
                        MessagesController.Instance.encryptedChats.putIfAbsent(chat.id, chat);
                    }
                }
                for (TLRPC.User user : encUsers) {
                    MessagesController.Instance.users.putIfAbsent(user.id, user);
                }
                searchResult = result;
                searchResultNames = names;
                if (searching) {
                    messagesListViewAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    public void searchDialogs(final String query) {
        if (query == null) {
            searchResult = null;
            searchResultNames = null;
        } else {
            try {
                if (searchDialogsTimer != null) {
                    searchDialogsTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            searchDialogsTimer = new Timer();
            searchDialogsTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchDialogsTimer.cancel();
                        searchDialogsTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    MessagesStorage.Instance.searchDialogs(activityToken, query, !serverOnly);
                }
            }, 100, 300);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (onlySelect) {
            inflater.inflate(R.menu.messages_list_select_menu, menu);
        } else {
            inflater.inflate(R.menu.messages_list_menu, menu);
        }
        searchItem = (SupportMenuItem)menu.findItem(R.id.messages_list_menu_search);
        searchView = (SearchView) searchItem.getActionView();

        TextView textView = (TextView) searchView.findViewById(R.id.search_src_text);
        if (textView != null) {
            textView.setTextColor(0xffffffff);
            try {
                Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                mCursorDrawableRes.setAccessible(true);
                mCursorDrawableRes.set(textView, R.drawable.search_carret);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ImageView img = (ImageView) searchView.findViewById(R.id.search_close_btn);
        if (img != null) {
            img.setImageResource(R.drawable.ic_msg_btn_cross_custom);
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                searchDialogs(s);
                if (s.length() != 0) {
                    searchWas = true;
                    if (messagesListViewAdapter != null) {
                        messagesListViewAdapter.notifyDataSetChanged();
                    }
                    if (searchEmptyView != null) {
                        messagesListView.setEmptyView(searchEmptyView);
                        empryView.setVisibility(View.GONE);
                    }
                }
                return true;
            }
        });

        searchItem.setSupportOnActionExpandListener(new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                if (parentActivity != null) {
                    parentActivity.getSupportActionBar().setIcon(R.drawable.ic_ab_logo);
                }
                searching = true;
                if (messagesListView != null) {
                    messagesListView.setEmptyView(searchEmptyView);
                }
                if (empryView != null) {
                    empryView.setVisibility(View.GONE);
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.setQuery("", false);
                searchDialogs(null);
                searching = false;
                searchWas = false;
                if (messagesListView != null) {
                    messagesListView.setEmptyView(empryView);
                    searchEmptyView.setVisibility(View.GONE);
                }
                if (messagesListViewAdapter != null) {
                    messagesListViewAdapter.notifyDataSetChanged();
                }
                if (onlySelect) {
                    ((LaunchActivity)parentActivity).fixBackButton();
                }
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        FragmentActivity inflaterActivity = parentActivity;
        if (inflaterActivity == null) {
            inflaterActivity = getActivity();
        }
        if (inflaterActivity == null) {
            return true;
        }
        switch (itemId) {

            case R.id.messages_list_menu_settings: {
                ((LaunchActivity)inflaterActivity).presentFragment(new SettingsActivity(), "settings", false);
                break;
            }
            case R.id.messages_list_menu_contacts: {
                ((LaunchActivity)inflaterActivity).presentFragment(new ContactsActivity(), "contacts", false);
                break;
            }
            case R.id.messages_list_menu_new_messages: {
                BaseFragment fragment = new ContactsActivity();
                Bundle bundle = new Bundle();
                bundle.putBoolean("onlyUsers", true);
                bundle.putBoolean("destroyAfterSelect", true);
                bundle.putBoolean("usersAsSections", true);
                fragment.animationType = 1;
                fragment.setArguments(bundle);
                ((LaunchActivity)inflaterActivity).presentFragment(fragment, "contacts_chat", false);
                break;
            }
            case R.id.messages_list_menu_new_secret_chat: {
                BaseFragment fragment = new ContactsActivity();
                Bundle bundle = new Bundle();
                bundle.putBoolean("onlyUsers", true);
                bundle.putBoolean("destroyAfterSelect", true);
                bundle.putBoolean("usersAsSections", true);
                bundle.putBoolean("createSecretChat", true);
                fragment.animationType = 1;
                fragment.setArguments(bundle);
                ((LaunchActivity)inflaterActivity).presentFragment(fragment, "contacts_chat", false);
                break;
            }
            case R.id.messages_list_menu_new_chat: {
                ((LaunchActivity)inflaterActivity).presentFragment(new GroupCreateActivity(), "group_create", false);
                break;
            }
            case android.R.id.home:
                if (onlySelect) {
                    finishFragment();
                }
                break;
        }
        return true;
    }

    private class MessagesAdapter extends BaseAdapter {
        private Context mContext;

        public MessagesAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            if (searching && searchWas) {
                if (searchResult == null) {
                    return 0;
                }
                return searchResult.size();
            }
            int count;
            if (serverOnly) {
                count = MessagesController.Instance.dialogsServerOnly.size();
            } else {
                count = MessagesController.Instance.dialogs.size();
            }
            if (count == 0 && MessagesController.Instance.loadingDialogs) {
                return 0;
            }
            if (!MessagesController.Instance.dialogsEndReached) {
                count++;
            }
            return count;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (searching && searchWas) {
                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                }
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                TLRPC.EncryptedChat encryptedChat = null;

                TLObject obj = searchResult.get(i);
                if (obj instanceof TLRPC.User) {
                    user = MessagesController.Instance.users.get(((TLRPC.User)obj).id);
                } else if (obj instanceof TLRPC.Chat) {
                    chat = MessagesController.Instance.chats.get(((TLRPC.Chat) obj).id);
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.Instance.encryptedChats.get(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.Instance.users.get(encryptedChat.user_id);
                }

                ((ChatOrUserCell)view).setData(user, chat, encryptedChat, searchResultNames.get(i), null);

                return view;
            }
            int type = getItemViewType(i);
            if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.loading_more_layout, viewGroup, false);
                }
                return view;
            }

            if (view == null) {
                view = new DialogCell(mContext);
            }
            if (serverOnly) {
                ((DialogCell)view).setDialog(MessagesController.Instance.dialogsServerOnly.get(i));
            } else {
                ((DialogCell)view).setDialog(MessagesController.Instance.dialogs.get(i));
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (searching && searchWas) {
                TLObject obj = searchResult.get(i);
                if (obj instanceof TLRPC.User || obj instanceof TLRPC.EncryptedChat) {
                    return 2;
                } else {
                    return 3;
                }
            }
            if (serverOnly && i == MessagesController.Instance.dialogsServerOnly.size() || !serverOnly && i == MessagesController.Instance.dialogs.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            if (searching && searchWas) {
                return searchResult == null || searchResult.size() == 0;
            }
            if (MessagesController.Instance.loadingDialogs && MessagesController.Instance.dialogs.isEmpty()) {
                return false;
            }
            int count;
            if (serverOnly) {
                count = MessagesController.Instance.dialogsServerOnly.size();
            } else {
                count = MessagesController.Instance.dialogs.size();
            }
            if (count == 0 && MessagesController.Instance.loadingDialogs) {
                return true;
            }
            if (!MessagesController.Instance.dialogsEndReached) {
                count++;
            }
            return count == 0;
        }
    }
}
