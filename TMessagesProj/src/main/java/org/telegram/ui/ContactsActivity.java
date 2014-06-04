/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.ContactsActivityAdapter;
import org.telegram.ui.Adapters.ContactsActivitySearchAdapter;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private SectionedBaseAdapter listViewAdapter;
    private PinnedHeaderListView listView;
    private ContactsActivitySearchAdapter searchListViewAdapter;
    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean usersAsSections;
    private boolean destroyAfterSelect;
    private boolean returnAsResult;
    private boolean createSecretChat;
    private boolean creatingChat = false;
    private String selectAlertString = null;
    private TextView emptyTextView;
    private HashMap<Integer, TLRPC.User> ignoreUsers;

    private String inviteText;
    private boolean updatingInviteText = false;
    private ContactsActivityDelegate delegate;

    public static interface ContactsActivityDelegate {
        public abstract void didSelectContact(TLRPC.User user);
    }

    public ContactsActivity(Bundle args) {
        super(args);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatCreated);
        if (arguments != null) {
            onlyUsers = getArguments().getBoolean("onlyUsers", false);
            destroyAfterSelect = getArguments().getBoolean("destroyAfterSelect", false);
            usersAsSections = getArguments().getBoolean("usersAsSections", false);
            returnAsResult = getArguments().getBoolean("returnAsResult", false);
            createSecretChat = getArguments().getBoolean("createSecretChat", false);
            selectAlertString = arguments.getString("selectAlertString");
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        inviteText = preferences.getString("invitetext", null);
        int time = preferences.getInt("invitetexttime", 0);
        if (inviteText == null || time + 86400 < (int)(System.currentTimeMillis() / 1000)) {
            updateInviteText();
        }

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatCreated);
        delegate = null;
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true);
            if (destroyAfterSelect) {
                actionBarLayer.setTitle(LocaleController.getString("SelectContact", R.string.SelectContact));
            } else {
                actionBarLayer.setTitle(LocaleController.getString("Contacts", R.string.Contacts));
            }

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                }

                @Override
                public void onSearchCollapse() {
                    searchListViewAdapter.searchDialogs(null);
                    searching = false;
                    searchWas = false;
                    ViewGroup group = (ViewGroup) listView.getParent();
                    listView.setAdapter(listViewAdapter);
                    if (!LocaleController.isRTL) {
                        listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(30), listView.getPaddingBottom());
                    } else {
                        listView.setPadding(Utilities.dp(30), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                        listView.setFastScrollAlwaysVisible(true);
                    }
                    listView.setFastScrollEnabled(true);
                    listView.setVerticalScrollBarEnabled(false);

                    emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (searchListViewAdapter == null) {
                        return;
                    }
                    String text = editText.getText().toString();
                    if (text.length() != 0) {
                        searchWas = true;
                        if (listView != null) {
                            listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                            listView.setAdapter(searchListViewAdapter);
                            if(android.os.Build.VERSION.SDK_INT >= 11) {
                                listView.setFastScrollAlwaysVisible(false);
                            }
                            listView.setFastScrollEnabled(false);
                            listView.setVerticalScrollBarEnabled(true);
                        }
                        if (emptyTextView != null) {
                            emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        }
                    }
                    searchListViewAdapter.searchDialogs(text);
                }
            });

            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.contacts_layout, container, false);

            emptyTextView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
            searchListViewAdapter = new ContactsActivitySearchAdapter(getParentActivity(), ignoreUsers);

            listView = (PinnedHeaderListView)fragmentView.findViewById(R.id.listView);
            listView.setEmptyView(emptyTextView);
            listView.setVerticalScrollBarEnabled(false);

            listViewAdapter = new ContactsActivityAdapter(getParentActivity(), onlyUsers, usersAsSections, ignoreUsers);
            listView.setAdapter(listViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (searching && searchWas) {
                        TLRPC.User user = searchListViewAdapter.getItem(i);
                        if (user == null || user.id == UserConfig.clientUserId) {
                            return;
                        }
                        if (returnAsResult) {
                            if (ignoreUsers != null && ignoreUsers.containsKey(user.id)) {
                                return;
                            }
                            didSelectResult(user, true);
                        } else {
                            if (createSecretChat) {
                                creatingChat = true;
                                MessagesController.getInstance().startSecretChat(getParentActivity(), user);
                            } else {
                                Bundle args = new Bundle();
                                args.putInt("user_id", user.id);
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    } else {
                        int section = listViewAdapter.getSectionForPosition(i);
                        int row = listViewAdapter.getPositionInSectionForPosition(i);
                        if (row < 0 || section < 0) {
                            return;
                        }
                        TLRPC.User user = null;
                        if (usersAsSections) {
                            if (section < ContactsController.getInstance().sortedUsersSectionsArray.size()) {
                                ArrayList<TLRPC.TL_contact> arr = ContactsController.getInstance().usersSectionsDict.get(ContactsController.getInstance().sortedUsersSectionsArray.get(section));
                                if (row < arr.size()) {
                                    TLRPC.TL_contact contact = arr.get(row);
                                    user = MessagesController.getInstance().users.get(contact.user_id);
                                } else {
                                    return;
                                }
                            }
                        } else {
                            if (section == 0) {
                                if (row == 0) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_SEND);
                                        intent.setType("text/plain");
                                        intent.putExtra(Intent.EXTRA_TEXT, inviteText != null ? inviteText : LocaleController.getString("InviteText", R.string.InviteText));
                                        getParentActivity().startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                    return;
                                } else {
                                    if (row - 1 < ContactsController.getInstance().contacts.size()) {
                                        user = MessagesController.getInstance().users.get(ContactsController.getInstance().contacts.get(row - 1).user_id);
                                    } else {
                                        return;
                                    }
                                }
                            }
                        }

                        if (user != null) {
                            if (user.id == UserConfig.clientUserId) {
                                return;
                            }
                            if (returnAsResult) {
                                if (ignoreUsers != null && ignoreUsers.containsKey(user.id)) {
                                    return;
                                }
                                didSelectResult(user, true);
                            } else {
                                if (createSecretChat) {
                                    creatingChat = true;
                                    MessagesController.getInstance().startSecretChat(getParentActivity(), user);
                                } else {
                                    Bundle args = new Bundle();
                                    args.putInt("user_id", user.id);
                                    presentFragment(new ChatActivity(args), true);
                                }
                            }
                        } else {
                            ArrayList<ContactsController.Contact> arr = ContactsController.getInstance().contactsSectionsDict.get(ContactsController.getInstance().sortedContactsSectionsArray.get(section - 1));
                            ContactsController.Contact contact = arr.get(row);
                            String usePhone = null;
                            if (!contact.phones.isEmpty()) {
                                usePhone = contact.phones.get(0);
                            }
                            if (usePhone == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("InviteUser", R.string.InviteUser));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            final String arg1 = usePhone;
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null));
                                        intent.putExtra("sms_body", LocaleController.getString("InviteText", R.string.InviteText));
                                        getParentActivity().startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    }
                }
            });

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                        Utilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void didSelectResult(final TLRPC.User user, boolean useAlert) {
        if (useAlert && selectAlertString != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatStringSimple(selectAlertString, Utilities.formatName(user.first_name, user.last_name)));
            builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(user, false);
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            builder.show().setCanceledOnTouchOutside(true);
        } else {
            if (delegate != null) {
                delegate.didSelectContact(user);
                delegate = null;
            }
            finishFragment();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.contactsDidLoaded) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == MessagesController.encryptedChatCreated) {
            if (createSecretChat && creatingChat) {
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat)args[0];
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", encryptedChat.id);
                presentFragment(new ChatActivity(args2), true);
            }
        }
    }

    private void updateInviteText() {
        if (!updatingInviteText) {
            updatingInviteText = true;
            TLRPC.TL_help_getInviteText req = new TLRPC.TL_help_getInviteText();
            req.lang_code = Locale.getDefault().getCountry();
            if (req.lang_code == null || req.lang_code.length() == 0) {
                req.lang_code = "en";
            }
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.TL_help_inviteText res = (TLRPC.TL_help_inviteText)response;
                        if (res.message.length() != 0) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    updatingInviteText = false;
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putString("invitetext", res.message);
                                    editor.putInt("invitetexttime", (int) (System.currentTimeMillis() / 1000));
                                    editor.commit();
                                }
                            });
                        }
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView != null) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof ChatOrUserCell) {
                    ((ChatOrUserCell) child).update(mask);
                }
            }
        }
    }

    public void setDelegate(ContactsActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public void setIgnoreUsers(HashMap<Integer, TLRPC.User> users) {
        ignoreUsers = users;
    }
}
