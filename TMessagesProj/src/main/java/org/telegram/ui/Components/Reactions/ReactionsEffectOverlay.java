package org.telegram.ui.Components.Reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.Random;

public class ReactionsEffectOverlay {

    public final static int LONG_ANIMATION = 0;
    public final static int SHORT_ANIMATION = 1;
    public final static int ONLY_MOVE_ANIMATION = 2;

    private final int animationType;
    @SuppressLint("StaticFieldLeak")
    public static ReactionsEffectOverlay currentOverlay;
    @SuppressLint("StaticFieldLeak")
    public static ReactionsEffectOverlay currentShortOverlay;

    private final AnimationView effectImageView;
    private final AnimationView emojiImageView;
    private final AnimationView emojiStaticImageView;
    private final FrameLayout container;
    private final int currentAccount;
    private ReactionsEffectOverlay nextReactionOverlay;
    boolean animateIn;
    float animateInProgress;
    float animateOutProgress;

    public FrameLayout windowView;
    BackupImageView backupImageView;
    private static int uniqPrefix;

    int[] loc = new int[2];
    private WindowManager windowManager;
    private boolean dismissed;
    private float dismissProgress;
    private final int messageId;
    private final long groupId;
    private final ReactionsLayoutInBubble.VisibleReaction reaction;
    private float lastDrawnToX;
    private float lastDrawnToY;
    public boolean started;
    private ReactionsContainerLayout.ReactionHolderView holderView = null;
    private SelectAnimatedEmojiDialog.ImageViewEmoji holderView2 = null;
    private boolean wasScrolled;
    private View cell;
    private boolean useWindow;
    private ViewGroup decorView;
    private static long lastHapticTime;
    ArrayList<AvatarParticle> avatars = new ArrayList<>();
    public long startTime;
    public boolean isStories;
    boolean isFinished;

