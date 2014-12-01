/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.UserCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ContactsSearchAdapter extends BaseContactsSearchAdapter {
    private Context mContext;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private ArrayList<TLRPC.User> searchResult = new ArrayList<TLRPC.User>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<CharSequence>();
    private HashMap<Integer, ?> checkedMap;
    private Timer searchTimer;
    private boolean allowUsernameSearch;
    private boolean useUserCell;

    public ContactsSearchAdapter(Context context, HashMap<Integer, TLRPC.User> arg1, boolean usernameSearch) {
        mContext = context;
        ignoreUsers = arg1;
        allowUsernameSearch = usernameSearch;
    }

    public void setCheckedMap(HashMap<Integer, ?> map) {
        checkedMap = map;
    }

    public void setUseUserCell(boolean value) {
        useUserCell = value;
    }

    public void searchDialogs(final String query) {
        try {
            if (searchTimer != null) {
                searchTimer.cancel();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (query == null) {
            searchResult.clear();
            searchResultNames.clear();
            if (allowUsernameSearch) {
                queryServerSearch(null);
            }
            notifyDataSetChanged();
        } else {
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

    private void processSearch(final String query) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (allowUsernameSearch) {
                    queryServerSearch(query);
                }
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
                            if (user.id == UserConfig.getClientUserId()) {
                                continue;
                            }

                            String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();

                            int found = 0;
                            if (name.startsWith(q) || name.contains(" " + q)) {
                                found = 1;
                            } else if (user.username != null && user.username.startsWith(q)) {
                                found = 2;
                            }

                            if (found != 0) {
                                if (found == 1) {
                                    resultArrayNames.add(Utilities.generateSearchName(user.first_name, user.last_name, q));
                                } else {
                                    resultArrayNames.add(Utilities.generateSearchName("@" + user.username, null, "@" + q));
                                }
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
        AndroidUtilities.runOnUIThread(new Runnable() {
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
        return i != searchResult.size();
    }

    @Override
    public int getCount() {
        int count = searchResult.size();
        int globalCount = globalSearch.size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        return count;
    }

    public boolean isGlobalSearch(int i) {
        int localCount = searchResult.size();
        int globalCount = globalSearch.size();
        if (i >= 0 && i < localCount) {
            return false;
        } else if (i > localCount && i <= globalCount + localCount) {
            return true;
        }
        return false;
    }

    @Override
    public TLRPC.User getItem(int i) {
        int localCount = searchResult.size();
        int globalCount = globalSearch.size();
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
        if (i == searchResult.size()) {
            if (view == null) {
                view = new GreySectionCell(mContext);
                ((GreySectionCell) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
            }
        } else {
            if (view == null) {
                if (useUserCell) {
                    view = new UserCell(mContext, 1);
                    if (checkedMap != null) {
                        ((UserCell) view).setChecked(false, false);
                    }
                } else {
                    view = new ProfileSearchCell(mContext);
                }
            }

            TLRPC.User user = getItem(i);
            if (user != null) {
                CharSequence username = null;
                CharSequence name = null;
                if (i < searchResult.size()) {
                    name = searchResultNames.get(i);
                    if (name != null && user != null && user.username != null && user.username.length() > 0) {
                        if (name.toString().startsWith("@" + user.username)) {
                            username = name;
                            name = null;
                        }
                    }
                } else if (i > searchResult.size() && user.username != null) {
                    try {
                        username = Html.fromHtml(String.format("<font color=\"#4d83b3\">@%s</font>%s", user.username.substring(0, lastFoundUsername.length()), user.username.substring(lastFoundUsername.length())));
                    } catch (Exception e) {
                        username = user.username;
                        FileLog.e("tmessages", e);
                    }
                }

                if (useUserCell) {
                    ((UserCell) view).setData(user, name, username, 0);
                    if (checkedMap != null) {
                        ((UserCell) view).setChecked(checkedMap.containsKey(user.id), false);
                    }
                } else {
                    ((ProfileSearchCell) view).setData(user, null, null, name, username);
                    ((ProfileSearchCell) view).useSeparator = (i != getCount() - 1 && i != searchResult.size() - 1);
                    if (ignoreUsers != null) {
                        if (ignoreUsers.containsKey(user.id)) {
                            ((ProfileSearchCell) view).drawAlpha = 0.5f;
                        } else {
                            ((ProfileSearchCell) view).drawAlpha = 1.0f;
                        }
                    }
                }
            }
        }
        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (i == searchResult.size()) {
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
        return searchResult.isEmpty() && globalSearch.isEmpty();
    }
}
