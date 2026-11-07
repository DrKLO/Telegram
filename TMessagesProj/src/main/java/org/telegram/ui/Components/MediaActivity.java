package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugProvider;
import org.telegram.ui.Components.Paint.ShapeDetector;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.animator.BoolAnimator;

/** The shared media of a chat, or the stories of a peer, as a simple glass screen: fullscreen pages under the glass header and tabs. */
public class MediaActivity extends BaseFragment implements SharedMediaLayout.SharedMediaPreloaderDelegate, FloatingDebugProvider, NotificationCenter.NotificationCenterDelegate {

    public static final int TYPE_MEDIA = 0;
    public static final int TYPE_STORIES = 1;
    public static final int TYPE_ARCHIVED_CHANNEL_STORIES = 2;
    public static final int TYPE_STORIES_SEARCH = 3;

    private int type;

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private TLRPC.ChatFull currentChatInfo;
    private TLRPC.UserFull currentUserInfo;
    private long dialogId;
    private long topicId;
    private String hashtag;
    private String username;
    private int storiesCount;
    private SimpleActionBarMenuItem optionsItem;
    private SparseArray<MessageObject> actionModeMessageObjects;
    private ActionBarMenuSubItem showPhotosItem, showVideosItem;
    private boolean filterPhotos = true, filterVideos = true;
    private int shiftDp = -12;
    private ActionBarMenuSubItem calendarItem, zoomInItem, zoomOutItem;

    /** The bottom tabs of the stories: posted and archived, glass like the main tabs. */
    private MainTabsLayout storiesTabsView;
    private final GlassTabView[] storiesTabs = new GlassTabView[2];
    private int storiesTabsSelected = -1;
    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;
    private BoolAnimator actionModeBottomAnimator;

    private View listTopOverlay;

    private Runnable applyBulletin;

    SharedMediaLayout sharedMediaLayout;
    private int initialTab;

    public MediaActivity(Bundle args, SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader) {
        super(args);
        this.sharedMediaPreloader = sharedMediaPreloader;
    }