    public ReactionsEffectOverlay(Context context, BaseFragment fragment, ReactionsContainerLayout reactionsLayout, View cell, View fromAnimationView, float x, float y, ReactionsLayoutInBubble.VisibleReaction visibleReaction, int currentAccount, int animationType, boolean isStories) {
        this.isStories = isStories;
        final MessageObject messageObject;
        if (cell instanceof ChatMessageCell) {
            messageObject = ((ChatMessageCell) cell).getMessageObject();
            this.messageId = messageObject.getId();
            this.groupId = messageObject.getGroupId();
        } else if (cell instanceof ChatActionCell) {
            messageObject = ((ChatActionCell) cell).getMessageObject();
            this.messageId = messageObject.getId();
            this.groupId = 0;
        } else {
            messageObject = null;
            this.messageId = 0;
            this.groupId = 0;
        }
        this.reaction = visibleReaction;
        this.animationType = animationType;
        this.currentAccount = currentAccount;
        this.cell = cell;
        ReactionsLayoutInBubble.ReactionButton reactionButton = null;
        if (cell instanceof ChatMessageCell) {
            reactionButton = ((ChatMessageCell) cell).getReactionButton(visibleReaction);
        } else if (cell instanceof ChatActionCell) {
            reactionButton = ((ChatActionCell) cell).getReactionButton(visibleReaction);
        }
        if (isStories && animationType == ONLY_MOVE_ANIMATION) {
            ReactionsEffectOverlay.currentShortOverlay = nextReactionOverlay = new ReactionsEffectOverlay(context, fragment, reactionsLayout, cell, fromAnimationView, x, y, visibleReaction, currentAccount, SHORT_ANIMATION, true);
        }
        float fromX, fromY, fromHeight;
        ChatActivity chatActivity = (fragment instanceof ChatActivity) ? (ChatActivity) fragment : null;
        if (reactionsLayout != null) {
            for (int i = 0; i < reactionsLayout.recyclerListView.getChildCount(); i++) {
                if (reactionsLayout.recyclerListView.getChildAt(i) instanceof ReactionsContainerLayout.ReactionHolderView) {
                    if (((ReactionsContainerLayout.ReactionHolderView) reactionsLayout.recyclerListView.getChildAt(i)).currentReaction.equals(reaction)) {
                        holderView = ((ReactionsContainerLayout.ReactionHolderView) reactionsLayout.recyclerListView.getChildAt(i));
                        break;
                    }
                }
            }
        }

        if (animationType == SHORT_ANIMATION) {
            Random random = new Random();
            ArrayList<TLRPC.MessagePeerReaction> recentReactions = null;
            if (messageObject != null && messageObject.messageOwner.reactions != null) {
                recentReactions = messageObject.messageOwner.reactions.recent_reactions;
            }
            if (recentReactions != null && chatActivity != null && chatActivity.getDialogId() < 0) {
                for (int i = 0; i < recentReactions.size(); i++) {
                    if (reaction.equals(recentReactions.get(i).reaction) && recentReactions.get(i).unread) {
                        TLRPC.User user;
                        TLRPC.Chat chat;

                        AvatarDrawable avatarDrawable = new AvatarDrawable();
                        ImageReceiver imageReceiver = new ImageReceiver();
                        long peerId = MessageObject.getPeerId(recentReactions.get(i).peer_id);
                        if (peerId < 0) {
                            chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                            if (chat == null) {
                                continue;
                            }
                            avatarDrawable.setInfo(currentAccount, chat);
                            imageReceiver.setForUserOrChat(chat, avatarDrawable);
                        } else {
                            user = MessagesController.getInstance(currentAccount).getUser(peerId);
                            if (user == null) {
                                continue;
                            }
                            avatarDrawable.setInfo(currentAccount, user);
                            imageReceiver.setForUserOrChat(user, avatarDrawable);
                        }

                        AvatarParticle avatarParticle = new AvatarParticle();
                        avatarParticle.imageReceiver = imageReceiver;
                        avatarParticle.fromX = 0.5f;// + Math.abs(random.nextInt() % 100) / 100f * 0.2f;
                        avatarParticle.fromY = 0.5f;// + Math.abs(random.nextInt() % 100) / 100f * 0.2f;
                        avatarParticle.jumpY = 0.3f + Math.abs(random.nextInt() % 100) / 100f * 0.1f;
                        avatarParticle.randomScale = 0.8f + Math.abs(random.nextInt() % 100) / 100f * 0.4f;
                        avatarParticle.randomRotation = 60 * Math.abs(random.nextInt() % 100) / 100f;
                        avatarParticle.leftTime = (int) (400 + Math.abs(random.nextInt() % 100) / 100f * 200);

                        if (avatars.isEmpty()) {
                            avatarParticle.toX = 0.2f + 0.6f * Math.abs(random.nextInt() % 100) / 100f;
                            avatarParticle.toY = 0.4f * Math.abs(random.nextInt() % 100) / 100f;
                        } else {
                            float bestDistance = 0;
                            float bestX = 0;
                            float bestY = 0;
                            for (int k = 0; k < 10; k++) {
                                float randX = 0.2f + 0.6f * Math.abs(random.nextInt() % 100) / 100f;
                                float randY = 0.2f + 0.4f * Math.abs(random.nextInt() % 100) / 100f;
                                float minDistance = Integer.MAX_VALUE;
                                for (int j = 0; j < avatars.size(); j++) {
                                    float rx = avatars.get(j).toX - randX;
                                    float ry = avatars.get(j).toY - randY;
                                    float distance = rx * rx + ry * ry;
                                    if (distance < minDistance) {
                                        minDistance = distance;
                                    }
                                }
                                if (minDistance > bestDistance) {
                                    bestDistance = minDistance;
                                    bestX = randX;
                                    bestY = randY;
                                }
                            }
                            avatarParticle.toX = bestX;
                            avatarParticle.toY = bestY;
                        }

                        avatars.add(avatarParticle);
                    }
                }
            }
        }

        boolean fromHolder = holderView != null || (x != 0 && y != 0);
        if (fromAnimationView != null) {
            fromAnimationView.getLocationOnScreen(loc);
            float viewX = loc[0];
            float viewY = loc[1];
            float viewSize = fromAnimationView.getWidth() * fromAnimationView.getScaleX();

            if (fromAnimationView instanceof SelectAnimatedEmojiDialog.ImageViewEmoji) {
                SelectAnimatedEmojiDialog.ImageViewEmoji imageViewEmoji = (SelectAnimatedEmojiDialog.ImageViewEmoji) fromAnimationView;
                if (imageViewEmoji.bigReactionSelectedProgress > 0) {
                    float scale = 1 + 2 * imageViewEmoji.bigReactionSelectedProgress;
                    viewSize = fromAnimationView.getWidth() * scale;
                    viewX -= (viewSize - fromAnimationView.getWidth()) / 2f;
                    viewY -= (viewSize - fromAnimationView.getWidth());
                }
            }
            fromX = viewX;
            fromY = viewY;
            fromHeight = viewSize;
        } else if (holderView != null) {
            holderView.getLocationOnScreen(loc);
            fromX = loc[0] + holderView.loopImageView.getX();
            fromY = loc[1] + holderView.loopImageView.getY();
            fromHeight = holderView.loopImageView.getWidth() * holderView.getScaleX();
        } else if (reactionButton != null) {
            cell.getLocationInWindow(loc);
            fromX = loc[0] + (reactionButton.imageReceiver == null ? 0 : reactionButton.imageReceiver.getImageX());
            fromY = loc[1] + (reactionButton.imageReceiver == null ? 0 : reactionButton.imageReceiver.getImageY());
            fromHeight = reactionButton.imageReceiver == null ? 0 : reactionButton.imageReceiver.getImageHeight();
        } else if (cell != null) {
            ((View) cell.getParent()).getLocationInWindow(loc);
            fromX = loc[0] + x;
            fromY = loc[1] + y + (cell instanceof ChatMessageCell ? ((ChatMessageCell) cell).starsPriceTopPadding : 0);
            fromHeight = 0;
        } else {
            fromX = x;
            fromY = y;
            fromHeight = 0;
        }

        int size;
        int sizeForFilter;
        if (animationType == ONLY_MOVE_ANIMATION) {
            size = (isStories && SharedConfig.deviceIsHigh()) ? AndroidUtilities.dp(60) : AndroidUtilities.dp(34);
            sizeForFilter = (int) (2f * size / AndroidUtilities.density);
        } else if (animationType == SHORT_ANIMATION) {
            if (isStories) {
                size = SharedConfig.deviceIsHigh() ? AndroidUtilities.dp(240) : AndroidUtilities.dp(140);
                sizeForFilter = SharedConfig.deviceIsHigh() ? (int) (2f * AndroidUtilities.dp(80) / AndroidUtilities.density) : sizeForAroundReaction();
            } else {
                size = AndroidUtilities.dp(80);
                sizeForFilter = sizeForAroundReaction();
            }

        } else {
            size = Math.round(Math.min(AndroidUtilities.dp(350), Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.8f);
            sizeForFilter = sizeForBigReaction();
        }

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
                        holderView.enterImageView.setAlpha(0);
                        holderView.pressedBackupImageView.setAlpha(0);
                    }
                }
                View drawingCell;
                if (fragment instanceof ChatActivity) {
                    drawingCell = ((ChatActivity) fragment).findCell(messageId, false);
                } else {
                    drawingCell = cell;
                }
                float toX, toY, toH;

