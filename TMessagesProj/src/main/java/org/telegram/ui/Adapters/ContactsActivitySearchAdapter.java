/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ContactsActivitySearchAdapter extends BaseFragmentAdapter {
    private Context mContext;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private ArrayList<TLRPC.User> searchResult;
    private ArrayList<CharSequence> searchResultNames;
    private Timer searchTimer;
    private ArrayList<TLRPC.User> globalSearch;
    private long reqId = 0;
    private int lastReqId;

    public ContactsActivitySearchAdapter(Context context, HashMap<Integer, TLRPC.User> arg1) {
        mContext = context;
        ignoreUsers = arg1;
    }

    public void searchDialogs(final String query) {
        if (query == null) {
            searchResult = null;
            searchResultNames = null;
            globalSearch = null;
            queryServerSearch(null);
            notifyDataSetChanged();
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
                    processSearch(query);
                }
            }, 200, 300);
        }
    }

    private void queryServerSearch(String query) {
        if (query == null || query.length() < 5) {
            if (reqId != 0) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                reqId = 0;
            }
            globalSearch = null;
            lastReqId = 0;
            notifyDataSetChanged();
            return;
        }
        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        req.limit = 50;
        final int currentReqId = ++lastReqId;
        reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                                globalSearch = res.users;
                                notifyDataSetChanged();
                            }
                        }
                        reqId = 0;
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    private void processSearch(final String query) {
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                queryServerSearch(query);
                final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<TLRPC.TL_contact>();
                contactsCopy.addAll(ContactsController.getInstance().contacts);
                Utilities.searchQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        String q = query.trim().toLowerCase();
                        if (q.length() == 0) {
                            updateSearchResults(new ArrayList<TLRPC.User>(), new ArrayList<CharSequence>());
                            return;
                        }
                        long time = System.currentTimeMillis();
                        ArrayList<TLRPC.User> resultArray = new ArrayList<TLRPC.User>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<CharSequence>();

                        for (TLRPC.TL_contact contact : contactsCopy) {
                            TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
                            String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                            if (name.contains(q)) {
                                if (user.id == UserConfig.getClientUserId()) {
                                    continue;
                                }
                                resultArrayNames.add(Utilities.generateSearchName(user.first_name, user.last_name, q));
                                resultArray.add(user);
                            }
                        }

                        updateSearchResults(resultArray, resultArrayNames);
                    }
                });
            }
        });
    }

    private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                searchResult = users;
                searchResultNames = names;
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        return i != (searchResult == null ? 0 : searchResult.size());
    }

    @Override
    public int getCount() {
        int count = searchResult == null ? 0 : searchResult.size();
        int globalCount = globalSearch == null ? 0 : globalSearch.size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        return count;
    }

    public boolean isGlobalSearch(int i) {
        int localCount = searchResult == null ? 0 : searchResult.size();
        int globalCount = globalSearch == null ? 0 : globalSearch.size();
        if (i >= 0 && i < localCount) {
            return false;
        } else if (i > localCount && i <= globalCount + localCount) {
            return true;
        }
        return false;
    }

    @Override
    public TLRPC.User getItem(int i) {
        int localCount = searchResult == null ? 0 : searchResult.size();
        int globalCount = globalSearch == null ? 0 : globalSearch.size();
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        } else if (i > localCount && i <= globalCount + localCount) {
            return globalSearch.get(i - localCount - 1);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (i == (searchResult == null ? 0 : searchResult.size())) {
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = li.inflate(R.layout.settings_section_layout, viewGroup, false);
                TextView textView = (TextView)view.findViewById(R.id.settings_section_text);
                textView.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
            }
        } else {
            if (view == null) {
                view = new ChatOrUserCell(mContext);
                ((ChatOrUserCell) view).usePadding = false;
            }

            ((ChatOrUserCell) view).useSeparator = (i != getCount() - 1 && i != searchResult.size() - 1);
            TLRPC.User user = getItem(i);
            if (user != null) {
                ((ChatOrUserCell) view).setData(user, null, null, i < searchResult.size() ? searchResultNames.get(i) : null, i > searchResult.size() ? "@" + user.username : null);

                if (ignoreUsers != null) {
                    if (ignoreUsers.containsKey(user.id)) {
                        ((ChatOrUserCell) view).drawAlpha = 0.5f;
                    } else {
                        ((ChatOrUserCell) view).drawAlpha = 1.0f;
                    }
                }
            }
        }
        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (i == (searchResult == null ? 0 : searchResult.size())) {
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
        return (searchResult == null || searchResult.size() == 0) && (globalSearch == null || globalSearch.isEmpty());
    }
}
