/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
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
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.ContactsAdapter;
import org.telegram.ui.Adapters.SearchAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.ChipSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterSectionsListView;

import java.util.ArrayList;
import java.util.HashMap;

public class GroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface GroupCreateActivityDelegate {
        void didSelectUsers(ArrayList<Integer> ids);
    }

    private ContactsAdapter listViewAdapter;
    private TextView emptyTextView;
    private EditText userSelectEditText;
    private LetterSectionsListView listView;
    private SearchAdapter searchListViewAdapter;

    private GroupCreateActivityDelegate delegate;

    private int beforeChangeIndex;
    private boolean ignoreChange;
    private CharSequence changeString;
    private int maxCount = 5000;
    private int chatType = ChatObject.CHAT_TYPE_CHAT;
    private boolean isAlwaysShare;
    private boolean isNeverShare;
    private boolean searchWas;
    private boolean searching;
    private boolean isGroup;
    private HashMap<Integer, ChipSpan> selectedContacts = new HashMap<>();
    private ArrayList<ChipSpan> allSpans = new ArrayList<>();

    private final static int done_button = 1;

    public GroupCreateActivity() {
        super();
    }

    public GroupCreateActivity(Bundle args) {
        super(args);
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
        isAlwaysShare = args.getBoolean("isAlwaysShare", false);
        isNeverShare = args.getBoolean("isNeverShare", false);
        isGroup = args.getBoolean("isGroup", false);
        maxCount = chatType == ChatObject.CHAT_TYPE_CHAT ? MessagesController.getInstance().maxMegagroupCount : MessagesController.getInstance().maxBroadcastCount;
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
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (isAlwaysShare) {
            if (isGroup) {
                actionBar.setTitle(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow));
            } else {
                actionBar.setTitle(LocaleController.getString("AlwaysShareWithTitle", R.string.AlwaysShareWithTitle));
            }
        } else if (isNeverShare) {
            if (isGroup) {
                actionBar.setTitle(LocaleController.getString("NeverAllow", R.string.NeverAllow));
            } else {
                actionBar.setTitle(LocaleController.getString("NeverShareWithTitle", R.string.NeverShareWithTitle));
            }
        } else {
            actionBar.setTitle(chatType == ChatObject.CHAT_TYPE_CHAT ? LocaleController.getString("NewGroup", R.string.NewGroup) : LocaleController.getString("NewBroadcastList", R.string.NewBroadcastList));
            actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (selectedContacts.isEmpty()) {
                        return;
                    }
                    ArrayList<Integer> result = new ArrayList<>();
                    result.addAll(selectedContacts.keySet());
                    if (isAlwaysShare || isNeverShare) {
                        if (delegate != null) {
                            delegate.didSelectUsers(result);
                        }
                        finishFragment();
                    } else {
                        Bundle args = new Bundle();
                        args.putIntegerArrayList("result", result);
                        args.putInt("chatType", chatType);
                        presentFragment(new GroupCreateFinalActivity(args));
                    }
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        searchListViewAdapter = new SearchAdapter(context, null, false, false, false, false);
        searchListViewAdapter.setCheckedMap(selectedContacts);
        searchListViewAdapter.setUseUserCell(true);
        listViewAdapter = new ContactsAdapter(context, 1, false, null, false);
        listViewAdapter.setCheckedMap(selectedContacts);

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        userSelectEditText = new EditText(context);
        userSelectEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        userSelectEditText.setHintTextColor(0xff979797);
        userSelectEditText.setTextColor(0xff212121);
        userSelectEditText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        userSelectEditText.setMinimumHeight(AndroidUtilities.dp(54));
        userSelectEditText.setSingleLine(false);
        userSelectEditText.setLines(2);
        userSelectEditText.setMaxLines(2);
        userSelectEditText.setVerticalScrollBarEnabled(true);
        userSelectEditText.setHorizontalScrollBarEnabled(false);
        userSelectEditText.setPadding(0, 0, 0, 0);
        userSelectEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        userSelectEditText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        AndroidUtilities.clearCursorDrawable(userSelectEditText);
        frameLayout.addView(userSelectEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 10, 0, 10, 0));

        if (isAlwaysShare) {
            if (isGroup) {
                userSelectEditText.setHint(LocaleController.getString("AlwaysAllowPlaceholder", R.string.AlwaysAllowPlaceholder));
            } else {
                userSelectEditText.setHint(LocaleController.getString("AlwaysShareWithPlaceholder", R.string.AlwaysShareWithPlaceholder));
            }
        } else if (isNeverShare) {
            if (isGroup) {
                userSelectEditText.setHint(LocaleController.getString("NeverAllowPlaceholder", R.string.NeverAllowPlaceholder));
            } else {
                userSelectEditText.setHint(LocaleController.getString("NeverShareWithPlaceholder", R.string.NeverShareWithPlaceholder));
            }
        } else {
            userSelectEditText.setHint(LocaleController.getString("SendMessageTo", R.string.SendMessageTo));
        }
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
                                ChipSpan sp = allSpans.get(a);
                                if (span.getSpanStart(sp) == -1) {
                                    allSpans.remove(sp);
                                    selectedContacts.remove(sp.uid);
                                }
                            }
                            if (!isAlwaysShare && !isNeverShare) {
                                actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
                            }
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
                            searching = true;
                            searchWas = true;
                            if (listView != null) {
                                listView.setAdapter(searchListViewAdapter);
                                searchListViewAdapter.notifyDataSetChanged();
                                if (android.os.Build.VERSION.SDK_INT >= 11) {
                                    listView.setFastScrollAlwaysVisible(false);
                                }
                                listView.setFastScrollEnabled(false);
                                listView.setVerticalScrollBarEnabled(true);
                            }
                            if (emptyTextView != null) {
                                emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                            }
                            searchListViewAdapter.searchDialogs(text);
                        } else {
                            searchListViewAdapter.searchDialogs(null);
                            searching = false;
                            searchWas = false;
                            listView.setAdapter(listViewAdapter);
                            listViewAdapter.notifyDataSetChanged();
                            if (android.os.Build.VERSION.SDK_INT >= 11) {
                                listView.setFastScrollAlwaysVisible(true);
                            }
                            listView.setFastScrollEnabled(true);
                            listView.setVerticalScrollBarEnabled(false);
                            emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                        }
                    }
                }
            }
        });

        LinearLayout emptyTextLayout = new LinearLayout(context);
        emptyTextLayout.setVisibility(View.INVISIBLE);
        emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(emptyTextLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        emptyTextView = new TextView(context);
        emptyTextView.setTextColor(0xff808080);
        emptyTextView.setTextSize(20);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        emptyTextLayout.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

        FrameLayout frameLayout2 = new FrameLayout(context);
        emptyTextLayout.addView(frameLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

        listView = new LetterSectionsListView(context);
        listView.setEmptyView(emptyTextLayout);
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
        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                TLRPC.User user;
                if (searching && searchWas) {
                    user = (TLRPC.User) searchListViewAdapter.getItem(i);
                } else {
                    int section = listViewAdapter.getSectionForPosition(i);
                    int row = listViewAdapter.getPositionInSectionForPosition(i);
                    if (row < 0 || section < 0) {
                        return;
                    }
                    user = (TLRPC.User) listViewAdapter.getItem(section, row);
                }
                if (user == null) {
                    return;
                }

                boolean check = true;
                if (selectedContacts.containsKey(user.id)) {
                    check = false;
                    try {
                        ChipSpan span = selectedContacts.get(user.id);
                        selectedContacts.remove(user.id);
                        SpannableStringBuilder text = new SpannableStringBuilder(userSelectEditText.getText());
                        text.delete(text.getSpanStart(span), text.getSpanEnd(span));
                        allSpans.remove(span);
                        ignoreChange = true;
                        userSelectEditText.setText(text);
                        userSelectEditText.setSelection(text.length());
                        ignoreChange = false;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else {
                    if (maxCount != 0 && selectedContacts.size() == maxCount) {
                        return;
                    }
                    if (chatType == ChatObject.CHAT_TYPE_CHAT && selectedContacts.size() == MessagesController.getInstance().maxGroupCount - 1) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("SoftUserLimitAlert", R.string.SoftUserLimitAlert));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                    ignoreChange = true;
                    ChipSpan span = createAndPutChipForUser(user);
                    span.uid = user.id;
                    ignoreChange = false;
                }
                if (!isAlwaysShare && !isNeverShare) {
                    actionBar.setSubtitle(LocaleController.formatString("MembersCount", R.string.MembersCount, selectedContacts.size(), maxCount));
                }
                if (searching || searchWas) {
                    ignoreChange = true;
                    SpannableStringBuilder ssb = new SpannableStringBuilder("");
                    for (ImageSpan sp : allSpans) {
                        ssb.append("<<");
                        ssb.setSpan(sp, ssb.length() - 2, ssb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    userSelectEditText.setText(ssb);
                    userSelectEditText.setSelection(ssb.length());
                    ignoreChange = false;

                    searchListViewAdapter.searchDialogs(null);
                    searching = false;
                    searchWas = false;
                    listView.setAdapter(listViewAdapter);
                    listViewAdapter.notifyDataSetChanged();
                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                        listView.setFastScrollAlwaysVisible(true);
                    }
                    listView.setFastScrollEnabled(true);
                    listView.setVerticalScrollBarEnabled(false);
                    emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                } else {
                    if (view instanceof UserCell) {
                        ((UserCell) view).setChecked(check, true);
                    }
                }
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
                if (i == SCROLL_STATE_TOUCH_SCROLL) {
                    AndroidUtilities.hideKeyboard(userSelectEditText);
                }
                if (listViewAdapter != null) {
                    listViewAdapter.setIsScrolling(i != SCROLL_STATE_IDLE);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (absListView.isFastScrollEnabled()) {
                    AndroidUtilities.clearDrawableAnimation(absListView);
                }
            }
        });

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
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            removeSelfFromStack();
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView != null) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof UserCell) {
                    ((UserCell) child).update(mask);
                }
            }
        }
    }

    public void setDelegate(GroupCreateActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private ChipSpan createAndPutChipForUser(TLRPC.User user) {
        LayoutInflater lf = (LayoutInflater) ApplicationLoader.applicationContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View textView = lf.inflate(R.layout.group_create_bubble, null);
        TextView text = (TextView)textView.findViewById(R.id.bubble_text_view);
        String name = UserObject.getUserName(user);
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
        ChipSpan span = new ChipSpan(bmpDrawable, ImageSpan.ALIGN_BASELINE);
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
