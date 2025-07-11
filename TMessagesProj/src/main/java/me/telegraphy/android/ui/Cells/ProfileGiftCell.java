package me.telegraphy.android.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.telegraphy.android.messenger.AndroidUtilities;
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.messenger.UserConfig;
import me.telegraphy.android.tgnet.TLRPC;
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
import me.telegraphy.android.ui.Components.BackupImageView;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.RLottieImageView;
import me.telegraphy.android.ui.Components.RecyclerListView;


public class ProfileGiftCell extends FrameLayout {

    // Animation states
    public enum AnimationState {
        COLLAPSED, EXPANDING, EXPANDED, COLLAPSING
    }

    private AnimationState currentAnimationState = AnimationState.COLLAPSED;
    private GiftAnimationListener animationListener;

    // UI Components
    private TextView titleTextView;
    private RecyclerView giftsRecyclerView;
    private GiftAdapter giftAdapter;
    private FrameLayout expandButton;
    private ImageView expandIcon;
    private LinearLayout headerLayout;


    private List<GiftItem> giftItemList = new ArrayList<>();
    private AnimatorSet currentAnimator;
    private boolean isExpanded = false;
    private int collapsedHeight = AndroidUtilities.dp(80); // Example: Title + 1 row preview
    private int expandedHeight = AndroidUtilities.dp(250); // Example: Title + multiple rows
    private boolean drawDivider = false;

    // Floating icons animation
    private List<RLottieImageView> floatingIconViews = new ArrayList<>();
    private Random random = new Random();
    private Handler floatingIconHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_FLOATING_ICONS = 15;
    private static final long FLOATING_ICON_INTERVAL = 300; // ms
    private boolean shouldAnimateFloatingIcons = false;


    public interface GiftAnimationListener {
        void onAnimationStart(ProfileGiftCell cell, AnimationState state);
        void onAnimationEnd(ProfileGiftCell cell, AnimationState state);
        void onAnimationUpdate(ProfileGiftCell cell, float progress); // progress: 0 (collapsed) to 1 (expanded)
        void onGiftClicked(int giftIndex); // Callback for when a gift is clicked
        void onStateChanged(AnimationState newState);
    }

    // GiftItem data class (can be moved to a separate file if used elsewhere)
    public static class GiftItem {
        public String id;
        public String name;
        public int iconResId; // For static icon
        public String lottieAnimationName; // For Lottie animation file name in assets
        public boolean isNew;
        public long dateReceived;
        public String description;
        public String senderName; // Optional: if gifts can be from other users

        public TLRPC.Document document; // For sticker gifts

        public GiftItem() {} // Default constructor

        // Constructor for demo/simpler gifts
        public GiftItem(String id, String name, int iconResId, String lottieName, boolean isNew, long date, String desc) {
            this.id = id;
            this.name = name;
            this.iconResId = iconResId;
            this.lottieAnimationName = lottieName;
            this.isNew = isNew;
            this.dateReceived = date;
            this.description = desc;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return null; } // Not used if using Lottie/ResID
        public void setImageUrl(String url) { /* Not used */ }
        public TLRPC.Document getDocument() { return document; }
        public void setDocument(TLRPC.Document doc) { this.document = doc; }
        public int getDate() { return (int) (dateReceived / 1000); } // Convert to seconds for TLRPC style date
        public void setDate(int date) { this.dateReceived = (long)date * 1000; }
        public long getFromId() { return 0; } // Placeholder
        public void setFromId(long id) { /* Placeholder */ }
        public TLRPC.User getFromUser() { return null; } // Placeholder
        public void setFromUser(TLRPC.User user) { /* Placeholder */ }

    }


