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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
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
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ListView messagesListView;
    private MessagesAdapter messagesListViewAdapter;
    private TextView searchEmptyView;
    private View progressView;
    private View empryView;
    private String selectAlertString;
    private boolean serverOnly = false;

    private static boolean dialogsLoaded = false;
    private boolean searching = false;
    private boolean searchWas = false;
    private boolean onlySelect = false;
    private int activityToken = (int)(Utilities.random.nextDouble() * Integer.MAX_VALUE);
    private long selectedDialog;

    private Timer searchTimer;
    public ArrayList<TLObject> searchResult;
    public ArrayList<CharSequence> searchResultNames;

    private MessagesActivityDelegate delegate;

    private final static int messages_list_menu_new_messages = 1;
    private final static int messages_list_menu_new_chat = 2;
    private final static int messages_list_menu_other = 6;
    private final static int messages_list_menu_new_secret_chat = 3;
    private final static int messages_list_menu_contacts = 4;
    private final static int messages_list_menu_settings = 5;

    public static interface MessagesActivityDelegate {
        public abstract void didSelectDialog(MessagesActivity fragment, long dialog_id);
    }

    public MessagesActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.getInstance().addObserver(this, 999);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, 1234);
        if (getArguments() != null) {
            onlySelect = arguments.getBoolean("onlySelect", false);
            serverOnly = arguments.getBoolean("serverOnly", false);
            selectAlertString = arguments.getString("selectAlertString");
        }
        if (!dialogsLoaded) {
            MessagesController.getInstance().loadDialogs(0, 0, 100, true);
            ContactsController.getInstance().checkAppAccount();
            dialogsLoaded = true;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.dialogsNeedReload);
        NotificationCenter.getInstance().removeObserver(this, 999);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.reloadSearchResults);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, 1234);
        delegate = null;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            ActionBarMenu menu = actionBarLayer.createMenu();
            menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    if (messagesListView != null) {
                        messagesListView.setEmptyView(searchEmptyView);
                    }
                    if (empryView != null) {
                        empryView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
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
                }

                @Override
                public void onTextChanged(EditText editText) {
                    String text = editText.getText().toString();
                    searchDialogs(text);
                    if (text.length() != 0) {
                        searchWas = true;
                        if (messagesListViewAdapter != null) {
                            messagesListViewAdapter.notifyDataSetChanged();
                        }
                        if (searchEmptyView != null) {
                            messagesListView.setEmptyView(searchEmptyView);
                            empryView.setVisibility(View.GONE);
                        }
                    }
                }
            });
            if (onlySelect) {
                actionBarLayer.setDisplayHomeAsUpEnabled(true);
                actionBarLayer.setTitle(LocaleController.getString("SelectChat", R.string.SelectChat));
            } else {
                actionBarLayer.setDisplayUseLogoEnabled(true);
                actionBarLayer.setTitle(LocaleController.getString("AppName", R.string.AppName));
                menu.addItem(messages_list_menu_new_messages, R.drawable.ic_ab_compose);
                ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
                item.addSubItem(messages_list_menu_new_chat, LocaleController.getString("NewGroup", R.string.NewGroup), 0);
                item.addSubItem(messages_list_menu_new_secret_chat, LocaleController.getString("NewSecretChat", R.string.NewSecretChat), 0);
                item.addSubItem(messages_list_menu_contacts, LocaleController.getString("Contacts", R.string.Contacts), 0);
                item.addSubItem(messages_list_menu_settings, LocaleController.getString("Settings", R.string.Settings), 0);
            }

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == messages_list_menu_settings) {
                        presentFragment(new SettingsActivity());
                    } else if (id == messages_list_menu_contacts) {
                        presentFragment(new ContactsActivity(null));
                    } else if (id == messages_list_menu_new_messages) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        presentFragment(new ContactsActivity(args));
                    } else if (id == messages_list_menu_new_secret_chat) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        args.putBoolean("createSecretChat", true);
                        presentFragment(new ContactsActivity(args));
                    } else if (id == messages_list_menu_new_chat) {
                        presentFragment(new GroupCreateActivity());
                    } else if (id == -1) {
                        if (onlySelect) {
                            finishFragment();
                        }
                    }
                }
            });

            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.messages_list, container, false);

            messagesListViewAdapter = new MessagesAdapter(getParentActivity());

            messagesListView = (ListView)fragmentView.findViewById(R.id.messages_list_view);
            messagesListView.setAdapter(messagesListViewAdapter);

            progressView = fragmentView.findViewById(R.id.progressLayout);
            messagesListViewAdapter.notifyDataSetChanged();
            searchEmptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            empryView = fragmentView.findViewById(R.id.list_empty_view);
            TextView textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text1);
            textView.setText(LocaleController.getString("NoChats", R.string.NoChats));
            textView = (TextView)fragmentView.findViewById(R.id.list_empty_view_text2);
            textView.setText(LocaleController.getString("NoChats", R.string.NoChatsHelp));

            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
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
                            if (i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogsServerOnly.get(i);
                            dialog_id = dialog.id;
                        } else {
                            if (i >= MessagesController.getInstance().dialogs.size()) {
                                return;
                            }
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs.get(i);
                            dialog_id = dialog.id;
                        }
                    }
                    if (onlySelect) {
                        didSelectResult(dialog_id, true);
                    } else {
                        Bundle args = new Bundle();
                        int lower_part = (int)dialog_id;
                        if (lower_part != 0) {
                            if (lower_part > 0) {
                                args.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                args.putInt("chat_id", -lower_part);
                            }
                        } else {
                            args.putInt("enc_id", (int)(dialog_id >> 32));
                        }
                        presentFragment(new ChatActivity(args));
                    }
                }
            });

            messagesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (onlySelect || searching && searchWas) {
                        return false;
                    }
                    TLRPC.TL_dialog dialog;
                    if (serverOnly) {
                        if (i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                            return false;
                        }
                        dialog = MessagesController.getInstance().dialogsServerOnly.get(i);
                    } else {
                        if (i >= MessagesController.getInstance().dialogs.size()) {
                            return false;
                        }
                        dialog = MessagesController.getInstance().dialogs.get(i);
                    }
                    selectedDialog = dialog.id;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));

                    if ((int)selectedDialog < 0) {
                        builder.setItems(new CharSequence[]{LocaleController.getString("ClearHistory", R.string.ClearHistory), LocaleController.getString("DeleteChat", R.string.DeleteChat)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    MessagesController.getInstance().deleteDialog(selectedDialog, 0, true);
                                } else if (which == 1) {
                                    MessagesController.getInstance().deleteUserFromChat((int) -selectedDialog, MessagesController.getInstance().users.get(UserConfig.clientUserId), null);
                                    MessagesController.getInstance().deleteDialog(selectedDialog, 0, false);
                                }
                            }
                        });
                    } else {
                        builder.setItems(new CharSequence[]{LocaleController.getString("ClearHistory", R.string.ClearHistory), LocaleController.getString("Delete", R.string.Delete)}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MessagesController.getInstance().deleteDialog(selectedDialog, 0, which == 0);
                            }
                        });
                    }
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.show().setCanceledOnTouchOutside(true);
                    return true;
                }
            });

            messagesListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                        Utilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (searching && searchWas) {
                        return;
                    }
                    if (visibleItemCount > 0) {
                        if (absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogs.size() && !serverOnly || absListView.getLastVisiblePosition() == MessagesController.getInstance().dialogsServerOnly.size() && serverOnly) {
                            MessagesController.getInstance().loadDialogs(MessagesController.getInstance().dialogs.size(), MessagesController.getInstance().dialogsServerOnly.size(), 100, true);
                        }
                    }
                }
            });

            if (MessagesController.getInstance().loadingDialogs) {
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
    public void onResume() {
        super.onResume();
        showActionBar();
        if (messagesListViewAdapter != null) {
            messagesListViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.dialogsNeedReload) {
            if (messagesListViewAdapter != null) {
                messagesListViewAdapter.notifyDataSetChanged();
            }
            if (messagesListView != null) {
                if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
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

    public void setDelegate(MessagesActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void didSelectResult(final long dialog_id, boolean useAlert) {
        if (useAlert && selectAlertString != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            int lower_part = (int)dialog_id;
            if (lower_part != 0) {
                if (lower_part > 0) {
                    TLRPC.User user = MessagesController.getInstance().users.get(lower_part);
                    if (user == null) {
                        return;
                    }
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertString, Utilities.formatName(user.first_name, user.last_name)));
                } else if (lower_part < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance().chats.get(-lower_part);
                    if (chat == null) {
                        return;
                    }
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertString, chat.title));
                }
            } else {
                int chat_id = (int)(dialog_id >> 32);
                TLRPC.EncryptedChat chat = MessagesController.getInstance().encryptedChats.get(chat_id);
                TLRPC.User user = MessagesController.getInstance().users.get(chat.user_id);
                if (user == null) {
                    return;
                }
                builder.setMessage(LocaleController.formatStringSimple(selectAlertString, Utilities.formatName(user.first_name, user.last_name)));
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
                        TLRPC.User user = (TLRPC.User) obj;
                        MessagesController.getInstance().users.putIfAbsent(user.id, user);
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        MessagesController.getInstance().chats.putIfAbsent(chat.id, chat);
                    } else if (obj instanceof TLRPC.EncryptedChat) {
                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                        MessagesController.getInstance().encryptedChats.putIfAbsent(chat.id, chat);
                    }
                }
                for (TLRPC.User user : encUsers) {
                    MessagesController.getInstance().users.putIfAbsent(user.id, user);
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
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    MessagesStorage.getInstance().searchDialogs(activityToken, query, !serverOnly);
                }
            }, 100, 300);
        }
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
                count = MessagesController.getInstance().dialogsServerOnly.size();
            } else {
                count = MessagesController.getInstance().dialogs.size();
            }
            if (count == 0 && MessagesController.getInstance().loadingDialogs) {
                return 0;
            }
            if (!MessagesController.getInstance().dialogsEndReached) {
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
                    user = MessagesController.getInstance().users.get(((TLRPC.User)obj).id);
                } else if (obj instanceof TLRPC.Chat) {
                    chat = MessagesController.getInstance().chats.get(((TLRPC.Chat) obj).id);
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.getInstance().encryptedChats.get(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.getInstance().users.get(encryptedChat.user_id);
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
                ((DialogCell)view).setDialog(MessagesController.getInstance().dialogsServerOnly.get(i));
            } else {
                ((DialogCell)view).setDialog(MessagesController.getInstance().dialogs.get(i));
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
            if (serverOnly && i == MessagesController.getInstance().dialogsServerOnly.size() || !serverOnly && i == MessagesController.getInstance().dialogs.size()) {
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
            if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
                return false;
            }
            int count;
            if (serverOnly) {
                count = MessagesController.getInstance().dialogsServerOnly.size();
            } else {
                count = MessagesController.getInstance().dialogs.size();
            }
            if (count == 0 && MessagesController.getInstance().loadingDialogs) {
                return true;
            }
            if (!MessagesController.getInstance().dialogsEndReached) {
                count++;
            }
            return count == 0;
        }
    }
}
