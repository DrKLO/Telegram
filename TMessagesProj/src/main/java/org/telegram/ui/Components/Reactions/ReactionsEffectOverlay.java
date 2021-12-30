package org.telegram.ui.Components.Reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ReactionsContainerLayout;

public class ReactionsEffectOverlay {

    public static ReactionsEffectOverlay currentOverlay;

    private final AnimationView effectImageView;
    private final AnimationView emojiImageView;
    private final FrameLayout container;
    boolean animateIn;
    float animateInProgress;
    float animateOutProgress;

    FrameLayout windowView;
    BackupImageView backupImageView;
    private static int unicPrefix;

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


    private ReactionsEffectOverlay(Context context, BaseFragment fragment, ReactionsContainerLayout reactionsLayout, ChatMessageCell cell, float x, float y, String reaction, int currentAccount) {
        this.messageId = cell.getMessageObject().getId();
        this.groupId = cell.getMessageObject().getGroupId();
        this.reaction = reaction;
        ReactionsLayoutInBubble.ReactionButton reactionButton = cell.getReactionButton(reaction);
        float fromX, fromY, fromHeight, fromWidth;
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
            reactionsLayout.getLocationOnScreen(loc);
            fromX = loc[0] + holderView.getX() + holderView.backupImageView.getX() + AndroidUtilities.dp(16);
            fromY = loc[1] + holderView.getY() + holderView.backupImageView.getY() + AndroidUtilities.dp(16);
            fromHeight = holderView.backupImageView.getWidth();
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

        int size = Math.round(Math.min(AndroidUtilities.dp(350), Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.8f);
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
                                try {
                                    windowManager.removeView(windowView);
                                } catch (Exception e) {

                                }
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
                    }
                }
                ChatMessageCell drawingCell;
                if (fragment instanceof ChatActivity) {
                    drawingCell = ((ChatActivity) fragment).findMessageCell(messageId);
                } else {
                    drawingCell = cell;
                }
                float toX, toY;
                if (drawingCell != null) {
                    cell.getLocationInWindow(loc);

                    ReactionsLayoutInBubble.ReactionButton reactionButton = cell.getReactionButton(reaction);
                    toX = loc[0] + cell.reactionsLayoutInBubble.x;
                    toY = loc[1] + cell.reactionsLayoutInBubble.y;
                    if (reactionButton != null) {
                        toX += reactionButton.x + reactionButton.imageReceiver.getImageX();
                        toY += reactionButton.y + reactionButton.imageReceiver.getImageY();
                    }

                    lastDrawnToX = toX;
                    lastDrawnToY = toY;
                } else {
                    toX = lastDrawnToX;
                    toY = lastDrawnToY;
                }
                float previewX = toX - emojiSize / 2f;
                float previewY = toY - emojiSize / 2f;
                if (fragment.getParentActivity() != null && fragment.getFragmentView().getParent() != null && fragment.getFragmentView().getVisibility() == View.VISIBLE && fragment.getFragmentView() != null) {
                    fragment.getFragmentView().getLocationOnScreen(loc);
                    setAlpha(((View) fragment.getFragmentView().getParent()).getAlpha());
                } else {
                    return;
                }
                if (previewX < loc[0]) {
                    previewX = loc[0];
                }
                if (previewX + emojiSize > loc[0] + getMeasuredWidth()) {
                    previewX = loc[0] + getMeasuredWidth() - emojiSize;
                }

