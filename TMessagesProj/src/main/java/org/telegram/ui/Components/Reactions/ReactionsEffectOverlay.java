package org.telegram.ui.Components.Reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ReactionsContainerLayout;

public class ReactionsEffectOverlay {

    public final static int LONG_ANIMATION = 0;
    public final static int SHORT_ANIMATION = 1;
    public final static int ONLY_MOVE_ANIMATION = 2;

    private final int animationType;
    @SuppressLint("StaticFieldLeak")
    public static ReactionsEffectOverlay currentOverlay;
    public static ReactionsEffectOverlay currentShortOverlay;

    private final AnimationView effectImageView;
    private final AnimationView emojiImageView;
    private final AnimationView emojiStaticImageView;
    private final FrameLayout container;
    private final BaseFragment fragment;
    private final int currentAccount;
    boolean animateIn;
    float animateInProgress;
    float animateOutProgress;

    FrameLayout windowView;
    BackupImageView backupImageView;
    private static int uniqPrefix;

    int[] loc = new int[2];
    private WindowManager windowManager;
    private boolean dismissed;
    private float dismissProgress;
    private final int messageId;
    private final long groupId;
    private final String reaction;
    private float lastDrawnToX;
    private float lastDrawnToY;
    private boolean started;
    private ReactionsContainerLayout.ReactionHolderView holderView = null;
    private boolean wasScrolled;
    private ChatMessageCell cell;
    private boolean finished;
    private boolean useWindow;
    private ViewGroup decorView;

