/*
 * This is the source code of Telegram for Android v. 1.2.3.
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
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
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
    MenuItem searchItem;
    private boolean isRTL;

    private Timer searchDialogsTimer;
    public ArrayList<TLRPC.User> searchResult;
    public ArrayList<CharSequence> searchResultNames;
    public ContactsActivityDelegate delegate;

    public static interface ContactsActivityDelegate {
        public abstract void didSelectContact(int user_id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsBookDidLoaded);
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
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsBookDidLoaded);
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

            Locale locale = Locale.getDefault();
            String lang = locale.getLanguage();
            isRTL = lang != null && lang.toLowerCase().equals("ar");

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
                        int user_id = searchResult.get(i).id;
                        if (user_id == UserConfig.clientUserId) {
                            return;
                        }
                        if (returnAsResult) {
                            if (ignoreUsers != null && ignoreUsers.containsKey(user_id)) {
                                return;
                            }
                            didSelectResult(user_id, true);
                        } else {
                            if (createSecretChat) {
                                creatingChat = true;
                                MessagesController.Instance.startSecretChat(parentActivity, user_id);
                            } else {
                                ChatActivity fragment = new ChatActivity();
                                Bundle bundle = new Bundle();
                                bundle.putInt("user_id", user_id);
                                fragment.setArguments(bundle);
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + user_id, destroyAfterSelect, false);
                            }
                        }
                    } else {
                        int section = listViewAdapter.getSectionForPosition(i);
                        int row = listViewAdapter.getPositionInSectionForPosition(i);
                        int uid = 0;
                        if (usersAsSections) {
                            if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
                                ArrayList<TLRPC.TL_contact> arr = MessagesController.Instance.usersSectionsDict.get(MessagesController.Instance.sortedUsersSectionsArray.get(section));
                                uid = arr.get(row).user_id;
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
                                        e.printStackTrace();
                                    }
                                    return;
                                } else {
                                    if (row - 1 < MessagesController.Instance.contacts.size()) {
                                        uid = MessagesController.Instance.contacts.get(row - 1).user_id;
                                    } else {
                                        return;
                                    }
                                }
                            }
                        }

                        if (uid != 0) {
                            if (uid == UserConfig.clientUserId) {
                                return;
                            }
                            if (returnAsResult) {
                                if (ignoreUsers != null && ignoreUsers.containsKey(uid)) {
                                    return;
                                }
                                didSelectResult(uid, true);
                            } else {
                                if (createSecretChat) {
                                    creatingChat = true;
                                    MessagesController.Instance.startSecretChat(parentActivity, uid);
                                } else {
                                    ChatActivity fragment = new ChatActivity();
                                    Bundle bundle = new Bundle();
                                    bundle.putInt("user_id", uid);
                                    fragment.setArguments(bundle);
                                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + uid, destroyAfterSelect, false);
                                }
                            }
                        } else {
                            ArrayList<MessagesController.Contact> arr = MessagesController.Instance.contactsSectionsDict.get(MessagesController.Instance.sortedContactsSectionsArray.get(section - 1));
                            MessagesController.Contact contact = arr.get(row);
                            String usePhone = null;
                            for (String phone : contact.phones) {
                                if (usePhone == null) {
                                    usePhone = phone;
                                }
                                TLRPC.TL_contact cLocal = MessagesController.Instance.contactsByPhones.get(phone);
                                if (cLocal != null) {
                                    if (cLocal.user_id == UserConfig.clientUserId) {
                                        return;
                                    }
                                    if (createSecretChat) {
                                        creatingChat = true;
                                        MessagesController.Instance.startSecretChat(parentActivity, cLocal.user_id);
                                    } else {
                                        ChatActivity fragment = new ChatActivity();
                                        Bundle bundle = new Bundle();
                                        bundle.putInt("user_id", cLocal.user_id);
                                        fragment.setArguments(bundle);
                                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_user_" + cLocal.user_id, destroyAfterSelect, false);
                                    }
                                    return;
                                }
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(getStringEntry(R.string.InviteUser));
                            builder.setTitle(getStringEntry(R.string.AppName));
                            final String arg1 = usePhone;
                            builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        String number = "+" + arg1;
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", number, null));
                                        intent.putExtra("sms_body", getStringEntry(R.string.InviteText));
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        e.printStackTrace();
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

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void didSelectResult(final int user_id, boolean useAlert) {
        if (useAlert && selectAlertString != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(R.string.AppName);
            TLRPC.User user = MessagesController.Instance.users.get(user_id);
            builder.setMessage(String.format(getStringEntry(selectAlertString), Utilities.formatName(user.first_name, user.last_name)));
            builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(user_id, false);
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            builder.show().setCanceledOnTouchOutside(true);
        } else {
            if (delegate != null) {
                delegate.didSelectContact(user_id);
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

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    Display display = manager.getDefaultDisplay();
                    int rotation = display.getRotation();
                    int height;
                    int currentActionBarHeight = parentActivity.getSupportActionBar().getHeight();
                    float density = Utilities.applicationContext.getResources().getDisplayMetrics().density;
                    if (currentActionBarHeight != 48 * density && currentActionBarHeight != 40 * density) {
                        height = currentActionBarHeight;
                    } else {
                        height = (int)(48.0f * density);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = (int)(40.0f * density);
                        }
                    }

                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)listView.getLayoutParams();
                    params.setMargins(0, height, 0, 0);
                    listView.setLayoutParams(params);

                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

                    return false;
                }
            });
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

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }

        if (destroyAfterSelect) {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.SelectContact) + "</font>"));
        } else {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.Contacts) + "</font>"));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getSherlockActivity() == null) {
            return;
        }
        if (!firstStart && listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
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
                e.printStackTrace();
            }
            searchDialogsTimer = new Timer();
            searchDialogsTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchDialogsTimer.cancel();
                        searchDialogsTimer = null;
                    } catch (Exception e) {
                        e.printStackTrace();
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
                if (query.length() == 0) {
                    updateSearchResults(new ArrayList<TLRPC.User>(), new ArrayList<CharSequence>());
                    return;
                }
                long time = System.currentTimeMillis();
                ArrayList<TLRPC.User> resultArray = new ArrayList<TLRPC.User>();
                ArrayList<CharSequence> resultArrayNames = new ArrayList<CharSequence>();
                String q = query.toLowerCase();

                for (TLRPC.TL_contact contact : MessagesController.Instance.contacts) {
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
        searchItem = menu.findItem(R.id.messages_list_menu_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getStringEntry(R.string.SearchContactHint));

        int srcId = searchView.getContext().getResources().getIdentifier("android:id/search_close_btn", null, null);
        ImageView img = (ImageView) searchView.findViewById(srcId);
        if (img != null) {
            img.setImageResource(R.drawable.ic_msg_in_cross);
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
                        float density = Utilities.applicationContext.getResources().getDisplayMetrics().density;
                        listView.setPadding((int)(density * 16), listView.getPaddingTop(), (int)(density * 16), listView.getPaddingBottom());
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

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                parentActivity.getSupportActionBar().setIcon(R.drawable.ic_ab_search);
                searching = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.setQuery("", false);
                searchDialogs(null);
                searching = false;
                searchWas = false;
                ViewGroup group = (ViewGroup)listView.getParent();
                listView.setAdapter(listViewAdapter);
                float density = Utilities.applicationContext.getResources().getDisplayMetrics().density;
                if (!isRTL) {
                    listView.setPadding((int)(density * 16), listView.getPaddingTop(), (int)(density * 30), listView.getPaddingBottom());
                } else {
                    listView.setPadding((int)(density * 30), listView.getPaddingTop(), (int)(density * 16), listView.getPaddingBottom());
                }
                if(android.os.Build.VERSION.SDK_INT >= 11) {
                    listView.setFastScrollAlwaysVisible(true);
                }
                listView.setFastScrollEnabled(true);
                listView.setVerticalScrollBarEnabled(false);
                epmtyTextView.setText(getStringEntry(R.string.NoContacts));
                return true;
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.contactsDidLoaded || id == MessagesController.contactsBookDidLoaded) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else if (id == MessagesController.updateInterfaces) {
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (id == MessagesController.encryptedChatCreated) {
            if (createSecretChat && creatingChat) {
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat)args[0];
                ChatActivity fragment = new ChatActivity();
                Bundle bundle = new Bundle();
                bundle.putInt("enc_id", encryptedChat.id);
                fragment.setArguments(bundle);
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat_enc_" + id, true, false);
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
            int type = getItemViewType(i);
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (type == 0) {
                    view = li.inflate(R.layout.messages_search_user_layout, viewGroup, false);
                } else {
                    view = li.inflate(R.layout.messages_search_chat_layout, viewGroup, false);
                }
            }
            ContactListRowHolder holder = (ContactListRowHolder)view.getTag();
            if (holder == null) {
                holder = new ContactListRowHolder(view);
                view.setTag(holder);
            }
            View divider = view.findViewById(R.id.settings_row_divider);
            if (i == searchResult.size() - 1) {
                divider.setVisibility(View.INVISIBLE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            Object obj = searchResult.get(i);
            CharSequence name = searchResultNames.get(i);

            TLRPC.User user = MessagesController.Instance.users.get(((TLRPC.User)obj).id);

            holder.nameTextView.setText(name);

            if (user != null) {
                if (ignoreUsers != null) {
                    if (ignoreUsers.containsKey(user.id)) {
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            holder.avatarImage.setAlpha(0.5f);
                            holder.messageTextView.setAlpha(0.5f);
                            holder.nameTextView.setAlpha(0.5f);
                        }
                    } else {
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            holder.avatarImage.setAlpha(1.0f);
                            holder.messageTextView.setAlpha(1.0f);
                            holder.nameTextView.setAlpha(1.0f);
                        }
                    }
                }
                TLRPC.FileLocation photo = null;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(user.id);
                holder.avatarImage.setImage(photo, "50_50", placeHolderId);

                if (user.status == null) {
                    holder.messageTextView.setTextColor(0xff808080);
                    holder.messageTextView.setText(getStringEntry(R.string.Offline));
                } else {
                    int currentTime = ConnectionsManager.Instance.getCurrentTime();
                    if (user.status.expires > currentTime || user.status.was_online > currentTime) {
                        holder.messageTextView.setTextColor(0xff006fc8);
                        holder.messageTextView.setText(getStringEntry(R.string.Online));
                    } else {
                        if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                            holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                        } else {
                            int value = user.status.was_online;
                            if (value == 0) {
                                value = user.status.expires;
                            }
                            holder.messageTextView.setText(getStringEntry(R.string.LastSeen) + " " + Utilities.formatDateOnline(value));
                        }
                        holder.messageTextView.setTextColor(0xff808080);
                    }
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            Object obj = searchResult.get(i);
            if (obj instanceof TLRPC.User) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 2;
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
                count += MessagesController.Instance.sortedUsersSectionsArray.size();
            } else {
                count++;
            }
            if (!onlyUsers) {
                count += MessagesController.Instance.sortedContactsSectionsArray.size();
            }
            return count;
        }

        @Override
        public int getCountForSection(int section) {
            if (usersAsSections) {
                if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
                    ArrayList<TLRPC.TL_contact> arr = MessagesController.Instance.usersSectionsDict.get(MessagesController.Instance.sortedUsersSectionsArray.get(section));
                    return arr.size();
                }
            } else {
                if (section == 0) {
                    return MessagesController.Instance.contacts.size() + 1;
                }
            }
            ArrayList<MessagesController.Contact> arr = MessagesController.Instance.contactsSectionsDict.get(MessagesController.Instance.sortedContactsSectionsArray.get(section - 1));
            return arr.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {

            TLRPC.User user = null;
            int count = 0;
            if (usersAsSections) {
                if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
                    ArrayList<TLRPC.TL_contact> arr = MessagesController.Instance.usersSectionsDict.get(MessagesController.Instance.sortedUsersSectionsArray.get(section));
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
                        if (MessagesController.Instance.contacts.isEmpty()) {
                            divider.setVisibility(View.INVISIBLE);
                        } else {
                            divider.setVisibility(View.VISIBLE);
                        }
                        return convertView;
                    }
                    user = MessagesController.Instance.users.get(MessagesController.Instance.contacts.get(position - 1).user_id);
                    count = MessagesController.Instance.contacts.size();
                }
            }
            if (user != null) {
                if (convertView == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = li.inflate(R.layout.messages_search_user_layout, parent, false);
                }
                ContactListRowHolder holder = (ContactListRowHolder)convertView.getTag();
                if (holder == null) {
                    holder = new ContactListRowHolder(convertView);
                    convertView.setTag(holder);
                    Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                    holder.nameTextView.setTypeface(typeface);
                }

                if (ignoreUsers != null) {
                    if (ignoreUsers.containsKey(user.id)) {
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            holder.avatarImage.setAlpha(0.5f);
                            holder.messageTextView.setAlpha(0.5f);
                            holder.nameTextView.setAlpha(0.5f);
                        }
                    } else {
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            holder.avatarImage.setAlpha(1.0f);
                            holder.messageTextView.setAlpha(1.0f);
                            holder.nameTextView.setAlpha(1.0f);
                        }
                    }
                }

                View divider = convertView.findViewById(R.id.settings_row_divider);
                if (position == count - 1) {
                    divider.setVisibility(View.INVISIBLE);
                } else {
                    divider.setVisibility(View.VISIBLE);
                }

                TLRPC.FileLocation photo = null;
                if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>"));
                } else if (user.first_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.first_name + "</b>"));
                } else {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.last_name + "</b>"));
                }
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(user.id);
                holder.avatarImage.setImage(photo, "50_50", placeHolderId);

                if (user.status == null) {
                    holder.messageTextView.setText(getStringEntry(R.string.Offline));
                    holder.messageTextView.setTextColor(0xff808080);
                } else {
                    int currentTime = ConnectionsManager.Instance.getCurrentTime();
                    if (user.status.expires > currentTime || user.status.was_online > currentTime) {
                        holder.messageTextView.setTextColor(0xff006fc8);
                        holder.messageTextView.setText(getStringEntry(R.string.Online));
                    } else {
                        if (user.status.was_online <= 10000 && user.status.expires <= 10000) {
                            holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                        } else {
                            int value = user.status.was_online;
                            if (value == 0) {
                                value = user.status.expires;
                            }
                            holder.messageTextView.setText(getStringEntry(R.string.LastSeen) + " " + Utilities.formatDateOnline(value));
                        }
                        holder.messageTextView.setTextColor(0xff808080);
                    }
                }
                return convertView;
            }

            TextView textView;
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_row_button_layout, parent, false);
                textView = (TextView)convertView.findViewById(R.id.settings_row_text);
                Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
                textView.setTypeface(typeface);
            } else {
                textView = (TextView)convertView.findViewById(R.id.settings_row_text);
            }
            View divider = convertView.findViewById(R.id.settings_row_divider);
            ArrayList<MessagesController.Contact> arr = MessagesController.Instance.contactsSectionsDict.get(MessagesController.Instance.sortedContactsSectionsArray.get(section - 1));
            MessagesController.Contact contact = arr.get(position);
            if (position == arr.size() - 1) {
                divider.setVisibility(View.INVISIBLE);
            } else {
                divider.setVisibility(View.VISIBLE);
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
                if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
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
                if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
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
                if (section < MessagesController.Instance.sortedUsersSectionsArray.size()) {
                    if (convertView == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                        convertView.setBackgroundColor(0xffffffff);
                    }
                    TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
                    textView.setText(MessagesController.Instance.sortedUsersSectionsArray.get(section));
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
            textView.setText(MessagesController.Instance.sortedContactsSectionsArray.get(section - 1));
            return convertView;
        }
    }

    public static class ContactListRowHolder {
        public BackupImageView avatarImage;
        public TextView messageTextView;
        public TextView nameTextView;

        public ContactListRowHolder(View view) {
            Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
            messageTextView = (TextView)view.findViewById(R.id.messages_list_row_message);
            if (messageTextView != null) {
                messageTextView.setTypeface(typeface);
            }
            nameTextView = (TextView)view.findViewById(R.id.messages_list_row_name);
            avatarImage = (BackupImageView)view.findViewById(R.id.messages_list_row_avatar);
        }
    }
}
