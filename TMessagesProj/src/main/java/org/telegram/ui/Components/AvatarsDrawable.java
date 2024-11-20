package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Stories.StoriesGradientTools;

import java.util.Random;

public class AvatarsDrawable {

    public final static int STYLE_GROUP_CALL_TOOLTIP = 10;
    public final static int STYLE_MESSAGE_SEEN = 11;
    private boolean showSavedMessages;

    DrawingState[] currentStates = new DrawingState[3];
    DrawingState[] animatingStates = new DrawingState[3];
    boolean wasDraw;

    float transitionProgress = 1f;
    ValueAnimator transitionProgressAnimator;
    boolean updateAfterTransition;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint xRefP = new Paint(Paint.ANTI_ALIAS_FLAG);

    Runnable updateDelegate;
    int currentStyle;
    boolean centered;

    private boolean isInCall;
    public int count;
    public int height;
    public int width;
    public int strokeWidth = AndroidUtilities.dp(1.67f);

    View parent;
    private int overrideSize;
    private float overrideSizeStepFactor = 0.8f;
    private float overrideAlpha = 1f;
    public long transitionDuration = 220;
    public Interpolator transitionInterpolator = CubicBezierInterpolator.DEFAULT;
    private boolean transitionInProgress;
    public boolean drawStoriesCircle;
    StoriesGradientTools storiesTools;

    public void commitTransition(boolean animated) {
        commitTransition(animated, true);
    }

    public void setTransitionProgress(float transitionProgress) {
        if (transitionInProgress) {
            if (this.transitionProgress != transitionProgress) {
                this.transitionProgress = transitionProgress;
                if (transitionProgress == 1f) {
                    swapStates();
                    transitionInProgress = false;
                }
            }
        }
    }

    public void commitTransition(boolean animated, boolean createAnimator) {
        if (!wasDraw || !animated) {
            transitionProgress = 1f;
            swapStates();
            return;
        }

        DrawingState[] removedStates = new DrawingState[3];
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            removedStates[i] = currentStates[i];
            if (currentStates[i].id != animatingStates[i].id) {
                changed = true;
            } else {
                currentStates[i].lastSpeakTime = animatingStates[i].lastSpeakTime;
            }
        }
        if (!changed) {
            transitionProgress = 1f;
            return;
        }
        for (int i = 0; i < 3; i++) {
            boolean found = false;
            for (int j = 0; j < 3; j++) {
                if (currentStates[j].id == animatingStates[i].id) {
                    found = true;
                    removedStates[j] = null;
                    if (i == j) {
                        animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_NONE;
                        GroupCallUserCell.AvatarWavesDrawable wavesDrawable = animatingStates[i].wavesDrawable;
                        animatingStates[i].wavesDrawable = currentStates[i].wavesDrawable;
                        currentStates[i].wavesDrawable = wavesDrawable;
                    } else {
                        animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_MOVE;
                        animatingStates[i].moveFromIndex = j;
                    }
                    break;
                }
            }
            if (!found) {
                animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_IN;
            }
        }

