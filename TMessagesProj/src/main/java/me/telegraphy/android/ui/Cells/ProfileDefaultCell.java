package me.telegraphy.android.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import me.telegraphy.android.messenger.AndroidUtilities;
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.MessagesController;
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.messenger.UserConfig;
import me.telegraphy.android.messenger.UserObject;
import me.telegraphy.android.tgnet.TLRPC;
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
import me.telegraphy.android.ui.Components.AvatarDrawable;
import me.telegraphy.android.ui.Components.BackupImageView;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.RecyclerListView;


import java.util.ArrayList;
import java.util.List;

public class ProfileDefaultCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;
    private TextView nameTextView;
    private TextView statusTextView;
    private TextView bioTextView;
    private TextView usernameTextView;
    private LinearLayout actionButtonsLayout;
    private FrameLayout messageButton;
    private FrameLayout callButton;
    private FrameLayout videoButton;
    private FrameLayout muteButton;
    private FrameLayout giftButton;

    private TLRPC.User currentUser;
    private TLRPC.UserFull currentUserFull;
    private int currentAccount = UserConfig.selectedAccount;

    private boolean isExpanded = true; // Default to expanded
    private float expansionAmount = 1.0f;
    private AnimatorSet currentAnimator;

    // Store measured heights for animation
    private int bioMaxHeight;
    private int usernameMaxHeight;
    private int actionButtonsMaxHeight;
    private boolean drawDivider = false;

    private DefaultCellDelegate delegate;

    public interface DefaultCellDelegate {
        void onSendMessage(TLRPC.User user);
        void onCall(TLRPC.User user, TLRPC.UserFull userFull, boolean video);
        void onMute(TLRPC.User user); // Or dialogId
        void onGift(TLRPC.User user);
        void onAvatarClick(TLRPC.User user);
    }

    public ProfileDefaultCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false); // Important for onDraw to be called if drawing divider
        currentAccount = UserConfig.selectedAccount;

        // Avatar
        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable = new AvatarDrawable();
        addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        avatarImageView.setOnClickListener(v -> {
            if (delegate != null && currentUser != null) {
                delegate.onAvatarClick(currentUser);
            }
        });

        // Name
        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 24, 88, 24, 0));

        // Status
        statusTextView = new TextView(context);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        statusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        statusTextView.setSingleLine(true);
        statusTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 24, 116, 24, 0));

        // Container for expandable content
        LinearLayout expandableContent = new LinearLayout(context);
        expandableContent.setOrientation(LinearLayout.VERTICAL);
        addView(expandableContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 148, 0, 0));


        // Bio
        bioTextView = new TextView(context);
        bioTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bioTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bioTextView.setGravity(Gravity.CENTER_HORIZONTAL); // Or START if design prefers
        bioTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        bioTextView.setEllipsize(TextUtils.TruncateAt.END);
        bioTextView.setMaxLines(3); // Example, adjust as needed
        expandableContent.addView(bioTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 8, 24, 8));

        // Username
        usernameTextView = new TextView(context);
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText)); // Link color
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        usernameTextView.setGravity(Gravity.CENTER_HORIZONTAL); // Or START
        usernameTextView.setSingleLine(true);
        usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
        expandableContent.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 4, 24, 16));

        // Action Buttons Layout
        actionButtonsLayout = new LinearLayout(context);
        actionButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        // Ensure layout params respect margins for the container itself
        LinearLayout.LayoutParams actionButtonsLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 0, 16, 16);
        expandableContent.addView(actionButtonsLayout, actionButtonsLp);


        // Create and add buttons
        messageButton = createActionButton(context, R.drawable.msg_message, LocaleController.getString("SendMessage", R.string.SendMessage));
        callButton = createActionButton(context, R.drawable.msg_call, LocaleController.getString("Call", R.string.Call));
        videoButton = createActionButton(context, R.drawable.msg_videocall, LocaleController.getString("VideoCall", R.string.VideoCall));
        muteButton = createActionButton(context, R.drawable.msg_mute, LocaleController.getString("Mute", R.string.Mute)); // Icon might change based on state
        giftButton = createActionButton(context, R.drawable.msg_gift, LocaleController.getString("Gift", R.string.Gift)); // Ensure R.drawable.msg_gift exists

        actionButtonsLayout.addView(messageButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 4, 0));
        actionButtonsLayout.addView(callButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));
        actionButtonsLayout.addView(videoButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));
        actionButtonsLayout.addView(muteButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 4, 0));
        actionButtonsLayout.addView(giftButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 0, 0));

        // Set click listeners
        messageButton.setOnClickListener(v -> { if(delegate != null && currentUser != null) delegate.onSendMessage(currentUser); });
        callButton.setOnClickListener(v -> { if(delegate != null && currentUser != null) delegate.onCall(currentUser, currentUserFull, false); });
        videoButton.setOnClickListener(v -> { if(delegate != null && currentUser != null) delegate.onCall(currentUser, currentUserFull, true); });
        muteButton.setOnClickListener(v -> { if(delegate != null && currentUser != null) delegate.onMute(currentUser); });
        giftButton.setOnClickListener(v -> { if(delegate != null && currentUser != null) delegate.onGift(currentUser); });
    }

    public void setDelegate(DefaultCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setUser(TLRPC.User user, TLRPC.UserFull userFull) {
        this.currentUser = user;
        this.currentUserFull = userFull;

        if (user == null) {
            setVisibility(GONE); // Hide cell if no user data
            return;
        }
        setVisibility(VISIBLE);

        avatarDrawable.setInfo(currentAccount, user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);

        nameTextView.setText(UserObject.getUserName(user));
        statusTextView.setText(LocaleController.formatUserStatus(currentAccount, user)); // Make sure this handles various statuses

        if (userFull != null && !TextUtils.isEmpty(userFull.about)) {
            bioTextView.setText(userFull.about);
            bioTextView.setVisibility(VISIBLE);
        } else {
            bioTextView.setText(""); // Clear if no bio
            bioTextView.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(user.username)) {
            usernameTextView.setText("@" + user.username);
            usernameTextView.setVisibility(VISIBLE);
        } else {
            usernameTextView.setText(""); // Clear if no username
            usernameTextView.setVisibility(GONE);
        }

        // Update mute button state (example)
        // boolean isMuted = MessagesController.getInstance(currentAccount).isDialogMuted(user.id, 0);
        // ((ImageView)((LinearLayout)muteButton.getChildAt(0)).getChildAt(0)).setImageResource(isMuted ? R.drawable.msg_unmute : R.drawable.msg_mute);
        // ((TextView)((LinearLayout)muteButton.getChildAt(0)).getChildAt(1)).setText(isMuted ? LocaleController.getString("Unmute", R.string.Unmute) : LocaleController.getString("Mute", R.string.Mute));


        measureExpandableViews(); // Recalculate heights of expandable views
        applyExpansionState(false); // Apply current expansion state without animation initially
    }

    private FrameLayout createActionButton(Context context, int iconRes, String text) {
        FrameLayout buttonLayout = new FrameLayout(context);
        buttonLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground)));
        buttonLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView iconView = new ImageView(context);
        iconView.setTag("action_icon"); // For theming
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY)); // Use MULTIPLY for tinting
        content.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_HORIZONTAL, 0,0,0,4)); // Icon size and margin

        TextView textView = new TextView(context);
        textView.setTag("action_text"); // For theming
        textView.setText(text);
        textView.setTextColor(Theme.getColor(Theme.key_profile_actionIcon));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        buttonLayout.addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        return buttonLayout;
    }

    // Measure the natural height of expandable views
    private void measureExpandableViews() {
        int widthSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(48), MeasureSpec.EXACTLY); // Parent width - margins
        // For actionButtonsLayout, consider its own horizontal margins if any, or the full width it can take.
        int actionButtonWidthSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(32), MeasureSpec.EXACTLY); // Parent width - its own margins
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED); // Measure with unspecified height

        if (bioTextView.getVisibility() == VISIBLE || (!TextUtils.isEmpty(bioTextView.getText()) && currentUserFull != null && !TextUtils.isEmpty(currentUserFull.about)) ) {
            bioTextView.measure(widthSpec, heightSpec);
            bioMaxHeight = bioTextView.getMeasuredHeight();
        } else {
            bioMaxHeight = 0;
        }

        if (usernameTextView.getVisibility() == VISIBLE || (!TextUtils.isEmpty(usernameTextView.getText()) && currentUser != null && !TextUtils.isEmpty(currentUser.username))) {
            usernameTextView.measure(widthSpec, heightSpec);
            usernameMaxHeight = usernameTextView.getMeasuredHeight();
        } else {
            usernameMaxHeight = 0;
        }

        // Action buttons are always technically "visible" in the layout, their container's visibility changes
        actionButtonsLayout.measure(actionButtonWidthSpec, heightSpec);
        actionButtonsMaxHeight = actionButtonsLayout.getMeasuredHeight();
    }

    public void setExpanded(boolean expanded, boolean animate) {
        if (this.isExpanded == expanded && (currentAnimator == null || !currentAnimator.isRunning())) return; // No change or animation already covers it
        this.isExpanded = expanded;

        if (currentAnimator != null) {
            currentAnimator.cancel();
            currentAnimator = null;
        }
        measureExpandableViews(); // Ensure max heights are current before animation

        if (animate) {
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator expansionAnimator = ValueAnimator.ofFloat(expansionAmount, expanded ? 1.0f : 0.0f);
            expansionAnimator.addUpdateListener(animation -> {
                expansionAmount = (float) animation.getAnimatedValue();
                applyExpansionState(true); // Apply changes during animation
            });
            expansionAnimator.setDuration(250); // Animation duration
            expansionAnimator.setInterpolator(new DecelerateInterpolator()); // Smooth easing
            animatorSet.play(expansionAnimator);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentAnimator = null;
                    // Ensure final state is set correctly
                    expansionAmount = ProfileDefaultCell.this.isExpanded ? 1.0f : 0.0f;
                    applyExpansionState(false); // Apply final state without marking as duringAnimation
                }
            });
            currentAnimator = animatorSet;
            animatorSet.start();
        } else {
            expansionAmount = expanded ? 1.0f : 0.0f;
            applyExpansionState(false); // Apply state directly without animation
        }
    }

    private void applyExpansionState(boolean duringAnimation) {
        // Alpha calculations for smoother fade-in/out
        // Bio fades in/out fully
        float bioAlpha = expansionAmount; // Simple alpha based on overall expansion
        // Username fades in slightly later or faster
        float usernameAlpha = Math.max(0f, Math.min(1f, (expansionAmount - 0.2f) / 0.8f)); // Starts fading after 20% collapse

        if (bioMaxHeight > 0) {
            bioTextView.setAlpha(bioAlpha);
            setHeight(bioTextView, (int) (bioMaxHeight * expansionAmount));
            bioTextView.setVisibility(expansionAmount > 0.01f && !TextUtils.isEmpty(bioTextView.getText()) ? VISIBLE : GONE);
        }

        if (usernameMaxHeight > 0) {
            usernameTextView.setAlpha(usernameAlpha);
            setHeight(usernameTextView, (int) (usernameMaxHeight * expansionAmount));
            usernameTextView.setVisibility(expansionAmount > 0.01f && !TextUtils.isEmpty(usernameTextView.getText()) ? VISIBLE : GONE);
        }

        actionButtonsLayout.setAlpha(expansionAmount);
        setHeight(actionButtonsLayout, (int) (actionButtonsMaxHeight * expansionAmount));
        actionButtonsLayout.setVisibility(expansionAmount > 0.01f ? VISIBLE : GONE);

        // Request layout update for the cell itself if not during animation's own update cycle
        // to ensure container resizes.
        if (!duringAnimation) {
            requestLayout();
        }
    }

    // Helper to set view height
    private void setHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            // This case should ideally not happen if views are added with LayoutParams
            params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        } else {
            params.height = height;
        }
        view.setLayoutParams(params);
    }

    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate base height (avatar, name, status)
        int totalHeight = AndroidUtilities.dp(148); // Approx. height for avatar + name + status + top margin for expandable

        // Add height of expandable content based on current expansionAmount
        if (bioTextView.getVisibility() == VISIBLE || (currentAnimator != null && currentAnimator.isRunning() && bioMaxHeight > 0) ) {
            totalHeight += (int) (bioMaxHeight * expansionAmount) + AndroidUtilities.dp(8); // bio + its bottom margin
        }
        if (usernameTextView.getVisibility() == VISIBLE || (currentAnimator != null && currentAnimator.isRunning() && usernameMaxHeight > 0)) {
            totalHeight += (int) (usernameMaxHeight * expansionAmount) + AndroidUtilities.dp(16); // username + its bottom margin
        }
        if (actionButtonsLayout.getVisibility() == VISIBLE || (currentAnimator != null && currentAnimator.isRunning() && actionButtonsMaxHeight > 0)) {
             totalHeight += (int) (actionButtonsMaxHeight * expansionAmount) + AndroidUtilities.dp(16); // action buttons + their bottom margin
        }
        // Ensure a minimum height if all are collapsed (e.g. just avatar/name/status part)
        // totalHeight = Math.max(totalHeight, AndroidUtilities.dp(148));

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            // Draw divider at the bottom of the cell, respecting padding
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    // Static method for ThemeDescriptions
    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        // Cell background
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileDefaultCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        // Divider
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, ThemeDescription.FLAG_DIVIDER, null, Theme.dividerPaint, null, null, Theme.key_divider));

        // Text views
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, 0, new Class[]{TextView.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, 0, new Class[]{TextView.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, 0, new Class[]{TextView.class}, new String[]{"bioTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, 0, new Class[]{TextView.class}, new String[]{"usernameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        // Action buttons
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{FrameLayout.class}, new String[]{"messageButton", "callButton", "videoButton", "muteButton", "giftButton"}, null, null, null, Theme.key_profile_actionBackground));
        // Assuming action_icon and action_text are tags or direct field names if ImageView/TextView are accessed directly for theming
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ImageView.class}, new String[]{"action_icon"}, null, null, null, Theme.key_profile_actionIcon));
        descriptions.add(new ThemeDescription(ProfileDefaultCell.class, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextView.class}, new String[]{"action_text"}, null, null, null, Theme.key_profile_actionIcon));
    }
}