    @Override
    public boolean onFragmentCreate() {
        type = getArguments().getInt("type", TYPE_MEDIA);
        dialogId = getArguments().getLong("dialog_id");
        topicId = getArguments().getLong("topic_id", 0);
        hashtag = getArguments().getString("hashtag", "");
        username = getArguments().getString("username", "");
        storiesCount = getArguments().getInt("storiesCount", -1);
        int defaultTab = SharedMediaLayout.TAB_PHOTOVIDEO;
        if (type == TYPE_ARCHIVED_CHANNEL_STORIES) {
            defaultTab = SharedMediaLayout.TAB_ARCHIVED_STORIES;
        } else if (type == TYPE_STORIES) {
            defaultTab = SharedMediaLayout.TAB_STORIES;
        }
        initialTab = getArguments().getInt("start_from", defaultTab);
        getNotificationCenter().addWeakObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().addWeakObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().addWeakObserver(this, NotificationCenter.storiesEnabledUpdate);
        if (DialogObject.isUserDialog(dialogId) && topicId == 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (UserObject.isUserSelf(user)) {
                getMessagesController().loadUserInfo(user, false, this.classGuid);
                currentUserInfo = getMessagesController().getUserFull(dialogId);
            }
        }
        if (this.sharedMediaPreloader == null) {
            this.sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
        }
        this.sharedMediaPreloader.addDelegate(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.storiesEnabledUpdate);
        if (applyBulletin != null) {
            Runnable runnable = applyBulletin;
            applyBulletin = null;
            AndroidUtilities.runOnUIThread(runnable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            long uid = (long) args[0];
            if (uid == dialogId) {
                currentUserInfo = (TLRPC.UserFull) args[1];
                if (sharedMediaLayout != null) {
                    sharedMediaLayout.setUserInfo(currentUserInfo);
                }
            }
        }
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    private boolean isStoriesType() {
        return type == TYPE_STORIES || type == TYPE_ARCHIVED_CHANNEL_STORIES;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonDrawable(new BackDrawable(false));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (sharedMediaLayout.closeActionMode(true)) {
                        return;
                    }
                    finishFragment();
                } else if (id == 10) {
                    sharedMediaLayout.showMediaCalendar(sharedMediaLayout.getClosestTab(), false);
                }
            }
        });

        final FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (sharedMediaLayout != null && sharedMediaLayout.isInFastScroll()) {
                    return sharedMediaLayout.dispatchFastScrollEvent(ev);
                }
                if (sharedMediaLayout != null && sharedMediaLayout.checkPinchToZoom(ev)) {
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        fragmentView = frameLayout;

        if (type == TYPE_MEDIA && dialogId == getUserConfig().getClientUserId() && topicId == 0 && !getMessagesController().getSavedMessagesController().unsupported && getMessagesController().getSavedMessagesController().hasDialogs()) {
            initialTab = SharedMediaLayout.TAB_SAVED_DIALOGS;
        }
        final BaseFragmentSimpleGlass glass = getBaseSimpleGlass();
        sharedMediaLayout = createSharedMediaLayout(context, glass.factory);
        sharedMediaLayout.setOnTopPanelShiftChanged(this::applyInsets);
        sharedMediaLayout.scrollSlidingTextTabStrip.setOpen(true);
        sharedMediaLayout.setPinnedToTop(true);
        frameLayout.addView(sharedMediaLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (isStoriesType()) {
            createStoriesOptions(context);
        }
        if (type == TYPE_STORIES) {
            createStoriesBottom(context, frameLayout, glass);
        }

        listTopOverlay = new View(context);
        sharedMediaLayout.addPagesOverlay(listTopOverlay, 0);
        listTopOverlay.setBackground(glass.createActionBarFadeDrawable(listTopOverlay));

        navigationBarFadeView = new View(context);
        navigationBarFadeView.setBackground(glass.createNavigationBarFadeDrawable(navigationBarFadeView));
        frameLayout.addView(navigationBarFadeView, storiesTabsView != null ? frameLayout.indexOfChild(storiesTabsView) : frameLayout.getChildCount(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        glass.setViews(frameLayout, simpleActionBar, getResourceProvider());
        glass.setCapture((canvas, position) -> sharedMediaLayout.capturePages(canvas, position, frameLayout));
        sharedMediaLayout.setPagesGlassEngine(glassEngine);
        simpleActionBar.setBackground(null);

        updateTitle(false);
        if (type == TYPE_STORIES_SEARCH && storiesCount != -1) {
            simpleActionBar.setSubtitle(LocaleController.formatPluralStringSpaced("FoundStories", storiesCount));
        }
        applyInsets();
        updateMediaCount();
        if (type == TYPE_STORIES) {
            updateStoriesTabs(initialTab == SharedMediaLayout.TAB_ARCHIVED_STORIES ? 1 : 0, false);
        }
        return frameLayout;
    }

    /** The options of the stories in the glass pill of the header; the selection uses the actions of the layout. */
    private void createStoriesOptions(Context context) {
        optionsItem = simpleActionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        optionsItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        optionsItem.setShown(false, false);
        zoomInItem = optionsItem.addSubItem(8, R.drawable.msg_zoomin, LocaleController.getString(R.string.MediaZoomIn));
        zoomInItem.setOnClickListener(v -> {
            final Boolean canZoomIn = sharedMediaLayout.zoomIn();
            if (canZoomIn != null) {
                updateZoomItems(true, canZoomIn);
            }
        });
        zoomOutItem = optionsItem.addSubItem(9, R.drawable.msg_zoomout, LocaleController.getString(R.string.MediaZoomOut));
        zoomOutItem.setOnClickListener(v -> {
            final Boolean canZoomOut = sharedMediaLayout.zoomOut();
            if (canZoomOut != null) {
                updateZoomItems(canZoomOut, true);
            }
        });
        calendarItem = optionsItem.addSubItem(10, R.drawable.msg_calendar2, LocaleController.getString(R.string.Calendar));
        calendarItem.setEnabled(false);
        calendarItem.setAlpha(.5f);
        optionsItem.addColoredGap();
        showPhotosItem = optionsItem.addSubItem(6, 0, LocaleController.getString(R.string.MediaShowPhotos), true);
        showPhotosItem.setChecked(filterPhotos);
        showPhotosItem.setOnClickListener(e -> {
            if (filterPhotos && !filterVideos) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(showPhotosItem, shiftDp = -shiftDp);
                return;
            }
            showPhotosItem.setChecked(filterPhotos = !filterPhotos);
            sharedMediaLayout.setStoriesFilter(filterPhotos, filterVideos);
        });
        showVideosItem = optionsItem.addSubItem(7, 0, LocaleController.getString(R.string.MediaShowVideos), true);
        showVideosItem.setChecked(filterVideos);
        showVideosItem.setOnClickListener(e -> {
            if (filterVideos && !filterPhotos) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(showVideosItem, shiftDp = -shiftDp);
                return;
            }
            showVideosItem.setChecked(filterVideos = !filterVideos);
            sharedMediaLayout.setStoriesFilter(filterPhotos, filterVideos);
        });
    }

    private void updateZoomItems(boolean canZoomOut, boolean canZoomIn) {
        zoomOutItem.setEnabled(canZoomOut);
        zoomOutItem.animate().alpha(canZoomOut ? 1f : .5f).start();
        zoomInItem.setEnabled(canZoomIn);
        zoomInItem.animate().alpha(canZoomIn ? 1f : .5f).start();
    }

    /** My stories: glass tabs of the posted and the archived ones at the bottom, the button of the selection instead of them. */
    private void createStoriesBottom(Context context, FrameLayout frameLayout, BaseFragmentSimpleGlass glass) {
        storiesTabsView = new MainTabsLayout(context, getResourceProvider());
        storiesTabsView.setClipChildren(false);
        storiesTabsView.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4));
        storiesTabs[0] = GlassTabView.createMainTab(context, getResourceProvider(), GlassTabView.TabAnimation.STORIES_SAVED, R.string.ProfileMyStoriesTab);
        storiesTabs[1] = GlassTabView.createMainTab(context, getResourceProvider(), GlassTabView.TabAnimation.STORIES_ARCHIVE, R.string.ProfileStoriesArchiveTab);
        for (int i = 0; i < storiesTabs.length; ++i) {
            final int index = i;
            storiesTabs[i].setOnClickListener(v -> sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_STORIES + index));
            storiesTabsView.addView(storiesTabs[i]);
            storiesTabsView.setViewVisible(storiesTabs[i], true, false);
        }
        final BlurredBackgroundDrawable tabsBackground = glass.factory.create(storiesTabsView, BlurredBackgroundProviderImpl.mainTabs(getResourceProvider()));
        tabsBackground.setRadius(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsBackground.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        storiesTabsView.setBackground(tabsBackground);
        frameLayout.addView(storiesTabsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(dp(16), dp(DialogsActivity.MAIN_TABS_MARGIN), dp(16), dp(DialogsActivity.MAIN_TABS_MARGIN));
        button = new ButtonWithCounterView(context, getResourceProvider());
        button.setText(LocaleController.getString(R.string.SaveToProfile), false);
        button.setShowZero(true);
        button.setCount(0, false);
        button.setEnabled(false);
        button.setOnClickListener(v -> onStoriesButtonClick());
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT));
        buttonContainer.setVisibility(View.INVISIBLE);
        frameLayout.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM));

        actionModeBottomAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> checkStoriesBottom(), CubicBezierInterpolator.EASE_OUT_QUINT, 320);

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return mSystemInsets.bottom + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS);
            }
        });
    }

    /** The tabs go down and the button of the selection comes up from below. */
    private void checkStoriesBottom() {
        if (storiesTabsView == null) {
            return;
        }
        final float factor = actionModeBottomAnimator.getFloatValue();
        final int height = dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) + mSystemInsets.bottom;
        storiesTabsView.setTranslationY(-mSystemInsets.bottom + height * factor);
        storiesTabsView.setAlpha(1f - factor);
        storiesTabsView.setVisibility(factor < 1f ? View.VISIBLE : View.INVISIBLE);
        buttonContainer.setTranslationY(-mSystemInsets.bottom + height * (1f - factor));
        buttonContainer.setAlpha(factor);
        buttonContainer.setVisibility(factor > 0f ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateStoriesTabs(int selected, boolean animated) {
        if (storiesTabsView == null || storiesTabsSelected == selected) {
            return;
        }
        storiesTabsSelected = selected;
        storiesTabsView.setTabSelected(storiesTabs[selected], animated);
    }

    private void onStoriesButtonClick() {
        if (applyBulletin != null) {
            applyBulletin.run();
            applyBulletin = null;
        }
        Bulletin.hideVisible();
        boolean pin = sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_ARCHIVED_STORIES;
        int count = 0;
        ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
        if (actionModeMessageObjects != null) {
            for (int i = 0; i < actionModeMessageObjects.size(); ++i) {
                MessageObject messageObject = actionModeMessageObjects.valueAt(i);
                if (messageObject.storyItem != null) {
                    storyItems.add(messageObject.storyItem);
                    count++;
                }
            }
        }
        sharedMediaLayout.closeActionMode(false);
        if (pin) {
            sharedMediaLayout.scrollToPage(SharedMediaLayout.TAB_STORIES);
        }
        if (storyItems.isEmpty()) {
            return;
        }
        boolean[] pastValues = new boolean[storyItems.size()];
        for (int i = 0; i < storyItems.size(); ++i) {
            TL_stories.StoryItem storyItem = storyItems.get(i);
            pastValues[i] = storyItem.pinned;
            storyItem.pinned = pin;
        }
        getMessagesController().getStoriesController().updateStoriesInLists(dialogId, storyItems);
        final boolean[] undone = new boolean[] { false };
        applyBulletin = () -> {
            getMessagesController().getStoriesController().updateStoriesPinned(dialogId, storyItems, pin, null);
        };
        final Runnable undo = () -> {
            undone[0] = true;
            AndroidUtilities.cancelRunOnUIThread(applyBulletin);
            for (int i = 0; i < storyItems.size(); ++i) {
                TL_stories.StoryItem storyItem = storyItems.get(i);
                storyItem.pinned = pastValues[i];
            }
            getMessagesController().getStoriesController().updateStoriesInLists(dialogId, storyItems);
        };
        Bulletin bulletin;
        if (pin) {
            bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.formatPluralString("StorySavedTitle", count), LocaleController.getString(R.string.StorySavedSubtitle), LocaleController.getString(R.string.Undo), undo).show();
        } else {
            bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_archived, LocaleController.formatPluralString("StoryArchived", count), LocaleController.getString(R.string.Undo), Bulletin.DURATION_PROLONG, undo).show();
        }
        bulletin.setOnHideListener(() -> {
            if (!undone[0] && applyBulletin != null) {
                applyBulletin.run();
            }
            applyBulletin = null;
        });
    }

    private void applyInsets() {
        if (sharedMediaLayout == null) {
            return;
        }
        // the tabs pill right under the glass header, the same overscan as the lists of other simple screens
        final int bottomTabsHeight = storiesTabsView != null ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        sharedMediaLayout.setPagesInsets(
            SharedMediaLayout.getPagesTabsTop(mSystemInsets.top + ActionBar.getCurrentActionBarHeight()),
            BaseFragmentSimpleGlass.getOverscan(mSystemInsets.top),
            BaseFragmentSimpleGlass.getOverscan(mSystemInsets.bottom + bottomTabsHeight),
            mSystemInsets.bottom + bottomTabsHeight
        );
        if (listTopOverlay != null) {
            // the tabs and the player above them are on the blur
            final int height = mSystemInsets.top + ActionBar.getCurrentActionBarHeight() + dp(44) + sharedMediaLayout.getTopPanelShift();
            if (listTopOverlay.getLayoutParams().height != height) {
                listTopOverlay.getLayoutParams().height = height;
                listTopOverlay.requestLayout();
            }
        }
        if (navigationBarFadeView != null) {
            navigationBarFadeView.getLayoutParams().height = mSystemInsets.bottom + bottomTabsHeight;
            navigationBarFadeView.setVisibility(mSystemInsets.bottom >= dp(40) || bottomTabsHeight > 0 ? View.VISIBLE : View.GONE);
            navigationBarFadeView.requestLayout();
        }
        checkStoriesBottom();
    }

    private CharSequence getDialogTitle() {
        final long avatarDialogId = topicId != 0 && dialogId == getUserConfig().getClientUserId() ? topicId : dialogId;
        CharSequence title = null;
        if (avatarDialogId == UserObject.ANONYMOUS) {
            title = LocaleController.getString(R.string.AnonymousForward);
        } else if (topicId != 0 && avatarDialogId == getUserConfig().getClientUserId()) {
            title = LocaleController.getString(R.string.MyNotes);
        } else if (DialogObject.isEncryptedDialog(avatarDialogId)) {
            final TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(avatarDialogId));
            final TLRPC.User user = encryptedChat != null ? getMessagesController().getUser(encryptedChat.user_id) : null;
            if (user != null) {
                title = ContactsController.formatName(user.first_name, user.last_name);
            }
        } else if (DialogObject.isUserDialog(avatarDialogId)) {
            final TLRPC.User user = getMessagesController().getUser(avatarDialogId);
            if (user != null) {
                title = user.self ? LocaleController.getString(R.string.SavedMessages) : ContactsController.formatName(user.first_name, user.last_name);
            }
        } else {
            final TLRPC.Chat chat = getMessagesController().getChat(-avatarDialogId);
            if (chat != null) {
                title = chat.title;
            }
        }
        return TextUtils.isEmpty(title) ? LocaleController.getString(R.string.SharedContentTitle) : title;
    }

    private CharSequence currentTitle;

    private void updateTitle(boolean animated) {
        final CharSequence title;
        boolean forward = true;
        if (type == TYPE_STORIES_SEARCH) {
            title = hashtag;
        } else if (type == TYPE_ARCHIVED_CHANNEL_STORIES) {
            title = LocaleController.getString(R.string.ProfileStoriesArchive);
        } else if (type == TYPE_STORIES) {
            forward = sharedMediaLayout == null || sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_ARCHIVED_STORIES;
            title = LocaleController.getString(forward ? R.string.ProfileStoriesArchive : R.string.ProfileMyStories);
        } else {
            title = getDialogTitle();
        }
        if (TextUtils.equals(currentTitle, title)) {
            return;
        }
        currentTitle = title;
        if (animated) {
            simpleActionBar.setTitleAnimated(title, forward, 320);
        } else {
            simpleActionBar.setTitle(title);
        }
    }

    private SharedMediaLayout createSharedMediaLayout(Context context, @Nullable BlurredBackgroundDrawableViewFactory glassFactory) {
        return new SharedMediaLayout(context, dialogId, sharedMediaPreloader, 0, null, currentChatInfo, currentUserInfo, initialTab, 0, this, new SharedMediaLayout.Delegate() {
            @Override
            public void scrollToSharedMedia() {

            }

            @Override
            public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View vi) {
                return false;
            }

            @Override
            public TLRPC.Chat getCurrentChat() {
                return null;
            }

            @Override
            public boolean isFragmentOpened() {
                return true;
            }

            @Override
            public RecyclerListView getListView() {
                return null;
            }

            @Override
            public boolean canSearchMembers() {
                return false;
            }

            @Override
            public void updateSelectedMediaTabText() {
                updateMediaCount();
            }

        }, SharedMediaLayout.VIEW_TYPE_MEDIA_ACTIVITY, getResourceProvider(), glassFactory) {
            @Override
            protected void onSelectedTabChanged() {
                super.onSelectedTabChanged();
                updateMediaCount();
            }

            @Override
            public String getStoriesHashtag() {
                return hashtag;
            }

            @Override
            public String getStoriesHashtagUsername() {
                return username;
            }

            @Override
            protected boolean canShowSearchItem() {
                return !isStoriesType();
            }

            @Override
            protected void onSearchStateChanged(boolean expanded) {
                AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
            }

            @Override
            protected boolean isStoriesView() {
                return isStoriesType();
            }

            protected boolean customTabs() {
                return isStoriesType() || type == TYPE_STORIES_SEARCH;
            }

            @Override
            protected boolean includeStories() {
                return isStoriesType();
            }

            @Override
            protected boolean includeSavedDialogs() {
                return type == TYPE_MEDIA && dialogId == getUserConfig().getClientUserId() && topicId == 0;
            }

            @Override
            protected boolean isArchivedOnlyStoriesView() {
                return type == TYPE_ARCHIVED_CHANNEL_STORIES;
            }

            @Override
            protected int getInitialTab() {
                return initialTab;
            }

            @Override
            protected void showActionMode(boolean show) {
                if (isActionModeShowed == show) {
                    return;
                }
                super.showActionMode(show);
                if (isStoriesType()) {
                    disableScroll(show);
                }
                if (actionModeBottomAnimator != null) {
                    actionModeBottomAnimator.setValue(show, true);
                }
            }

            @Override
            protected void onActionModeSelectedUpdate(SparseArray<MessageObject> messageObjects) {
                final int count = messageObjects.size();
                actionModeMessageObjects = messageObjects;
                if (button != null) {
                    button.setEnabled(count > 0);
                    button.setCount(count, true);
                    if (sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_STORIES) {
                        button.setText(LocaleController.formatPluralString("ArchiveStories", count), true);
                    }
                }
            }

            @Override
            protected void onTabProgress(float progress) {
                if (type != TYPE_STORIES) {
                    return;
                }
                updateStoriesTabs(Math.round(progress - TAB_STORIES) > 0 ? 1 : 0, true);
                updateTitle(true);
            }
        };
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasShownSheet()) {
            if (invoked) closeSheet();
            return false;
        }
        if (sharedMediaLayout.isActionModeShown()) {
            if (invoked) sharedMediaLayout.closeActionMode(false);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (!sharedMediaLayout.isSwipeBackEnabled()) {
            return false;
        }
        return sharedMediaLayout.isCurrentTabFirst();
    }

    @Override
    public boolean canBeginSlide() {
        if (!sharedMediaLayout.isSwipeBackEnabled()) {
            return false;
        }
        return super.canBeginSlide();
    }

    private int lastTab;

    private void setSubtitle(CharSequence text) {
        simpleActionBar.setSubtitleAnimated(text);
    }

    private void updateMediaCount() {
        if (sharedMediaLayout == null || simpleActionBar == null) {
            return;
        }
        int id = sharedMediaLayout.getClosestTab();
        if (type == TYPE_STORIES_SEARCH && id != SharedMediaLayout.TAB_STORIES) {
            return;
        }
        int[] mediaCount = sharedMediaPreloader.getLastMediaCount();
        final boolean animated = !LocaleController.isRTL;
        if (id == SharedMediaLayout.TAB_STORIES || id == SharedMediaLayout.TAB_ARCHIVED_STORIES) {
            if (zoomOutItem != null) {
                zoomOutItem.setEnabled(sharedMediaLayout.canZoomOut());
                zoomOutItem.setAlpha(zoomOutItem.isEnabled() ? 1f : .5f);
            }
            if (zoomInItem != null) {
                zoomInItem.setEnabled(sharedMediaLayout.canZoomIn());
                zoomInItem.setAlpha(zoomInItem.isEnabled() ? 1f : .5f);
            }

            final int count = sharedMediaLayout.getStoriesCount(id);
            if (type == TYPE_STORIES_SEARCH) {
                if (count > 0) {
                    setSubtitle(LocaleController.formatPluralStringSpaced("FoundStories", count));
                }
            } else if (id == SharedMediaLayout.TAB_STORIES) {
                setSubtitle(count > 0 ? LocaleController.formatPluralString("ProfileMyStoriesCount", count) : null);
            } else {
                setSubtitle(count > 0 ? LocaleController.formatPluralString("ProfileStoriesArchiveCount", count) : null);
            }
            updateTitle(true);

            if (optionsItem != null) {
                optionsItem.setShown(count > 0, true);
            }

            if (button != null) {
                boolean animated2 = animated && lastTab == id;
                if (id == SharedMediaLayout.TAB_STORIES) {
                    button.setText(LocaleController.formatPluralString("ArchiveStories", actionModeMessageObjects == null ? 0 : actionModeMessageObjects.size()), animated2);
                } else {
                    button.setText(LocaleController.getString(R.string.SaveToProfile), animated2);
                }
                lastTab = id;
            }

            if (calendarItem != null) {
                boolean calendarAvailable = count > 0;
                calendarItem.setEnabled(calendarAvailable);
                calendarItem.setAlpha(calendarAvailable ? 1f : .5f);
            }

            return;
        }
        if (id == SharedMediaLayout.TAB_SAVED_DIALOGS) {
            int count = getMessagesController().getSavedMessagesController().getAllCount();
            setSubtitle(LocaleController.formatPluralString("SavedDialogsTabCount", count));
            return;
        }
        if (id < 0 || id < mediaCount.length && mediaCount[id] < 0) {
            return;
        }
        if (id == SharedMediaLayout.TAB_PHOTOVIDEO) {
            if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
                setSubtitle(LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]));
            } else if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
                setSubtitle(LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]));
            } else {
                setSubtitle(LocaleController.formatPluralString("Media", mediaCount[MediaDataController.MEDIA_PHOTOVIDEO]));
            }
        } else if (id == SharedMediaLayout.TAB_FILES) {
            setSubtitle(LocaleController.formatPluralString("Files", mediaCount[MediaDataController.MEDIA_FILE]));
        } else if (id == SharedMediaLayout.TAB_VOICE) {
            setSubtitle(LocaleController.formatPluralString("Voice", mediaCount[MediaDataController.MEDIA_AUDIO]));
        } else if (id == SharedMediaLayout.TAB_LINKS) {
            setSubtitle(LocaleController.formatPluralString("Links", mediaCount[MediaDataController.MEDIA_URL]));
        } else if (id == SharedMediaLayout.TAB_AUDIO) {
            setSubtitle(LocaleController.formatPluralString("MusicFiles", mediaCount[MediaDataController.MEDIA_MUSIC]));
        } else if (id == SharedMediaLayout.TAB_GIF) {
            setSubtitle(LocaleController.formatPluralString("GIFs", mediaCount[MediaDataController.MEDIA_GIF]));
        } else if (id == SharedMediaLayout.TAB_POLL && mediaCount[MediaDataController.MEDIA_POLL] >= 0) {
            setSubtitle(LocaleController.formatPluralString("ProfilePollsCount", mediaCount[MediaDataController.MEDIA_POLL]));
        } else if (id == SharedMediaLayout.TAB_RECOMMENDED_CHANNELS) {
            MessagesController.ChannelRecommendations rec = MessagesController.getInstance(currentAccount).getChannelRecommendations(dialogId);
            setSubtitle(LocaleController.formatPluralString("Channels", rec == null ? 0 : rec.more + rec.chats.size()));
        }
    }

    public void setChatInfo(TLRPC.ChatFull currentChatInfo) {
        this.currentChatInfo = currentChatInfo;
    }

    public long getDialogId() {
        return dialogId;
    }

    @Override
    public void mediaCountUpdated() {
        if (sharedMediaLayout != null && sharedMediaPreloader != null) {
            sharedMediaLayout.setNewMediaCounts(sharedMediaPreloader.getLastMediaCount());
        }
        updateMediaCount();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.addAll(sharedMediaLayout.getThemeDescriptions());
        return arrayList;
    }

    @Override
    public List<FloatingDebugController.DebugItem> onGetDebugItems() {
        return Arrays.asList(
            new FloatingDebugController.DebugItem(
                (ShapeDetector.isLearning(getContext()) ? "Disable" : "Enable") + " shape detector learning debug",
                () -> {
                    ShapeDetector.setLearning(getContext(), !ShapeDetector.isLearning(getContext()));
                }
            )
        );
    }

    @Override
    public int getNavigationBarColor() {
        final int color = super.getNavigationBarColor();
        if (getLastStoryViewer() != null && getLastStoryViewer().attachedToParent()) {
            return getLastStoryViewer().getNavigationBarColor(color);
        }
        return color;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        applyInsets();
    }
}
