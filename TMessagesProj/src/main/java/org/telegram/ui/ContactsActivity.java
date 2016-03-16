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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseSectionsAdapter;
import org.telegram.ui.Adapters.ContactsAdapter;
import org.telegram.ui.Adapters.SearchAdapter;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterSectionsListView;

import java.util.ArrayList;
import java.util.HashMap;

public class ContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private BaseSectionsAdapter listViewAdapter;
    private TextView emptyTextView;
    private LetterSectionsListView listView;
    private SearchAdapter searchListViewAdapter;

    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean needPhonebook;
    private boolean destroyAfterSelect;
    private boolean returnAsResult;
    private boolean createSecretChat;
    private boolean creatingChat = false;
    private boolean allowBots = true;
    private boolean needForwardCount = true;
    private int chat_id;
    private String selectAlertString = null;
    private HashMap<Integer, TLRPC.User> ignoreUsers;
    private boolean allowUsernameSearch = true;
    private ContactsActivityDelegate delegate;

    public interface ContactsActivityDelegate {
        void didSelectContact(TLRPC.User user, String param);
    }

    public ContactsActivity(Bundle args) {
        super(args);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        if (arguments != null) {
            onlyUsers = getArguments().getBoolean("onlyUsers", false);
            destroyAfterSelect = arguments.getBoolean("destroyAfterSelect", false);
            returnAsResult = arguments.getBoolean("returnAsResult", false);
            createSecretChat = arguments.getBoolean("createSecretChat", false);
            selectAlertString = arguments.getString("selectAlertString");
            allowUsernameSearch = arguments.getBoolean("allowUsernameSearch", true);
            needForwardCount = arguments.getBoolean("needForwardCount", true);
            allowBots = arguments.getBoolean("allowBots", true);
            chat_id = arguments.getInt("chat_id", 0);
        } else {
            needPhonebook = true;
        }

        ContactsController.getInstance().checkInviteText();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        delegate = null;
    }

    @Override
    public View createView(Context context) {

        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (destroyAfterSelect) {
            if (returnAsResult) {
                actionBar.setTitle(LocaleController.getString("SelectContact", R.string.SelectContact));
            } else {
                if (createSecretChat) {
                    actionBar.setTitle(LocaleController.getString("NewSecretChat", R.string.NewSecretChat));
                } else {
                    actionBar.setTitle(LocaleController.getString("NewMessageTitle", R.string.NewMessageTitle));
                }
            }
        } else {
            actionBar.setTitle(LocaleController.getString("Contacts", R.string.Contacts));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
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

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                if (text.length() != 0) {
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
                }
                searchListViewAdapter.searchDialogs(text);
            }
        });
        item.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        searchListViewAdapter = new SearchAdapter(context, ignoreUsers, allowUsernameSearch, false, false, allowBots);
        listViewAdapter = new ContactsAdapter(context, onlyUsers ? 1 : 0, needPhonebook, ignoreUsers, chat_id != 0);

        fragmentView = new FrameLayout(context);

        LinearLayout emptyTextLayout = new LinearLayout(context);
        emptyTextLayout.setVisibility(View.INVISIBLE);
        emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
        ((FrameLayout) fragmentView).addView(emptyTextLayout);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emptyTextLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP;
        emptyTextLayout.setLayoutParams(layoutParams);
        emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        emptyTextView = new TextView(context);
        emptyTextView.setTextColor(0xff808080);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        emptyTextLayout.addView(emptyTextView);
        LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) emptyTextView.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        layoutParams1.weight = 0.5f;
        emptyTextView.setLayoutParams(layoutParams1);

        FrameLayout frameLayout = new FrameLayout(context);
        emptyTextLayout.addView(frameLayout);
        layoutParams1 = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        layoutParams1.weight = 0.5f;
        frameLayout.setLayoutParams(layoutParams1);

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
        ((FrameLayout) fragmentView).addView(listView);
        layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        listView.setLayoutParams(layoutParams);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (searching && searchWas) {
                    TLRPC.User user = (TLRPC.User) searchListViewAdapter.getItem(i);
                    if (user == null) {
                        return;
                    }
                    if (searchListViewAdapter.isGlobalSearch(i)) {
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        MessagesController.getInstance().putUsers(users, false);
                        MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                    }
                    if (returnAsResult) {
                        if (ignoreUsers != null && ignoreUsers.containsKey(user.id)) {
                            return;
                        }
                        didSelectResult(user, true, null);
                    } else {
                        if (createSecretChat) {
                            if (user.id == UserConfig.getClientUserId()) {
                                return;
                            }
                            creatingChat = true;
                            SecretChatHelper.getInstance().startSecretChat(getParentActivity(), user);
                        } else {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            if (MessagesController.checkCanOpenChat(args, ContactsActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }
                } else {
                    int section = listViewAdapter.getSectionForPosition(i);
                    int row = listViewAdapter.getPositionInSectionForPosition(i);
                    if (row < 0 || section < 0) {
                        return;
                    }
                    if ((!onlyUsers || chat_id != 0) && section == 0) {
                        if (needPhonebook) {
                            if (row == 0) {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    intent.setType("text/plain");
                                    intent.putExtra(Intent.EXTRA_TEXT, ContactsController.getInstance().getInviteText());
                                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("InviteFriends", R.string.InviteFriends)), 500);
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        } else if (chat_id != 0) {
                            if (row == 0) {
                                presentFragment(new GroupInviteActivity(chat_id));
                            }
                        } else {
                            if (row == 0) {
                                if (!MessagesController.isFeatureEnabled("chat_create", ContactsActivity.this)) {
                                    return;
                                }
                                presentFragment(new GroupCreateActivity(), false);
                            } else if (row == 1) {
                                Bundle args = new Bundle();
                                args.putBoolean("onlyUsers", true);
                                args.putBoolean("destroyAfterSelect", true);
                                args.putBoolean("createSecretChat", true);
                                args.putBoolean("allowBots", false);
                                presentFragment(new ContactsActivity(args), false);
                            } else if (row == 2) {
                                if (!MessagesController.isFeatureEnabled("broadcast_create", ContactsActivity.this)) {
                                    return;
                                }
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                if (preferences.getBoolean("channel_intro", false)) {
                                    Bundle args = new Bundle();
                                    args.putInt("step", 0);
                                    presentFragment(new ChannelCreateActivity(args));
                                } else {
                                    presentFragment(new ChannelIntroActivity());
                                    preferences.edit().putBoolean("channel_intro", true).commit();
                                }
                            }
                        }
                    } else {
                        Object item = listViewAdapter.getItem(section, row);

                        if (item instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) item;
                            if (returnAsResult) {
                                if (ignoreUsers != null && ignoreUsers.containsKey(user.id)) {
                                    return;
                                }
                                didSelectResult(user, true, null);
                            } else {
                                if (createSecretChat) {
                                    creatingChat = true;
                                    SecretChatHelper.getInstance().startSecretChat(getParentActivity(), user);
                                } else {
                                    Bundle args = new Bundle();
                                    args.putInt("user_id", user.id);
                                    if (MessagesController.checkCanOpenChat(args, ContactsActivity.this)) {
                                        presentFragment(new ChatActivity(args), true);
                                    }
                                }
                            }
                        } else if (item instanceof ContactsController.Contact) {
                            ContactsController.Contact contact = (ContactsController.Contact) item;
                            String usePhone = null;
                            if (!contact.phones.isEmpty()) {
                                usePhone = contact.phones.get(0);
                            }
                            if (usePhone == null || getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("InviteUser", R.string.InviteUser));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            final String arg1 = usePhone;
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null));
                                        intent.putExtra("sms_body", LocaleController.getString("InviteText", R.string.InviteText));
                                        getParentActivity().startActivityForResult(intent, 500);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                        }
                    }
                }
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
                if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
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

    private void didSelectResult(final TLRPC.User user, boolean useAlert, String param) {
        if (useAlert && selectAlertString != null) {
            if (getParentActivity() == null) {
                return;
            }
            if (user.bot && user.bot_nochats) {
                try {
                    Toast.makeText(getParentActivity(), LocaleController.getString("BotCantJoinGroups", R.string.BotCantJoinGroups), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            String message = LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user));
            EditText editText = null;
            if (!user.bot && needForwardCount) {
                message = String.format("%s\n\n%s", message, LocaleController.getString("AddToTheGroupForwardCount", R.string.AddToTheGroupForwardCount));
                editText = new EditText(getParentActivity());
                if (android.os.Build.VERSION.SDK_INT < 11) {
                    editText.setBackgroundResource(android.R.drawable.editbox_background_normal);
                }
                editText.setTextSize(18);
                editText.setText("50");
                editText.setGravity(Gravity.CENTER);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                final EditText editTextFinal = editText;
                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            String str = s.toString();
                            if (str.length() != 0) {
                                int value = Utilities.parseInt(str);
                                if (value < 0) {
                                    editTextFinal.setText("0");
                                    editTextFinal.setSelection(editTextFinal.length());
                                } else if (value > 300) {
                                    editTextFinal.setText("300");
                                    editTextFinal.setSelection(editTextFinal.length());
                                } else if (!str.equals("" + value)) {
                                    editTextFinal.setText("" + value);
                                    editTextFinal.setSelection(editTextFinal.length());
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                });
                builder.setView(editText);
            }
            builder.setMessage(message);
            final EditText finalEditText = editText;
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(user, false, finalEditText != null ? finalEditText.getText().toString() : "0");
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
            if (editText != null) {
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
                if (layoutParams != null) {
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                    }
                    layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(10);
                    editText.setLayoutParams(layoutParams);
                }
                editText.setSelection(editText.getText().length());
            }
        } else {
            if (delegate != null) {
                delegate.didSelectContact(user, param);
                delegate = null;
            }
            finishFragment();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (actionBar != null) {
            actionBar.closeSearchField();
        }
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
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (createSecretChat && creatingChat) {
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat)args[0];
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", encryptedChat.id);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                presentFragment(new ChatActivity(args2), true);
            }
        } else if (id == NotificationCenter.closeChats) {
            if (!creatingChat) {
                removeSelfFromStack();
            }
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

    public void setDelegate(ContactsActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public void setIgnoreUsers(HashMap<Integer, TLRPC.User> users) {
        ignoreUsers = users;
    }
}
