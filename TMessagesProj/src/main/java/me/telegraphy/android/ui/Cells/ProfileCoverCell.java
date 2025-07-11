package me.telegraphy.android.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.telegraphy.android.messenger.AndroidUtilities;
import me.telegraphy.android.messenger.ChatObject;
import me.telegraphy.android.messenger.ConnectionsManager;
import me.telegraphy.android.messenger.ImageLocation;
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.MessagesController;
import me.telegraphy.android.messenger.NotificationCenter;
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.messenger.UserConfig;
import me.telegraphy.android.messenger.UserObject;
import me.telegraphy.android.tgnet.TLRPC;
import me.telegraphy.android.ui.ActionBar.SimpleTextView;
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
import me.telegraphy.android.ui.Components.AvatarDrawable;
import me.telegraphy.android.ui.Components.BackupImageView;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.RLottieImageView;
import me.telegraphy.android.ui.Components.RecyclerListView;
import me.telegraphy.android.ui.Components.ScamDrawable;
import me.telegraphy.android.ui.Stories.StoriesController;
import me.telegraphy.android.ui.Stories.StoryCircleView;


import java.util.ArrayList;
import java.util.List;

public class ProfileCoverCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView coverImageView;
    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;

    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private RLottieImageView premiumStarView;
    private ImageView verifiedIcon;
    private ScamDrawable scamDrawable;

    private FrameLayout storiesContainer;
    private RecyclerListView storiesRecyclerView;
    private StoriesAdapter storiesAdapter;
    private ArrayList<TLRPC.StoryItem> currentStoryItems = new ArrayList<>();
    private boolean hasUnreadStories;

    private Paint scrimPaint;
    private Paint overlayPaint;
    private float overlayAlpha = 0f;

    private int coverHeight = AndroidUtilities.dp(280);
    private int initialCoverHeight = coverHeight; // Store initial height for reset or calculations
    private int scrollOffset;

    private TLRPC.User currentUser;
    private TLRPC.UserFull currentUserFull;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull currentChatFull;
    private int currentAccount = UserConfig.selectedAccount;

    private ProfileCoverCellDelegate delegate;
    private StoriesListener storiesListener;


    private boolean isOwnProfile;
    private FrameLayout addStoryButtonContainer;

    // Expansion and animation properties
    private boolean isExpanded = true; // Assuming it starts expanded
    private float expansionProgress = 1f;


    public interface ProfileCoverCellDelegate {
        void onAvatarClicked();
        // Add other delegate methods if needed, e.g., for cover photo click
    }

    public interface StoriesListener {
        void onStoryClicked(int position);
        void onStoryLongClicked(int position); // Example, if needed
        void onAddStoryClicked();
        void onStoriesScrolled(int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }


    public ProfileCoverCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        currentAccount = UserConfig.selectedAccount; // Ensure currentAccount is initialized

        coverImageView = new BackupImageView(context);
        coverImageView.getImageReceiver().setAllowStartAnimation(false); // Generally good for profile covers
        coverImageView.setContentDescription(LocaleController.getString("ProfileCoverPhoto", R.string.ProfileCoverPhoto));
        addView(coverImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Scrim will be drawn in onDraw

        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(42)); // Example radius, adjust as needed
        avatarDrawable = new AvatarDrawable();
        avatarImageView.setContentDescription(LocaleController.getString("UserAvatar", R.string.UserAvatar));
        addView(avatarImageView, LayoutHelper.createFrame(84, 84, Gravity.BOTTOM | Gravity.LEFT, 16, 0, 0, 68)); // Example layout params
        avatarImageView.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onAvatarClicked();
            }
        });

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Color.WHITE);
        nameTextView.setTextSize(20);
        nameTextView.setGravity(Gravity.LEFT); // Or CENTER_HORIZONTAL depending on design
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(0.6f), 0x40000000); // Subtle shadow for readability
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 110, 0, 40, 96));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextColor(0xB3FFFFFF); // Semi-transparent white
        statusTextView.setTextSize(14);
        statusTextView.setGravity(Gravity.LEFT);
        statusTextView.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(0.6f), 0x40000000);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 110, 0, 40, 74));

        premiumStarView = new RLottieImageView(context);
        premiumStarView.setAnimation(R.raw.premium_star, 24, 24); // Adjust size as needed
        premiumStarView.setVisibility(View.GONE); // Initially hidden
        addView(premiumStarView, LayoutHelper.createFrame(24, 24, Gravity.BOTTOM | Gravity.LEFT)); // Position near name

        verifiedIcon = new ImageView(context);
        verifiedIcon.setImageResource(R.drawable.verified_profile); // Ensure this drawable exists
        verifiedIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        verifiedIcon.setVisibility(View.GONE); // Initially hidden
        addView(verifiedIcon, LayoutHelper.createFrame(20, 20, Gravity.BOTTOM | Gravity.LEFT)); // Position near name

        // Stories Container
        storiesContainer = new FrameLayout(context);
        storiesRecyclerView = new RecyclerListView(context);
        storiesRecyclerView.setOrientation(LinearLayoutManager.HORIZONTAL);
        storiesRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        storiesAdapter = new StoriesAdapter(context);
        storiesRecyclerView.setAdapter(storiesAdapter);
        storiesRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        storiesRecyclerView.setClipToPadding(false);
        storiesRecyclerView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0); // Horizontal padding for items
        storiesContainer.addView(storiesRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.BOTTOM)); // Height for story circles
        addView(storiesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(60), Gravity.BOTTOM, 0,0,0,0)); // Positioned at the bottom of the cover
        storiesContainer.setVisibility(View.GONE); // Initially hidden or based on story availability

        scamDrawable = new ScamDrawable(13, ScamDrawable.TYPE_PROFILE); // Context for scam text
        scamDrawable.setColor(Color.WHITE); // For visibility on dark cover
    }

    public void setDelegate(ProfileCoverCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setStoriesListener(StoriesListener listener) {
        this.storiesListener = listener;
    }


    public void setUser(TLRPC.User user, TLRPC.UserFull userFull) {
        if (user == null) return;
        this.currentUser = user;
        this.currentUserFull = userFull;
        this.currentChat = null; // Clear chat data
        this.currentChatFull = null;
        this.isOwnProfile = user.id == UserConfig.getInstance(currentAccount).getClientUserId();


        avatarDrawable.setInfo(currentAccount, currentUser);
        avatarImageView.setForUserOrChat(currentUser, avatarDrawable);

        nameTextView.setText(UserObject.getUserName(currentUser));
        updateStatus();

        premiumStarView.setVisibility(currentUser.premium ? View.VISIBLE : View.GONE);
        if (currentUser.premium) {
            premiumStarView.playAnimation();
        }
        verifiedIcon.setVisibility(currentUser.verified ? View.VISIBLE : View.GONE);

        // Cover photo logic
        TLRPC.Photo photoToSet = null;
        if (currentUserFull != null && currentUserFull.profile_photo != null && currentUserFull.profile_photo.id != 0) {
            photoToSet = currentUserFull.profile_photo;
        } else if (currentUserFull != null && currentUserFull.personal_photo != null && currentUserFull.personal_photo.id != 0 && !isOwnProfile) {
            // If not own profile and personal_photo exists, use it.
            // This logic might need adjustment based on exact requirements for "cover" vs "profile" vs "personal" photos.
            photoToSet = currentUserFull.personal_photo;
        } // Add fallback to user.photo if other options are null

        if (photoToSet != null) {
            coverImageView.setImage(ImageLocation.getForPhoto(photoToSet, TLRPC.PhotoSize.LAZY_STRIPPED_TYPE), "1280_720_filter", null, null, 0);
        } else if (currentUser.photo != null && currentUser.photo.photo_big != null) {
            // Fallback to regular profile photo for cover if specific cover/personal photo isn't set
            coverImageView.setImage(ImageLocation.getForUser(currentUser, ImageLocation.TYPE_BIG), "1280_720_filter", avatarDrawable, null, currentUser, 0);
        } else {
            // Fallback to generated color if no photo
            coverImageView.setImageDrawable(Theme.createProfileDialogBackground(avatarDrawable.getColor(), avatarDrawable.getColor()));
        }
        // Load and display stories
        loadAndSetStoriesForUser(user.id);
        requestLayout(); // Important after setting data that might change dimensions
    }

    public void setChat(TLRPC.Chat chat, TLRPC.ChatFull chatFull) {
        if (chat == null) return;
        this.currentChat = chat;
        this.currentChatFull = chatFull;
        this.currentUser = null; // Clear user data
        this.currentUserFull = null;
        this.isOwnProfile = false; // Chats are not "own" profile in the same way users are

        avatarDrawable.setInfo(currentAccount, currentChat);
        avatarImageView.setForUserOrChat(currentChat, avatarDrawable);

        nameTextView.setText(currentChat.title);
        updateStatus(); // Update status based on chat info

        premiumStarView.setVisibility(View.GONE); // Chats don't have premium star directly
        verifiedIcon.setVisibility(currentChat.verified ? View.VISIBLE : View.GONE);

        // Cover photo logic for chats
        if (currentChat.photo != null && currentChat.photo.photo_big != null) {
            coverImageView.setImage(ImageLocation.getForChat(currentChat, ImageLocation.TYPE_BIG), "1280_720_filter", avatarDrawable, null, currentChat, 0);
        } else {
            coverImageView.setImageDrawable(Theme.createProfileDialogBackground(avatarDrawable.getColor(), avatarDrawable.getColor()));
        }
        loadAndSetStoriesForChat(chat.id);
        requestLayout();
    }

    private void updateStatus() {
        String statusText = "";
        int statusColor = 0xB3FFFFFF; // Default semi-transparent white

        if (currentUser != null) {
            if (UserObject.isService(currentUser.id) || UserObject.isDeleted(currentUser)) {
                statusText = ""; // No status for service/deleted accounts
            } else if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                statusText = LocaleController.getString("Online", R.string.Online); // "Online" for self
                statusColor = Theme.getColor(Theme.key_profile_statusOnline); // Use theme color for online
            } else {
                // For other users, get their online status
                String customStatus = MessagesController.getInstance(currentAccount).getUserPrintingString(currentUser.id);
                if (customStatus != null) {
                    statusText = customStatus;
                    statusColor = Theme.getColor(Theme.key_chat_status); // Color for "typing..." etc.
                } else {
                    statusText = LocaleController.formatUserStatus(currentAccount, currentUser);
                    if (currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                         statusColor = Theme.getColor(Theme.key_profile_statusOnline);
                    } else {
                         statusColor = 0xB3FFFFFF; // Default if offline and no custom status
                    }
                }
            }
        } else if (currentChat != null) {
            // Status for chats (groups/channels)
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                if (currentChat.participants_count != 0) {
                    statusText = LocaleController.formatPluralString("Subscribers", currentChat.participants_count);
                } else if (currentChatFull != null && currentChatFull.participants_count != 0) {
                    statusText = LocaleController.formatPluralString("Subscribers", currentChatFull.participants_count);
                } else {
                    statusText = LocaleController.getString("Channel", R.string.Channel);
                }
            } else { // Group or megagroup
                 if (currentChat.participants_count != 0) {
                    statusText = LocaleController.formatPluralString("Members", currentChat.participants_count);
                } else if (currentChatFull != null && currentChatFull.participants != null && currentChatFull.participants.participants != null) {
                    statusText = LocaleController.formatPluralString("Members", currentChatFull.participants.participants.size());
                } else {
                    statusText = LocaleController.getString("Group", R.string.Group);
                }
            }
        }
        statusTextView.setText(statusText);
        statusTextView.setTextColor(statusColor);
    }


    private void loadAndSetStoriesForUser(long userId) {
        StoriesController.UserStories userStories = StoriesController.getInstance(currentAccount).getUserStories(userId);
        ArrayList<TLRPC.StoryItem> items = new ArrayList<>();
        boolean hasUnread = false;
        if (userStories != null && userStories.items != null) {
            items.addAll(userStories.items);
            hasUnread = userStories.hasUnread;
        }
        setStories(items, hasUnread);
    }

    private void loadAndSetStoriesForChat(long chatId) {
        // For chats, stories are usually associated with users within the chat, not the chat itself directly.
        // If this app supports chat stories differently, this logic would need to be adapted.
        // For now, assuming no direct stories for a chat entity itself.
        setStories(new ArrayList<>(), false);
    }


    public void setStories(ArrayList<TLRPC.StoryItem> stories, boolean hasUnread) {
        currentStoryItems.clear();
        if (stories != null && !stories.isEmpty()) {
            currentStoryItems.addAll(stories);
        }
        this.hasUnreadStories = hasUnread; // Store unread status
        if (storiesAdapter != null) {
            storiesAdapter.notifyDataSetChanged();
        }
        // Show stories container only if there are stories OR it's own profile (for "add story" button)
        storiesContainer.setVisibility(currentStoryItems.isEmpty() && !isOwnProfile ? View.GONE : View.VISIBLE);
    }

    // Call this method when the cover height needs to change due to scroll/animation
    public void setCoverHeight(int height) {
        if (this.coverHeight != height) {
            this.coverHeight = height;
            requestLayout(); // This will trigger onMeasure
        }
    }

    // Used to restore the initial height, perhaps after an animation
    public void setInitialCoverHeight(int height) {
        this.initialCoverHeight = height;
        this.coverHeight = height; // Also set current height
        requestLayout();
    }


    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    // This method will be called by ProfileActivityEnhanced based on RecyclerView scroll
    public void setScrollOffset(int offset) {
        if (this.scrollOffset != offset) {
            this.scrollOffset = offset;

            // Parallax for cover image (more subtle)
            coverImageView.setTranslationY(offset * 0.3f); // Slower movement for parallax

            // Avatar animation (scale and translation)
            // This needs to be coordinated with the overall layout progress
            // For now, just a simple translation. Scale might be handled by expansionProgress.
            avatarImageView.setTranslationY(offset * 0.5f);

            // Text elements translation
            nameTextView.setTranslationY(offset * 0.6f);
            statusTextView.setTranslationY(offset * 0.6f);
            premiumStarView.setTranslationY(offset * 0.6f);
            verifiedIcon.setTranslationY(offset * 0.6f);

            // Stories container translation (moves faster to disappear quicker or reveal from bottom)
            storiesContainer.setTranslationY(offset * 0.8f);

            invalidate(); // Redraw if needed for custom drawing effects
        }
    }

    // Alpha for an overlay, typically for the action bar background
    public void setOverlayAlpha(float alpha) {
        if (overlayAlpha != alpha) {
            overlayAlpha = alpha;
            invalidate(); // To redraw the overlay
        }
    }

    public void setStoriesVisible(boolean visible) {
        storiesContainer.setVisibility(visible && (!currentStoryItems.isEmpty() || isOwnProfile) ? VISIBLE : GONE);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = coverHeight; // Use the current coverHeight which might be animated
        // Ensure the height doesn't go below a minimum (e.g., action bar height if that's the collapsed state)
        // desiredHeight = Math.max(desiredHeight, ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight);

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY));

        // Adjust layout of name, status, icons based on expansion or other factors if needed
        if (nameTextView.getVisibility() == VISIBLE) {
            // Example: Measure name text to position icons next to it
            nameTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(110 + 40 + 24 + 4 + 20 + 4), MeasureSpec.AT_MOST), // available width
                               MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST)); // max height
            float nameWidth = nameTextView.getTextWidth();
            float currentLeft = nameTextView.getLeft() + nameWidth + AndroidUtilities.dp(4); // Start positioning after name

            if (premiumStarView.getVisibility() == VISIBLE) {
                premiumStarView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY),
                                      MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
                // Position premiumStarView relative to nameTextView's bottom and the calculated currentLeft
                // This assumes nameTextView is already laid out or its position is known.
                // The translations below are conceptual; actual positioning depends on how nameTextView is placed.
                premiumStarView.setTranslationX(currentLeft - AndroidUtilities.dp(110)); // Adjusting for nameTextView's left margin
                premiumStarView.setTranslationY(nameTextView.getTop() + (nameTextView.getMeasuredHeight() - AndroidUtilities.dp(24)) / 2f - AndroidUtilities.dp(96)); // Align vertically with name
                currentLeft += AndroidUtilities.dp(24 + 2); // Add width of star and some padding
            }
            if (verifiedIcon.getVisibility() == VISIBLE) {
                verifiedIcon.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY),
                                     MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
                verifiedIcon.setTranslationX(currentLeft - AndroidUtilities.dp(110));
                verifiedIcon.setTranslationY(nameTextView.getTop() + (nameTextView.getMeasuredHeight() - AndroidUtilities.dp(20)) / 2f - AndroidUtilities.dp(96));
            }
        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdatedUser);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        // Initial status update
        updateStatus();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdatedUser);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;

        if (id == NotificationCenter.storiesUpdatedUser) {
            if (args.length > 0 && args[0] instanceof Long) {
                long uid = (Long) args[0];
                if (currentUser != null && currentUser.id == uid) {
                    // Reload stories for this user
                    StoriesController.UserStories updatedStories = StoriesController.getInstance(currentAccount).getUserStories(uid);
                    ArrayList<TLRPC.StoryItem> newItems = new ArrayList<>();
                    if (updatedStories != null && updatedStories.items != null) {
                        newItems.addAll(updatedStories.items);
                    }
                    setStories(newItems, updatedStories != null && updatedStories.hasUnread);
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (args.length > 0 && args[0] instanceof Integer) {
                int mask = (Integer) args[0];
                if ((mask & MessagesController.UPDATE_MASK_USER_PRINT) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateStatus();
                }
                // Update user/chat object if name or avatar changed
                if ((mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                     if (currentUser != null && args.length > 1 && args[1] instanceof Long && (Long)args[1] == currentUser.id) {
                         setUser(MessagesController.getInstance(currentAccount).getUser(currentUser.id), currentUserFull);
                     } else if (currentChat != null && args.length > 1 && args[1] instanceof Long && (Long)args[1] == currentChat.id) { // Assuming chat ID is positive in this context
                         setChat(MessagesController.getInstance(currentAccount).getChat(currentChat.id), currentChatFull);
                     }
                }
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw scrim gradient from 30% height to bottom
        LinearGradient gradient = new LinearGradient(0, getMeasuredHeight() * 0.3f, 0, getMeasuredHeight(),
                                    new int[]{Color.TRANSPARENT, 0x99000000}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
        scrimPaint.setShader(gradient);
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);

        // Draw action bar overlay if alpha > 0
        if (overlayAlpha > 0) {
            overlayPaint.setColor(Theme.getColor(Theme.key_actionBarDefault)); // Or a specific profile action bar color
            overlayPaint.setAlpha((int) (overlayAlpha * 255));
            canvas.drawRect(0, 0, getWidth(), me.telegraphy.android.ui.ActionBar.ActionBar.getCurrentActionBarHeight(), overlayPaint);
        }

        // Draw scam badge if user is marked as scam
        if (currentUser != null && MessagesController.getInstance(currentAccount).isUserScam(currentUser.id)) {
            // Position scamDrawable next to the name or in a designated spot
            // This requires nameTextView to be measured and laid out first.
            // Example positioning (adjust based on actual layout):
            float nameTextWidth = nameTextView.getTextWidth();
            // Assuming nameTextView is laid out relative to avatarImageView and margins
            float nameLayoutLeft = getPaddingLeft() + AndroidUtilities.dp(110); // Based on avatar and its margin
            float nameLayoutTop = getMeasuredHeight() - AndroidUtilities.dp(96) - nameTextView.getMeasuredHeight(); // Approximate top based on bottom gravity

            scamDrawable.setBounds((int) (nameLayoutLeft + nameTextWidth + AndroidUtilities.dp(4)), // 4dp padding after name
                                 (int) (nameLayoutTop + (nameTextView.getMeasuredHeight() - AndroidUtilities.dp(13)) / 2f), // Vertically centered with name
                                 (int) (nameLayoutLeft + nameTextWidth + AndroidUtilities.dp(4 + 13)), // 13dp width for scam icon
                                 (int) (nameLayoutTop + (nameTextView.getMeasuredHeight() + AndroidUtilities.dp(13)) / 2f));
            scamDrawable.draw(canvas);
        }
    }

    // Adapter for Stories RecyclerView
    private class StoriesAdapter extends RecyclerListView.Adapter {
        private Context context;

        public StoriesAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) { // Story item
                StoryCircleView storyCircleView = new StoryCircleView(context, StoryCircleView.TYPE_PROFILE_STORIES_LIST);
                storyCircleView.setSmallRadius(AndroidUtilities.dp(26)); // Example size, adjust
                storyCircleView.setGap(AndroidUtilities.dp(2));
                view = storyCircleView;
            } else { // Add story button
                // This is a simplified "add story" button. A real one might be more complex.
                addStoryButtonContainer = new FrameLayout(context);
                ImageView addIcon = new ImageView(context);
                addIcon.setTag("addStoryButtonIcon"); // For theme descriptions
                addIcon.setImageResource(R.drawable.profile_addstory); // Ensure this drawable exists
                addIcon.setScaleType(ImageView.ScaleType.CENTER);
                // Use theme keys for background and icon color
                addIcon.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(52), Theme.getColor(Theme.key_profile_addStoryButtonBackground)));
                addIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_addStoryButtonIcon), PorterDuff.Mode.MULTIPLY));
                addStoryButtonContainer.addView(addIcon, LayoutHelper.createFrame(52, 52, Gravity.CENTER));
                view = addStoryButtonContainer;
            }
            // Ensure items are not overly wide if you have few, or use WRAP_CONTENT if they should size individually
            view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(60), AndroidUtilities.dp(60))); // Fixed size for story circles
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) { // Story Item
                StoryCircleView storyCircleView = (StoryCircleView) holder.itemView;
                int storyIndex = position - (isOwnProfile ? 1 : 0); // Offset if "add story" button is present
                if (storyIndex >= 0 && storyIndex < currentStoryItems.size()) {
                    TLRPC.StoryItem storyItem = currentStoryItems.get(storyIndex);
                    long ownerId = (currentUser != null) ? currentUser.id : ((currentChat != null) ? currentChat.id : 0); // Determine story owner
                    storyCircleView.setStory(ownerId, storyItem); // Method in StoryCircleView to set data
                    storyCircleView.setOnClickListener(v -> {
                        if (storiesListener != null) {
                            storiesListener.onStoryClicked(storyIndex); // Pass index or StoryItem
                        }
                    });
                } else {
                    // Handle potential index out of bounds if logic is complex
                    storyCircleView.clear(); // Clear previous data
                }
            } else { // Add Story Button
                holder.itemView.setOnClickListener(v -> {
                    if (storiesListener != null) {
                        storiesListener.onAddStoryClicked();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            int count = currentStoryItems.size();
            if (isOwnProfile) {
                count++; // For the "add story" button
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (isOwnProfile && position == 0) {
                return 1; // View type for "add story" button
            }
            return 0; // View type for actual story items
        }
    }

    // Method for ProfileActivityEnhanced to get theme descriptions
    public static void getThemeDescriptions(List<ThemeDescription> descriptions) {
        // Theme keys for ProfileCoverCell elements
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_profile_header)); // Example for cover background
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{SimpleTextView.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_profile_title));
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{SimpleTextView.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_profile_statusLine)); // Generic status line
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{SimpleTextView.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_profile_statusOnline)); // Online status specific
        // Stories container and items (conceptual)
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{FrameLayout.class}, new String[]{"storiesContainer"}, null, null, null, Theme.key_profile_storiesContainer));
        // For StoryCircleView items, theming would typically be handled within StoryCircleView itself
        // Add Story button theming
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{ImageView.class}, new String[]{"addStoryButtonIcon"}, null, null, null, Theme.key_profile_addStoryButtonIcon));
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{FrameLayout.class}, new String[]{"addStoryButtonContainer"}, null, null, null, Theme.key_profile_addStoryButtonBackground)); // Assuming this refers to the FrameLayout holding the icon
        // Verified and Scam icons
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{ImageView.class}, new String[]{"verifiedIcon"}, null, null, null, Theme.key_profile_verifiedIcon)); // If you have a specific key
        descriptions.add(new ThemeDescription(ProfileCoverCell.class, 0, new Class[]{ScamDrawable.class}, new String[]{"scamDrawable"}, null, null, null, Theme.key_profile_scamText));
    }
}