    public ProfileGiftCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 10, 16, 0));


        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("GiftsReceived", R.string.GiftsReceived)); // Example title
        headerLayout.addView(titleTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));


        expandButton = new FrameLayout(context);
        expandButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)); // Ripple effect
        headerLayout.addView(expandButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL));

        expandIcon = new ImageView(context);
        expandIcon.setImageResource(R.drawable.arrow_more); // Standard dropdown arrow
        expandIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7), PorterDuff.Mode.MULTIPLY));
        expandButton.addView(expandIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        expandButton.setOnClickListener(v -> toggleExpansion());

        giftsRecyclerView = new RecyclerListView(context);
        giftsRecyclerView.setLayoutManager(new GridLayoutManager(context, 3)); // 3 items per row
        giftAdapter = new GiftAdapter(context);
        giftsRecyclerView.setAdapter(giftAdapter);
        giftsRecyclerView.setNestedScrollingEnabled(false); // Important if inside another scroll view
        addView(giftsRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 50, 16, 10));

        // Initial setup
        updateHeights();
        loadSampleGifts(); // Load some sample data
    }

    public void setAnimationListener(GiftAnimationListener listener) {
        this.animationListener = listener;
    }

    public void setData(TLRPC.User user, List<GiftItem> gifts) {
        // In a real app, you'd fetch gifts for this user
        this.giftItemList.clear();
        if (gifts != null) {
            this.giftItemList.addAll(gifts);
        }
        giftAdapter.notifyDataSetChanged();
        updateHeights(); // Recalculate height based on new items
        // Maybe automatically expand if there are new gifts, or based on some logic
    }

    private void loadSampleGifts() {
        // For demonstration purposes
        giftItemList.add(new GiftItem("gift1", "Premium Badge", R.drawable.msg_premium_gift /* placeholder */, "premium_badge_lottie.json", true, System.currentTimeMillis(), "A special premium badge!"));
        giftItemList.add(new GiftItem("gift2", "Animated Avatar Frame", R.drawable.msg_settings /* placeholder */, "avatar_frame_lottie.json", false, System.currentTimeMillis() - 86400000, "Cool animated frame."));
        giftItemList.add(new GiftItem("gift3", "Confetti Blast", R.drawable.msg_gift /* placeholder */, "confetti_lottie.json", true, System.currentTimeMillis() - 172800000, "Celebration time!"));
        giftItemList.add(new GiftItem("gift4", "Sparkle Effect", R.drawable.msg_secret /* placeholder */, "sparkle_lottie.json", false, System.currentTimeMillis() - 259200000, "Shiny profile effect."));
        giftItemList.add(new GiftItem("gift5", "Tele-bear Sticker", R.drawable.msg_sticker /* placeholder */, null, true, System.currentTimeMillis() - 345600000, "A friendly bear."));
        giftAdapter.notifyDataSetChanged();
        updateHeights();
    }

    private void updateHeights() {
        // Calculate collapsed height (e.g., title + one row of gifts)
        int titleHeight = AndroidUtilities.dp(50); // Approx height for headerLayout
        int oneRowHeight = giftItemList.isEmpty() ? 0 : AndroidUtilities.dp(100); // Approx height for one row of gifts
        collapsedHeight = titleHeight + oneRowHeight + AndroidUtilities.dp(10); // Add some padding

        // Calculate expanded height (title + all gifts)
        int numRows = (int) Math.ceil(giftItemList.size() / 3.0);
        expandedHeight = titleHeight + (numRows * AndroidUtilities.dp(100)) + AndroidUtilities.dp(10); // Add padding
        expandedHeight = Math.max(expandedHeight, collapsedHeight); // Ensure expanded is not smaller

        getLayoutParams().height = isExpanded ? expandedHeight : collapsedHeight;
        requestLayout();
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void collapseCell() {
        if (isExpanded) {
            toggleExpansion();
        }
    }
    public void forceCollapse() {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        isExpanded = false;
        getLayoutParams().height = collapsedHeight;
        giftsRecyclerView.setAlpha(0f); // Hide content
        expandIcon.setRotation(0);
        requestLayout();
        if (animationListener != null) {
            animationListener.onStateChanged(AnimationState.COLLAPSED);
        }
        stopFloatingIcons();
    }


    private void toggleExpansion() {
        isExpanded = !isExpanded;
        if (animationListener != null) {
            animationListener.onStateChanged(isExpanded ? AnimationState.EXPANDING : AnimationState.COLLAPSING);
            animationListener.onAnimationStart(this, isExpanded ? AnimationState.EXPANDING : AnimationState.COLLAPSING);
        }


        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        int startHeight = getHeight();
        int endHeight = isExpanded ? expandedHeight : collapsedHeight;

        ValueAnimator heightAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        heightAnimator.addUpdateListener(animation -> {
            getLayoutParams().height = (Integer) animation.getAnimatedValue();
            requestLayout();
            if (animationListener != null) {
                float progress = (float) (getHeight() - collapsedHeight) / (expandedHeight - collapsedHeight);
                animationListener.onAnimationUpdate(this, Math.max(0, Math.min(1, progress)));
            }
        });

        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(giftsRecyclerView, "alpha", isExpanded ? 0f : 1f, isExpanded ? 1f : 0f);
        ObjectAnimator iconRotation = ObjectAnimator.ofFloat(expandIcon, "rotation", isExpanded ? 0f : 180f, isExpanded ? 180f : 0f);

        currentAnimator = new AnimatorSet();
        currentAnimator.playTogether(heightAnimator, alphaAnimator, iconRotation);
        // Use duration from integers.xml
        currentAnimator.setDuration(getContext().getResources().getInteger(R.integer.gift_animation_duration));
        currentAnimator.setInterpolator(new DecelerateInterpolator());
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAnimator = null;
                if (animationListener != null) {
                    animationListener.onAnimationEnd(ProfileGiftCell.this, isExpanded ? AnimationState.EXPANDED : AnimationState.COLLAPSED);
                    animationListener.onStateChanged(isExpanded ? AnimationState.EXPANDED : AnimationState.COLLAPSED);
                }
                if (isExpanded) {
                    startFloatingIcons();
                } else {
                    stopFloatingIcons();
                }
            }
        });
        currentAnimator.start();
        if (isExpanded) {
            giftsRecyclerView.setAlpha(0f); // Ensure it starts transparent before fade-in
        }
    }

    public void pauseAnimations() {
        // Pause Lottie animations in gift items if any are playing
        for (int i = 0; i < giftsRecyclerView.getChildCount(); i++) {
            View child = giftsRecyclerView.getChildAt(i);
            if (child instanceof GiftItemView) {
                ((GiftItemView) child).pauseLottieAnimation();
            }
        }
        stopFloatingIcons();
    }

    public void resumeAnimations() {
        // Resume Lottie animations
        for (int i = 0; i < giftsRecyclerView.getChildCount(); i++) {
            View child = giftsRecyclerView.getChildAt(i);
            if (child instanceof GiftItemView) {
                ((GiftItemView) child).resumeLottieAnimation();
            }
        }
        if (isExpanded && shouldAnimateFloatingIcons) {
            startFloatingIcons();
        }
    }


    private void startFloatingIcons() {
        shouldAnimateFloatingIcons = true;
        floatingIconHandler.post(floatingIconRunnable);
    }

    private void stopFloatingIcons() {
        shouldAnimateFloatingIcons = false;
        floatingIconHandler.removeCallbacks(floatingIconRunnable);
        for (RLottieImageView iconView : floatingIconViews) {
            removeView(iconView);
        }
        floatingIconViews.clear();
    }

    private Runnable floatingIconRunnable = new Runnable() {
        @Override
        public void run() {
            if (!shouldAnimateFloatingIcons || floatingIconViews.size() >= MAX_FLOATING_ICONS || !isExpanded) {
                return;
            }

            RLottieImageView iconView = new RLottieImageView(getContext());
            // Choose a random Lottie animation from a predefined list or gift items
            int randomGiftIndex = random.nextInt(giftItemList.size());
            GiftItem randomGift = giftItemList.get(randomGiftIndex);
            if (!TextUtils.isEmpty(randomGift.lottieAnimationName)) {
                 try {
                    // Assuming Lottie files are in assets. Adjust path if needed.
                    iconView.setAnimation(randomGift.lottieAnimationName, 24, 24);
                } catch (Exception e) {
                    // Fallback if Lottie fails
                    iconView.setImageResource(randomGift.iconResId != 0 ? randomGift.iconResId : R.drawable.msg_gift);
                }
            } else if (randomGift.iconResId != 0) {
                 iconView.setImageResource(randomGift.iconResId);
            } else {
                iconView.setImageResource(R.drawable.msg_gift); // Default
            }

            iconView.setAlpha(0f);
            int size = AndroidUtilities.dp(20 + random.nextInt(15)); // Random size
            addView(iconView, LayoutHelper.createFrame(size, size, Gravity.TOP | Gravity.LEFT));
            floatingIconViews.add(iconView);

            float startX = random.nextFloat() * getWidth();
            float endX = startX + (random.nextFloat() - 0.5f) * AndroidUtilities.dp(100);
            float startY = getHeight();
            float endY = -size; // Move off screen at the top

            iconView.setTranslationX(startX);
            iconView.setTranslationY(startY);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(iconView, "translationY", startY, endY),
                    ObjectAnimator.ofFloat(iconView, "translationX", startX, endX),
                    ObjectAnimator.ofFloat(iconView, "alpha", 0f, 0.8f, 0f), // Fade in then out
                    ObjectAnimator.ofFloat(iconView, "rotation", 0f, (random.nextFloat() - 0.5f) * 360)
            );
            // Use duration from integers.xml for floating animation parts
            long floatingDuration = getContext().getResources().getInteger(R.integer.gift_floating_duration);
            long translateDuration = floatingDuration + random.nextInt((int)(floatingDuration * 0.5)); // Add some variance
            long rotateDuration = floatingDuration + random.nextInt((int)(floatingDuration * 0.5));

            ObjectAnimator translateY = ObjectAnimator.ofFloat(iconView, "translationY", startY, endY);
            translateY.setDuration(translateDuration);

            ObjectAnimator translateX = ObjectAnimator.ofFloat(iconView, "translationX", startX, endX);
            translateX.setDuration(translateDuration); // Match Y for synchronized movement feel

            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(iconView, "alpha", 0f, 0.8f, 0f);
            alphaAnim.setDuration(translateDuration); // Alpha over the course of movement

            ObjectAnimator rotationAnim = ObjectAnimator.ofFloat(iconView, "rotation", 0f, (random.nextFloat() - 0.5f) * 360);
            rotationAnim.setDuration(rotateDuration);


            set.playTogether(
                    translateY,
                    translateX,
                    alphaAnim,
                    rotationAnim
            );
            // set.setDuration(3000 + random.nextInt(2000)); // Duration is now per animator
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(iconView);
                    floatingIconViews.remove(iconView);
                }
            });
            set.start();
            iconView.playAnimation();


            if (shouldAnimateFloatingIcons) {
                floatingIconHandler.postDelayed(this, FLOATING_ICON_INTERVAL);
            }
        }
    };


    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }
    public void updateTheme() {
        // Update colors of text, icons, backgrounds based on Theme.getColor()
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        expandIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7), PorterDuff.Mode.MULTIPLY));
        // Redraw child views if they also depend on theme colors
        if (giftAdapter != null) {
            giftAdapter.notifyDataSetChanged(); // This will rebind and potentially update item views
        }
        invalidate(); // Redraw the cell itself for divider or background
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    // Adapter for Gifts RecyclerView
    private class GiftAdapter extends RecyclerView.Adapter<GiftViewHolder> {
        private Context context;

        public GiftAdapter(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public GiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GiftViewHolder(new GiftItemView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull GiftViewHolder holder, int position) {
            holder.bind(giftItemList.get(position));
            holder.itemView.setOnClickListener(v -> {
                if (animationListener != null) {
                    animationListener.onGiftClicked(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return giftItemList.size();
        }
    }

    // ViewHolder for Gift Item
    private static class GiftViewHolder extends RecyclerView.ViewHolder {
        GiftItemView itemView;

        public GiftViewHolder(@NonNull GiftItemView itemView) {
            super(itemView);
            this.itemView = itemView;
        }

        public void bind(GiftItem item) {
            itemView.setGiftData(item);
        }
    }

    // View for individual Gift Item
    private static class GiftItemView extends FrameLayout {
        private RLottieImageView lottieImageView;
        private ImageView staticImageView;
        private TextView nameTextView;
        private TextView newBadgeTextView;


        public GiftItemView(@NonNull Context context) {
            super(context);
            setClipChildren(false); // Allow animations to go outside bounds if needed

            lottieImageView = new RLottieImageView(context);
            addView(lottieImageView, LayoutHelper.createFrame(60, 60, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));

            staticImageView = new ImageView(context);
            staticImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(staticImageView, LayoutHelper.createFrame(60, 60, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(1);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 8, 0, 8, 5));

            newBadgeTextView = new TextView(context);
            // Use new gift colors
            newBadgeTextView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(R.color.gift_background_circle))); // Example usage
            newBadgeTextView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelVoicePressed)); // Assuming this is white or contrasting
            newBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            newBadgeTextView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
            newBadgeTextView.setText(LocaleController.getString("New", R.string.New));
            newBadgeTextView.setVisibility(GONE);
            addView(newBadgeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 5,5,5,0));
        }

        public void setGiftData(GiftItem item) {
            nameTextView.setText(item.name);
            int iconSize = getContext().getResources().getDimensionPixelSize(R.dimen.gift_icon_size);

            if (!TextUtils.isEmpty(item.lottieAnimationName)) {
                lottieImageView.setVisibility(VISIBLE);
                staticImageView.setVisibility(GONE);
                try {
                    lottieImageView.setAnimation(item.lottieAnimationName, iconSize, iconSize);
                    lottieImageView.playAnimation();
                } catch (Exception e) {
                    lottieImageView.setVisibility(GONE);
                    staticImageView.setVisibility(VISIBLE);
                    if (item.iconResId != 0) staticImageView.setImageResource(item.iconResId);
                    else staticImageView.setImageResource(R.drawable.msg_gift);
                    updateStaticImageViewSize(iconSize);
                }
            } else if (item.iconResId != 0) {
                lottieImageView.setVisibility(GONE);
                staticImageView.setVisibility(VISIBLE);
                staticImageView.setImageResource(item.iconResId);
                updateStaticImageViewSize(iconSize);
            } else {
                lottieImageView.setVisibility(GONE);
                staticImageView.setVisibility(VISIBLE);
                staticImageView.setImageResource(R.drawable.msg_gift);
                updateStaticImageViewSize(iconSize);
            }
            newBadgeTextView.setVisibility(item.isNew ? VISIBLE : GONE);
        }

        private void updateStaticImageViewSize(int iconSize) {
            ViewGroup.LayoutParams params = staticImageView.getLayoutParams();
            params.width = iconSize;
            params.height = iconSize;
            staticImageView.setLayoutParams(params);

            ViewGroup.LayoutParams lottieParams = lottieImageView.getLayoutParams();
            lottieParams.width = iconSize;
            lottieParams.height = iconSize;
            lottieImageView.setLayoutParams(lottieParams);
        }

        public void pauseLottieAnimation() {
            if (lottieImageView.getVisibility() == VISIBLE && lottieImageView.isAnimating()) {
                lottieImageView.pauseAnimation();
            }
        }

        public void resumeLottieAnimation() {
            if (lottieImageView.getVisibility() == VISIBLE && !lottieImageView.isAnimating()) {
                // Check if animation was ever started to avoid issues
                if (lottieImageView.getAnimatedDrawable() != null) {
                    lottieImageView.playAnimation();
                }
            }
        }
    }

    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileGiftCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, ThemeDescription.FLAG_DIVIDER, null, Theme.dividerPaint, null, null, Theme.key_divider));
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, 0, new Class[]{TextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, 0, new Class[]{ImageView.class}, new String[]{"expandIcon"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));

        // For GiftItemView inside RecyclerView (assuming items are direct children for theming)
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, 0, new Class[]{TextView.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText)); // Gift item name
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, 0, new Class[]{TextView.class}, new String[]{"newBadgeTextView"}, null, null, null, Theme.key_chat_mention বাকি)); // Gift item new badge background
        descriptions.add(new ThemeDescription(ProfileGiftCell.class, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextView.class}, new String[]{"newBadgeTextView"}, null, null, null, Theme.key_chat_messagePanelVoicePressed)); // Gift item new badge text (example key)
    }
}