    private ReactionsEffectOverlay(Context context, BaseFragment fragment, ReactionsContainerLayout reactionsLayout, ChatMessageCell cell, float x, float y, String reaction, int currentAccount, int animationType) {
        this.fragment = fragment;
        this.messageId = cell.getMessageObject().getId();
        this.groupId = cell.getMessageObject().getGroupId();
        this.reaction = reaction;
        this.animationType = animationType;
        this.currentAccount = currentAccount;
        this.cell = cell;
        ReactionsLayoutInBubble.ReactionButton reactionButton = cell.getReactionButton(reaction);
        float fromX, fromY, fromHeight, fromWidth;
        ChatActivity chatActivity = (fragment instanceof ChatActivity) ? (ChatActivity) fragment : null;
        if (reactionsLayout != null) {
            for (int i = 0; i < reactionsLayout.recyclerListView.getChildCount(); i++) {
                if (((ReactionsContainerLayout.ReactionHolderView) reactionsLayout.recyclerListView.getChildAt(i)).currentReaction.reaction.equals(reaction)) {
                    holderView = ((ReactionsContainerLayout.ReactionHolderView) reactionsLayout.recyclerListView.getChildAt(i));
                    break;
                }
            }
        }
        boolean fromHolder = holderView != null || (x != 0 && y != 0);
        if (holderView != null) {
            holderView.getLocationOnScreen(loc);
            fromX = loc[0] + holderView.backupImageView.getX();
            fromY = loc[1] + holderView.backupImageView.getY();
            fromHeight = holderView.backupImageView.getWidth() * holderView.getScaleX();
        } else if (reactionButton != null) {
            cell.getLocationInWindow(loc);
            fromX = loc[0] + cell.reactionsLayoutInBubble.x + reactionButton.x + reactionButton.imageReceiver.getImageX();
            fromY = loc[1] + cell.reactionsLayoutInBubble.y + reactionButton.y + reactionButton.imageReceiver.getImageY();
            fromHeight = reactionButton.imageReceiver.getImageHeight();
            fromWidth = reactionButton.imageReceiver.getImageWidth();
        } else {
            ((View) cell.getParent()).getLocationInWindow(loc);
            fromX = loc[0] + x;
            fromY = loc[1] + y;
            fromHeight = 0;
            fromWidth = 0;
        }

        int size;
        if (animationType == ONLY_MOVE_ANIMATION) {
            size = AndroidUtilities.dp(34);
        } else if (animationType == SHORT_ANIMATION) {
            size = AndroidUtilities.dp(80);
        } else {
            size = Math.round(Math.min(AndroidUtilities.dp(350), Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.8f);
        }
        int sizeForFilter = (int) (2f * size / AndroidUtilities.density);
        int emojiSize = size >> 1;
        int emojiSizeForFilter = sizeForFilter >> 1;

        float fromScale = fromHeight / (float) emojiSize;
        animateInProgress = 0f;
        animateOutProgress = 0f;

        container = new FrameLayout(context);
        windowView = new FrameLayout(context) {

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (dismissed) {
                    if (dismissProgress != 1f) {
                        dismissProgress += 16 / 150f;
                        if (dismissProgress > 1f) {
                            dismissProgress = 1f;
                            AndroidUtilities.runOnUIThread(() -> {
                                removeCurrentView();
                            });
                        }
                    }
                    if (dismissProgress != 1f) {
                        setAlpha(1f - dismissProgress);
                        super.dispatchDraw(canvas);
                    }
                    invalidate();
                    return;
                }
                if (!started) {
                    invalidate();
                    return;
                } else {
                    if (holderView != null) {
                        holderView.backupImageView.setAlpha(0);
                        holderView.pressedBackupImageView.setAlpha(0);
                    }
                }
                ChatMessageCell drawingCell;
                if (fragment instanceof ChatActivity) {
                    drawingCell = ((ChatActivity) fragment).findMessageCell(messageId, false);
                } else {
                    drawingCell = cell;
                }
                float toX, toY, toH;

                if (cell.getMessageObject().shouldDrawReactionsInLayout()) {
                    toH = AndroidUtilities.dp(20);
                } else {
                    toH = AndroidUtilities.dp(14);
                }
                if (drawingCell != null) {
                    cell.getLocationInWindow(loc);

                    ReactionsLayoutInBubble.ReactionButton reactionButton = cell.getReactionButton(reaction);
                    toX = loc[0] + cell.reactionsLayoutInBubble.x;
                    toY = loc[1] + cell.reactionsLayoutInBubble.y;
                    if (reactionButton != null) {
                        toX += reactionButton.x + reactionButton.imageReceiver.getImageX();
                        toY += reactionButton.y + reactionButton.imageReceiver.getImageY();
                    }
                    if (chatActivity != null) {
                        toY += chatActivity.drawingChatLisViewYoffset;
                    }
                    if (drawingCell.drawPinnedBottom && !drawingCell.shouldDrawTimeOnMedia()) {
                        toY += AndroidUtilities.dp(2);
                    }
                    lastDrawnToX = toX;
                    lastDrawnToY = toY;
                } else {
                    toX = lastDrawnToX;
                    toY = lastDrawnToY;
                }

                if (fragment.getParentActivity() != null && fragment.getFragmentView().getParent() != null && fragment.getFragmentView().getVisibility() == View.VISIBLE && fragment.getFragmentView() != null) {
                    fragment.getFragmentView().getLocationOnScreen(loc);
                    setAlpha(((View) fragment.getFragmentView().getParent()).getAlpha());
                } else {
                    return;
                }
                float previewX = toX - (emojiSize - toH) / 2f;
                float previewY = toY - (emojiSize - toH) / 2f;

                if (animationType != SHORT_ANIMATION) {
                    if (previewX < loc[0]) {
                        previewX = loc[0];
                    }
                    if (previewX + emojiSize > loc[0] + getMeasuredWidth()) {
                        previewX = loc[0] + getMeasuredWidth() - emojiSize;
                    }
                }

                float animateInProgressX, animateInProgressY;
                float animateOutProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(ReactionsEffectOverlay.this.animateOutProgress);
                if (animationType == ONLY_MOVE_ANIMATION) {
                    animateInProgressX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animateOutProgress);
                    animateInProgressY = CubicBezierInterpolator.DEFAULT.getInterpolation(animateOutProgress);
                } else if (fromHolder) {
                    animateInProgressX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animateInProgress);
                    animateInProgressY = CubicBezierInterpolator.DEFAULT.getInterpolation(animateInProgress);
                } else {
                    animateInProgressX = animateInProgressY = animateInProgress;
                }

                float scale = animateInProgressX + (1f - animateInProgressX) * fromScale;

                float toScale = toH / (float) emojiSize;

                float x;
                float y;
                if (animationType == SHORT_ANIMATION) {
                    x = previewX;
                    y = previewY;
                    scale = 1f;
                } else {
                    x = fromX * (1f - animateInProgressX) + previewX * animateInProgressX;
                    y = fromY * (1f - animateInProgressY) + previewY * animateInProgressY;
                }


                effectImageView.setTranslationX(x);
                effectImageView.setTranslationY(y);
                effectImageView.setAlpha((1f - animateOutProgress));
                effectImageView.setScaleX(scale);
                effectImageView.setScaleY(scale);

                if (animationType == ONLY_MOVE_ANIMATION) {
                    scale = fromScale * (1f - animateInProgressX) + toScale * animateInProgressX;
                    x = fromX * (1f - animateInProgressX) + toX * animateInProgressX;
                    y = fromY * (1f - animateInProgressY) + toY * animateInProgressY;
                } else {
                    if (animateOutProgress != 0) {
                        scale = scale * (1f - animateOutProgress) + toScale * animateOutProgress;
                        x = x * (1f - animateOutProgress) + toX * animateOutProgress;
                        y = y * (1f - animateOutProgress) + toY * animateOutProgress;
                    }
                }

                if (animationType != SHORT_ANIMATION) {
                    emojiStaticImageView.setAlpha(animateOutProgress > 0.7f ? (animateOutProgress - 0.7f) / 0.3f : 0);
                }
                //emojiImageView.setAlpha(animateOutProgress < 0.5f ? 1f - (animateOutProgress / 0.5f) : 0f);
                container.setTranslationX(x);
                container.setTranslationY(y);

                container.setScaleX(scale);
                container.setScaleY(scale);

                super.dispatchDraw(canvas);

                if ((animationType == SHORT_ANIMATION || emojiImageView.wasPlaying) && animateInProgress != 1f) {
                    if (fromHolder) {
                        animateInProgress += 16f / 350f;
                    } else {
                        animateInProgress += 16f / 220f;
                    }
                    if (animateInProgress > 1f) {
                        animateInProgress = 1f;
                    }
                }

                if (animationType == ONLY_MOVE_ANIMATION || (wasScrolled && animationType == LONG_ANIMATION) || (animationType != SHORT_ANIMATION && emojiImageView.wasPlaying && emojiImageView.getImageReceiver().getLottieAnimation() != null && !emojiImageView.getImageReceiver().getLottieAnimation().isRunning()) ||
                        (animationType == SHORT_ANIMATION && effectImageView.wasPlaying && effectImageView.getImageReceiver().getLottieAnimation() != null && !effectImageView.getImageReceiver().getLottieAnimation().isRunning())) {
                    if (ReactionsEffectOverlay.this.animateOutProgress != 1f) {
                        if (animationType == SHORT_ANIMATION) {
                            ReactionsEffectOverlay.this.animateOutProgress = 1f;
                        } else {
                            float duration = animationType == ONLY_MOVE_ANIMATION ? 350f : 220f;
                            ReactionsEffectOverlay.this.animateOutProgress += 16f / duration;
                        }
                        if (ReactionsEffectOverlay.this.animateOutProgress > 0.7f && !finished) {
                            startShortAnimation();
                        }
                        if (ReactionsEffectOverlay.this.animateOutProgress >= 1f) {
                            if (animationType == LONG_ANIMATION || animationType == ONLY_MOVE_ANIMATION) {
                                cell.reactionsLayoutInBubble.animateReaction(reaction);
                            }
                            ReactionsEffectOverlay.this.animateOutProgress = 1f;
                            if (animationType == SHORT_ANIMATION) {
                                currentShortOverlay = null;
                            } else {
                                currentOverlay = null;
                            }
                            cell.invalidate();
                            if (cell.getCurrentMessagesGroup() != null && cell.getParent() != null) {
                                ((View) cell.getParent()).invalidate();
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                removeCurrentView();
                            });
                        }
                    }
                }

