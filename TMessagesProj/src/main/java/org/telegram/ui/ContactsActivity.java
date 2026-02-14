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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
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
import org.telegram.messenger.LiteMode;
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
import org.telegram.messenger.utils.SearchTextWatcher;
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
import org.telegram.ui.Components.ContactsEmptyView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class ContactsActivity extends BaseFragment implements FactorAnimator.Target, NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate, WindowAnimatedInsetsProvider.Listener {
    private final int ADDITIONAL_LIST_HEIGHT_DP = Build.VERSION.SDK_INT >= 31 ? 48 : 0;

    private static final int ANIMATOR_ID_SEARCH_FIELD_VISIBLE = 0;
//    private static final int ANIMATOR_ID_SEARCH_FIELD_HEIGHT = 1;
    private static final int ANIMATOR_ID_SEARCH_HAS_QUERY = 2;
//
    private final BoolAnimator animatorSearchFieldVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_FIELD_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
//    private final FactorAnimator animatorSearchFieldHeight = new FactorAnimator(ANIMATOR_ID_SEARCH_FIELD_HEIGHT,
//        this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    private final BoolAnimator animatorSearchHasQuery = new BoolAnimator(ANIMATOR_ID_SEARCH_HAS_QUERY,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    @Keep
    public int phonebookRow = 0;

    private ContactsAdapter listViewAdapter;
    private StickerEmptyView emptyView;
    private RecyclerListView listView;
    private RecyclerAnimationScrollHelper scrollHelper;
    private LinearLayoutManager layoutManager;
    private SearchAdapter searchListViewAdapter;

    private ActionBarMenuItem sortItem;
    private boolean sortByName;

    private FragmentFloatingButton floatingButton;
    private boolean floatingButtonVisibleByScroll = true;
    private SizeNotifierFrameLayout contentView;

    private boolean searchWas;
    private boolean searching;
    private boolean onlyUsers;
    private boolean needPhonebook;
    private boolean hasMainTabs;
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
    private HeaderShadowView headerShadowView;
    private FragmentSearchField searchField;

    private AlertDialog permissionDialog;
    private boolean askAboutContacts = true;

    private boolean disableSections;

    private final LongSparseArray<TLRPC.User> selectedContacts = new LongSparseArray<>();

    private @Nullable ImageView actionModeCloseView;
    private NumberTextView selectedContactsCountTextView;

    private ActionBarMenuItem searchItem;

    private BackDrawable backDrawable;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            iBlur3SourceGlassFrosted = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceGlass = new BlurredBackgroundSourceRenderNode(null);
        } else {
            scrollableViewNoiseSuppressor = null;
            iBlur3SourceGlassFrosted = null;
            iBlur3SourceGlass = null;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
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
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setAllowOverlayTitle(true);
        if (destroyAfterSelect) {
            if (returnAsResult) {
                actionBar.setTitle(getString(R.string.SelectContact));
            } else {
                actionBar.setTitle(getString(createSecretChat ? R.string.NewSecretChat : R.string.NewMessageTitle));
            }
        } else {
            actionBar.setTitle(getString(R.string.Contacts));
        }

        backDrawable = new BackDrawable(false);
        if (!hasMainTabs) {
            actionBar.setBackButtonDrawable(backDrawable);
        }

        searchField = new FragmentSearchField(context, resourceProvider);
        searchField.setSectionBackground();
        searchField.setPivotY(0);
        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);
        actionMode.setBackgroundColor(0);

        if (hasMainTabs) {
            actionModeCloseView = new ImageView(context);
            actionModeCloseView.setScaleType(ImageView.ScaleType.CENTER);
            actionModeCloseView.setImageDrawable(new BackDrawable(true));
            actionModeCloseView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
            actionModeCloseView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector)));
            actionModeCloseView.setOnClickListener(v -> hideActionMode());
            actionMode.addView(actionModeCloseView, LayoutHelper.createLinear(54, 54, Gravity.CENTER_VERTICAL));
        }

        selectedContactsCountTextView = new NumberTextView(actionMode.getContext());
        selectedContactsCountTextView.setTextSize(18);
        selectedContactsCountTextView.setTypeface(AndroidUtilities.bold());
        selectedContactsCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedContactsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, hasMainTabs ? 18 : 72, 0, 0, 0));
        selectedContactsCountTextView.setOnTouchListener((v, event) -> true);

        actionMode.addItemWithWidth(delete, R.drawable.msg_delete, dp(54), getString(R.string.Delete));
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
                } else if (id == search_button) {
                    listView.smoothScrollToPosition(0);
//                    animatorSearchFieldVisible.setValue(true, true);
//                    animatorSearchFieldHeight.animateTo(dp(DialogsActivity.SEARCH_FIELD_HEIGHT));
                    AndroidUtilities.doOnPreDraw(searchField.editText, () -> {
                        searchField.editText.requestFocus();
                        AndroidUtilities.showKeyboard(searchField.editText);
                    });
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        searchItem = menu.addItem(search_button, R.drawable.outline_header_search);
        searchItem.setContentDescription(getString(R.string.SearchContacts));

        searchField.editText.addTextChangedListener(new SearchTextWatcher(searchField.editText, new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                checkUi_floatingButtonVisible();
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
                listView.getFastScroll().topOffset = dp(90);
                // emptyView.setText(LocaleController.getString(R.string.NoContacts));
                checkUi_floatingButtonVisible();
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                animatorSearchHasQuery.setValue(!text.isEmpty(), true);
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
                    if (listView != null) {
                        listView.setAdapter(listViewAdapter);
                        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_STICKY_HEADERS);
                    }
                }
            }
        }));
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
        listViewAdapter.setDisableSections(disableSections);
        listViewAdapter.includeSearch = false;

        fragmentView = contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= 31 && scrollableViewNoiseSuppressor != null) {
                    blur3_InvalidateBlur();

                    final int width = getMeasuredWidth();
                    final int height = getMeasuredHeight();
                    if (iBlur3SourceGlassFrosted != null && !iBlur3SourceGlassFrosted.inRecording()) {
                        // if (iBlur3SourceGlassFrosted.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
                        final Canvas c = iBlur3SourceGlassFrosted.beginRecording(width, height);
                        c.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        if (SharedConfig.chatBlurEnabled()) {
                            scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
                        }
                        iBlur3SourceGlassFrosted.endRecording();
                        // }
                    }
                    if (iBlur3SourceGlass != null && !iBlur3SourceGlass.inRecording()) {
                        // if (iBlur3SourceGlass.needUpdateDisplayList(width, height) || iBlur3Invalidated) {
                        final Canvas c = iBlur3SourceGlass.beginRecording(width, height);
                        c.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        if (SharedConfig.chatBlurEnabled()) {
                            scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                        }
                        iBlur3SourceGlass.endRecording();
                        // }
                    }
                    iBlur3Invalidated = false;
                }

                super.dispatchDraw(canvas);
            }

            @Override
            public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null) {
                    canvas.drawRect(rectTmp, blurScrimPaint);
                    return;
                }

                canvas.save();
                canvas.translate(0, -y);
                iBlur3SourceGlassFrosted.draw(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
                canvas.restore();

                final int oldScrimAlpha = blurScrimPaint.getAlpha();
                blurScrimPaint.setAlpha(ChatActivity.ACTION_BAR_BLUR_ALPHA);
                canvas.drawRect(rectTmp, blurScrimPaint);
                blurScrimPaint.setAlpha(oldScrimAlpha);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                ((MarginLayoutParams) emptyView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight() + dp(DialogsActivity.SEARCH_FIELD_HEIGHT);
                ((MarginLayoutParams) headerShadowView.getLayoutParams()).topMargin = actionBar.getMeasuredHeight();

                checkUi_listViewPadding();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                checkUi_emptyView();
                checkUi_searchButton();
                checkUi_sortItem();
                checkUi_floatingButtonPosition();
                checkUi_searchFieldY();
            }
        };
        iBlur3Capture = new ViewGroupPartRenderer(listView, contentView, listView::drawChild);
        listView.addEdgeEffectListener(() -> listView.postOnAnimation(() -> {
            blur3_InvalidateBlur();
        }));
        listView.setSections(true);
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.PROFILE_SEARCH_CELL);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView, 0);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);
        emptyView.title.setText(getString(R.string.NoResult));
        emptyView.subtitle.setText(getString(R.string.SearchEmptyViewFilteredSubtitle2));
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 12, 52 + 12, 12, 0));

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
        scrollHelper.setScrollListener(this::blur3_InvalidateBlur);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, -ADDITIONAL_LIST_HEIGHT_DP, 0, -ADDITIONAL_LIST_HEIGHT_DP));

        contentView.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.TOP, 6, 0, 6, 0));

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
//                if (scrollingManually && newState != RecyclerView.SCROLL_STATE_DRAGGING) {
//                    final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
//                    final View topChild = recyclerView.getChildAt(0);
//                    final int firstViewTop = topChild != null ? topChild.getTop() : 0;
//
//                    final boolean searchForcedVisible = animatorSearchHasQuery.getValue() || (firstVisibleItem == 0 && firstViewTop >= (listView.getPaddingTop() - dp(DialogsActivity.SEARCH_FIELD_HEIGHT)));
//                    final boolean searchVisible = searchForcedVisible || (animatorSearchFieldHeight.getFactor() > dp(DialogsActivity.SEARCH_FIELD_HEIGHT * (lastScrollToDown ? 1 : 4) / 5f));
//
//                    final float heightTo = dp(searchVisible ? DialogsActivity.SEARCH_FIELD_HEIGHT : 0);
//                    if (animatorSearchFieldHeight.getToFactor() != heightTo) {
//                        animatorSearchFieldHeight.animateTo(heightTo);
//                        canScrollByAnimation = true;
//                    }
//                    animatorSearchFieldVisible.setValue(searchVisible, true);
//                }

                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (searching && searchWas || searchField.editText.isFocused()) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    scrollingManually = true;
                } else {
                    scrollingManually = false;
                }