        for (int i = 0; i < 3; i++) {
            if (removedStates[i] != null) {
                removedStates[i].animationType = DrawingState.ANIMATION_TYPE_OUT;
            }
        }
        if (transitionProgressAnimator != null) {
            transitionProgressAnimator.removeAllListeners();
            transitionProgressAnimator.cancel();
            if (transitionInProgress) {
                swapStates();
                transitionInProgress = false;
            }
        }
        transitionProgress = 0;
        if (createAnimator) {
            transitionProgressAnimator = ValueAnimator.ofFloat(0, 1f);
            transitionProgressAnimator.addUpdateListener(valueAnimator -> {
                transitionProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            transitionProgressAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (transitionProgressAnimator != null) {
                        transitionProgress = 1f;
                        swapStates();
                        if (updateAfterTransition) {
                            updateAfterTransition = false;
                            if (updateDelegate != null) {
                                updateDelegate.run();
                            }
                        }
                        invalidate();
                    }
                    transitionProgressAnimator = null;
                }
            });
            transitionProgressAnimator.setDuration(transitionDuration);
            transitionProgressAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            transitionProgressAnimator.start();
        } else {
            transitionInProgress = true;
        }
        invalidate();
    }

    private void swapStates() {
        for (int i = 0; i < 3; i++) {
            DrawingState state = currentStates[i];
            currentStates[i] = animatingStates[i];
            animatingStates[i] = state;
        }
    }

    public void updateAfterTransitionEnd() {
        updateAfterTransition = true;
    }

    public void setDelegate(Runnable delegate) {
        updateDelegate = delegate;
    }

    public void setStyle(int currentStyle) {
        this.currentStyle = currentStyle;
        invalidate();
    }

    private void invalidate() {
        if (parent != null) {
            parent.invalidate();
        }
    }

    public void setSize(int size) {
        overrideSize = size;
    }

    public void setStepFactor(float factor) {
        overrideSizeStepFactor = factor;
    }

    public void animateFromState(AvatarsDrawable avatarsDrawable, int currentAccount, boolean createAnimator) {
        if (avatarsDrawable == null) {
            return;
        }
        if (avatarsDrawable.transitionProgressAnimator != null) {
            avatarsDrawable.transitionProgressAnimator.cancel();
            if (transitionInProgress) {
                transitionInProgress = false;
                swapStates();
            }
        }
        TLObject[] objects = new TLObject[3];
        for (int i = 0; i < 3; i++) {
            objects[i] = currentStates[i].object;
            setObject(i, currentAccount, avatarsDrawable.currentStates[i].object);
        }
        commitTransition(false);
        for (int i = 0; i < 3; i++) {
            setObject(i, currentAccount, objects[i]);
        }
        wasDraw = true;
        commitTransition(true, createAnimator);
    }

    public void setAlpha(float alpha) {
        overrideAlpha = alpha;
    }

    private static class DrawingState {

        public static final int ANIMATION_TYPE_NONE = -1;
        public static final int ANIMATION_TYPE_IN = 0;
        public static final int ANIMATION_TYPE_OUT = 1;
        public static final int ANIMATION_TYPE_MOVE = 2;

        private AvatarDrawable avatarDrawable;
        private GroupCallUserCell.AvatarWavesDrawable wavesDrawable;
        private long lastUpdateTime;
        private long lastSpeakTime;
        private ImageReceiver imageReceiver;
        TLRPC.TL_groupCallParticipant participant;

        private long id;
        private TLObject object;

        private int animationType;
        private int moveFromIndex;
    }

    Random random = new Random();

    public AvatarsDrawable(View parent, boolean inCall) {
        this.parent = parent;
        for (int a = 0; a < 3; a++) {
            currentStates[a] = new DrawingState();
            currentStates[a].imageReceiver = new ImageReceiver(parent);
            currentStates[a].imageReceiver.setInvalidateAll(true);
            currentStates[a].imageReceiver.setRoundRadius(AndroidUtilities.dp(12));
            currentStates[a].avatarDrawable = new AvatarDrawable();
            currentStates[a].avatarDrawable.setTextSize(AndroidUtilities.dp(12));

            animatingStates[a] = new DrawingState();
            animatingStates[a].imageReceiver = new ImageReceiver(parent);
            animatingStates[a].imageReceiver.setInvalidateAll(true);
            animatingStates[a].imageReceiver.setRoundRadius(AndroidUtilities.dp(12));
            animatingStates[a].avatarDrawable = new AvatarDrawable();
            animatingStates[a].avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        }
        isInCall = inCall;
        xRefP.setColor(0);
        xRefP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setAvatarsTextSize(int newTextSize) {
        for (int a = 0; a < 3; a++) {
            if (currentStates[a] != null && currentStates[a].avatarDrawable != null) {
                currentStates[a].avatarDrawable.setTextSize(newTextSize);
            }
            if (animatingStates[a] != null && animatingStates[a].avatarDrawable != null) {
                animatingStates[a].avatarDrawable.setTextSize(newTextSize);
            }
        }
    }

    public void setObject(int index, int account, TLObject object) {
        animatingStates[index].id = 0;
        animatingStates[index].participant = null;
        if (object == null) {
            animatingStates[index].imageReceiver.setImageBitmap((Drawable) null);
            invalidate();
            return;
        }
        TLRPC.User currentUser = null;
        TLRPC.Chat currentChat = null;
        animatingStates[index].lastSpeakTime = -1;
        animatingStates[index].object = object;
        if (object instanceof TLRPC.TL_groupCallParticipant) {
            TLRPC.TL_groupCallParticipant participant = (TLRPC.TL_groupCallParticipant) object;
            animatingStates[index].participant = participant;
            long id = MessageObject.getPeerId(participant.peer);
            if (DialogObject.isUserDialog(id)) {
                currentUser = MessagesController.getInstance(account).getUser(id);
                animatingStates[index].avatarDrawable.setInfo(account, currentUser);
            } else {
                currentChat = MessagesController.getInstance(account).getChat(-id);
                animatingStates[index].avatarDrawable.setInfo(account, currentChat);
            }
            if (currentStyle == 4) {
                if (id == AccountInstance.getInstance(account).getUserConfig().getClientUserId()) {
                    animatingStates[index].lastSpeakTime = 0;
                } else {
                    if (isInCall) {
                        animatingStates[index].lastSpeakTime = participant.lastActiveDate;
                    } else {
                        animatingStates[index].lastSpeakTime = participant.active_date;
                    }
                }
            } else {
                animatingStates[index].lastSpeakTime = participant.active_date;
            }
            animatingStates[index].id = id;
        } else if (object instanceof TLRPC.User) {
            currentUser = (TLRPC.User) object;
            if (currentUser.self && showSavedMessages) {
                animatingStates[index].avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                animatingStates[index].avatarDrawable.setScaleSize(0.6f);
            } else {
                animatingStates[index].avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_NORMAL);
                animatingStates[index].avatarDrawable.setScaleSize(1f);
                animatingStates[index].avatarDrawable.setInfo(account, currentUser);
            }
            animatingStates[index].id = currentUser.id;
        } else if (object instanceof TLRPC.Chat) {
            currentChat = (TLRPC.Chat) object;
            animatingStates[index].avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_NORMAL);
            animatingStates[index].avatarDrawable.setScaleSize(1f);
            animatingStates[index].avatarDrawable.setInfo(account, currentChat);
            animatingStates[index].id = -currentChat.id;
        }
        int size = getSize();
        if (object instanceof TL_stories.StoryItem) {
            TL_stories.StoryItem story = (TL_stories.StoryItem) object;
            animatingStates[index].id = story.id;
            if (story.media.document != null) {
                TLRPC.PhotoSize photoSize1 = FileLoader.getClosestPhotoSizeWithSize(story.media.document.thumbs, 50, true, null, false);
                TLRPC.PhotoSize photoSize2 = FileLoader.getClosestPhotoSizeWithSize(story.media.document.thumbs, 50, true, photoSize1, true);
                animatingStates[index].imageReceiver.setImage(
                    ImageLocation.getForDocument(photoSize2, story.media.document), size + "_" + size,
                    ImageLocation.getForDocument(photoSize1, story.media.document), size + "_" + size,
                    0, null, story, 0
                );
            } else if (story.media.photo != null) {
                TLRPC.PhotoSize photoSize1 = FileLoader.getClosestPhotoSizeWithSize(story.media.photo.sizes, 50, true, null, false);
                TLRPC.PhotoSize photoSize2 = FileLoader.getClosestPhotoSizeWithSize(story.media.photo.sizes, 50, true, photoSize1, true);
                animatingStates[index].imageReceiver.setImage(
                    ImageLocation.getForPhoto(photoSize2, story.media.photo), size + "_" + size,
                    ImageLocation.getForPhoto(photoSize1, story.media.photo), size + "_" + size,
                    0, null, story, 0
                );
            }
        } else if (currentUser != null) {
            if (currentUser.self && showSavedMessages) {
                animatingStates[index].imageReceiver.setImageBitmap(animatingStates[index].avatarDrawable);
            } else {
                animatingStates[index].imageReceiver.setForUserOrChat(currentUser, animatingStates[index].avatarDrawable);
            }
        } else {
            animatingStates[index].imageReceiver.setForUserOrChat(currentChat, animatingStates[index].avatarDrawable);
        }
        animatingStates[index].imageReceiver.setRoundRadius(size / 2);
        animatingStates[index].imageReceiver.setImageCoords(0, 0, size, size);
        invalidate();
    }

    public void onDraw(Canvas canvas) {
        wasDraw = true;
        boolean bigAvatars = currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP;
        int size = getSize();
        int toAdd;
        if (currentStyle == STYLE_MESSAGE_SEEN) {
            toAdd = AndroidUtilities.dp(12);
        } else if (overrideSize != 0) {
            toAdd = (int) (overrideSize * overrideSizeStepFactor);
        } else {
            toAdd = AndroidUtilities.dp(bigAvatars ? 24 : 20);
        }
        int drawCount = 0;
        for (int i = 0; i < 3; i++) {
            if (currentStates[i].id != 0) {
                drawCount++;
            }
        }
        int startPadding = (currentStyle == 0 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN) ? 0 : AndroidUtilities.dp(10);
        int ax = centered ? (width - drawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2 : startPadding;
        boolean isMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
        if (currentStyle == 4) {
            paint.setColor(Theme.getColor(Theme.key_inappPlayerBackground));
        } else if (currentStyle != 3) {
            paint.setColor(Theme.getColor(isMuted ? Theme.key_returnToCallMutedBackground : Theme.key_returnToCallBackground));
        }

        int animateToDrawCount = 0;
        for (int i = 0; i < 3; i++) {
            if (animatingStates[i].id != 0) {
                animateToDrawCount++;
            }
        }
        boolean useAlphaLayer = currentStyle == 0 || currentStyle == 1 || currentStyle == 3 || currentStyle == 4 || currentStyle == 5 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN;
        if (useAlphaLayer) {
            float padding = currentStyle == STYLE_GROUP_CALL_TOOLTIP ? AndroidUtilities.dp(16) : 0;
            if (drawStoriesCircle) {
                padding += AndroidUtilities.dp(20);
            }
            canvas.saveLayerAlpha(-padding, -padding, width + padding, height + padding, 255, Canvas.ALL_SAVE_FLAG);
        }
        if (drawStoriesCircle) {
            for (int a = 2; a >= 0; a--) {
                for (int k = 0; k < 2; k++) {
                    if (k == 0 && transitionProgress == 1f) {
                        continue;
                    }
                    DrawingState[] states = k == 0 ? animatingStates : currentStates;

                    if (k == 1 && transitionProgress != 1f && states[a].animationType != DrawingState.ANIMATION_TYPE_OUT) {
                        continue;
                    }
                    ImageReceiver imageReceiver = states[a].imageReceiver;
                    if (!imageReceiver.hasImageSet()) {
                        continue;
                    }
                    if (k == 0) {
                        int toAx = centered ? (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2 : startPadding;
                        imageReceiver.setImageX(toAx + toAdd * a);
                    } else {
                        imageReceiver.setImageX(ax + toAdd * a);
                    }

                    if (currentStyle == 0 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN) {
                        imageReceiver.setImageY((height - size) / 2f);
                    } else {
                        imageReceiver.setImageY(AndroidUtilities.dp(currentStyle == 4 ? 8 : 6));
                    }

                    boolean needRestore = false;
                    float alpha = 1f;
                    if (transitionProgress != 1f) {
                        if (states[a].animationType == DrawingState.ANIMATION_TYPE_OUT) {
                            canvas.save();
                            canvas.scale(1f - transitionProgress, 1f - transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                            needRestore = true;
                            alpha = 1f - transitionProgress;
                        } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_IN) {
                            canvas.save();
                            canvas.scale(transitionProgress, transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                            alpha = transitionProgress;
                            needRestore = true;
                        } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_MOVE) {
                            int toAx = centered ? (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2 : startPadding;
                            int toX = toAx + toAdd * a;
                            int fromX = ax + toAdd * states[a].moveFromIndex;
                            imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                        } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_NONE && centered) {
                            int toAx = (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2;
                            int toX = toAx + toAdd * a;
                            int fromX = ax + toAdd * a;
                            imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                        }
                    }
                    alpha *= overrideAlpha;
                    float rad = getSize() / 2f + AndroidUtilities.dp(4);
                    if (storiesTools == null) {
                        storiesTools = new StoriesGradientTools();
                    }
                    storiesTools.setBounds(0, 0, parent.getMeasuredHeight(), AndroidUtilities.dp(40));
                    storiesTools.paint.setAlpha((int) (255 * alpha));
                    canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), rad, storiesTools.paint);
                    if (needRestore) {
                        canvas.restore();
                    }
                }
            }
        }
        for (int a = 2; a >= 0; a--) {
            for (int k = 0; k < 2; k++) {
                if (k == 0 && transitionProgress == 1f) {
                    continue;
                }
                DrawingState[] states = k == 0 ? animatingStates : currentStates;


                if (k == 1 && transitionProgress != 1f && states[a].animationType != DrawingState.ANIMATION_TYPE_OUT) {
                    continue;
                }
                ImageReceiver imageReceiver = states[a].imageReceiver;
                if (!imageReceiver.hasImageSet()) {
                    continue;
                }
                if (k == 0) {
                    int toAx = centered ? (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2 : startPadding;
                    imageReceiver.setImageX(toAx + toAdd * a);
                } else {
                    imageReceiver.setImageX(ax + toAdd * a);
                }

                if (currentStyle == 0 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN) {
                    imageReceiver.setImageY((height - size) / 2f);
                } else {
                    imageReceiver.setImageY(AndroidUtilities.dp(currentStyle == 4 ? 8 : 6));
                }

                boolean needRestore = false;
                float alpha = 1f;
                if (transitionProgress != 1f) {
                    if (states[a].animationType == DrawingState.ANIMATION_TYPE_OUT) {
                        canvas.save();
                        canvas.scale(1f - transitionProgress, 1f - transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                        needRestore = true;
                        alpha = 1f - transitionProgress;
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_IN) {
                        canvas.save();
                        canvas.scale(transitionProgress, transitionProgress, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                        alpha = transitionProgress;
                        needRestore = true;
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_MOVE) {
                        int toAx = centered ? (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2 : startPadding;
                        int toX = toAx + toAdd * a;
                        int fromX = ax + toAdd * states[a].moveFromIndex;
                        imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                    } else if (states[a].animationType == DrawingState.ANIMATION_TYPE_NONE && centered) {
                        int toAx = (width - animateToDrawCount * toAdd - AndroidUtilities.dp(bigAvatars ? 8 : 4)) / 2;
                        int toX = toAx + toAdd * a;
                        int fromX = ax + toAdd * a;
                        imageReceiver.setImageX((int) (toX * transitionProgress + fromX * (1f - transitionProgress)));
                    }
                }
                alpha *= overrideAlpha;

                float avatarScale = 1f;
                if (a != states.length - 1 || drawStoriesCircle) {
                    if (currentStyle == 1 || currentStyle == 3 || currentStyle == 5) {
                        canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(13), xRefP);
                        if (states[a].wavesDrawable == null) {
                            if (currentStyle == 5) {
                                states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(14), AndroidUtilities.dp(16));
                            } else {
                                states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(17), AndroidUtilities.dp(21));
                            }
                        }
                        if (currentStyle == 5) {
                            states[a].wavesDrawable.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_speakingText), (int) (255 * 0.3f * alpha)));
                        }
                        if (states[a].participant != null && states[a].participant.amplitude > 0) {
                            states[a].wavesDrawable.setShowWaves(true, parent);
                            float amplitude = states[a].participant.amplitude * 15f;
                            states[a].wavesDrawable.setAmplitude(amplitude);
                        } else {
                            states[a].wavesDrawable.setShowWaves(false, parent);
                        }
                        if (currentStyle == 5 && (SystemClock.uptimeMillis() - states[a].participant.lastSpeakTime) > 500) {
                            updateDelegate.run();
                        }
                        states[a].wavesDrawable.update();
                        if (currentStyle == 5) {
                            states[a].wavesDrawable.draw(canvas, imageReceiver.getCenterX(), imageReceiver.getCenterY(), parent);
                            invalidate();
                        }
                        avatarScale = states[a].wavesDrawable.getAvatarScale();
                    } else if (currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
                        canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(17), xRefP);
                        if (states[a].wavesDrawable == null) {
                            states[a].wavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(17), AndroidUtilities.dp(21));
                        }
                        if (currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
                            states[a].wavesDrawable.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_speakingText), (int) (255 * 0.3f * alpha)));
                        } else {
                            states[a].wavesDrawable.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_listeningText), (int) (255 * 0.3f * alpha)));
                        }
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - states[a].lastUpdateTime > 100) {
                            states[a].lastUpdateTime = currentTime;
                            if (currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
                                if (states[a].participant != null && states[a].participant.amplitude > 0) {
                                    states[a].wavesDrawable.setShowWaves(true, parent);
                                    float amplitude = states[a].participant.amplitude * 15f;
                                    states[a].wavesDrawable.setAmplitude(amplitude);
                                } else {
                                    states[a].wavesDrawable.setShowWaves(false, parent);
                                }
                            } else {
                                if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime() - states[a].lastSpeakTime <= 5) {
                                    states[a].wavesDrawable.setShowWaves(true, parent);
                                    states[a].wavesDrawable.setAmplitude(random.nextInt() % 100);
                                } else {
                                    states[a].wavesDrawable.setShowWaves(false, parent);
                                    states[a].wavesDrawable.setAmplitude(0);
                                }
                            }
                        }
                        states[a].wavesDrawable.update();
                        states[a].wavesDrawable.draw(canvas, imageReceiver.getCenterX(), imageReceiver.getCenterY(), parent);
                        avatarScale = states[a].wavesDrawable.getAvatarScale();
                    } else {
                        float rad = getSize() / 2f + strokeWidth;
                        if (useAlphaLayer) {
                            canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), rad, xRefP);
                        } else {
                            int paintAlpha = paint.getAlpha();
                            if (alpha != 1f) {
                                paint.setAlpha((int) (paintAlpha * alpha));
                            }
                            canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), rad, paint);
                            if (alpha != 1f) {
                                paint.setAlpha(paintAlpha);
                            }
                        }
                    }
                }
                imageReceiver.setAlpha(alpha);
                if (avatarScale != 1f) {
                    canvas.save();
                    canvas.scale(avatarScale, avatarScale, imageReceiver.getCenterX(), imageReceiver.getCenterY());
                    imageReceiver.draw(canvas);
                    canvas.restore();
                } else {
                    imageReceiver.draw(canvas);
                }
                if (needRestore) {
                    canvas.restore();
                }
            }
        }
        if (useAlphaLayer) {
            canvas.restore();
        }
    }

    public int getSize() {
        if (overrideSize != 0) {
            return overrideSize;
        }
        boolean bigAvatars = currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP;
        return AndroidUtilities.dp(bigAvatars ? 32 : 24);
    }

    private boolean attached;

    public void onDetachedFromWindow() {
        if (!attached) {
            return;
        }
        attached = false;
        wasDraw = false;
        for (int a = 0; a < 3; a++) {
            currentStates[a].imageReceiver.onDetachedFromWindow();
            animatingStates[a].imageReceiver.onDetachedFromWindow();
        }
        if (currentStyle == 3) {
            Theme.getFragmentContextViewWavesDrawable().setAmplitude(0);
        }
    }

    public void onAttachedToWindow() {
        if (attached) {
            return;
        }
        attached = true;
        for (int a = 0; a < 3; a++) {
            currentStates[a].imageReceiver.onAttachedToWindow();
            animatingStates[a].imageReceiver.onAttachedToWindow();
        }
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public void setCount(int count) {
        this.count = count;
        if (parent != null) {
            parent.requestLayout();
        }
    }

    public void reset() {
        for (int i = 0; i < animatingStates.length; ++i) {
            setObject(0, 0, null);
        }
    }

    public void setShowSavedMessages(boolean showSavedMessages) {
        this.showSavedMessages = showSavedMessages;
    }
}
