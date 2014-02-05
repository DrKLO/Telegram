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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.TL.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private SectionedBaseAdapter listViewAdapter;
    private PinnedHeaderListView listView;
    private BaseAdapter searchListViewAdapter;
    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean usersAsSections;
    private boolean destroyAfterSelect;
    private boolean returnAsResult;
    private boolean createSecretChat;
    private boolean creatingChat = false;
    public int selectAlertString = 0;
    private SearchView searchView;
    private TextView epmtyTextView;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private SupportMenuItem searchItem;

    private Timer searchDialogsTimer;
    public ArrayList<TLRPC.User> searchResult;
    public ArrayList<CharSequence> searchResultNames;
    public ContactsActivityDelegate delegate;

    public static interface ContactsActivityDelegate {
        public abstract void didSelectContact(TLRPC.User user);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatCreated);
        if (getArguments() != null) {
            onlyUsers = getArguments().getBoolean("onlyUsers", false);
            destroyAfterSelect = getArguments().getBoolean("destroyAfterSelect", false);
            usersAsSections = getArguments().getBoolean("usersAsSections", false);
            returnAsResult = getArguments().getBoolean("returnAsResult", false);
            createSecretChat = getArguments().getBoolean("createSecretChat", false);
            if (destroyAfterSelect) {
                ignoreUsers = (HashMap<Integer, TLRPC.User>)NotificationCenter.Instance.getFromMemCache(7);
            }
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.encryptedChatCreated);
        delegate = null;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void willBeHidden() {
        if (searchItem != null) {
            if (searchItem.isActionViewExpanded()) {
                searchItem.collapseActionView();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {

            fragmentView = inflater.inflate(R.layout.contacts_layout, container, false);

            epmtyTextView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            searchListViewAdapter = new SearchAdapter(parentActivity);

            listView = (PinnedHeaderListView)fragmentView.findViewById(R.id.listView);
            listView.setEmptyView(epmtyTextView);
            listView.setVerticalScrollBarEnabled(false);

            listView.setAdapter(listViewAdapter = new ListAdapter(parentActivity));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (searching && searchWas) {
                        TLRPC.User user = searchResult.get(i);
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
                                MessagesController.Instance.startSecretChat(parentActivity, user);
                            } else {
                                ChatActivity fragment = new ChatActivity();
                                Bundle bundle = new Bundle();
                                bundle.putInt("user_id", user.id);
                                fragment.setArguments(bundle);
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), destroyAfterSelect, false);
                            }
                        }
                    } else {
                        int section = listViewAdapter.getSectionForPosition(i);
                        int row = listViewAdapter.getPositionInSectionForPosition(i);
                        TLRPC.User user = null;
                        if (usersAsSections) {
                            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                                ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                                if (row >= arr.size()) {
                                    return;
                                }
                                user = MessagesController.Instance.users.get(arr.get(row).user_id);
                            }
                        } else {
                            if (section == 0) {
                                if (row == 0) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_SEND);
                                        intent.setType("text/plain");
                                        intent.putExtra(Intent.EXTRA_TEXT, getStringEntry(R.string.InviteText));
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                    return;
                                } else {
                                    if (row - 1 < ContactsController.Instance.contacts.size()) {
                                        user = MessagesController.Instance.users.get(ContactsController.Instance.contacts.get(row - 1).user_id);
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
                                    MessagesController.Instance.startSecretChat(parentActivity, user);
                                } else {
                                    ChatActivity fragment = new ChatActivity();
                                    Bundle bundle = new Bundle();
                                    bundle.putInt("user_id", user.id);
                                    fragment.setArguments(bundle);
                                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), destroyAfterSelect, false);
                                }
                            }
                        } else {
                            ArrayList<ContactsController.Contact> arr = ContactsController.Instance.contactsSectionsDict.get(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
                            ContactsController.Contact contact = arr.get(row);
                            String usePhone = null;
                            if (!contact.phones.isEmpty()) {
                                usePhone = contact.phones.get(0);
                            }
                            if (usePhone == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(getStringEntry(R.string.InviteUser));
                            builder.setTitle(getStringEntry(R.string.AppName));
                            final String arg1 = usePhone;
                            builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null));
                                        intent.putExtra("sms_body", getStringEntry(R.string.InviteText));
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            builder.setNegativeButton(getStringEntry(R.string.Cancel), null);
                            builder.show().setCanceledOnTouchOutside(true);
                        }
                    }
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                    if (searchItem != null) {
                        if (searchItem.isActionViewExpanded()) {
                            searchItem.collapseActionView();
                        }
                    }
                }
            });
            epmtyTextView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                    if (searchItem != null) {
                        if (searchItem.isActionViewExpanded()) {
                            searchItem.collapseActionView();
                        }
                    }
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
        if (useAlert && selectAlertString != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(R.string.AppName);
            builder.setMessage(String.format(getStringEntry(selectAlertString), Utilities.formatName(user.first_name, user.last_name)));
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
            if (searchItem != null) {
                if (searchItem.isActionViewExpanded()) {
                    searchItem.collapseActionView();
                }
            }
        }
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        actionBar.setSubtitle(null);

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }

        if (destroyAfterSelect) {
            actionBar.setTitle(getStringEntry(R.string.SelectContact));
        } else {
            actionBar.setTitle(getStringEntry(R.string.Contacts));
        }

        ((ApplicationActivity)parentActivity).fixBackButton();
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
        if (!firstStart && listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
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
                    processSearch(query);
                }
            }, 100, 300);
        }
    }

    private void processSearch(final String query) {
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

                for (TLRPC.TL_contact contact : ContactsController.Instance.contacts) {
                    TLRPC.User user = MessagesController.Instance.users.get(contact.user_id);
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

    private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                searchResult = users;
                searchResultNames = names;
                searchListViewAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if (searchItem != null) {
                    if (searchItem.isActionViewExpanded()) {
                        searchItem.collapseActionView();
                    }
                }
                finishFragment();
                break;
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.contacts_menu, menu);
        searchItem = (SupportMenuItem)menu.findItem(R.id.messages_list_menu_search);
        searchView = (SearchView)searchItem.getActionView();

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
                    if (listView != null) {
                        listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                        listView.setAdapter(searchListViewAdapter);
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            listView.setFastScrollAlwaysVisible(false);
                        }
                        listView.setFastScrollEnabled(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    if (epmtyTextView != null) {
                        epmtyTextView.setText(getStringEntry(R.string.NoResult));
                    }
                }
                return true;
            }
        });

        searchItem.setSupportOnActionExpandListener(new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                if (parentActivity != null) {
                    ActionBar actionBar = parentActivity.getSupportActionBar();
                    actionBar.setIcon(R.drawable.ic_ab_search);
                }
                searching = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.setQuery("", false);
                searchDialogs(null);
                searching = false;
                searchWas = false;
                ViewGroup group = (ViewGroup) listView.getParent();
                listView.setAdapter(listViewAdapter);
                if (!Utilities.isRTL) {
                    listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(30), listView.getPaddingBottom());
                } else {
                    listView.setPadding(Utilities.dp(30), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                }
                if (android.os.Build.VERSION.SDK_INT >= 11) {
                    listView.setFastScrollAlwaysVisible(true);
                }
                listView.setFastScrollEnabled(true);
                listView.setVerticalScrollBarEnabled(false);
                ((ApplicationActivity)parentActivity).updateActionBar();

                epmtyTextView.setText(getStringEntry(R.string.NoContacts));
                return true;
            }
        });
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
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("enc_id", encryptedChat.id);
                fragment.setArguments(bundle);
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), true, false);
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

    private class SearchAdapter extends BaseAdapter {
        private Context mContext;

        public SearchAdapter(Context context) {
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
            if (searchResult == null) {
                return 0;
            }
            return searchResult.size();
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
            if (view == null) {
                view = new ChatOrUserCell(mContext);
                ((ChatOrUserCell)view).usePadding = false;
            }

            ((ChatOrUserCell) view).useSeparator = i != searchResult.size() - 1;

            Object obj = searchResult.get(i);
            TLRPC.User user = MessagesController.Instance.users.get(((TLRPC.User)obj).id);

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

    private class ListAdapter extends SectionedBaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public long getItemId(int section, int position) {
            return 0;
        }

        @Override
        public int getSectionCount() {
            int count = 0;
            if (usersAsSections) {
                count += ContactsController.Instance.sortedUsersSectionsArray.size();
            } else {
                count++;
            }
            if (!onlyUsers) {
                count += ContactsController.Instance.sortedContactsSectionsArray.size();
            }
            return count;
        }

        @Override
        public int getCountForSection(int section) {
            if (usersAsSections) {
                if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                    ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                    return arr.size();
                }
            } else {
                if (section == 0) {
                    return ContactsController.Instance.contacts.size() + 1;
                }
            }
            ArrayList<ContactsController.Contact> arr = ContactsController.Instance.contactsSectionsDict.get(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
            return arr.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {

            TLRPC.User user = null;
            int count = 0;
            if (usersAsSections) {
                if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                    ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                    user = MessagesController.Instance.users.get(arr.get(position).user_id);
                    count = arr.size();
                }
            } else {
                if (section == 0) {
                    if (position == 0) {
                        if (convertView == null) {
                            LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            convertView = li.inflate(R.layout.contacts_invite_row_layout, parent, false);
                        }
                        View divider = convertView.findViewById(R.id.settings_row_divider);
                        if (ContactsController.Instance.contacts.isEmpty()) {
                            divider.setVisibility(View.INVISIBLE);
                        } else {
                            divider.setVisibility(View.VISIBLE);
                        }
                        return convertView;
                    }
                    user = MessagesController.Instance.users.get(ContactsController.Instance.contacts.get(position - 1).user_id);
                    count = ContactsController.Instance.contacts.size();
                }
            }
            if (user != null) {
                if (convertView == null) {
                    convertView = new ChatOrUserCell(mContext);
                    ((ChatOrUserCell)convertView).useBoldFont = true;
                    ((ChatOrUserCell)convertView).usePadding = false;
                }

                ((ChatOrUserCell)convertView).setData(user, null, null, null, null);

                if (ignoreUsers != null) {
                    if (ignoreUsers.containsKey(user.id)) {
                        ((ChatOrUserCell)convertView).drawAlpha = 0.5f;
                    } else {
                        ((ChatOrUserCell)convertView).drawAlpha = 1.0f;
                    }
                }

                ((ChatOrUserCell) convertView).useSeparator = position != count - 1;

                return convertView;
            }

            TextView textView;
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_row_button_layout, parent, false);
                textView = (TextView)convertView.findViewById(R.id.settings_row_text);
            } else {
                textView = (TextView)convertView.findViewById(R.id.settings_row_text);
            }

            View divider = convertView.findViewById(R.id.settings_row_divider);
            ArrayList<ContactsController.Contact> arr = ContactsController.Instance.contactsSectionsDict.get(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
            ContactsController.Contact contact = arr.get(position);
            if (divider != null) {
                if (position == arr.size() - 1) {
                    divider.setVisibility(View.INVISIBLE);
                } else {
                    divider.setVisibility(View.VISIBLE);
                }
            }
            if (contact.first_name != null && contact.last_name != null) {
                textView.setText(Html.fromHtml(contact.first_name + " <b>" + contact.last_name + "</b>"));
            } else if (contact.first_name != null && contact.last_name == null) {
                textView.setText(Html.fromHtml("<b>" + contact.first_name + "</b>"));
            } else {
                textView.setText(Html.fromHtml("<b>" + contact.last_name + "</b>"));
            }
            return convertView;
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (usersAsSections) {
                if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                    return 0;
                }
            } else if (section == 0) {
                if (position == 0) {
                    return 2;
                }
                return 0;
            }
            return 1;
        }

        @Override
        public int getItemViewTypeCount() {
            return 3;
        }

        @Override
        public int getSectionHeaderViewType(int section) {
            if (usersAsSections) {
                if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                    return 1;
                }
            } else if (section == 0) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getSectionHeaderViewTypeCount() {
            return 2;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (usersAsSections) {
                if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                    if (convertView == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                        convertView.setBackgroundColor(0xffffffff);
                    }
                    TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
                    textView.setText(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                    return convertView;
                }
            } else {
                if (section == 0) {
                    if (convertView == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        convertView = li.inflate(R.layout.empty_layout, parent, false);
                    }
                    return convertView;
                }
            }

            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                convertView.setBackgroundColor(0xffffffff);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
            textView.setText(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
            return convertView;
        }
    }
}
