/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.BaseSectionsAdapter;
import org.telegram.ui.Adapters.ContactsActivityAdapter;
import org.telegram.ui.Adapters.ContactsActivitySearchAdapter;
import org.telegram.ui.Views.ActionBar.ActionBar;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.SectionsListView;

import java.util.ArrayList;
import java.util.HashMap;

public class GroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public class XImageSpan extends ImageSpan {
        public int uid;

        public XImageSpan(Drawable d, int verticalAlignment) {
            super(d, verticalAlignment);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = new Paint.FontMetricsInt();
            }

            int sz = super.getSize(paint, text, start, end, fm);
            int offset = AndroidUtilities.dp(6);
            int w = (fm.bottom - fm.top) / 2;
            fm.top = -w - offset;
            fm.bottom = w - offset;
            fm.ascent = -w - offset;
            fm.leading = 0;
            fm.descent = w - offset;
            return sz;
        }
    }

    private BaseSectionsAdapter listViewAdapter;
    private TextView emptyTextView;
    private EditText userSelectEditText;
    private SectionsListView listView;
    private ContactsActivitySearchAdapter searchListViewAdapter;

    private int beforeChangeIndex;
    private int maxCount = 200;
    private boolean ignoreChange = false;
    private boolean isBroadcast = false;
    private boolean searchWas;
    private boolean searching;
    private CharSequence changeString;
    private HashMap<Integer, XImageSpan> selectedContacts =  new HashMap<Integer, XImageSpan>();
    private ArrayList<XImageSpan> allSpans = new ArrayList<XImageSpan>();

    private final static int done_button = 1;

    public GroupCreateActivity() {
        super();
    }

    public GroupCreateActivity(Bundle args) {
        super(args);
        isBroadcast = args.getBoolean("broadcast", false);
        maxCount = !isBroadcast ? MessagesController.getInstance().maxGroupCount : MessagesController.getInstance().maxBroadcastCount;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatDidCreated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatDidCreated);
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            searching = false;
            searchWas = false;

            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setBackOverlay(R.layout.updating_state_layout);
            actionBar.setTitle(isBroadcast ? LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList) : LocaleController.getString("NewGroup", R.string.NewGroup));
            actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        if (!selectedContacts.isEmpty()) {
                            ArrayList<Integer> result = new ArrayList<Integer>();
                            result.addAll(selectedContacts.keySet());
                            Bundle args = new Bundle();
                            args.putIntegerArrayList("result", result);
                            args.putBoolean("broadcast", isBroadcast);
                            presentFragment(new GroupCreateFinalActivity(args));
                        }
                    }
                }
            });
            ActionBarMenu menu = actionBar.createMenu();
            View doneItem = menu.addItemResource(done_button, R.layout.group_create_done_layout);
            TextView doneTextView = (TextView)doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Next", R.string.Next));

            searchListViewAdapter = new ContactsActivitySearchAdapter(getParentActivity(), null, false);
            listViewAdapter = new ContactsActivityAdapter(getParentActivity(), true, false, null);

            /*


    <FrameLayout
        android:layout_width="fill_parent"
        android:layout_height="wrap_content"
        android:id="@+id/top_layout"
        android:layout_gravity="top">

        <EditText
            android:textColorHint="#a6a6a6"
            android:layout_width="fill_parent"
            android:layout_height="wrap_content"
            android:layout_marginLeft="5dp"
            android:layout_marginRight="5dp"
            android:minHeight="52dp"
            android:gravity="right|center_vertical"
            android:maxLines="2"
            android:paddingTop="3dp"
            android:layout_marginTop="0dp"
            android:inputType="textFilter|textNoSuggestions|textMultiLine"
            android:imeOptions="flagNoExtractUi"
            android:textCursorDrawable="@null"
            android:textColor="#000000"/>

    </FrameLayout>


            ------------RTL---------- END


        <EditText
            android:textColorHint="#a6a6a6"
            android:layout_width="fill_parent"
            android:layout_height="wrap_content"
            android:layout_marginLeft="5dp"
            android:layout_marginRight="5dp"
            android:minHeight="52dp"
            android:gravity="left|center_vertical"
            android:maxLines="2"
            android:paddingTop="3dp"
            android:layout_marginTop="0dp"
            android:inputType="textFilter|textNoSuggestions|textMultiLine"
            android:imeOptions="flagNoExtractUi"
            android:textCursorDrawable="@null"
            android:textColor="#000000"/>


             */

            fragmentView = new LinearLayout(getParentActivity());
            LinearLayout linearLayout = (LinearLayout) fragmentView;
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            emptyTextView = new TextView(getParentActivity());
            emptyTextView.setTextColor(0xff808080);
            emptyTextView.setTextSize(24);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setVisibility(View.INVISIBLE);
            emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
            linearLayout.addView(emptyTextView);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) emptyTextView.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            emptyTextView.setLayoutParams(layoutParams);
            emptyTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            FrameLayout frameLayout = new FrameLayout(getParentActivity());
            linearLayout.addView(frameLayout);
            layoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.TOP;
            frameLayout.setLayoutParams(layoutParams);

            userSelectEditText = new EditText(getParentActivity());
            userSelectEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            frameLayout.addView(userSelectEditText);

            userSelectEditText.setHint(LocaleController.getString("SendMessageTo", R.string.SendMessageTo));
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
                                    XImageSpan sp = allSpans.get(a);
                                    if (span.getSpanStart(sp) == -1) {
                                        allSpans.remove(sp);
                                        selectedContacts.remove(sp.uid);
                                    }
                                }
                                actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
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
                                emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                                listViewAdapter.notifyDataSetChanged();
                            } else {
                                searchResult = null;
                                searchResultNames = null;
                                searching = false;
                                searchWas = false;
                                emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                                listViewAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
            });

            listView = new SectionsListView(getParentActivity());
            listView.setEmptyView(emptyTextView);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setFastScrollEnabled(true);
            listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
            listView.setAdapter(listViewAdapter);
            if (Build.VERSION.SDK_INT >= 11) {
                listView.setFastScrollAlwaysVisible(true);
                listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }
            ((FrameLayout) fragmentView).addView(listView);
            layoutParams = (LinearLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    TLRPC.User user;
                    int section = listViewAdapter.getSectionForPosition(i);
                    int row = listViewAdapter.getPositionInSectionForPosition(i);
                    if (searching && searchWas) {
                        user = searchResult.get(row);
                    } else {
                        ArrayList<TLRPC.TL_contact> arr = ContactsController.getInstance().usersSectionsDict.get(ContactsController.getInstance().sortedUsersSectionsArray.get(section));
                        user = MessagesController.getInstance().getUser(arr.get(row).user_id);
                        listView.invalidateViews();
                    }
                    if (selectedContacts.containsKey(user.id)) {
                        XImageSpan span = selectedContacts.get(user.id);
                        selectedContacts.remove(user.id);
                        SpannableStringBuilder text = new SpannableStringBuilder(userSelectEditText.getText());
                        text.delete(text.getSpanStart(span), text.getSpanEnd(span));
                        allSpans.remove(span);
                        ignoreChange = true;
                        userSelectEditText.setText(text);
                        userSelectEditText.setSelection(text.length());
                        ignoreChange = false;
                    } else {
                        if (selectedContacts.size() == maxCount) {
                            return;
                        }
                        ignoreChange = true;
                        XImageSpan span = createAndPutChipForUser(user);
                        span.uid = user.id;
                        ignoreChange = false;
                    }
                    actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
                    if (searching || searchWas) {
                        searching = false;
                        searchWas = false;
                        emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));

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
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL) {
                        AndroidUtilities.hideKeyboard(userSelectEditText);
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.contactsDidLoaded) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                if (listView != null) {
                    listView.invalidateViews();
                }
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    removeSelfFromStack();
                }
            });
        }
    }

    public XImageSpan createAndPutChipForUser(TLRPC.User user) {
        LayoutInflater lf = (LayoutInflater)ApplicationLoader.applicationContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View textView = lf.inflate(R.layout.group_create_bubble, null);
        TextView text = (TextView)textView.findViewById(R.id.bubble_text_view);
        String name = ContactsController.formatName(user.first_name, user.last_name);
        if (name.length() == 0 && user.phone != null && user.phone.length() != 0) {
            name = PhoneFormat.getInstance().format("+" + user.phone);
        }
        text.setText(name + ", ");

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
        XImageSpan span = new XImageSpan(bmpDrawable, ImageSpan.ALIGN_BASELINE);
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
}
