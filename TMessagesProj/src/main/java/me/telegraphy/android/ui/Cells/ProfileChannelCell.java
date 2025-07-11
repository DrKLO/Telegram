package me.telegraphy.android.ui.Cells;

import static me.telegraphy.android.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.telegraphy.android.SQLite.SQLiteCursor;
import me.telegraphy.android.messenger.AndroidUtilities;
import me.telegraphy.android.messenger.DialogObject;
import me.telegraphy.android.messenger.FileLog;
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.MessageObject;
import me.telegraphy.android.messenger.MessagesController;
import me.telegraphy.android.messenger.MessagesStorage;
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.messenger.UserConfig;
import me.telegraphy.android.tgnet.ConnectionsManager;
import me.telegraphy.android.tgnet.NativeByteBuffer;
import me.telegraphy.android.tgnet.TLRPC;
import me.telegraphy.android.ui.ActionBar.BaseFragment;
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;

import me.telegraphy.android.ui.Components.AnimatedFloat;
import me.telegraphy.android.ui.Components.AnimatedTextView;
import me.telegraphy.android.ui.Components.AvatarDrawable;
import me.telegraphy.android.ui.Components.BackupImageView;
import me.telegraphy.android.ui.Components.ClickableAnimatedTextView;
import me.telegraphy.android.ui.Components.CubicBezierInterpolator;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.LoadingDrawable;
import me.telegraphy.android.ui.Components.RecyclerListView;
import me.telegraphy.android.ui.Stories.StoriesController;
import me.telegraphy.android.ui.Stories.StoriesListPlaceProvider;
import me.telegraphy.android.ui.Cells.DialogCell; // Assuming DialogCell is in this package now


import java.util.ArrayList;
import java.util.List;

