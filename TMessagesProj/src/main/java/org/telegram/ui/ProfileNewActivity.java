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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
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
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.TopicsFragment;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesStorage;

import androidx.core.content.ContextCompat;

public class ProfileNewActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private CoordinatorLayout coordinatorLayout;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private Toolbar toolbar;
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
    
    // Profile data
    private long dialogId;
    private long chatId;
    private long userId;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private boolean isMuted;
    
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
    
    // Row tracking variables (similar to ProfileActivity)
    private int phoneRow = -1;
    private int usernameRow = -1;
    private int bioRow = -1;
    private int sharedMediaRow = -1;
    private int rowCount;
    
    public ProfileNewActivity(Bundle args) {
        super(args);
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
    
    public ProfileNewActivity(Bundle args, Object sharedMediaPreloader) {
        this(args);
        // SharedMediaPreloader parameter for compatibility
    }
    
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }
    
    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }
    
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Profile", R.string.AppName));
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
        
        // Inflate the collapsing layout
        LayoutInflater inflater = LayoutInflater.from(context);
        coordinatorLayout = (CoordinatorLayout) inflater.inflate(R.layout.profile_collapsing_layout, null);
        
        // Get references to views
        appBarLayout = coordinatorLayout.findViewById(R.id.app_bar_layout);
        collapsingToolbarLayout = coordinatorLayout.findViewById(R.id.collapsing_toolbar_layout);
        toolbar = coordinatorLayout.findViewById(R.id.collapsed_toolbar);
        
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
        
        // Setup theme
        updateTheme();
        
        fragmentView = coordinatorLayout;
        return fragmentView;
    }
    
    private void setupToolbar() {
        // Set up toolbar navigation (back button) - use existing back button string reference
        toolbar.setNavigationOnClickListener(v -> finishFragment());
        
        // Set up toolbar menu
        Menu toolbarMenu = toolbar.getMenu();
        toolbarMenu.add(0, 10, 0, "").setIcon(R.drawable.ic_ab_other).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10) {
                // The 3-dot menu is handled by the ActionBar menu system
                return true;
            }
            return false;
        });
        
        // Setup collapsing toolbar
        collapsingToolbarLayout.setTitle("");
        collapsingToolbarLayout.setCollapsedTitleTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        collapsingToolbarLayout.setExpandedTitleColor(Color.TRANSPARENT);
    }
    
    private void setupHeaderContent(Context context) {
        // Create avatar using BackupImageView 
        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarImageView.setRoundRadius(dp(42));
        avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
        
        // Initialize avatar drawable
        avatarDrawable = new AvatarDrawable();
        
        // Create name text view
        nameTextView = new TextView(context);
        nameTextView.setTextColor(Color.WHITE);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(Gravity.CENTER);
        nameContainer.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        
        // Create online status text view
        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Color.WHITE);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setGravity(Gravity.CENTER);
        onlineTextView.setAlpha(0.8f);
        onlineContainer.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        
        // Create header buttons using provided vector drawable icons
        messageButton = new BubbleButton(context, R.drawable.profile_header_message, LocaleController.getString("SendMessage", R.string.SendMessage));
        muteButton = new BubbleButton(context, R.drawable.profile_header_unmute, LocaleController.getString("Unmute", R.string.Unmute));
        callButton = new BubbleButton(context, R.drawable.profile_header_call, LocaleController.getString("Call", R.string.Call));
        videoButton = new BubbleButton(context, R.drawable.profile_header_video, LocaleController.getString("VideoCall", R.string.VideoCall));
        
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
        // avatarContainer.addView(giftParticleSystem, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        
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
        // Create RecyclerListView with existing ProfileActivity content structure
        RecyclerListView listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        
        // Create adapter with profile content
        updateRowsIds();
        FullProfileContentAdapter adapter = new FullProfileContentAdapter(context);
        listView.setAdapter(adapter);
        
        // Add click handler that uses processOnClickOrPress logic
        listView.setOnItemClickListener((view, position, x, y) -> {
            processOnClickOrPress(position, view, x, y);
        });
        
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }
    
    private void setupCollapsedTitles(Context context) {
        // Create collapsed name text view
        collapsedNameTextView = new TextView(context);
        collapsedNameTextView.setTextColor(Color.WHITE);
        collapsedNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        collapsedNameTextView.setTypeface(AndroidUtilities.bold());
        collapsedNameTextView.setSingleLine(true);
        collapsedNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        collapsedNameTextView.setGravity(Gravity.START);
        collapsedNameContainer.addView(collapsedNameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));
        
        // Create collapsed online status text view
        collapsedOnlineTextView = new TextView(context);
        collapsedOnlineTextView.setTextColor(Color.WHITE);
        collapsedOnlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        collapsedOnlineTextView.setGravity(Gravity.START);
        collapsedOnlineTextView.setAlpha(0.8f);
        collapsedOnlineContainer.addView(collapsedOnlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));
    }
    
    private void setupScrollBehavior() {
        // Add scroll listener to handle collapse/expand animations
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                int totalScrollRange = appBarLayout.getTotalScrollRange();
                float offsetAlpha = (float) Math.abs(verticalOffset) / totalScrollRange;
                
                // Fade out avatar and buttons as we scroll up
                avatarContainer.setAlpha(1f - offsetAlpha);
                headerButtonsContainer.setAlpha(1f - offsetAlpha);
                
                // Fade in collapsed title as we scroll up
                collapsedTitleContainer.setAlpha(offsetAlpha);
                
                // Scale avatar down as we scroll
                float scale = 1f - (offsetAlpha * 0.3f); // Scale down to 70%
                avatarContainer.setScaleX(scale);
                avatarContainer.setScaleY(scale);
            }
        });
    }
    
    private void updateRowsIds() {
        rowCount = 0;
        
        if (currentUser != null) {
            // Phone number row
            if (!TextUtils.isEmpty(currentUser.phone)) {
                phoneRow = rowCount++;
            }
            
            // Username row  
            if (!TextUtils.isEmpty(currentUser.username)) {
                usernameRow = rowCount++;
            }
            
            // Bio row
            bioRow = rowCount++;
            
            // Shared media row
            sharedMediaRow = rowCount++;
            
        } else if (currentChat != null) {
            // Chat-specific rows
            bioRow = rowCount++;
            sharedMediaRow = rowCount++;
        }
    }
    
    private boolean processOnClickOrPress(final int position, final View view, final float x, final float y) {
        if (position == phoneRow && currentUser != null) {
            // Handle phone number click - show call options
            return true;
        } else if (position == usernameRow && currentUser != null) {
            // Handle username click - show QR or copy
            showQRCode();
            return true;
        } else if (position == bioRow) {
            // Handle bio click
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
    
    // Full content adapter that mirrors ProfileActivity structure  
    private class FullProfileContentAdapter extends RecyclerListView.SelectionAdapter {
        private static final int VIEW_TYPE_TEXT_DETAIL = 2;
        private static final int VIEW_TYPE_TEXT = 4;
        private static final int VIEW_TYPE_DIVIDER = 5;
        private static final int VIEW_TYPE_SHADOW = 7;
        private static final int VIEW_TYPE_SHARED_MEDIA = 13;
        private static final int VIEW_TYPE_ABOUT_LINK = 3;
        
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
            if (position == phoneRow || position == usernameRow) {
                return VIEW_TYPE_TEXT_DETAIL;
            } else if (position == bioRow) {
                return VIEW_TYPE_ABOUT_LINK;
            } else if (position == sharedMediaRow) {
                return VIEW_TYPE_SHARED_MEDIA;
            }
            return VIEW_TYPE_TEXT;
        }
        
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_TEXT_DETAIL:
                    view = new TextDetailCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_ABOUT_LINK:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case VIEW_TYPE_SHARED_MEDIA:
                    view = new TextCell(context, getResourceProvider());
                    break;
                default:
                    view = new TextCell(context, getResourceProvider());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }
        
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder == null || holder.itemView == null) return;
            
            View view = holder.itemView;
            
            if (position == phoneRow && currentUser != null && !TextUtils.isEmpty(currentUser.phone)) {
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
                
            } else if (position == bioRow) {
                TextCell cell = (TextCell) view;
                String bio = "";
                if (currentUser != null && !TextUtils.isEmpty(currentUser.first_name)) {
                    bio = LocaleController.getString("UserBio", R.string.UserBio);
                } else if (currentChat != null && !TextUtils.isEmpty(currentChat.title)) {
                    bio = "Chat Information";
                }
                if (TextUtils.isEmpty(bio)) {
                    bio = LocaleController.getString("DescriptionPlaceholder", R.string.DescriptionPlaceholder);
                }
                cell.setText(bio, false);
                
            } else if (position == sharedMediaRow) {
                TextCell cell = (TextCell) view;
                cell.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia), false);
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
            // Update user profile - handle bot differentiation
            if (currentUser.bot) {
                name = currentUser.first_name;
                onlineStatus = LocaleController.getString("Bot", R.string.Bot);
            } else {
                name = UserObject.getUserName(currentUser);
                onlineStatus = LocaleController.formatUserStatus(currentAccount, currentUser);
            }
            
            nameTextView.setText(name);
            onlineTextView.setText(onlineStatus);
            
            // Set up avatar drawable and load avatar image using ProfileActivity patterns
            avatarDrawable.setInfo(currentAccount, currentUser);
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_BIG);
            ImageLocation thumbLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_SMALL);
            
            if (imageLocation != null) {
                avatarImageView.setImage(imageLocation, "50_50", thumbLocation, "50_50", avatarDrawable, currentUser);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }
            
            // Update button visibility for user profiles
            messageButton.setVisibility(View.VISIBLE);
            muteButton.setVisibility(View.VISIBLE);
            callButton.setVisibility(!currentUser.bot && currentUser.phone != null && !currentUser.phone.isEmpty() ? View.VISIBLE : View.GONE);
            videoButton.setVisibility(!currentUser.bot && currentUser.phone != null && !currentUser.phone.isEmpty() ? View.VISIBLE : View.GONE);
            
        } else if (currentChat != null) {
            // Update chat profile
            name = currentChat.title;
            onlineStatus = LocaleController.formatPluralString("Members", currentChat.participants_count);
            
            nameTextView.setText(name);
            onlineTextView.setText(onlineStatus);
            
            // Set up avatar drawable and load chat avatar image
            avatarDrawable.setInfo(currentAccount, currentChat);
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_BIG);
            ImageLocation thumbLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_SMALL);
            
            if (imageLocation != null) {
                avatarImageView.setImage(imageLocation, "50_50", thumbLocation, "50_50", avatarDrawable, currentChat);
            } else {
                avatarImageView.setImageDrawable(avatarDrawable);
            }
            
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
        toolbar.setBackgroundColor(headerColor); // Match toolbar to header color
        
        toolbar.setNavigationIcon(getParentActivity().getDrawable(R.drawable.ic_ab_back));
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }
        
        // Update menu icon color to white for visibility
        Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            }
        }
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
                otherItem.addSubItem(edit_info, R.drawable.msg_edit, LocaleController.getString("EditInfo", R.string.EditInfo));
                otherItem.addSubItem(add_photo, R.drawable.msg_addphoto, LocaleController.getString("AddPhoto", R.string.AddPhoto));
                otherItem.addSubItem(edit_color, R.drawable.menu_profile_colors, LocaleController.getString("ProfileColorEdit", R.string.ProfileColorEdit));
                otherItem.addSubItem(set_username, R.drawable.menu_username_change, LocaleController.getString("ProfileUsernameEdit", R.string.ProfileUsernameEdit));
                otherItem.addSubItem(copy_link_profile, R.drawable.msg_link2, LocaleController.getString("ProfileCopyLink", R.string.ProfileCopyLink));
                otherItem.addSubItem(logout, R.drawable.msg_leave, LocaleController.getString("LogOut", R.string.LogOut));
            } else {
                // Other user menu
                boolean isBot = currentUser.bot;
                boolean userBlocked = getMessagesController().blockePeers.indexOfKey(currentUser.id) >= 0;
                
                if (isBot) {
                    otherItem.addSubItem(share, R.drawable.msg_share, LocaleController.getString("BotShare", R.string.BotShare));
                    otherItem.addSubItem(bot_privacy, R.drawable.menu_privacy_policy, LocaleController.getString("BotPrivacyPolicy", R.string.BotPrivacyPolicy));
                    otherItem.addSubItem(report, R.drawable.msg_report, LocaleController.getString("ReportBot", R.string.ReportBot));
                    if (userBlocked) {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block, LocaleController.getString("Unblock", R.string.Unblock));
                    } else {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block2, LocaleController.getString("DeleteAndBlock", R.string.DeleteAndBlock));
                    }
                } else {
                    // Regular user menu
                    boolean isContact = getContactsController().isContact(currentUser.id);
                    
                    if (!isContact) {
                        otherItem.addSubItem(add_contact, R.drawable.msg_addcontact, LocaleController.getString("AddContact", R.string.AddContact));
                    } else {
                        otherItem.addSubItem(edit_contact, R.drawable.msg_edit, LocaleController.getString("EditContact", R.string.EditContact));
                        otherItem.addSubItem(delete_contact, R.drawable.msg_delete, LocaleController.getString("DeleteContact", R.string.DeleteContact));
                    }
                    
                    otherItem.addSubItem(share_contact, R.drawable.msg_share, LocaleController.getString("ShareContact", R.string.ShareContact));
                    
                    if (userBlocked) {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block, LocaleController.getString("Unblock", R.string.Unblock));
                    } else {
                        otherItem.addSubItem(block_contact, R.drawable.msg_block, LocaleController.getString("BlockContact", R.string.BlockContact));
                    }
                    
                    if (!UserObject.isDeleted(currentUser)) {
                        otherItem.addSubItem(gift_premium, R.drawable.msg_gift_premium, LocaleController.getString("ProfileSendAGift", R.string.ProfileSendAGift));
                        otherItem.addSubItem(start_secret_chat, R.drawable.msg_secret, LocaleController.getString("StartEncryptedChat", R.string.StartEncryptedChat));
                    }
                    
                    otherItem.addSubItem(report, R.drawable.msg_report, LocaleController.getString("ReportChat", R.string.ReportChat));
                }
                
                otherItem.addSubItem(add_shortcut, R.drawable.msg_link, LocaleController.getString("AddShortcut", R.string.AddShortcut));
            }
        } else if (currentChat != null) {
            // Chat menu
            if (ChatObject.isChannel(currentChat)) {
                // Channel menu
                if (ChatObject.canManageCalls(currentChat)) {
                    otherItem.addSubItem(call_item, R.drawable.msg_voicechat, LocaleController.getString("StartVoipChannel", R.string.StartVoipChannel));
                }
                
                if (ChatObject.hasAdminRights(currentChat)) {
                    otherItem.addSubItem(statistics, R.drawable.msg_stats, LocaleController.getString("Statistics", R.string.Statistics));
                    otherItem.addSubItem(edit_channel, R.drawable.msg_edit, LocaleController.getString("EditAdminRights", R.string.EditAdminRights));
                }
                
                otherItem.addSubItem(search_members, R.drawable.msg_search, LocaleController.getString("SearchMembers", R.string.SearchMembers));
                otherItem.addSubItem(leave_group, R.drawable.msg_leave, LocaleController.getString("LeaveChannelMenu", R.string.LeaveChannelMenu));
                otherItem.addSubItem(share, R.drawable.msg_share, LocaleController.getString("BotShare", R.string.BotShare));
                
                // Note: linked_chat_id might not be available, using a simple check
                // if (currentChat.linked_chat_id != 0) {
                //     otherItem.addSubItem(view_discussion, R.drawable.msg_discussion, LocaleController.getString("ViewDiscussion", R.string.ViewDiscussion));
                // }
                
                otherItem.addSubItem(channel_stories, R.drawable.msg_archive, LocaleController.getString("OpenChannelArchiveStories", R.string.OpenChannelArchiveStories));
            } else {
                // Group menu
                if (ChatObject.canManageCalls(currentChat)) {
                    otherItem.addSubItem(call_item, R.drawable.msg_voicechat, LocaleController.getString("StartVoipChat", R.string.StartVoipChat));
                }
                
                otherItem.addSubItem(search_members, R.drawable.msg_search, LocaleController.getString("SearchMembers", R.string.SearchMembers));
                otherItem.addSubItem(leave_group, R.drawable.msg_leave, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                
                if (ChatObject.hasAdminRights(currentChat)) {
                    otherItem.addSubItem(edit_channel, R.drawable.msg_edit, LocaleController.getString("EditAdminRights", R.string.EditAdminRights));
                }
            }
            
            otherItem.addSubItem(add_shortcut, R.drawable.msg_link, LocaleController.getString("AddShortcut", R.string.AddShortcut));
        }
    }
    
    // Menu handler methods
    private void handleBlockContact() {
        if (currentUser == null) return;
        
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
        if (currentUser == null) return;
        Bundle args = new Bundle();
        args.putLong("user_id", currentUser.id);
        args.putBoolean("addContact", true);
        presentFragment(new ContactAddActivity(args));
    }
    
    private void handleShareContact() {
        if (currentUser == null) return;
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity fragment1, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, TopicsFragment topicsFragment) {
                // Handle sharing contact to selected dialogs
                return true;
            }
        });
        presentFragment(fragment);
    }
    
    private void handleEditContact() {
        if (currentUser == null) return;
        Bundle args = new Bundle();
        args.putLong("user_id", currentUser.id);
        presentFragment(new ContactAddActivity(args));
    }
    
    private void handleDeleteContact() {
        if (currentUser == null) return;
        
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(currentUser);
        
        getContactsController().deleteContact(users, false);
        createActionBarMenu(true);
    }
    
    private void handleLeaveGroup() {
        if (currentChat == null) return;
        // TODO: Implement leave group functionality
        // This would typically show a confirmation dialog and then leave the chat
    }
    
    private void handleEditChannel() {
        if (currentChat == null) return;
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
        if (currentUser == null) return;
        // showDialog(new GiftSheet(getContext(), currentAccount, currentUser.id, null, null)); // TODO: Add proper import
    }
    
    private void handleStartSecretChat() {
        if (currentUser == null) return;
        // TODO: Implement secret chat creation
    }
    
    private void handleBotPrivacy() {
        if (currentUser == null) return;
        // BotWebViewAttachedSheet.openPrivacy(currentAccount, currentUser.id); // TODO: Add proper import
    }
    
    private void handleStatistics() {
        if (currentChat == null) return;
        // presentFragment(StatisticActivity.create(currentChat, false)); // TODO: Add proper import
    }
    
    private void handleSearchMembers() {
        if (currentChat == null) return;
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
            // VoIPHelper.showGroupCallAlert(this, currentChat, null, false, getAccountInstance()); // TODO: Add proper import
        }
    }
    
    private void handleEditColor() {
        // presentFragment(new PeerColorActivity(0).startOnProfile().setOnApplied(this)); // TODO: Add proper import
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
        if (currentChat == null) return;
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
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateMuteButton();
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
        if (currentUser == null) return null;
        return new UserInfoWrapper(currentUser);
    }
    
    // Simple wrapper class to match BackButtonMenu expectations
    public static class UserInfoWrapper {
        public TLRPC.User user;
        
        public UserInfoWrapper(TLRPC.User user) {
            this.user = user;
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
            container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
            
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
}