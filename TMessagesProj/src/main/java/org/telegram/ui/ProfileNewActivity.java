/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.HeaderGiftParticleSystem;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.AvatarAnimationHelper;
import org.telegram.ui.Components.TextTransitionHelper;
import org.telegram.ui.Components.HeaderButtonsAnimationHelper;
import org.telegram.ui.Components.HeaderScrollResponder;
import org.telegram.ui.Components.CollapsingHeaderOffsetListener;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.SettingsSuggestionCell;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Business.ProfileLocationCell;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.TopicsFragment;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.tl.TL_account;
import java.util.Calendar;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatUsersActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.ChatEditActivity;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.PrivacySettingsActivity;
import org.telegram.ui.DataSettingsActivity;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.SessionsActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Business.BusinessLinksActivity;
import org.telegram.ui.ChangeBioActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import androidx.core.content.ContextCompat;

public class ProfileNewActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate,
        SharedMediaLayout.Delegate, SharedMediaLayout.SharedMediaPreloaderDelegate {

    private CoordinatorLayout coordinatorLayout;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;
//    private Toolbar toolbar;
    private NestedScrollView nestedScrollView;

    // Header content containers
    private FrameLayout avatarContainer;
    private FrameLayout nameContainer;
    private FrameLayout onlineContainer;
    private LinearLayout headerButtonsContainer;

    // Collapsed title containers
    private LinearLayout collapsedTitleContainer;
    private FrameLayout collapsedNameContainer;
    private FrameLayout collapsedOnlineContainer;

    // Header elements
    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;
    private TextView nameTextView;
    private TextView onlineTextView;

    // Collapsed title elements
    private TextView collapsedNameTextView;
    private TextView collapsedOnlineTextView;
    private BubbleButton messageButton;
    private BubbleButton muteButton;
    private BubbleButton callButton;
    private BubbleButton videoButton;

    // Gift particle system
    private HeaderGiftParticleSystem giftParticleSystem;

    // Animation helpers for collapsing header
    private AvatarAnimationHelper avatarAnimationHelper;
    private TextTransitionHelper textTransitionHelper;
    private HeaderButtonsAnimationHelper buttonAnimationHelper;
    private HeaderScrollResponder scrollResponder;
    private CollapsingHeaderOffsetListener offsetListener;

    // Profile data
    private long dialogId;
    private long chatId;
    private long userId;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.UserFull userInfo;
    private TLRPC.ChatFull chatInfo;
    private boolean isMuted;
    private boolean canSearchMembers;

    // Menu items
    private ActionBarMenuItem otherItem;
    private ActionBarMenuItem qrItem;

    // Menu item constants (from ProfileActivity)
    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    private final static int leave_group = 7;
    private final static int invite_to_group = 9;
    private final static int share = 10;
    private final static int edit_channel = 12;
    private final static int add_shortcut = 14;
    private final static int call_item = 15;
    private final static int video_call_item = 16;
    private final static int search_members = 17;
    private final static int statistics = 19;
    private final static int start_secret_chat = 20;
    private final static int gallery_menu_save = 21;
    private final static int view_discussion = 22;
    private final static int delete_topic = 23;
    private final static int report = 24;
    private final static int edit_info = 30;
    private final static int logout = 31;
    private final static int set_as_main = 33;
    private final static int edit_avatar = 34;
    private final static int delete_avatar = 35;
    private final static int add_photo = 36;
    private final static int qr_button = 37;
    private final static int gift_premium = 38;
    private final static int channel_stories = 39;
    private final static int edit_color = 40;
    private final static int edit_profile = 41;
    private final static int copy_link_profile = 42;
    private final static int set_username = 43;
    private final static int bot_privacy = 44;

    // SharedMediaLayout for proper content integration
    private SharedMediaLayout sharedMediaLayout;
    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private FullProfileContentAdapter listAdapter;

    // Row tracking variables (complete set from ProfileActivity, excluding
    // notification rows)
    private int rowCount;

    // Profile Header & Avatar
    private int setAvatarRow = -1;
    private int setAvatarSectionRow = -1;
    private int channelRow = -1;
    private int channelDividerRow = -1;

    // Account Section
    private int numberSectionRow = -1;
    private int numberRow = -1;
    private int birthdayRow = -1;
    private int setUsernameRow = -1;
    private int bioRow = -1;

    // Suggestions
    private int phoneSuggestionSectionRow = -1;
    private int graceSuggestionRow = -1;
    private int graceSuggestionSectionRow = -1;
    private int phoneSuggestionRow = -1;
    private int passwordSuggestionSectionRow = -1;
    private int passwordSuggestionRow = -1;

    // Settings Sections
    private int settingsSectionRow = -1;
    private int settingsSectionRow2 = -1;
    // notificationRow excluded
    private int languageRow = -1;
    private int privacyRow = -1;
    private int dataRow = -1;
    private int chatRow = -1;
    private int filtersRow = -1;
    private int liteModeRow = -1;
    private int stickersRow = -1;
    private int devicesRow = -1;
    private int devicesSectionRow = -1;

    // Help Section
    private int helpHeaderRow = -1;
    private int questionRow = -1;
    private int faqRow = -1;
    private int policyRow = -1;
    private int helpSectionCell = -1;

    // Debug Section
    private int debugHeaderRow = -1;
    private int sendLogsRow = -1;
    private int sendLastLogsRow = -1;
    private int clearLogsRow = -1;
    private int switchBackendRow = -1;
    private int versionRow = -1;

    // Layout Elements
    private int emptyRow = -1;
    private int bottomPaddingRow = -1;

    // User Info Section
    private int infoHeaderRow = -1;
    private int phoneRow = -1;
    private int locationRow = -1;
    private int userInfoRow = -1;
    private int channelInfoRow = -1;
    private int usernameRow = -1;
    // notificationsDividerRow excluded
    // notificationsRow excluded
    private int bizHoursRow = -1;
    private int bizLocationRow = -1;
    // notificationsSimpleRow excluded
    private int infoStartRow = -1;
    private int infoEndRow = -1;
    private int infoSectionRow = -1;

    // Affiliate Program
    private int affiliateRow = -1;
    private int infoAffiliateRow = -1;

    // Actions
    private int sendMessageRow = -1;
    private int reportRow = -1;
    private int reportReactionRow = -1;
    private int reportDividerRow = -1;
    private int addToContactsRow = -1;
    private int addToGroupButtonRow = -1;
    private int addToGroupInfoRow = -1;

    // Premium Features
    private int premiumRow = -1;
    private int starsRow = -1;
    private int tonRow = -1;
    private int businessRow = -1;
    private int premiumGiftingRow = -1;
    private int premiumSectionsRow = -1;

    // Bot Features
    private int botAppRow = -1;
    private int botPermissionsHeader = -1;
    private int botPermissionLocation = -1;
    private int botPermissionEmojiStatus = -1;
    private int botPermissionBiometry = -1;
    private int botPermissionsDivider = -1;

    // Secret Chat Settings
    private int settingsTimerRow = -1;
    private int settingsKeyRow = -1;
    private int secretSettingsSectionRow = -1;

    // Members Section
    private int membersHeaderRow = -1;
    private int membersStartRow = -1;
    private int membersEndRow = -1;
    private int addMemberRow = -1;
    private int subscribersRow = -1;
    private int subscribersRequestsRow = -1;
    private int administratorsRow = -1;
    private int settingsRow = -1;

    // Balances
    private int botStarsBalanceRow = -1;
    private int botTonBalanceRow = -1;
    private int channelBalanceRow = -1;
    private int channelBalanceSectionRow = -1;
    private int balanceDividerRow = -1;
    private int blockedUsersRow = -1;
    private int membersSectionRow = -1;

    // Shared Media
    private int sharedMediaRow = -1;

    // Final Actions
    private int unblockRow = -1;
    private int joinRow = -1;
    private int lastSectionRow = -1;

    public ProfileNewActivity(Bundle args) {
        this(args, null);
    }

    public ProfileNewActivity(Bundle args, SharedMediaLayout.SharedMediaPreloader preloader) {
        super(args);
        sharedMediaPreloader = preloader;

        if (args != null) {
            dialogId = args.getLong("dialog_id", 0);
            chatId = args.getLong("chat_id", 0);
            userId = args.getLong("user_id", 0);
        }

        if (dialogId != 0) {
            if (dialogId > 0) {
                userId = dialogId;
            } else {
                chatId = -dialogId;
            }
        }

        if (userId != 0) {
            currentUser = MessagesController.getInstance(currentAccount).getUser(userId);
        } else if (chatId != 0) {
            currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        // Load user/chat data like ProfileActivity does
        if (userId != 0) {
            currentUser = getMessagesController().getUser(userId);
            userInfo = getMessagesController().getUserFull(userId);
            getMessagesController().loadFullUser(getMessagesController().getUser(userId), classGuid, true);
        } else if (chatId != 0) {
            currentChat = getMessagesController().getChat(chatId);
            if (chatInfo == null) {
                chatInfo = getMessagesController().getChatFull(chatId);
            }
            if (ChatObject.isChannel(currentChat)) {
                getMessagesController().loadFullChat(chatId, classGuid, true);
            }
        }

        // Initialize SharedMediaPreloader if null (copied from ProfileActivity)
        if (sharedMediaPreloader == null) {
            sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
        }
        sharedMediaPreloader.addDelegate(this);

        // Initialize animation helpers for collapsing header
        avatarAnimationHelper = new AvatarAnimationHelper();
        textTransitionHelper = new TextTransitionHelper();
        buttonAnimationHelper = new HeaderButtonsAnimationHelper();
        scrollResponder = new HeaderScrollResponder();
        offsetListener = new CollapsingHeaderOffsetListener();

        // Connect animation helpers
        scrollResponder.setAnimationHelpers(avatarAnimationHelper, textTransitionHelper);
        scrollResponder.setButtonAnimationHelper(buttonAnimationHelper);
        offsetListener.setAnimationHelpers(avatarAnimationHelper, textTransitionHelper, buttonAnimationHelper);
        offsetListener.setScrollResponder(scrollResponder);

        // Set status bar color callback for dynamic color changes
        offsetListener.setStatusBarColorCallback(this::setStatusBarColorForCollapse);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this,
                NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        // Cleanup SharedMedia components (copied from ProfileActivity)
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onDestroy();
        }
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(this);
            sharedMediaPreloader.removeDelegate(this);
        }

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this,
                NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    @Override
    public View createView(Context context) {
        // Configure transparent status bar for collapsing header
        configureTransparentStatusBar();

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false); // Disable overlay title
        actionBar.setTitle(""); // Remove title - we'll use translated text from header
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 20) {
                    // Handle QR code
                    showQRCode();
                } else if (id == block_contact) {
                    handleBlockContact();
                } else if (id == add_contact) {
                    handleAddContact();
                } else if (id == share_contact) {
                    handleShareContact();
                } else if (id == edit_contact) {
                    handleEditContact();
                } else if (id == delete_contact) {
                    handleDeleteContact();
                } else if (id == leave_group) {
                    handleLeaveGroup();
                } else if (id == edit_channel) {
                    handleEditChannel();
                } else if (id == edit_info) {
                    handleEditInfo();
                } else if (id == edit_profile) {
                    handleEditProfile();
                } else if (id == gift_premium) {
                    handleGiftPremium();
                } else if (id == start_secret_chat) {
                    handleStartSecretChat();
                } else if (id == bot_privacy) {
                    handleBotPrivacy();
                } else if (id == statistics) {
                    handleStatistics();
                } else if (id == search_members) {
                    handleSearchMembers();
                } else if (id == add_shortcut) {
                    handleAddShortcut();
                } else if (id == call_item || id == video_call_item) {
                    handleVoiceVideoCall(id == video_call_item);
                } else if (id == edit_color) {
                    handleEditColor();
                } else if (id == copy_link_profile) {
                    handleCopyProfileLink();
                } else if (id == set_username) {
                    handleSetUsername();
                } else if (id == logout) {
                    handleLogout();
                } else if (id == gallery_menu_save) {
                    handleGalleryMenuSave();
                } else if (id == set_as_main) {
                    handleSetAsMain();
                } else if (id == edit_avatar) {
                    handleEditAvatar();
                } else if (id == delete_avatar) {
                    handleDeleteAvatar();
                } else if (id == add_photo) {
                    handleAddPhoto();
                } else if (id == share) {
                    handleShare();
                } else if (id == report) {
                    handleReport();
                } else if (id == view_discussion) {
                    handleViewDiscussion();
                } else if (id == channel_stories) {
                    handleChannelStories();
                }
            }
        });

        // Create menu
        ActionBarMenu menu = actionBar.createMenu();
        qrItem = menu.addItem(20, R.drawable.msg_qr_mini);
        qrItem.setContentDescription(LocaleController.getString("GetQRCode", R.string.GetQRCode));
        otherItem = menu.addItem(10, R.drawable.ic_ab_other);

        // Create the action bar menu
        createActionBarMenu(false);

        // Mark SharedMediaLayout as attached for proper integration
        sharedMediaLayoutAttached = true;

        // Inflate the collapsing layout
        LayoutInflater inflater = LayoutInflater.from(context);
        coordinatorLayout = (CoordinatorLayout) inflater.inflate(R.layout.profile_collapsing_layout, null);

        // Get references to views
        appBarLayout = coordinatorLayout.findViewById(R.id.app_bar_layout);
        collapsingToolbarLayout = coordinatorLayout.findViewById(R.id.collapsing_toolbar_layout);