                if (isStories) {
                    toH = SharedConfig.deviceIsHigh() ? AndroidUtilities.dp(120) : AndroidUtilities.dp(50);
                } else if (messageObject != null && messageObject.shouldDrawReactionsInLayout()) {
                    toH = AndroidUtilities.dp(20);
                } else {
                    toH = AndroidUtilities.dp(14);
                }
                if (drawingCell != null) {
                    drawingCell.getLocationInWindow(loc);

                    toX = loc[0];
                    toY = loc[1];
                    ReactionsLayoutInBubble.ReactionButton reactionButton = null;
                    if (drawingCell instanceof ChatMessageCell) {
                        ChatMessageCell messageCell = (ChatMessageCell) drawingCell;
                        reactionButton = messageCell.getReactionButton(reaction);
                        if (messageCell.drawPinnedBottom && !messageCell.shouldDrawTimeOnMedia()) {
                            toY += AndroidUtilities.dp(2);
                        }
                    } else if (drawingCell instanceof ChatActionCell) {
                        reactionButton = ((ChatActionCell) drawingCell).getReactionButton(reaction);
                    }
                    if (reactionButton != null) {
                        toX += reactionButton.drawingImageRect.left;
                        toY += reactionButton.drawingImageRect.top;
                    }
                    if (chatActivity != null) {
                        toY += chatActivity.drawingChatListViewYoffset;
                    }
                    lastDrawnToX = toX;
                    lastDrawnToY = toY;
                } else if (isStories) {
                    toX = getMeasuredWidth() / 2f - toH / 2f;
                    toY = getMeasuredHeight() / 2f - toH / 2f;
                } else {
                    toX = lastDrawnToX;
                    toY = lastDrawnToY;
                }