//                lastListScrollState = newState;
            }

            private boolean lastScrollToDown;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                final View topChild = recyclerView.getChildAt(0);
                final int firstViewTop = topChild != null ? topChild.getTop() : 0;

                if (floatingButton != null && !searching) {
                    boolean goingDown = dy > 0;
                    if (dy != 0 && scrollUpdated && (goingDown || scrollingManually)) {
                        floatingButtonVisibleByScroll = !goingDown;
                        checkUi_floatingButtonVisible();
                    }
                    scrollUpdated = true;
                }

//                if (scrollingManually) {
//                    final boolean searchForcedVisible = animatorSearchHasQuery.getValue() || (firstVisibleItem == 0 && firstViewTop >= (listView.getPaddingTop() /*- dp(DialogsActivity.SEARCH_FIELD_HEIGHT)*/));
//                    float searchH = animatorSearchFieldHeight.getFactor();
//                    if (!searchForcedVisible && dy != 0) {
//                        searchH = MathUtils.clamp(searchH - dy, 0, dp(DialogsActivity.SEARCH_FIELD_HEIGHT));
//                        animatorSearchFieldHeight.forceFactor(searchH);
//                    }
//
//                    final boolean searchVisible = searchForcedVisible || (animatorSearchFieldHeight.getFactor() > 0);
//                    animatorSearchFieldVisible.setValue(searchVisible, true);
//                }

                final boolean shadowVisible = !(firstVisibleItem == 0 && firstViewTop >= listView.getPaddingTop());
                headerShadowView.setShadowVisible(shadowVisible, true);

                lastScrollToDown = dy < 0;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                    scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    blur3_InvalidateBlur();
                }

                checkUi_searchFieldY();
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
            actionBar.openSearchField(initialSearchString, false);
            initialSearchString = null;
        }

        contentView.addView(actionBar);

        headerShadowView = new HeaderShadowView(context, parentLayout);
        headerShadowView.setShadowVisible(false, false);
        contentView.addView(headerShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 5, Gravity.TOP));

        actionBar.setAdaptiveBackground(listView);
        actionBar.setDrawBlurBackground(contentView);