                float animateInProgressX, animateInProgressY;
                if (fromHolder) {
                    animateInProgressX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animateInProgress);
                    animateInProgressY = CubicBezierInterpolator.DEFAULT.getInterpolation(animateInProgress);
                } else {
                    animateInProgressX = animateInProgressY = animateInProgress;
                }

                float scale = animateInProgressX + (1f - animateInProgressX) * fromScale;

                float toScale;
                if (cell.getMessageObject().shouldDrawReactionsInLayout()) {
                    toScale = AndroidUtilities.dp(20) / (float) emojiSize;
                } else {
                    toScale = AndroidUtilities.dp(14) / (float) emojiSize;
                }


                float x = fromX * (1f - animateInProgressX) + previewX * animateInProgressX;
                float y = fromY * (1f - animateInProgressY) + previewY * animateInProgressY;

                effectImageView.setTranslationX(x);
                effectImageView.setTranslationY(y);
                effectImageView.setAlpha((1f - animateOutProgress));

                if (animateOutProgress != 0) {
                    scale = scale * (1f - animateOutProgress) + toScale * animateOutProgress;
                    x = x * (1f - animateOutProgress) + toX * animateOutProgress;
                    y = y * (1f - animateOutProgress) + toY * animateOutProgress;
                }

                container.setTranslationX(x);
                container.setTranslationY(y);

                container.setScaleX(scale);
                container.setScaleY(scale);

                super.dispatchDraw(canvas);

                if (emojiImageView.wasPlaying && animateInProgress != 1f) {
                    if (fromHolder) {
                        animateInProgress += 16f / 350f;
                    } else {
                        animateInProgress += 16f / 220f;
                    }
                    if (animateInProgress > 1f) {
                        animateInProgress = 1f;
                    }
                }

                if (wasScrolled || (emojiImageView.wasPlaying && emojiImageView.getImageReceiver().getLottieAnimation() != null && !emojiImageView.getImageReceiver().getLottieAnimation().isRunning())) {
                    if (animateOutProgress != 1f) {
                        animateOutProgress += 16f / 220f;
                        if (animateOutProgress > 1f) {
                            animateOutProgress = 1f;
                            currentOverlay = null;
                            cell.invalidate();
                            if (cell.getCurrentMessagesGroup() != null && cell.getParent() != null) {
                                ((View) cell.getParent()).invalidate();
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    windowManager.removeView(windowView);
                                } catch (Exception e) {

                                }
                            });
                        }
                    }
                }

                invalidate();
            }
        };
        effectImageView = new AnimationView(context);
        emojiImageView = new AnimationView(context);
        TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
        if (availableReaction != null) {
            TLRPC.Document document = availableReaction.effect_animation;

            effectImageView.getImageReceiver().setUniqKeyPrefix((unicPrefix++) + "_" + cell.getMessageObject().getId() + "_");
            effectImageView.setImage(ImageLocation.getForDocument(document), sizeForFilter + "_" + sizeForFilter + "_pcache", null, null, 0, null);

            effectImageView.getImageReceiver().setAutoRepeat(0);
            effectImageView.getImageReceiver().setAllowStartAnimation(false);

            if (effectImageView.getImageReceiver().getLottieAnimation() != null) {
                effectImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                effectImageView.getImageReceiver().getLottieAnimation().start();
            }

            document = availableReaction.activate_animation;
            emojiImageView.getImageReceiver().setUniqKeyPrefix((unicPrefix++) + "_" + cell.getMessageObject().getId() + "_");
            emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter + "_" + emojiSizeForFilter, null, null, 0, null);

            emojiImageView.getImageReceiver().setAutoRepeat(0);
            emojiImageView.getImageReceiver().setAllowStartAnimation(false);

            if (emojiImageView.getImageReceiver().getLottieAnimation() != null) {
                emojiImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                emojiImageView.getImageReceiver().getLottieAnimation().start();
            }

            int topOffset = (size - emojiSize) >> 1;
            int leftOffset = size - emojiSize;
            container.addView(emojiImageView);
            emojiImageView.getLayoutParams().width = emojiSize;
            emojiImageView.getLayoutParams().height = emojiSize;
            ((FrameLayout.LayoutParams) emojiImageView.getLayoutParams()).topMargin = topOffset;
            ((FrameLayout.LayoutParams) emojiImageView.getLayoutParams()).leftMargin = leftOffset;

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

            container.setPivotX(leftOffset);
            container.setPivotY(topOffset);
        }
    }

    public static void show(BaseFragment baseFragment, ReactionsContainerLayout reactionsLayout, ChatMessageCell cell, float x, float y, String reaction, int currentAccount) {
        if (cell == null) {
            return;
        }
        ReactionsEffectOverlay reactionsEffectOverlay = new ReactionsEffectOverlay(baseFragment.getParentActivity(), baseFragment, reactionsLayout, cell, x, y, reaction, currentAccount);
        currentOverlay = reactionsEffectOverlay;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        lp.format = PixelFormat.TRANSLUCENT;

        reactionsEffectOverlay.windowManager = baseFragment.getParentActivity().getWindowManager();
        reactionsEffectOverlay.windowManager.addView(reactionsEffectOverlay.windowView, lp);
        cell.invalidate();
        if (cell.getCurrentMessagesGroup() != null && cell.getParent() != null) {
            ((View) cell.getParent()).invalidate();
        }
    }

    public static void startAnimation() {
        if (currentOverlay != null) {
            currentOverlay.started = true;
        }
    }

    public static void removeCurrent(boolean instant) {
        if (currentOverlay != null) {
            if (instant) {
                try {
                    currentOverlay.windowManager.removeView(currentOverlay.windowView);
                } catch (Exception e) {

                }
            } else {
                currentOverlay.dismissed = true;
            }
        }
        currentOverlay = null;
    }

    public static boolean isPlaying(int messageId, long groupId, String reaction) {
        if (currentOverlay != null) {
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
                getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                getImageReceiver().getLottieAnimation().start();
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
