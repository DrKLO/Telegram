/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class SearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private LongSparseArray<TLRPC.User> ignoreUsers;
    private ArrayList<Object> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private SearchAdapterHelper searchAdapterHelper;
    private LongSparseArray<TLRPC.User> selectedUsers;
    private Timer searchTimer;
    private boolean allowUsernameSearch;
    private boolean useUserCell;
    private boolean onlyMutual;
    private boolean allowChats;
    private boolean allowBots;
    private boolean allowSelf;
    private boolean allowPhoneNumbers;
    private long channelId;
    private boolean searchInProgress;
    private int searchReqId;
    private int searchPointer;
    private ArrayList<ContactEntry> allUnregistredContacts;
    private ArrayList<ContactsController.Contact> unregistredContacts = new ArrayList<>();


    public SearchAdapter(Context context, LongSparseArray<TLRPC.User> arg1, LongSparseArray<TLRPC.User> selected, boolean usernameSearch, boolean mutual, boolean chats, boolean bots, boolean self, boolean phones, int searchChannelId) {
        mContext = context;
        ignoreUsers = arg1;
        selectedUsers = selected;
        onlyMutual = mutual;
        allowUsernameSearch = usernameSearch;
        allowChats = chats;
        allowBots = bots;
        channelId = searchChannelId;
        allowSelf = self;
        allowPhoneNumbers = phones;
        searchAdapterHelper = new SearchAdapterHelper(true);
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int searchId) {
                notifyDataSetChanged();
                if (searchId != 0) {
                    onSearchProgressChanged();
                }
            }

            @Override
            public LongSparseArray<TLRPC.User> getExcludeUsers() {
                return ignoreUsers;
            }
        });
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
        searchResult.clear();
        unregistredContacts.clear();
        searchResultNames.clear();
        if (allowUsernameSearch) {
            searchAdapterHelper.queryServerSearch(null, true, allowChats, allowBots, allowSelf, false, channelId, allowPhoneNumbers, 0, 0);
        }
        notifyDataSetChanged();
        if (!TextUtils.isEmpty(query)) {
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
                searchAdapterHelper.queryServerSearch(query, true, allowChats, allowBots, allowSelf, false, channelId, allowPhoneNumbers, -1, 1);
            }
            final int currentAccount = UserConfig.selectedAccount;
            final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
            searchInProgress = true;
            searchReqId = searchPointer++;
            int searchReqIdFinal = searchReqId;
            Utilities.searchQueue.postRunnable(() -> {
                String search1 = query.trim().toLowerCase();
                if (search1.length() == 0) {
                    updateSearchResults(searchReqIdFinal, new ArrayList<>(), new ArrayList<>(), unregistredContacts);
                    return;
                }
                String search2 = LocaleController.getInstance().getTranslitString(search1);
                if (search1.equals(search2) || search2.length() == 0) {
                    search2 = null;
                }
                String[] search = new String[1 + (search2 != null ? 1 : 0)];
                search[0] = search1;
                if (search2 != null) {
                    search[1] = search2;
                }

                ArrayList<Object> resultArray = new ArrayList<>();
                ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                ArrayList<ContactsController.Contact> unregistredContacts = new ArrayList<>();

                for (int a = 0; a < contactsCopy.size(); a++) {
                    TLRPC.TL_contact contact = contactsCopy.get(a);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (!allowSelf && user.self || onlyMutual && !user.mutual_contact || ignoreUsers != null && ignoreUsers.indexOfKey(contact.user_id) >= 0) {
                        continue;
                    }

                    final String[] names = new String[3];
                    names[0] = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                    names[1] = LocaleController.getInstance().getTranslitString(names[0]);
                    if (names[0].equals(names[1])) {
                        names[1] = null;
                    }
                    if (UserObject.isReplyUser(user)) {
                        names[2] = LocaleController.getString("RepliesTitle", R.string.RepliesTitle).toLowerCase();
                    } else if (user.self) {
                        names[2] = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
                    }

                    int found = 0;
                    for (String q : search) {
                        for (int i = 0; i < names.length; i++) {
                            final String name = names[i];
                            if (name != null && (name.startsWith(q) || name.contains(" " + q))) {
                                found = 1;
                                break;
                            }
                        }
                        String username = UserObject.getPublicUsername(user);
                        if (found == 0 && username != null && username.startsWith(q)) {
                            found = 2;
                        }
                        if (found != 0) {
                            if (found == 1) {
                                resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                            } else {
                                resultArrayNames.add(AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q));
                            }
                            resultArray.add(user);
                            break;
                        }
                    }
                }

                if (allUnregistredContacts == null) {
                    allUnregistredContacts = new ArrayList<>();
                    for (ContactsController.Contact contact : ContactsController.getInstance(currentAccount).phoneBookContacts) {
                        ContactEntry contactEntry = new ContactEntry();
                        contactEntry.contact = contact;
                        contactEntry.q1 = (contact.first_name + " " + contact.last_name).toLowerCase();
                        contactEntry.q2 = (contact.last_name + " " + contact.first_name).toLowerCase();
                        allUnregistredContacts.add(contactEntry);
                    }
                }
                for (int i = 0; i < allUnregistredContacts.size(); i++) {
                    ContactEntry contact = allUnregistredContacts.get(i);
                    if ((search2 != null && (contact.q1.toLowerCase().contains(search2) || contact.q1.toLowerCase().contains(search2))) || contact.q1.toLowerCase().contains(search1) || contact.q1.toLowerCase().contains(search1)) {
                        unregistredContacts.add(contact.contact);
                    }
                }

                updateSearchResults(searchReqIdFinal, resultArray, resultArrayNames, unregistredContacts);
            });
        });
    }

    private void updateSearchResults(int searchReqIdFinal, final ArrayList<Object> users, final ArrayList<CharSequence> names, ArrayList<ContactsController.Contact> unregistredContacts) {
        AndroidUtilities.runOnUIThread(() -> {
            if (searchReqIdFinal == searchReqId) {
                searchResult = users;
                searchResultNames = names;
                this.unregistredContacts = unregistredContacts;
                searchAdapterHelper.mergeResults(users);
                searchInProgress = false;
                notifyDataSetChanged();
                onSearchProgressChanged();
            }
        });
    }

    protected void onSearchProgressChanged() {

    }

    public boolean searchInProgress() {
        return searchInProgress || searchAdapterHelper.isSearchInProgress();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int type = holder.getItemViewType();
        return type == 0 || type == 2 || type == 3;
    }

    int unregistredContactsHeaderRow;
    @Override
    public int getItemCount() {
        unregistredContactsHeaderRow = -1;
        int count = searchResult.size();
        unregistredContactsHeaderRow = count;
        if (!unregistredContacts.isEmpty()) {
            count += unregistredContacts.size() + 1;
        }

        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (phoneCount != 0) {
            count += phoneCount;
        }
        return count;
    }

    public boolean isGlobalSearch(int i) {
        int localCount = searchResult.size();
        int unregistredCount = unregistredContacts.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (i >= 0 && i < localCount) {
            return false;
        } else if (i > localCount && i < localCount + unregistredCount + 1) {
            return false;
        } else if (i > localCount + unregistredCount + 1 && i < localCount + phoneCount + unregistredCount + 1) {
            return false;
        } else if (i > localCount + phoneCount + unregistredCount + 1 && i <= globalCount + phoneCount + localCount + unregistredCount + 1) {
            return true;
        }
        return false;
    }

    public Object getItem(int i) {
        int localCount = searchResult.size();
        int unregistredCount = unregistredContacts.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        } else {
            i -= localCount;
            if (unregistredCount > 0) {
                if (i == 0) {
                    return null;
                }
                if (i > 0 && i <= unregistredCount) {
                    return unregistredContacts.get(i - 1);
                } else {
                    i -= unregistredCount + 1;
                }
            }
            if (i >= 0 && i < phoneCount) {
                return searchAdapterHelper.getPhoneSearch().get(i);
            } else {
                i -= phoneCount;
                if (i > 0 && i <= globalCount) {
                    return searchAdapterHelper.getGlobalSearch().get(i - 1);
                }
            }
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
                } else {
                    view = new ProfileSearchCell(mContext);
                }
                break;
            case 1:
                view = new GraySectionCell(mContext);
                break;
            case 2:
            default:
                view = new TextCell(mContext, 16, false);
                break;
            case 3:
                view = new ProfileSearchCell(mContext);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                TLObject object = (TLObject) getItem(position);
                if (object != null) {
                    long id = 0;
                    String un = null;
                    boolean self = false;
                    if (object instanceof TLRPC.User) {
                        un = ((TLRPC.User) object).username;
                        id = ((TLRPC.User) object).id;
                        self = ((TLRPC.User) object).self;
                    } else if (object instanceof TLRPC.Chat) {
                        un = ChatObject.getPublicUsername((TLRPC.Chat) object);
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
                        if (foundUserName != null && foundUserName.startsWith("@")) {
                            foundUserName = foundUserName.substring(1);
                        }
                        try {
                            int index;
                            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                            spannableStringBuilder.append("@");
                            spannableStringBuilder.append(un);
                            if (foundUserName != null && (index = AndroidUtilities.indexOfIgnoreCase(un, foundUserName)) != -1) {
                                int len = foundUserName.length();
                                if (index == 0) {
                                    len++;
                                } else {
                                    index++;
                                }
                                spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                        userCell.setChecked(selectedUsers.indexOfKey(id) >= 0, false);
                    } else {
                        ProfileSearchCell profileSearchCell = (ProfileSearchCell) holder.itemView;
                        if (self) {
                            name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                        }
                        profileSearchCell.setData(object, null, name, username, false, self);
                        profileSearchCell.useSeparator = (position != getItemCount() - 1 && position != searchResult.size() - 1);
                        profileSearchCell.setChecked(selectedUsers.indexOfKey(id) >= 0, false);
                        /*if (ignoreUsers != null) {
                            if (ignoreUsers.containsKey(id)) {
                                profileSearchCell.drawAlpha = 0.5f;
                            } else {
                                profileSearchCell.drawAlpha = 1.0f;
                            }
                        }*/
                    }
                }
                break;
            }
            case 1: {
                GraySectionCell cell = (GraySectionCell) holder.itemView;
                if (position ==  unregistredContactsHeaderRow) {
                    cell.setText(LocaleController.getString("InviteToTelegramShort", R.string.InviteToTelegramShort));
                } else if (getItem(position) == null) {
                    cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                } else {
                    cell.setText(LocaleController.getString("PhoneNumberSearch", R.string.PhoneNumberSearch));
                }
                break;
            }
            case 2: {
                String str = (String) getItem(position);
                TextCell cell = (TextCell) holder.itemView;
                cell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText2);
                cell.setText(LocaleController.formatString("AddContactByPhone", R.string.AddContactByPhone, PhoneFormat.getInstance().format("+" + str)), false);
                break;
            }
            case 3: {
                ProfileSearchCell profileSearchCell = (ProfileSearchCell) holder.itemView;
                ContactsController.Contact contact = (ContactsController.Contact) getItem(position);
                profileSearchCell.useSeparator = getItem(position + 1) instanceof ContactsController.Contact ;
                profileSearchCell.setData(contact, null, ContactsController.formatName(contact.first_name, contact.last_name), PhoneFormat.getInstance().format("+" + contact.shortPhones.get(0)), false, false);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        Object item = getItem(i);
        if (item == null) {
            return 1;
        } else if (item instanceof String) {
            String str = (String) item;
            if ("section".equals(str)) {
                return 1;
            } else {
                return 2;
            }
        } else if (item instanceof ContactsController.Contact) {
            return 3;
        }
        return 0;
    }

    private static class ContactEntry {
        String q1;
        String q2;
        ContactsController.Contact contact;
    }
}