public class ProfileChannelCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    // Channel specific views (from original ProfileChannelCell)
    private final TextView headerView; // Renamed from titleTextView to avoid conflict
    private final AnimatedTextView subscribersView;
    public final DialogCell dialogCell; // Used to display last message or channel info

    // Gift-related views (from your provided ProfileChannelCell with gifts)
    private LinearLayout giftsContainer;
    private TextView giftsHeaderView;
    private RecyclerView giftsRecyclerView;
    private GiftsAdapter giftsAdapter;
    private View giftsUnderline;

    // Animation properties for gifts
    private boolean giftsVisible = false;
    private AnimatorSet currentGiftAnimation; // Renamed from currentAnimation
    private final ArrayList<GiftItem> giftItems = new ArrayList<>();

    private long currentChannelId = 0;
    private boolean loading;
    private AnimatedFloat loadingAlpha = new AnimatedFloat(320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final LoadingDrawable loadingDrawable;
    private boolean set = false;
    private BaseFragment fragment; // To access fragment context for stories, etc.
    private boolean drawDivider = true;


    // Gift item data class (nested or separate)
    private static class GiftItem {
        String emoji;
        String count; // Or name/description

        GiftItem(String emoji, String count) {
            this.emoji = emoji;
            this.count = count;
        }
    }

    // Callback for gift loading
    private interface GiftLoadCallback {
        void onGiftsLoaded(List<GiftItem> gifts);
        void onError(String error);
    }


    public ProfileChannelCell(BaseFragment parentFragment, Theme.ResourcesProvider parentResourceProvider) {
        super(parentFragment.getContext());
        final Context context = parentFragment.getContext();
        this.fragment = parentFragment; // Store fragment reference
        this.resourcesProvider = parentResourceProvider != null ? parentResourceProvider : fragment.getResourceProvider();

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 22, 16.6f, 22, 0));

        headerView = new TextView(context);
        headerView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerView.setText(LocaleController.getString("ProfileChannel", R.string.ProfileChannel));
        headerLayout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        subscribersView = new ClickableAnimatedTextView(context);
        if (subscribersView.getDrawable() != null) { // Null check for safety
            subscribersView.getDrawable().setHacks(true, true, true);
        }
        subscribersView.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
        subscribersView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // Changed to rmedium for consistency
        subscribersView.setTextSize(dp(11));
        subscribersView.setPadding(dp(4.33f), 0, dp(4.33f), 0);
        subscribersView.setGravity(Gravity.LEFT);
        headerLayout.addView(subscribersView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 17, Gravity.LEFT | Gravity.TOP, 4, 2, 4, 0));

        dialogCell = new DialogCell(null, context, false, true, UserConfig.selectedAccount, this.resourcesProvider);
        dialogCell.setBackgroundColor(0); // Transparent background
        dialogCell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
            @Override
            public void onButtonClicked(DialogCell cell) {}
            @Override
            public void onButtonLongPress(DialogCell cell) {}
            @Override
            public boolean canClickButtonInside() { return true; }

            @Override
            public void openStory(DialogCell cell, Runnable onDone) {
                if (fragment.getMessagesController().getStoriesController().hasStories(cell.getDialogId())) {
                    fragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
                    fragment.getOrCreateStoryViewer().open(fragment.getContext(), cell.getDialogId(), StoriesListPlaceProvider.of(ProfileChannelCell.this));
                }
            }
            @Override
            public void showChatPreview(DialogCell cell) {}
            @Override
            public void openHiddenStories() {
                StoriesController storiesController = fragment.getMessagesController().getStoriesController();
                if (storiesController.getHiddenList().isEmpty()) {
                    return;
                }
                boolean unreadOnly = storiesController.getUnreadState(DialogObject.getPeerDialogId(storiesController.getHiddenList().get(0).peer)) != StoriesController.STATE_READ;
                ArrayList<Long> peerIds = new ArrayList<>();
                for (int i = 0; i < storiesController.getHiddenList().size(); i++) {
                    long dialogId = DialogObject.getPeerDialogId(storiesController.getHiddenList().get(i).peer);
                    if (!unreadOnly || storiesController.getUnreadState(dialogId) != StoriesController.STATE_READ) {
                        peerIds.add(dialogId);
                    }
                }
                fragment.getOrCreateStoryViewer().open(context, null, peerIds, 0, null, null, StoriesListPlaceProvider.of(ProfileChannelCell.this), false);
            }
        });
        dialogCell.avatarStart = 15;
        dialogCell.messagePaddingStart = 83;
        addView(dialogCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        initializeGifts(context);
        updateColors();
        setWillNotDraw(false);

        loadingDrawable = new LoadingDrawable();
        loadingDrawable.setColors(
            Theme.multAlpha(Theme.getColor(Theme.key_listSelector, this.resourcesProvider), 1.25f),
            Theme.multAlpha(Theme.getColor(Theme.key_listSelector, this.resourcesProvider), .8f)
        );
        loadingDrawable.setRadiiDp(8);
    }

    private void initializeGifts(Context context) {
        giftsContainer = new LinearLayout(context);
        giftsContainer.setOrientation(LinearLayout.VERTICAL);
        giftsContainer.setAlpha(0f);
        giftsContainer.setVisibility(GONE);

        giftsHeaderView = new TextView(context);
        giftsHeaderView.setText("🎁 " + LocaleController.getString("Gifts", R.string.Gifts));
        giftsHeaderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        giftsHeaderView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        giftsHeaderView.setPadding(dp(16), dp(16), dp(16), dp(8));
        giftsContainer.addView(giftsHeaderView);

        giftsUnderline = new View(context);
        LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(dp(40), dp(2));
        underlineParams.leftMargin = dp(16);
        underlineParams.bottomMargin = dp(8);
        giftsContainer.addView(giftsUnderline, underlineParams);

        giftsRecyclerView = new RecyclerListView(context);
        GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
        giftsRecyclerView.setLayoutManager(layoutManager);
        giftsRecyclerView.setNestedScrollingEnabled(false);

        giftsAdapter = new GiftsAdapter();
        giftsRecyclerView.setAdapter(giftsAdapter);

        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        recyclerParams.topMargin = dp(8);
        giftsContainer.addView(giftsRecyclerView, recyclerParams);

        // Position giftsContainer below the dialogCell or header, adjust '120' as needed
        addView(giftsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 120, 0, 0));

        // Removed setupGiftData() call from here, will be called in set()
    }

    private void setupGiftData() {
        giftItems.clear();
        if (currentChannelId == 0) {
            if (giftsAdapter != null) giftsAdapter.notifyDataSetChanged();
            return;
        }

        loadChannelGifts(currentChannelId, new GiftLoadCallback() {
            @Override
            public void onGiftsLoaded(List<GiftItem> gifts) {
                AndroidUtilities.runOnUIThread(() -> {
                    giftItems.clear();
                    giftItems.addAll(gifts);
                    if (giftsAdapter != null) giftsAdapter.notifyDataSetChanged();
                    if (!gifts.isEmpty() && !giftsVisible) {
                        showGifts(true);
                    }
                });
            }
            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    giftItems.clear();
                    if (giftsAdapter != null) giftsAdapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void loadChannelGifts(long channelId, GiftLoadCallback callback) {
        // This is a placeholder. In a real app, this would involve:
        // 1. Querying MessagesStorage for messages with gift actions related to this channel.
        // 2. Or, making an API call via TdApiManager to fetch gift information if available through API.
        // For now, let's simulate with some default gifts for demo.
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<GiftItem> loadedGifts = new ArrayList<>();
            if (channelId != 0) { // Only add samples if a channel is set
                loadedGifts.add(new GiftItem("🎁", "Premium Gift"));
                loadedGifts.add(new GiftItem("⭐", "Stars x50"));
                // Add more sample gifts if needed
            }
            callback.onGiftsLoaded(loadedGifts);
        }, 100); // Simulate async loading
    }


    public void showGifts(boolean show) {
        if (giftsVisible == show || giftsContainer == null) return;
        giftsVisible = show;

        if (currentGiftAnimation != null) {
            currentGiftAnimation.cancel();
        }
        currentGiftAnimation = new AnimatorSet();

        if (show) {
            giftsContainer.setVisibility(VISIBLE);
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(giftsContainer, "alpha", 0f, 1f);
            ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(giftsContainer, "translationY", dp(20), 0f);
            currentGiftAnimation.playTogether(alphaAnimator, translationAnimator);
        } else {
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(giftsContainer, "alpha", 1f, 0f);
            ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(giftsContainer, "translationY", 0f, dp(20));
            currentGiftAnimation.playTogether(alphaAnimator, translationAnimator);
        }
        currentGiftAnimation.setDuration(show ? 300 : 250);
        currentGiftAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show && giftsContainer != null) {
                    giftsContainer.setVisibility(GONE);
                }
                currentGiftAnimation = null;
            }
        });
        currentGiftAnimation.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float loadingVal = loadingAlpha.set(this.loading);
        if (loadingVal > 0 && dialogCell != null) {
            loadingDrawable.setAlpha((int) (0xFF * loadingVal));
            // Adjust drawing bounds for loading state based on dialogCell's position and size
            AndroidUtilities.rectTmp.set(
                dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                dialogCell.getY() + dp(38),
                dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .5f,
                dialogCell.getY() + dp(38 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);
            // ... (rest of loadingDrawable drawing as in original) ...
            invalidate();
        }
         if (drawDivider) {
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return loadingDrawable == who || super.verifyDrawable(who);
    }

    public void set(TLRPC.Chat channel, MessageObject messageObject) {
        set(channel, messageObject, false); // Default don't show gifts unless specified
    }

    public void setData(TLRPC.Chat channel, TLRPC.ChatFull chatFull, MessageObject messageObject) {
         set(channel, messageObject, false); // Default behavior for compatibility with ProfileActivityEnhanced
         // You might want to use chatFull here for more detailed channel info display if needed.
    }


    public void set(TLRPC.Chat channel, MessageObject messageObject, boolean showGiftsOnClick) {
        final boolean animated = set; // 'set' is a class member indicating if data was previously set
        final boolean subscribersShown = channel == null || channel.participants_count > 0;

        if (subscribersView != null) {
            subscribersView.cancelAnimation();
            subscribersView.setPivotX(0);
            if (animated) {
                subscribersView.animate().alpha(subscribersShown ? 1f : 0f).scaleX(subscribersShown ? 1f : .8f).scaleY(subscribersShown ? 1f : .8f).setDuration(420).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                subscribersView.setAlpha(subscribersShown ? 1f : 0f);
                subscribersView.setScaleX(subscribersShown ? 1f : 0f); // Corrected from 0f to 1f or 0.8f
                subscribersView.setScaleY(subscribersShown ? 1f : 0f);
            }
        }

        if (channel != null) {
            this.currentChannelId = channel.id;
            if (subscribersView != null) {
                int[] result = new int[1];
                boolean ignoreShort = AndroidUtilities.isAccessibilityScreenReaderEnabled();
                String shortNumber = ignoreShort ? String.valueOf(result[0] = channel.participants_count) : LocaleController.formatShortNumber(channel.participants_count, result);
                subscribersView.setText(LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber), true);
            }

            loading = (messageObject == null);
            if (dialogCell != null) {
                dialogCell.setDialog(-channel.id, messageObject, messageObject != null ? messageObject.messageOwner.date : 0, false, animated);
            }
            if (showGiftsOnClick) { // If triggered by a click or specific action to show gifts
                setupGiftData(); // This will load and potentially show gifts
            }
        } else {
            this.currentChannelId = 0;
            if (subscribersView != null) subscribersView.setText("", true);
            if (dialogCell != null) dialogCell.setDialog(0, null, 0, false, animated);
            loading = false;
        }

        if (!animated) {
            loadingAlpha.set(loading, true);
        }

        showGifts(showGiftsOnClick && giftItems != null && !giftItems.isEmpty()); // Control gift visibility

        invalidate();
        set = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Base height for header and dialogCell part
        int baseHeight = dp(115.66f); // From original ProfileChannelCell measure logic

        // Add gifts container height if visible
        int giftsContainerHeight = 0;
        if (giftsVisible && giftsContainer != null) {
            giftsContainer.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - dp(32), MeasureSpec.EXACTLY), // Padded width
                                 MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)); // Measure to wrap content
            giftsContainerHeight = giftsContainer.getMeasuredHeight();
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                      MeasureSpec.makeMeasureSpec(baseHeight + giftsContainerHeight, MeasureSpec.EXACTLY));
    }

    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }


    // Gifts adapter (inner class)
    private class GiftsAdapter extends RecyclerView.Adapter<GiftViewHolder> {
        @NonNull
        @Override
        public GiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GiftViewHolder(new GiftItemView(parent.getContext()));
        }
        @Override
        public void onBindViewHolder(@NonNull GiftViewHolder holder, int position) {
            if (position < giftItems.size()) {
                holder.bind(giftItems.get(position));
            }
        }
        @Override
        public int getItemCount() {
            return giftItems.size();
        }
    }

    // Gift view holder (inner class)
    private static class GiftViewHolder extends RecyclerView.ViewHolder {
        private GiftItemView giftItemView;
        GiftViewHolder(GiftItemView itemView) {
            super(itemView);
            this.giftItemView = itemView;
        }
        void bind(GiftItem item) {
            giftItemView.setGiftData(item);
        }
    }

    // Individual gift item view (inner class)
    private static class GiftItemView extends FrameLayout {
        private TextView emojiView;
        private TextView countView;
        // private BackupImageView avatarView; // If sender avatar is needed
        // private AvatarDrawable avatarDrawable;
        private View backgroundView;

        GiftItemView(Context context) {
            super(context);
            backgroundView = new View(context);
            backgroundView.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 4, 4, 4, 4));

            emojiView = new TextView(context);
            emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            emojiView.setGravity(Gravity.CENTER);
            addView(emojiView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));

            countView = new TextView(context);
            countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9);
            countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            countView.setGravity(Gravity.CENTER);
            countView.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.getColor(Theme.key_windowBackgroundWhite)));
            countView.setPadding(dp(3), dp(1), dp(3), dp(1));
            addView(countView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 3, 3));

            setLayoutParams(new RecyclerView.LayoutParams(dp(90), dp(80)));
        }

        void setGiftData(GiftItem item) {
            emojiView.setText(item.emoji);
            if (!TextUtils.isEmpty(item.count)) {
                countView.setText(item.count);
                countView.setVisibility(VISIBLE);
            } else {
                countView.setVisibility(GONE);
            }
        }
    }

    public void updateColors() {
        final int headerColor = resourcesProvider != null ? resourcesProvider.getColor(Theme.key_windowBackgroundWhiteBlueHeader) : Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (subscribersView != null) {
            subscribersView.setTextColor(headerColor);
            subscribersView.setBackground(Theme.createRoundRectDrawable(dp(4.5f), dp(4.5f), Theme.multAlpha(headerColor, .1f)));
        }
        if (headerView != null) {
            headerView.setTextColor(headerColor);
        }
        if (giftsHeaderView != null) {
            giftsHeaderView.setTextColor(headerColor);
        }
        if (giftsUnderline != null) {
            giftsUnderline.setBackgroundColor(resourcesProvider != null ? resourcesProvider.getColor(Theme.key_profile_tabSelectedLine) : Theme.getColor(Theme.key_profile_tabSelectedLine));
        }
        // Update colors for GiftItemView if needed, though they use Theme keys directly
        if (giftsAdapter != null) {
            giftsAdapter.notifyDataSetChanged(); // Will trigger rebind and GiftItemView will pick up new theme colors
        }
    }


    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        // Existing descriptions from original ProfileChannelCell
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileChannelCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextView.class}, new String[]{"headerView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AnimatedTextView.class}, new String[]{"subscribersView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, ThemeDescription.FLAG_BACKGROUNDDRAWABLE, new Class[]{AnimatedTextView.class}, new String[]{"subscribersView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader)); // For background tint of subscribersView

        // Add descriptions for Gift section
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, 0, new Class[]{TextView.class}, new String[]{"giftsHeaderView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader)); // Assuming same color as main header
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, ThemeDescription.FLAG_BACKGROUND, new Class[]{View.class}, new String[]{"giftsUnderline"}, null, null, null, Theme.key_profile_tabSelectedLine));

        // Theme descriptions for items within GiftItemView (if they don't handle their own theming internally via Theme.getColor)
        // This assumes GiftItemView's TextViews are accessible by field name if not tagged.
        // If GiftItemView handles its own theming, these might not be strictly necessary here.
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, 0, new Class[]{GiftItemView.class}, new String[]{"emojiView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText)); // Example
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, 0, new Class[]{GiftItemView.class}, new String[]{"countView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText)); // Example
        descriptions.add(new ThemeDescription(ProfileChannelCell.class, ThemeDescription.FLAG_BACKGROUND, new Class[]{GiftItemView.class}, new String[]{"backgroundView"}, null, null, null, Theme.key_windowBackgroundGray)); // Example for item background
    }
}
