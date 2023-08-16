/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.collection.LongSparseArray;
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
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
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
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.ArrayList;
import java.util.Arrays;

public class ContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ContactsAdapter listViewAdapter;
    private StickerEmptyView emptyView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private SearchAdapter searchListViewAdapter;

    private ActionBarMenuItem sortItem;
    private boolean sortByName;

    private RLottieImageView floatingButton;
    private FrameLayout floatingButtonContainer;
    private AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();
    private int prevPosition;
    private int prevTop;
    private boolean scrollUpdated;
    private boolean floatingHidden;

    private boolean hasGps;
    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean needPhonebook;
    private boolean destroyAfterSelect;
    private boolean returnAsResult;
    private boolean createSecretChat;
    private boolean createSecretChatSkipAnimation;
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

    private LongSparseArray<TLRPC.User> selectedContacts = new LongSparseArray<>();

    private NumberTextView selectedContactsCountTextView;

    private ActionBarMenuItem deleteItem;

    private BackDrawable backDrawable;

    private String searchQuery;

    private boolean checkPermission = true;
    private long permissionRequestTime;

    private AnimatorSet bounceIconAnimator;
    private int animationIndex = -1;

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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        checkPermission = UserConfig.getInstance(currentAccount).syncContacts;
        if (arguments != null) {
            onlyUsers = arguments.getBoolean("onlyUsers", false);
            destroyAfterSelect = arguments.getBoolean("destroyAfterSelect", false);
            returnAsResult = arguments.getBoolean("returnAsResult", false);
            createSecretChat = arguments.getBoolean("createSecretChat", false);
            createSecretChatSkipAnimation = arguments.getBoolean("createSecretChatSkipAnimation", false);
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
        } else {
            needPhonebook = true;
        }

        if (!createSecretChat && !returnAsResult) {
            sortByName = SharedConfig.sortContactsByName;
        }

        getContactsController().checkInviteText();
        getContactsController().reloadContactsStatusesMaybe(false);
        MessagesController.getInstance(currentAccount).getStoriesController().loadHiddenStories();


        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        delegate = null;
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        getNotificationCenter().onAnimationFinish(animationIndex);
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        super.onTransitionAnimationProgress(isOpen, progress);
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

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

        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));

        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);
        actionMode.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionMode.drawBlur = false;

        selectedContactsCountTextView = new NumberTextView(actionMode.getContext());
        selectedContactsCountTextView.setTextSize(18);
        selectedContactsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedContactsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedContactsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedContactsCountTextView.setOnTouchListener((v, event) -> true);

        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode();
                    } else {
                        finishFragment();
                    }
                } else if (id == delete) {
                    performSelectedContactsDelete();
                } else if (id == sort_button) {
                    SharedConfig.toggleSortContactsByName();
                    sortByName = SharedConfig.sortContactsByName;
                    listViewAdapter.setSortType(sortByName ? 1 : 2, false);
                    sortItem.setIcon(sortByName ? R.drawable.msg_contacts_time : R.drawable.msg_contacts_name);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem item = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (floatingButtonContainer != null) {
                    floatingButtonContainer.setVisibility(View.GONE);
                }
                if (sortItem != null) {
                    sortItem.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSearchCollapse() {
                searchListViewAdapter.searchDialogs(null);
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
                listViewAdapter.notifyDataSetChanged();
                listView.setFastScrollVisible(true);
                listView.setVerticalScrollBarEnabled(false);
                listView.getFastScroll().topOffset = AndroidUtilities.dp(90);
                // emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                if (floatingButtonContainer != null) {
                    floatingButtonContainer.setVisibility(View.VISIBLE);
                    floatingHidden = true;
                    floatingButtonContainer.setTranslationY(AndroidUtilities.dp(100));
                    hideFloatingButton(false);
                }
                if (sortItem != null) {
                    sortItem.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                searchQuery = text;
                if (text.length() != 0) {
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
                    if (listView != null) {
                        listView.setAdapter(listViewAdapter);
                        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
                    }
                }
            }
        });
        item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        item.setContentDescription(LocaleController.getString("Search", R.string.Search));
        if (!createSecretChat && !returnAsResult) {
            sortItem = menu.addItem(sort_button, sortByName ? R.drawable.msg_contacts_time : R.drawable.msg_contacts_name);
            sortItem.setContentDescription(LocaleController.getString("AccDescrContactSorting", R.string.AccDescrContactSorting));
        }

        searchListViewAdapter = new SearchAdapter(context, ignoreUsers, selectedContacts, allowUsernameSearch, false, false, allowBots, allowSelf, true, 0) {
            @Override
            protected void onSearchProgressChanged() {
                if (!searchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
                showItemsAnimated();
            }

        };
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
        try {
            hasGps = ApplicationLoader.applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        } catch (Throwable e) {
            hasGps = false;
        }
        listViewAdapter = new ContactsAdapter(context, this, onlyUsers ? 1 : 0, needPhonebook, ignoreUsers, selectedContacts, inviteViaLink, hasGps) {
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
        };
        listViewAdapter.setSortType(sortItem != null ? (sortByName ? 1 : 2) : 0, false);
        listViewAdapter.setDisableSections(disableSections);

        fragmentView = new FrameLayout(context) {

            Paint actionBarPaint = new Paint();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                actionBarPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));
                float actionBarBottom = actionBar.getMeasuredHeight();
                canvas.drawRect(0, 0, getMeasuredWidth(), actionBar.getMeasuredHeight(), actionBarPaint);
                parentLayout.drawHeaderShadow(canvas, (int) actionBarBottom);
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                ((MarginLayoutParams) emptyView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
                ((MarginLayoutParams) listView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (listView.getAdapter() == listViewAdapter) {
                    if (emptyView.getVisibility() == VISIBLE) {
                        emptyView.setTranslationY(AndroidUtilities.dp(74));
                    }
                } else {
                    emptyView.setTranslationY(AndroidUtilities.dp(0));
                }
            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);
        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context) {
            @Override
            public void setPadding(int left, int top, int right, int bottom) {
                super.setPadding(left, top, right, bottom);
                if (emptyView != null) {
                    emptyView.setPadding(left, top, right, bottom);
                }
            }
        };
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
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (listView.getAdapter() == searchListViewAdapter) {
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
                        NewContactBottomSheet activity = new NewContactBottomSheet(ContactsActivity.this, getContext());
                        activity.setInitialPhoneNumber(str, true);
                        activity.show();
                    }
                } else if (object instanceof ContactsController.Contact) {
                    ContactsController.Contact contact = (ContactsController.Contact) object;
                    AlertsCreator.createContactInviteDialog(ContactsActivity.this, contact.first_name, contact.last_name, contact.phones.get(0));
                }
            } else {
                int section = listViewAdapter.getSectionForPosition(position);
                int row = listViewAdapter.getPositionInSectionForPosition(position);

                if (row < 0 || section < 0) {
                    return;
                }

                if (!selectedContacts.isEmpty() && view instanceof UserCell) {
                    UserCell userCell = (UserCell) view;
                    showOrUpdateActionMode(userCell);
                    return;
                }

                if (listViewAdapter.hasStories && section == 1) {
                    if (!(view instanceof UserCell)) {
                        return;
                    }
                    UserCell userCell = (UserCell) view;
                    long dialogId = userCell.getDialogId();
                    getOrCreateStoryViewer().open(getContext(), dialogId, StoriesListPlaceProvider.of(listView));
                    return;
                } else if (listViewAdapter.hasStories && section > 1) {
                    section--;
                }
                if ((!onlyUsers || inviteViaLink != 0) && section == 0) {
                    if (needPhonebook) {
                        if (row == 0) {
                            presentFragment(new InviteContactsActivity());
                        } else if (row == 1 && hasGps) {
                            if (Build.VERSION.SDK_INT >= 23) {
                                Activity activity = getParentActivity();
                                if (activity != null) {
                                    if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_NEARBY_LOCATION_ACCESS));
                                        return;
                                    }
                                }
                            }
                            boolean enabled = true;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
                                enabled = lm.isLocationEnabled();
                            } else if (Build.VERSION.SDK_INT >= 19) {
                                try {
                                    int mode = Settings.Secure.getInt(ApplicationLoader.applicationContext.getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
                                    enabled = (mode != Settings.Secure.LOCATION_MODE_OFF);
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                }
                            }
                            if (!enabled) {
                                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_NEARBY_LOCATION_ENABLED));
                                return;
                            }
                            presentFragment(new PeopleNearbyActivity());
                        }
                    } else if (inviteViaLink != 0) {
                        if (row == 0) {
                            presentFragment(new GroupInviteActivity(chatId != 0 ? chatId : channelId));
                        }
                    } else {
                        if (row == 0) {
                            Bundle args = new Bundle();
                            presentFragment(new GroupCreateActivity(args), false);
                        } else if (row == 1) {
                            Bundle args = new Bundle();
                            args.putBoolean("onlyUsers", true);
                            args.putBoolean("destroyAfterSelect", true);
                            args.putBoolean("createSecretChat", true);
                            args.putBoolean("allowBots", false);
                            args.putBoolean("allowSelf", false);
                            presentFragment(new ContactsActivity(args), false);
                        } else if (row == 2) {
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
                        builder.setMessage(LocaleController.getString("InviteUser", R.string.InviteUser));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        final String arg1 = usePhone;
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", arg1, null));
                                intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(1));
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (listView.getAdapter() == listViewAdapter) {
                    int section = listViewAdapter.getSectionForPosition(position);
                    int row = listViewAdapter.getPositionInSectionForPosition(position);
                    if (Bulletin.getVisibleBulletin() != null) {
                        Bulletin.getVisibleBulletin().hide();
                    }
                    if (row < 0 || section < 0) {
                        return false;
                    }
                    if (listViewAdapter.hasStories && section == 1 && view instanceof UserCell) {
                        UserCell userCell = (UserCell) view;
                        long dialogId = userCell.getDialogId();
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        final String key = NotificationsController.getSharedPrefKey(dialogId, 0);
                        boolean muted = !NotificationsCustomSettingsActivity.areStoriesNotMuted(currentAccount, dialogId);
                        ItemOptions filterOptions = ItemOptions.makeOptions(ContactsActivity.this, view)
                                //.setViewAdditionalOffsets(0, AndroidUtilities.dp(8), 0, 0)
                                .setScrimViewBackground(Theme.createRoundRectDrawable(0, 0, Theme.getColor(Theme.key_windowBackgroundWhite)))
                                .add(R.drawable.msg_discussion, LocaleController.getString("SendMessage", R.string.SendMessage), () -> {
                                    presentFragment(ChatActivity.of(dialogId));
                                })
                                .add(R.drawable.msg_openprofile, LocaleController.getString("OpenProfile", R.string.OpenProfile), () -> {
                                    presentFragment(ProfileActivity.of(dialogId));
                                })
                                .addIf(!muted, R.drawable.msg_mute, LocaleController.getString("NotificationsStoryMute", R.string.NotificationsStoryMute), () -> {
                                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, false).apply();
                                    getNotificationsController().updateServerNotificationsSettings(dialogId, 0);
                                    String name = user == null ? "" : user.first_name.trim();
                                    int index = name.indexOf(" ");
                                    if (index > 0) {
                                        name = name.substring(0, index);
                                    }
                                    BulletinFactory.of(ContactsActivity.this).createUsersBulletin(Arrays.asList(user), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryMutedHint", R.string.NotificationsStoryMutedHint, name))).show();
                                })
                                .addIf(muted, R.drawable.msg_unmute, LocaleController.getString("NotificationsStoryUnmute", R.string.NotificationsStoryUnmute), () -> {
                                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, true).apply();
                                    getNotificationsController().updateServerNotificationsSettings(dialogId, 0);
                                    String name = user == null ? "" : user.first_name.trim();
                                    int index = name.indexOf(" ");
                                    if (index > 0) {
                                        name = name.substring(0, index);
                                    }
                                    BulletinFactory.of(ContactsActivity.this).createUsersBulletin(Arrays.asList(user), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryUnmutedHint", R.string.NotificationsStoryUnmutedHint, name))).show();
                                });
                        // if (user.stories_hidden) {
                        filterOptions.add(R.drawable.msg_viewintopic, LocaleController.getString("ShowInChats", R.string.ShowInChats), () -> {
                            // listViewAdapter.removeStory(dialogId);
                            getMessagesController().getStoriesController().toggleHidden(dialogId, false, false, true);
                            BulletinFactory.UndoObject undoObject = new BulletinFactory.UndoObject();
                            undoObject.onUndo = () -> {
                                getMessagesController().getStoriesController().toggleHidden(dialogId, true, false, true);
                            };
                            undoObject.onAction = () -> {
                                getMessagesController().getStoriesController().toggleHidden(dialogId, false, true, true);
                            };
                            BulletinFactory.global().createUsersBulletin(
                                    Arrays.asList(user),
                                    AndroidUtilities.replaceTags(LocaleController.formatString("StoriesMovedToDialogs", R.string.StoriesMovedToDialogs, ContactsController.formatName(user.first_name, null, 20))),
                                    null,
                                    undoObject
                            ).show();

                        });
//                    } else {
//                        filterOptions.add(R.drawable.msg_cancel, LocaleController.getString("Hide", R.string.Hide), () -> {
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

                        filterOptions.setGravity(Gravity.RIGHT)
                                .show();
                        return true;
                    }
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
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            private boolean scrollingManually;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
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
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (floatingButtonContainer != null && floatingButtonContainer.getVisibility() != View.GONE) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();

                    final View topChild = recyclerView.getChildAt(0);
                    int firstViewTop = 0;
                    if (topChild != null) {
                        firstViewTop = topChild.getTop();
                    }
                    boolean goingDown;
                    boolean changed = true;
                    if (prevPosition == firstVisibleItem) {
                        final int topDelta = prevTop - firstViewTop;
                        goingDown = firstViewTop < prevTop;
                        changed = Math.abs(topDelta) > 1;
                    } else {
                        goingDown = firstVisibleItem > prevPosition;
                    }
                    if (changed && scrollUpdated && (goingDown || scrollingManually)) {
                        hideFloatingButton(goingDown);
                    }
                    prevPosition = firstVisibleItem;
                    prevTop = firstViewTop;
                    scrollUpdated = true;
                }
            }
        });

        if (!createSecretChat && !returnAsResult) {
            floatingButtonContainer = new FrameLayout(context);
            frameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 20, (Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 4 : 0, 0, LocaleController.isRTL ? 0 : 4, 0));
            floatingButtonContainer.setOnClickListener(v -> {
                AndroidUtilities.requestAdjustNothing(getParentActivity(), getClassGuid());
                new NewContactBottomSheet(ContactsActivity.this, getContext()) {
                    @Override
                    public void dismissInternal() {
                        super.dismissInternal();
                        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                    }
                }.show();
            });

            floatingButton = new RLottieImageView(context);
            floatingButton.setScaleType(ImageView.ScaleType.CENTER);
            Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                drawable = combinedDrawable;
            }
            floatingButton.setBackgroundDrawable(drawable);
            floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean configAnimationsEnabled = preferences.getBoolean("view_animations", true);
            if (getMessagesController().storiesEnabled()) {
                floatingButton.setAnimation(configAnimationsEnabled ? R.raw.write_contacts_fab_icon_camera : R.raw.write_contacts_fab_icon_reverse_camera, 56, 56);
            } else {
                floatingButton.setAnimation(configAnimationsEnabled ? R.raw.write_contacts_fab_icon : R.raw.write_contacts_fab_icon_reverse, 52, 52);
            }
            floatingButtonContainer.setContentDescription(LocaleController.getString("CreateNewContact", R.string.CreateNewContact));
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                floatingButton.setStateListAnimator(animator);
                floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            floatingButtonContainer.addView(floatingButton, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.LEFT | Gravity.TOP, 10, 6, 10, 0));
        }

        if (initialSearchString != null) {
            actionBar.openSearchField(initialSearchString, false);
            initialSearchString = null;
        }

        ((FrameLayout) fragmentView).addView(actionBar);

        listViewAdapter.setStories(getMessagesController().storiesController.getHiddenList(), false);

        return fragmentView;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        actionBar.setBackground(null);
        actionBar.setAddToContainer(false);
        return actionBar;
    }

    public boolean addOrRemoveSelectedContact(UserCell cell) {
        long dialogId = cell.getDialogId();
        if (selectedContacts.indexOfKey(dialogId) >= 0) {
            selectedContacts.remove(dialogId);
            cell.setChecked(false, true);
            return false;
        } else {
            if (cell.getCurrentObject() instanceof TLRPC.User) {
                selectedContacts.put(dialogId, (TLRPC.User) cell.getCurrentObject());
                cell.setChecked(true, true);
                return true;
            }
            return false;
        }
    }

    public boolean addOrRemoveSelectedContact(ProfileSearchCell cell) {
        long dialogId = cell.getDialogId();
        if (selectedContacts.indexOfKey(dialogId) >= 0) {
            selectedContacts.remove(dialogId);
            cell.setChecked(false, true);
            return false;
        } else {
            if (cell.getUser() != null) {
                selectedContacts.put(dialogId, cell.getUser());
                cell.setChecked(true, true);
                return true;
            }
            return false;
        }
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

        if (actionBar.isActionModeShowed()) {
            if (selectedContacts.isEmpty()) {
                hideActionMode();
                return;
            }
            updateAnimated = true;
        } else if (checked) {
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.showActionMode();

            backDrawable.setRotation(1, true);
        }

        selectedContactsCountTextView.setNumber(selectedContacts.size(), updateAnimated);
    }

    private void hideActionMode() {
        actionBar.hideActionMode();
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
        backDrawable.setRotation(0, true);
    }

    private void performSelectedContactsDelete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        if (selectedContacts.size() == 1) {
            builder.setTitle(LocaleController.getString("DeleteContactTitle", R.string.DeleteContactTitle));
            builder.setMessage(LocaleController.getString("DeleteContactSubtitle", R.string.DeleteContactSubtitle));
        } else {
            builder.setTitle(LocaleController.formatPluralString("DeleteContactsTitle", selectedContacts.size()));
            builder.setMessage(LocaleController.getString("DeleteContactsSubtitle", R.string.DeleteContactsSubtitle));
        }
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
            ArrayList<TLRPC.User> contacts = new ArrayList<>(selectedContacts.size());
            for (int i = 0; i < selectedContacts.size(); i++) {
                long key = selectedContacts.keyAt(i);
                TLRPC.User contact = selectedContacts.get(key);
                contacts.add(contact);
            }

            getContactsController().deleteContactsUndoable(getContext(), ContactsActivity.this, contacts);

            hideActionMode();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
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
                        BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("BotCantJoinGroups", R.string.BotCantJoinGroups)).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }
                if (channelId != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(channelId);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    if (ChatObject.canAddAdmins(chat)) {
                        builder.setTitle(LocaleController.getString("AddBotAdminAlert", R.string.AddBotAdminAlert));
                        builder.setMessage(LocaleController.getString("AddBotAsAdmin", R.string.AddBotAsAdmin));
                        builder.setPositiveButton(LocaleController.getString("AddAsAdmin", R.string.AddAsAdmin), (dialogInterface, i) -> {
                            if (delegate != null) {
                                delegate.didSelectContact(user, param, this);
                                delegate = null;
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    } else {
                        builder.setMessage(LocaleController.getString("CantAddBotAsAdmin", R.string.CantAddBotAsAdmin));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    }
                    showDialog(builder.create());
                    return;
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            String message = LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user));
            EditTextBoldCursor editText = null;
            if (!user.bot && needForwardCount) {
                message = String.format("%s\n\n%s", message, LocaleController.getString("AddToTheGroupForwardCount", R.string.AddToTheGroupForwardCount));
                editText = new EditTextBoldCursor(getParentActivity());
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                editText.setText("50");
                editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                editText.setGravity(Gravity.CENTER);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
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
                            FileLog.e(e);
                        }
                    }

                });
                builder.setView(editText);
            }
            builder.setMessage(message);
            final EditText finalEditText = editText;
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> didSelectResult(user, false, finalEditText != null ? finalEditText.getText().toString() : "0"));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
            if (editText != null) {
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
                if (layoutParams != null) {
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                    }
                    layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
                    layoutParams.height = AndroidUtilities.dp(36);
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
    public boolean onBackPressed() {
        if (actionBar.isActionModeShowed()) {
            hideActionMode();
            return false;
        } else {
            return super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (floatingButtonContainer != null) {
            floatingButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    floatingButtonContainer.setTranslationY((floatingHidden ? AndroidUtilities.dp(100) : 0));
                    floatingButtonContainer.setClickable(!floatingHidden);
                    if (floatingButtonContainer != null) {
                        floatingButtonContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
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
                        MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts", askAboutContacts = false).commit();
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
        if (actionBar != null) {
            actionBar.closeSearchField();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesUpdated) {
            if (listViewAdapter != null) {
                listViewAdapter.setStories(getMessagesController().getStoriesController().getHiddenList(), true);
            }
            MessagesController.getInstance(currentAccount).getStoriesController().loadHiddenStories();
        } else if (id == NotificationCenter.contactsDidLoad) {
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

    private void hideFloatingButton(boolean hide) {
        if (floatingHidden == hide) {
            return;
        }
        floatingHidden = hide;
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y, (floatingHidden ? AndroidUtilities.dp(100) : 0)));
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(floatingInterpolator);
        floatingButtonContainer.setClickable(!hide);
        animatorSet.start();
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

    private void showItemsAnimated() {
        int from = layoutManager == null ? 0 : layoutManager.findLastVisibleItemPosition();
        listView.invalidate();
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    if (listView.getChildAdapterPosition(child) <= from) {
                        continue;
                    }
                    child.setAlpha(0);
                    int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                    int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                    a.setStartDelay(delay);
                    a.setDuration(200);
                    animatorSet.playTogether(a);
                }
                animatorSet.start();
                return true;
            }
        });
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (createSecretChatSkipAnimation) {
            return null;
        }
        ValueAnimator valueAnimator = isOpen ? ValueAnimator.ofFloat(1f, 0) : ValueAnimator.ofFloat(0, 1f);
        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        BaseFragment previousFragment = parentLayout.getFragmentStack().size() > 1 ? parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2) : null;
        DialogsActivity dialogsActivity = null;
        if (previousFragment instanceof DialogsActivity) {
            dialogsActivity = (DialogsActivity) previousFragment;
        }
        if (dialogsActivity == null) {
            return null;
        }
        final boolean stories = dialogsActivity.storiesEnabled;
        RLottieImageView previousFab = dialogsActivity.getFloatingButton();
        View previousFabContainer = previousFab.getParent() != null ? (View) previousFab.getParent() : null;
        if (floatingButton != null && (floatingButtonContainer == null || previousFabContainer == null || previousFab.getVisibility() != View.VISIBLE || Math.abs(previousFabContainer.getTranslationY()) > AndroidUtilities.dp(4) || Math.abs(floatingButtonContainer.getTranslationY()) > AndroidUtilities.dp(4))) {
            if (stories) {
                floatingButton.setAnimation(R.raw.write_contacts_fab_icon_camera, 56, 56);
            } else {
                floatingButton.setAnimation(R.raw.write_contacts_fab_icon, 52, 52);
            }
            floatingButton.getAnimatedDrawable().setCurrentFrame(floatingButton.getAnimatedDrawable().getFramesCount() - 1);

            return null;
        }
        previousFabContainer.setVisibility(View.GONE);
        if (isOpen) {
            parent.setAlpha(0f);
        }
        valueAnimator.addUpdateListener(valueAnimator1 -> {
            float v = (float) valueAnimator.getAnimatedValue();
            parent.setTranslationX(AndroidUtilities.dp(48) * v);
            parent.setAlpha(1f - v);
        });
        if (floatingButtonContainer != null) {
            ((ViewGroup) fragmentView).removeView(floatingButtonContainer);
            parentLayout.getOverlayContainerView().addView(floatingButtonContainer);
        }
        valueAnimator.setDuration(150);
        valueAnimator.setInterpolator(new DecelerateInterpolator(1.5f));

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (floatingButtonContainer != null) {
                    ViewGroup viewParent;
                    if (floatingButtonContainer.getParent() instanceof ViewGroup) {
                        viewParent = (ViewGroup) floatingButtonContainer.getParent();
                        viewParent.removeView(floatingButtonContainer);
                    }
                    ((ViewGroup) fragmentView).addView(floatingButtonContainer);

                    previousFabContainer.setVisibility(View.VISIBLE);
                    if (!isOpen) {
                        if (stories) {
                            previousFab.setAnimation(R.raw.write_contacts_fab_icon_reverse_camera, 56, 56);
                        } else {
                            previousFab.setAnimation(R.raw.write_contacts_fab_icon_reverse, 52, 52);
                        }
                        previousFab.getAnimatedDrawable().setCurrentFrame(floatingButton.getAnimatedDrawable().getCurrentFrame());
                        previousFab.playAnimation();
                    }
                }
                callback.run();
            }
        });
        animatorSet.playTogether(valueAnimator);
        AndroidUtilities.runOnUIThread(() -> {
            if (floatingButton == null) {
                return;
            }
            animationIndex = getNotificationCenter().setAnimationInProgress(animationIndex, new int[]{NotificationCenter.diceStickersDidLoad}, false);
            animatorSet.start();
            if (stories) {
                floatingButton.setAnimation(isOpen ? R.raw.write_contacts_fab_icon_camera : R.raw.write_contacts_fab_icon_reverse_camera, 56, 56);
            } else {
                floatingButton.setAnimation(isOpen ? R.raw.write_contacts_fab_icon : R.raw.write_contacts_fab_icon_reverse, 52, 52);
            }
            floatingButton.playAnimation();
            if (bounceIconAnimator != null) {
                bounceIconAnimator.cancel();
            }
            bounceIconAnimator = new AnimatorSet();
            float totalDuration = floatingButton.getAnimatedDrawable().getDuration();
            long delay = 0;
            if (isOpen) {
                for (int i = 0; i < 6; i++) {
                    AnimatorSet set = new AnimatorSet();
                    if (i == 0) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1f, 0.9f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1f, 0.9f)
                        );
                        set.setDuration((long) (6f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    } else if (i == 1) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 0.9f, 1.06f)
                        );
                        set.setDuration((long) (17f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else if (i == 2) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.06f, 0.9f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.06f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1.06f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1.06f, 0.9f)
                        );
                        set.setDuration((long) (10f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else if (i == 3) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.9f, 1.03f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.9f, 1.03f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 0.9f, 1.03f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 0.9f, 1.03f)
                        );
                        set.setDuration((long) (5f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else if (i == 4) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.03f, 0.98f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.03f, 0.98f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1.03f, 0.98f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1.03f, 0.98f)
                        );
                        set.setDuration((long) (5f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.98f, 1f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.98f, 1f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 0.98f, 1f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 0.98f, 1f)
                        );

                        set.setDuration((long) (4f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_IN);
                    }
                    set.setStartDelay(delay);
                    delay += set.getDuration();
                    bounceIconAnimator.playTogether(set);
                }
            } else {
                for (int i = 0; i < 5; i++) {
                    AnimatorSet set = new AnimatorSet();
                    if (i == 0) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1f, 0.9f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1f, 0.9f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1f, 0.9f)
                        );
                        set.setDuration((long) (7f / 36f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    } else if (i == 1) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 0.9f, 1.06f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 0.9f, 1.06f)
                        );
                        set.setDuration((long) (8f / 36f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else if (i == 2) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.06f, 0.92f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.06f, 0.92f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1.06f, 0.92f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1.06f, 0.92f)
                        );
                        set.setDuration((long) (7f / 36f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else if (i == 3) {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.92f, 1.02f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.92f, 1.02f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 0.92f, 1.02f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 0.92f, 1.02f)
                        );
                        set.setDuration((long) (9f / 36f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    } else {
                        set.playTogether(
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.02f, 1f),
                                ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.02f, 1f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_X, 1.02f, 1f),
                                ObjectAnimator.ofFloat(previousFabContainer, View.SCALE_Y, 1.02f, 1f)
                        );
                        set.setDuration((long) (5f / 47f * totalDuration));
                        set.setInterpolator(CubicBezierInterpolator.EASE_IN);
                    }
                    set.setStartDelay(delay);
                    delay += set.getDuration();
                    bounceIconAnimator.playTogether(set);
                }
            }
            bounceIconAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    floatingButton.setScaleX(1f);
                    floatingButton.setScaleY(1f);
                    previousFabContainer.setScaleX(1f);
                    previousFabContainer.setScaleY(1f);
                    bounceIconAnimator = null;
                    getNotificationCenter().onAnimationFinish(animationIndex);
                }
            });
            bounceIconAnimator.start();
        }, 50);
        return animatorSet;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    } else if (child instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SECTIONS, new Class[]{LetterSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
        themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));

        return themeDescriptions;
    }
}
