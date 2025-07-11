package me.telegraphy.android.ui;

// Using me.telegraphy.android for new/refactored classes
// Assuming other necessary classes from org.telegram will be imported as needed
// or eventually refactored into me.telegraphy.android as well.

// Corrected static import if dp is in me.telegraphy.android.messenger.AndroidUtilities
// For now, assuming it's available as AndroidUtilities.dp()
// import static me.telegraphy.android.messenger.AndroidUtilities.dp;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.Arrays; // Keep if used
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// Telegram specific imports (will remain org.telegram until fully refactored)
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SQLiteCursor;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots; // This was from your provided code
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatEditActivity;
import org.telegram.ui.ChatUsersActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.SecretChatHelper;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.MediaActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PrivacySettingsActivity;
import org.telegram.ui.QrActivity;
import org.telegram.ui.SessionsActivity;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.DataSettingsActivity;
import org.telegram.ui.UserInfoActivity;


// New cell imports from me.telegraphy.android
import me.telegraphy.android.ui.Cells.AboutLinkCell;
import me.telegraphy.android.ui.Cells.EmptyCell;
import me.telegraphy.android.ui.Cells.HeaderCell;
import me.telegraphy.android.ui.Cells.ProfileActionsCell;
import me.telegraphy.android.ui.Cells.ProfileBotCell;
import me.telegraphy.android.ui.Cells.ProfileBusinessCell;
import me.telegraphy.android.ui.Cells.ProfileChannelCell;
import me.telegraphy.android.ui.Cells.ProfileCoverCell;
import me.telegraphy.android.ui.Cells.ProfileDefaultCell;
import me.telegraphy.android.ui.Cells.ProfileGiftCell;
import me.telegraphy.android.ui.Cells.ProfileGroupCell;
import me.telegraphy.android.ui.Cells.ProfileHeaderCell;
import me.telegraphy.android.ui.Cells.ShadowSectionCell;
import me.telegraphy.android.ui.Cells.TextCell;
import me.telegraphy.android.ui.Cells.TextDetailCell;
import me.telegraphy.android.ui.Cells.TextInfoPrivacyCell;
import me.telegraphy.android.ui.Cells.UserCell;
import me.telegraphy.android.ProfileViewHolder; // Assuming ProfileViewHolder is in me.telegraphy.android
import me.telegraphy.android.TdApiManager;
import me.telegraphy.android.DatabaseManager;
import org.telegram.tdlib.TdApi; // Import TdApi
import org.telegram.messenger.ApplicationLoader; // For context

// GiftObject might be an inner class or a separate file in me.telegraphy.android.model perhaps
// For now, keeping it as defined in the prompt for ProfileActivityEnhanced
class GiftObject { // Copied from your prompt
    String id;
    String name;
    int iconResId;
    String lottieAnimationName;
    boolean isNew;
    long dateReceived;
    String description;
    String senderName;

    public GiftObject(String id, String name, int iconResId, String lottieAnimationName, boolean isNew, long date, String description) {
        this.id = id;
        this.name = name;
        this.iconResId = iconResId;
        this.lottieAnimationName = lottieAnimationName;
        this.isNew = isNew;
        this.dateReceived = date;
        this.description = description;
    }
}


