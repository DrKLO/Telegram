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
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.util.Log;
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

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.objects.MessageObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;
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
    private boolean isRTL;

    private static boolean dialogsLoaded = false;
    private boolean searching = false;
    private boolean searchWas = false;
    private float density = 1;
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
        density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
        NotificationCenter.Instance.addObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.Instance.addObserver(this, 999);
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.Instance.addObserver(this, MessagesController.userPrintUpdateAll);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, 1234);
        if (getArguments() != null) {
            onlySelect = getArguments().getBoolean("onlySelect", false);
            serverOnly = getArguments().getBoolean("serverOnly", false);
        }
        if (!dialogsLoaded) {
            MessagesController.Instance.readContacts();
            MessagesController.Instance.loadDialogs(0, 0, 100, true);
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
        NotificationCenter.Instance.removeObserver(this, MessagesController.userPrintUpdateAll);
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
            fragmentView = inflater.inflate(R.layout.messages_list, container, false);

            messagesListViewAdapter = new MessagesAdapter(parentActivity);
            Locale locale = Locale.getDefault();
            String lang = locale.getLanguage();
            isRTL = lang != null && lang.toLowerCase().equals("ar");

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
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                            } else if (lower_part < 0) {
                                bundle.putInt("chat_id", -lower_part);
                                fragment.setArguments(bundle);
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                            }
                        } else {
                            int id = (int)(dialog_id >> 32);
                            bundle.putInt("enc_id", id);
                            fragment.setArguments(bundle);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
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
                                    MessagesController.Instance.deleteUserFromChat((int) -selectedDialog, UserConfig.clientUserId, null);
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
            actionBar.setTitle(getStringEntry(R.string.AppName));
            ((ApplicationActivity)parentActivity).fixBackButton();
        } else {
            ImageView view = (ImageView)parentActivity.findViewById(16908332);
            if (view == null) {
                view = (ImageView)parentActivity.findViewById(R.id.home);
            }
            if (view != null) {
                view.setPadding((int)(density * 6), 0, (int)(density * 6), 0);
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
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
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
                updateVisibleRows();
            }
        } else if (id == MessagesController.updateInterfaces) {
            if (messagesListViewAdapter != null) {
                messagesListViewAdapter.notifyDataSetChanged();
            }
        } else if (id == MessagesController.reloadSearchResults) {
            int token = (Integer)args[0];
            if (token == activityToken) {
                updateSearchResults((ArrayList<TLObject>)args[1], (ArrayList<CharSequence>)args[2], (ArrayList<TLRPC.User>)args[3]);
            }
        } else if (id == 1234) {
            dialogsLoaded = false;
        } else if (id == MessagesController.userPrintUpdateAll) {
            if (messagesListView != null) {
                updateVisibleRows();
            }
        } else if (id == MessagesController.encryptedChatUpdated) {
            if (messagesListView != null) {
                updateVisibleRows();
            }
        } else if (id == MessagesController.contactsDidLoaded) {
            if (messagesListView != null) {
                updateVisibleRows();
            }
        }
    }

    private void updateVisibleRows() {
        if (searching && searchWas) {
            messagesListView.invalidate();
        } else {
            int count = messagesListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = messagesListView.getChildAt(a);
                Object tag = child.getTag();
                if (tag instanceof MessagesListRowHolder) {
                    ((MessagesListRowHolder) tag).update();
                }
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
                    builder.setMessage(String.format(getStringEntry(selectAlertString), Utilities.formatName(user.first_name, user.last_name)));
                } else if (lower_part < 0) {
                    TLRPC.Chat chat = MessagesController.Instance.chats.get(-lower_part);
                    builder.setMessage(String.format(getStringEntry(selectAlertString), chat.title));
                }
            } else {
                int chat_id = (int)(dialog_id >> 32);
                TLRPC.EncryptedChat chat = MessagesController.Instance.encryptedChats.get(chat_id);
                TLRPC.User user = MessagesController.Instance.users.get(chat.user_id);
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
                    ((ApplicationActivity)parentActivity).fixBackButton();
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
                ((ApplicationActivity)inflaterActivity).presentFragment(new SettingsActivity(), "settings", false);
                break;
            }
            case R.id.messages_list_menu_contacts: {
                ((ApplicationActivity)inflaterActivity).presentFragment(new ContactsActivity(), "contacts", false);
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
                ((ApplicationActivity)inflaterActivity).presentFragment(fragment, "contacts_chat", false);
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
                ((ApplicationActivity)inflaterActivity).presentFragment(fragment, "contacts_chat", false);
                break;
            }
            case R.id.messages_list_menu_new_chat: {
                ((ApplicationActivity)inflaterActivity).presentFragment(new GroupCreateActivity(), "group_create", false);
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
                int type = getItemViewType(i);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    if (type == 2) {
                        view = li.inflate(R.layout.messages_search_user_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.messages_search_chat_layout, viewGroup, false);
                    }
                    View v = view.findViewById(R.id.settings_row_divider);
                    v.setVisibility(View.VISIBLE);
                    view.setPadding((int)(11 * density), 0, (int)(11 * density), 0);
                }
                MessagesListSearchRowHolder holder = (MessagesListSearchRowHolder)view.getTag();
                if (holder == null) {
                    holder = new MessagesListSearchRowHolder(view);
                    view.setTag(holder);
                }
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                TLRPC.EncryptedChat encryptedChat = null;

                TLObject obj = searchResult.get(i);
                CharSequence name = searchResultNames.get(i);
                if (obj instanceof TLRPC.User) {
                    user = MessagesController.Instance.users.get(((TLRPC.User)obj).id);
                } else if (obj instanceof TLRPC.Chat) {
                    chat = MessagesController.Instance.chats.get(((TLRPC.Chat) obj).id);
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.Instance.encryptedChats.get(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.Instance.users.get(encryptedChat.user_id);
                }

                holder.nameTextView.setText(name);
                if (encryptedChat != null) {
                    if (!isRTL) {
                        holder.nameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_green, 0, 0, 0);
                    } else {
                        holder.nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_lock_green, 0);
                    }
                    holder.nameTextView.setCompoundDrawablePadding((int)(4 * density));
                } else {
                    holder.nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    holder.nameTextView.setCompoundDrawablePadding(0);
                }

                TLRPC.FileLocation photo = null;
                int placeHolderId = 0;
                if (user != null) {
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                    placeHolderId = Utilities.getUserAvatarForId(user.id);
                } else if (chat != null) {
                    if (chat.photo != null) {
                        photo = chat.photo.photo_small;
                    }
                    placeHolderId = Utilities.getGroupAvatarForId(chat.id);
                }
                holder.avatarImage.setImage(photo, "50_50", placeHolderId);

                if (user != null) {
                    if (user.status == null) {
                        holder.messageTextView.setTextColor(0xff808080);
                        holder.messageTextView.setText(getStringEntry(R.string.Offline));
                    } else {
                        int currentTime = ConnectionsManager.Instance.getCurrentTime();
                        if (user.status.expires > currentTime || user.status.was_online > currentTime) {
                            holder.messageTextView.setTextColor(0xff316f9f);
                            holder.messageTextView.setText(getStringEntry(R.string.Online));
                        } else {
                            if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                                holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                            } else {
                                int value = user.status.was_online;
                                if (value == 0) {
                                    value = user.status.expires;
                                }
                                holder.messageTextView.setText(getStringEntry(R.string.LastSeen) + " " + Utilities.formatDateOnline(value));
                            }
                            holder.messageTextView.setTextColor(0xff808080);
                        }
                    }
                }

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
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = li.inflate(R.layout.messages_list_row, viewGroup, false);
            }

            MessagesListRowHolder holder = (MessagesListRowHolder)view.getTag();
            if (holder == null) {
                holder = new MessagesListRowHolder(view);
                view.setTag(holder);
            }
            if (serverOnly) {
                holder.dialog = MessagesController.Instance.dialogsServerOnly.get(i);
            } else {
                holder.dialog = MessagesController.Instance.dialogs.get(i);
            }
            holder.update();

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
            if (serverOnly) {
                if (i == MessagesController.Instance.dialogsServerOnly.size()) {
                    return 1;
                }
            } else {
                if (i == MessagesController.Instance.dialogs.size()) {
                    return 1;
                }
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

    private class MessagesListSearchRowHolder {
        public BackupImageView avatarImage;
        public TextView messageTextView;
        public TextView nameTextView;

        public MessagesListSearchRowHolder(View view) {
            messageTextView = (TextView)view.findViewById(R.id.messages_list_row_message);
            nameTextView = (TextView)view.findViewById(R.id.messages_list_row_name);
            avatarImage = (BackupImageView)view.findViewById(R.id.messages_list_row_avatar);
        }
    }

    private class MessagesListRowHolder {
        public ImageView errorImage;
        public TextView messagesCountImage;
        public BackupImageView avatarImage;
        public TextView timeTextView;
        public TextView messageTextView;
        public TextView nameTextView;
        public ImageView check1Image;
        public ImageView check2Image;
        public ImageView clockImage;
        public TLRPC.TL_dialog dialog;

        public MessagesListRowHolder(View view) {
            messageTextView = (TextView)view.findViewById(R.id.messages_list_row_message);
            nameTextView = (TextView)view.findViewById(R.id.messages_list_row_name);
            if (nameTextView != null) {
                Typeface typeface = Utilities.getTypeface("fonts/rmedium.ttf");
                nameTextView.setTypeface(typeface);
            }
            timeTextView = (TextView)view.findViewById(R.id.messages_list_row_time);
            avatarImage = (BackupImageView)view.findViewById(R.id.messages_list_row_avatar);
            messagesCountImage = (TextView)view.findViewById(R.id.messages_list_row_badge);
            errorImage = (ImageView)view.findViewById(R.id.messages_list_row_error);
            check1Image = (ImageView)view.findViewById(R.id.messages_list_row_check_half);
            check2Image = (ImageView)view.findViewById(R.id.messages_list_row_check);
            clockImage = (ImageView)view.findViewById(R.id.messages_list_row_clock);
        }

        public void update() {
            MessageObject message = MessagesController.Instance.dialogMessage.get(dialog.top_message);
            TLRPC.User user = null;
            TLRPC.Chat chat = null;
            TLRPC.EncryptedChat encryptedChat = null;

            int lower_id = (int)dialog.id;
            if (lower_id != 0) {
                if (lower_id < 0) {
                    chat = MessagesController.Instance.chats.get(-lower_id);
                } else {
                    user = MessagesController.Instance.users.get(lower_id);
                }
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(0);
            } else {
                encryptedChat = MessagesController.Instance.encryptedChats.get((int)(dialog.id >> 32));
                if (encryptedChat != null) {
                    user = MessagesController.Instance.users.get(encryptedChat.user_id);
                    if (!isRTL) {
                        nameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_green, 0, 0, 0);
                    } else {
                        nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_lock_green, 0);
                    }
                    nameTextView.setCompoundDrawablePadding((int)(4 * density));
                } else {
                    Log.e("test", "tda");
                }
            }

            if (chat != null) {
                nameTextView.setText(chat.title);
                nameTextView.setTextColor(0xff000000);
            } else if (user != null) {
                if (user.id != 333000 && MessagesController.Instance.contactsDict.get(user.id) == null) {
                    if (MessagesController.Instance.contactsDict.size() == 0 && MessagesController.Instance.loadingContacts) {
                        nameTextView.setTextColor(0xff000000);
                        nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
                    } else {
                        if (user.phone != null && user.phone.length() != 0) {
                            nameTextView.setTextColor(0xff000000);
                            nameTextView.setText(PhoneFormat.Instance.format("+" + user.phone));
                        } else {
                            nameTextView.setTextColor(0xff316f9f);
                            nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
                        }
                    }
                    if (encryptedChat != null) {
                        nameTextView.setTextColor(0xff00a60e);
                    }
                } else {
                    if (encryptedChat != null) {
                        nameTextView.setTextColor(0xff00a60e);
                    } else {
                        nameTextView.setTextColor(0xff000000);
                    }
                    nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
                }
            }
            TLRPC.FileLocation photo = null;
            int placeHolderId = 0;
            if (user != null) {
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                placeHolderId = Utilities.getUserAvatarForId(user.id);
            } else if (chat != null) {
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
                placeHolderId = Utilities.getGroupAvatarForId(chat.id);
            }
            CharSequence printingString = MessagesController.Instance.printingStrings.get(dialog.id);

            avatarImage.setImage(photo, "50_50", placeHolderId);

            if (message == null) {
                if (printingString != null) {
                    messageTextView.setText(printingString);
                    messageTextView.setTextColor(0xff316f9f);
                } else {
                    if (encryptedChat != null) {
                        messageTextView.setTextColor(0xff316f9f);
                        if (encryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
                            messageTextView.setText(getStringEntry(R.string.EncryptionProcessing));
                        } else if (encryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
                            messageTextView.setText(String.format(getStringEntry(R.string.AwaitingEncryption), user.first_name));
                        } else if (encryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
                            messageTextView.setText(getStringEntry(R.string.EncryptionRejected));
                        } else if (encryptedChat instanceof TLRPC.TL_encryptedChat) {
                            if (encryptedChat.admin_id == UserConfig.clientUserId) {
                                if (user != null) {
                                    messageTextView.setText(String.format(getStringEntry(R.string.EncryptedChatStartedOutgoing), user.first_name));
                                }
                            } else {
                                if (user != null) {
                                    messageTextView.setText(String.format(getStringEntry(R.string.EncryptedChatStartedIncoming), user.first_name));
                                }
                            }
                        }
                    } else {
                        messageTextView.setText("");
                    }
                }
                if (dialog.last_message_date != 0) {
                    timeTextView.setText(Utilities.stringForMessageListDate(dialog.last_message_date));
                } else {
                    timeTextView.setText("");
                }
                messagesCountImage.setVisibility(View.GONE);
                check1Image.setVisibility(View.GONE);
                check2Image.setVisibility(View.GONE);
                errorImage.setVisibility(View.GONE);
                clockImage.setVisibility(View.GONE);
            } else {
                TLRPC.User fromUser = MessagesController.Instance.users.get(message.messageOwner.from_id);

                if (dialog.last_message_date != 0) {
                    timeTextView.setText(Utilities.stringForMessageListDate(dialog.last_message_date));
                } else {
                    timeTextView.setText(Utilities.stringForMessageListDate(message.messageOwner.date));
                }
                if (printingString != null) {
                    messageTextView.setTextColor(0xff316f9f);
                    messageTextView.setText(printingString);
                } else {
                    if (message.messageOwner instanceof TLRPC.TL_messageService) {
                        messageTextView.setText(message.messageText);
                        messageTextView.setTextColor(0xff316f9f);
                    } else {
                        if (chat != null) {
                            String name = "";
                            if (message.messageOwner.from_id == UserConfig.clientUserId) {
                                name = getStringEntry(R.string.FromYou);
                            } else {
                                if (fromUser != null) {
                                    if (fromUser.first_name.length() > 0) {
                                        name = fromUser.first_name;
                                    } else {
                                        name = fromUser.last_name;
                                    }
                                }
                            }
                            if (message.messageOwner.media != null && !(message.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                                messageTextView.setTextColor(0xff316f9f);
                                messageTextView.setText(message.messageText);
                            } else {
                                messageTextView.setText(Emoji.replaceEmoji(Html.fromHtml(String.format("<font color=#316f9f>%s:</font> <font color=#808080>%s</font>", name, message.messageOwner.message))));
                            }
                        } else {
                            messageTextView.setText(message.messageText);
                            if (message.messageOwner.media != null && !(message.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                                messageTextView.setTextColor(0xff316f9f);
                            } else {
                                messageTextView.setTextColor(0xff808080);
                            }
                        }
                    }
                }

                if (dialog.unread_count != 0) {
                    messagesCountImage.setVisibility(View.VISIBLE);
                    messagesCountImage.setText(String.format("%d", dialog.unread_count));
                } else {
                    messagesCountImage.setVisibility(View.GONE);
                }

                if (message.messageOwner.id < 0 && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENT) {
                    if (MessagesController.Instance.sendingMessages.get(message.messageOwner.id) == null) {
                        message.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
                    }
                }

                if (message.messageOwner.from_id == UserConfig.clientUserId) {
                    if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                        check1Image.setVisibility(View.GONE);
                        check2Image.setVisibility(View.GONE);
                        clockImage.setVisibility(View.VISIBLE);
                        errorImage.setVisibility(View.GONE);
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                        check1Image.setVisibility(View.GONE);
                        check2Image.setVisibility(View.GONE);
                        clockImage.setVisibility(View.GONE);
                        errorImage.setVisibility(View.VISIBLE);
                        messagesCountImage.setVisibility(View.GONE);
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                        if (!message.messageOwner.unread) {
                            check1Image.setVisibility(View.VISIBLE);
                            check2Image.setVisibility(View.VISIBLE);
                        } else {
                            check1Image.setVisibility(View.GONE);
                            check2Image.setVisibility(View.VISIBLE);
                        }
                        clockImage.setVisibility(View.GONE);
                        errorImage.setVisibility(View.GONE);
                    }
                } else {
                    check1Image.setVisibility(View.GONE);
                    check2Image.setVisibility(View.GONE);
                    errorImage.setVisibility(View.GONE);
                    clockImage.setVisibility(View.GONE);
                }
            }
        }
    }
}