                if (fragment != null && fragment.getParentActivity() != null && fragment.getFragmentView() != null && fragment.getFragmentView().getParent() != null && fragment.getFragmentView().getVisibility() == View.VISIBLE && fragment.getFragmentView() != null) {
                    fragment.getFragmentView().getLocationOnScreen(loc);
                    setAlpha(((View) fragment.getFragmentView().getParent()).getAlpha());
                } else if (!isStories){
                    return;
                }
                float previewX = toX - (emojiSize - toH) / 2f;
                float previewY = toY - (emojiSize - toH) / 2f;
                if (isStories && animationType == LONG_ANIMATION) {
                    previewX += AndroidUtilities.dp(40);
                }

                if (animationType != SHORT_ANIMATION && !isStories) {
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
                    if (!isStories) {
                        emojiStaticImageView.setAlpha(animateOutProgress > 0.7f ? (animateOutProgress - 0.7f) / 0.3f : 0);
                    } else {
                        emojiStaticImageView.setAlpha(1f);
                    }
                }
                if (animationType == LONG_ANIMATION && isStories) {
                    emojiImageView.setAlpha(1f - animateOutProgress);
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

                if (animationType == ONLY_MOVE_ANIMATION || (wasScrolled && animationType == LONG_ANIMATION) || (animationType != SHORT_ANIMATION && (emojiImageView.wasPlaying && emojiImageView.getImageReceiver().getLottieAnimation() != null && !emojiImageView.getImageReceiver().getLottieAnimation().isRunning()) || (visibleReaction.documentId != 0 && System.currentTimeMillis() - startTime > 2000)) ||
                        (animationType == SHORT_ANIMATION && (effectImageView.wasPlaying && (effectImageView.getImageReceiver().getLottieAnimation() != null && !effectImageView.getImageReceiver().getLottieAnimation().isRunning())) || (visibleReaction.documentId != 0 && System.currentTimeMillis() - startTime > 2000))) {
                    if (ReactionsEffectOverlay.this.animateOutProgress != 1f) {
                        if (animationType == SHORT_ANIMATION) {
                            ReactionsEffectOverlay.this.animateOutProgress = 1f;
                        } else {
                            float duration = animationType == ONLY_MOVE_ANIMATION ? 350f : 220f;
                            ReactionsEffectOverlay.this.animateOutProgress += 16f / duration;
                        }
                        if (ReactionsEffectOverlay.this.animateOutProgress > 0.7f) {
                            if (isStories && animationType == ONLY_MOVE_ANIMATION) {
                                if (!isFinished) {
                                    isFinished = true;
                                    try {
                                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                    } catch (Exception ignored) {}

                                    ViewGroup viewGroup = (ViewGroup) getParent();
                                    viewGroup.addView(nextReactionOverlay.windowView);
                                    nextReactionOverlay.isStories = true;
                                    nextReactionOverlay.started = true;
                                    nextReactionOverlay.startTime = System.currentTimeMillis();
                                    nextReactionOverlay.windowView.setTag(R.id.parent_tag, 1);
                                    animate().scaleX(0).scaleY(0).setStartDelay(1000).setDuration(150).setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            removeCurrentView();
                                        }
                                    });
                                }
                            } else {
                                startShortAnimation();
                            }
                        }
                        if (ReactionsEffectOverlay.this.animateOutProgress >= 1f) {
                            if (animationType == LONG_ANIMATION || animationType == ONLY_MOVE_ANIMATION) {
                                if (cell instanceof ChatMessageCell) {
                                    ((ChatMessageCell) cell).reactionsLayoutInBubble.animateReaction(reaction);
                                } else if (cell instanceof ChatActionCell) {
                                    ((ChatActionCell) cell).reactionsLayoutInBubble.animateReaction(reaction);
                                }
                            }
                            ReactionsEffectOverlay.this.animateOutProgress = 1f;
                            if (animationType == SHORT_ANIMATION) {
                                currentShortOverlay = null;
                            } else {
                                currentOverlay = null;
                            }
                            if (cell != null) {
                                cell.invalidate();
                                if (cell instanceof ChatMessageCell && ((ChatMessageCell) cell).getCurrentMessagesGroup() != null && cell.getParent() != null) {
                                    ((View) cell.getParent()).invalidate();
                                }
                            }
                            if (isStories && animationType == ONLY_MOVE_ANIMATION) {

                            } else {
                                AndroidUtilities.runOnUIThread(() -> {
                                    removeCurrentView();
                                });
                            }
                        }
                    }
                }


                if (!avatars.isEmpty() && effectImageView.wasPlaying) {
                    RLottieDrawable animation = effectImageView.getImageReceiver().getLottieAnimation();

                    for (int i = 0; i < avatars.size(); i++) {
                        AvatarParticle particle = avatars.get(i);
                        float progress = particle.progress;
                        boolean isLeft;
                        if (animation != null && animation.isRunning()) {
                            long duration = effectImageView.getImageReceiver().getLottieAnimation().getDuration();
                            int totalFramesCount = effectImageView.getImageReceiver().getLottieAnimation().getFramesCount();
                            int currentFrame = effectImageView.getImageReceiver().getLottieAnimation().getCurrentFrame();
                            int timeLeft = (int) (duration - duration * (currentFrame / (float) totalFramesCount));
                            isLeft = timeLeft < particle.leftTime;
                        } else {
                            isLeft = true;
                        }

                        if (isLeft && particle.outProgress != 1f) {
                            particle.outProgress += 16f / 150f;
                            if (particle.outProgress > 1f) {
                                particle.outProgress = 1f;
                                avatars.remove(i);
                                i--;
                                continue;
                            }
                        }
                        float jumpProgress = progress < 0.5f ? (progress / 0.5f) : (1f - ((progress - 0.5f) / 0.5f));
                        float avatarX = particle.fromX * (1f - progress) + particle.toX * progress;
                        float avatarY = particle.fromY * (1f - progress) + particle.toY * progress - particle.jumpY * jumpProgress;

                        float s = progress * particle.randomScale * (1f - particle.outProgress);
                        float cx = effectImageView.getX() + (effectImageView.getWidth() * effectImageView.getScaleX()) * avatarX;
                        float cy = effectImageView.getY() + (effectImageView.getHeight() * effectImageView.getScaleY()) * avatarY;
                        int size = AndroidUtilities.dp(16);
                        avatars.get(i).imageReceiver.setImageCoords(cx - size / 2f, cy - size / 2f, size, size);
                        avatars.get(i).imageReceiver.setRoundRadius(size >> 1);
                        canvas.save();
                        canvas.translate(0, particle.globalTranslationY);
                        canvas.scale(s, s, cx, cy);
                        canvas.rotate(particle.currentRotation, cx, cy);

                        avatars.get(i).imageReceiver.draw(canvas);
                        canvas.restore();

                        if (particle.progress < 1f) {
                            particle.progress += 16f / 350f;
                            if (particle.progress > 1f) {
                                particle.progress = 1f;
                            }
                        }
                        if (progress >= 1f) {
                            particle.globalTranslationY += AndroidUtilities.dp(20) * 16f / 500f;
                        }

                        if (particle.incrementRotation) {
                            particle.currentRotation += particle.randomRotation / 250f;
                            if (particle.currentRotation > particle.randomRotation) {
                                particle.incrementRotation = false;
                            }
                        } else {
                            particle.currentRotation -= particle.randomRotation / 250f;
                            if (particle.currentRotation < -particle.randomRotation) {
                                particle.incrementRotation = true;
                            }
                        }
                    }
                }

                invalidate();
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                for (int i = 0; i < avatars.size(); i++) {
                    avatars.get(i).imageReceiver.onAttachedToWindow();
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                for (int i = 0; i < avatars.size(); i++) {
                    avatars.get(i).imageReceiver.onDetachedFromWindow();
                }
            }
        };
        effectImageView = new AnimationView(context);
        emojiImageView = new AnimationView(context);
        emojiStaticImageView = new AnimationView(context);
        TLRPC.TL_availableReaction availableReaction = null;
        if (visibleReaction.emojicon != null) {
            availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction.emojicon);
        }
        if (availableReaction != null || visibleReaction.documentId != 0) {
            if (availableReaction != null) {
                if (animationType != ONLY_MOVE_ANIMATION) {
                    if ((animationType == SHORT_ANIMATION && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_CHAT)) || animationType == LONG_ANIMATION)  {
                        TLRPC.Document document = animationType == SHORT_ANIMATION ? availableReaction.around_animation : availableReaction.effect_animation;
                        String filer = animationType == SHORT_ANIMATION ? getFilterForAroundAnimation() : sizeForFilter + "_" + sizeForFilter;
                        effectImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + messageId + "_");
                        effectImageView.setImage(ImageLocation.getForDocument(document), filer, null, null, 0, null);

                        effectImageView.getImageReceiver().setAutoRepeat(0);
                        effectImageView.getImageReceiver().setAllowStartAnimation(false);
                    }

                    if (effectImageView.getImageReceiver().getLottieAnimation() != null) {
                        effectImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                        effectImageView.getImageReceiver().getLottieAnimation().start();
                    }
                }

                if (animationType == ONLY_MOVE_ANIMATION) {
                    TLRPC.Document document = isStories ? availableReaction.select_animation : availableReaction.appear_animation;
                    emojiImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + messageId + "_");
                    emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter + "_" + emojiSizeForFilter, null, null, 0, null);
                } else if (animationType == LONG_ANIMATION) {
                    TLRPC.Document document = availableReaction.activate_animation;
                    emojiImageView.getImageReceiver().setUniqKeyPrefix((uniqPrefix++) + "_" + messageId + "_");
                    emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter + "_" + emojiSizeForFilter, null, null, 0, null);
                }
            } else {
                if (animationType == LONG_ANIMATION) {
                    emojiImageView.setAnimatedReactionDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE, currentAccount, visibleReaction.documentId));
                } else if (animationType == ONLY_MOVE_ANIMATION) {
                    emojiImageView.setAnimatedReactionDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, currentAccount, visibleReaction.documentId));
                }
                if (animationType == LONG_ANIMATION || animationType == SHORT_ANIMATION) {
                    AnimatedEmojiDrawable animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, currentAccount, visibleReaction.documentId);
                    int color;
                    if (messageObject != null) {
                        color = Theme.getColor(
                            messageObject.shouldDrawWithoutBackground() ?
                                messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground :
                                messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonTextSelected : Theme.key_chat_inReactionButtonTextSelected,
                            fragment != null ? fragment.getResourceProvider() : null
                        );
                    } else {
                        color = Color.WHITE;
                    }
                    animatedEmojiDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                    boolean longAnimation = animationType == LONG_ANIMATION;
                    effectImageView.setAnimatedEmojiEffect(AnimatedEmojiEffect.createFrom(animatedEmojiDrawable, longAnimation, !longAnimation));
                    windowView.setClipChildren(false);
                }
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

            if (animationType != SHORT_ANIMATION && !isStories) {
                if (availableReaction != null) {
                    emojiStaticImageView.getImageReceiver().setImage(ImageLocation.getForDocument(availableReaction.center_icon), "40_40_lastreactframe", null, "webp", availableReaction, 1);
                }
                container.addView(emojiStaticImageView);
                emojiStaticImageView.getLayoutParams().width = emojiSize;
                emojiStaticImageView.getLayoutParams().height = emojiSize;
                ((FrameLayout.LayoutParams) emojiStaticImageView.getLayoutParams()).topMargin = topOffset;
                ((FrameLayout.LayoutParams) emojiStaticImageView.getLayoutParams()).leftMargin = leftOffset;
            }

            windowView.addView(container);
            container.getLayoutParams().width = size;
            container.getLayoutParams().height = size;
            ((FrameLayout.LayoutParams) container.getLayoutParams()).topMargin = -topOffset;
            ((FrameLayout.LayoutParams) container.getLayoutParams()).leftMargin = -leftOffset;

            //if (availableReaction != null) {
                windowView.addView(effectImageView);
                effectImageView.getLayoutParams().width = size;
                effectImageView.getLayoutParams().height = size;

                effectImageView.getLayoutParams().width = size;
                effectImageView.getLayoutParams().height = size;
                ((FrameLayout.LayoutParams) effectImageView.getLayoutParams()).topMargin = -topOffset;
                ((FrameLayout.LayoutParams) effectImageView.getLayoutParams()).leftMargin = -leftOffset;
           // }

            container.setPivotX(leftOffset);
            container.setPivotY(topOffset);
        } else {
            dismissed = true;
        }
    }

    public static String getFilterForAroundAnimation() {
        return sizeForAroundReaction() + "_" + sizeForAroundReaction() + "_nolimit_pcache";
    }

    private void removeCurrentView() {
        try {
            if (useWindow) {
                windowManager.removeView(windowView);
            } else {
                AndroidUtilities.removeFromParent(windowView);
            }
        } catch (Exception e) {

        }
    }

    public static void show(BaseFragment baseFragment, ReactionsContainerLayout reactionsLayout, View cell, View fromAnimationView, float x, float y, ReactionsLayoutInBubble.VisibleReaction visibleReaction, int currentAccount, int animationType) {
        if (cell == null || visibleReaction == null || baseFragment == null || baseFragment.getParentActivity() == null) {
            return;
        }
        boolean animationEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        if (!animationEnabled) {
            return;
        }
        if (animationType == ONLY_MOVE_ANIMATION || animationType == LONG_ANIMATION) {
            show(baseFragment, null, cell, fromAnimationView, 0, 0, visibleReaction, currentAccount, SHORT_ANIMATION);
        }

        ReactionsEffectOverlay reactionsEffectOverlay = new ReactionsEffectOverlay(baseFragment.getParentActivity(), baseFragment, reactionsLayout, cell, fromAnimationView, x, y, visibleReaction,  currentAccount, animationType, false);
        if (animationType == SHORT_ANIMATION) {
            currentShortOverlay = reactionsEffectOverlay;
        } else {
            currentOverlay = reactionsEffectOverlay;
        }

        boolean useWindow = false;
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            if ((animationType == LONG_ANIMATION || animationType == ONLY_MOVE_ANIMATION) && chatActivity.scrimPopupWindow != null && chatActivity.scrimPopupWindow.isShowing()) {
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
            AndroidUtilities.setPreferredMaxRefreshRate(reactionsEffectOverlay.windowManager, reactionsEffectOverlay.windowView, lp);
            reactionsEffectOverlay.windowManager.addView(reactionsEffectOverlay.windowView, lp);
        } else {
            reactionsEffectOverlay.decorView = (FrameLayout) baseFragment.getParentActivity().getWindow().getDecorView();
            reactionsEffectOverlay.decorView.addView(reactionsEffectOverlay.windowView);
        }
        cell.invalidate();
        if (cell instanceof ChatMessageCell && ((ChatMessageCell) cell).getCurrentMessagesGroup() != null && cell.getParent() != null) {
            ((View) cell.getParent()).invalidate();
        }

    }

    public static void startAnimation() {
        if (currentOverlay != null) {
            currentOverlay.started = true;
            currentOverlay.startTime = System.currentTimeMillis();
            if (currentOverlay.animationType == LONG_ANIMATION && System.currentTimeMillis() - lastHapticTime > 200) {
                lastHapticTime = System.currentTimeMillis();
                currentOverlay.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        } else {
            startShortAnimation();
            if (currentShortOverlay != null) {
                if (currentShortOverlay.cell instanceof ChatMessageCell) {
                    ((ChatMessageCell) currentShortOverlay.cell).reactionsLayoutInBubble.animateReaction(currentShortOverlay.reaction);
                } else if (currentShortOverlay.cell instanceof ChatActionCell) {
                    ((ChatActionCell) currentShortOverlay.cell).reactionsLayoutInBubble.animateReaction(currentShortOverlay.reaction);
                }
            }
        }
    }

    public static void startShortAnimation() {
        if (currentShortOverlay != null && !currentShortOverlay.started) {
            currentShortOverlay.started = true;
            currentShortOverlay.startTime = System.currentTimeMillis();
            if (currentShortOverlay.animationType == SHORT_ANIMATION && System.currentTimeMillis() - lastHapticTime > 200) {
                lastHapticTime = System.currentTimeMillis();
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

    public static boolean isPlaying(int messageId, long groupId, ReactionsLayoutInBubble.VisibleReaction reaction) {
        if (currentOverlay != null && (currentOverlay.animationType == ONLY_MOVE_ANIMATION || currentOverlay.animationType == LONG_ANIMATION)) {
            return ((currentOverlay.groupId != 0 && groupId == currentOverlay.groupId) || messageId == currentOverlay.messageId) && currentOverlay.reaction.equals(reaction);
        }
        return false;
    }


    private class AnimationView extends BackupImageView {

        public AnimationView(Context context) {
            super(context);
            getImageReceiver().setFileLoadingPriority(FileLoader.PRIORITY_HIGH);
        }

        boolean wasPlaying;
        AnimatedEmojiDrawable animatedEmojiDrawable;
        AnimatedEmojiEffect emojiEffect;
        boolean attached;

        @Override
        protected void onDraw(Canvas canvas) {
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                animatedEmojiDrawable.setAlpha(255);
                animatedEmojiDrawable.draw(canvas);
                wasPlaying = true;
                return;
            }
            if (emojiEffect != null) {
                emojiEffect.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                emojiEffect.draw(canvas);
                wasPlaying = true;
                return;
            }
            if (getImageReceiver().getLottieAnimation() != null && getImageReceiver().getLottieAnimation().isRunning()) {
                wasPlaying = true;
            }
            if (!wasPlaying && getImageReceiver().getLottieAnimation() != null && !getImageReceiver().getLottieAnimation().isRunning()) {
                if (animationType == ONLY_MOVE_ANIMATION && !isStories) {
                    getImageReceiver().getLottieAnimation().setCurrentFrame(getImageReceiver().getLottieAnimation().getFramesCount() - 1, false);
                } else {
                    getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                    getImageReceiver().getLottieAnimation().start();
                }
            }
            super.onDraw(canvas);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(this);
            }
            if (emojiEffect != null) {
                emojiEffect.setView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(this);
            }
            if (emojiEffect != null) {
                emojiEffect.removeView(this);
            }
        }

        public void setAnimatedReactionDrawable(AnimatedEmojiDrawable animatedEmojiDrawable) {
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(this);
            }
            this.animatedEmojiDrawable = animatedEmojiDrawable;
            if (attached && animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(this);
            }
        }

        public void setAnimatedEmojiEffect(AnimatedEmojiEffect effect) {
            emojiEffect = effect;
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

    public static int sizeForBigReaction() {
        return (int) (Math.round(Math.min(AndroidUtilities.dp(350), Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.7f) / AndroidUtilities.density);
    }

    public static int sizeForAroundReaction() {
        int size = AndroidUtilities.dp(40);
        return (int) (2f * size / AndroidUtilities.density);
    }

    public static void dismissAll() {
        if (currentOverlay != null) {
            currentOverlay.dismissed = true;
        }
        if (currentShortOverlay != null) {
            currentShortOverlay.dismissed = true;
        }
    }

    private class AvatarParticle {
        ImageReceiver imageReceiver;

        public int leftTime;
        float progress;
        float outProgress;
        float jumpY;
        float fromX;
        float fromY;
        float toX;
        float toY;
        float randomScale;
        float randomRotation;
        float currentRotation;
        boolean incrementRotation;
        float globalTranslationY;
    }
}
