package org.telegram.ui.Components.Reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.SelectAnimatedEmojiDialog;

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

    public CustomEmojiReactionsWindow(BaseFragment baseFragment, List<ReactionsLayoutInBubble.VisibleReaction> reactions, HashSet<ReactionsLayoutInBubble.VisibleReaction> selectedReactions, ReactionsContainerLayout reactionsContainerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.reactions = reactions;
        this.baseFragment = baseFragment;
        this.resourcesProvider = resourcesProvider;
        Context context = baseFragment.getContext();
        windowView = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    dismiss();
                    return true;
                }
                return false;
            }

            @Override
            protected void dispatchSetPressed(boolean pressed) {

            }
        };
        windowView.setOnClickListener(v -> dismiss());

        containerView = new ContainerView(context);
        selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(baseFragment, context, false, null, SelectAnimatedEmojiDialog.TYPE_REACTIONS, resourcesProvider) {
            @Override
            protected void onReactionClick(ImageViewEmoji emoji, ReactionsLayoutInBubble.VisibleReaction reaction) {
                reactionsContainerLayout.onReactionClicked(emoji, reaction, false);
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
        EditTextBoldCursor editTextBoldCursor = new EditTextBoldCursor(context) {

            boolean focusable = false;

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (!focusable) {
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                    lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
                    lp.format = PixelFormat.TRANSLUCENT;
                    windowManager.updateViewLayout(windowView, lp);
                }
                return super.dispatchTouchEvent(event);
            }
        };
        editTextBoldCursor.setBackgroundColor(Color.BLACK);
        //containerView.addView(editTextBoldCursor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        lp.format = PixelFormat.TRANSLUCENT;

        windowManager = baseFragment.getParentActivity().getWindowManager();
        windowManager.addView(windowView, lp);

        this.reactionsContainerLayout = reactionsContainerLayout;
        reactionsContainerLayout.prepareAnimation(true);
        containerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                containerView.removeOnLayoutChangeListener(this);
                reactionsContainerLayout.prepareAnimation(false);
                createTransition(true);
            }
        });
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 7);
    }

    private void showUnlockPremiumAlert() {
        if (baseFragment instanceof ChatActivity) {
            baseFragment.showDialog(new PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
        }
    }

    private void createTransition(boolean enter) {
        fromRect.set(reactionsContainerLayout.rect);
        fromRadius = reactionsContainerLayout.radius;
        int[] location = new int[2];
        reactionsContainerLayout.getLocationOnScreen(location);
        float y = location[1] - AndroidUtilities.dp(44) - AndroidUtilities.dp(34);
        if (y + containerView.getMeasuredHeight() > windowView.getMeasuredHeight() - AndroidUtilities.dp(32)) {
            y = windowView.getMeasuredHeight() - AndroidUtilities.dp(32) - containerView.getMeasuredHeight();
        }
        if (y < AndroidUtilities.dp(16)) {
            y = AndroidUtilities.dp(16);
        }

        containerView.setTranslationX(location[0] - AndroidUtilities.dp(2));
        containerView.setTranslationY(y);
        fromRect.offset(location[0] - containerView.getX(), location[1] - containerView.getY());

        reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress);

        if (enter) {
            enterTransitionFinished = false;
        }
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(enterTransitionProgress, enter ? 1f : 0);
        valueAnimator.addUpdateListener(animation -> {
            enterTransitionProgress = (float) animation.getAnimatedValue();
            reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress);
            invalidatePath = true;
            containerView.invalidate();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                enterTransitionProgress = enter ? 1f : 0f;
                if (enter) {
                    enterTransitionFinished = true;
                    selectAnimatedEmojiDialog.resetBackgroundBitmaps();
                    reactionsContainerLayout.onCustomEmojiWindowOpened();
                    containerView.invalidate();
                }
                reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress);
                if (!enter) {
                    reactionsContainerLayout.setSkipDraw(false);
                }
                if (!enter) {
                    removeView();
                }
            }
        });
        valueAnimator.setStartDelay(30);
        valueAnimator.setDuration(350);
        valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        valueAnimator.start();
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
        createTransition(false);
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
            dimPaint.setAlpha((int) (0.2f * enterTransitionProgress * 255));
            canvas.drawPaint(dimPaint);
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            AndroidUtilities.lerp(fromRect, AndroidUtilities.rectTmp, enterTransitionProgress, drawingRect);
            float radius = AndroidUtilities.lerp(fromRadius, AndroidUtilities.dp(8), enterTransitionProgress);

            shadow.setAlpha((int) (Utilities.clamp(enterTransitionProgress / 0.05f, 1f, 0f) * 255));
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

            if (reactionsContainerLayout != null && enterTransitionProgress != 1f) {
                for (int i = 0; i < selectAnimatedEmojiDialog.emojiGridView.getChildCount(); i++) {
                    if (selectAnimatedEmojiDialog.emojiGridView.getChildAt(i) instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                        SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) selectAnimatedEmojiDialog.emojiGridView.getChildAt(i);
                        if (imageViewEmoji.reaction != null) {
                            transitionReactions.put(imageViewEmoji.reaction, imageViewEmoji);
                            imageViewEmoji.notDraw = false;
                            imageViewEmoji.invalidate();
                        }
                    }
                }

                canvas.save();

                canvas.translate(drawingRect.left, drawingRect.top + reactionsContainerLayout.expandSize() * (1f - enterTransitionProgress));
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
                            float fromX = child.getX() + holderView.loopImageView.getX();
                            float fromY = child.getY() + holderView.loopImageView.getY();
                            if (i == -1) {
                                fromX -= reactionsContainerLayout.recyclerListView.getX();
                                fromY -= reactionsContainerLayout.recyclerListView.getY();
                            }
                            float toX = toImageView.getX() + selectAnimatedEmojiDialog.getX() + selectAnimatedEmojiDialog.emojiGridView.getX();
                            float toY = toImageView.getY() + selectAnimatedEmojiDialog.getY() + selectAnimatedEmojiDialog.emojiGridView.getY();
                            float toImageViewSize = toImageView.getMeasuredWidth();
                            if (toImageView.selected) {
                                float sizeAfterScale = toImageViewSize * (0.8f + 0.2f * 0.3f);
                                toX += (toImageViewSize - sizeAfterScale) /2f;
                                toY += (toImageViewSize - sizeAfterScale) /2f;
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

                        if (holderView.loopImageView.getVisibility() == View.VISIBLE && toImageView != null && imageIsEquals(holderView.loopImageView, toImageView)) {
                            if (toImageView.selected) {
                                float cx = holderView.loopImageView.getMeasuredWidth() / 2f;
                                float cy = holderView.loopImageView.getMeasuredHeight() / 2f;
                                float fromSize = holderView.getMeasuredWidth() - AndroidUtilities.dp(2);
                                float toSize = toImageView.getMeasuredWidth() - AndroidUtilities.dp(2);
                                float finalSize = AndroidUtilities.lerp(fromSize, toSize / scale, enterTransitionProgress);
                                AndroidUtilities.rectTmp.set(cx - finalSize / 2f, cy - finalSize / 2f, cx + finalSize / 2f, cy + finalSize / 2f);
                                float rectRadius = AndroidUtilities.lerp(fromSize / 2f, AndroidUtilities.dp(4), enterTransitionProgress);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, rectRadius, rectRadius, selectAnimatedEmojiDialog.selectorPaint);
                            }
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
                                holderView.loopImageView.draw(canvas);
                                holderView.loopImageView.draw(canvas);
                                imageReceiver.setRoundRadius(radiusTmp);
                            } else {
                                holderView.loopImageView.draw(canvas);
                            }
                            if (!toImageView.notDraw) {
                                toImageView.notDraw = true;
                                toImageView.invalidate();
                            }
                        } else {
                            if (holderView.hasEnterAnimation) {
                                float oldAlpha = holderView.enterImageView.getImageReceiver().getAlpha();
                                holderView.enterImageView.getImageReceiver().setAlpha(oldAlpha * (1f - enterTransitionProgress));
                                holderView.enterImageView.draw(canvas);
                                holderView.enterImageView.getImageReceiver().setAlpha(oldAlpha);
                            } else {
                                ImageReceiver imageReceiver = holderView.loopImageView.getImageReceiver();
                                if (holderView.loopImageView.animatedEmojiDrawable != null &&  holderView.loopImageView.animatedEmojiDrawable.getImageReceiver() != null) {
                                    imageReceiver = holderView.loopImageView.animatedEmojiDrawable.getImageReceiver();
                                }
                                float oldAlpha = imageReceiver.getAlpha();
                                imageReceiver.setAlpha(oldAlpha * (1f - enterTransitionProgress));
                                holderView.loopImageView.draw(canvas);
                                imageReceiver.setAlpha(oldAlpha);
                            }
                        }
                    } else {
                        canvas.translate(child.getX() + drawingRect.width() - reactionsContainerLayout.rect.width(), child.getY() + fromRect.top - drawingRect.top);
                        canvas.saveLayerAlpha(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight(), (int) (255 * (1f - enterTransitionProgress)), Canvas.ALL_SAVE_FLAG);
                        canvas.scale(1f - enterTransitionProgress, 1f - enterTransitionProgress, child.getMeasuredWidth() >> 1, child.getMeasuredHeight() >> 1);
                        child.draw(canvas);
                        canvas.restore();
                    }
                    canvas.restore();
                }
                canvas.restore();
            }

            if (invalidatePath) {
                invalidatePath = false;
                pathToClip.rewind();
                pathToClip.addRoundRect(drawingRect, radius, radius, Path.Direction.CW);
            }
            canvas.save();
            canvas.clipPath(pathToClip);
            canvas.translate(enterTransitionOffsetX, enterTransitionOffsetY);
            canvas.scale(enterTransitionScale, enterTransitionScale, enterTransitionScalePx, enterTransitionScalePy);
            selectAnimatedEmojiDialog.setAlpha(enterTransitionProgress);
            super.dispatchDraw(canvas);
            canvas.restore();

            if (frameDrawCount < 5) {
                if (frameDrawCount == 3) {
                    reactionsContainerLayout.setSkipDraw(true);
                }
                frameDrawCount++;
            }

            selectAnimatedEmojiDialog.drawBigReaction(canvas, this);
            invalidate();
        }
    }

    private boolean imageIsEquals(BackupImageView loopImageView, SelectAnimatedEmojiDialog.ImageViewEmoji toImageView) {
        if (toImageView.span == null) {
            return toImageView.imageReceiver.getLottieAnimation() == loopImageView.getImageReceiver().getLottieAnimation();
        }
        if (loopImageView.animatedEmojiDrawable != null) {
            return toImageView.span.getDocumentId() == loopImageView.animatedEmojiDrawable.getDocumentId();
        }
        return false;
    }

    public void setRecentReactions(List<ReactionsLayoutInBubble.VisibleReaction> reactions) {
        selectAnimatedEmojiDialog.setRecentReactions(reactions);
    }

    public SelectAnimatedEmojiDialog getSelectAnimatedEmojiDialog() {
        return selectAnimatedEmojiDialog;
    }
}
