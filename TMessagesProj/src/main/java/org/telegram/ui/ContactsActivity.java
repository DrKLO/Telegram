/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.ActionBar.SimpleActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.ContactsAdapter;
import org.telegram.ui.Adapters.SearchAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ContactsEmptyView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;

import java.util.ArrayList;
import java.util.HashSet;

import me.vkryl.android.animator.FactorAnimator;

public class ContactsActivity extends BaseFragment implements FactorAnimator.Target, NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate, WindowAnimatedInsetsProvider.Listener {
    private static final int ANIMATOR_ID_SECTION_LETTERS = 0;

    /** Section letters appear after the list fades in with the name sorting. */
    private final FactorAnimator animatorSectionLetters = new FactorAnimator(ANIMATOR_ID_SECTION_LETTERS,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, 1f);

    @Keep
    public int phonebookRow = 0;

    private ContactsAdapter listViewAdapter;
    private StickerEmptyView emptyView;
    private RecyclerListView listView;
    private RecyclerAnimationScrollHelper scrollHelper;
    private LinearLayoutManager layoutManager;
    private SearchAdapter searchListViewAdapter;

    private SimpleActionBarMenuItem sortItem;
    private boolean sortByName;

    private FragmentFloatingButton floatingButton;
    private boolean floatingButtonVisibleByScroll = true;
    private SizeNotifierFrameLayout contentView;

    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean needPhonebook;
    public boolean hasMainTabs;
    private boolean destroyAfterSelect;
    private boolean returnAsResult;
    private boolean createSecretChat;
    private boolean creatingChat;
    private boolean allowSelf = true;
    private boolean allowBots = true;
    private boolean needForwardCount = true;
    private boolean needFinishFragment = true;
    private boolean resetDelegate = true;
    private long channelId;
    private long chatId;
    private String selectAlertString = null;
    private LongSparseArray<TLRPC.User> ignoreUsers;
    private boolean allowUsernameSearch = true;
    private ContactsActivityDelegate delegate;
    private String initialSearchString;

    private AlertDialog permissionDialog;
    private boolean askAboutContacts = true;

    private boolean disableSections;

    private final LongSparseArray<TLRPC.User> selectedContacts = new LongSparseArray<>();

    private SimpleActionBarMenuItem searchItem;

    private String searchQuery;

    private boolean checkPermission = true;
    private long permissionRequestTime;

    private final static int search_button = 0;
    private final static int sort_button = 1;

    private final static int delete = 100;



    public interface ContactsActivityDelegate {
        void didSelectContact(TLRPC.User user, String param, ContactsActivity activity);
    }