public class ProfileActivityEnhanced extends BaseFragment implements
        NotificationCenter.NotificationCenterDelegate,
        ProfileGiftCell.GiftAnimationListener,
        ProfileCoverCell.ProfileCoverCellDelegate,
        ProfileCoverCell.StoriesListener {

    public static final int SCROLL_THRESHOLD = 10;

    private RecyclerListView listView;
    private ProfileAdapter profileAdapter;
    private LinearLayoutManager layoutManager;
    // private FrameLayout fragmentViewContainer; // fragmentView is inherited from BaseFragment
    private UndoView undoView;

    private long userId;
    private long chatId;
    private long topicId;
    private long dialogId;
    private boolean isCurrentUser;

    // private TLRPC.User currentUser; // Will be replaced by TdApi.User
    // private TLRPC.Chat currentChat; // Will be replaced by TdApi.Chat
    // private TLRPC.UserFull currentUserFull; // Will be replaced by TdApi.UserFullInfo
    // private TLRPC.ChatFull currentChatFull; // Will be replaced by TdApi.ChatFull

    private TdApi.User tdCurrentUser;
    private TdApi.Chat tdCurrentChat;
    private TdApi.UserFullInfo tdCurrentUserFullInfo;
    private TdApi.ChatFullInfo tdCurrentChatFullInfo;

    private TdApiManager tdApiManager;
    private DatabaseManager databaseManager;

    private TLRPC.EncryptedChat currentEncryptedChat; // Keep if secret chats are handled outside TdApiManager initially
    private TL_bots.BotInfo botInfo; // This might map to TdApi.BotInfo or TdApi.UserTypeBot
    private boolean userBlocked; // Will be determined from TdApi.UserFullInfo or TdApi.BlockList

    public enum ProfileType { USER, BOT, BUSINESS, CHANNEL, GROUP, GROUP_TOPIC, GIFT_PROFILE }
    private ProfileType profileType = ProfileType.USER;

    private ArrayList<Integer> rowTypes = new ArrayList<>();
    private ArrayList<Object> rowData = new ArrayList<>();

    private static final int TYPE_COVER_CELL = 0;
    private static final int TYPE_DEFAULT_CELL = 1;
    private static final int TYPE_GIFT_CELL = 2;
    private static final int TYPE_CHANNEL_CELL = 3;
    private static final int TYPE_GROUP_CELL = 4;
    private static final int TYPE_BUSINESS_CELL = 5;
    private static final int TYPE_BOT_CELL = 6;
    private static final int TYPE_PHONE_ROW = 7;
    private static final int TYPE_USERNAME_ROW = 8;
    private static final int TYPE_BIO_ROW = 9;
    private static final int TYPE_SHARED_MEDIA_ROW = 10;
    private static final int TYPE_NOTIFICATIONS_ROW = 11;
    private static final int TYPE_MEMBERS_HEADER_ROW = 12;
    private static final int TYPE_ADD_MEMBER_ROW = 13;
    private static final int TYPE_MEMBER_ROW = 14;
    private static final int TYPE_SETTINGS_ROW = 15;
    private static final int TYPE_EMPTY_ROW = 16;
    private static final int TYPE_HEADER_TEXT_ROW = 17;
    // Removed TYPE_INFO_CELL and TYPE_ACTIONS_CELL as their functionality seems covered by new cells

    private int coverRow = -1;
    private int defaultInfoRow = -1;
    private int giftDisplayRow = -1;
    private int channelInfoRow = -1;
    private int groupInfoRow = -1;
    private int botInfoRow = -1;
    private int businessInfoRow = -1;
    private int phoneRow = -1;
    private int usernameRow = -1;
    private int bioRow = -1;
    private int sharedMediaRow = -1;
    private int notificationsRow = -1;
    private int membersHeaderRow = -1;
    private int addMemberRow = -1;
    private int membersStartRow = -1;
    private int membersEndRow = -1;
    private int addToContactsRow = -1;
    private int reportRow = -1;


    private float extraHeight;
    private boolean playProfileAnimation;
    private boolean openAnimationInProgress;
    private float headerAnimationProgress = 1.0f;

    // Menu Item IDs
    private final static int ADD_CONTACT_ID = 1;
    private final static int BLOCK_CONTACT_ID = 2;
    private final static int SHARE_CONTACT_ID = 3;
    private final static int EDIT_CONTACT_ID = 4;
    private final static int DELETE_CONTACT_ID = 5;
    private final static int LEAVE_GROUP_ID = 7;
    private final static int INVITE_TO_GROUP_ID = 9;
    private final static int SHARE_PROFILE_ID = 10;
    private final static int EDIT_CHANNEL_OR_GROUP_ID = 12;
    private final static int CALL_ITEM_ID = 13;
    private final static int VIDEO_CALL_ITEM_ID = 14;
    private final static int GIFT_PREMIUM_ID = 15;
    private final static int ADD_SHORTCUT_ID = 16;
    private final static int SEARCH_MEMBERS_ID = 17;
    private final static int ADD_MEMBER_ID = 18;
    private final static int STATISTICS_ID = 19;
    private final static int START_SECRET_CHAT_ID = 20;
    // Removed some SET_USERNAME, EDIT_BIO, etc. as these might be part of edit flows
    private final static int VIEW_DISCUSSION_ID = 25;
    private final static int QR_CODE_ID = 26;
    // private final static int EDIT_SETTINGS_ID = 27; // This seems too generic, specific settings are better

    private List<ProfileGiftCell.GiftItem> userGifts = new ArrayList<>();

    // Other fields from your provided ProfileActivityEnhanced
    private boolean saved; // from original args
    private boolean openSimilar;
    private boolean isTopic;
    private long banFromGroup;
    private int reportReactionMessageId;
    private long reportReactionFromDialogId;
    private boolean showAddToContacts = true; // default from your code
    private String vcardPhone;
    private String vcardFirstName;
    private String vcardLastName;
    private boolean reportSpam;
    private boolean myProfile; // if this profile is for the current logged in user
    private boolean openGifts;
    private boolean openCommonChats;
    private boolean creatingChat; // For secret chat creation state


    public ProfileActivityEnhanced(Bundle args) {
        super(args);
        if (args != null) {
            this.userId = args.getLong("user_id", 0);
            this.chatId = args.getLong("chat_id", 0);
            this.topicId = args.getLong("topic_id", 0);
            if (this.userId != 0) this.dialogId = this.userId;
            else if (this.chatId != 0) this.dialogId = -this.chatId;

            this.currentAccount = UserConfig.selectedAccount; // Set current account from UserConfig
            this.isCurrentUser = (this.userId != 0 && this.userId == UserConfig.getInstance(currentAccount).getClientUserId());
            this.myProfile = args.getBoolean("my_profile", this.isCurrentUser); // my_profile might be redundant if isCurrentUser is accurate

            this.saved = args.getBoolean("saved", false);
            this.openSimilar = args.getBoolean("similar", false);
            this.isTopic = this.topicId != 0;
            this.banFromGroup = args.getLong("ban_chat_id", 0);
            this.reportReactionMessageId = args.getInt("report_reaction_message_id", 0);
            this.reportReactionFromDialogId = args.getLong("report_reaction_from_dialog_id", 0);
            this.showAddToContacts = args.getBoolean("show_add_to_contacts", true);
            this.vcardPhone = args.getString("vcard_phone"); // Consider PhoneFormat.stripExceptNumbers here if needed
            this.vcardFirstName = args.getString("vcard_first_name");
            this.vcardLastName = args.getString("vcard_last_name");
            this.reportSpam = args.getBoolean("reportSpam", false);
            this.openGifts = args.getBoolean("open_gifts", false);
            this.openCommonChats = args.getBoolean("open_common", false);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        // Initialize TdApiManager and DatabaseManager
        tdApiManager = TdApiManager.getInstance();
        databaseManager = DatabaseManager.getInstance(ApplicationLoader.getApplicationContext());

        // Ensure currentAccount is valid (from original code)
        if (UserConfig.getInstance(currentAccount).getCurrentUser() == null && UserConfig.getActivatedAccountsCount() > 0) {
            for (int i=0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                if (UserConfig.getInstance(i).isClientActivated()) {
                    UserConfig.selectedAccount = i;
                    currentAccount = i;
                    break;
                }
            }
        } else if (UserConfig.getInstance(currentAccount).getCurrentUser() == null) {
            return false;
        }

        // Set the account for TdApiManager if it's not implicitly handling it based on UserConfig.selectedAccount
        // tdApiManager.switchAccountIfNeeded(currentAccount); // Assuming such a method exists or is handled internally

        if (userId != 0) {
            // Load from DatabaseManager first
            tdCurrentUser = databaseManager.getUser(userId);
            tdCurrentUserFullInfo = databaseManager.getUserFullInfo(userId);

            // Request from TdApiManager
            tdApiManager.getUser(userId, user -> {
                if (user != null && getParentActivity() != null) { // Check activity context
                    getParentActivity().runOnUiThread(() -> {
                        tdCurrentUser = user;
                        databaseManager.addUser(user);
                        determineProfileType();
                        updateRows();
                        updateProfileData();
                        // Also request full info if basic user info is fetched
                        tdApiManager.getUserFullInfo(userId, userFullInfo -> {
                            if (userFullInfo != null && getParentActivity() != null) {
                                getParentActivity().runOnUiThread(() -> {
                                    tdCurrentUserFullInfo = userFullInfo;
                                    databaseManager.addUserFullInfo(userFullInfo);
                                    determineProfileType();
                                    updateRows();
                                    updateProfileData();
                                });
                            }
                        });
                    });
                }
            });
            if (tdCurrentUserFullInfo == null) { // Request if not in DB or to refresh
                 tdApiManager.getUserFullInfo(userId, userFullInfo -> {
                    if (userFullInfo != null && getParentActivity() != null) {
                        getParentActivity().runOnUiThread(() -> {
                            tdCurrentUserFullInfo = userFullInfo;
                            databaseManager.addUserFullInfo(userFullInfo);
                            determineProfileType();
                            updateRows();
                            updateProfileData();
                        });
                    }
                });
            }
            determineProfileType();

        } else if (chatId != 0) {
            tdCurrentChat = databaseManager.getChat(chatId);
            tdCurrentChatFullInfo = databaseManager.getChatFullInfo(chatId);

            tdApiManager.getChat(chatId, chat -> {
                if (chat != null && getParentActivity() != null) {
                    getParentActivity().runOnUiThread(() -> {
                        tdCurrentChat = chat;
                        databaseManager.addChat(chat);
                        determineProfileType();
                        updateRows();
                        updateProfileData();
                        // For chats, full info is often needed for permissions, members etc.
                        tdApiManager.getChatFullInfo(chatId, chatFullInfo -> {
                            if (chatFullInfo != null && getParentActivity() != null) {
                                getParentActivity().runOnUiThread(() -> {
                                    tdCurrentChatFullInfo = chatFullInfo;
                                    databaseManager.addChatFullInfo(chatFullInfo);
                                    determineProfileType();
                                    updateRows();
                                    updateProfileData();
                                });
                            }
                        });
                    });
                }
            });
             if (tdCurrentChatFullInfo == null) { // Request if not in DB or to refresh
                 tdApiManager.getChatFullInfo(chatId, chatFullInfo -> {
                    if (chatFullInfo != null && getParentActivity() != null) {
                         getParentActivity().runOnUiThread(() -> {
                            tdCurrentChatFullInfo = chatFullInfo;
                            databaseManager.addChatFullInfo(chatFullInfo);
                            determineProfileType();
                            updateRows();
                            updateProfileData();
                        });
                    }
                });
            }
            determineProfileType();
        } else {
            return false;
        }

        extraHeight = AndroidUtilities.dp(280);
        setupNotificationListeners();
        updateRows();
        loadGiftData();

        return true;
    }

    private void determineProfileType() {
        if (tdCurrentUser != null) {
            if (tdCurrentUser.type instanceof TdApi.UserTypeBot) {
                profileType = ProfileType.BOT;
                 if (tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.botInfo != null) {
                    // botInfo = mapTdApiBotInfo(tdCurrentUserFullInfo.botInfo); // Conceptual mapping
                }
            } else if (tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.businessInfo != null) {
                profileType = ProfileType.BUSINESS;
            } else if (openGifts || (userGifts != null && !userGifts.isEmpty())) {
                profileType = ProfileType.GIFT_PROFILE;
            } else {
                profileType = ProfileType.USER;
            }
        } else if (tdCurrentChat != null) {
            if (tdCurrentChat.type instanceof TdApi.ChatTypeSupergroup) {
                TdApi.ChatTypeSupergroup supergroupType = (TdApi.ChatTypeSupergroup) tdCurrentChat.type;
                if (supergroupType.isChannel) {
                    profileType = ProfileType.CHANNEL;
                } else if (isTopic) {
                     profileType = ProfileType.GROUP_TOPIC;
                } else {
                    profileType = ProfileType.GROUP; // Megagroup
                }
            } else if (tdCurrentChat.type instanceof TdApi.ChatTypeBasicGroup) {
                 if (isTopic) {
                    profileType = ProfileType.GROUP_TOPIC;
                } else {
                    profileType = ProfileType.GROUP; // Basic group
                }
            }
        }

        if (userId != 0 && tdCurrentUserFullInfo != null) {
            userBlocked = tdCurrentUserFullInfo.isBlocked;
        } else if (userId != 0 && tdCurrentUser != null && tdCurrentUser.isBlocked) { // Fallback if full info not yet available
            userBlocked = tdCurrentUser.isBlocked;
        }
        else {
            userBlocked = false;
        }
    }

    private void setupNotificationListeners() {
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        // TDLib related updates will come from TdApiManager's internal handler,
        // which should post specific NotificationCenter events that this activity will listen to.
        nc.addObserver(this, NotificationCenter.tdLibUpdateUser); // Posted by TdApiManager
        nc.addObserver(this, NotificationCenter.tdLibUpdateUserFullInfo); // Posted by TdApiManager
        nc.addObserver(this, NotificationCenter.tdLibUpdateChat); // Posted by TdApiManager
        nc.addObserver(this, NotificationCenter.tdLibUpdateChatFullInfo); // Posted by TdApiManager
        nc.addObserver(this, NotificationCenter.tdLibUpdateBlockList); // For block status changes

        // Keep global theme updates and other non-TDLib specific UI updates if needed
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        // nc.addObserver(this, NotificationCenter.updateInterfaces); // If still needed for some UI updates
    }

    private void loadGiftData() {
        // Gift data will likely come from TdApi.UserFullInfo or a specific TdApi call.
        // For now, this is a placeholder. Adapt when TdApi structure for gifts is known.
        // if (profileType == ProfileType.GIFT_PROFILE || (tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.premiumGifts != null)) {
        //     userGifts.clear(); // Clear before loading new ones
        //     for (TdApi.PremiumGiftOption option : tdCurrentUserFullInfo.premiumGifts) { // Assuming TdApi.PremiumGiftOption structure
        //         userGifts.add(new ProfileGiftCell.GiftItem(
        //             "gift_months_" + option.months,
        //             LocaleController.formatPluralString("Months", option.months) + " Premium",
        //             R.drawable.msg_premium_gift,
        //             null,
        //             false,
        //             System.currentTimeMillis() - (long)option.months * 30 * 24 * 60 * 60 * 1000,
        //             "Gift of " + option.months + " months"
        //         ));
        //     }
        // }
    }


    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        fragmentView = new FrameLayout(context); // Use inherited fragmentView
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (getParentActivity() == null) return;
                if (id == -1) {
                    finishFragment();
                } else if (id == BLOCK_CONTACT_ID) {
                    if (tdCurrentUser == null) return;
                    if (userBlocked) {
                        tdApiManager.unblockUser(userId, success -> {
                            if (success && getParentActivity() != null) getParentActivity().runOnUiThread(() -> {
                                userBlocked = false;
                                // Optimistically update UI or wait for tdLibUpdateBlockList
                                updateRows();
                                createActionBarMenu(); // Recreate to update block/unblock text
                                // Show bulletin if needed
                            });
                        });
                    } else {
                        tdApiManager.blockUser(userId, success -> {
                             if (success && getParentActivity() != null) getParentActivity().runOnUiThread(() -> {
                                userBlocked = true;
                                updateRows();
                                createActionBarMenu(); // Recreate to update block/unblock text
                            });
                        });
                    }
                } else if (id == ADD_CONTACT_ID) {
                    if (tdCurrentUser == null) return;
                    Bundle args = new Bundle();
                    args.putLong("user_id", tdCurrentUser.id);
                    args.putBoolean("addContact", true);
                    // ContactAddActivity will need to use TdApiManager
                    presentFragment(new ContactAddActivity(args));
                } else if (id == EDIT_CONTACT_ID) {
                     if (tdCurrentUser == null) return;
                     Bundle args = new Bundle();
                     args.putLong("user_id", tdCurrentUser.id);
                     presentFragment(new ContactAddActivity(args));
                } else if (id == DELETE_CONTACT_ID) {
                    if (tdCurrentUser == null) return;
                    // Adapt AlertsCreator or use TdApiManager directly
                     tdApiManager.deleteContact(userId, success -> {
                        if (success && getParentActivity() != null) getParentActivity().runOnUiThread(() -> {
                            // Update UI, maybe show bulletin
                            updateRows(); // Contact status might change rows
                        });
                    });
                } else if (id == SHARE_CONTACT_ID || id == SHARE_PROFILE_ID) {
                    // Share logic will need to be adapted for TdApi.User/Chat
                    // Example: Create a TdApi.InputMessageContact or TdApi.InputMessageText with profile link
                } else if (id == CALL_ITEM_ID) {
                    if (tdCurrentUser != null && tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.canBeCalled) {
                        // VoIPHelper and AccountInstance might need adaptation for TdApi types or TdApiManager handles call creation
                        // VoIPHelper.startCall(tdCurrentUser, false, tdCurrentUserFullInfo.hasVideoCalls, getParentActivity(), tdCurrentUserFullInfo, AccountInstance.getInstance(currentAccount));
                        tdApiManager.createCall(tdCurrentUser.id, false, callId -> { /* Handle call UI */ });
                    }
                } else if (id == VIDEO_CALL_ITEM_ID) {
                     if (tdCurrentUser != null && tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.canBeCalled && tdCurrentUserFullInfo.hasVideoCalls) {
                        // VoIPHelper.startCall(tdCurrentUser, true, tdCurrentUserFullInfo.hasVideoCalls, getParentActivity(), tdCurrentUserFullInfo, AccountInstance.getInstance(currentAccount));
                        tdApiManager.createCall(tdCurrentUser.id, true, callId -> { /* Handle call UI */ });
                    }
                }
                // ... Other menu items ...
            }
        });
        createActionBarMenu();

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(layoutManager);
        profileAdapter = new ProfileAdapter(context);
        listView.setAdapter(profileAdapter);
        listView.setClipToPadding(false); // Important for cover cell animation

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                handleScroll(dy);
            }
        });

        // Initialize extraHeight based on the expected initial height of ProfileCoverCell
        // This might need to be dynamically obtained from ProfileCoverCell after its first measure,
        // or set to a known expanded height.
        ProfileCoverCell tempCoverCell = new ProfileCoverCell(context); // Temporary for height calculation
        tempCoverCell.measure(
            MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) // Measure to get preferred height
        );
        extraHeight = tempCoverCell.getMeasuredHeight(); // Use its natural height
        if (extraHeight == 0) extraHeight = AndroidUtilities.dp(280); // Fallback

        listView.setPadding(0, (int)extraHeight, 0, AndroidUtilities.dp(48)); // Initial padding for cover cell


        ((FrameLayout)fragmentView).addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        undoView = new UndoView(context);
        ((FrameLayout)fragmentView).addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateProfileData();
        updateRows();

        return fragmentView;
    }

    private void handleScroll(int dy) {
        if (layoutManager.findFirstVisibleItemPosition() == coverRow) {
            View coverCellView = layoutManager.findViewByPosition(coverRow);
            if (coverCellView instanceof ProfileCoverCell) {
                ProfileCoverCell cell = (ProfileCoverCell) coverCellView;

                float newExtraHeight = extraHeight - dy;
                int minHeight = actionBar.getMeasuredHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                int initialCoverHeight = cell.getInitialCoverHeight() == 0 ? AndroidUtilities.dp(280) : cell.getInitialCoverHeight(); // Use a default if not set

                extraHeight = Math.max(minHeight, Math.min(initialCoverHeight, newExtraHeight));

                listView.setPadding(0, (int) extraHeight, 0, AndroidUtilities.dp(48));
                cell.setScrollOffset((int) (initialCoverHeight - extraHeight)); // Pass the amount scrolled *from expanded state*

                float scrollProgress = (initialCoverHeight - extraHeight) / (float) (initialCoverHeight - minHeight);
                scrollProgress = Math.max(0, Math.min(1, scrollProgress));

                // ActionBar fade and title
                if (scrollProgress > 0.85f) { // Mostly collapsed
                    actionBar.setAlpha(1.0f);
                    String title = "";
                    if (currentUser != null) title = UserObject.getUserName(currentUser);
                    else if (currentChat != null) title = currentChat.title;
                    actionBar.setTitle(title);
                     if (parentLayout != null && parentLayout.getThemeDelegate() != null) {
                        // Use ThemeDelegate to get color
                        actionBar.setBackgroundColor(parentLayout.getThemeDelegate().getPrimaryColor());
                    } else {
                        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
                    }
                } else { // Mostly expanded
                    float alpha = Math.max(0, (scrollProgress - 0.5f) * 2); // Start fading title/bg after 50% scroll
                    actionBar.setAlpha(alpha);
                    actionBar.setTitle("");
                    actionBar.setBackgroundColor(Color.TRANSPARENT);
                }

                // Default cell expansion
                View defaultCellView = layoutManager.findViewByPosition(defaultInfoRow);
                if (defaultCellView instanceof ProfileDefaultCell) {
                    ProfileDefaultCell defaultCell = (ProfileDefaultCell) defaultCellView;
                    defaultCell.setExpanded(scrollProgress > 0.7f, true);
                }
            }
        } else { // Cover cell is scrolled off-screen
            extraHeight = actionBar.getMeasuredHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            listView.setPadding(0, (int) extraHeight, 0, AndroidUtilities.dp(48));
            actionBar.setAlpha(1.0f);
            String title = "";
            if (currentUser != null) title = UserObject.getUserName(currentUser);
            else if (currentChat != null) title = currentChat.title;
            actionBar.setTitle(title);
            if (parentLayout != null && parentLayout.getThemeDelegate() != null) {
                actionBar.setBackgroundColor(parentLayout.getThemeDelegate().getPrimaryColor());
            } else {
                actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            }

            View defaultCellView = layoutManager.findViewByPosition(defaultInfoRow);
            if (defaultCellView instanceof ProfileDefaultCell) {
                 ((ProfileDefaultCell) defaultCellView).setExpanded(true, false); // Keep expanded when cover is gone
            }
        }
    }

    private void createActionBarMenu() {
        ActionBarMenu menu = actionBar.createMenu();
        menu.clearItems();
        // Logic from your provided ProfileActivityEnhanced for menu items
        if (userId != 0 && currentUser != null) {
            if (!UserObject.isUserSelf(currentUser)) {
                ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                otherItem.addSubItem(SHARE_CONTACT_ID, R.drawable.msg_share, LocaleController.getString("ShareContact", R.string.ShareContact));
                otherItem.addSubItem(BLOCK_CONTACT_ID, userBlocked ? R.drawable.msg_unblock : R.drawable.msg_block, userBlocked ? LocaleController.getString("Unblock", R.string.Unblock) : LocaleController.getString("BlockContact", R.string.BlockContact));
                if (!ContactsController.getInstance(currentAccount).isContact(userId)) {
                    otherItem.addSubItem(ADD_CONTACT_ID, R.drawable.msg_addcontact, LocaleController.getString("AddContact", R.string.AddContact));
                } else {
                    otherItem.addSubItem(EDIT_CONTACT_ID, R.drawable.ic_edit, LocaleController.getString("EditContact", R.string.EditContact));
                    otherItem.addSubItem(DELETE_CONTACT_ID, R.drawable.msg_delete, LocaleController.getString("DeleteContact", R.string.DeleteContact));
                }
                // Add call options
                 if (currentUserFull != null && currentUserFull.phone_calls_available) {
                    otherItem.addSubItem(CALL_ITEM_ID, R.drawable.msg_call, LocaleController.getString("Call", R.string.Call));
                    if (currentUserFull.video_calls_available) {
                         otherItem.addSubItem(VIDEO_CALL_ITEM_ID, R.drawable.msg_videocall, LocaleController.getString("VideoCall", R.string.VideoCall));
                    }
                }
                if (!currentUser.bot && !isCurrentUser) {
                     otherItem.addSubItem(START_SECRET_CHAT_ID, R.drawable.msg_secret, LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat));
                }

            } else { // Own profile
                 ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                 otherItem.addSubItem(QR_CODE_ID, R.drawable.msg_qrcode, LocaleController.getString("MyQRcode", R.string.MyQRcode));
                 // otherItem.addSubItem(EDIT_SETTINGS_ID, R.drawable.ic_settings, LocaleController.getString("Settings", R.string.Settings)); // Example
            }
             menu.addItem(ADD_SHORTCUT_ID, R.drawable.msg_home, LocaleController.getString("AddShortcut", R.string.AddShortcut));

        } else if (chatId != 0 && currentChat != null) {
            // Menu items for chats/channels
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup && currentChatFull != null && currentChatFull.linked_chat_id != 0) {
                menu.addItem(VIEW_DISCUSSION_ID, R.drawable.msg_discussion, LocaleController.getString("Discussion", R.string.Discussion));
            }
            if (ChatObject.canEditInfo(currentChat)) {
                 menu.addItem(EDIT_CHANNEL_OR_GROUP_ID, R.drawable.ic_edit, LocaleController.getString("Edit", R.string.Edit));
            }
            if (ChatObject.isMegagroup(currentChat) || (ChatObject.isChannel(currentChat) && !currentChat.creator && !currentChat.megagroup)) {
                 // No search for basic groups or own channels here
            } else {
                 menu.addItem(SEARCH_MEMBERS_ID, R.drawable.ic_search_white, LocaleController.getString("Search", R.string.Search));
            }

            ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
            if (ChatObject.canAddUsers(currentChat)) {
                otherItem.addSubItem(ADD_MEMBER_ID, R.drawable.msg_addcontact, LocaleController.getString("AddMember", R.string.AddMember));
            }
            // Add shortcut for chats too
            otherItem.addSubItem(ADD_SHORTCUT_ID, R.drawable.msg_home, LocaleController.getString("AddShortcut", R.string.AddShortcut));
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                 otherItem.addSubItem(STATISTICS_ID, R.drawable.msg_stats, LocaleController.getString("Statistics", R.string.Statistics));
            }
            otherItem.addSubItem(QR_CODE_ID, R.drawable.msg_qrcode, LocaleController.getString("QRCODE", R.string.QRCODE));
            if (!ChatObject.isPublic(currentChat)) { // Share profile for public, share contact for private (handled by SHARE_CONTACT_ID)
                 otherItem.addSubItem(SHARE_PROFILE_ID, R.drawable.msg_share, LocaleController.getString("ShareLink", R.string.ShareLink));
            }
             if (!currentChat.creator && ChatObject.isChannel(currentChat) && !currentChat.megagroup && !currentChat.left && !currentChat.kicked) {
                // Leave channel option
            } else if (currentChat.megagroup && (!currentChat.left && !currentChat.kicked)) {
                 otherItem.addSubItem(LEAVE_GROUP_ID, R.drawable.msg_leave, LocaleController.getString("LeaveGroup", R.string.LeaveGroup));
            }
        }
        // Common options
        // ...
    }


    private void updateProfileData() {
        // This method is crucial for setting the initial state of UI elements
        // based on currentUser, currentChat, etc.
        // For example, setting the ActionBar title.
        if (currentUser != null) {
            actionBar.setTitle(UserObject.getUserName(currentUser));
        } else if (currentChat != null) {
            actionBar.setTitle(currentChat.title);
        } else {
            actionBar.setTitle(""); // Default or loading title
        }

        // Notify adapter if data it relies on has changed
        if (profileAdapter != null) {
            profileAdapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowTypes.clear();
        rowData.clear();

        // 1. Cover Cell
        coverRow = addRow(TYPE_COVER_CELL, null); // Data for cover cell will be currentUser or currentChat

        // 2. Main Info Cell
        switch (profileType) {
            case USER:
            case GIFT_PROFILE:
                defaultInfoRow = addRow(TYPE_DEFAULT_CELL, tdCurrentUser);
                break;
            case BOT:
                botInfoRow = addRow(TYPE_BOT_CELL, tdCurrentUser);
                break;
            case BUSINESS:
                businessInfoRow = addRow(TYPE_BUSINESS_CELL, tdCurrentUserFullInfo);
                break;
            case CHANNEL:
                channelInfoRow = addRow(TYPE_CHANNEL_CELL, tdCurrentChat);
                break;
            case GROUP:
            case GROUP_TOPIC:
                groupInfoRow = addRow(TYPE_GROUP_CELL, tdCurrentChat);
                break;
        }

        // 3. Gift Display Cell
        if (profileType == ProfileType.GIFT_PROFILE && userGifts != null && !userGifts.isEmpty()) {
            giftDisplayRow = addRow(TYPE_GIFT_CELL, userGifts);
        }

        // 4. Standard Info Rows (Phone, Username, Bio)
        if (profileType == ProfileType.USER || profileType == ProfileType.BOT || profileType == ProfileType.BUSINESS) {
            if (tdCurrentUser != null && !TextUtils.isEmpty(tdCurrentUser.phoneNumber)) {
                phoneRow = addRow(TYPE_PHONE_ROW, tdCurrentUser.phoneNumber);
            }
            if (tdCurrentUser != null && tdCurrentUser.usernames != null && tdCurrentUser.usernames.activeUsernames.length > 0 && !TextUtils.isEmpty(tdCurrentUser.usernames.activeUsernames[0])) {
                usernameRow = addRow(TYPE_USERNAME_ROW, "@" + tdCurrentUser.usernames.activeUsernames[0]);
            }
            if (tdCurrentUserFullInfo != null && tdCurrentUserFullInfo.bio != null && !TextUtils.isEmpty(tdCurrentUserFullInfo.bio.text)) {
                bioRow = addRow(TYPE_BIO_ROW, tdCurrentUserFullInfo.bio.text);
            }
        }

        // 5. Shared Media, Notifications
        if (dialogId != 0) {
            sharedMediaRow = addRow(TYPE_SHARED_MEDIA_ROW, dialogId);
            notificationsRow = addRow(TYPE_NOTIFICATIONS_ROW, dialogId);
        }

        // 6. Group/Channel Members - Adapt with TdApi.ChatMember and TdApiManager.getChatMembers
        // if ((profileType == ProfileType.GROUP || profileType == ProfileType.CHANNEL || profileType == ProfileType.GROUP_TOPIC) && tdCurrentChatFullInfo != null) {
        //     if (tdCurrentChatFullInfo.members != null && tdCurrentChatFullInfo.members.length > 0 /* || canAddUsers(tdCurrentChatFullInfo) */) {
        //         membersHeaderRow = addRow(TYPE_HEADER_TEXT_ROW, "Members"); // Placeholder text
        //         // if (canAddUsers(tdCurrentChatFullInfo)) { addMemberRow = addRow(TYPE_ADD_MEMBER_ROW, null); }
        //         membersStartRow = rowTypes.size();
        //         for (TdApi.ChatMember member : tdCurrentChatFullInfo.members) {
        //             if (rowTypes.size() - membersStartRow < 5) {
        //                  TdApi.User user = databaseManager.getUser(member.memberId.getUserId()); // Or fetch if not in DB
        //                  if (user != null) addRow(TYPE_MEMBER_ROW, user);
        //             } else { break; }
        //         }
        //         membersEndRow = rowTypes.size();
        //     }
        // }

        addRow(TYPE_EMPTY_ROW, AndroidUtilities.dp(56));

        if (profileAdapter != null) {
            profileAdapter.notifyDataSetChanged();
        }
    }

    private int addRow(int type, Object data) {
        rowTypes.add(type);
        rowData.add(data);
        return rowTypes.size() - 1;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (profileAdapter != null) {
            profileAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        // If cover cell's initial height depends on its content (e.g. stories),
        // re-evaluate extraHeight here or after ProfileCoverCell measures itself.
        ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (listView != null && listView.getChildCount() > 0) {
                    View coverView = listView.getChildAt(0);
                    if (coverView instanceof ProfileCoverCell) {
                        int newInitialHeight = ((ProfileCoverCell) coverView).getInitialCoverHeight();
                        if (newInitialHeight > 0 && extraHeight != newInitialHeight) {
                            extraHeight = newInitialHeight;
                            listView.setPadding(0, (int)extraHeight, 0, AndroidUtilities.dp(48));
                            handleScroll(0); // Recalculate layout based on new extraHeight
                        }
                    }
                }
                if (fragmentView != null) { // Check if fragmentView is not null
                     fragmentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else if (getView() != null) { // Fallback to getView() if fragmentView is null
                    getView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        };
        if (fragmentView != null) { // Check if fragmentView is not null
            fragmentView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        } else if (getView() != null) { // Fallback to getView() if fragmentView is null
             getView().getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }


    // Delegate implementations (Avatar, Stories, Gifts)
    @Override
    public void onAvatarClick() {
        TLRPC.FileLocation photoLocation = null;
        if (currentUser != null && currentUser.photo != null && currentUser.photo.photo_big != null) {
            photoLocation = currentUser.photo.photo_big;
        } else if (currentChat != null && currentChat.photo != null && currentChat.photo.photo_big != null) {
            photoLocation = currentChat.photo.photo_big;
        }

        if (photoLocation != null) {
            PhotoViewer.getInstance().setParentActivity(getParentActivity());
            PhotoViewer.getInstance().openPhoto(photoLocation, new PhotoViewer.EmptyPhotoViewerProvider() {
                @Override
                public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation1, int index, boolean needPreview) {
                    View coverCellView = layoutManager.findViewByPosition(coverRow);
                    if (coverCellView instanceof ProfileCoverCell) {
                        ProfileCoverCell cell = (ProfileCoverCell) coverCellView;
                        BackupImageView avatarView = cell.getAvatarImageView();
                        if (avatarView != null) {
                            int[] coords = new int[2];
                            avatarView.getLocationInWindow(coords);
                            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                            object.viewX = coords[0];
                            object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                            object.parentView = avatarView;
                            object.imageReceiver = avatarView.getImageReceiver();
                            object.dialogId = dialogId;
                            object.thumb = object.imageReceiver.getBitmapSafe();
                            object.radius = avatarView.getImageReceiver().getRoundRadius()[0];
                            // For TdApi, object.fileLocation would be TdApi.File or its local path
                            // object.tdFile = tdPhoto.big; // Example if PlaceProviderObject is adapted
                            return object;
                        }
                    }
                    return null;
                }
                @Override
                public void willHidePhotoViewer() {
                     View coverCellView = layoutManager.findViewByPosition(coverRow);
                    if (coverCellView instanceof ProfileCoverCell) {
                        ((ProfileCoverCell)coverCellView).getAvatarImageView().getImageReceiver().setVisible(true, true);
                    }
                }
            //    });
            // }
        }
    }

    @Override
    public void onStoryClicked(int position) {
        // Open StoryViewer for (currentUser or currentChat) at story item `position`
        // This requires access to the list of stories held by ProfileCoverCell or this activity
        // Example:
        // ArrayList<TLRPC.StoryItem> stories = ... get stories from ProfileCoverCell or this activity ...
        // if (stories != null && position < stories.size()) {
        //     TLRPC.StoryItem storyItem = stories.get(position);
        //     StoryViewer.getInstance().setParentActivity(getParentActivity());
        //     StoryViewer.getInstance().open(getContext(), storyItem, ...);
        // }
    }
    @Override public void onStoryLongClicked(int position) { /* TODO */ }
    @Override public void onAddStoryClicked() { /* TODO: Open story creation */ }
    @Override public void onStoriesScrolled(int firstVisibleItem, int visibleItemCount, int totalItemCount) { /* Optional */ }

    @Override public void onAnimationStart(ProfileGiftCell cell, ProfileGiftCell.AnimationState state) { /* TODO */ }
    @Override public void onAnimationEnd(ProfileGiftCell cell, ProfileGiftCell.AnimationState state) { /* TODO */ }
    @Override public void onAnimationUpdate(ProfileGiftCell cell, float progress) { /* TODO */ }
    @Override public void onGiftClicked(int giftIndex) { /* TODO */ }
    @Override public void onStateChanged(ProfileGiftCell.AnimationState newState) { /* TODO */ }


    private class ProfileAdapter extends RecyclerListView.SelectionAdapter {
        private Context context;
        public ProfileAdapter(Context context) { this.context = context; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_COVER_CELL:
                    ProfileCoverCell coverCell = new ProfileCoverCell(context);
                    coverCell.setDelegate(ProfileActivityEnhanced.this);
                    coverCell.setStoriesListener(ProfileActivityEnhanced.this);
                    coverCell.setInitialCoverHeight(AndroidUtilities.dp(280));
                    view = coverCell;
                    holder = new RecyclerListView.Holder(view);
                    break;
                // ... other cases similarly assign to holder ...
                case TYPE_DEFAULT_CELL:
                    ProfileDefaultCell defaultCell = new ProfileDefaultCell(context);
                    view = defaultCell;
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_GIFT_CELL:
                    ProfileGiftCell giftCell = new ProfileGiftCell(context);
                    giftCell.setAnimationListener(ProfileActivityEnhanced.this);
                    view = giftCell;
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_CHANNEL_CELL:
                    view = new ProfileChannelCell(ProfileActivityEnhanced.this, ProfileActivityEnhanced.this.getResourceProvider());
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_GROUP_CELL:
                    view = new ProfileGroupCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_BUSINESS_CELL:
                    view = new ProfileBusinessCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_BOT_CELL:
                    view = new ProfileBotCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_PHONE_ROW:
                case TYPE_USERNAME_ROW:
                    view = new TextDetailCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_BIO_ROW:
                    view = new AboutLinkCell(context, ProfileActivityEnhanced.this);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_SHARED_MEDIA_ROW:
                case TYPE_NOTIFICATIONS_ROW:
                case TYPE_ADD_MEMBER_ROW:
                    view = new TextCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_HEADER_TEXT_ROW:
                case TYPE_MEMBERS_HEADER_ROW:
                    view = new HeaderCell(context);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_MEMBER_ROW:
                    view = new UserCell(context, AndroidUtilities.dp(16), 0, true);
                    holder = new RecyclerListView.Holder(view);
                    break;
                case TYPE_EMPTY_ROW:
                default:
                    // Access rowData safely using holder.getAdapterPosition() - but holder is null here
                    // This was problematic. Let's define holder before switch or handle default differently.
                    // For now, assuming a fixed height for safety if holder is not available.
                    int height = AndroidUtilities.dp(8);
                    // The following line was causing an error because holder was not initialized for all paths.
                    // It's now initialized in each case block.
                    // int currentPosition = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
                    // if (rowData != null && currentPosition != RecyclerView.NO_POSITION && currentPosition < rowData.size() && rowData.get(currentPosition) instanceof Integer) {
                    //    height = (Integer) rowData.get(currentPosition);
                    // }
                    // A better way for default, if holder is guaranteed:
                    if (holder != null) {
                         int currentPositionInDefault = holder.getAdapterPosition();
                         if (rowData != null && currentPositionInDefault != RecyclerView.NO_POSITION && currentPositionInDefault < rowData.size() && rowData.get(currentPositionInDefault) instanceof Integer) {
                            height = (Integer) rowData.get(currentPositionInDefault);
                         }
                    }
                    view = new EmptyCell(context, height);
                    if (holder == null) holder = new RecyclerListView.Holder(view); // Should not happen if all cases assign holder
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            // return new RecyclerListView.Holder(view); // This was original, now return holder
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            Object data = (position >= 0 && position < rowData.size()) ? rowData.get(position) : null;

            boolean needsDivider = true;
            if (position + 1 < getItemCount()) {
                int nextType = getItemViewType(position + 1);
                if (nextType == TYPE_HEADER_TEXT_ROW || nextType == TYPE_MEMBERS_HEADER_ROW || nextType == TYPE_EMPTY_ROW) {
                    needsDivider = false;
                }
            } else {
                needsDivider = false;
            }
            if (viewType == TYPE_COVER_CELL) needsDivider = false;


            switch (viewType) {
                case TYPE_COVER_CELL:
                    ProfileCoverCell coverCell = (ProfileCoverCell) holder.itemView;
                    if (tdCurrentUser != null) coverCell.setTdUser(tdCurrentUser, tdCurrentUserFullInfo);
                    else if (tdCurrentChat != null) coverCell.setTdChat(tdCurrentChat, tdCurrentChatFullInfo);
                    break;
                case TYPE_DEFAULT_CELL:
                    ProfileDefaultCell defaultCell = (ProfileDefaultCell) holder.itemView;
                    if (data instanceof TdApi.User) defaultCell.setTdUser((TdApi.User) data, tdCurrentUserFullInfo);
                    defaultCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_GIFT_CELL:
                    ProfileGiftCell giftCell = (ProfileGiftCell) holder.itemView;
                    if (data instanceof List) giftCell.setTdData(tdCurrentUser, (List<ProfileGiftCell.GiftItem>) data); // Assuming GiftItem is compatible
                    giftCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_CHANNEL_CELL:
                    ProfileChannelCell channelCell = (ProfileChannelCell) holder.itemView;
                    if (data instanceof TdApi.Chat) channelCell.setTdChat((TdApi.Chat)data, tdCurrentChatFullInfo);
                    channelCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_GROUP_CELL:
                    ProfileGroupCell groupCell = (ProfileGroupCell) holder.itemView;
                    if (data instanceof TdApi.Chat) groupCell.setTdData((TdApi.Chat)data, tdCurrentChatFullInfo);
                    groupCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_BUSINESS_CELL:
                    ProfileBusinessCell businessCell = (ProfileBusinessCell) holder.itemView;
                    if (data instanceof TdApi.UserFullInfo) businessCell.setTdData((TdApi.UserFullInfo)data);
                    businessCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_BOT_CELL:
                    ProfileBotCell botCell = (ProfileBotCell) holder.itemView;
                    if (data instanceof TdApi.User) botCell.setTdData((TdApi.User)data, tdCurrentUserFullInfo);
                    botCell.setDrawDivider(needsDivider);
                    break;
                case TYPE_PHONE_ROW:
                    TextDetailCell phoneCell = (TextDetailCell) holder.itemView;
                    if (data instanceof String) phoneCell.setTextAndValue((String)data, LocaleController.getString("PhoneMobile", R.string.PhoneMobile), needsDivider);
                    break;
                case TYPE_USERNAME_ROW:
                    TextDetailCell usernameCell = (TextDetailCell) holder.itemView;
                     if (data instanceof String) usernameCell.setTextAndValue((String)data, LocaleController.getString("Username", R.string.Username), needsDivider);
                    break;
                case TYPE_BIO_ROW:
                    AboutLinkCell bioCell = (AboutLinkCell) holder.itemView;
                    if (data instanceof String) bioCell.setTextAndValue((String)data, LocaleController.getString("UserBio", R.string.UserBio), needsDivider);
                    break;
                case TYPE_SHARED_MEDIA_ROW:
                    TextCell sharedMedia = (TextCell) holder.itemView;
                    sharedMedia.setTextAndIcon(LocaleController.getString("SharedMedia", R.string.SharedMedia), R.drawable.profile_media, needsDivider);
                    break;
                case TYPE_NOTIFICATIONS_ROW:
                    TextCell notifications = (TextCell) holder.itemView;
                    // Mute status will need to come from TdApi.Chat.notificationSettings or similar
                    boolean muted = false; // Placeholder
                    if (tdCurrentChat != null && tdCurrentChat.notificationSettings != null) {
                        // Check if mute_for is in the future
                        muted = tdCurrentChat.notificationSettings.muteFor > (System.currentTimeMillis() / 1000);
                    } else if (tdCurrentUser != null && tdCurrentUser.id != 0 && tdCurrentUserFullInfo != null) {
                        // For user chats, check global notification settings or if TdApi.User has notification settings
                        // This part needs more detailed logic based on how TdApi stores user notification settings
                        // Example: if (tdCurrentUserFullInfo.notificationSettings != null) muted = ...
                    }
                    notifications.setTextAndIconAndValue(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds), R.drawable.profile_notifications, muted ? LocaleController.getString("NotificationsMuted", R.string.NotificationsMuted) : LocaleController.getString("NotificationsUnmuted", R.string.NotificationsUnmuted), needsDivider);
                    break;
                case TYPE_ADD_MEMBER_ROW:
                     TextCell addMember = (TextCell) holder.itemView;
                     addMember.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                     addMember.setTextAndIcon(LocaleController.getString("AddMember", R.string.AddMember), R.drawable.profile_add_member, needsDivider);
                    break;
                case TYPE_HEADER_TEXT_ROW:
                case TYPE_MEMBERS_HEADER_ROW:
                    HeaderCell header = (HeaderCell) holder.itemView;
                    if (data instanceof String) header.setText((String)data);
                    break;
                case TYPE_MEMBER_ROW:
                    UserCell memberCell = (UserCell) holder.itemView;
                    if (data instanceof TdApi.User) memberCell.setTdData((TdApi.User)data, null, null, 0);
                    memberCell.setNeedDivider(needsDivider);
                    break;
                case TYPE_EMPTY_ROW:
                    if (holder.itemView instanceof EmptyCell && data instanceof Integer) {
                        ((EmptyCell) holder.itemView).setHeight((Integer)data);
                    }
                    break;
            }
        }

        @Override
        public int getItemCount() { return rowTypes.size(); }
        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= rowTypes.size()) return TYPE_EMPTY_ROW; // Fallback
            return rowTypes.get(position);
        }
        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_SHARED_MEDIA_ROW || type == TYPE_NOTIFICATIONS_ROW ||
                   type == TYPE_ADD_MEMBER_ROW || type == TYPE_MEMBER_ROW ||
                   type == TYPE_PHONE_ROW || type == TYPE_USERNAME_ROW || type == TYPE_BIO_ROW ||
                   (holder.itemView instanceof ProfileCoverCell && ((ProfileCoverCell)holder.itemView).isClickable()); // Cover cell might have clickable areas
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        if (listView != null) {
            ProfileCoverCell.getThemeDescriptions(themeDescriptions);
            ProfileDefaultCell.getThemeDescriptions(themeDescriptions, listView);
            ProfileGiftCell.getThemeDescriptions(themeDescriptions, listView);
            ProfileChannelCell.getThemeDescriptions(themeDescriptions, listView);
            ProfileGroupCell.getThemeDescriptions(themeDescriptions, listView);
            ProfileBusinessCell.getThemeDescriptions(themeDescriptions, listView);
            ProfileBotCell.getThemeDescriptions(themeDescriptions, listView);
            // Add for other common cells used, e.g., TextCell, HeaderCell, UserCell
            // TextCell.getThemeDescriptions(themeDescriptions, listView);
            // HeaderCell.getThemeDescriptions(themeDescriptions, listView);
            // UserCell.getThemeDescriptions(themeDescriptions, listView);
        }
        return themeDescriptions;
    }
}
