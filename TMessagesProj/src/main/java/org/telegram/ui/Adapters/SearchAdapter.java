/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.UserCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SearchAdapter extends BaseSearchAdapter {

    private Context mContext;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private ArrayList<TLRPC.User> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private HashMap<Integer, ?> checkedMap;
    private Timer searchTimer;
    private boolean allowUsernameSearch;
    private boolean useUserCell;
    private boolean onlyMutual;
    private boolean allowChats;
    private boolean allowBots;

    public SearchAdapter(Context context, HashMap<Integer, TLRPC.User> arg1, boolean usernameSearch, boolean mutual, boolean chats, boolean bots) {
        mContext = context;
        ignoreUsers = arg1;
        onlyMutual = mutual;
        allowUsernameSearch = usernameSearch;
        allowChats = chats;
        allowBots = bots;
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
                queryServerSearch(null, allowChats, allowBots);
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
                    queryServerSearch(query, allowChats, allowBots);
                }
                final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>();
                contactsCopy.addAll(ContactsController.getInstance().contacts);
                Utilities.searchQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<TLRPC.User>(), new ArrayList<CharSequence>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String search[] = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<TLRPC.User> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contactsCopy.size(); a++) {
                            TLRPC.TL_contact contact = contactsCopy.get(a);
                            TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
                            if (user.id == UserConfig.getClientUserId() || onlyMutual && !user.mutual_contact) {
                                continue;
                            }

                            String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (user.username != null && user.username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                    }
                                    resultArray.add(user);
                                    break;
                                }
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
    public TLObject getItem(int i) {
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
                    view = new UserCell(mContext, 1, 1, false);
                    if (checkedMap != null) {
                        ((UserCell) view).setChecked(false, false);
                    }
                } else {
                    view = new ProfileSearchCell(mContext);
                }
            }

            TLObject object = getItem(i);
            if (object != null) {
                int id = 0;
                String un = null;
                if (object instanceof TLRPC.User) {
                    un = ((TLRPC.User) object).username;
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    un = ((TLRPC.Chat) object).username;
                    id = ((TLRPC.Chat) object).id;
                }

                CharSequence username = null;
                CharSequence name = null;
                if (i < searchResult.size()) {
                    name = searchResultNames.get(i);
                    if (name != null && un != null && un.length() > 0) {
                        if (name.toString().startsWith("@" + un)) {
                            username = name;
                            name = null;
                        }
                    }
                } else if (i > searchResult.size() && un != null) {
                    String foundUserName = lastFoundUsername;
                    if (foundUserName.startsWith("@")) {
                        foundUserName = foundUserName.substring(1);
                    }
                    try {
                        username = AndroidUtilities.replaceTags(String.format("<c#ff4d83b3>@%s</c>%s", un.substring(0, foundUserName.length()), un.substring(foundUserName.length())));
                    } catch (Exception e) {
                        username = un;
                        FileLog.e("tmessages", e);
                    }
                }

                if (useUserCell) {
                    ((UserCell) view).setData(object, name, username, 0);
                    if (checkedMap != null) {
                        ((UserCell) view).setChecked(checkedMap.containsKey(id), false);
                    }
                } else {
                    ((ProfileSearchCell) view).setData(object, null, name, username, false);
                    ((ProfileSearchCell) view).useSeparator = (i != getCount() - 1 && i != searchResult.size() - 1);
                    if (ignoreUsers != null) {
                        if (ignoreUsers.containsKey(id)) {
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