    public ContactsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.closeChats);
        checkPermission = UserConfig.getInstance(currentAccount).syncContacts;
        if (arguments != null) {
            onlyUsers = arguments.getBoolean("onlyUsers", false);
            destroyAfterSelect = arguments.getBoolean("destroyAfterSelect", false);
            returnAsResult = arguments.getBoolean("returnAsResult", false);
            createSecretChat = arguments.getBoolean("createSecretChat", false);
            selectAlertString = arguments.getString("selectAlertString");
            allowUsernameSearch = arguments.getBoolean("allowUsernameSearch", true);
            needForwardCount = arguments.getBoolean("needForwardCount", true);
            allowBots = arguments.getBoolean("allowBots", true);
            allowSelf = arguments.getBoolean("allowSelf", true);
            channelId = arguments.getLong("channelId", 0);
            needFinishFragment = arguments.getBoolean("needFinishFragment", true);
            chatId = arguments.getLong("chat_id", 0);
            disableSections = arguments.getBoolean("disableSections", false);
            resetDelegate = arguments.getBoolean("resetDelegate", false);
            needPhonebook = arguments.getBoolean("needPhonebook", false);
            hasMainTabs = arguments.getBoolean("hasMainTabs", false);
        } else {
            needPhonebook = true;
        }

        if (!createSecretChat && !returnAsResult) {
            sortByName = SharedConfig.sortContactsByName;
        }

        getContactsController().checkInviteText();
        getContactsController().reloadContactsStatusesMaybe(false);

        additionNavigationBarHeight = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        additionFloatingButtonOffset = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN) : 0;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        delegate = null;
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        super.onTransitionAnimationProgress(isOpen, progress);
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        if (destroyAfterSelect) {
            if (returnAsResult) {
                simpleActionBar.setTitle(getString(R.string.SelectContact));
            } else {
                simpleActionBar.setTitle(getString(createSecretChat ? R.string.NewSecretChat : R.string.NewMessageTitle));
            }
        } else {
            simpleActionBar.setTitle(getString(R.string.Contacts));
        }

        if (!hasMainTabs) {
            simpleActionBar.setBackButtonDrawable(new BackDrawable(false));
        }

        final SimpleActionBarMenu actionMode = simpleActionBar.createActionMode();
        actionMode.addItemWithWidth(delete, R.drawable.msg_delete, dp(54), getString(R.string.Delete));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (simpleActionBar.isActionModeShowed()) {
                        hideActionMode();
                    } else {
                        finishFragment();
                    }
                } else if (id == delete) {
                    performSelectedContactsDelete();
                } else if (id == sort_button) {
                    toggleSort();
                }
            }
        });

        final SimpleActionBarMenu menu = simpleActionBar.createMenu();
        searchItem = menu.addItem(search_button, R.drawable.outline_header_search);
        searchItem.setContentDescription(getString(R.string.SearchContacts));
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                checkUi_floatingButtonVisible();
            }

            @Override
            public void onSearchCollapse() {
                searchListViewAdapter.searchDialogs(null);
                searchQuery = null;
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
                listViewAdapter.notifyDataSetChanged();
                listView.setFastScrollVisible(true);
                listView.setVerticalScrollBarEnabled(false);
                checkUi_floatingButtonVisible();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                searchQuery = text;
                if (!text.isEmpty()) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setAdapter(searchListViewAdapter);
                        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_SIMPLE);
                        searchListViewAdapter.notifyDataSetChanged();
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    emptyView.showProgress(true, true);
                    searchListViewAdapter.searchDialogs(text);
                } else {
                    searchWas = false;
                    searchListViewAdapter.searchDialogs(null);
                    if (listView != null) {
                        listView.setAdapter(listViewAdapter);
                        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
                        listView.setFastScrollVisible(true);
                        listView.setVerticalScrollBarEnabled(false);
                    }
                }
            }
        });
        if (!createSecretChat && !returnAsResult) {
            sortItem = menu.addItem(sort_button, sortByName ? R.drawable.msg_contacts_time : R.drawable.msg_contacts_name);
            sortItem.setContentDescription(getString(R.string.AccDescrContactSorting));
        }

        listView = new RecyclerListView(context);
        searchListViewAdapter = new SearchAdapter(listView, context, ignoreUsers, selectedContacts, allowUsernameSearch, false, false, allowBots, allowSelf, true, 0, resourceProvider) {
            @Override
            protected void onSearchProgressChanged() {
                if (!searchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
            }
        };
        searchListViewAdapter.includeSearch = false;
        int inviteViaLink;
        if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            inviteViaLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) ? 1 : 0;
        } else if (channelId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(channelId);
            inviteViaLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE) && !ChatObject.isPublic(chat) ? 2 : 0;
        } else {
            inviteViaLink = 0;
        }
        listViewAdapter = new ContactsAdapter(context, this, onlyUsers ? 1 : 0, needPhonebook, ignoreUsers, selectedContacts, inviteViaLink) {
            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                if (listView != null && listView.getAdapter() == this) {
                    int count = super.getItemCount();
                    if (needPhonebook) {
                        //  emptyView.setVisibility(count == 2 ? View.VISIBLE : View.GONE);
                        listView.setFastScrollVisible(count != 2);
                    } else {
                        //emptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                        listView.setFastScrollVisible(count != 0);
                    }
                }
            }

            @Override
            public int getSectionCount() {
                final int result = super.getSectionCount();
                checkUi_floatingButtonVisible();
                checkUi_sortItem();
                checkUi_searchFieldHint();
                return result;
            }
        };
        listViewAdapter.setSortType(sortItem != null ? (sortByName ? 1 : 2) : 0, false);
        listViewAdapter.setSectionLetterTextSize(17);
        listViewAdapter.setDisableSections(disableSections);
        listViewAdapter.includeSearch = false;

        fragmentView = contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureChildWithMargins(simpleActionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                ((MarginLayoutParams) emptyView.getLayoutParams()).topMargin = simpleActionBar.getMeasuredHeight();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                checkUi_emptyView();
                checkUi_sortItem();
                checkUi_floatingButtonPosition();
            }
        };
        listView.setSections();
        ThemeBind.backgroundColor(contentView, Theme.key_windowBackgroundGray, getResourceProvider());

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.PROFILE_SEARCH_CELL);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);
        emptyView.title.setText(getString(R.string.NoResult));
        emptyView.subtitle.setText(getString(R.string.SearchEmptyViewFilteredSubtitle2));
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 12, 12, 12, 0));

        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setDurations(150);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(defaultItemAnimator);
        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
        listView.setVerticalScrollBarEnabled(false);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter);
        listView.setClipToPadding(false);
        scrollHelper = new RecyclerAnimationScrollHelper(listView, layoutManager);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (listView.getAdapter() == searchListViewAdapter) {
                if (searchListViewAdapter.includeSearch) {
                    if (position == 0) return;
                    position--;
                }
                Object object = searchListViewAdapter.getItem(position);

                if (!selectedContacts.isEmpty() && view instanceof ProfileSearchCell) {
                    ProfileSearchCell cell = (ProfileSearchCell) view;
                    if (cell.getUser() != null && cell.getUser().contact) {
                        showOrUpdateActionMode(cell);
                    }

                    return;
                }

                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (searchListViewAdapter.isGlobalSearch(position)) {
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        getMessagesController().putUsers(users, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    }
                    if (returnAsResult) {
                        if (ignoreUsers != null && ignoreUsers.indexOfKey(user.id) >= 0) {
                            return;
                        }
                        didSelectResult(user, true, null);
                    } else {
                        if (createSecretChat) {
                            if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                return;
                            }
                            creatingChat = true;
                            SecretChatHelper.getInstance(currentAccount).startSecretChat(getParentActivity(), user);
                        } else {
                            Bundle args = new Bundle();
                            args.putLong("user_id", user.id);
                            if (getMessagesController().checkCanOpenChat(args, ContactsActivity.this)) {
                                presentFragment(new ChatActivity(args), needFinishFragment);
                            }
                        }
                    }
                } else if (object instanceof String) {
                    String str = (String) object;
                    if (!str.equals("section")) {
                        if (MessagesController.getInstance(currentAccount).isFrozen()) {
                            AccountFrozenAlert.show(currentAccount);
                            return;
                        }
                        NewContactBottomSheet activity = new NewContactBottomSheet(ContactsActivity.this, getContext());
                        activity.setInitialPhoneNumber(str, true);
                        activity.show();
                    }
                } else if (object instanceof ContactsController.Contact) {
                    ContactsController.Contact contact = (ContactsController.Contact) object;
                    AlertsCreator.createContactInviteDialog(ContactsActivity.this, contact.first_name, contact.last_name, contact.phones.get(0));
                }
            } else {
                if (listViewAdapter.includeSearch) {
                    if (position == 0) return;
                    position--;
                }
                int section = listViewAdapter.getSectionForPosition(position);
                int row = listViewAdapter.getPositionInSectionForPosition(position);

                if (row < 0 || section < 0) {
                    return;
                }

                //if (view instanceof InviteUserCell) {
                //    InviteUserCell cell = (InviteUserCell) view;
                //    ContactsController.Contact contact = cell.getContact();
                //    AlertsCreator.createContactInviteDialog(ContactsActivity.this, contact.first_name, contact.last_name, contact.phones.get(0));
                //    return;
                //}

                if (view instanceof ViewGroup && ((ViewGroup) view).getChildAt(0) instanceof ContactsEmptyView) {
                    if (floatingButton != null) {
                        floatingButton.performClick();
                    }
                    return;
                }

                if (!selectedContacts.isEmpty() && view instanceof UserCell) {
                    UserCell userCell = (UserCell) view;
                    showOrUpdateActionMode(userCell);
                    return;
                }

//                if (listViewAdapter.hasStories && section == 1) {
//                    if (!(view instanceof UserCell)) {
//                        return;
//                    }
//                    UserCell userCell = (UserCell) view;
//                    long dialogId = userCell.getDialogId();
//                    getOrCreateStoryViewer().open(getContext(), dialogId, StoriesListPlaceProvider.of(listView));
//                    return;
//                } else if (listViewAdapter.hasStories && section > 1) {
//                    section--;
//                }
                if ((!onlyUsers || inviteViaLink != 0) && section == 0) {
                    if (needPhonebook) {
                        if (row == 0) {
                            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                                AccountFrozenAlert.show(currentAccount);
                                return;
                            }
                            presentFragment(new InviteContactsActivity());
                        } else if (row == 1) {
                            presentFragment(new CallLogActivity());
                        }
                    } else if (inviteViaLink != 0) {
                        if (row == 0) {
                            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                                AccountFrozenAlert.show(currentAccount);
                                return;
                            }
                            presentFragment(new GroupInviteActivity(chatId != 0 ? chatId : channelId));
                        }
                    } else {
                        if (row == 0) {
                            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                                AccountFrozenAlert.show(currentAccount);
                                return;
                            }
                            Bundle args = new Bundle();
                            presentFragment(new GroupCreateActivity(args), false);
                        } else if (row == 1) {
                            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                                AccountFrozenAlert.show(currentAccount);
                                return;
                            }
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                                Bundle args = new Bundle();
                                args.putInt("step", 0);
                                presentFragment(new ChannelCreateActivity(args));
                            } else {
                                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                                preferences.edit().putBoolean("channel_intro", true).commit();
                            }
                        }
                    }
                } else {
                    section = listViewAdapter.getSectionForPosition(position);
                    row = listViewAdapter.getPositionInSectionForPosition(position);
                    Object item1 = listViewAdapter.getItem(section, row);

                    if (item1 instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) item1;
                        if (returnAsResult) {
                            if (ignoreUsers != null && ignoreUsers.indexOfKey(user.id) >= 0) {
                                return;
                            }
                            didSelectResult(user, true, null);
                        } else {
                            if (createSecretChat) {
                                creatingChat = true;
                                SecretChatHelper.getInstance(currentAccount).startSecretChat(getParentActivity(), user);
                            } else {
                                Bundle args = new Bundle();
                                args.putLong("user_id", user.id);
                                if (getMessagesController().checkCanOpenChat(args, ContactsActivity.this)) {
                                    presentFragment(new ChatActivity(args), needFinishFragment);
                                }
                            }
                        }
                    } else if (item1 instanceof ContactsController.Contact) {
                        ContactsController.Contact contact = (ContactsController.Contact) item1;
                        String usePhone = null;
                        if (!contact.phones.isEmpty()) {
                            usePhone = contact.phones.get(0);
                        }
                        if (usePhone == null || getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(getString(R.string.InviteUser));
                        builder.setTitle(getString(R.string.AppName));
                        final String arg1 = usePhone;
                        builder.setPositiveButton(getString(R.string.OK), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null));
                                intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(1));
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                        builder.setNegativeButton(getString(R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (listView.getAdapter() == listViewAdapter) {
                int section = listViewAdapter.getSectionForPosition(position);
                int row = listViewAdapter.getPositionInSectionForPosition(position);
                if (Bulletin.getVisibleBulletin() != null) {
                    Bulletin.getVisibleBulletin().hide();
                }
                if (row < 0 || section < 0) {
                    return false;
                }
//                if (listViewAdapter.hasStories && section == 1 && view instanceof UserCell) {
//                    UserCell userCell = (UserCell) view;
//                    long dialogId = userCell.getDialogId();
//                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
//                    final String key = NotificationsController.getSharedPrefKey(dialogId, 0);
//                    boolean muted = !NotificationsCustomSettingsActivity.areStoriesNotMuted(currentAccount, dialogId);
//                    ItemOptions filterOptions = ItemOptions.makeOptions(ContactsActivity.this, view)
//                            //.setViewAdditionalOffsets(0, dp(8), 0, 0)
//                            .setScrimViewBackground(Theme.createRoundRectDrawable(0, 0, getThemedColor(Theme.key_windowBackgroundWhite)))
//                            .add(R.drawable.msg_discussion, getString(R.string.SendMessage), () -> presentFragment(ChatActivity.of(dialogId)))
//                            .add(R.drawable.msg_openprofile, getString(R.string.OpenProfile), () -> presentFragment(ProfileActivity.of(dialogId)))
//                            .addIf(!muted, R.drawable.msg_mute, getString(R.string.NotificationsStoryMute), () -> {
//                                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, false).apply();
//                                getNotificationsController().updateServerNotificationsSettings(dialogId, 0);
//                                String name = user == null ? "" : user.first_name.trim();
//                                int index = name.indexOf(" ");
//                                if (index > 0) {
//                                   name = name.substring(0, index);
//                               }
//                                BulletinFactory.of(ContactsActivity.this).createUsersBulletin(Arrays.asList(user), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryMutedHint", R.string.NotificationsStoryMutedHint, name))).show();
//                            })
//                            .addIf(muted, R.drawable.msg_unmute, getString(R.string.NotificationsStoryUnmute), () -> {
//                                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, true).apply();
//                                getNotificationsController().updateServerNotificationsSettings(dialogId, 0);
//                                String name = user == null ? "" : user.first_name.trim();
//                                int index = name.indexOf(" ");
//                                if (index > 0) {
//                                    name = name.substring(0, index);
//                                }
//                                BulletinFactory.of(ContactsActivity.this).createUsersBulletin(Arrays.asList(user), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryUnmutedHint", R.string.NotificationsStoryUnmutedHint, name))).show();
//                            });
//                    // if (user.stories_hidden) {
//                    filterOptions.add(R.drawable.msg_viewintopic, getString(R.string.ShowInChats), () -> {
//                        // listViewAdapter.removeStory(dialogId);
//                        getMessagesController().getStoriesController().toggleHidden(dialogId, false, false, true);
//                        BulletinFactory.UndoObject undoObject = new BulletinFactory.UndoObject();
//                        undoObject.onUndo = () -> getMessagesController().getStoriesController().toggleHidden(dialogId, true, false, true);
//                        undoObject.onAction = () -> getMessagesController().getStoriesController().toggleHidden(dialogId, false, true, true);
//                        BulletinFactory.global().createUsersBulletin(
//                            Arrays.asList(user),
//                            AndroidUtilities.replaceTags(LocaleController.formatString("StoriesMovedToDialogs", R.string.StoriesMovedToDialogs, ContactsController.formatName(user.first_name, null, 20))),
//                            null,
//                            undoObject
//                        ).show();
//
//                    });
//                    } else {
//                        filterOptions.add(R.drawable.msg_cancel, LocaleController.getString(R.string.Hide), () -> {
//                            BulletinFactory.global().createUndoBulletin(
//                                    AndroidUtilities.replaceTags(LocaleController.formatString("StoriesMovedToContacts", R.string.StoriesMovedToContacts, user.first_name)),
//                                    () -> {
//                                        //undo
//                                        getMessagesController().getStoriesController().toggleHidden(dialogId, false, false, true);
//                                    }, () -> {
//                                        //action
//                                        getMessagesController().getStoriesController().toggleHidden(dialogId, true, true, true);
//                                    }).show();
//                        });
//                    }
//
//                    filterOptions.setGravity(Gravity.RIGHT)
//                            .show();
//                    return true;
//                }
            }

            if (!returnAsResult && !createSecretChat && view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                showOrUpdateActionMode(cell);
                return true;
            }

            if (!returnAsResult && !createSecretChat && view instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) view;
                if (cell.getUser() != null && cell.getUser().contact) {
                    showOrUpdateActionMode(cell);
                }
                return true;
            }
            return false;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean scrollUpdated;
            private boolean scrollingManually;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    scrollingManually = true;
                } else {
                    scrollingManually = false;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (floatingButton != null && !searching) {
                    boolean goingDown = dy > 0;
                    if (dy != 0 && scrollUpdated && (goingDown || scrollingManually)) {
                        floatingButtonVisibleByScroll = !goingDown;
                        checkUi_floatingButtonVisible();
                    }
                    scrollUpdated = true;
                }
            }
        });

        if (!createSecretChat && !returnAsResult) {
            floatingButton = new FragmentFloatingButton(context, resourceProvider);
            contentView.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
            floatingButton.setOnClickListener(v -> {
                if (MessagesController.getInstance(currentAccount).isFrozen()) {
                    AccountFrozenAlert.show(currentAccount);
                    return;
                }
                new NewContactBottomSheet(ContactsActivity.this, getContext()).show();
            });

            floatingButton.setAnimation(R.raw.write_contacts_fab_icon, 44);
            floatingButton.imageView.getAnimatedDrawable().setCurrentFrame(floatingButton.imageView.getAnimatedDrawable().getFramesCount() - 1);
            floatingButton.setContentDescription(getString(R.string.CreateNewContact));
        }

        if (initialSearchString != null) {
            simpleActionBar.openSearchField(initialSearchString, false);
            initialSearchString = null;
        }

        checkUi_searchFieldHint();

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public void onBottomOffsetChange(float offset) {
                additionalFloatingTranslation = Math.max(0, offset - navigationBarHeight - additionFloatingButtonOffset);
                checkUi_floatingButtonPosition();
            }

            @Override
            public int getBottomOffset(int tag) {
                return navigationBarHeight + additionFloatingButtonOffset;
            }
        });
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.getRootAnimatedInsetsListener().subscribeToWindowInsetsAnimation(this);
        }
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }


    public boolean addOrRemoveSelectedContact(UserCell cell) {
        long dialogId = cell.getDialogId();
        if (selectedContacts.indexOfKey(dialogId) >= 0) {
            selectedContacts.remove(dialogId);
            cell.setChecked(false, true);
        } else if (cell.getCurrentObject() instanceof TLRPC.User) {
            selectedContacts.put(dialogId, (TLRPC.User) cell.getCurrentObject());
            cell.setChecked(true, true);
            return true;
        }

        return false;
    }

    public boolean addOrRemoveSelectedContact(ProfileSearchCell cell) {
        long dialogId = cell.getDialogId();
        if (selectedContacts.indexOfKey(dialogId) >= 0) {
            selectedContacts.remove(dialogId);
            cell.setChecked(false, true);
        } else {
            if (cell.getUser() != null) {
                selectedContacts.put(dialogId, cell.getUser());
                cell.setChecked(true, true);
                return true;
            }
        }
        return false;
    }

    private void showOrUpdateActionMode(Object cell) {
        boolean checked;
        if (cell instanceof UserCell) {
            checked = addOrRemoveSelectedContact((UserCell) cell);
        } else if (cell instanceof ProfileSearchCell) {
            checked = addOrRemoveSelectedContact((ProfileSearchCell) cell);
        } else {
            return;
        }
        boolean updateAnimated = false;

        if (simpleActionBar.isActionModeShowed()) {
            if (selectedContacts.isEmpty()) {
                hideActionMode();
                return;
            }
            updateAnimated = true;
        } else if (checked) {
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            simpleActionBar.setActionModeCount(selectedContacts.size(), false);
            simpleActionBar.showActionMode();
        }

        simpleActionBar.setActionModeCount(selectedContacts.size(), updateAnimated);
    }

    private void hideActionMode() {
        simpleActionBar.hideActionMode();
        int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = listView.getChildAt(i);
            if (view instanceof UserCell) {
                UserCell cell = (UserCell) view;
                if (selectedContacts.indexOfKey(cell.getDialogId()) >= 0) {
                    cell.setChecked(false, true);
                }
            } else if (view instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) view;
                if (selectedContacts.indexOfKey(cell.getDialogId()) >= 0) {
                    cell.setChecked(false, true);
                }
            }
        }
        selectedContacts.clear();
    }

    private ViewPropertyAnimator sortAnimator;

    /** The list fades out, changes the sorting and fades in; the section letters then appear. */
    private void toggleSort() {
        SharedConfig.toggleSortContactsByName();
        sortByName = SharedConfig.sortContactsByName;
        if (sortItem != null) {
            AndroidUtilities.updateImageViewImageAnimated(sortItem.getIconView(), sortByName ? R.drawable.msg_contacts_time : R.drawable.msg_contacts_name);
        }
        if (sortAnimator != null) {
            sortAnimator.cancel();
        }
        animatorSectionLetters.cancel();
        sortAnimator = listView.animate().alpha(0f).setDuration(120).setInterpolator(CubicBezierInterpolator.EASE_OUT).withEndAction(() -> {
            animatorSectionLetters.forceFactor(sortByName ? 0f : 1f);
            listViewAdapter.setSectionLettersProgress(animatorSectionLetters.getFactor());
            listViewAdapter.setSortType(sortByName ? ContactsAdapter.SORT_TYPE_BY_NAME : ContactsAdapter.SORT_TYPE_BY_TIME, false);
            listView.scrollToPosition(0);
            sortAnimator = listView.animate().alpha(1f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                sortAnimator = null;
            });
            sortAnimator.start();
            if (sortByName) {
                animatorSectionLetters.setStartDelay(80);
                animatorSectionLetters.animateTo(1f);
            }
        });
        sortAnimator.start();
    }

    private void performSelectedContactsDelete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        if (selectedContacts.size() == 1) {
            builder.setTitle(getString(R.string.DeleteContactTitle));
            builder.setMessage(getString(R.string.DeleteContactSubtitle));
        } else {
            builder.setTitle(LocaleController.formatPluralString("DeleteContactsTitle", selectedContacts.size()));
            builder.setMessage(getString(R.string.DeleteContactsSubtitle));
        }
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            ArrayList<TLRPC.User> contacts = new ArrayList<>(selectedContacts.size());
            for (int i = 0; i < selectedContacts.size(); i++) {
                long key = selectedContacts.keyAt(i);
                TLRPC.User contact = selectedContacts.get(key);
                contacts.add(contact);
            }

            getContactsController().deleteContactsUndoable(getContext(), ContactsActivity.this, contacts);

            hideActionMode();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.redPositive();
    }

    private void didSelectResult(final TLRPC.User user, boolean useAlert, String param) {
        if (useAlert && selectAlertString != null) {
            if (getParentActivity() == null) {
                return;
            }
            if (user.bot) {
                if (user.bot_nochats) {
                    try {
                        BulletinFactory.of(this).createErrorBulletin(getString(R.string.BotCantJoinGroups)).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }
                if (channelId != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(channelId);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    if (ChatObject.canAddAdmins(chat)) {
                        builder.setTitle(getString(R.string.AddBotAdminAlert));
                        builder.setMessage(getString(R.string.AddBotAsAdmin));
                        builder.setPositiveButton(getString(R.string.AddAsAdmin), (dialogInterface, i) -> {
                            if (delegate != null) {
                                delegate.didSelectContact(user, param, this);
                                delegate = null;
                            }
                        });
                        builder.setNegativeButton(getString(R.string.Cancel), null);
                    } else {
                        builder.setMessage(getString(R.string.CantAddBotAsAdmin));
                        builder.setPositiveButton(getString(R.string.OK), null);
                    }
                    showDialog(builder.create());
                    return;
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString(R.string.AppName));
            String message = LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user));
            EditTextBoldCursor editText = null;
            if (!user.bot && needForwardCount) {
                message = String.format("%s\n\n%s", message, getString(R.string.AddToTheGroupForwardCount));
                editText = new EditTextBoldCursor(getParentActivity());
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                editText.setText("50");
                ThemeBind.textColor(editText, Theme.key_dialogTextBlack, getResourceProvider());
                editText.setGravity(Gravity.CENTER);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                editText.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
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
                            if (!str.isEmpty()) {
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
                            FileLog.e(e);
                        }
                    }

                });
                builder.setView(editText);
            }
            builder.setMessage(message);
            final EditText finalEditText = editText;
            builder.setPositiveButton(getString(R.string.OK), (dialogInterface, i) -> didSelectResult(user, false, finalEditText != null ? finalEditText.getText().toString() : "0"));
            builder.setNegativeButton(getString(R.string.Cancel), null);
            showDialog(builder.create());
            if (editText != null) {
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
                if (layoutParams != null) {
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                    }
                    layoutParams.rightMargin = layoutParams.leftMargin = dp(24);
                    layoutParams.height = dp(36);
                    editText.setLayoutParams(layoutParams);
                }
                editText.setSelection(editText.getText().length());
            }
        } else {
            if (delegate != null) {
                delegate.didSelectContact(user, param, this);
                if (resetDelegate) {
                    delegate = null;
                }
            }
            if (needFinishFragment) {
                finishFragment();
            }
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (simpleActionBar.isActionModeShowed()) {
            if (invoked) hideActionMode();
            return false;
        } else if (simpleActionBar.isSearchFieldVisible()) {
            // the tab host does not let ActionBarLayout close the search of a tab
            if (invoked) simpleActionBar.closeSearchField();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        if (checkPermission && Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkPermission = false;
                if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED/* ||
                    activity.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED*/) {
                    if (activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)/* ||
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)*/) {
                        AlertDialog.Builder builder = AlertsCreator.createContactsPermissionDialog(activity, param -> {
                            askAboutContacts = param != 0;
                            if (param == 0) {
                                return;
                            }
                            askForPermissons(false);
                        });
                        showDialog(permissionDialog = builder.create());
                    } else {
                        askForPermissons(true);
                    }
                }
            }
        }
    }

    protected RecyclerListView getListView() {
        return listView;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);
        if (permissionDialog != null && dialog == permissionDialog && getParentActivity() != null && askAboutContacts) {
            askForPermissons(false);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void askForPermissons(boolean alert) {
        Activity activity = getParentActivity();
        if (activity == null || !UserConfig.getInstance(currentAccount).syncContacts || activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED/* && activity.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED*/) {
            return;
        }
        if (alert && askAboutContacts) {
            AlertDialog.Builder builder = AlertsCreator.createContactsPermissionDialog(activity, param -> {
                MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts2", false).commit();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.contactsPermissionBadgeCheck);
                askAboutContacts = param != 0;
                if (param == 0) {
                    return;
                }
                askForPermissons(false);
            });
            showDialog(builder.create());
            return;
        }
        permissionRequestTime = SystemClock.elapsedRealtime();
        ArrayList<String> permissons = new ArrayList<>();
        permissons.add(Manifest.permission.READ_CONTACTS);
        permissons.add(Manifest.permission.WRITE_CONTACTS);
        permissons.add(Manifest.permission.GET_ACCOUNTS);
        String[] items = permissons.toArray(new String[0]);
        try {
            activity.requestPermissions(items, 1);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            for (int a = 0; a < permissions.length; a++) {
                if (grantResults.length <= a) {
                    continue;
                }
                if (Manifest.permission.READ_CONTACTS.equals(permissions[a])) {
                    if (grantResults[a] == PackageManager.PERMISSION_GRANTED) {
                        ContactsController.getInstance(currentAccount).forceImportContacts();
                    } else {
                        MessagesController.getGlobalNotificationsSettings().edit()
                            .putBoolean("askAboutContacts", askAboutContacts = false)
                            .putBoolean("askAboutContacts2", false)
                            .apply();
                        if (SystemClock.elapsedRealtime() - permissionRequestTime < 200) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", ApplicationLoader.applicationContext.getPackageName(), null);
                                intent.setData(uri);
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (simpleActionBar != null) {
            simpleActionBar.closeSearchField();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (listViewAdapter != null) {
                if (!sortByName) {
                    listViewAdapter.setSortType(2, true);
                }
                listViewAdapter.notifyDataSetChanged();
            }
            if (searchListViewAdapter != null && listView.getAdapter() == searchListViewAdapter) {
                searchListViewAdapter.searchDialogs(searchQuery);
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0 && !sortByName && listViewAdapter != null) {
                scheduleSort();
            }
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (createSecretChat && creatingChat) {
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) args[0];
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", encryptedChat.id);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                presentFragment(new ChatActivity(args2), false);
            }
        } else if (id == NotificationCenter.closeChats) {
            if (!creatingChat) {
                removeSelfFromStack(true);
            }
        }
    }

    boolean scheduled;
    Runnable sortContactsRunnable = new Runnable() {
        @Override
        public void run() {
            listViewAdapter.sortOnlineContacts();
            scheduled = false;
        }
    };

    private void scheduleSort() {
        if (!scheduled) {
            scheduled = true;
            AndroidUtilities.cancelRunOnUIThread(sortContactsRunnable);
            AndroidUtilities.runOnUIThread(sortContactsRunnable, 5000);
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

    public void setIgnoreUsers(LongSparseArray<TLRPC.User> users) {
        ignoreUsers = users;
    }

    public void setInitialSearchString(String initialSearchString) {
        this.initialSearchString = initialSearchString;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::cellDelegateThemeColors;

        if (!hasMainTabs) {
            themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));




        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));

        if (floatingButton != null) {
            themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
            themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));
        }


        return themeDescriptions;
    }

//    private int lastListScrollState;
//    private boolean canScrollByAnimation;
//    private float lastSearchFieldHeight;
//    private float scrollByAcc;

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SECTION_LETTERS) {
            listViewAdapter.setSectionLettersProgress(factor);
            listView.invalidate();
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (listView != null && listView.getFastScroll() != null && listView.getFastScroll().isPressed()) {
            return false;
        }
        return true;
    }


    /* * */

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private int additionNavigationBarHeight;
    private int additionFloatingButtonOffset;
    private float additionalFloatingTranslation;
    private int navigationBarHeight;
    private int imeInsetAnimatedHeight;

    @Override
    public View getAnimatedInsetsTargetView() {
        return fragmentView;
    }

    @Override
    public void onAnimatedInsetsChanged(View view, WindowInsetsCompat insets) {
        imeInsetAnimatedHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        checkUi_emptyView();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        navigationBarHeight = systemInsets.bottom;
        statusBarInset = systemInsets.top;

        checkUi_listViewPadding();
        checkUi_floatingButtonPosition();
        checkUi_emptyView();

        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        statusBarInset = top;
        checkUi_listViewPadding();
        checkUi_floatingButtonPosition();
        checkUi_emptyView();
    }

    private void checkUi_emptyView() {
        if (emptyView != null) {
            emptyView.setKeyboardHeight(Math.max(navigationBarHeight + additionNavigationBarHeight, imeInsetAnimatedHeight), false);
        }
    }

    private int statusBarInset;

    private void checkUi_listViewPadding() {
        BaseFragmentSimpleGlass.setListViewParams(listView, statusBarInset, navigationBarHeight, ActionBar.getCurrentActionBarHeight(), additionNavigationBarHeight);
    }

    private boolean lastIsEmpty;

    private void checkUi_searchFieldHint() {
        final boolean isEmpty = listViewAdapter != null && listViewAdapter.isEmpty();

        if (lastIsEmpty != isEmpty || !hintSet) {
            searchItem.setSearchFieldHint(getString(isEmpty ? R.string.SearchPeopleByUsername : R.string.SearchContacts));
            lastIsEmpty = isEmpty;
            hintSet = true;
        }
    }

    private boolean hintSet;


    private void checkUi_sortItem() {
        if (sortItem != null) {
            sortItem.setShown(listViewAdapter != null && !listViewAdapter.isEmpty(), true);
        }
    }


    private void checkUi_floatingButtonPosition() {
        if (floatingButton != null) {
            floatingButton.setTranslationY(-navigationBarHeight - additionFloatingButtonOffset - additionalFloatingTranslation);
        }
    }

    private void checkUi_floatingButtonVisible() {
        if (floatingButton != null && listViewAdapter != null) {
            floatingButton.setButtonVisible(floatingButtonVisibleByScroll && !searching && !listViewAdapter.isEmpty(), true);
        }
    }



    /* Glass */

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public GlassSourceRenderNode getGlassSource() {
        return getBaseSimpleGlass().getGlassSource();
    }

    @Override
    public GlassSourceRenderNode getFrostedGlassSource() {
        return getBaseSimpleGlass().getFrostedGlassSource();
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return !hasMainTabs;
    }


    @Override
    public void onParentScrollToTop() {
        if (layoutManager.findFirstVisibleItemPosition() < 15) {
            listView.smoothScrollToPosition(0);
        } else {
            scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(0, 0, false, true);
        }
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            cellDelegateThemeColors();
            applyThemeColorsHook();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void applyThemeColorsHook() {
        try {
            if (listView != null) {
                listView.updateFastScrollColors();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cellDelegateThemeColors() {
        // the simple header is a Theme.Colorable, recolored by ThemeBind
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_chats_secretName);
        keys.add(Theme.key_chats_name);
        keys.add(Theme.key_windowBackgroundWhiteBlueText3);
        keys.add(Theme.key_windowBackgroundWhiteGrayText3);
        keys.add(Theme.key_chats_verifiedBackground);
        keys.add(Theme.key_chats_verifiedCheck);
        keys.add(Theme.key_graySection);
        keys.add(Theme.key_graySectionText);
        keys.add(Theme.key_windowBackgroundWhiteGrayIcon);
        keys.add(Theme.key_windowBackgroundWhiteBlackText);
        keys.add(Theme.key_avatar_backgroundPink);
        keys.add(Theme.key_avatar_backgroundBlue);
        keys.add(Theme.key_avatar_backgroundCyan);
        keys.add(Theme.key_avatar_backgroundGreen);
        keys.add(Theme.key_avatar_backgroundViolet);
        keys.add(Theme.key_avatar_backgroundOrange);
        keys.add(Theme.key_avatar_backgroundRed);
        keys.add(Theme.key_avatar_text);
        keys.add(Theme.key_windowBackgroundWhiteGrayText);
        keys.add(Theme.key_divider);
        keys.add(Theme.key_windowBackgroundWhiteGrayText4);
        keys.add(Theme.key_listSelector);
        keys.add(Theme.key_actionBarDefaultSelector);
        keys.add(Theme.key_actionBarDefaultTitle);
        keys.add(Theme.key_actionBarDefaultIcon);
        keys.add(Theme.key_actionBarDefault);
        keys.add(Theme.key_windowBackgroundWhite);
    }
}
