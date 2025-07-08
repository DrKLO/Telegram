package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CorrectlyMeasuringTextView;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.KitKatInsetsFrameLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Dialogs.DialogsActivity;
import org.telegram.ui.Profile.ProfileStoriesView;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.StoryViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

// Import new cell types
import org.telegram.ui.cells.ProfileHeaderCell;
import org.telegram.ui.cells.ProfileActionsCell;
import org.telegram.ui.cells.ProfileInfoCell;
// Import animation and gesture handlers
import org.telegram.ui.components.ProfileAnimationManager;
import org.telegram.ui.components.ProfileGestureHandler;


public class NewProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private EmptyTextProgressView emptyView;

    private FrameLayout avatarContainer;
    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private ChatAvatarContainer chatAvatarContainer; // For shared element transition if needed

    private UndoView undoView;

    private long userId;
    private long chatId;
    private long dialogId;
    private int bCount; // Bot commands count
    private boolean creatingChat; // If we are creating a new chat
    private TLRPC.EncryptedChat currentEncryptedChat;
    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;
    private TLRPC.BotInfo botInfo;
    private TLRPC.ChatFull chatFullInfo;
    private TLRPC.UserFull userFullInfo;

    private int selectedUser; // For group user selection context menu

    // Row types
    private int emptyRow;
    private int headerRow;
    private int actionsRow;
    private int phoneRow;
    private int usernameRow;
    private int bioRow;
    private int locationRow; // For business/channel
    private int workingHoursRow; // For business
    private int membersEndRow;
    private int membersSectionRow;
    private int membersRow;
    private int sharedMediaRow;
    private int settingsNotificationsRow;
    private int settingsTimerRow; // Auto-delete timer
    private int reportRow;
    private int blockRow; // Block/Unblock user or Leave group/channel
    private int addToContactsRow;
    private int shareContactRow;
    private int editContactRow;
    private int dataUsageRow; // Storage usage
    private int commonGroupsRow;
    private int groupsInCommonRow; // Section for groups in common
    private int channelInfoRow; // Channel specific info
    private int userInfoRow; // User specific info (like last seen)
    private int botInfoRow; // Bot specific info (commands)
    private int businessInfoRow; // Business specific info
    private int topicInfoRow; // Group topic info
    private int giftInfoRow; // Gift info
    private int rowCount;

    private boolean userBlocked;
    private boolean isBot;
    private boolean isChat; // If current screen is for a group/channel

    private ProfileAnimationManager animationManager;
    private ProfileGestureHandler gestureHandler;

    private final static int add_contact = 1;
    private final static int block_contact = 2;
    private final static int share_contact = 3;
    private final static int edit_contact = 4;
    private final static int delete_contact = 5;
    // ... other menu items from original ProfileActivity

    public NewProfileActivity(Bundle args) {
        super(args);
        userId = args.getLong("user_id", 0);
        chatId = args.getLong("chat_id", 0);
        // ... other initializations from original ProfileActivity
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.blockedUsersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userFullInfoDidLoad);
        // ... other observers

        if (userId != 0) {
            currentUser = getMessagesController().getUser(userId);
            if (currentUser == null) {
                // Load user if not in cache
                getMessagesController().loadFullUser(getMessagesController().getUser(userId), classGuid, false);
            } else {
                isBot = currentUser.bot;
                userFullInfo = getMessagesController().getUserFull(userId);
                 if (userFullInfo == null) {
                    getMessagesController().loadFullUser(currentUser, classGuid, true);
                }
            }
        } else if (chatId != 0) {
            currentChat = getMessagesController().getChat(chatId);
            if (currentChat == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    currentChat = getMessagesStorage().getChatSync(chatId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentChat != null) {
                    getMessagesController().putChat(currentChat, true);
                } else {
                    return false; // Can't load chat
                }
            }
            chatFullInfo = getMessagesController().getChatFull(chatId);
            if (chatFullInfo == null) {
                 getMessagesController().loadFullChat(chatId, classGuid, true);
            }
            isChat = true;
        } else {
            return false; // No user or chat ID provided
        }

        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        // ... remove other observers
        if (animationManager != null) {
            animationManager.onDestroy();
        }
    }

    @SuppressLint("InflateParams")
    @Override
    public View createView(Context context) {
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        actionBar.setBackgroundColor(Theme.getColor(Theme.key_profile_actionBar));
        actionBar.setItemsColor(Theme.getColor(Theme.key_profile_actionBarItemsColor), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_profile_actionBarSelectorColor), false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false); // Will be handled by animation manager
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21);


        avatarContainer = new FrameLayout(context);
        avatarContainer.setPivotX(0);
        avatarContainer.setPivotY(0);

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21)); // Example, adjust as per design
        avatarContainer.addView(avatarImage, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.TOP, 66, 0, 0, 0)); // Example position

        avatarDrawable = new AvatarDrawable();

        if (currentUser != null) {
            avatarDrawable.setInfo(currentUser);
            avatarImage.setForUserOrChat(currentUser, avatarDrawable);
            actionBar.setTitle(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
        } else if (currentChat != null) {
            avatarDrawable.setInfo(currentChat);
            avatarImage.setForUserOrChat(currentChat, avatarDrawable);
            actionBar.setTitle(currentChat.title);
        }
        // Add ActionBar menu items
        ActionBarMenu menu = actionBar.createMenu();
        // Example: More options button
        // menu.addItem(0, R.drawable.ic_ab_other).addSubMenu(...);
        // This will be populated based on user/chat type later in updateProfileData

        listView = new RecyclerListView(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false; // Optimization for complex views
            }
        };
        listView.setTag(6);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setClipToPadding(false);

        // Add padding for header/actionbar if not handled by animation manager initial state
        // listView.setPadding(0, ActionBar.getCurrentActionBarHeight(), 0, 0);


        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        // Initialize AnimationManager and GestureHandler
        animationManager = new ProfileAnimationManager(this, frameLayout, actionBar, listView, avatarContainer, avatarImage);
        gestureHandler = new ProfileGestureHandler(this, frameLayout, listView, animationManager);
        frameLayout.setOnTouchListener((v, event) -> gestureHandler.onTouchEvent(event)); // For gesture handling on the main container

        // Add views in correct order for z-index (ListView below ActionBar initially)
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(actionBar); // ActionBar on top
        frameLayout.addView(avatarContainer); // Avatar container might be part of header or separate for animation


        // Empty view for loading/no data state
        emptyView = new EmptyTextProgressView(context);
        emptyView.setShowAtCenter(true);
        // emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        // emptyView.showProgress();
        // frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // listView.setEmptyView(emptyView); // Show empty view when adapter is empty

        undoView = new UndoView(context);
        frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 8, 0, 8, 8));

        updateProfileData();

        if (animationManager != null) {
            animationManager.onFragmentViewCreated(fragmentView);
        }

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        emptyRow = -1; // Placeholder for potential empty space
        headerRow = -1;
        actionsRow = -1;
        phoneRow = -1;
        usernameRow = -1;
        bioRow = -1;
        locationRow = -1;
        workingHoursRow = -1;
        membersRow = -1;
        membersSectionRow = -1;
        membersEndRow = -1;
        sharedMediaRow = -1;
        settingsNotificationsRow = -1;
        settingsTimerRow = -1;
        reportRow = -1;
        blockRow = -1;
        addToContactsRow = -1;
        shareContactRow = -1;
        editContactRow = -1;
        dataUsageRow = -1;
        commonGroupsRow = -1;
        groupsInCommonRow = -1;
        channelInfoRow = -1;
        userInfoRow = -1;
        botInfoRow = -1;
        businessInfoRow = -1;
        topicInfoRow = -1;
        giftInfoRow = -1;


        // Header is always present
        headerRow = rowCount++;
        actionsRow = rowCount++;

        if (currentUser != null) {
            if (!UserObject.isDeleted(currentUser) && !currentUser.bot) {
                 if (currentUser.phone != null && currentUser.phone.length() != 0 && currentUser.access_hash != 0 && getUserConfig().getClientUserId() != currentUser.id) {
                    boolean hide = getMessagesController().getContact(userId) != null;
                    if (hide) {
                         TLRPC.UserFull userFull = getMessagesController().getUserFull(userId);
                        if (userFull != null && userFull.phone_calls_available && !userFull.phone_calls_private) {
                            hide = false;
                        }
                    }
                    if (!hide) {
                        phoneRow = rowCount++;
                    }
                }
            }
            if (currentUser.username != null && currentUser.username.length() > 0) {
                usernameRow = rowCount++;
            }
            userFullInfo = getMessagesController().getUserFull(userId);
            if (userFullInfo != null && !TextUtils.isEmpty(userFullInfo.about)) {
                bioRow = rowCount++;
            }

            // Add more rows based on user type and info (bot, business, etc.)
            if (currentUser.bot) {
                botInfoRow = rowCount++;
            } else {
                 // General user info like last seen, common groups
                userInfoRow = rowCount++;
                if (getMessagesController().getContact(userId) == null && !UserObject.isService(currentUser.id)) {
                    addToContactsRow = rowCount++;
                } else {
                    editContactRow = rowCount++;
                }
                shareContactRow = rowCount++;
            }
            settingsNotificationsRow = rowCount++;
            sharedMediaRow = rowCount++;
            if (!UserObject.isService(currentUser.id)) {
                 blockRow = rowCount++; // Block/Unblock
            }


        } else if (currentChat != null) {
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                // Channel specific info
                if (!TextUtils.isEmpty(currentChat.username)) {
                     usernameRow = rowCount++; // Channel link
                }
                if (chatFullInfo != null && !TextUtils.isEmpty(chatFullInfo.about)) {
                    bioRow = rowCount++; // Channel description
                }
                channelInfoRow = rowCount++;
            } else {
                // Group specific info
                if (chatFullInfo != null && !TextUtils.isEmpty(chatFullInfo.about)) {
                    bioRow = rowCount++; // Group description
                }
            }
            settingsNotificationsRow = rowCount++;
            sharedMediaRow = rowCount++;

            if (!ChatObject.isScam(currentChat)) {
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup && !currentChat.creator && currentChat.admin_rights == null && currentChat.left) {
                    // Join channel button
                } else if (ChatObject.isLeftFromChat(currentChat)) {
                    // Join group button
                } else {
                     blockRow = rowCount++; // Leave group/channel
                }
            }
        }

        // Example: Add a gift info row if applicable
        // if (shouldShowGiftInfo()) {
        // giftInfoRow = rowCount++;
        // }


        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateProfileData() {
        if (avatarImage == null || listAdapter == null) {
            return;
        }

        if (currentUser != null) {
            avatarImage.setForUserOrChat(currentUser, avatarDrawable);
            actionBar.setTitle(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            // Update subtitle (last seen, online status) in animation manager or header cell
        } else if (currentChat != null) {
            avatarImage.setForUserOrChat(currentChat, avatarDrawable);
            actionBar.setTitle(currentChat.title);
            // Update subtitle (members count, online count)
        }

        // Update action bar menu items based on profile type
        actionBar.createMenu().clearItems();
        if (currentUser != null && !UserObject.isService(currentUser.id)) {
            if (getMessagesController().getContact(userId) == null) {
                actionBar.createMenu().addItem(add_contact, R.drawable.profile_addcontact); // Replace with actual icon
            }
            actionBar.createMenu().addItem(block_contact, R.drawable.profile_block); // Replace with actual icon
            // ... add more items: Share, Edit, Delete
        } else if (currentChat != null) {
            // ... add chat specific menu items: Search, Mute, Leave
        }


        updateRows(); // This will call notifyDataSetChanged
        if (animationManager != null) {
            animationManager.onProfileDataUpdated(currentUser, currentChat, userFullInfo, chatFullInfo);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        // Update user/chat info if needed
        // fixLayout(); // From original ProfileActivity, may need similar logic
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (currentUser != null) {
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateProfileData();
                }
            } else if (currentChat != null) {
                 if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_ADMINS) != 0) {
                    updateProfileData();
                }
            }
        } else if (id == NotificationCenter.contactsDidLoad) {
            updateProfileData();
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (currentChat != null && chatFull.id == currentChat.id) {
                chatFullInfo = chatFull;
                updateProfileData();
            }
        } else if (id == NotificationCenter.userFullInfoDidLoad) {
             long uid = (Long) args[0];
            if (currentUser != null && currentUser.id == uid) {
                userFullInfo = (TLRPC.UserFull) args[1];
                updateProfileData();
            }
        }
        // ... handle other notifications
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        // Handle results from other activities (e.g., image picker for avatar)
        // if (imageUpdater != null) {
        // imageUpdater.onActivityResult(requestCode, resultCode, data);
        // }
    }

    // --- ImageUpdater.ImageUpdaterDelegate implementation ---
    @Override
    public void didUploadPhoto(TLRPC.InputFile file, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, TLRPC.VideoSize videoBigSize, TLRPC.VideoSize videoSmallSize, boolean isVideo, TLRPC.InputGeoPoint point, int personal_photo) {
        // Handle new avatar uploaded
        if (userId != 0) {
            MessagesController.getInstance(currentAccount).changeUserProfilePhoto(file, video, videoStartTimestamp, bigSize, smallSize, null);
        } else if (chatId != 0) {
            MessagesController.getInstance(currentAccount).changeChatAvatar(chatId, file, video, videoStartTimestamp, bigSize, smallSize, null);
        }
    }

    @Override
    public String getInitialSearchString() {
        return null; // Not used here
    }
    // --- End ImageUpdater.ImageUpdaterDelegate ---


    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            // Enable clicks for actionable rows
            return type == 0 || type == 2 || type == 3 || type == 4; // TextCell, ProfileActionsCell, etc.
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: // Header Cell
                    view = new ProfileHeaderCell(mContext, animationManager); // Pass animationManager for coordination
                     if (animationManager != null) {
                        ((ProfileHeaderCell) view).setAvatarImageView(animationManager.getAvatarImageView());
                        ((ProfileHeaderCell) view).setAvatarContainer(animationManager.getAvatarContainer());
                    }
                    break;
                case 1: // Actions Cell
                    view = new ProfileActionsCell(mContext);
                    // Set callbacks for action buttons
                    ((ProfileActionsCell)view).setDelegate(new ProfileActionsCell.ProfileActionsCellDelegate() {
                        @Override
                        public void didClickMessage() {
                            if (currentUser != null) {
                                Bundle args = new Bundle();
                                args.putLong("user_id", currentUser.id);
                                presentFragment(new ChatActivity(args));
                            }
                        }
                        @Override
                        public void didClickCall() {
                            if (currentUser != null) {
                                VoIPHelper.startCall(currentUser, null, null, false, getParentActivity(), NewProfileActivity.this);
                            }
                        }
                        @Override
                        public void didClickVideoCall() {
                             if (currentUser != null) {
                                VoIPHelper.startCall(currentUser, null, null, true, getParentActivity(), NewProfileActivity.this);
                            }
                        }
                        @Override
                        public void didClickMore() {
                            // Show more options sheet
                        }
                    });
                    break;
                case 2: // Info Cell (Generic TextCell or custom ProfileInfoCell)
                    // For simplicity, using TextCell here. Replace with ProfileInfoCell for richer content.
                    view = new TextCell(mContext);
                    break;
                case 3: // Section Cell
                    view = new GraySectionCell(mContext);
                    break;
                case 4: // Empty Cell for spacing
                    view = new EmptyCell(mContext, AndroidUtilities.dp(8)); // Example height
                    break;
                // Add more cases for other cell types (Bot, Business, Group, Channel, Gift, etc.)
                default: // Fallback to a simple TextCell or a loading cell
                    view = new LoadingCell(mContext); // Or TextCell
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: // Header Cell
                    ProfileHeaderCell headerCell = (ProfileHeaderCell) holder.itemView;
                    if (currentUser != null) {
                        headerCell.setData(currentUser, userFullInfo, chatFullInfo, currentAccount);
                    } else if (currentChat != null) {
                        headerCell.setData(currentChat, userFullInfo, chatFullInfo, currentAccount);
                    }
                    break;
                case 1: // Actions Cell
                    ProfileActionsCell actionsCell = (ProfileActionsCell) holder.itemView;
                    if (currentUser != null) {
                        actionsCell.setData(currentUser, userFullInfo, chatFullInfo);
                    } else if (currentChat != null) {
                        // Configure actions for chat
                    }
                    break;
                case 2: // Info Cell
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == phoneRow) {
                        textCell.setText(LocaleController.getString("Phone", R.string.Phone), currentUser.phone, R.drawable.profile_phone, true); // Replace with actual icon
                    } else if (position == usernameRow) {
                        textCell.setText(LocaleController.getString("Username", R.string.Username), "@" + (currentUser != null ? currentUser.username : currentChat.username), R.drawable.profile_info, true); // Replace with actual icon
                    } else if (position == bioRow) {
                         String bio = "";
                        if (userFullInfo != null && !TextUtils.isEmpty(userFullInfo.about)) {
                            bio = userFullInfo.about;
                        } else if (chatFullInfo != null && !TextUtils.isEmpty(chatFullInfo.about)) {
                            bio = chatFullInfo.about;
                        }
                        textCell.setText(LocaleController.getString("UserBio", R.string.UserBio), bio, R.drawable.profile_info, true);
                    } else if (position == settingsNotificationsRow) {
                        textCell.setText(LocaleController.getString("Notifications", R.string.Notifications), "", R.drawable.profile_notifications, true);
                    } else if (position == sharedMediaRow) {
                        textCell.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia), "", R.drawable.profile_photos, true);
                    } else if (position == blockRow) {
                        if (currentUser != null) {
                            textCell.setText(userBlocked ? LocaleController.getString("Unblock", R.string.Unblock) : LocaleController.getString("BlockUser", R.string.BlockUser), "", R.drawable.profile_block, false);
                            textCell.setNeedDivider(false);
                            textCell.getTextView().setTextColor(Theme.getColor(userBlocked ? Theme.key_windowBackgroundWhiteRedText : Theme.key_windowBackgroundWhiteRedText));
                        } else if (currentChat != null) {
                            // Logic for Leave Group/Channel
                            textCell.setText(LocaleController.getString("LeaveChannel", R.string.LeaveChannel),"", R.drawable.profile_delete, false);
                             textCell.getTextView().setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));

                        }

                    }
                    // ... Bind data for other info rows
                    break;
                case 3: // Section Cell
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == membersSectionRow) {
                        sectionCell.setText(LocaleController.getString("Members", R.string.Members));
                    } else if (position == groupsInCommonRow) {
                         sectionCell.setText(LocaleController.getString("GroupsInCommon", R.string.GroupsInCommon));
                    }
                    break;
                // ... Bind data for other cell types
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == actionsRow) return 1;
            if (position == phoneRow || position == usernameRow || position == bioRow ||
                position == settingsNotificationsRow || position == sharedMediaRow || position == blockRow /* ... other info rows */) return 2;
            if (position == membersSectionRow || position == groupsInCommonRow) return 3;
            if (position == emptyRow) return 4; // Empty cell for spacing

            // Define more view types for specific cells (Bot, Business, Group, Channel, Gift)
            // Example:
            // if (position == botInfoRow) return 5; // Assuming 5 is for ProfileBotInfoCell
            // if (position == giftInfoRow) return 6; // Assuming 6 is for ProfileGiftCell

            return 2; // Default to info cell for simplicity
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        // Add ThemeDescriptions for all new UI elements
        // Example:
        // arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        // arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_profile_actionBar));
        // ... for all cells, text colors, icons, etc.
        // Ensure new keys are defined in Theme.java if custom colors are used extensively

        SimpleThemeDescription.addToList(arrayList, listView, null, null, null, null, Theme.key_windowBackgroundWhite);
        SimpleThemeDescription.addToList(arrayList, actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, Theme.key_profile_actionBar);
        SimpleThemeDescription.addToList(arrayList, actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, Theme.key_profile_actionBarItemsColor);
        SimpleThemeDescription.addToList(arrayList, actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, Theme.key_profile_actionBarSelectorColor);
        SimpleThemeDescription.addToList(arrayList, actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, Theme.key_profile_actionBarItemsColor); // Assuming title color is same as items

        // For ProfileHeaderCell (assuming it has methods to get its views)
        // arrayList.add(new ThemeDescription(headerCell.getNameTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_title));
        // arrayList.add(new ThemeDescription(headerCell.getStatusTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_profile_status));

        // For ProfileActionsCell
        // arrayList.add(new ThemeDescription(actionsCell.getMessageButton(), ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionIcon));
        // arrayList.add(new ThemeDescription(actionsCell.getMessageButton(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionIconPressed));

        // For TextCells used in info rows
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        // Generic TextCell theming (assuming common keys are used)
        arrayList.add(new ThemeDescription(null, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(null, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        arrayList.add(new ThemeDescription(null, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        // Red text for block/leave
        arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText));


        if (undoView != null) {
            arrayList.addAll(undoView.getThemeDescriptions());
        }

        return arrayList;
    }

    // Method to be called by ProfileGestureHandler or AnimationManager to close the fragment
    public voiddismissFragment() {
        finishFragment();
    }

    public FrameLayout getFragmentView() {
        return (FrameLayout) fragmentView;
    }

    public BaseFragment getParentFragment() {
        return parentFragment;
    }

    public Activity getParentActivity() {
        return parentActivity;
    }

    @Override
    public boolean onBackPressed() {
        if (animationManager != null && animationManager.isAnimating()) {
            return true; // Prevent back press during animation
        }
        if (animationManager != null && animationManager.isExpanded()) {
            animationManager.minimizeProfile(); // Or trigger the minimize animation
            return true;
        }
        return super.onBackPressed();
    }

    // This method would be part of the shared element transition setup
    public void prepareSharedElementTransition(View sharedElement) {
        if (animationManager != null) {
            // animationManager.setSharedElement(sharedElement);
            // Potentially trigger enlarge animation from here or let AnimationManager handle it
            // based on fragment lifecycle or a specific call.
        }
    }

    public void onTransitionAnimationStart(boolean isOpen, boolean sharedTransition) {
        if (animationManager != null) {
            if (isOpen) {
                // animationManager.startEnlargeAnimation(sharedTransition);
            } else {
                // animationManager.startMinimizeAnimation();
            }
        }
    }

    public void onTransitionAnimationEnd(boolean isOpen, boolean sharedTransition) {
         if (animationManager != null) {
            animationManager.onTransitionAnimationEnded(isOpen);
        }
    }
}
