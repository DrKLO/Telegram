/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private SparseArray<TLRPC.User> ignoreUsers;
    private ArrayList<TLRPC.User> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private SearchAdapterHelper searchAdapterHelper;
    private SparseArray<?> checkedMap;
    private Timer searchTimer;
    private boolean allowUsernameSearch;
    private boolean useUserCell;
    private boolean onlyMutual;
    private boolean allowChats;
    private boolean allowBots;
    private int channelId;

    public SearchAdapter(Context context, SparseArray<TLRPC.User> arg1, boolean usernameSearch, boolean mutual, boolean chats, boolean bots, int searchChannelId) {
        mContext = context;
        ignoreUsers = arg1;
        onlyMutual = mutual;
        allowUsernameSearch = usernameSearch;
        allowChats = chats;
        allowBots = bots;
        channelId = searchChannelId;
        searchAdapterHelper = new SearchAdapterHelper(true);
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged() {
                notifyDataSetChanged();
            }

            @Override
            public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {

            }
        });
    }

    public void setCheckedMap(SparseArray<?> map) {
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
            FileLog.e(e);
        }
        if (query == null) {
            searchResult.clear();
            searchResultNames.clear();
            if (allowUsernameSearch) {
                searchAdapterHelper.queryServerSearch(null, true, allowChats, allowBots, true, channelId, false);
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
                        FileLog.e(e);
                    }
                    processSearch(query);
                }
            }, 200, 300);
        }
    }

    private void processSearch(final String query) {
        AndroidUtilities.runOnUIThread(() -> {
            if (allowUsernameSearch) {
                searchAdapterHelper.queryServerSearch(query, true, allowChats, allowBots, true, channelId, false);
            }
            final int currentAccount = UserConfig.selectedAccount;
            final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
            Utilities.searchQueue.postRunnable(() -> {
                String search1 = query.trim().toLowerCase();
                if (search1.length() == 0) {
                    updateSearchResults(new ArrayList<>(), new ArrayList<>());
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
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (user.id == UserConfig.getInstance(currentAccount).getClientUserId() || onlyMutual && !user.mutual_contact) {
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
            });
        });
    }

    private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
        AndroidUtilities.runOnUIThread(() -> {
            searchResult = users;
            searchResultNames = names;
            notifyDataSetChanged();
        });
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getAdapterPosition() != searchResult.size();
    }

    @Override
    public int getItemCount() {
        int count = searchResult.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        return count;
    }

    public boolean isGlobalSearch(int i) {
        int localCount = searchResult.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        if (i >= 0 && i < localCount) {
            return false;
        } else if (i > localCount && i <= globalCount + localCount) {
            return true;
        }
        return false;
    }

    public TLObject getItem(int i) {
        int localCount = searchResult.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        } else if (i > localCount && i <= globalCount + localCount) {
            return searchAdapterHelper.getGlobalSearch().get(i - localCount - 1);
        }
        return null;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                if (useUserCell) {
                    view = new UserCell(mContext, 1, 1, false);
                    if (checkedMap != null) {
                        ((UserCell) view).setChecked(false, false);
                    }
                } else {
                    view = new ProfileSearchCell(mContext);
                }
                break;
            case 1:
            default:
                view = new GraySectionCell(mContext);
                ((GraySectionCell) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == 0) {
            TLObject object = getItem(position);
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
                if (position < searchResult.size()) {
                    name = searchResultNames.get(position);
                    if (name != null && un != null && un.length() > 0) {
                        if (name.toString().startsWith("@" + un)) {
                            username = name;
                            name = null;
                        }
                    }
                } else if (position > searchResult.size() && un != null) {
                    String foundUserName = searchAdapterHelper.getLastFoundUsername();
                    if (foundUserName.startsWith("@")) {
                        foundUserName = foundUserName.substring(1);
                    }
                    try {
                        int index;
                        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                        spannableStringBuilder.append("@");
                        spannableStringBuilder.append(un);
                        if ((index = un.toLowerCase().indexOf(foundUserName)) != -1) {
                            int len = foundUserName.length();
                            if (index == 0) {
                                len++;
                            } else {
                                index++;
                            }
                            spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        username = spannableStringBuilder;
                    } catch (Exception e) {
                        username = un;
                        FileLog.e(e);
                    }
                }

                if (useUserCell) {
                    UserCell userCell = (UserCell) holder.itemView;
                    userCell.setData(object, name, username, 0);
                    if (checkedMap != null) {
                        userCell.setChecked(checkedMap.indexOfKey(id) >= 0, false);
                    }
                } else {
                    ProfileSearchCell profileSearchCell = (ProfileSearchCell) holder.itemView;
                    profileSearchCell.setData(object, null, name, username, false, false);
                    profileSearchCell.useSeparator = (position != getItemCount() - 1 && position != searchResult.size() - 1);
                    /*if (ignoreUsers != null) {
                        if (ignoreUsers.containsKey(id)) {
                            profileSearchCell.drawAlpha = 0.5f;
                        } else {
                            profileSearchCell.drawAlpha = 1.0f;
                        }
                    }*/
                }
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == searchResult.size()) {
            return 1;
        }
        return 0;
    }
}
