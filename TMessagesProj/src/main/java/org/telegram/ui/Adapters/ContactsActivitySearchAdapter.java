/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
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
    private Timer searchDialogsTimer;

    public ContactsActivitySearchAdapter(Context context, HashMap<Integer, TLRPC.User> arg1) {
        mContext = context;
        ignoreUsers = arg1;
    }

    public void searchDialogs(final String query) {
        if (query == null) {
            searchResult = null;
            searchResultNames = null;
            notifyDataSetChanged();
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
                    processSearch(query);
                }
            }, 100, 300);
        }
    }

    private void processSearch(final String query) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<TLRPC.TL_contact>();
                contactsCopy.addAll(ContactsController.getInstance().contacts);
                Utilities.globalQueue.postRunnable(new Runnable() {
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
                            TLRPC.User user = MessagesController.getInstance().users.get(contact.user_id);
                            if (user.first_name != null && user.first_name.toLowerCase().startsWith(q) || user.last_name != null && user.last_name.toLowerCase().startsWith(q)) {
                                if (user.id == UserConfig.clientUserId) {
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
        Utilities.RunOnUIThread(new Runnable() {
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
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public int getCount() {
        if (searchResult == null) {
            return 0;
        }
        return searchResult.size();
    }

    @Override
    public TLRPC.User getItem(int i) {
        if (searchResult != null) {
            if (i >= 0 && i < searchResult.size()) {
                return searchResult.get(i);
            }
        }
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
        if (view == null) {
            view = new ChatOrUserCell(mContext);
            ((ChatOrUserCell)view).usePadding = false;
        }

        ((ChatOrUserCell) view).useSeparator = i != searchResult.size() - 1;

        Object obj = searchResult.get(i);
        TLRPC.User user = MessagesController.getInstance().users.get(((TLRPC.User)obj).id);

        if (user != null) {
            ((ChatOrUserCell)view).setData(user, null, null, searchResultNames.get(i), null);

            if (ignoreUsers != null) {
                if (ignoreUsers.containsKey(user.id)) {
                    ((ChatOrUserCell)view).drawAlpha = 0.5f;
                } else {
                    ((ChatOrUserCell)view).drawAlpha = 1.0f;
                }
            }
        }
        return view;
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return searchResult == null || searchResult.size() == 0;
    }
}