//        toolbar = coordinatorLayout.findViewById(R.id.collapsed_toolbar);

        // Get header containers
        avatarContainer = coordinatorLayout.findViewById(R.id.avatar_container);
        nameContainer = coordinatorLayout.findViewById(R.id.name_container);
        onlineContainer = coordinatorLayout.findViewById(R.id.online_container);
        headerButtonsContainer = coordinatorLayout.findViewById(R.id.header_buttons_container);

        // Get collapsed title containers
        collapsedTitleContainer = coordinatorLayout.findViewById(R.id.collapsed_title_container);
        collapsedNameContainer = coordinatorLayout.findViewById(R.id.collapsed_name_container);
        collapsedOnlineContainer = coordinatorLayout.findViewById(R.id.collapsed_online_container);

        // Setup toolbar
        setupToolbar();

        // Setup header content
        setupHeaderContent(context);

        // Setup content below header
        setupContentArea(context);

        // Setup collapsed title text views
        setupCollapsedTitles(context);

        // Setup scroll behavior for collapsing
        setupScrollBehavior();

        // Configure layout containers to prevent clipping during animations
        setupLayoutClipping();

        // Setup theme
        updateTheme();

        fragmentView = coordinatorLayout;
        return fragmentView;
    }

    // IMPORTANT FOR NEW PROFILE LAYOUT TO SET TRANSPARENCY HERE
    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context, getResourceProvider());
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);
        actionBar.setElevation(0);
        actionBar.setCastShadows(false);

        // Completely remove any shadow or bottom line
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            actionBar.setOutlineProvider(null);
        }

        // Remove any background drawable that might cause a line
        actionBar.setBackgroundDrawable(null);
        actionBar.setBackground(null);

        if (inPreviewMode || inBubbleMode) {
            actionBar.setOccupyStatusBar(false);
        }
        return actionBar;
    }

    private void setupToolbar() {
        // Set up toolbar navigation (back button) - use existing back button string
        // reference
//        toolbar.setNavigationOnClickListener(v -> finishFragment());
//
//        // Set up toolbar menu
//        Menu toolbarMenu = toolbar.getMenu();
//        toolbarMenu.add(0, 10, 0,
//                "").setIcon(R.drawable.ic_ab_other).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//
//        toolbar.setOnMenuItemClickListener(item -> {
//            if (item.getItemId() == 10) {
//                // The 3-dot menu is handled by the ActionBar menu system
//                return true;
//            }
//
//            return false;
//        });
//
//        // Apply complete toolbar transparency using multiple methods
//        // Method 1: Remove titles and backgrounds
//        toolbar.setTitle(""); // Remove any default title
//        toolbar.setBackgroundColor(Color.TRANSPARENT); // Make background transparent
//        toolbar.setBackgroundDrawable(null); // Remove any background drawable
//        toolbar.setBackground(null); // Ensure no background is set
//
//        // Method 2: Set transparent overlay
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            toolbar.setElevation(0f); // Remove elevation shadow
//            toolbar.setOutlineProvider(null); // Remove outline/shadow
//        }
//
//        // Method 3: Setup collapsing toolbar to be transparent with no titles
//        collapsingToolbarLayout.setTitle(""); // No title in collapsing toolbar
//        collapsingToolbarLayout.setCollapsedTitleTextColor(Color.TRANSPARENT); // Hide any title text
//        collapsingToolbarLayout.setExpandedTitleColor(Color.TRANSPARENT); // Hide expanded title
//        collapsingToolbarLayout.setContentScrimColor(Color.TRANSPARENT); // Transparent scrim
//        collapsingToolbarLayout.setStatusBarScrimColor(Color.TRANSPARENT); // Transparent status bar scrim
//        collapsingToolbarLayout.setBackgroundColor(Color.TRANSPARENT); // Make sure background is transparent
//        collapsingToolbarLayout.setBackground(null); // Remove any background drawable
//
//        // Remove any elevation or shadow from collapsing toolbar
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            collapsingToolbarLayout.setElevation(0f);
//            collapsingToolbarLayout.setOutlineProvider(null);
//        }
//
//        // Method 4: Set AppBar transparency
//        appBarLayout.setBackgroundColor(Color.TRANSPARENT);
//        appBarLayout.setBackground(null);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            appBarLayout.setElevation(0f); // Remove elevation shadow
//            appBarLayout.setOutlineProvider(null); // Remove outline/shadow
//        }

        // Method 5: Apply status bar transparency using multiple techniques from Stack
        // Overflow
        setupStatusBarTransparency();
    }

    private void setupHeaderContent(Context context) {
        // Create avatar using BackupImageView
        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarImageView.setRoundRadius(dp(42));
        avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.CENTER));

        // Initialize avatar drawable
        avatarDrawable = new AvatarDrawable();

        // Create name text view - identical settings to onlineTextView for consistent
        // animation behavior
        nameTextView = new TextView(context);
        nameTextView.setTextColor(Color.WHITE);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setGravity(Gravity.CENTER);
        // Use match_parent width to allow for translation animation space
        nameContainer.addView(nameTextView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Create online status text view
        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Color.WHITE);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setGravity(Gravity.CENTER);
        onlineTextView.setAlpha(0.8f);
        // Use match_parent width to match name container layout
        onlineContainer.addView(onlineTextView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // CRITICAL: Ensure all containers allow text to translate beyond their bounds
        setupContainerClippingForTranslation();

        // ADDITIONAL: Configure FrameLayouts to prevent internal text clipping
        setupFrameLayoutsForTranslation();

        // Create header buttons using provided vector drawable icons
        messageButton = new BubbleButton(context, R.drawable.profile_header_message,
                LocaleController.getString("SendMessage", R.string.SendMessage));
        muteButton = new BubbleButton(context, R.drawable.profile_header_unmute,
                LocaleController.getString("Unmute", R.string.Unmute));
        callButton = new BubbleButton(context, R.drawable.profile_header_call,
                LocaleController.getString("Call", R.string.Call));
        videoButton = new BubbleButton(context, R.drawable.profile_header_video,
                LocaleController.getString("VideoCall", R.string.VideoCall));

        // Set button click listeners
        messageButton.setOnClickListener(v -> onMessageButtonClick());
        muteButton.setOnClickListener(v -> onMuteButtonClick());
        callButton.setOnClickListener(v -> onCallButtonClick());
        videoButton.setOnClickListener(v -> onVideoCallButtonClick());

        // Add buttons to container
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        headerButtonsContainer.addView(messageButton, buttonParams);

        buttonParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        buttonParams.leftMargin = dp(8);
        headerButtonsContainer.addView(muteButton, buttonParams);

        buttonParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        buttonParams.leftMargin = dp(8);
        headerButtonsContainer.addView(callButton, buttonParams);

        buttonParams = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        buttonParams.leftMargin = dp(8);
        headerButtonsContainer.addView(videoButton, buttonParams);

        // Setup gift particle system
        // giftParticleSystem = new HeaderGiftParticleSystem(context, null);
        // avatarContainer.addView(giftParticleSystem,
        // LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
        // LayoutHelper.MATCH_PARENT));

        // Update header content
        updateHeaderContent();
    }

    private void setupContentArea(Context context) {
        // Create nested scroll view for content
        nestedScrollView = new NestedScrollView(context);
        nestedScrollView.setFillViewport(true);

        // Create content container with proper margins
        FrameLayout contentContainer = new FrameLayout(context);
        contentContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        contentContainer.setPadding(0, 0, 0, dp(80)); // Add bottom margin so bubble buttons are visible

        // Add content here (tabs, lists, etc.)
        createProfileContent(contentContainer, context);

        nestedScrollView.addView(contentContainer);

        // Find content placeholder and replace with nested scroll view
        FrameLayout contentPlaceholder = coordinatorLayout.findViewById(R.id.content_placeholder);
        ViewGroup parent = (ViewGroup) contentPlaceholder.getParent();
        int index = parent.indexOfChild(contentPlaceholder);
        parent.removeView(contentPlaceholder);
        parent.addView(nestedScrollView, index, contentPlaceholder.getLayoutParams());
    }

    private void createProfileContent(FrameLayout container, Context context) {
        // Create SharedMediaLayout first
        createSharedMediaLayout(context);

        // Create RecyclerListView with existing ProfileActivity content structure
        RecyclerListView listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        // Create adapter with profile content
        updateRowsIds();
        listAdapter = new FullProfileContentAdapter(context);
        listView.setAdapter(listAdapter);

        // Add scroll listener to handle header visibility changes
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkListViewScroll();
            }
        });

        // Add click handler that uses processOnClickOrPress logic
        listView.setOnItemClickListener((view, position, x, y) -> {
            processOnClickOrPress(position, view, x, y);
        });

        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void createSharedMediaLayout(Context context) {
        long did = dialogId;
        if (currentUser != null) {
            did = currentUser.id;
        } else if (currentChat != null) {
            did = -currentChat.id;
        }

        // Initialize with proper parameters like ProfileActivity
        int commonGroupsCount = userInfo != null ? userInfo.common_chats_count : 0;
        int initialTab = 0; // Start with photos/videos tab

        sharedMediaLayout = new SharedMediaLayout(context, did, sharedMediaPreloader, commonGroupsCount, null, chatInfo,
                userInfo, initialTab, this, this, SharedMediaLayout.VIEW_TYPE_PROFILE_ACTIVITY, getResourceProvider()) {
            @Override
            protected void onSelectedTabChanged() {
                // Handle tab change if needed
            }

            @Override
            protected boolean canShowSearchItem() {
                return true;
            }

            @Override
            protected void onSearchStateChanged(boolean expanded) {
                // Handle search state change if needed
            }
        };

        sharedMediaLayout.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
    }

    private void setupCollapsedTitles(Context context) {
        // Create collapsed name text view
        collapsedNameTextView = new TextView(context);
        collapsedNameTextView.setTextColor(Color.WHITE);
        collapsedNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        collapsedNameTextView.setTypeface(AndroidUtilities.bold());
        collapsedNameTextView.setGravity(Gravity.START);
        collapsedNameTextView.setAlpha(0.8f);
        collapsedNameContainer.addView(collapsedNameTextView,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));

        // Create collapsed online status text view
        collapsedOnlineTextView = new TextView(context);
        collapsedOnlineTextView.setTextColor(Color.WHITE);
        collapsedOnlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        collapsedOnlineTextView.setGravity(Gravity.START);
        collapsedOnlineTextView.setAlpha(0.8f);
        collapsedOnlineContainer.addView(collapsedOnlineTextView,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));
    }

    private void setupScrollBehavior() {
        // Set up views for the animation helpers
        offsetListener.setAnimatedViews(avatarImageView, nameContainer, collapsedTitleContainer,
                headerButtonsContainer);
        offsetListener.setTextViews(nameTextView, onlineTextView); // Pass individual text views
        textTransitionHelper.setTextViews(nameTextView, onlineTextView, collapsedNameTextView, collapsedOnlineTextView);

        // Set up button animation helper with bubble button views
        buttonAnimationHelper.setButtonViews(headerButtonsContainer, messageButton, muteButton, callButton,
                videoButton);
        buttonAnimationHelper.initializeLayout(AndroidUtilities.dp(320)); // Header height

        // Add our sophisticated animation listener to the AppBarLayout
        appBarLayout.addOnOffsetChangedListener(offsetListener);
    }

    private void setupLayoutClipping() {
        // Configure layout containers to prevent text and avatar clipping during
        // animations
        // This ensures smooth translation animations without content vanishing

        if (appBarLayout instanceof ViewGroup) {
            ((ViewGroup) appBarLayout).setClipChildren(false);
            ((ViewGroup) appBarLayout).setClipToPadding(false);
        }

        if (collapsingToolbarLayout instanceof ViewGroup) {
            ((ViewGroup) collapsingToolbarLayout).setClipChildren(false);
            ((ViewGroup) collapsingToolbarLayout).setClipToPadding(false);
        }

        // Find the expanded header container
        View expandedHeaderContainer = coordinatorLayout.findViewById(R.id.expanded_header_container);
        if (expandedHeaderContainer instanceof ViewGroup) {
            ((ViewGroup) expandedHeaderContainer).setClipChildren(false);
            ((ViewGroup) expandedHeaderContainer).setClipToPadding(false);
        }

        if (collapsedTitleContainer instanceof ViewGroup) {
            ((ViewGroup) collapsedTitleContainer).setClipChildren(false);
            ((ViewGroup) collapsedTitleContainer).setClipToPadding(false);
        }

//        if (toolbar instanceof ViewGroup) {
//            ((ViewGroup) toolbar).setClipChildren(false);
//            ((ViewGroup) toolbar).setClipToPadding(false);
//        }

        // Also configure the main coordinator layout to allow overflow
        if (coordinatorLayout instanceof ViewGroup) {
            coordinatorLayout.setClipChildren(false);
            coordinatorLayout.setClipToPadding(false);
        }

        // Configure text containers to prevent clipping during translation animations
        if (nameContainer instanceof ViewGroup) {
            ((ViewGroup) nameContainer).setClipChildren(false);
            ((ViewGroup) nameContainer).setClipToPadding(false);
        }

        if (onlineContainer instanceof ViewGroup) {
            ((ViewGroup) onlineContainer).setClipChildren(false);
            ((ViewGroup) onlineContainer).setClipToPadding(false);
        }

        if (avatarContainer instanceof ViewGroup) {
            ((ViewGroup) avatarContainer).setClipChildren(false);
            ((ViewGroup) avatarContainer).setClipToPadding(false);
        }
    }

    private void setupStatusBarTransparency() {
        // Apply status bar transparency using multiple research-backed techniques from
        // Stack Overflow

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Method 1: Set status bar color to transparent
            getParentActivity().getWindow().setStatusBarColor(Color.TRANSPARENT);

            // Method 2: Use FLAG_LAYOUT_NO_LIMITS for complete transparency
            getParentActivity().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            // Method 3: Set proper system UI visibility flags
            getParentActivity().getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // For KitKat, use translucent status bar
            getParentActivity().getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        // Method 4: Configure CoordinatorLayout for status bar transparency
        if (coordinatorLayout != null) {
            coordinatorLayout.setFitsSystemWindows(true);
            coordinatorLayout.setStatusBarBackground(null);
        }
    }

    private void updateRowsIds() {
        // Initialize all rows to -1
        setAvatarRow = setAvatarSectionRow = channelRow = channelDividerRow = numberSectionRow = numberRow = birthdayRow = setUsernameRow = bioRow = phoneSuggestionSectionRow = graceSuggestionRow = graceSuggestionSectionRow = phoneSuggestionRow = passwordSuggestionSectionRow = passwordSuggestionRow = settingsSectionRow = settingsSectionRow2 = languageRow = privacyRow = dataRow = chatRow = filtersRow = liteModeRow = stickersRow = devicesRow = devicesSectionRow = helpHeaderRow = questionRow = faqRow = policyRow = helpSectionCell = debugHeaderRow = sendLogsRow = sendLastLogsRow = clearLogsRow = switchBackendRow = versionRow = emptyRow = bottomPaddingRow = infoHeaderRow = phoneRow = locationRow = userInfoRow = channelInfoRow = usernameRow = bizHoursRow = bizLocationRow = infoStartRow = infoEndRow = infoSectionRow = affiliateRow = infoAffiliateRow = sendMessageRow = reportRow = reportReactionRow = reportDividerRow = addToContactsRow = addToGroupButtonRow = addToGroupInfoRow = premiumRow = starsRow = tonRow = businessRow = premiumGiftingRow = premiumSectionsRow = botAppRow = botPermissionsHeader = botPermissionLocation = botPermissionEmojiStatus = botPermissionBiometry = botPermissionsDivider = settingsTimerRow = settingsKeyRow = secretSettingsSectionRow = membersHeaderRow = membersStartRow = membersEndRow = addMemberRow = subscribersRow = subscribersRequestsRow = administratorsRow = settingsRow = botStarsBalanceRow = botTonBalanceRow = channelBalanceRow = channelBalanceSectionRow = balanceDividerRow = blockedUsersRow = membersSectionRow = sharedMediaRow = unblockRow = joinRow = lastSectionRow = -1;

        rowCount = 0;

        // Check if SharedMediaLayout has content (copied from ProfileActivity)
        boolean hasMedia = false;
        if (sharedMediaPreloader != null) {
            int[] lastMediaCount = sharedMediaPreloader.getLastMediaCount();
            for (int a = 0; a < lastMediaCount.length; a++) {
                if (lastMediaCount[a] > 0) {
                    hasMedia = true;
                    break;
                }
            }
            if (!hasMedia) {
                hasMedia = sharedMediaPreloader.hasSavedMessages;
            }
        }

        // Handle different profile types
        if (currentUser != null) {
            boolean isUserSelf = UserObject.isUserSelf(currentUser);

            if (isUserSelf) {
                // Self profile layout
                setAvatarRow = rowCount++;
                setAvatarSectionRow = rowCount++;

                numberSectionRow = rowCount++;
                numberRow = rowCount++;
                if (userInfo != null && userInfo.birthday != null) {
                    birthdayRow = rowCount++;
                }
                setUsernameRow = rowCount++;
                bioRow = rowCount++;

                // Settings sections
                settingsSectionRow = rowCount++;
                chatRow = rowCount++;
                privacyRow = rowCount++;
                dataRow = rowCount++;
                liteModeRow = rowCount++;
                filtersRow = rowCount++;
                devicesRow = rowCount++;
                devicesSectionRow = rowCount++;

                // Premium features
                premiumRow = rowCount++;
                starsRow = rowCount++;
                businessRow = rowCount++;
                premiumSectionsRow = rowCount++;

                // Help section
                helpHeaderRow = rowCount++;
                questionRow = rowCount++;
                faqRow = rowCount++;
                policyRow = rowCount++;
                helpSectionCell = rowCount++;

                if (BuildVars.LOGS_ENABLED) {
                    debugHeaderRow = rowCount++;
                    sendLogsRow = rowCount++;
                    sendLastLogsRow = rowCount++;
                    clearLogsRow = rowCount++;
                    if (BuildVars.DEBUG_VERSION) {
                        switchBackendRow = rowCount++;
                    }
                    versionRow = rowCount++;
                }

            } else {
                // Other user profile layout
                infoHeaderRow = rowCount++;

                if (!TextUtils.isEmpty(currentUser.phone)) {
                    phoneRow = rowCount++;
                }

                if (!TextUtils.isEmpty(currentUser.username)) {
                    usernameRow = rowCount++;
                }

                userInfoRow = rowCount++;

                if (userInfo != null && userInfo.birthday != null) {
                    birthdayRow = rowCount++;
                }

                // Business info if available
                // TODO: Add business hours and location checks

                infoSectionRow = rowCount++;

                // Action buttons
                if (!currentUser.bot) {
                    sendMessageRow = rowCount++;
                }

                // Contact actions
                boolean isContact = getContactsController().isContact(currentUser.id);
                if (isContact) {
                    // Contact options
                } else {
                    addToContactsRow = rowCount++;
                }

                reportDividerRow = rowCount++;
                reportRow = rowCount++;
            }

        } else if (currentChat != null) {
            // Chat/Channel profile layout
            channelInfoRow = rowCount++;

            if (chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation) {
                locationRow = rowCount++;
            }

            infoSectionRow = rowCount++;

            // Members section
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                subscribersRow = rowCount++;
                if (currentChat.admin_rights != null && currentChat.admin_rights.invite_users) {
                    subscribersRequestsRow = rowCount++;
                }
                administratorsRow = rowCount++;
            } else if (currentChat.megagroup) {
                membersHeaderRow = rowCount++;
                // TODO: Add member rows
                addMemberRow = rowCount++;
            }

            membersSectionRow = rowCount++;
        }

        // Add SharedMediaLayout if there's content or it's needed
        if (hasMedia || currentUser != null || currentChat != null) {
            sharedMediaRow = rowCount++;
        }

        // Add bottom padding if no shared media
        if (sharedMediaRow == -1) {
            bottomPaddingRow = rowCount++;
        }
    }

    private boolean processOnClickOrPress(final int position, final View view, final float x, final float y) {
        if (position == phoneRow && currentUser != null) {
            // Handle phone number click - show call/copy options
            try {
                VoIPHelper.startCall(currentUser, false, currentUser.id != 0, getParentActivity(), null,
                        getAccountInstance());
            } catch (Exception e) {
                FileLog.e(e);
            }
            return true;
        } else if (position == usernameRow && currentUser != null) {
            // Handle username click - show QR or copy
            showQRCode();
            return true;
        } else if (position == bioRow) {
            // Handle bio click - edit if self, otherwise do nothing
            if (currentUser != null && UserObject.isUserSelf(currentUser)) {
                presentFragment(new ChangeBioActivity());
            }
            return true;
        } else if (position == sendMessageRow) {
            // Open chat with user
            if (currentUser != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", currentUser.id);
                presentFragment(new ChatActivity(args), true);
            }
            return true;
        } else if (position == addToContactsRow) {
            // Add user to contacts
            if (currentUser != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", currentUser.id);
                args.putBoolean("addContact", true);
                presentFragment(new ContactAddActivity(args));
            }
            return true;
        } else if (position == reportRow) {
            // Report user/chat
            handleReport();
            return true;
        } else if (position == subscribersRow) {
            // Show subscribers list
            if (currentChat != null) {
                Bundle args = new Bundle();
                args.putLong("chat_id", currentChat.id);
                args.putInt("type", ChatUsersActivity.TYPE_USERS);
                presentFragment(new ChatUsersActivity(args));
            }
            return true;
        } else if (position == administratorsRow) {
            // Show administrators list
            if (currentChat != null) {
                Bundle args = new Bundle();
                args.putLong("chat_id", currentChat.id);
                args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
                presentFragment(new ChatUsersActivity(args));
            }
            return true;
        } else if (position == addMemberRow) {
            // Add member to group
            if (currentChat != null) {
                Bundle args = new Bundle();
                args.putBoolean("addToGroup", true);
                args.putLong("chatId", currentChat.id);
                presentFragment(new GroupCreateActivity(args), false);
            }
            return true;
        } else if (position == settingsRow) {
            // Open chat settings
            if (currentChat != null) {
                Bundle args = new Bundle();
                args.putLong("chat_id", currentChat.id);
                presentFragment(new ChatEditActivity(args));
            }
            return true;
        } else if (position == chatRow) {
            // Chat settings
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            return true;
        } else if (position == privacyRow) {
            // Privacy settings
            presentFragment(new PrivacySettingsActivity());
            return true;
        } else if (position == dataRow) {
            // Data settings
            presentFragment(new DataSettingsActivity());
            return true;
        } else if (position == liteModeRow) {
            // Lite mode settings
            presentFragment(new LiteModeSettingsActivity());
            return true;
        } else if (position == languageRow) {
            // Language settings
            presentFragment(new LanguageSelectActivity());
            return true;
        } else if (position == devicesRow) {
            // Devices settings
            presentFragment(new SessionsActivity(0));
            return true;
        } else if (position == questionRow) {
            // Ask a question
            showDialog(AlertsCreator.createSupportAlert(this, null));
            return true;
        } else if (position == faqRow) {
            // FAQ
            Browser.openUrl(getParentActivity(), LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl));
            return true;
        } else if (position == policyRow) {
            // Privacy policy
            Browser.openUrl(getParentActivity(),
                    LocaleController.getString("PrivacyPolicyUrl", R.string.PrivacyPolicyUrl));
            return true;
        } else if (position == sendLogsRow) {
            // Send logs
            sendLogs(false);
            return true;
        } else if (position == sendLastLogsRow) {
            // Send last logs
            sendLogs(true);
            return true;
        } else if (position == clearLogsRow) {
            // Clear logs
            FileLog.cleanupLogs();
            return true;
        } else if (position == switchBackendRow) {
            // Switch backend (debug only)
            if (BuildVars.DEBUG_VERSION) {
                // Toggle backend and restart
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                preferences.edit().putInt("dc" + currentAccount, ConnectionsManager.DEFAULT_DATACENTER_ID == 1 ? 3 : 1)
                        .apply();
                // Show restart dialog
            }
            return true;
        } else if (position == premiumRow) {
            // Premium settings
            presentFragment(new PremiumPreviewFragment("settings"));
            return true;
        } else if (position == starsRow) {
            // Stars
            presentFragment(new StarsIntroActivity());
            return true;
        } else if (position == businessRow) {
            // Business settings
            presentFragment(new BusinessLinksActivity());
            return true;
        }
        return false;
    }

    private void showQRCode() {
        if (currentUser != null && !TextUtils.isEmpty(currentUser.username)) {
            // Show QR code for user
            Bundle args = new Bundle();
            args.putLong("chat_id", userId);
            args.putString("username", currentUser.username);
            // presentFragment(new QrActivity(args)); // TODO: Implement QR activity
        }
    }

    private void sendLogs(boolean last) {
        // Send debug logs functionality
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                File logDir = AndroidUtilities.getLogsDir();
                if (logDir == null) {
                    return;
                }
                File[] files = logDir.listFiles();
                if (files == null || files.length == 0) {
                    return;
                }

                File shareFile = null;
                if (last) {
                    // Send only the last log file
                    long lastModified = 0;
                    for (File file : files) {
                        if (file.lastModified() > lastModified) {
                            lastModified = file.lastModified();
                            shareFile = file;
                        }
                    }
                } else {
                    // Send all logs in a ZIP file
                    // TODO: Implement ZIP creation and sharing
                    shareFile = files[files.length - 1]; // For now, just send the last file
                }

                if (shareFile != null) {
                    File finalFile = shareFile;
                    AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        try {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType("*/*");
                            intent.putExtra(Intent.EXTRA_STREAM,
                                    androidx.core.content.FileProvider.getUriForFile(getParentActivity(),
                                            ApplicationLoader.getApplicationId() + ".provider", finalFile));
                            getParentActivity().startActivity(Intent.createChooser(intent, "Send logs"));
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> progressDialog.dismiss());
            }
        });
    }

    // Full content adapter that mirrors ProfileActivity structure
    private class FullProfileContentAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_HEADER = 1,
                VIEW_TYPE_TEXT_DETAIL = 2,
                VIEW_TYPE_ABOUT_LINK = 3,
                VIEW_TYPE_TEXT = 4,
                VIEW_TYPE_DIVIDER = 5,
                VIEW_TYPE_NOTIFICATIONS_CHECK = 6,
                VIEW_TYPE_SHADOW = 7,
                VIEW_TYPE_USER = 8,
                VIEW_TYPE_EMPTY = 11,
                VIEW_TYPE_BOTTOM_PADDING = 12,
                VIEW_TYPE_SHARED_MEDIA = 13,
                VIEW_TYPE_VERSION = 14,
                VIEW_TYPE_SUGGESTION = 15,
                VIEW_TYPE_ADDTOGROUP_INFO = 17,
                VIEW_TYPE_PREMIUM_TEXT_CELL = 18,
                VIEW_TYPE_TEXT_DETAIL_MULTILINE = 19,
                VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE = 20,
                VIEW_TYPE_LOCATION = 21,
                VIEW_TYPE_HOURS = 22,
                VIEW_TYPE_CHANNEL = 23,
                VIEW_TYPE_STARS_TEXT_CELL = 24,
                VIEW_TYPE_BOT_APP = 25,
                VIEW_TYPE_SHADOW_TEXT = 26,
                VIEW_TYPE_COLORFUL_TEXT = 27;

        private Context context;

        public FullProfileContentAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == setAvatarRow || position == infoHeaderRow || position == helpHeaderRow ||
                    position == debugHeaderRow || position == membersHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == phoneRow || position == usernameRow || position == numberRow ||
                    position == birthdayRow || position == setUsernameRow || position == userInfoRow ||
                    position == locationRow || position == channelInfoRow) {
                return VIEW_TYPE_TEXT_DETAIL;
            } else if (position == bioRow) {
                return VIEW_TYPE_ABOUT_LINK;
            } else if (position == chatRow || position == privacyRow || position == dataRow ||
                    position == liteModeRow || position == filtersRow || position == devicesRow ||
                    position == languageRow || position == questionRow || position == faqRow ||
                    position == policyRow || position == premiumRow || position == starsRow ||
                    position == businessRow || position == sendMessageRow || position == addToContactsRow ||
                    position == reportRow || position == subscribersRow || position == administratorsRow ||
                    position == addMemberRow || position == sendLogsRow || position == sendLastLogsRow ||
                    position == clearLogsRow) {
                return VIEW_TYPE_TEXT;
            } else if (position == setAvatarSectionRow || position == numberSectionRow ||
                    position == settingsSectionRow || position == devicesSectionRow ||
                    position == helpSectionCell || position == infoSectionRow ||
                    position == reportDividerRow || position == membersSectionRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == bizHoursRow) {
                return VIEW_TYPE_HOURS;
            } else if (position == bizLocationRow) {
                return VIEW_TYPE_LOCATION;
            } else if (position == sharedMediaRow) {
                return VIEW_TYPE_SHARED_MEDIA;
            } else if (position == versionRow) {
                return VIEW_TYPE_VERSION;
            } else if (position == bottomPaddingRow) {
                return VIEW_TYPE_BOTTOM_PADDING;
            } else if (position == emptyRow) {
                return VIEW_TYPE_EMPTY;
            }
            return VIEW_TYPE_TEXT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_TEXT_DETAIL:
                    view = new TextDetailCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_ABOUT_LINK:
                    view = new AboutLinkCell(context, ProfileNewActivity.this);
                    break;
                case VIEW_TYPE_TEXT:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_DIVIDER:
                    view = new DividerCell(context);
                    break;
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(context);
                    break;
                case VIEW_TYPE_USER:
                    view = new UserCell(context, 61, 0, false);
                    break;
                case VIEW_TYPE_SHARED_MEDIA:
                    if (sharedMediaLayout != null && sharedMediaLayout.getParent() == null) {
                        view = sharedMediaLayout;
                    } else {
                        view = new FrameLayout(context) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec,
                                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
                            }
                        };
                        view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    }
                    break;
                case VIEW_TYPE_EMPTY:
                    view = new View(context);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_BOTTOM_PADDING:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec,
                                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
                        }
                    };
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_VERSION:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_HOURS:
                    view = new ProfileHoursCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_LOCATION:
                    view = new ProfileLocationCell(context, getResourceProvider());
                    break;
                default:
                    view = new TextCell(context, getResourceProvider());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder == null || holder.itemView == null)
                return;

            View view = holder.itemView;

            // Handle different row types with complete ProfileActivity binding logic
            if (position == setAvatarRow) {
                HeaderCell cell = (HeaderCell) view;
                cell.setText(LocaleController.getString("SetProfilePhoto", R.string.SetProfilePhoto));
            } else if (position == infoHeaderRow) {
                HeaderCell cell = (HeaderCell) view;
                cell.setText(LocaleController.getString("Info", R.string.Info));
            } else if (position == helpHeaderRow) {
                HeaderCell cell = (HeaderCell) view;
                cell.setText(LocaleController.getString("SettingsHelp", R.string.SettingsHelp));
            } else if (position == debugHeaderRow) {
                HeaderCell cell = (HeaderCell) view;
                cell.setText("Debug");
            } else if (position == membersHeaderRow) {
                HeaderCell cell = (HeaderCell) view;
                cell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
            } else if (position == phoneRow && currentUser != null && !TextUtils.isEmpty(currentUser.phone)) {
                TextDetailCell cell = (TextDetailCell) view;
                cell.setTextAndValue(LocaleController.getString("PhoneMobile", R.string.PhoneMobile),
                        "+" + currentUser.phone, false);
            } else if (position == usernameRow && currentUser != null && !TextUtils.isEmpty(currentUser.username)) {
                TextDetailCell cell = (TextDetailCell) view;
                cell.setTextAndValue(LocaleController.getString("Username", R.string.Username),
                        "@" + currentUser.username, false);

                // Add QR code icon like in ProfileActivity
                try {
                    Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_qr_mini);
                    if (drawable != null) {
                        drawable.setColorFilter(new PorterDuffColorFilter(
                                getThemedColor(Theme.key_switch2TrackChecked), PorterDuff.Mode.MULTIPLY));
                        cell.setImage(drawable, LocaleController.getString("GetQRCode", R.string.GetQRCode));
                        cell.setImageClickListener(v -> showQRCode());
                    }
                } catch (Exception e) {
                    // Ignore QR icon setup errors
                }
            } else if (position == numberRow && currentUser != null) {
                TextDetailCell cell = (TextDetailCell) view;
                cell.setTextAndValue(LocaleController.getString("Phone", R.string.Phone),
                        !TextUtils.isEmpty(currentUser.phone) ? "+" + currentUser.phone : "Unknown", false);
            } else if (position == birthdayRow && userInfo != null && userInfo.birthday != null) {
                TextDetailCell cell = (TextDetailCell) view;
                String birthdayText = formatBirthday(userInfo.birthday);
                cell.setTextAndValue(LocaleController.getString("ContactBirthday", R.string.ContactBirthday),
                        birthdayText, false);
            } else if (position == userInfoRow && currentUser != null) {
                TextDetailCell cell = (TextDetailCell) view;
                String about = userInfo != null && !TextUtils.isEmpty(userInfo.about) ? userInfo.about : "";
                cell.setTextAndValue(LocaleController.getString("UserBio", R.string.UserBio), about, false);
            } else if (position == locationRow && chatInfo != null
                    && chatInfo.location instanceof TLRPC.TL_channelLocation) {
                TextDetailCell cell = (TextDetailCell) view;
                TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                String locationText = formatLocation(location);
                cell.setTextAndValue(LocaleController.getString("AttachLocation", R.string.AttachLocation),
                        locationText, false);
            } else if (position == channelInfoRow && currentChat != null) {
                TextDetailCell cell = (TextDetailCell) view;
                String about = chatInfo != null && !TextUtils.isEmpty(chatInfo.about) ? chatInfo.about : "";
                cell.setTextAndValue(LocaleController.getString("Info", R.string.Info), about, false);
            } else if (position == bioRow) {
                AboutLinkCell cell = (AboutLinkCell) view;
                String bio = "";
                if (currentUser != null && userInfo != null && !TextUtils.isEmpty(userInfo.about)) {
                    bio = userInfo.about;
                } else if (currentChat != null && chatInfo != null && !TextUtils.isEmpty(chatInfo.about)) {
                    bio = chatInfo.about;
                }
                cell.setText(bio, false);
            } else if (position == chatRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("ChatSettings", R.string.ChatSettings),
                        R.drawable.msg_settings, true);
            } else if (position == privacyRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("PrivacySettings", R.string.PrivacySettings),
                        R.drawable.msg_secret, true);
            } else if (position == dataRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("DataSettings", R.string.DataSettings),
                        R.drawable.msg_settings, true);
            } else if (position == liteModeRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("LiteMode", R.string.LiteMode), R.drawable.msg_settings,
                        true);
            } else if (position == languageRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("Language", R.string.Language), R.drawable.msg_language,
                        true);
            } else if (position == devicesRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("Devices", R.string.Devices), R.drawable.msg_settings,
                        true);
            } else if (position == questionRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("AskAQuestion", R.string.AskAQuestion),
                        R.drawable.msg_settings, true);
            } else if (position == faqRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ),
                        R.drawable.msg_settings, true);
            } else if (position == policyRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("PrivacyPolicy", R.string.PrivacyPolicy),
                        R.drawable.msg_policy, true);
            } else if (position == premiumRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("TelegramPremium", R.string.TelegramPremium),
                        R.drawable.msg_settings, true);
            } else if (position == starsRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("TelegramStars", R.string.TelegramStars),
                        R.drawable.menu_premium_main, true);
            } else if (position == businessRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("TelegramBusiness", R.string.TelegramBusiness),
                        R.drawable.menu_premium_main, true);
            } else if (position == sendMessageRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("SendMessage", R.string.SendMessage),
                        R.drawable.msg_message, true);
            } else if (position == addToContactsRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("AddContact", R.string.AddContact),
                        R.drawable.msg_addcontact, true);
            } else if (position == reportRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("ReportChat", R.string.ReportChat),
                        R.drawable.msg_report, true);
            } else if (position == subscribersRow) {
                TextCell cell = (TextCell) view;
                int count = chatInfo != null ? chatInfo.participants_count
                        : currentChat != null ? currentChat.participants_count : 0;
                cell.setTextAndValueAndIcon(
                        LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers),
                        String.valueOf(count), R.drawable.msg_groups, true);
            } else if (position == administratorsRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators),
                        R.drawable.msg_settings, true);
            } else if (position == addMemberRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndIcon(LocaleController.getString("AddMember", R.string.AddMember),
                        R.drawable.msg_contact_add, true);
            } else if (position == sendLogsRow) {
                TextCell cell = (TextCell) view;
                cell.setText("Send Logs", false);
            } else if (position == sendLastLogsRow) {
                TextCell cell = (TextCell) view;
                cell.setText("Send Last Logs", false);
            } else if (position == clearLogsRow) {
                TextCell cell = (TextCell) view;
                cell.setText("Clear Logs", false);
            } else if (position == versionRow) {
                TextCell cell = (TextCell) view;
                cell.setTextAndValue("Version", "Telegram " + BuildVars.BUILD_VERSION_STRING, false);
            } else if (position == sharedMediaRow) {
                // SharedMediaLayout is self-contained and doesn't need binding
                // The view is either SharedMediaLayout or a placeholder FrameLayout
            } else if (position == bizHoursRow && chatInfo != null) {
                ProfileHoursCell cell = (ProfileHoursCell) view;
                // TODO: Bind business hours data when available
                // cell.setBusinessHours(chatInfo.business_hours);
            } else if (position == bizLocationRow && chatInfo != null) {
                ProfileLocationCell cell = (ProfileLocationCell) view;
                if (chatInfo.location instanceof TLRPC.TL_channelLocation) {
                    TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatInfo.location;
                    // cell.setLocation(location);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            return viewType == VIEW_TYPE_TEXT_DETAIL ||
                    viewType == VIEW_TYPE_SHARED_MEDIA;
        }
    }

    private void updateHeaderContent() {
        String name = "";
        String onlineStatus = "";

        if (currentUser != null) {
            // Update user profile using helper methods
            name = UserObject.getUserName(currentUser);
            onlineStatus = formatUserStatus(currentUser);

            nameTextView.setText(name);
            onlineTextView.setText(onlineStatus);

            // Update text transition helper with new text
            if (textTransitionHelper != null) {
                textTransitionHelper.setText(name, onlineStatus);
            }

            // Set up avatar drawable and load avatar image using ProfileActivity patterns
            avatarDrawable.setInfo(currentAccount, currentUser);
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_BIG);
            ImageLocation thumbLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_SMALL);

            if (imageLocation != null) {
                avatarImageView.setImage(imageLocation, "150_150", thumbLocation, "50_50", avatarDrawable, currentUser);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }

            // Update avatar click handler for photo viewing
            avatarImageView.setOnClickListener(v -> openAvatar());

            // Update button visibility for user profiles
            messageButton.setVisibility(View.VISIBLE);
            muteButton.setVisibility(View.VISIBLE);
            callButton.setVisibility(
                    !currentUser.bot && currentUser.phone != null && !currentUser.phone.isEmpty() ? View.VISIBLE
                            : View.GONE);
            videoButton.setVisibility(
                    !currentUser.bot && currentUser.phone != null && !currentUser.phone.isEmpty() ? View.VISIBLE
                            : View.GONE);

        } else if (currentChat != null) {
            // Update chat profile using helper methods
            name = currentChat.title;
            onlineStatus = formatChatStatus(currentChat);

            nameTextView.setText(name);
            onlineTextView.setText(onlineStatus);

            // Update text transition helper with new text
            if (textTransitionHelper != null) {
                textTransitionHelper.setText(name, onlineStatus);
            }

            // Set up avatar drawable and load chat avatar image
            avatarDrawable.setInfo(currentAccount, currentChat);
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_BIG);
            ImageLocation thumbLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_SMALL);

            if (imageLocation != null) {
                avatarImageView.setImage(imageLocation, "150_150", thumbLocation, "50_50", avatarDrawable, currentChat);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }

            // Update avatar click handler for photo viewing
            avatarImageView.setOnClickListener(v -> openAvatar());

            // Update button visibility for chat profiles
            messageButton.setVisibility(View.VISIBLE);
            muteButton.setVisibility(View.VISIBLE);
            callButton.setVisibility(View.GONE);
            videoButton.setVisibility(View.GONE);
        }

        // Update collapsed title text views with the same content
        if (collapsedNameTextView != null) {
            collapsedNameTextView.setText(name);
        }
        if (collapsedOnlineTextView != null) {
            collapsedOnlineTextView.setText(onlineStatus);
        }

        // Update mute button state
        updateMuteButton();

        // Update stories display
        updateStoriesDisplay();

        // Update premium status
        updatePremiumStatus();
    }

    private void updateMuteButton() {
        isMuted = getMessagesController().isDialogMuted(dialogId, 0);
        if (isMuted) {
            muteButton.updateIcon(R.drawable.profile_header_unmute);
            muteButton.updateText(LocaleController.getString("Unmute", R.string.Unmute));
        } else {
            muteButton.updateIcon(R.drawable.profile_header_mute);
            muteButton.updateText(LocaleController.getString("Mute", R.string.Mute));
        }
    }

    private void updateTheme() {
        // Update theme colors - use the same color for both header and toolbar
        int headerColor = getThemedColor(Theme.key_avatar_backgroundActionBarBlue);
        appBarLayout.setBackgroundColor(headerColor);
    }

    // Button click handlers
    private void onMessageButtonClick() {
        Bundle args = new Bundle();
        if (currentUser != null) {
            args.putLong("user_id", currentUser.id);
        } else if (currentChat != null) {
            args.putLong("chat_id", currentChat.id);
        }
        presentFragment(new ChatActivity(args));
    }

    private void onMuteButtonClick() {
        boolean muted = getMessagesController().isDialogMuted(dialogId, 0);
        getNotificationsController().muteDialog(dialogId, 0, !muted);
        if (fragmentView != null) {
            BulletinFactory.createMuteBulletin(ProfileNewActivity.this, !muted, null).show();
        }
        updateMuteButton();
    }

    private void onCallButtonClick() {
        if (currentUser != null && !currentUser.bot) {
            VoIPHelper.startCall(currentUser, false,
                    currentUser.id != 0,
                    getParentActivity(), null, getAccountInstance());
        }
    }

    private void onVideoCallButtonClick() {
        if (currentUser != null && !currentUser.bot) {
            VoIPHelper.startCall(currentUser, true,
                    currentUser.id != 0,
                    getParentActivity(), null, getAccountInstance());
        }
    }

    private void createActionBarMenu(boolean animated) {
        if (otherItem == null) {
            return;
        }

        otherItem.removeAllSubItems();

        if (currentUser != null) {
            if (UserObject.isUserSelf(currentUser)) {
                // Self user menu
                otherItem.addSubItem(edit_info, R.drawable.msg_edit,
                        LocaleController.getString("EditInfo", R.string.EditInfo));
                otherItem.addSubItem(add_photo, R.drawable.msg_addphoto,
                        LocaleController.getString("AddPhoto", R.string.AddPhoto));
                otherItem.addSubItem(edit_color, R.drawable.menu_profile_colors,
                        LocaleController.getString("ProfileColorEdit", R.string.ProfileColorEdit));
                otherItem.addSubItem(set_username, R.drawable.menu_username_change,
                        LocaleController.getString("ProfileUsernameEdit", R.string.ProfileUsernameEdit));
                otherItem.addSubItem(copy_link_profile, R.drawable.msg_link2,
                        LocaleController.getString("ProfileCopyLink", R.string.ProfileCopyLink));
                otherItem.addSubItem(logout, R.drawable.msg_leave,
                        LocaleController.getString("LogOut", R.string.LogOut));
            } else {
                // Other user menu
                boolean isBot = currentUser.bot;
                boolean userBlocked = getMessagesController().blockePeers.indexOfKey(currentUser.id) >= 0;

                if (isBot) {
                    otherItem.addSubItem(share, R.drawable.msg_share,
                            LocaleController.getString("BotShare", R.string.BotShare));
                    otherItem.addSubItem(bot_privacy, R.drawable.menu_privacy_policy,
                            LocaleController.getString("BotPrivacyPolicy", R.string.BotPrivacyPolicy));
                    otherItem.addSubItem(report, R.drawable.msg_report,
                            LocaleController.getString("ReportBot", R.string.ReportBot));
                    if (userBlocked) {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block,
                                LocaleController.getString("Unblock", R.string.Unblock));
                    } else {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block2,
                                LocaleController.getString("DeleteAndBlock", R.string.DeleteAndBlock));
                    }
                } else {
                    // Regular user menu
                    boolean isContact = getContactsController().isContact(currentUser.id);

                    if (!isContact) {
                        otherItem.addSubItem(add_contact, R.drawable.msg_addcontact,
                                LocaleController.getString("AddContact", R.string.AddContact));
                    } else {
                        otherItem.addSubItem(edit_contact, R.drawable.msg_edit,
                                LocaleController.getString("EditContact", R.string.EditContact));
                        otherItem.addSubItem(delete_contact, R.drawable.msg_delete,
                                LocaleController.getString("DeleteContact", R.string.DeleteContact));
                    }

                    otherItem.addSubItem(share_contact, R.drawable.msg_share,
                            LocaleController.getString("ShareContact", R.string.ShareContact));

                    if (userBlocked) {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block,
                                LocaleController.getString("Unblock", R.string.Unblock));
                    } else {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block,
                                LocaleController.getString("BlockContact", R.string.BlockContact));
                    }

                    if (!UserObject.isDeleted(currentUser)) {
                        otherItem.addSubItem(gift_premium, R.drawable.msg_gift_premium,
                                LocaleController.getString("ProfileSendAGift", R.string.ProfileSendAGift));
                        otherItem.addSubItem(start_secret_chat, R.drawable.msg_secret,
                                LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat));
                    }

                    otherItem.addSubItem(report, R.drawable.msg_report,
                            LocaleController.getString("ReportChat", R.string.ReportChat));
                }

                otherItem.addSubItem(add_shortcut, R.drawable.msg_link,
                        LocaleController.getString("AddShortcut", R.string.AddShortcut));
            }
        } else if (currentChat != null) {
            // Chat menu
            if (ChatObject.isChannel(currentChat)) {
                // Channel menu
                if (ChatObject.canManageCalls(currentChat)) {
                    otherItem.addSubItem(call_item, R.drawable.msg_voicechat,
                            LocaleController.getString("StartVoipChannel", R.string.StartVoipChannel));
                }

                if (ChatObject.hasAdminRights(currentChat)) {
                    otherItem.addSubItem(statistics, R.drawable.msg_stats,
                            LocaleController.getString("Statistics", R.string.Statistics));
                    otherItem.addSubItem(edit_channel, R.drawable.msg_edit,
                            LocaleController.getString("EditAdminRights", R.string.EditAdminRights));
                }

                otherItem.addSubItem(search_members, R.drawable.msg_search,
                        LocaleController.getString("SearchMembers", R.string.SearchMembers));
                otherItem.addSubItem(leave_group, R.drawable.msg_leave,
                        LocaleController.getString("LeaveChannelMenu", R.string.LeaveChannelMenu));
                otherItem.addSubItem(share, R.drawable.msg_share,
                        LocaleController.getString("BotShare", R.string.BotShare));

                // Note: linked_chat_id might not be available, using a simple check
                // if (currentChat.linked_chat_id != 0) {
                // otherItem.addSubItem(view_discussion, R.drawable.msg_discussion,
                // LocaleController.getString("ViewDiscussion", R.string.ViewDiscussion));
                // }

                otherItem.addSubItem(channel_stories, R.drawable.msg_archive,
                        LocaleController.getString("OpenChannelArchiveStories", R.string.OpenChannelArchiveStories));
            } else {
                // Group menu
                if (ChatObject.canManageCalls(currentChat)) {
                    otherItem.addSubItem(call_item, R.drawable.msg_voicechat,
                            LocaleController.getString("StartVoipChat", R.string.StartVoipChat));
                }

                otherItem.addSubItem(search_members, R.drawable.msg_search,
                        LocaleController.getString("SearchMembers", R.string.SearchMembers));
                otherItem.addSubItem(leave_group, R.drawable.msg_leave,
                        LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));

                if (ChatObject.hasAdminRights(currentChat)) {
                    otherItem.addSubItem(edit_channel, R.drawable.msg_edit,
                            LocaleController.getString("EditAdminRights", R.string.EditAdminRights));
                }
            }

            otherItem.addSubItem(add_shortcut, R.drawable.msg_link,
                    LocaleController.getString("AddShortcut", R.string.AddShortcut));
        }
    }

    // Menu handler methods
    private void handleBlockContact() {
        if (currentUser == null)
            return;

        boolean userBlocked = getMessagesController().blockePeers.indexOfKey(currentUser.id) >= 0;
        if (userBlocked) {
            getMessagesController().unblockPeer(currentUser.id);
            BulletinFactory.createBanBulletin(this, false).show();
        } else {
            getMessagesController().blockPeer(currentUser.id);
            BulletinFactory.createBanBulletin(this, true).show();
        }
        createActionBarMenu(true);
    }

    private void handleAddContact() {
        if (currentUser == null)
            return;
        Bundle args = new Bundle();
        args.putLong("user_id", currentUser.id);
        args.putBoolean("addContact", true);
        presentFragment(new ContactAddActivity(args));
    }

    private void handleShareContact() {
        if (currentUser == null)
            return;
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity fragment1, ArrayList<MessagesStorage.TopicKey> dids,
                    CharSequence message, boolean param, boolean notify, int scheduleDate,
                    TopicsFragment topicsFragment) {
                // Handle sharing contact to selected dialogs
                return true;
            }
        });
        presentFragment(fragment);
    }

    private void handleEditContact() {
        if (currentUser == null)
            return;
        Bundle args = new Bundle();
        args.putLong("user_id", currentUser.id);
        presentFragment(new ContactAddActivity(args));
    }

    private void handleDeleteContact() {
        if (currentUser == null)
            return;

        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(currentUser);

        getContactsController().deleteContact(users, false);
        createActionBarMenu(true);
    }

    private void handleLeaveGroup() {
        if (currentChat == null)
            return;
        // TODO: Implement leave group functionality
        // This would typically show a confirmation dialog and then leave the chat
    }

    private void handleEditChannel() {
        if (currentChat == null)
            return;
        Bundle args = new Bundle();
        args.putLong("chat_id", currentChat.id);
        // presentFragment(new ChatEditActivity(args)); // TODO: Add proper import
    }

    private void handleEditInfo() {
        // presentFragment(new UserInfoActivity()); // TODO: Add proper import
    }

    private void handleEditProfile() {
        // presentFragment(new UserInfoActivity()); // TODO: Add proper import
    }

    private void handleGiftPremium() {
        if (currentUser == null)
            return;
        // showDialog(new GiftSheet(getContext(), currentAccount, currentUser.id, null,
        // null)); // TODO: Add proper import
    }

    private void handleStartSecretChat() {
        if (currentUser == null)
            return;
        // TODO: Implement secret chat creation
    }

    private void handleBotPrivacy() {
        if (currentUser == null)
            return;
        // BotWebViewAttachedSheet.openPrivacy(currentAccount, currentUser.id); // TODO:
        // Add proper import
    }

    private void handleStatistics() {
        if (currentChat == null)
            return;
        // presentFragment(StatisticActivity.create(currentChat, false)); // TODO: Add
        // proper import
    }

    private void handleSearchMembers() {
        if (currentChat == null)
            return;
        Bundle args = new Bundle();
        args.putLong("chat_id", currentChat.id);
        // presentFragment(new ChatUsersActivity(args)); // Already imported
        presentFragment(new ChatUsersActivity(args));
    }

    private void handleAddShortcut() {
        // TODO: Implement add shortcut functionality
    }

    private void handleVoiceVideoCall(boolean isVideo) {
        if (currentUser != null && !currentUser.bot) {
            VoIPHelper.startCall(currentUser, isVideo,
                    currentUser.id != 0,
                    getParentActivity(), null, getAccountInstance());
        } else if (currentChat != null) {
            // Handle group calls
            // VoIPHelper.showGroupCallAlert(this, currentChat, null, false,
            // getAccountInstance()); // TODO: Add proper import
        }
    }

    private void handleEditColor() {
        // presentFragment(new
        // PeerColorActivity(0).startOnProfile().setOnApplied(this)); // TODO: Add
        // proper import
    }

    private void handleCopyProfileLink() {
        if (currentUser != null && !TextUtils.isEmpty(currentUser.username)) {
            String link = "https://t.me/" + currentUser.username;
            AndroidUtilities.addToClipboard(link);
            BulletinFactory.createCopyLinkBulletin(this).show();
        }
    }

    private void handleSetUsername() {
        // presentFragment(new ChangeUsernameActivity()); // TODO: Add proper import
    }

    private void handleLogout() {
        // presentFragment(new LogoutActivity()); // TODO: Add proper import
    }

    private void handleGalleryMenuSave() {
        // TODO: Implement save to gallery functionality
    }

    private void handleSetAsMain() {
        // TODO: Implement set as main avatar functionality
    }

    private void handleEditAvatar() {
        // TODO: Implement edit avatar functionality
    }

    private void handleDeleteAvatar() {
        // TODO: Implement delete avatar functionality
    }

    private void handleAddPhoto() {
        // TODO: Implement add photo functionality (camera/gallery picker)
    }

    private void handleShare() {
        if (currentUser != null) {
            String shareText = "https://t.me/" + (currentUser.username != null ? currentUser.username : "");
            if (!TextUtils.isEmpty(shareText)) {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
                DialogsActivity fragment = new DialogsActivity(args);
                presentFragment(fragment);
            }
        } else if (currentChat != null) {
            // Handle chat sharing
            String shareText = "https://t.me/" + (currentChat.username != null ? currentChat.username : "");
            if (!TextUtils.isEmpty(shareText)) {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
                DialogsActivity fragment = new DialogsActivity(args);
                presentFragment(fragment);
            }
        }
    }

    private void handleReport() {
        if (currentUser != null) {
            // TODO: Implement user reporting
        } else if (currentChat != null) {
            // TODO: Implement chat reporting
        }
    }

    private void handleViewDiscussion() {
        if (currentChat != null) {
            // Note: linked_chat_id field might not be available in current TLRPC version
            // Bundle args = new Bundle();
            // args.putLong("chat_id", currentChat.linked_chat_id);
            // presentFragment(new ChatActivity(args));
        }
    }

    private void handleChannelStories() {
        if (currentChat == null)
            return;
        // TODO: Implement channel stories functionality
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateHeaderContent();
            }
        } else if (id == NotificationCenter.contactsDidLoad) {
            updateHeaderContent();
            createActionBarMenu(false);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateMuteButton();
        } else if (id == NotificationCenter.userInfoDidLoad) {
            Long uid = (Long) args[0];
            if (currentUser != null && uid == currentUser.id) {
                userInfo = (TLRPC.UserFull) args[1];
                updateRowsIds();
                updateHeaderContent();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            Long chatId = (Long) args[0];
            if (currentChat != null && chatId == currentChat.id) {
                chatInfo = (TLRPC.ChatFull) args[1];
                updateRowsIds();
                updateHeaderContent();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    // Static factory method for compatibility
    public static ProfileNewActivity of(long dialogId) {
        Bundle args = new Bundle();
        args.putLong("dialog_id", dialogId);
        return new ProfileNewActivity(args);
    }

    // Method for compatibility with ProfileActivity animations
    public void setPlayProfileAnimation(int animationType) {
        // Handle profile animation states if needed
    }

    // Method for compatibility with ProfileActivity photo expansion
    public void setExpandPhoto(boolean expand) {
        // Handle photo expansion if needed
    }

    // Additional compatibility methods
    public void setUserInfo(Object userInfo, Object channelMessageFetcher, Object birthdayAssetsFetcher) {
        // Handle user info setting for compatibility
    }

    public void setChatInfo(Object chatInfo) {
        // Handle chat info setting for compatibility
    }

    public boolean myProfile = false; // Compatibility field
    public boolean saved = false; // Compatibility field

    // Additional compatibility methods
    public long getDialogId() {
        return dialogId;
    }

    public long getTopicId() {
        return 0; // Default topic ID
    }

    public UndoView getUndoView() {
        return null; // Placeholder for undo view
    }

    public void prepareBlurBitmap() {
        // Placeholder for blur bitmap preparation
    }

    public boolean isSettings() {
        return false; // Not a settings profile
    }

    public boolean isChat() {
        return chatId != 0; // True if this is a chat profile
    }

    public static void sendLogs(Object activity, boolean debug) {
        // Placeholder for log sending functionality
    }

    // Additional compatibility methods for BackButtonMenu and other components
    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    public UserInfoWrapper getUserInfo() {
        if (currentUser == null)
            return null;
        return new UserInfoWrapper(currentUser);
    }

    // Simple wrapper class to match BackButtonMenu expectations
    public static class UserInfoWrapper {
        public TLRPC.User user;

        public UserInfoWrapper(TLRPC.User user) {
            this.user = user;
        }
    }

    // Essential helper methods copied from ProfileActivity
    private void checkListViewScroll() {
        // Get the RecyclerView from the content placeholder container
        RecyclerView listView = null;
        if (coordinatorLayout != null && coordinatorLayout.getChildCount() > 1) {
            FrameLayout contentContainer = (FrameLayout) coordinatorLayout.findViewById(R.id.content_placeholder);
            if (contentContainer != null && contentContainer.getChildCount() > 0) {
                View child = contentContainer.getChildAt(0);
                if (child instanceof RecyclerView) {
                    listView = (RecyclerView) child;
                }
            }
        }

        if (listView == null || listView.getVisibility() != View.VISIBLE) {
            return;
        }
        if (sharedMediaLayout != null && sharedMediaLayoutAttached) {
            sharedMediaLayout.setVisibleHeight(listView.getMeasuredHeight() - sharedMediaLayout.getTop());
        }

        if (listView.getChildCount() <= 0) {
            return;
        }

        // Check scroll position and update header visibility
        RecyclerView.ViewHolder firstHolder = listView.findViewHolderForAdapterPosition(0);
        if (firstHolder != null) {
            int top = firstHolder.itemView.getTop();
            boolean mediaHeaderVisible = sharedMediaRow != -1 && top <= 0;
            setMediaHeaderVisible(mediaHeaderVisible);
        }
    }

    private void setMediaHeaderVisible(boolean visible) {
        if (mediaHeaderVisible == visible) {
            return;
        }
        mediaHeaderVisible = visible;

        // Update toolbar menu items visibility based on media header state
        if (otherItem != null) {
            otherItem.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
    }

    private void updateAvatarRoundRadius() {
        if (avatarImageView != null) {
            // Update avatar corner radius - for Material Design, we keep it circular
            avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        }
    }

    private int getSmallAvatarRoundRadius() {
        // For Material Design collapsing header, we maintain circular avatars
        return AndroidUtilities.dp(21);
    }

    private void updateNotificationSettings() {
        if (currentUser != null) {
            isMuted = getMessagesController().isDialogMuted(currentUser.id, 0);
        } else if (currentChat != null) {
            isMuted = getMessagesController().isDialogMuted(-currentChat.id, 0);
        }
        updateMuteButton();
    }

    private String formatUserStatus(TLRPC.User user) {
        if (user == null)
            return "";

        if (user.bot) {
            return LocaleController.getString("Bot", R.string.Bot);
        } else if (UserObject.isUserSelf(user)) {
            return LocaleController.getString("Online", R.string.Online);
        } else {
            return LocaleController.formatUserStatus(currentAccount, user);
        }
    }

    private String formatChatStatus(TLRPC.Chat chat) {
        if (chat == null)
            return "";

        if (ChatObject.isChannel(chat)) {
            int count = chat.participants_count;
            if (chatInfo != null) {
                count = chatInfo.participants_count;
            }
            return LocaleController.formatPluralString("Subscribers", count);
        } else {
            int count = chat.participants_count;
            if (chatInfo != null) {
                count = chatInfo.participants_count;
            }
            return LocaleController.formatPluralString("Members", count);
        }
    }

    private void updateStoriesViewBounds(boolean animated) {
        // For Material Design header, stories integration would be in the header area
        updateStoriesDisplay();
    }

    private boolean isUserOnline(TLRPC.User user) {
        if (user == null)
            return false;
        if (user.bot)
            return false;
        if (UserObject.isUserSelf(user))
            return true;

        return user.status != null
                && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime();
    }

    private void updateProfileData(boolean reload) {
        if (reload) {
            // Reload user/chat info
            if (currentUser != null) {
                getMessagesController().loadFullUser(currentUser, classGuid, true);
            } else if (currentChat != null) {
                getMessagesController().loadFullChat(currentChat.id, classGuid, true);
            }
        }

        updateHeaderContent();
        updateRowsIds();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateFloatingButtonColor() {
        // Update header button colors to match Material Design theme
        // BubbleButton colors are handled internally
    }

    private boolean mediaHeaderVisible = false;
    private boolean sharedMediaLayoutAttached = false;

    // PhotoViewer provider for avatar viewing - copied from ProfileActivity
    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject,
                TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (fileLocation == null) {
                return null;
            }

            TLRPC.FileLocation photoBig = null;
            if (userId != 0) {
                TLRPC.User user = getMessagesController().getUser(userId);
                if (user != null && user.photo != null && user.photo.photo_big != null) {
                    photoBig = user.photo.photo_big;
                }
            } else if (chatId != 0) {
                TLRPC.Chat chat = getMessagesController().getChat(chatId);
                if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                    photoBig = chat.photo.photo_big;
                }
            }

            if (photoBig != null && photoBig.local_id == fileLocation.local_id
                    && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int[] coords = new int[2];
                avatarImageView.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = avatarImageView;
                object.imageReceiver = avatarImageView.getImageReceiver();
                if (userId != 0) {
                    object.dialogId = userId;
                } else if (chatId != 0) {
                    object.dialogId = -chatId;
                }
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.size = -1;
                object.radius = avatarImageView.getImageReceiver().getRoundRadius(true);
                object.scale = avatarImageView.getScaleX();
                object.canEdit = userId == getUserConfig().clientUserId;
                return object;
            }
            return null;
        }

        @Override
        public void willHidePhotoViewer() {
            avatarImageView.getImageReceiver().setVisible(true, true);
        }
    };

    // Birthday formatting helper
    private String formatBirthday(TL_account.TL_birthday birthday) {
        if (birthday == null)
            return "";

        try {
            Calendar calendar = Calendar.getInstance();
            calendar.set(
                    birthday.year != 0 ? birthday.year : calendar.get(Calendar.YEAR),
                    birthday.month - 1, // Calendar months are 0-based
                    birthday.day);

            java.text.DateFormat dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
            return dateFormat.format(calendar.getTime());
        } catch (Exception e) {
            return birthday.day + "/" + birthday.month + (birthday.year != 0 ? "/" + birthday.year : "");
        }
    }

    // Location formatting helper
    private String formatLocation(TLRPC.TL_channelLocation location) {
        if (location == null || TextUtils.isEmpty(location.address))
            return "";
        return location.address;
    }

    // Avatar handling methods - copied from ProfileActivity
    private void openAvatar() {
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(ProfileNewActivity.this);
                if (user.photo.dc_id != 0) {
                    user.photo.photo_big.dc_id = user.photo.dc_id;
                }
                PhotoViewer.getInstance().openPhoto(user.photo.photo_big, provider);
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(ProfileNewActivity.this);
                if (chat.photo.dc_id != 0) {
                    chat.photo.photo_big.dc_id = chat.photo.dc_id;
                }
                ImageLocation videoLocation;
                if (chatInfo != null && (chatInfo.chat_photo instanceof TLRPC.TL_photo)
                        && !chatInfo.chat_photo.video_sizes.isEmpty()) {
                    videoLocation = ImageLocation.getForPhoto(chatInfo.chat_photo.video_sizes.get(0),
                            chatInfo.chat_photo);
                } else {
                    videoLocation = null;
                }
                PhotoViewer.getInstance().openPhotoWithVideo(chat.photo.photo_big, videoLocation, provider);
            }
        }
    }

    // Story integration - copied from ProfileActivity
    private boolean needInsetForStories() {
        return getMessagesController().getStoriesController().hasStories(getDialogId());
    }

    private void updateStoriesDisplay() {
        // Update avatar to show story ring when stories are available
        // In Material Design header, story rings would be handled differently
        boolean hasStories = needInsetForStories();
        // TODO: Implement story ring display for Material Design avatar
    }

    // Premium status handling
    private void updatePremiumStatus() {
        if (currentUser != null && userInfo != null) {
            // Handle premium badge display in name text view
            // Premium indicators would be shown alongside the name
        }
    }

    // ShowDrawable class for compatibility
    public static class ShowDrawable extends Drawable {
        private String text;
        private Paint textPaint;
        private Paint backgroundPaint;

        public ShowDrawable(String text) {
            this.text = text;
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(14));
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(0x1e000000);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            // Draw background
            canvas.drawRect(getBounds(), backgroundPaint);
            // Draw text
            canvas.drawText(text, getBounds().centerX(), getBounds().centerY(), textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            textPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            textPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        // Additional methods for compatibility
        public void setTextColor(int color) {
            textPaint.setColor(color);
        }

        public void setBackgroundColor(int color) {
            backgroundPaint.setColor(color);
        }
    }

    // Bubble Button Component - reusable component for header action buttons
    private static class BubbleButton extends FrameLayout {
        private ImageView iconView;
        private TextView textView;
        private GradientDrawable background;

        public BubbleButton(Context context, int iconRes, String text) {
            super(context);
            init(context, iconRes, text);
        }

        private void init(Context context, int iconRes, String text) {
            // Create transparent background for bubble effect
            background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(dp(16));
            background.setColor(Color.argb(51, 255, 255, 255)); // 20% white transparency
            setBackground(background);

            // Create container for centered icon and text
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // Create icon using provided vector drawable
            iconView = new ImageView(context);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            container.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 2));

            // Create text label
            textView = new TextView(context);
            textView.setText(text);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

            // Add press animation and touch feedback
            setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                        background.setColor(Color.argb(77, 255, 255, 255)); // 30% white on press
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        background.setColor(Color.argb(51, 255, 255, 255)); // 20% white normal
                        break;
                }
                return false;
            });
        }

        public void updateIcon(int iconRes) {
            iconView.setImageResource(iconRes);
        }

        public void updateText(String text) {
            textView.setText(text);
        }
    }

    // SharedMediaLayout.Delegate implementation
    @Override
    public void scrollToSharedMedia() {
        // Handle scroll to shared media
    }

    @Override
    public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View view) {
        // Handle member click
        return false;
    }

    @Override
    public boolean isFragmentOpened() {
        return !isPaused;
    }

    @Override
    public RecyclerListView getListView() {
        // Return the RecyclerListView from the adapter
        return null; // TODO: Store reference to listView
    }

    @Override
    public boolean canSearchMembers() {
        return canSearchMembers;
    }

    @Override
    public void updateSelectedMediaTabText() {
        // Handle media tab text updates
    }

    // SharedMediaPreloaderDelegate implementation
    @Override
    public void mediaCountUpdated() {
        if (sharedMediaLayout != null && sharedMediaPreloader != null) {
            sharedMediaLayout.setNewMediaCounts(sharedMediaPreloader.getLastMediaCount());
        }
        updateRowsIds();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Configure transparent status bar for collapsing header animation
     * Comprehensive solution supporting minSDK 19+ with proper API level handling
     */
    private void configureTransparentStatusBar() {
        if (getParentActivity() == null)
            return;

        android.view.Window window = getParentActivity().getWindow();

        // Apply API 19+ layout flags for full screen content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Handle API 19-20 (KitKat) - Use translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

            setWindowFlag(window, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, true);

            // Adjust layout for translucent status bar on KitKat
            if (coordinatorLayout != null) {
                coordinatorLayout.setFitsSystemWindows(true);
            }
        }

        // Handle API 21+ (Lollipop and above) - Use fully transparent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Remove translucent flag if it was set
            setWindowFlag(window, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false);

            // Set transparent status bar
            window.setStatusBarColor(Color.TRANSPARENT);

            // Add system bar background flag
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            // Ensure layout extends behind status bar
            if (coordinatorLayout != null) {
                coordinatorLayout.setFitsSystemWindows(false);
            }
        }

        // Handle API 30+ (Android 11+) - Use modern edge-to-edge approach
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setDecorFitsSystemWindows(false);
        }
    }

    /**
     * Helper method to set window flags safely across API levels
     */
    private void setWindowFlag(android.view.Window window, final int bits, boolean on) {
        WindowManager.LayoutParams winParams = window.getAttributes();
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        window.setAttributes(winParams);
    }

    /**
     * Set status bar color dynamically during collapse animation
     * Adapts to the current collapse state for smooth transitions
     */
    private void setStatusBarColorForCollapse(float collapseProgress) {
        if (getParentActivity() == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        android.view.Window window = getParentActivity().getWindow();

        // Interpolate between transparent (expanded) and theme color (collapsed)
        int themeColor = Theme.getColor(Theme.key_actionBarDefault);
        int alpha = (int) (collapseProgress * 255);
        int statusBarColor = Color.argb(alpha, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor));

        window.setStatusBarColor(statusBarColor);
    }

    /**
     * Setup container clipping to allow smooth text translation beyond bounds
     * This is critical for preventing username text from being clipped during
     * animation
     */
    private void setupContainerClippingForTranslation() {
        // Disable clipping on all containers in the hierarchy to allow text translation

        // Primary text containers (already set in XML but ensuring programmatically)
        if (nameContainer instanceof ViewGroup) {
            ((ViewGroup) nameContainer).setClipChildren(false);
            ((ViewGroup) nameContainer).setClipToPadding(false);
        }

        if (onlineContainer instanceof ViewGroup) {
            ((ViewGroup) onlineContainer).setClipChildren(false);
            ((ViewGroup) onlineContainer).setClipToPadding(false);
        }

        // Parent containers - critical for allowing text to move beyond normal bounds
        if (appBarLayout instanceof ViewGroup) {
            ((ViewGroup) appBarLayout).setClipChildren(false);
            ((ViewGroup) appBarLayout).setClipToPadding(false);
        }

        if (collapsingToolbarLayout instanceof ViewGroup) {
            ((ViewGroup) collapsingToolbarLayout).setClipChildren(false);
            ((ViewGroup) collapsingToolbarLayout).setClipToPadding(false);
        }

        if (coordinatorLayout instanceof ViewGroup) {
            ((ViewGroup) coordinatorLayout).setClipChildren(false);
            ((ViewGroup) coordinatorLayout).setClipToPadding(false);
        }

        // Header content container
        View expandedHeaderContainer = coordinatorLayout.findViewById(R.id.expanded_header_container);
        if (expandedHeaderContainer instanceof ViewGroup) {
            ((ViewGroup) expandedHeaderContainer).setClipChildren(false);
            ((ViewGroup) expandedHeaderContainer).setClipToPadding(false);
        }

        // Root coordinator layout - ensure no clipping at top level
        if (coordinatorLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) coordinatorLayout.getParent()).setClipChildren(false);
            ((ViewGroup) coordinatorLayout.getParent()).setClipToPadding(false);
        }
    }

    /**
     * Configure FrameLayouts specifically to prevent text clipping during
     * translation
     * This addresses the specific issue where TextViews are clipped by their
     * FrameLayout containers
     */
    private void setupFrameLayoutsForTranslation() {
        // Configure name container FrameLayout
        if (nameContainer instanceof FrameLayout) {
            FrameLayout nameFrame = (FrameLayout) nameContainer;
            // Ensure FrameLayout doesn't constrain child TextView during translation
            nameFrame.setClipChildren(false);
            nameFrame.setClipToPadding(false);
            nameFrame.setClipBounds(null); // Remove any clip bounds

            // Ensure the FrameLayout allows overflow
            nameFrame.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        // Configure online container FrameLayout (apply same settings for consistency)
        if (onlineContainer instanceof FrameLayout) {
            FrameLayout onlineFrame = (FrameLayout) onlineContainer;
            // Apply identical settings to ensure both text views behave the same
            onlineFrame.setClipChildren(false);
            onlineFrame.setClipToPadding(false);
            onlineFrame.setClipBounds(null); // Remove any clip bounds

            // Ensure the FrameLayout allows overflow
            onlineFrame.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        // Additional TextView configuration to prevent clipping
        if (nameTextView != null) {
            // Ensure TextView itself doesn't have constraints
            nameTextView.setIncludeFontPadding(false);
            nameTextView.setSingleLine(false); // Allow text to flow freely
            nameTextView.setEllipsize(null); // Never ellipsize
            nameTextView.setHorizontallyScrolling(true); // Allow horizontal overflow
        }

        if (onlineTextView != null) {
            // Apply identical settings to online text view
            onlineTextView.setIncludeFontPadding(false);
            onlineTextView.setSingleLine(false); // Allow text to flow freely
            onlineTextView.setEllipsize(null); // Never ellipsize
            onlineTextView.setHorizontallyScrolling(true); // Allow horizontal overflow
        }
    }
}