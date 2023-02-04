package org.telegram.ui.Components.Reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CustomEmojiReactionsWindow {

    ContainerView containerView;
    WindowManager windowManager;
    FrameLayout windowView;

    float fromRadius;
    RectF fromRect = new RectF();
    public RectF drawingRect = new RectF();
    float enterTransitionProgress;
    boolean enterTransitionFinished;
    boolean isShowing;

    SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;
    ReactionsContainerLayout reactionsContainerLayout;
    Path pathToClip = new Path();
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

    public CustomEmojiReactionsWindow(BaseFragment baseFragment, List<ReactionsLayoutInBubble.VisibleReaction> reactions, HashSet<ReactionsLayoutInBubble.VisibleReaction> selectedReactions, ReactionsContainerLayout reactionsContainerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.reactions = reactions;
        this.baseFragment = baseFragment;
        this.resourcesProvider = resourcesProvider;
        Context context = baseFragment.getContext();
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

        };
        windowView.setOnClickListener(v -> {
            if (enterTransitionFinished) {
                dismiss();
            }
        });

        // sizeNotifierFrameLayout.setFitsSystemWindows(true);

        containerView = new ContainerView(context);
        selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(baseFragment, context, false, null, SelectAnimatedEmojiDialog.TYPE_REACTIONS, resourcesProvider) {
            @Override
            protected void onInputFocus() {
                if (!wasFocused) {
                    wasFocused = true;
                    windowManager.updateViewLayout(windowView, createLayoutParams(true));
                    if (baseFragment instanceof ChatActivity) {
                        ((ChatActivity) baseFragment).needEnterText();
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
        WindowManager.LayoutParams lp = createLayoutParams(false);

        windowManager = baseFragment.getParentActivity().getWindowManager();
        windowManager.addView(windowView, lp);

        this.reactionsContainerLayout = reactionsContainerLayout;
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
        if (y + containerView.getMeasuredHeight() > windowView.getMeasuredHeight() - keyboardHeight - AndroidUtilities.dp(32)) {
            y = windowView.getMeasuredHeight() - keyboardHeight - containerView.getMeasuredHeight() - AndroidUtilities.dp(32);
        }
        if (y < 0) {
            y = 0;
        }
        containerView.animate().translationY(y).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    private WindowManager.LayoutParams createLayoutParams(boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
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
        }
    }

    int[] location = new int[2];
    int animationIndex;

    private void createTransition(boolean enter) {
        fromRect.set(reactionsContainerLayout.rect);
        fromRadius = reactionsContainerLayout.radius;

        int[] windowLocation = new int[2];
        if (enter) {
            reactionsContainerLayout.getLocationOnScreen(location);
        }
        windowView.getLocationOnScreen(windowLocation);
        float y = location[1] - windowLocation[1] - AndroidUtilities.dp(44) - AndroidUtilities.dp(52) - (selectAnimatedEmojiDialog.includeHint ? AndroidUtilities.dp(26) : 0);
        if (y + containerView.getMeasuredHeight() > windowView.getMeasuredHeight() - AndroidUtilities.dp(32)) {
            y = windowView.getMeasuredHeight() - AndroidUtilities.dp(32) - containerView.getMeasuredHeight();
        }
        if (y < AndroidUtilities.dp(16)) {
            y = AndroidUtilities.dp(16);
        }

        containerView.setTranslationX(location[0] - windowLocation[0] - AndroidUtilities.dp(2));
        if (!enter) {
            yTranslation = containerView.getTranslationY();
        } else {
            yTranslation = y;
            containerView.setTranslationY(yTranslation);
        }

        fromRect.offset(location[0] - windowLocation[0] - containerView.getX(), location[1] - windowLocation[1] - containerView.getY());

        reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress);

        if (enter) {
            cascadeAnimation = false;//SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH;
            enterTransitionFinished = false;
        } else {
            cascadeAnimation = false;
        }
        if (cascadeAnimation) {
            updateCascadeEnter(0);
        }
        selectAnimatedEmojiDialog.setEnterAnimationInProgress(true);
        account = UserConfig.selectedAccount;
        animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null);
        valueAnimator = ValueAnimator.ofFloat(enterTransitionProgress, enter ? 1f : 0);
        valueAnimator.addUpdateListener(animation -> {
            valueAnimator = null;
            enterTransitionProgress = (float) animation.getAnimatedValue();
            reactionsContainerLayout.setCustomEmojiEnterProgress(Utilities.clamp(enterTransitionProgress,1f, 0));
            invalidatePath = true;
            containerView.invalidate();

            if (cascadeAnimation) {
                updateCascadeEnter(enterTransitionProgress);
            }
        });
        if (!enter) {
            syncReactionFrames(enter);
        }
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimationEnd();
                enterTransitionProgress = enter ? 1f : 0f;
                if (enter) {
                    enterTransitionFinished = true;
                    selectAnimatedEmojiDialog.resetBackgroundBitmaps();
                    reactionsContainerLayout.onCustomEmojiWindowOpened();
                    containerView.invalidate();
                }
                reactionsContainerLayout.setCustomEmojiEnterProgress(Utilities.clamp(enterTransitionProgress, 1f, 0f));
                if (enter) {
                    syncReactionFrames(enter);
                }
                if (!enter) {
                    reactionsContainerLayout.setSkipDraw(false);
                }
                if (!enter) {
                    removeView();
                }
            }
        });
        valueAnimator.setStartDelay(30);
        if (cascadeAnimation) {
            valueAnimator.setDuration(450);
            valueAnimator.setInterpolator(new OvershootInterpolator(1f));
        } else {
            valueAnimator.setDuration(350);
            valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        }
        valueAnimator.start();
        containerView.invalidate();
    }

    HashSet<View> animatingEnterChild = new HashSet<>();
    ArrayList<ValueAnimator> animators = new ArrayList<>();

    private void updateCascadeEnter(float progress) {
        int fullHeight = selectAnimatedEmojiDialog.contentView.getHeight();
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
                child.setScaleX(0f);
                child.setScaleY(0f);
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
                child.setScaleX(0f);
                child.setScaleY(0f);
                updated = true;
            }
        }
        if (updated) {
            selectAnimatedEmojiDialog.emojiGridView.invalidate();
            selectAnimatedEmojiDialog.contentView.invalidate();
            selectAnimatedEmojiDialog.emojiTabs.contentView.invalidate();
        }
        if (animatedViews != null) {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            ArrayList<View> finalAnimatedViews = animatedViews;
            valueAnimator.addUpdateListener(animation -> {
                float s = (float) animation.getAnimatedValue();
                for (int i = 0; i < finalAnimatedViews.size(); i++) {
                    finalAnimatedViews.get(i).setScaleX(s);
                    finalAnimatedViews.get(i).setScaleY(s);
                }
                selectAnimatedEmojiDialog.emojiGridView.invalidate();
                selectAnimatedEmojiDialog.contentView.invalidate();
                selectAnimatedEmojiDialog.emojiTabs.contentView.invalidate();
            });
            animators.add(valueAnimator);
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animators.remove(valueAnimator);
                    checkAnimationEnd();
                }
            });
            valueAnimator.setDuration(350);
            valueAnimator.setInterpolator(new OvershootInterpolator(1f));
            valueAnimator.start();
        }
    }

    private void checkAnimationEnd() {
        if (animators.isEmpty()) {
            NotificationCenter.getInstance(account).onAnimationFinish(animationIndex);
            selectAnimatedEmojiDialog.setEnterAnimationInProgress(false);
        }
    }

    private void syncReactionFrames(boolean enter) {
        HashMap<ReactionsLayoutInBubble.VisibleReaction, SelectAnimatedEmojiDialog.ImageViewEmoji> transitionReactions = new HashMap<>();

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
            try {
                windowManager.removeView(windowView);
            } catch (Exception e) {

            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
    }

    private void dismiss() {
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

    private class ContainerView extends FrameLayout {

        Drawable shadow;
        Rect shadowPad = new Rect();
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] radiusTmp = new int[4];


        public ContainerView(@NonNull Context context) {
            super(context);
            shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
            shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = AndroidUtilities.dp(7);
            shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            int measuredSize = AndroidUtilities.dp(36) * 8 + AndroidUtilities.dp(12);
            if (measuredSize < size) {
                size = measuredSize;
            }
            int height = size;
//            if (height * 1.2 < MeasureSpec.getSize(heightMeasureSpec)) {
//                height *= 1.2;
//            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        HashMap<ReactionsLayoutInBubble.VisibleReaction, SelectAnimatedEmojiDialog.ImageViewEmoji> transitionReactions = new HashMap<>();

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!isShowing) {
                return;
            }
            float progressClpamped = Utilities.clamp(enterTransitionProgress,1f, 0f);
            dimPaint.setAlpha((int) (0.2f * progressClpamped * 255));
            canvas.drawPaint(dimPaint);
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            AndroidUtilities.lerp(fromRect, AndroidUtilities.rectTmp, enterTransitionProgress, drawingRect);
            float radius = AndroidUtilities.lerp(fromRadius, AndroidUtilities.dp(8), enterTransitionProgress);

            shadow.setAlpha((int) (Utilities.clamp(progressClpamped / 0.05f, 1f, 0f) * 255));
            shadow.setBounds((int) drawingRect.left - shadowPad.left, (int) drawingRect.top - shadowPad.top, (int) drawingRect.right + shadowPad.right, (int) drawingRect.bottom + shadowPad.bottom);
            shadow.draw(canvas);

            transitionReactions.clear();
            canvas.drawRoundRect(drawingRect, radius, radius, backgroundPaint);

            float rightDelta = drawingRect.left - reactionsContainerLayout.rect.left + (drawingRect.width() - reactionsContainerLayout.rect.width());

            if (enterTransitionProgress > 0.05f) {
                canvas.save();
                canvas.translate(rightDelta, drawingRect.top - reactionsContainerLayout.rect.top + (drawingRect.height() - reactionsContainerLayout.rect.height()));
                reactionsContainerLayout.drawBubbles(canvas);
                canvas.restore();
            }
            float enterTransitionOffsetX = 0;
            float enterTransitionOffsetY = 0;
            float enterTransitionScale = 1f;
            float enterTransitionScalePx = 0;
            float enterTransitionScalePy = 0;

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

                canvas.translate(drawingRect.left, drawingRect.top + reactionsContainerLayout.expandSize() * (1f - enterTransitionProgress));

                float alpha = Math.max(selectAnimatedEmojiDialog.emojiGridView.getAlpha(), 1f - enterTransitionProgress);
                if (alpha != 1f) {
                    canvas.saveLayerAlpha(0, 0, drawingRect.width(), drawingRect.height(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                }
                int top = (int) (selectAnimatedEmojiDialog.getX() + selectAnimatedEmojiDialog.emojiGridView.getX());
                int left = (int) (selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.emojiGridView.getY());
                canvas.clipRect(left, top + AndroidUtilities.dp(36) * enterTransitionProgress, left + selectAnimatedEmojiDialog.emojiGridView.getMeasuredHeight(), top + selectAnimatedEmojiDialog.emojiGridView.getMeasuredWidth());
                for (int i = -1; i < reactionsContainerLayout.recyclerListView.getChildCount(); i++) {
                    View child;
                    if (enterTransitionProgress == 1 && i == -1) {
                        continue;
                    }
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

            if (invalidatePath) {
                invalidatePath = false;
                pathToClip.rewind();
                pathToClip.addRoundRect(drawingRect, radius, radius, Path.Direction.CW);
            }
            canvas.save();
            canvas.clipPath(pathToClip);
            canvas.translate(cascadeAnimation ? 0 : enterTransitionOffsetX, enterTransitionOffsetY);
            canvas.scale(enterTransitionScale, enterTransitionScale, enterTransitionScalePx, enterTransitionScalePy);
            if (!cascadeAnimation) {
                selectAnimatedEmojiDialog.setAlpha(enterTransitionProgress);
            }
            super.dispatchDraw(canvas);
            canvas.restore();

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
        }
    }

    public void setRecentReactions(List<ReactionsLayoutInBubble.VisibleReaction> reactions) {
        selectAnimatedEmojiDialog.setRecentReactions(reactions);
    }

    public SelectAnimatedEmojiDialog getSelectAnimatedEmojiDialog() {
        return selectAnimatedEmojiDialog;
    }
}