//        animatorSearchFieldHeight.forceFactor(dp(DialogsActivity.SEARCH_FIELD_HEIGHT));
        animatorSearchFieldVisible.setValue(true, false);

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

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        actionBar.setUseContainerForTitles();
        actionBar.getTitlesContainer().setTranslationX(dp(4));
        actionBar.setAddToContainer(false);
        actionBar.createAdditionalSubTitleOverlayContainer();
        actionBar.getAdditionalSubTitleOverlayContainer().setTranslationX(dp(4));
        actionBar.getAdditionalSubTitleOverlayContainer().setTranslationY(-dp(2));
        return actionBar;
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
                editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
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
        if (actionBar.isActionModeShowed()) {
            if (invoked) hideActionMode();
            return false;
        } else if (animatorSearchHasQuery.getValue()) {
            if (invoked) {
                searchField.editText.getText().clear();
            }
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
            if (actionModeCloseView != null) {
                actionModeCloseView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
                actionModeCloseView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector)));
            }
            if (actionBar != null) {
                actionBar.updateColors();
            }
            if (contentView != null) {
                contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            }
        };

        if (!hasMainTabs) {
            themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }
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

        if (floatingButton != null) {
            themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
            themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
            themeDescriptions.add(new ThemeDescription(floatingButton.imageView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));
        }
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