                invalidate();
            }
        };
        effectImageView = new AnimationView(context);
        emojiImageView = new AnimationView(context);
        emojiStaticImageView = new AnimationView(context);
        TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
        if (availableReaction != null) {

            if (animationType != ONLY_MOVE_ANIMATION) {
                TLRPC.Document document = animationType == SHORT_ANIMATION ? availableReaction.around_animation : availableReaction.effect_animation;
                effectImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + cell.getMessageObject().getId() + "_");
                effectImageView.setImage(ImageLocation.getForDocument(document), sizeForFilter + "_" + sizeForFilter + "_pcache", null, null, 0, null);

                effectImageView.getImageReceiver().setAutoRepeat(0);
                effectImageView.getImageReceiver().setAllowStartAnimation(false);

                if (effectImageView.getImageReceiver().getLottieAnimation() != null) {
                    effectImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    effectImageView.getImageReceiver().getLottieAnimation().start();
                }
            }

            if (animationType == ONLY_MOVE_ANIMATION) {
                TLRPC.Document document = availableReaction.appear_animation;
                emojiImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + cell.getMessageObject().getId() + "_");
                emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter + "_" + emojiSizeForFilter, null, null, 0, null);
            } else if (animationType == LONG_ANIMATION) {
                TLRPC.Document document = availableReaction.activate_animation;
                emojiImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + cell.getMessageObject().getId() + "_");
                emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter + "_" + emojiSizeForFilter, null, null, 0, null);
            }

            emojiImageView.getImageReceiver().setAutoRepeat(0);
            emojiImageView.getImageReceiver().setAllowStartAnimation(false);

            if (emojiImageView.getImageReceiver().getLottieAnimation() != null) {
                if (animationType == ONLY_MOVE_ANIMATION) {
                    emojiImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(emojiImageView.getImageReceiver().getLottieAnimation().getFramesCount() - 1, false);
                } else {
                    emojiImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    emojiImageView.getImageReceiver().getLottieAnimation().start();
                }
            }

            int topOffset = (size - emojiSize) >> 1;
            int leftOffset;
            if (animationType == SHORT_ANIMATION) {
                leftOffset = topOffset;
            } else {
                leftOffset = size - emojiSize;
            }
            container.addView(emojiImageView);
            emojiImageView.getLayoutParams().width = emojiSize;
            emojiImageView.getLayoutParams().height = emojiSize;
            ((FrameLayout.LayoutParams) emojiImageView.getLayoutParams()).topMargin = topOffset;
            ((FrameLayout.LayoutParams) emojiImageView.getLayoutParams()).leftMargin = leftOffset;

            if (animationType != SHORT_ANIMATION) {
                emojiStaticImageView.getImageReceiver().setImage(ImageLocation.getForDocument(availableReaction.static_icon), "40_40", null, "webp", availableReaction, 1);
            }
            container.addView(emojiStaticImageView);
            emojiStaticImageView.getLayoutParams().width = emojiSize;
            emojiStaticImageView.getLayoutParams().height = emojiSize;
            ((FrameLayout.LayoutParams) emojiStaticImageView.getLayoutParams()).topMargin = topOffset;
            ((FrameLayout.LayoutParams) emojiStaticImageView.getLayoutParams()).leftMargin = leftOffset;


            windowView.addView(container);
            container.getLayoutParams().width = size;
            container.getLayoutParams().height = size;
            ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin = -topOffset;
            ((FrameLayout.LayoutParams) container.getLayoutParams()).leftMargin = -leftOffset;

            windowView.addView(effectImageView);
            effectImageView.getLayoutParams().width = size;
            effectImageView.getLayoutParams().height = size;

            effectImageView.getLayoutParams().width = size;
            effectImageView.getLayoutParams().height = size;
            ((FrameLayout.LayoutParams) effectImageView.getLayoutParams()).topMargin = -topOffset;
            ((FrameLayout.LayoutParams) effectImageView.getLayoutParams()).leftMargin = -leftOffset;

            // if (!SHORT_ANIMATION) {
            container.setPivotX(leftOffset);
            container.setPivotY(topOffset);
            //}
        } else {
            dismissed = true;
        }
    }

    private void removeCurrentView() {
        try {
            if (useWindow) {
                windowManager.removeView(windowView);
            } else {
                decorView.removeView(windowView);
            }
        } catch (Exception e) {

        }
    }

    public static void show(BaseFragment baseFragment, ReactionsContainerLayout reactionsLayout, ChatMessageCell cell, float x, float y, String reaction, int currentAccount, int animationType) {
        if (cell == null || reaction == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return;
        }
        boolean animationEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        if (!animationEnabled) {
            return;
        }
        if (animationType == ONLY_MOVE_ANIMATION || animationType == LONG_ANIMATION) {
            show(baseFragment, null, cell, 0, 0, reaction, currentAccount, SHORT_ANIMATION);
        }

        ReactionsEffectOverlay reactionsEffectOverlay = new ReactionsEffectOverlay(baseFragment.getParentActivity(), baseFragment, reactionsLayout, cell, x, y, reaction, currentAccount, animationType);
        if (animationType == SHORT_ANIMATION) {
            currentShortOverlay = reactionsEffectOverlay;
        } else {
            currentOverlay = reactionsEffectOverlay;
        }

        boolean useWindow = false;
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            if (chatActivity.scrimPopupWindow != null && chatActivity.scrimPopupWindow.isShowing()) {
                useWindow = true;
            }
        }

        reactionsEffectOverlay.useWindow = useWindow;
        if (useWindow) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
            lp.format = PixelFormat.TRANSLUCENT;

            reactionsEffectOverlay.windowManager = baseFragment.getParentActivity().getWindowManager();
            reactionsEffectOverlay.windowManager.addView(reactionsEffectOverlay.windowView, lp);
        } else {
            reactionsEffectOverlay.decorView = (FrameLayout) baseFragment.getParentActivity().getWindow().getDecorView();
            reactionsEffectOverlay.decorView.addView(reactionsEffectOverlay.windowView);
        }
        cell.invalidate();
        if (cell.getCurrentMessagesGroup() != null && cell.getParent() != null) {
            ((View) cell.getParent()).invalidate();
        }

    }

    public static void startAnimation() {
        if (currentOverlay != null) {
            currentOverlay.started = true;
        } else {
            startShortAnimation();
            if (currentShortOverlay != null) {
                currentShortOverlay.cell.reactionsLayoutInBubble.animateReaction(currentShortOverlay.reaction);
            }
        }
    }

    public static void startShortAnimation() {
        if (currentShortOverlay != null && !currentShortOverlay.started) {
            currentShortOverlay.started = true;
            if (currentShortOverlay.animationType == SHORT_ANIMATION) {
                currentShortOverlay.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
    }

    public static void removeCurrent(boolean instant) {
        for (int i = 0; i < 2; i++) {
            ReactionsEffectOverlay overlay = i == 0 ? currentOverlay : currentShortOverlay;
            if (overlay != null) {
                if (instant) {
                    overlay.removeCurrentView();
                } else {
                    overlay.dismissed = true;
                }
            }
        }
        currentShortOverlay = null;
        currentOverlay = null;
    }

    public static boolean isPlaying(int messageId, long groupId, String reaction) {
        if (currentOverlay != null && (currentOverlay.animationType == ONLY_MOVE_ANIMATION || currentOverlay.animationType == LONG_ANIMATION)) {
            return ((currentOverlay.groupId != 0 && groupId == currentOverlay.groupId) || messageId == currentOverlay.messageId) && currentOverlay.reaction.equals(reaction);
        }
        return false;
    }


    private class AnimationView extends BackupImageView {

        public AnimationView(Context context) {
            super(context);
        }

        boolean wasPlaying;

        @Override
        protected void onDraw(Canvas canvas) {
            if (getImageReceiver().getLottieAnimation() != null && getImageReceiver().getLottieAnimation().isRunning()) {
                wasPlaying = true;
            }
            if (!wasPlaying && getImageReceiver().getLottieAnimation() != null && !getImageReceiver().getLottieAnimation().isRunning()) {
                if (animationType == ONLY_MOVE_ANIMATION) {
                    getImageReceiver().getLottieAnimation().setCurrentFrame(getImageReceiver().getLottieAnimation().getFramesCount() - 1, false);
                } else {
                    getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    getImageReceiver().getLottieAnimation().start();
                }
            }
            super.onDraw(canvas);
        }
    }

    public static void onScrolled(int dy) {
        if (currentOverlay != null) {
            currentOverlay.lastDrawnToY -= dy;
            if (dy != 0) {
                currentOverlay.wasScrolled = true;
            }
        }
    }
}
