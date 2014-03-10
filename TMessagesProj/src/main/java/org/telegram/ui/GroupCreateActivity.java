/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class GroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private SectionedBaseAdapter listViewAdapter;
    private PinnedHeaderListView listView;
    private TextView epmtyTextView;
    private TextView doneTextView;
    private EditText userSelectEditText;
    private TextView countTextView;
    private boolean ignoreChange = false;

    private HashMap<Integer, Emoji.XImageSpan> selectedContacts =  new HashMap<Integer, Emoji.XImageSpan>();
    private ArrayList<Emoji.XImageSpan> allSpans = new ArrayList<Emoji.XImageSpan>();

    private boolean searchWas;
    private boolean searching;
    private Timer searchDialogsTimer;
    public ArrayList<TLRPC.User> searchResult;
    public ArrayList<CharSequence> searchResultNames;

    private CharSequence changeString;
    private int beforeChangeIndex;

    public GroupCreateActivity() {
        animationType = 1;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.chatDidCreated);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.chatDidCreated);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {

            searching = false;
            searchWas = false;

            fragmentView = inflater.inflate(R.layout.group_create_layout, container, false);

            epmtyTextView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            userSelectEditText = (EditText)fragmentView.findViewById(R.id.bubble_input_text);
            countTextView = (TextView)fragmentView.findViewById(R.id.bubble_counter_text);
            if (Build.VERSION.SDK_INT >= 11) {
                userSelectEditText.setTextIsSelectable(false);
            }
            userSelectEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                    if (!ignoreChange) {
                        beforeChangeIndex = userSelectEditText.getSelectionStart();
                        changeString = new SpannableString(charSequence);
                    }
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (!ignoreChange) {
                        boolean search = false;
                        int afterChangeIndex = userSelectEditText.getSelectionEnd();
                        if (editable.toString().length() < changeString.toString().length()) {
                            String deletedString = "";
                            try {
                                deletedString = changeString.toString().substring(afterChangeIndex, beforeChangeIndex);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            if (deletedString.length() > 0) {
                                if (searching && searchWas) {
                                    search = true;
                                }
                                Spannable span = userSelectEditText.getText();
                                for (int a = 0; a < allSpans.size(); a++) {
                                    Emoji.XImageSpan sp = allSpans.get(a);
                                    if (span.getSpanStart(sp) == -1) {
                                        allSpans.remove(sp);
                                        selectedContacts.remove(sp.uid);
                                    }
                                }
                                if (selectedContacts.isEmpty()) {
                                    doneTextView.setText(getStringEntry(R.string.Done));
                                } else {
                                    doneTextView.setText(getStringEntry(R.string.Done) + " (" + selectedContacts.size() + ")");
                                }
                                countTextView.setText(selectedContacts.size() + "/200");
                                listView.invalidateViews();
                            } else {
                                search = true;
                            }
                        } else {
                            search = true;
                        }
                        if (search) {
                            String text = userSelectEditText.getText().toString().replace("<", "");
                            if (text.length() != 0) {
                                searchDialogs(text);
                                searching = true;
                                searchWas = true;
                                epmtyTextView.setText(getStringEntry(R.string.NoResult));
                                listViewAdapter.notifyDataSetChanged();
                            } else {
                                searchResult = null;
                                searchResultNames = null;
                                searching = false;
                                searchWas = false;
                                epmtyTextView.setText(getStringEntry(R.string.NoContacts));
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
            });

            listView = (PinnedHeaderListView)fragmentView.findViewById(R.id.listView);
            listView.setEmptyView(epmtyTextView);
            listView.setVerticalScrollBarEnabled(false);

            listView.setAdapter(listViewAdapter = new ListAdapter(parentActivity));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    TLRPC.User user;
                    int section = listViewAdapter.getSectionForPosition(i);
                    int row = listViewAdapter.getPositionInSectionForPosition(i);
                    if (searching && searchWas) {
                        user = searchResult.get(row);
                    } else {
                        ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                        user = MessagesController.Instance.users.get(arr.get(row).user_id);
                        listView.invalidateViews();
                    }
                    if (selectedContacts.containsKey(user.id)) {
                        Emoji.XImageSpan span = selectedContacts.get(user.id);
                        selectedContacts.remove(user.id);
                        SpannableStringBuilder text = new SpannableStringBuilder(userSelectEditText.getText());
                        text.delete(text.getSpanStart(span), text.getSpanEnd(span));
                        allSpans.remove(span);
                        ignoreChange = true;
                        userSelectEditText.setText(text);
                        userSelectEditText.setSelection(text.length());
                        ignoreChange = false;
                    } else {
                        if (selectedContacts.size() == 200) {
                            return;
                        }
                        ignoreChange = true;
                        Emoji.XImageSpan span = createAndPutChipForUser(user);
                        span.uid = user.id;
                        ignoreChange = false;
                    }
                    if (selectedContacts.isEmpty()) {
                        doneTextView.setText(getStringEntry(R.string.Done));
                    } else {
                        doneTextView.setText(getStringEntry(R.string.Done) + " (" + selectedContacts.size() + ")");
                    }
                    countTextView.setText(selectedContacts.size() + "/200");
                    if (searching || searchWas) {
                        searching = false;
                        searchWas = false;
                        epmtyTextView.setText(getStringEntry(R.string.NoContacts));

                        ignoreChange = true;
                        SpannableStringBuilder ssb = new SpannableStringBuilder("");
                        for (ImageSpan sp : allSpans) {
                            ssb.append("<<");
                            ssb.setSpan(sp, ssb.length() - 2, ssb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        userSelectEditText.setText(ssb);
                        userSelectEditText.setSelection(ssb.length());
                        ignoreChange = false;

                        listViewAdapter.notifyDataSetChanged();
                    } else {
                        listView.invalidateViews();
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
        actionBar.setTitle(getStringEntry(R.string.NewGroup));

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) {
            return;
        }
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
    }

    public Emoji.XImageSpan createAndPutChipForUser(TLRPC.User user) {
        LayoutInflater lf = (LayoutInflater)parentActivity.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View textView = lf.inflate(R.layout.group_create_bubble, null);
        TextView text = (TextView)textView.findViewById(R.id.bubble_text_view);
        text.setText(Utilities.formatName(user.first_name, user.last_name));

        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        textView.measure(spec, spec);
        textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(textView.getWidth(), textView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.translate(-textView.getScrollX(), -textView.getScrollY());
        textView.draw(canvas);
        textView.setDrawingCacheEnabled(true);
        Bitmap cacheBmp = textView.getDrawingCache();
        Bitmap viewBmp = cacheBmp.copy(Bitmap.Config.ARGB_8888, true);
        textView.destroyDrawingCache();

        final BitmapDrawable bmpDrawable = new BitmapDrawable(b);
        bmpDrawable.setBounds(0, 0, b.getWidth(), b.getHeight());

        SpannableStringBuilder ssb = new SpannableStringBuilder("");
        Emoji.XImageSpan span = new Emoji.XImageSpan(bmpDrawable, ImageSpan.ALIGN_BASELINE);
        allSpans.add(span);
        selectedContacts.put(user.id, span);
        for (ImageSpan sp : allSpans) {
            ssb.append("<<");
            ssb.setSpan(sp, ssb.length() - 2, ssb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        userSelectEditText.setText(ssb);
        userSelectEditText.setSelection(ssb.length());
        return span;
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
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<TLRPC.TL_contact>();
                contactsCopy.addAll(ContactsController.Instance.contacts);
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

                        for (TLRPC.TL_contact contact : contactsCopy) {
                            TLRPC.User user = MessagesController.Instance.users.get(contact.user_id);
                            if (user.first_name.toLowerCase().startsWith(q) || user.last_name.toLowerCase().startsWith(q)) {
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
                listViewAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_create_menu, menu);
        SupportMenuItem doneItem = (SupportMenuItem)menu.findItem(R.id.done_menu_item);
        doneTextView = (TextView)doneItem.getActionView().findViewById(R.id.done_button);
        doneTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!selectedContacts.isEmpty()) {
                    ArrayList<Integer> result = new ArrayList<Integer>();
                    result.addAll(selectedContacts.keySet());
                    NotificationCenter.Instance.addToMemCache(2, result);
                } else {
                    return;
                }
                ((LaunchActivity)parentActivity).presentFragment(new GroupCreateFinalActivity(), "group_craate_final", false);
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
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == MessagesController.chatDidCreated) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    removeSelfFromStack();
                }
            });
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
            if (searching && searchWas) {
                return searchResult == null || searchResult.isEmpty() ? 0 : 1;
            }
            return ContactsController.Instance.sortedUsersSectionsArray.size();
        }

        @Override
        public int getCountForSection(int section) {
            if (searching && searchWas) {
                return searchResult == null ? 0 : searchResult.size();
            }
            ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
            return arr.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            TLRPC.User user;
            int size;

            if (searchWas && searching) {
                user = MessagesController.Instance.users.get(searchResult.get(position).id);
                size = searchResult.size();
            } else {
                ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                user = MessagesController.Instance.users.get(arr.get(position).user_id);
                size = arr.size();
            }

            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.group_create_row_layout, parent, false);
            }
            ContactListRowHolder holder = (ContactListRowHolder)convertView.getTag();
            if (holder == null) {
                holder = new ContactListRowHolder(convertView);
                convertView.setTag(holder);
            }

            ImageView checkButton = (ImageView)convertView.findViewById(R.id.settings_row_check_button);
            if (selectedContacts.containsKey(user.id)) {
                checkButton.setImageResource(R.drawable.btn_check_on_old);
            } else {
                checkButton.setImageResource(R.drawable.btn_check_off_old);
            }

            View divider = convertView.findViewById(R.id.settings_row_divider);
            if (position == size - 1) {
                divider.setVisibility(View.INVISIBLE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            if (searchWas && searching) {
                holder.nameTextView.setText(searchResultNames.get(position));
            } else {
                if (user.first_name.length() != 0 && user.last_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml(user.first_name + " <b>" + user.last_name + "</b>"));
                } else if (user.first_name.length() != 0) {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.first_name + "</b>"));
                } else {
                    holder.nameTextView.setText(Html.fromHtml("<b>" + user.last_name + "</b>"));
                }
            }

            TLRPC.FileLocation photo = null;
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
                if (user.status.expires > currentTime) {
                    holder.messageTextView.setTextColor(0xff357aa8);
                    holder.messageTextView.setText(getStringEntry(R.string.Online));
                } else {
                    if (user.status.expires <= 10000) {
                        holder.messageTextView.setText(getStringEntry(R.string.Invisible));
                    } else {
                        holder.messageTextView.setText(Utilities.formatDateOnline(user.status.expires));
                    }
                    holder.messageTextView.setTextColor(0xff808080);
                }
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int section, int position) {
            return 0;
        }

        @Override
        public int getItemViewTypeCount() {
            return 1;
        }

        @Override
        public int getSectionHeaderViewType(int section) {
            return 0;
        }

        @Override
        public int getSectionHeaderViewTypeCount() {
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                convertView.setBackgroundColor(0xffffffff);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
            if (searching && searchWas) {
                textView.setText(getStringEntry(R.string.AllContacts));
            } else {
                textView.setText(ContactsController.Instance.sortedUsersSectionsArray.get(section));
            }
            return convertView;
        }
    }

    public static class ContactListRowHolder {
        public BackupImageView avatarImage;
        public TextView messageTextView;
        public TextView nameTextView;

        public ContactListRowHolder(View view) {
            messageTextView = (TextView)view.findViewById(R.id.messages_list_row_message);
            nameTextView = (TextView)view.findViewById(R.id.messages_list_row_name);
            avatarImage = (BackupImageView)view.findViewById(R.id.messages_list_row_avatar);
        }
    }
}