//    private int lastListScrollState;
//    private boolean canScrollByAnimation;
//    private float lastSearchFieldHeight;
//    private float scrollByAcc;

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SEARCH_FIELD_VISIBLE) {
            checkUi_searchButton();
//        } else if (id == ANIMATOR_ID_SEARCH_FIELD_HEIGHT) {
//            searchField.setAlpha(animatorSearchFieldVisible.getFloatValue() * (animatorSearchFieldHeight.getFactor() / dp(DialogsActivity.SEARCH_FIELD_HEIGHT)));
//            headerShadowView.setTranslationY(factor);
//            final float alpha = factor / dp(DialogsActivity.SEARCH_FIELD_HEIGHT);
//            searchField.setClipHeight(alpha);
//
//            if (canScrollByAnimation && lastListScrollState == RecyclerView.SCROLL_STATE_IDLE) {
//                scrollByAcc += (lastSearchFieldHeight - factor);
//                int scrollBy = Math.round(scrollByAcc);
//                scrollByAcc -= scrollBy;
//                listView.scrollBy(0, scrollBy);
//            }
//
//            lastSearchFieldHeight = factor;
//            checkUi_listClip();
        } else if (id == ANIMATOR_ID_SEARCH_HAS_QUERY) {
            checkUi_searchButton();
            checkUi_sortItem();
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (listView != null && listView.getFastScroll() != null && listView.getFastScroll().isPressed()) {
            return false;
        }
        return true;
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
//        if (id == ANIMATOR_ID_SEARCH_FIELD_HEIGHT) {
//            canScrollByAnimation = false;
//            scrollByAcc = 0;
//        }
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
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

        checkUi_listViewPadding();
        checkUi_floatingButtonPosition();
        checkUi_emptyView();

        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        navigationBarHeight = bottom;
        checkUi_listViewPadding();
        checkUi_floatingButtonPosition();
        checkUi_emptyView();
    }

    private void checkUi_emptyView() {
        if (emptyView != null) {
            emptyView.setKeyboardHeight(Math.max(navigationBarHeight + additionNavigationBarHeight, imeInsetAnimatedHeight), false);
        }
    }

    private void checkUi_listViewPadding() {
        listView.setPadding(
            0,
            dp(ADDITIONAL_LIST_HEIGHT_DP + 44) + actionBar.getMeasuredHeight(),
            0,
            dp(ADDITIONAL_LIST_HEIGHT_DP) + navigationBarHeight + additionNavigationBarHeight
        );
    }

    private boolean lastIsEmpty;

    private void checkUi_searchFieldHint() {
        final boolean isEmpty = listViewAdapter != null && listViewAdapter.isEmpty();

        if (lastIsEmpty != isEmpty || TextUtils.isEmpty(searchField.editText.getHint())) {
            searchField.editText.setHint(getString(isEmpty ? R.string.SearchPeopleByUsername : R.string.SearchContacts));
            searchField.editText.setContentDescription(getString(isEmpty ? R.string.SearchPeopleByUsername : R.string.SearchContacts));
            lastIsEmpty = isEmpty;
        }
    }

    private void checkUi_searchFieldY() {
        float top = listView.getY() + listView.getPaddingTop();
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            final int position = listView.getChildAdapterPosition(child);
            if (position == 0) {
                final RecyclerView.ItemDecoration decoration = listView.getItemDecorationAt(i);
                decoration.getItemOffsets(AndroidUtilities.rectTmp2, child, listView, listView.mState);
                top = listView.getY() + (child.getY() - (listViewAdapter.isEmptyWithMainTabs ? 0 : AndroidUtilities.rectTmp2.top));
                break;
            } else if (position > 0) {
                top = -dp(52);
                break;
            }
        }
        searchField.setTranslationY(lerp(top, listView.getY() + listView.getPaddingTop(), animatorSearchHasQuery.getFloatValue()) - dp(48));
        animatorSearchFieldVisible.setValue(top > listView.getY() + listView.getPaddingTop() - dp(12), true);
    }

    private void checkUi_sortItem() {
        final float factor1 = 1f - animatorSearchHasQuery.getFloatValue();
        final float factor2 = listViewAdapter == null || listViewAdapter.isEmpty() ? 0 : 1;
        final float factor = factor1 * factor2;
        FragmentFloatingButton.setAnimatedVisibility(sortItem, factor);
    }

    private void checkUi_searchButton() {
        final float factor1 = 1f - animatorSearchFieldVisible.getFloatValue();
        final float factor2 = 1f - animatorSearchHasQuery.getFloatValue();
        final float factor = factor1 * factor2;
        FragmentFloatingButton.setAnimatedVisibility(searchItem, factor);
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



    /* Blur */

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlass;

    private IBlur3Capture iBlur3Capture;
    private boolean iBlur3Invalidated;

    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
    private final RectF iBlur3PositionActionBar = new RectF();
    private final RectF iBlur3PositionMainTabs = new RectF(); {
        iBlur3Positions.add(iBlur3PositionActionBar);
        iBlur3Positions.add(iBlur3PositionMainTabs);
    }

    private void blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        final int additionalList = dp(48);
        final int additionalSearch = dp(DialogsActivity.SEARCH_FIELD_HEIGHT);

        final int mainTabBottom = fragmentView.getMeasuredHeight() - navigationBarHeight - dp(DialogsActivity.MAIN_TABS_MARGIN);
        final int mainTabTop = mainTabBottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);

        iBlur3PositionActionBar.set(0, -additionalList, fragmentView.getMeasuredWidth(), actionBar.getMeasuredHeight() + additionalList + additionalSearch );
        iBlur3PositionMainTabs.set(0, mainTabTop, fragmentView.getMeasuredWidth(), mainTabBottom);
        iBlur3PositionMainTabs.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -dp(48));

        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, hasMainTabs ? 2 : 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return iBlur3SourceGlass;
    }


    @Override
    public void onParentScrollToTop() {
        if (layoutManager.findFirstVisibleItemPosition() < 15) {
            listView.smoothScrollToPosition(0);
        } else {
            scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(0, 0, false, true);
        }
//        animatorSearchFieldHeight.animateTo(dp(DialogsActivity.SEARCH_FIELD_HEIGHT));
        animatorSearchFieldVisible.setValue(true, true);
    }
}
