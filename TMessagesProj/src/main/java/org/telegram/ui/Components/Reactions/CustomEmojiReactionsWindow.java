package org.telegram.ui.Components.Reactions;

import static org.telegram.ui.Components.ReactionsContainerLayout.TYPE_STORY;
import static org.telegram.ui.Components.ReactionsContainerLayout.TYPE_STORY_LIKES;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.StableAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CustomEmojiReactionsWindow {

    ContainerView containerView;
    WindowManager windowManager;
    public FrameLayout windowView;
    boolean attachToParent;

    float fromRadius;
    RectF fromRect = new RectF();
    public RectF drawingRect = new RectF();
    float enterTransitionProgress;
    boolean enterTransitionFinished;
    boolean isShowing;

    SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;
    ReactionsContainerLayout reactionsContainerLayout;
    private final Path pathToClipApi20 = new Path();
    private boolean invalidatePath;

    List<ReactionsLayoutInBubble.VisibleReaction> reactions;
    private Runnable onDismiss;
    private float dismissProgress;
    private boolean dismissed;
    BaseFragment baseFragment;
    Theme.ResourcesProvider resourcesProvider;

    float yTranslation;
    float keyboardHeight;
    private boolean wasFocused;
    private int account;
    private boolean cascadeAnimation;
    private ValueAnimator valueAnimator;
    private final int type;

    public CustomEmojiReactionsWindow(int type, BaseFragment baseFragment, List<ReactionsLayoutInBubble.VisibleReaction> reactions, HashSet<ReactionsLayoutInBubble.VisibleReaction> selectedReactions, ReactionsContainerLayout reactionsContainerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.type = type;
        this.reactions = reactions;
        this.baseFragment = baseFragment;
        this.resourcesProvider = resourcesProvider;
        Context context = baseFragment != null ? baseFragment.getContext() : reactionsContainerLayout.getContext();
        windowView = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (enterTransitionFinished) {
                        dismiss();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            protected void dispatchSetPressed(boolean pressed) {

            }

            @Override
            protected boolean fitSystemWindows(Rect insets) {
                if (keyboardHeight != insets.bottom && wasFocused) {
                    keyboardHeight = insets.bottom;
                    updateWindowPosition();
                }
                return super.fitSystemWindows(insets);
            }

            Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return (int) keyboardHeight;
                }
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(this, bulletinDelegate);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
            }
        };
        windowView.setOnClickListener(v -> {
            if (enterTransitionFinished) {
                dismiss();
            }
        });
        attachToParent = type == TYPE_STORY_LIKES;

        // sizeNotifierFrameLayout.setFitsSystemWindows(true);

        containerView = new ContainerView(context);
        int dialogType = reactionsContainerLayout.showExpandableReactions() ? SelectAnimatedEmojiDialog.TYPE_EXPANDABLE_REACTIONS : SelectAnimatedEmojiDialog.TYPE_REACTIONS;
        selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(baseFragment, context, false, null, dialogType, type != TYPE_STORY, resourcesProvider, 16) {

            @Override
            public boolean prevWindowKeyboardVisible() {
                if (reactionsContainerLayout.getDelegate() != null) {
                    return reactionsContainerLayout.getDelegate().needEnterText();
                }
                return false;
            }

            @Override
            protected void onInputFocus() {
                if (!wasFocused) {
                    wasFocused = true;
                    if (!attachToParent) {
                        windowManager.updateViewLayout(windowView, createLayoutParams(true));
                    }
                    if (baseFragment instanceof ChatActivity) {
                        ((ChatActivity) baseFragment).needEnterText();
                    }
                    if (reactionsContainerLayout.getDelegate() != null) {
                        reactionsContainerLayout.getDelegate().needEnterText();
                    }
                }
            }

            @Override
            protected void onReactionClick(ImageViewEmoji emoji, ReactionsLayoutInBubble.VisibleReaction reaction) {
                reactionsContainerLayout.onReactionClicked(emoji, reaction, false);
                AndroidUtilities.hideKeyboard(windowView);
            }

            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, Integer until) {
                if (!UserConfig.getInstance(baseFragment.getCurrentAccount()).isPremium()) {
                    windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    BulletinFactory.of(windowView, null).createEmojiBulletin(
                            document,
                            AndroidUtilities.replaceTags(LocaleController.getString("UnlockPremiumEmojiReaction", R.string.UnlockPremiumEmojiReaction)),
                            LocaleController.getString("PremiumMore", R.string.PremiumMore),
                            () -> showUnlockPremiumAlert()
                    ).show();
                    return;
                }
                reactionsContainerLayout.onReactionClicked(emojiView, ReactionsLayoutInBubble.VisibleReaction.fromCustomEmoji(documentId), false);
                AndroidUtilities.hideKeyboard(windowView);
            }

            @Override
            protected void invalidateParent() {
                containerView.invalidate();
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            selectAnimatedEmojiDialog.setOutlineProvider(new ViewOutlineProvider() {
                final Rect rect = new Rect();
                final RectF rectTmp = new RectF();
                final RectF rectF = new RectF();

                @Override
                public void getOutline(View view, Outline outline) {
                    float radius = AndroidUtilities.lerp(fromRadius, AndroidUtilities.dp(8), enterTransitionProgress);
                    rectTmp.set(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    AndroidUtilities.lerp(fromRect, rectTmp, enterTransitionProgress, rectF);
                    rectF.round(rect);
                    outline.setRoundRect(rect, radius);
                }
            });
            selectAnimatedEmojiDialog.setClipToOutline(true);
        }
        
        selectAnimatedEmojiDialog.setOnLongPressedListener(new SelectAnimatedEmojiDialog.onLongPressedListener() {
            @Override
            public void onLongPressed(SelectAnimatedEmojiDialog.ImageViewEmoji view) {
                if (view.isDefaultReaction) {
                    reactionsContainerLayout.onReactionClicked(view, view.reaction, true);
                } else {
                    reactionsContainerLayout.onReactionClicked(view, ReactionsLayoutInBubble.VisibleReaction.fromCustomEmoji(view.span.documentId), true);
                }
            }
        });
        selectAnimatedEmojiDialog.setOnRecentClearedListener(new SelectAnimatedEmojiDialog.onRecentClearedListener() {
            @Override
            public void onRecentCleared() {
                reactionsContainerLayout.clearRecentReactions();
            }
        });
        selectAnimatedEmojiDialog.setRecentReactions(reactions);
        selectAnimatedEmojiDialog.setSelectedReactions(selectedReactions);
        selectAnimatedEmojiDialog.setDrawBackground(false);
        selectAnimatedEmojiDialog.onShow(null);
        containerView.addView(selectAnimatedEmojiDialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 16, 16, 16, 16));
        windowView.setClipChildren(false);
        if (type == TYPE_STORY || (reactionsContainerLayout.getDelegate() != null && reactionsContainerLayout.getDelegate().drawBackground())) {
            selectAnimatedEmojiDialog.setBackgroundDelegate((canvas, left, top, right, bottom, x, y) -> {
                AndroidUtilities.rectTmp.set(left, top, right, bottom);
                reactionsContainerLayout.getDelegate().drawRoundRect(canvas, AndroidUtilities.rectTmp, 0, containerView.getX() + x, getBlurOffset() + y, 255,true);
            });
        }
        if (attachToParent) {
            ViewGroup group = (ViewGroup) reactionsContainerLayout.getParent();
            group.addView(windowView);
        } else {
            WindowManager.LayoutParams lp = createLayoutParams(false);
            windowManager = AndroidUtilities.findActivity(context).getWindowManager();
            windowManager.addView(windowView, lp);
        }

        this.reactionsContainerLayout = reactionsContainerLayout;
        reactionsContainerLayout.setOnSwitchedToLoopView(() -> containerView.invalidate()); //fixed emoji freeze
        reactionsContainerLayout.prepareAnimation(true);
        AndroidUtilities.runOnUIThread(() -> {
            isShowing = true;
            containerView.invalidate();
            reactionsContainerLayout.prepareAnimation(false);
            createTransition(true);
        }, 50);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 7);
    }

    private void updateWindowPosition() {
        if (dismissed) {
            return;
        }
        float y = yTranslation;
        int bottomOffset = AndroidUtilities.dp(32);
        if (type == TYPE_STORY || type == TYPE_STORY_LIKES) {
            bottomOffset = AndroidUtilities.dp(24);
        }
        if (y + containerView.getMeasuredHeight() > windowView.getMeasuredHeight() - keyboardHeight - bottomOffset) {
            y = windowView.getMeasuredHeight() - keyboardHeight - containerView.getMeasuredHeight() - bottomOffset;
        }
        if (y < 0) {
            y = 0;
        }
        containerView.animate().translationY(y).setDuration(250).setUpdateListener(animation -> {
            containerView.invalidate();
        }).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    private WindowManager.LayoutParams createLayoutParams(boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.type = type == ReactionsContainerLayout.TYPE_DEFAULT ? WindowManager.LayoutParams.TYPE_APPLICATION_PANEL : WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if (focusable) {
            lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        } else {
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        }
        lp.format = PixelFormat.TRANSLUCENT;
        return lp;
    }

    private void showUnlockPremiumAlert() {
        if (baseFragment instanceof ChatActivity) {
            baseFragment.showDialog(new PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
        } else {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null) {
                fragment.showDialog(new PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
            }
        }
    }

    int[] location = new int[2];
    final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    private void createTransition(boolean enter) {
        fromRect.set(reactionsContainerLayout.rect);
        fromRadius = reactionsContainerLayout.radius;

        int[] windowLocation = new int[2];
        if (enter) {
            reactionsContainerLayout.getLocationOnScreen(location);
        }
        windowView.getLocationOnScreen(windowLocation);
        float y = location[1] - windowLocation[1] - AndroidUtilities.dp(44) - AndroidUtilities.dp(52) - (selectAnimatedEmojiDialog.includeHint ? AndroidUtilities.dp(26) : 0) + reactionsContainerLayout.getTopOffset();

        if (reactionsContainerLayout.showExpandableReactions()) {
            y = location[1] - windowLocation[1] - AndroidUtilities.dp(12);
        }

        if (y + containerView.getMeasuredHeight() > windowView.getMeasuredHeight() - AndroidUtilities.dp(32)) {
            y = windowView.getMeasuredHeight() - AndroidUtilities.dp(32) - containerView.getMeasuredHeight();
        }

        if (y < AndroidUtilities.dp(16)) {
            y = AndroidUtilities.dp(16);
        }

        if (type == TYPE_STORY) {
            containerView.setTranslationX((windowView.getMeasuredWidth() - containerView.getMeasuredWidth()) / 2f - AndroidUtilities.dp(16));
        } else if (type == TYPE_STORY_LIKES) {
            containerView.setTranslationX(location[0] - windowLocation[0] - AndroidUtilities.dp(18));
        } else {
            containerView.setTranslationX(location[0] - windowLocation[0] - AndroidUtilities.dp(2));
        }

        if (!enter) {
            yTranslation = containerView.getTranslationY();
        } else {
            yTranslation = y;
            containerView.setTranslationY(yTranslation);
        }

        fromRect.offset(location[0] - windowLocation[0] - containerView.getX(), location[1] - windowLocation[1] - containerView.getY());

        reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress);

        if (enter) {
            cascadeAnimation = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);
            enterTransitionFinished = false;
        } else {
            cascadeAnimation = false;
        }
        if (cascadeAnimation) {
            updateCascadeEnter(0, true);
        }
        updateContainersAlpha();
        selectAnimatedEmojiDialog.setEnterAnimationInProgress(true);
        selectAnimatedEmojiDialog.emojiTabs.showRecentTabStub(enter && cascadeAnimation);
        account = UserConfig.selectedAccount;
        notificationsLocker.lock();
        valueAnimator = StableAnimator.ofFloat(enterTransitionProgress, enter ? 1f : 0);
        valueAnimator.addUpdateListener(animation -> {
            valueAnimator = null;
            enterTransitionProgress = (float) animation.getAnimatedValue();
            updateContainersAlpha();
            updateContentPosition();
            reactionsContainerLayout.setCustomEmojiEnterProgress(Utilities.clamp(enterTransitionProgress, 1f, 0));
            invalidatePath = true;
            containerView.invalidate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selectAnimatedEmojiDialog.invalidateOutline();
            }
            if (cascadeAnimation) {
                updateCascadeEnter(enterTransitionProgress, enter);
            }
        });
        if (!enter) {
            syncReactionFrames();
        }
        valueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                updateContainersAlpha();
                updateContentPosition();
                checkAnimationEnd(enter);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    selectAnimatedEmojiDialog.invalidateOutline();
                }
                enterTransitionProgress = enter ? 1f : 0f;
                if (enter) {
                    enterTransitionFinished = true;
                    containerView.invalidate();
                }
                reactionsContainerLayout.setCustomEmojiEnterProgress(Utilities.clamp(enterTransitionProgress, 1f, 0f));
                if (!enter) {
                    reactionsContainerLayout.setSkipDraw(false);
                    removeView();
                    Runtime.getRuntime().gc(); //to prevent garbage collection when reopening
                    reactionsContainerLayout.setCustomEmojiReactionsBackground(true);
                }
            }
        });

        if (cascadeAnimation) {
            valueAnimator.setDuration(450);
            valueAnimator.setInterpolator(new OvershootInterpolator(0.5f));
        } else {
            valueAnimator.setDuration(350);
            valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        }
        containerView.invalidate();
        switchLayerType(true);
        if (!enter) {
            reactionsContainerLayout.isHiddenNextReaction = true;
            reactionsContainerLayout.invalidate();
            valueAnimator.setStartDelay(30);
            valueAnimator.start();
        } else {
            reactionsContainerLayout.setCustomEmojiReactionsBackground(false);
            final ValueAnimator finalAnimator = valueAnimator;
            HwEmojis.prepare(finalAnimator::start, cascadeAnimation);
        }
        HwEmojis.enableHw();
    }

    private void updateContainersAlpha() {
        if (!cascadeAnimation) {
            selectAnimatedEmojiDialog.searchBox.setAlpha(enterTransitionProgress);
            selectAnimatedEmojiDialog.emojiGridView.setAlpha(enterTransitionProgress);
            selectAnimatedEmojiDialog.emojiSearchGridView.setAlpha(enterTransitionProgress);
            selectAnimatedEmojiDialog.emojiTabs.setAlpha(enterTransitionProgress);
            selectAnimatedEmojiDialog.emojiTabsShadow.setAlpha(enterTransitionProgress);
        }
    }

    private void updateContentPosition() {
        selectAnimatedEmojiDialog.contentView.setTranslationX(cascadeAnimation ? 0 : containerView.enterTransitionOffsetX);
        selectAnimatedEmojiDialog.contentView.setTranslationY(containerView.enterTransitionOffsetY);
        selectAnimatedEmojiDialog.contentView.setPivotX(containerView.enterTransitionScalePx);
        selectAnimatedEmojiDialog.contentView.setPivotY(containerView.enterTransitionScalePy);
        selectAnimatedEmojiDialog.contentView.setScaleX(containerView.enterTransitionScale);
        selectAnimatedEmojiDialog.contentView.setScaleY(containerView.enterTransitionScale);
    }

    private void switchLayerType(boolean hardware) {
        int layerType = hardware ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        selectAnimatedEmojiDialog.emojiGridView.setLayerType(layerType, null);
        selectAnimatedEmojiDialog.searchBox.setLayerType(layerType, null);
        if (cascadeAnimation) {
            for (int i = 0; i < Math.min(selectAnimatedEmojiDialog.emojiTabs.contentView.getChildCount(), 16); i++) {
                View child = selectAnimatedEmojiDialog.emojiTabs.contentView.getChildAt(i);
                child.setLayerType(layerType, null);
            }
        } else {
            selectAnimatedEmojiDialog.emojiTabsShadow.setLayerType(layerType, null);
            selectAnimatedEmojiDialog.emojiTabs.setLayerType(layerType, null);
        }
    }

    HashSet<View> animatingEnterChild = new HashSet<>();
    ArrayList<ValueAnimator> animators = new ArrayList<>();

    private void setScaleForChild(View child, float value) {
        if (child instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
            ((SelectAnimatedEmojiDialog.ImageViewEmoji) child).setAnimatedScale(value);
        } else if (child instanceof EmojiTabsStrip.EmojiTabButton) {
            child.setScaleX(value);
            child.setScaleY(value);
        }
    }

    private void updateCascadeEnter(float progress, boolean enter) {
        int parentTop = (int) (selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.contentView.getY() + selectAnimatedEmojiDialog.emojiGridView.getY());
        ArrayList<View> animatedViews = null;
        boolean updated = false;
        for (int i = 0; i < selectAnimatedEmojiDialog.emojiGridView.getChildCount(); i++) {
            View child = selectAnimatedEmojiDialog.emojiGridView.getChildAt(i);
            if (animatingEnterChild.contains(child)) {
                continue;
            }
            float cy = parentTop + child.getTop() + child.getMeasuredHeight() / 2f;
            if (cy < drawingRect.bottom && cy > drawingRect.top && progress != 0) {
                if (animatedViews == null) {
                    animatedViews = new ArrayList<>();
                }
                animatedViews.add(child);
                animatingEnterChild.add(child);
            } else {
                setScaleForChild(child, 0f);
                updated = true;
            }
        }
        parentTop = (int) (selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.contentView.getY() + selectAnimatedEmojiDialog.emojiTabs.getY());
        for (int i = 0; i < selectAnimatedEmojiDialog.emojiTabs.contentView.getChildCount(); i++) {
            View child = selectAnimatedEmojiDialog.emojiTabs.contentView.getChildAt(i);
            if (animatingEnterChild.contains(child)) {
                continue;
            }
            float cy = parentTop + child.getTop() + child.getMeasuredHeight() / 2f;
            if (cy < drawingRect.bottom && cy > drawingRect.top && progress != 0) {
                if (animatedViews == null) {
                    animatedViews = new ArrayList<>();
                }
                animatedViews.add(child);
                animatingEnterChild.add(child);
            } else {
                setScaleForChild(child, 0f);
                updated = true;
            }
        }
        if (updated) {
            selectAnimatedEmojiDialog.emojiGridViewContainer.invalidate();
        }
        if (animatedViews != null) {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            ArrayList<View> finalAnimatedViews = animatedViews;
            valueAnimator.addUpdateListener(animation -> {
                float s = (float) animation.getAnimatedValue();
                for (int i = 0; i < finalAnimatedViews.size(); i++) {
                    View v = finalAnimatedViews.get(i);
                    setScaleForChild(v, s);
                }
                selectAnimatedEmojiDialog.emojiGridViewContainer.invalidate();
            });
            animators.add(valueAnimator);
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animators.remove(valueAnimator);
                    checkAnimationEnd(enter);
                }
            });
            valueAnimator.setDuration(350);
            valueAnimator.setInterpolator(new OvershootInterpolator(1f));
            valueAnimator.start();
        }
    }

    private void checkAnimationEnd(boolean enter) {
        if (animators.isEmpty()) {
            switchLayerType(false);
            HwEmojis.disableHw();
            notificationsLocker.unlock();
            selectAnimatedEmojiDialog.setEnterAnimationInProgress(false);
            if (enter) {
                selectAnimatedEmojiDialog.emojiTabs.showRecentTabStub(false);
                selectAnimatedEmojiDialog.emojiGridView.invalidate();
                selectAnimatedEmojiDialog.emojiGridView.invalidateViews();
                selectAnimatedEmojiDialog.searchBox.checkInitialization();
                if (reactionsContainerLayout.getPullingLeftProgress() > 0) {
                    reactionsContainerLayout.isHiddenNextReaction = false;
                    reactionsContainerLayout.onCustomEmojiWindowOpened();
                } else {
                    reactionsContainerLayout.isHiddenNextReaction = true;
                    reactionsContainerLayout.onCustomEmojiWindowOpened();
                }
                selectAnimatedEmojiDialog.resetBackgroundBitmaps();
                syncReactionFrames();
                containerView.invalidate();
            }
        }
    }

    private void syncReactionFrames() {
        for (int i = 0; i < selectAnimatedEmojiDialog.emojiGridView.getChildCount(); i++) {
            if (selectAnimatedEmojiDialog.emojiGridView.getChildAt(i) instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) selectAnimatedEmojiDialog.emojiGridView.getChildAt(i);
                if (imageViewEmoji.reaction != null) {
                    imageViewEmoji.notDraw = false;
                    imageViewEmoji.invalidate();
                }
            }
        }
    }

    public void removeView() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 7);
        AndroidUtilities.runOnUIThread(() -> {
            if (windowView.getParent() == null) {
                return;
            }
            if (attachToParent) {
                AndroidUtilities.removeFromParent(windowView);
            } else {
                try {
                    windowManager.removeView(windowView);
                } catch (Exception e) {

                }
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
    }

    public void dismiss() {
        if (dismissed) {
            return;
        }
        Bulletin.hideVisible();
        dismissed = true;
        AndroidUtilities.hideKeyboard(windowView);
        createTransition(false);
        if (wasFocused && baseFragment instanceof ChatActivity) {
            ((ChatActivity) baseFragment).onEditTextDialogClose(true, true);
        }
    }

    public void onDismissListener(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void dismiss(boolean animated) {
        if (dismissed && animated) {
            return;
        }
        dismissed = true;
        if (!animated) {
            removeView();
        } else {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
            valueAnimator.addUpdateListener(animation -> {
                dismissProgress = (float) animation.getAnimatedValue();
                containerView.setAlpha(1f - dismissProgress);
            });

            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView();
                }
            });
            valueAnimator.setDuration(150);
            valueAnimator.start();
        }
    }

    private int frameDrawCount = 0;

    public boolean isShowing() {
        return !dismissed;
    }

    public void dismissWithAlpha() {
        if (dismissed) {
            return;
        }
        Bulletin.hideVisible();
        dismissed = true;
        AndroidUtilities.hideKeyboard(windowView);
        windowView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimationEnd(false);
                enterTransitionProgress = 0f;
                reactionsContainerLayout.setCustomEmojiEnterProgress(Utilities.clamp(enterTransitionProgress, 1f, 0f));
                reactionsContainerLayout.setSkipDraw(false);
                windowView.setVisibility(View.GONE);
                removeView();
            }
        });
        if (wasFocused && baseFragment instanceof ChatActivity) {
            ((ChatActivity) baseFragment).onEditTextDialogClose(true, true);
        }
    }

    private class ContainerView extends FrameLayout {

        Drawable shadow;
        Rect shadowPad = new Rect();
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] radiusTmp = new int[4];


        public ContainerView(@NonNull Context context) {
            super(context);
            shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
            shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = AndroidUtilities.dp(7);
            shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            if (type == TYPE_STORY_LIKES) {
                backgroundPaint.setColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.13f));
            } else {
                backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (type == TYPE_STORY || (reactionsContainerLayout != null && reactionsContainerLayout.getDelegate() != null && reactionsContainerLayout.getDelegate().drawBackground())) {
                selectAnimatedEmojiDialog.invalidateSearchBox();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size;
            if (type == TYPE_STORY || type == TYPE_STORY_LIKES) {
                size = reactionsContainerLayout.getMeasuredWidth();
            } else {
                size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                int measuredSize = AndroidUtilities.dp(36) * 8 + AndroidUtilities.dp(12);
                if (measuredSize < size) {
                    size = measuredSize;
                }
            }
            int height = size;
            if (reactionsContainerLayout.showExpandableReactions()) {
                int rows = (int) Math.ceil(reactions.size() / 8f);
                if (rows <= 8) {
                    height = rows * AndroidUtilities.dp(36) + AndroidUtilities.dp(8);
                } else {
                    height = AndroidUtilities.dp(36) * 8 - AndroidUtilities.dp(8);
                }
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        HashMap<ReactionsLayoutInBubble.VisibleReaction, SelectAnimatedEmojiDialog.ImageViewEmoji> transitionReactions = new HashMap<>();

        float enterTransitionOffsetX = 0;
        float enterTransitionOffsetY = 0;
        float enterTransitionScale = 1f;
        float enterTransitionScalePx = 0;
        float enterTransitionScalePy = 0;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!isShowing) {
                return;
            }
            float progressClpamped = Utilities.clamp(enterTransitionProgress,1f, 0f);
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            AndroidUtilities.lerp(fromRect, AndroidUtilities.rectTmp, enterTransitionProgress, drawingRect);
            float radius = AndroidUtilities.lerp(fromRadius, AndroidUtilities.dp(8), enterTransitionProgress);


            transitionReactions.clear();
            if (type == TYPE_STORY || (reactionsContainerLayout.getDelegate() != null && reactionsContainerLayout.getDelegate().drawBackground())) {
                reactionsContainerLayout.getDelegate().drawRoundRect(canvas, drawingRect, radius, getX(), getBlurOffset(), 255, true);
            } else {
                shadow.setAlpha((int) (Utilities.clamp(progressClpamped / 0.05f, 1f, 0f) * 255));
                shadow.setBounds((int) drawingRect.left - shadowPad.left, (int) drawingRect.top - shadowPad.top, (int) drawingRect.right + shadowPad.right, (int) drawingRect.bottom + shadowPad.bottom);
                shadow.draw(canvas);
                canvas.drawRoundRect(drawingRect, radius, radius, backgroundPaint);
            }
            if (reactionsContainerLayout.hintView != null) {
                canvas.save();
                canvas.translate(drawingRect.left, drawingRect.top + reactionsContainerLayout.hintView.getY());
                canvas.saveLayerAlpha( 0, 0, reactionsContainerLayout.hintView.getMeasuredWidth(), reactionsContainerLayout.hintView.getMeasuredHeight(), (int) (255 * reactionsContainerLayout.hintView.getAlpha() * (1f - enterTransitionProgress)), Canvas.ALL_SAVE_FLAG);
                reactionsContainerLayout.hintView.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            float rightDelta = drawingRect.left - reactionsContainerLayout.rect.left + (drawingRect.width() - reactionsContainerLayout.rect.width());

            if (enterTransitionProgress > 0.05f) {
                canvas.save();
                canvas.translate(rightDelta, drawingRect.top - reactionsContainerLayout.rect.top + (drawingRect.height() - reactionsContainerLayout.rect.height()));
                reactionsContainerLayout.drawBubbles(canvas);
                canvas.restore();
            }
            enterTransitionOffsetX = 0;
            enterTransitionOffsetY = 0;
            enterTransitionScale = 1f;
            enterTransitionScalePx = 0;
            enterTransitionScalePy = 0;

            if (reactionsContainerLayout != null) {
                for (int i = 0; i < selectAnimatedEmojiDialog.emojiGridView.getChildCount(); i++) {
                    if (selectAnimatedEmojiDialog.emojiGridView.getChildAt(i) instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                        SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) selectAnimatedEmojiDialog.emojiGridView.getChildAt(i);
                        if (imageViewEmoji.reaction != null) {
                            transitionReactions.put(imageViewEmoji.reaction, imageViewEmoji);
                        }
                    }
                }

                int restoreCount = canvas.save();

                canvas.translate(drawingRect.left, drawingRect.top + (reactionsContainerLayout.getTopOffset() + reactionsContainerLayout.expandSize()) * (1f - enterTransitionProgress));

                float a = selectAnimatedEmojiDialog.emojiSearchGridView.getVisibility() == View.VISIBLE ? selectAnimatedEmojiDialog.emojiSearchGridView.getAlpha() : 0;
                float alpha = Math.max(1f - a, 1f - enterTransitionProgress);
                if (alpha != 1f) {
                    canvas.saveLayerAlpha(0, 0, drawingRect.width(), drawingRect.height(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                }
                int top = (int) (selectAnimatedEmojiDialog.getX() + selectAnimatedEmojiDialog.emojiGridView.getX());
                int left = (int) (selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.emojiGridView.getY());
                boolean isEmojiTabsVisible = selectAnimatedEmojiDialog.emojiTabs.getParent() != null;
                canvas.clipRect(left, isEmojiTabsVisible ? top + AndroidUtilities.dp(36) * enterTransitionProgress : 0, left + selectAnimatedEmojiDialog.emojiGridView.getMeasuredWidth(), top + selectAnimatedEmojiDialog.emojiGridView.getMeasuredHeight());
                for (int i = -1; i < reactionsContainerLayout.recyclerListView.getChildCount(); i++) {
                    View child;
                    if (i == -1) {
                        child = reactionsContainerLayout.nextRecentReaction;
                    } else {
                        child = reactionsContainerLayout.recyclerListView.getChildAt(i);
                    }
                    if (child.getLeft() < 0 || child.getVisibility() == View.GONE) {
                        continue;
                    }
                    canvas.save();

                    if (child instanceof ReactionsContainerLayout.ReactionHolderView) {
                        ReactionsContainerLayout.ReactionHolderView holderView = (ReactionsContainerLayout.ReactionHolderView) child;
                        SelectAnimatedEmojiDialog.ImageViewEmoji toImageView = transitionReactions.get(holderView.currentReaction);
                        float fromRoundRadiusLt = 0f;
                        float fromRoundRadiusRt = 0f;
                        float fromRoundRadiusLb = 0f;
                        float fromRoundRadiusRb = 0f;

                        float toRoundRadiusLt = 0f;
                        float toRoundRadiusRt = 0f;
                        float toRoundRadiusLb = 0f;
                        float toRoundRadiusRb = 0f;

                        float scale = 1f;
                        if (toImageView != null) {
                            float fromX = child.getX();
                            float fromY = child.getY();
                            if (i == -1) {
                                fromX -= reactionsContainerLayout.recyclerListView.getX();
                                fromY -= reactionsContainerLayout.recyclerListView.getY();
                            }
                            float toX = toImageView.getX() + selectAnimatedEmojiDialog.getX() + selectAnimatedEmojiDialog.emojiGridView.getX() - holderView.loopImageView.getX() - AndroidUtilities.dp(1);
                            float toY = toImageView.getY() + selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.gridViewContainer.getY() + selectAnimatedEmojiDialog.emojiGridView.getY() - holderView.loopImageView.getY();
                            float toImageViewSize = toImageView.getMeasuredWidth();
                            if (toImageView.selected) {
                                float sizeAfterScale = toImageViewSize * (0.8f + 0.2f * 0.3f);
                                toX += (toImageViewSize - sizeAfterScale) / 2f;
                                toY += (toImageViewSize - sizeAfterScale) / 2f;
                                toImageViewSize = sizeAfterScale;
                            }

                            float dX = AndroidUtilities.lerp(fromX, toX, enterTransitionProgress);
                            float dY = AndroidUtilities.lerp(fromY, toY, enterTransitionProgress);

                            float toScale = toImageViewSize / (float) holderView.loopImageView.getMeasuredWidth();
                            scale = AndroidUtilities.lerp(1f, toScale, enterTransitionProgress);
                            if (holderView.position == 0) {
                                fromRoundRadiusLb = fromRoundRadiusLt = AndroidUtilities.dp(6);
                                fromRoundRadiusRb = fromRoundRadiusRt = 0;
                            } else if (holderView.selected) {
                                fromRoundRadiusRb = fromRoundRadiusLb = fromRoundRadiusRt = fromRoundRadiusLt = AndroidUtilities.dp(6);
                            }

                            canvas.translate(dX, dY);
                            canvas.scale(scale, scale);

                            if (enterTransitionOffsetX == 0 && enterTransitionOffsetY == 0) {
                                enterTransitionOffsetX = AndroidUtilities.lerp(fromRect.left + fromX - toX, 0f, enterTransitionProgress);
                                enterTransitionOffsetY = AndroidUtilities.lerp(fromRect.top + fromY - toY, 0f, enterTransitionProgress);
                                enterTransitionScale = AndroidUtilities.lerp(1f / toScale, 1f, enterTransitionProgress);
                                enterTransitionScalePx = toX;
                                enterTransitionScalePy = toY;
                            }
                        } else {
                            canvas.translate(child.getX() + holderView.loopImageView.getX(), child.getY() + holderView.loopImageView.getY());
                        }

                        if (toImageView != null) {
                            if (toImageView.selected) {
                                float cx = holderView.getMeasuredWidth() / 2f;
                                float cy = holderView.getMeasuredHeight() / 2f;
                                float fromSize = holderView.getMeasuredWidth() - AndroidUtilities.dp(2);
                                float toSize = toImageView.getMeasuredWidth() - AndroidUtilities.dp(2);
                                float finalSize = AndroidUtilities.lerp(fromSize, toSize / scale, enterTransitionProgress);
                                AndroidUtilities.rectTmp.set(cx - finalSize / 2f, cy - finalSize / 2f, cx + finalSize / 2f, cy + finalSize / 2f);
                                float rectRadius = AndroidUtilities.lerp(fromSize / 2f, AndroidUtilities.dp(4), enterTransitionProgress);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, rectRadius, rectRadius, selectAnimatedEmojiDialog.selectorPaint);
                            }
                            holderView.drawSelected = false;
                            if (fromRoundRadiusLb != 0 || toRoundRadiusLb != 0) {
                                ImageReceiver imageReceiver = holderView.loopImageView.getImageReceiver();
                                holderView.checkPlayLoopImage();
                                if (holderView.loopImageView.animatedEmojiDrawable != null && holderView.loopImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                                    imageReceiver = holderView.loopImageView.animatedEmojiDrawable.getImageReceiver();
                                }
                                int[] oldRadius = imageReceiver.getRoundRadius();
                                for (int k = 0; k < 4; k++) {
                                    radiusTmp[k] = oldRadius[k];
                                }
                                imageReceiver.setRoundRadius(
                                        (int) AndroidUtilities.lerp(fromRoundRadiusLt, toRoundRadiusLt, enterTransitionProgress),
                                        (int) AndroidUtilities.lerp(fromRoundRadiusRt, toRoundRadiusRt, enterTransitionProgress),
                                        (int) AndroidUtilities.lerp(fromRoundRadiusRb, toRoundRadiusRb, enterTransitionProgress),
                                        (int) AndroidUtilities.lerp(fromRoundRadiusLb, toRoundRadiusLb, enterTransitionProgress)
                                );
                                holderView.draw(canvas);
                                imageReceiver.setRoundRadius(radiusTmp);
                            } else {
                                holderView.draw(canvas);
                            }
                            holderView.drawSelected = true;
                            if (!toImageView.notDraw) {
                                toImageView.notDraw = true;
                                toImageView.invalidate();
                            }
                        } else {
                            if (holderView.hasEnterAnimation && holderView.loopImageView.getImageReceiver().getLottieAnimation() == null) {
                                float oldAlpha = holderView.enterImageView.getImageReceiver().getAlpha();
                                holderView.enterImageView.getImageReceiver().setAlpha(oldAlpha * (1f - progressClpamped));
                                holderView.enterImageView.draw(canvas);
                                holderView.enterImageView.getImageReceiver().setAlpha(oldAlpha);
                            } else {
                                holderView.checkPlayLoopImage();
                                ImageReceiver imageReceiver = holderView.loopImageView.getImageReceiver();
                                if (holderView.loopImageView.animatedEmojiDrawable != null && holderView.loopImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                                    imageReceiver = holderView.loopImageView.animatedEmojiDrawable.getImageReceiver();
                                }
                                float oldAlpha = imageReceiver.getAlpha();
                                imageReceiver.setAlpha(oldAlpha * (1f - progressClpamped));
                                holderView.loopImageView.draw(canvas);
                                imageReceiver.setAlpha(oldAlpha);
                            }
                        }
                        if (holderView.loopImageView.getVisibility() != View.VISIBLE) {
                            invalidate();
                        }
                    } else {
                        canvas.translate(child.getX() + drawingRect.width() - reactionsContainerLayout.rect.width(), child.getY() + fromRect.top - drawingRect.top);
                        canvas.saveLayerAlpha(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight(), (int) (255 * (1f - progressClpamped)), Canvas.ALL_SAVE_FLAG);
                        canvas.scale(1f - enterTransitionProgress, 1f - enterTransitionProgress, child.getMeasuredWidth() >> 1, child.getMeasuredHeight() >> 1);
                        child.draw(canvas);
                        canvas.restore();
                    }
                    canvas.restore();
                }
                canvas.restoreToCount(restoreCount);
            }

            boolean beforeLollipop = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
            if (beforeLollipop) {
                if (invalidatePath) {
                    invalidatePath = false;
                    pathToClipApi20.rewind();
                    pathToClipApi20.addRoundRect(drawingRect, radius, radius, Path.Direction.CW);
                }
                canvas.save();
                canvas.clipPath(pathToClipApi20);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                super.dispatchDraw(canvas);
            }

            if (frameDrawCount < 5) {
                if (frameDrawCount == 3) {
                    reactionsContainerLayout.setSkipDraw(true);
                }
                frameDrawCount++;
            }

            selectAnimatedEmojiDialog.drawBigReaction(canvas, this);
            if (valueAnimator != null) {
                invalidate();
            }
            HwEmojis.exec();
        }
    }

    private float getBlurOffset() {
        if (type == TYPE_STORY) {
            return containerView.getY() - AndroidUtilities.statusBarHeight;
        }
        return containerView.getY() + windowView.getY();
    }

    public void setRecentReactions(List<ReactionsLayoutInBubble.VisibleReaction> reactions) {
        selectAnimatedEmojiDialog.setRecentReactions(reactions);
    }

    public SelectAnimatedEmojiDialog getSelectAnimatedEmojiDialog() {
        return selectAnimatedEmojiDialog;
    }
}
