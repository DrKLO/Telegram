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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashMap;

public class SettingsBlockedUsers extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {
    private ListView listView;
    private ListAdapter listViewAdapter;
    private boolean loading;
    private View progressView;
    private TextView emptyView;
    private ArrayList<TLRPC.TL_contactBlocked> blockedContacts = new ArrayList<TLRPC.TL_contactBlocked>();
    private HashMap<Integer, TLRPC.TL_contactBlocked> blockedContactsDict = new HashMap<Integer, TLRPC.TL_contactBlocked>();
    private int selectedUserId;

    private final static int block_user = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        loadBlockedContacts(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true);
            actionBarLayer.setTitle(LocaleController.getString("BlockedUsers", R.string.BlockedUsers));
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == block_user) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("usersAsSections", true);
                        args.putBoolean("returnAsResult", true);
                        ContactsActivity fragment = new ContactsActivity(args);
                        fragment.setDelegate(SettingsBlockedUsers.this);
                        presentFragment(fragment);
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            menu.addItem(block_user, R.drawable.plus);

            fragmentView = inflater.inflate(R.layout.settings_blocked_users_layout, container, false);
            listViewAdapter = new ListAdapter(getParentActivity());
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
            if (loading) {
                progressView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }
            listView.setAdapter(listViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < blockedContacts.size()) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", blockedContacts.get(i).user_id);
                        presentFragment(new UserProfileActivity(args));
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i >= blockedContacts.size()) {
                        return true;
                    }
                    selectedUserId = blockedContacts.get(i).user_id;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                    CharSequence[] items = new CharSequence[] {LocaleController.getString("Unblock", R.string.Unblock)};

                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (i == 0) {
                                TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
                                TLRPC.User user = MessagesController.getInstance().users.get(selectedUserId);
                                if (user == null) {
                                    return;
                                }
                                req.id = MessagesController.getInputUser(user);
                                TLRPC.TL_contactBlocked blocked = blockedContactsDict.get(selectedUserId);
                                blockedContactsDict.remove(selectedUserId);
                                blockedContacts.remove(blocked);
                                listViewAdapter.notifyDataSetChanged();
                                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {

                                    }
                                }, null, true, RPCRequest.RPCRequestClassGeneric);
                            }
                        }
                    });
                    showAlertDialog(builder);

                    return true;
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

    private void loadBlockedContacts(int offset, int count) {
        if (loading) {
            return;
        }
        loading = true;
        TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
        req.offset = offset;
        req.limit = count;
        long requestId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            if (progressView != null) {
                                progressView.setVisibility(View.GONE);
                            }
                            if (listView != null && listView.getEmptyView() == null) {
                                listView.setEmptyView(emptyView);
                            }
                            if (listViewAdapter != null) {
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
                final TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked)response;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        for (TLRPC.User user : res.users) {
                            MessagesController.getInstance().users.put(user.id, user);
                        }
                        for (TLRPC.TL_contactBlocked blocked : res.blocked) {
                            if (!blockedContactsDict.containsKey(blocked.user_id)) {
                                blockedContacts.add(blocked);
                                blockedContactsDict.put(blocked.user_id, blocked);
                            }
                        }
                        if (progressView != null) {
                            progressView.setVisibility(View.GONE);
                        }
                        if (listView != null && listView.getEmptyView() == null) {
                            listView.setEmptyView(emptyView);
                        }
                        if (listViewAdapter != null) {
                            listViewAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
        ConnectionsManager.getInstance().bindRequestToGuid(requestId, classGuid);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateVisibleRows(mask);
            }
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatOrUserCell) {
                ((ChatOrUserCell) child).update(mask);
            }
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
    public void didSelectContact(TLRPC.User user) {
        if (user == null || blockedContactsDict.containsKey(user.id)) {
            return;
        }
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        req.id = MessagesController.getInputUser(user);
        TLRPC.TL_contactBlocked blocked = new TLRPC.TL_contactBlocked();
        blocked.user_id = user.id;
        blocked.date = (int)(System.currentTimeMillis() / 1000);
        blockedContactsDict.put(blocked.user_id, blocked);
        blockedContacts.add(blocked);
        listViewAdapter.notifyDataSetChanged();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    private class ListAdapter extends BaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != blockedContacts.size();
        }

        @Override
        public int getCount() {
            if (blockedContacts.isEmpty()) {
                return 0;
            }
            return blockedContacts.size() + 1;
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
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new ChatOrUserCell(mContext);
                    ((ChatOrUserCell)view).usePadding = false;
                    ((ChatOrUserCell)view).useSeparator = true;
                }
                TLRPC.User user = MessagesController.getInstance().users.get(blockedContacts.get(i).user_id);
                ((ChatOrUserCell)view).setData(user, null, null, null, user.phone != null && user.phone.length() != 0 ? PhoneFormat.getInstance().format("+" + user.phone) : "Unknown");
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.settings_unblock_info_row_layout, viewGroup, false);
                    TextView textView = (TextView)view.findViewById(R.id.info_text_view);
                    textView.setText(LocaleController.getString("UnblockText", R.string.UnblockText));
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if(i == blockedContacts.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return blockedContacts.isEmpty();
        }
    }
}
