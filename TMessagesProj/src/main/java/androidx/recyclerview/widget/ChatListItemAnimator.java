package androidx.recyclerview.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatGreetingsView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static androidx.recyclerview.widget.ViewInfoStore.InfoRecord.FLAG_DISAPPEARED;

public class ChatListItemAnimator extends DefaultItemAnimator {

    public static final int ANIMATION_TYPE_OUT = 1;
    public static final int ANIMATION_TYPE_IN = 2;
    public static final int ANIMATION_TYPE_MOVE = 3;

    private final ChatActivity activity;
    private final RecyclerListView recyclerListView;

    private HashMap<Integer, MessageObject.GroupedMessages> willRemovedGroup = new HashMap<>();
    private ArrayList<MessageObject.GroupedMessages> willChangedGroups = new ArrayList<>();

    HashMap<RecyclerView.ViewHolder,Animator> animators = new HashMap<>();

    ArrayList<Runnable> runOnAnimationsEnd = new ArrayList<>();

    private boolean shouldAnimateEnterFromBottom;
    private RecyclerView.ViewHolder greetingsSticker;
    private ChatGreetingsView chatGreetingsView;

    public ChatListItemAnimator(ChatActivity activity, RecyclerListView listView) {
        this.activity = activity;
        this.recyclerListView = listView;
        translationInterpolator = CubicBezierInterpolator.DEFAULT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            listView.getElevation();
        }
    }

    @Override
    public void runPendingAnimations() {
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            return;
        }

        boolean runTranslationFromBottom = false;
        if (shouldAnimateEnterFromBottom) {
            for (int i = 0; i < mPendingAdditions.size(); i++) {
                if (mPendingAdditions.get(i).getLayoutPosition() == 0) {
                    runTranslationFromBottom = true;
                }
            }
        }

        onAnimationStart();

        if (runTranslationFromBottom) {
            runMessageEnterTransition();
        } else {
            runAlphaEnterTransition();
        }

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            activity.onListItemAniamtorTick();
        });
        valueAnimator.setDuration(getRemoveDuration() + getMoveDuration());
        valueAnimator.start();
    }

    private void runAlphaEnterTransition() {
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            // nothing to animate
            return;
        }
        // First, remove stuff
        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();
        // Next, move stuff
        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>();
            moves.addAll(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();
            Runnable mover = new Runnable() {
                @Override
                public void run() {
                    for (MoveInfo moveInfo : moves) {
                        animateMoveImpl(moveInfo.holder, moveInfo);
                    }
                    moves.clear();
                    mMovesList.remove(moves);
                }
            };
            if (delayAnimations && removalsPending) {
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, getMoveAnimationDelay());
            } else {
                mover.run();
            }
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {
            final ArrayList<ChangeInfo> changes = new ArrayList<>();
            changes.addAll(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();
            Runnable changer = new Runnable() {
                @Override
                public void run() {
                    for (ChangeInfo change : changes) {
                        animateChangeImpl(change);
                    }
                    changes.clear();
                    mChangesList.remove(changes);
                }
            };
            if (delayAnimations && removalsPending) {
                RecyclerView.ViewHolder holder = changes.get(0).oldHolder;
                ViewCompat.postOnAnimationDelayed(holder.itemView, changer, 0);
            } else {
                changer.run();
            }
        }
        // Next, add stuff
        if (additionsPending) {
            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
            additions.addAll(mPendingAdditions);
            mPendingAdditions.clear();

            for (RecyclerView.ViewHolder holder : additions) {
                animateAddImpl(holder);
            }
            additions.clear();
        }
    }

    private void runMessageEnterTransition() {
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();

        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            return;
        }

        int addedItemsHeight = 0;
        for (int i = 0; i < mPendingAdditions.size(); i++) {
            View view = mPendingAdditions.get(i).itemView;
            if (view instanceof ChatMessageCell) {
                ChatMessageCell cell = ((ChatMessageCell) view);
                if (cell.getCurrentPosition() != null && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0) {
                    continue;
                }
            }
            addedItemsHeight += mPendingAdditions.get(i).itemView.getHeight();
        }

        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();
        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>();
            moves.addAll(mPendingMoves);
            mPendingMoves.clear();
            for (MoveInfo moveInfo : moves) {
                animateMoveImpl(moveInfo.holder, moveInfo);
            }
            moves.clear();
        }

        if (additionsPending) {
            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
            additions.addAll(mPendingAdditions);
            mPendingAdditions.clear();

            for (RecyclerView.ViewHolder holder : additions) {
                animateAddImpl(holder, addedItemsHeight);
            }
            additions.clear();
        }
    }

    @Override
    public boolean animateAppearance(@NonNull RecyclerView.ViewHolder viewHolder, @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        boolean res = super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo);
        if (res && shouldAnimateEnterFromBottom) {
            boolean runTranslationFromBottom = false;
            for (int i = 0; i < mPendingAdditions.size(); i++) {
                if (mPendingAdditions.get(i).getLayoutPosition() == 0) {
                    runTranslationFromBottom = true;
                }
            }
            int addedItemsHeight = 0;
            if (runTranslationFromBottom) {
                for (int i = 0; i < mPendingAdditions.size(); i++) {
                    addedItemsHeight += mPendingAdditions.get(i).itemView.getHeight();
                }
            }

            for (int i = 0; i < mPendingAdditions.size(); i++) {
                mPendingAdditions.get(i).itemView.setTranslationY(addedItemsHeight);
            }
        }
        return res;
    }

    public void animateAddImpl(final RecyclerView.ViewHolder holder, int addedItemsHeight) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);
        view.setTranslationY(addedItemsHeight);
        if (!(holder.itemView instanceof ChatMessageCell && ((ChatMessageCell) holder.itemView).getTransitionParams().ignoreAlpha)) {
            holder.itemView.setAlpha(1);
        }
        animation.translationY(0).setDuration(getMoveDuration())
                .setInterpolator(translationInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchAddStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setTranslationY(0);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        if (mAddAnimations.remove(holder)) {
                            dispatchAddFinished(holder);
                            dispatchFinishedWhenDone();
                        }

                    }
                }).start();
    }

    @Override
    public boolean animateRemove(RecyclerView.ViewHolder holder, ItemHolderInfo info) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate remove");
        }
        boolean rez = super.animateRemove(holder, info);
        if (rez) {
            if (info != null) {
                int fromY = info.top;
                int toY = holder.itemView.getTop();

                int fromX = info.left;
                int toX = holder.itemView.getLeft();

                int deltaX = toX - fromX;
                int deltaY = toY - fromY;

                if (deltaY != 0) {
                    holder.itemView.setTranslationY(-deltaY);
                }

                if (holder.itemView instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) holder.itemView;
                    if (deltaX != 0) {
                        chatMessageCell.setAnimationOffsetX(-deltaX);
                    }
                    if (info instanceof ItemHolderInfoExtended) {
                        ItemHolderInfoExtended infoExtended = ((ItemHolderInfoExtended) info);
                        chatMessageCell.setImageCoords(infoExtended.imageX, infoExtended.imageY, infoExtended.imageWidth, infoExtended.imageHeight);
                    }
                } else {
                    if (deltaX != 0) {
                        holder.itemView.setTranslationX(-deltaX);
                    }
                }
            }
        }
        return rez;
    }

    @Override
    public boolean animateMove(RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate move");
        }
        final View view = holder.itemView;
        if (holder.itemView instanceof ChatMessageCell) {
            fromX += (int) ((ChatMessageCell) holder.itemView).getAnimationOffsetX();
        } else {
            fromX += (int) holder.itemView.getTranslationX();
        }
        fromY += (int) holder.itemView.getTranslationY();
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }

        MoveInfoExtended moveInfo = new MoveInfoExtended(holder, fromX, fromY, toX, toY);

        if (holder.itemView instanceof ChatMessageCell) {
            ChatMessageCell chatMessageCell = (ChatMessageCell) holder.itemView;
            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();

            if (!params.supportChangeAnimation()) {
                if (deltaX == 0 && deltaY == 0) {
                    dispatchMoveFinished(holder);
                    return false;
                }
                if (deltaX != 0) {
                    view.setTranslationX(-deltaX);
                }
                mPendingMoves.add(moveInfo);
                return true;
            }

            MessageObject.GroupedMessages group = chatMessageCell.getCurrentMessagesGroup();

            if (deltaX != 0) {
                chatMessageCell.setAnimationOffsetX(-deltaX);
            }

            if (info instanceof ItemHolderInfoExtended) {
                ImageReceiver newImage = chatMessageCell.getPhotoImage();
                ItemHolderInfoExtended infoExtended = ((ItemHolderInfoExtended) info);
                moveInfo.animateImage = params.wasDraw && infoExtended.imageHeight != 0 && infoExtended.imageWidth != 0;
                if (moveInfo.animateImage) {
                    recyclerListView.setClipChildren(false);
                    recyclerListView.invalidate();

                    params.imageChangeBoundsTransition = true;
                    params.animateToImageX = newImage.getImageX();
                    params.animateToImageY = newImage.getImageY();
                    params.animateToImageW = newImage.getImageWidth();
                    params.animateToImageH = newImage.getImageHeight();

                    params.animateToRadius = newImage.getRoundRadius();
                    params.animateRadius = false;
                    for (int i = 0; i < 4; i++) {
                        if (params.imageRoundRadius[i] != params.animateToRadius[i]) {
                            params.animateRadius = true;
                            break;
                        }
                    }
                    if (params.animateToImageX == infoExtended.imageX && params.animateToImageY == infoExtended.imageY &&
                            params.animateToImageH == infoExtended.imageHeight && params.animateToImageW == infoExtended.imageWidth && !params.animateRadius) {
                        params.imageChangeBoundsTransition = false;
                        moveInfo.animateImage = false;
                    } else {
                        moveInfo.imageX = infoExtended.imageX;
                        moveInfo.imageY = infoExtended.imageY;
                        moveInfo.imageWidth = infoExtended.imageWidth;
                        moveInfo.imageHeight = infoExtended.imageHeight;

                        if (group != null && group.hasCaption != group.transitionParams.drawCaptionLayout) {
                            group.transitionParams.captionEnterProgress = group.transitionParams.drawCaptionLayout ? 1f : 0;
                        }
                        if (params.animateRadius) {
                            params.animateToRadius = new int[4];
                            for (int i = 0; i < 4; i++) {
                                params.animateToRadius[i] = newImage.getRoundRadius()[i];
                            }
                            newImage.setRoundRadius(params.imageRoundRadius);
                        }
                        chatMessageCell.setImageCoords(moveInfo.imageX, moveInfo.imageY, moveInfo.imageWidth, moveInfo.imageHeight);
                    }
                }

                if (group == null && params.wasDraw) {
                    boolean isOut = chatMessageCell.getMessageObject().isOutOwner();
                    if ((isOut && params.lastDrawingBackgroundRect.left != chatMessageCell.getBackgroundDrawableLeft()) ||
                            (!isOut && params.lastDrawingBackgroundRect.right != chatMessageCell.getBackgroundDrawableRight()) ||
                            params.lastDrawingBackgroundRect.top != chatMessageCell.getBackgroundDrawableTop() ||
                            params.lastDrawingBackgroundRect.bottom != chatMessageCell.getBackgroundDrawableBottom()) {
                        moveInfo.deltaBottom = chatMessageCell.getBackgroundDrawableBottom() - params.lastDrawingBackgroundRect.bottom;
                        moveInfo.deltaTop = chatMessageCell.getBackgroundDrawableTop() - params.lastDrawingBackgroundRect.top;
                        if (isOut) {
                            moveInfo.deltaLeft = chatMessageCell.getBackgroundDrawableLeft() - params.lastDrawingBackgroundRect.left;
                        } else {
                            moveInfo.deltaRight = chatMessageCell.getBackgroundDrawableRight() - params.lastDrawingBackgroundRect.right;
                        }
                        moveInfo.animateBackgroundOnly = true;

                        params.animateBackgroundBoundsInner = true;
                        params.deltaLeft = -moveInfo.deltaLeft;
                        params.deltaRight = -moveInfo.deltaRight;
                        params.deltaTop = -moveInfo.deltaTop;
                        params.deltaBottom = -moveInfo.deltaBottom;

                        recyclerListView.setClipChildren(false);
                        recyclerListView.invalidate();
                    }
                }
            }

            if (group != null) {
                if (willChangedGroups.contains(group)) {
                    willChangedGroups.remove(group);
                    RecyclerListView recyclerListView = (RecyclerListView) holder.itemView.getParent();
                    int animateToLeft = 0;
                    int animateToRight = 0;
                    int animateToTop = 0;
                    int animateToBottom = 0;
                    boolean allVisibleItemsDeleted = true;

                    MessageObject.GroupedMessages.TransitionParams groupTransitionParams = group.transitionParams;
                    ChatMessageCell lastDrawingCell = null;
                    for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                        View child = recyclerListView.getChildAt(i);

                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (cell.getCurrentMessagesGroup() == group && !cell.getMessageObject().deleted) {

                                int left = cell.getLeft() + cell.getBackgroundDrawableLeft();
                                int right = cell.getLeft() + cell.getBackgroundDrawableRight();
                                int top = cell.getTop() + cell.getBackgroundDrawableTop();
                                int bottom = cell.getTop() + cell.getBackgroundDrawableBottom();

                                if (animateToLeft == 0 || left < animateToLeft) {
                                    animateToLeft = left;
                                }

                                if (animateToRight == 0 || right > animateToRight) {
                                    animateToRight = right;
                                }

                                if (cell.getTransitionParams().wasDraw || groupTransitionParams.isNewGroup) {
                                    lastDrawingCell = cell;
                                    allVisibleItemsDeleted = false;
                                    if (animateToTop == 0 || top < animateToTop) {
                                        animateToTop = top;
                                    }
                                    if (animateToBottom == 0 || bottom > animateToBottom) {
                                        animateToBottom = bottom;
                                    }
                                }
                            }
                        }
                    }

                    groupTransitionParams.isNewGroup = false;

                    if (animateToTop == 0 &&  animateToBottom == 0 && animateToLeft == 0 && animateToRight == 0) {
                        moveInfo.animateChangeGroupBackground = false;
                        groupTransitionParams.backgroundChangeBounds = false;
                    } else {
                        moveInfo.groupOffsetTop = -animateToTop + groupTransitionParams.top;
                        moveInfo.groupOffsetBottom = -animateToBottom + groupTransitionParams.bottom;
                        moveInfo.groupOffsetLeft = -animateToLeft + groupTransitionParams.left;
                        moveInfo.groupOffsetRight = -animateToRight + groupTransitionParams.right;

                        moveInfo.animateChangeGroupBackground = true;
                        groupTransitionParams.backgroundChangeBounds = true;
                        groupTransitionParams.offsetTop = moveInfo.groupOffsetTop;
                        groupTransitionParams.offsetBottom = moveInfo.groupOffsetBottom;
                        groupTransitionParams.offsetLeft = moveInfo.groupOffsetLeft;
                        groupTransitionParams.offsetRight = moveInfo.groupOffsetRight;

                        groupTransitionParams.captionEnterProgress = groupTransitionParams.drawCaptionLayout ? 1f : 0f;

                        recyclerListView.setClipChildren(false);
                        recyclerListView.invalidate();
                    }

                    groupTransitionParams.drawBackgroundForDeletedItems = allVisibleItemsDeleted;
                }
            }

            MessageObject.GroupedMessages removedGroup = willRemovedGroup.get(chatMessageCell.getMessageObject().getId());
            if (removedGroup != null) {
                MessageObject.GroupedMessages.TransitionParams groupTransitionParams = removedGroup.transitionParams;
                willRemovedGroup.remove(chatMessageCell.getMessageObject().getId());
                if (params.wasDraw) {
                    // invoke when group transform to single message
                    int animateToLeft = chatMessageCell.getLeft() + chatMessageCell.getBackgroundDrawableLeft();
                    int animateToRight = chatMessageCell.getLeft() + chatMessageCell.getBackgroundDrawableRight();
                    int animateToTop = chatMessageCell.getTop() + chatMessageCell.getBackgroundDrawableTop();
                    int animateToBottom = chatMessageCell.getTop() + chatMessageCell.getBackgroundDrawableBottom();

                    params.animateBackgroundBoundsInner = moveInfo.animateRemoveGroup = true;
                    moveInfo.deltaLeft = animateToLeft - groupTransitionParams.left;
                    moveInfo.deltaRight = animateToRight - groupTransitionParams.right;
                    moveInfo.deltaTop = animateToTop - groupTransitionParams.top;
                    moveInfo.deltaBottom = animateToBottom - groupTransitionParams.bottom;
                    moveInfo.animateBackgroundOnly = false;

                    params.deltaLeft = (int) (-moveInfo.deltaLeft - chatMessageCell.getAnimationOffsetX());
                    params.deltaRight = (int) (-moveInfo.deltaRight - chatMessageCell.getAnimationOffsetX());
                    params.deltaTop = (int) (-moveInfo.deltaTop - chatMessageCell.getTranslationY());
                    params.deltaBottom = (int) (-moveInfo.deltaBottom - chatMessageCell.getTranslationY());
                    params.transformGroupToSingleMessage = true;

                    recyclerListView.setClipChildren(false);
                    recyclerListView.invalidate();
                } else {
                    groupTransitionParams.drawBackgroundForDeletedItems = true;
                }
            }
            boolean drawPinnedBottom = chatMessageCell.isDrawPinnedBottom();
            if (params.drawPinnedBottomBackground != drawPinnedBottom) {
                moveInfo.animatePinnedBottom = true;
                params.changePinnedBottomProgress = 0;
            }

            moveInfo.animateChangeInternal = chatMessageCell.getTransitionParams().animateChange();
            if (moveInfo.animateChangeInternal) {
                chatMessageCell.getTransitionParams().animateChangeProgress = 0f;
            }

            if (deltaX == 0 && deltaY == 0 && !moveInfo.animateImage && !moveInfo.animateRemoveGroup && !moveInfo.animateChangeGroupBackground && !moveInfo.animatePinnedBottom && !moveInfo.animateBackgroundOnly && !moveInfo.animateChangeInternal) {
                dispatchMoveFinished(holder);
                return false;
            }
        } else if (holder.itemView instanceof BotHelpCell) {
            BotHelpCell botInfo = (BotHelpCell) holder.itemView;
            botInfo.setAnimating(true);
        } else {
            if (deltaX == 0 && deltaY == 0) {
                dispatchMoveFinished(holder);
                return false;
            }
            if (deltaX != 0) {
                view.setTranslationX(-deltaX);
            }
        }

        mPendingMoves.add(moveInfo);
        return true;
    }

    @Override
    void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate move impl");
        }
        int fromX = moveInfo.fromX;
        int fromY = moveInfo.fromY;
        int toX = moveInfo.toX;
        int toY = moveInfo.toY;
        final View view = holder.itemView;
        final int deltaY = toY - fromY;

        AnimatorSet animatorSet = new AnimatorSet();

        if (deltaY != 0) {
            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0));
        }
        mMoveAnimations.add(holder);

        MoveInfoExtended moveInfoExtended = (MoveInfoExtended) moveInfo;

        if (holder.itemView instanceof BotHelpCell) {
            BotHelpCell botCell = (BotHelpCell) holder.itemView ;
            int top = recyclerListView.getMeasuredHeight() / 2 - botCell.getMeasuredHeight() / 2;
            float animateTo = 0;
            if (botCell.getTop() > top) {
                animateTo = top - botCell.getTop();
            }
            animatorSet.playTogether(ObjectAnimator.ofFloat(botCell, View.TRANSLATION_Y, botCell.getTranslationY(), animateTo));
        } else if (holder.itemView instanceof ChatMessageCell) {
            ChatMessageCell chatMessageCell = (ChatMessageCell) holder.itemView;
            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(chatMessageCell, chatMessageCell.ANIMATION_OFFSET_X, 0);
            animatorSet.playTogether(objectAnimator);

            if (moveInfoExtended.animateImage) {
                chatMessageCell.setImageCoords(moveInfoExtended.imageX, moveInfoExtended.imageY, moveInfoExtended.imageWidth, moveInfoExtended.imageHeight);
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);


                float captionEnterFrom = chatMessageCell.getCurrentMessagesGroup() == null ? params.captionEnterProgress : chatMessageCell.getCurrentMessagesGroup().transitionParams.captionEnterProgress;
                float captionEnterTo = chatMessageCell.getCurrentMessagesGroup() == null ? (chatMessageCell.hasCaptionLayout()  ? 1 : 0) : (chatMessageCell.getCurrentMessagesGroup().hasCaption ? 1 : 0);
                boolean animateCaption = captionEnterFrom != captionEnterTo;

                int[] fromRoundRadius = null;
                if (params.animateRadius) {
                    fromRoundRadius = new int[4];
                    for (int i = 0; i < 4; i++) {
                        fromRoundRadius[i] = chatMessageCell.getPhotoImage().getRoundRadius()[i];
                    }
                }

                int[] finalFromRoundRadius = fromRoundRadius;
                valueAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    float x = moveInfoExtended.imageX * (1f - v) + params.animateToImageX * v;
                    float y = moveInfoExtended.imageY * (1f - v) + params.animateToImageY * v;
                    float width = moveInfoExtended.imageWidth * (1f - v) + params.animateToImageW * v;
                    float height = moveInfoExtended.imageHeight * (1f - v) + params.animateToImageH * v;

                    if (animateCaption) {
                        float captionP = captionEnterFrom * (1f - v) + captionEnterTo * v;
                        params.captionEnterProgress = captionP;
                        if (chatMessageCell.getCurrentMessagesGroup() != null) {
                            chatMessageCell.getCurrentMessagesGroup().transitionParams.captionEnterProgress = captionP;
                        }
                    }


                    if (params.animateRadius) {
                        chatMessageCell.getPhotoImage().setRoundRadius(
                                (int) (finalFromRoundRadius[0] * (1f - v) + params.animateToRadius[0] * v),
                                (int) (finalFromRoundRadius[1] * (1f - v) + params.animateToRadius[1] * v),
                                (int) (finalFromRoundRadius[2] * (1f - v) + params.animateToRadius[2] * v),
                                (int) (finalFromRoundRadius[3] * (1f - v) + params.animateToRadius[3] * v)
                        );
                    }

                    chatMessageCell.setImageCoords(x, y, width, height);
                    holder.itemView.invalidate();
                });
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        params.imageChangeBoundsTransition = false;
                    }
                });
                animatorSet.playTogether(valueAnimator);
            }
            if (moveInfoExtended.deltaBottom != 0 || moveInfoExtended.deltaRight != 0 || moveInfoExtended.deltaTop != 0 || moveInfoExtended.deltaLeft != 0) {

                recyclerListView.setClipChildren(false);
                recyclerListView.invalidate();

                ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
                valueAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    if (moveInfoExtended.animateBackgroundOnly) {
                        params.deltaLeft = (int) (-moveInfoExtended.deltaLeft * v);
                        params.deltaRight = (int) (-moveInfoExtended.deltaRight * v);
                        params.deltaTop = (int) (-moveInfoExtended.deltaTop * v);
                        params.deltaBottom = (int) (-moveInfoExtended.deltaBottom * v);
                    } else {
                        params.deltaLeft = (int) (-moveInfoExtended.deltaLeft * v - chatMessageCell.getAnimationOffsetX());
                        params.deltaRight = (int) (-moveInfoExtended.deltaRight * v - chatMessageCell.getAnimationOffsetX());
                        params.deltaTop = (int) (-moveInfoExtended.deltaTop * v - chatMessageCell.getTranslationY());
                        params.deltaBottom = (int) (-moveInfoExtended.deltaBottom * v - chatMessageCell.getTranslationY());
                    }
                    chatMessageCell.invalidate();
                });
                animatorSet.playTogether(valueAnimator);
            }

            MessageObject.GroupedMessages group = chatMessageCell.getCurrentMessagesGroup();
            if (group == null) {
                moveInfoExtended.animateChangeGroupBackground = false;
            }

            if (moveInfoExtended.animateChangeGroupBackground) {
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
                MessageObject.GroupedMessages.TransitionParams groupTransitionParams = group.transitionParams;
                RecyclerListView recyclerListView = (RecyclerListView) holder.itemView.getParent();

                float captionEnterFrom = group.transitionParams.captionEnterProgress;
                float captionEnterTo = group.hasCaption ? 1 : 0;

                boolean animateCaption = captionEnterFrom != captionEnterTo;
                valueAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    groupTransitionParams.offsetTop = moveInfoExtended.groupOffsetTop * v;
                    groupTransitionParams.offsetBottom = moveInfoExtended.groupOffsetBottom * v;
                    groupTransitionParams.offsetLeft = moveInfoExtended.groupOffsetLeft * v;
                    groupTransitionParams.offsetRight = moveInfoExtended.groupOffsetRight * v;
                    if (animateCaption) {
                        groupTransitionParams.captionEnterProgress = captionEnterFrom * v + captionEnterTo * (1f - v);
                    }
                    recyclerListView.invalidate();
                });

                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        groupTransitionParams.backgroundChangeBounds = false;
                        groupTransitionParams.drawBackgroundForDeletedItems = false;
                    }
                });
                animatorSet.playTogether(valueAnimator);
            }

            if (moveInfoExtended.animatePinnedBottom) {
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                valueAnimator.addUpdateListener(animation -> {
                    params.changePinnedBottomProgress = (float) animation.getAnimatedValue();
                    chatMessageCell.invalidate();
                });

                animatorSet.playTogether(valueAnimator);
            }

            if (moveInfoExtended.animateChangeInternal) {
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                valueAnimator.addUpdateListener(animation -> {
                    params.animateChangeProgress = (float) animation.getAnimatedValue();
                    chatMessageCell.invalidate();
                });
                animatorSet.playTogether(valueAnimator);
            }
        }

        if (translationInterpolator != null) {
            animatorSet.setInterpolator(translationInterpolator);
        }
        animatorSet.setDuration(getMoveDuration());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchMoveStarting(holder);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                if (deltaY != 0) {
                    view.setTranslationY(0);
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animator.removeAllListeners();
                restoreTransitionParams(holder.itemView);
                if (holder.itemView instanceof ChatMessageCell) {
                    MessageObject.GroupedMessages group = ((ChatMessageCell) view).getCurrentMessagesGroup();
                    if (group != null) {
                        group.transitionParams.reset();
                    }
                }
                if (mMoveAnimations.remove(holder)) {
                    dispatchMoveFinished(holder);
                    dispatchFinishedWhenDone();
                }
            }
        });
        animatorSet.start();
        animators.put(holder, animatorSet);
    }

    boolean reset;

    @Override
    public void resetAnimation(RecyclerView.ViewHolder holder) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("reset animation");
        }
        reset = true;
        super.resetAnimation(holder);
        reset = false;
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, ItemHolderInfo info,
                                 int fromX, int fromY, int toX, int toY) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate change");
        }
        if (oldHolder == newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder, info, fromX, fromY, toX, toY);
        }
        final float prevTranslationX;
        if (oldHolder.itemView instanceof ChatMessageCell) {
            prevTranslationX = ((ChatMessageCell) oldHolder.itemView).getAnimationOffsetX();
        } else {
            prevTranslationX = oldHolder.itemView.getTranslationX();
        }
        final float prevTranslationY = oldHolder.itemView.getTranslationY();
        final float prevAlpha = oldHolder.itemView.getAlpha();
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        // recover prev translation state after ending animation
        if (oldHolder.itemView instanceof ChatMessageCell) {
            ((ChatMessageCell) oldHolder.itemView).setAnimationOffsetX(prevTranslationX);
        } else {
            oldHolder.itemView.setTranslationX(prevTranslationX);
        }
        oldHolder.itemView.setTranslationY(prevTranslationY);
        oldHolder.itemView.setAlpha(prevAlpha);
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder);
            if (newHolder.itemView instanceof ChatMessageCell) {
                ((ChatMessageCell) newHolder.itemView).setAnimationOffsetX(-deltaX);
            } else {
                newHolder.itemView.setTranslationX(-deltaX);
            }
            newHolder.itemView.setTranslationY(-deltaY);
            newHolder.itemView.setAlpha(0);
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        return true;
    }

    void animateChangeImpl(final ChangeInfo changeInfo) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate change impl");
        }
        final RecyclerView.ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final RecyclerView.ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        if (view != null) {
            final ViewPropertyAnimator oldViewAnim = view.animate().setDuration(
                    getChangeDuration());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
            oldViewAnim.alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.oldHolder, true);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    oldViewAnim.setListener(null);
                    view.setAlpha(1);
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setAnimationOffsetX(0);
                    } else {
                        view.setTranslationX(0);
                    }
                    view.setTranslationY(0);
                    if (mChangeAnimations.remove(changeInfo.oldHolder)) {
                        dispatchChangeFinished(changeInfo.oldHolder, true);
                        dispatchFinishedWhenDone();
                    }
                }
            }).start();
        }
        if (newView != null) {
            final ViewPropertyAnimator newViewAnimation = newView.animate();
            mChangeAnimations.add(changeInfo.newHolder);
            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration())
                    .alpha(1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.newHolder, false);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    newViewAnimation.setListener(null);
                    newView.setAlpha(1);
                    if (newView instanceof ChatMessageCell) {
                        ((ChatMessageCell) newView).setAnimationOffsetX(0);
                    } else {
                        newView.setTranslationX(0);
                    }
                    newView.setTranslationY(0);

                    if (mChangeAnimations.remove(changeInfo.newHolder)) {
                        dispatchChangeFinished(changeInfo.newHolder, false);
                        dispatchFinishedWhenDone();
                    }
                }
            }).start();
        }
    }

    @NonNull
    @Override
    public ItemHolderInfo recordPreLayoutInformation(@NonNull RecyclerView.State state, @NonNull RecyclerView.ViewHolder viewHolder, int changeFlags, @NonNull List<Object> payloads) {
        ItemHolderInfo info = super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads);
        if (viewHolder.itemView instanceof ChatMessageCell) {
            ChatMessageCell chatMessageCell = (ChatMessageCell) viewHolder.itemView;
            ItemHolderInfoExtended extended = new ItemHolderInfoExtended();
            extended.left = info.left;
            extended.top = info.top;
            extended.right = info.right;
            extended.bottom = info.bottom;

            ChatMessageCell.TransitionParams params = chatMessageCell.getTransitionParams();
            extended.imageX = params.lastDrawingImageX;
            extended.imageY = params.lastDrawingImageY;
            extended.imageWidth = params.lastDrawingImageW;
            extended.imageHeight = params.lastDrawingImageH;
            return extended;
        }
        return info;
    }

    @Override
    protected void onAllAnimationsDone() {
        super.onAllAnimationsDone();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("all animations done");
        }

        if (!reset) {
            recyclerListView.setClipChildren(true);
        }
        while (!runOnAnimationsEnd.isEmpty()) {
            runOnAnimationsEnd.remove(0).run();
        }
        cancelAnimators();
    }

    private void cancelAnimators() {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("cancel animations");
        }
        ArrayList<Animator> anim = new ArrayList<>(animators.values());
        animators.clear();
        for (Animator animator : anim) {
            if (animator != null) {
                animator.cancel();
            }
        }
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
        Animator animator = animators.remove(item);
        if (animator != null) {
            animator.cancel();
        }
        super.endAnimation(item);
        restoreTransitionParams(item.itemView);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("end animation");
        }
    }

    private void restoreTransitionParams(View view) {
        view.setAlpha(1f);
        view.setTranslationY(0f);
        if (view instanceof BotHelpCell) {
            BotHelpCell botCell = (BotHelpCell) view;
            int top = recyclerListView.getMeasuredHeight() / 2 - view.getMeasuredHeight() / 2;
            botCell.setAnimating(false);
            if (view.getTop() > top) {
                view.setTranslationY(top - view.getTop());
            } else {
                view.setTranslationY(0);
            }
        } else if (view instanceof ChatMessageCell) {
            ((ChatMessageCell) view).getTransitionParams().resetAnimation();
            ((ChatMessageCell) view).setAnimationOffsetX(0f);
        } else {
            view.setTranslationX(0f);
        }
    }

    @Override
    public void endAnimations() {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("end animations");
        }
        for (MessageObject.GroupedMessages groupedMessages : willChangedGroups) {
            groupedMessages.transitionParams.isNewGroup = false;
        }
        willChangedGroups.clear();
        cancelAnimators();

        if (chatGreetingsView != null) {
            chatGreetingsView.stickerToSendView.setAlpha(1f);
        }
        greetingsSticker = null;
        chatGreetingsView = null;

        int count = mPendingMoves.size();
        for (int i = count - 1; i >= 0; i--) {
            MoveInfo item = mPendingMoves.get(i);
            View view = item.holder.itemView;
            restoreTransitionParams(view);
            dispatchMoveFinished(item.holder);
            mPendingMoves.remove(i);
        }
        count = mPendingRemovals.size();
        for (int i = count - 1; i >= 0; i--) {
            RecyclerView.ViewHolder item = mPendingRemovals.get(i);
            restoreTransitionParams(item.itemView);
            dispatchRemoveFinished(item);
            mPendingRemovals.remove(i);
        }
        count = mPendingAdditions.size();
        for (int i = count - 1; i >= 0; i--) {
            RecyclerView.ViewHolder item = mPendingAdditions.get(i);
            restoreTransitionParams(item.itemView);
            dispatchAddFinished(item);
            mPendingAdditions.remove(i);
        }
        count = mPendingChanges.size();
        for (int i = count - 1; i >= 0; i--) {
            endChangeAnimationIfNecessary(mPendingChanges.get(i));
        }
        mPendingChanges.clear();
        if (!isRunning()) {
            return;
        }

        int listCount = mMovesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            count = moves.size();
            for (int j = count - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                RecyclerView.ViewHolder item = moveInfo.holder;
                restoreTransitionParams(item.itemView);
                dispatchMoveFinished(moveInfo.holder);
                moves.remove(j);
                if (moves.isEmpty()) {
                    mMovesList.remove(moves);
                }
            }
        }
        listCount = mAdditionsList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
            count = additions.size();
            for (int j = count - 1; j >= 0; j--) {
                RecyclerView.ViewHolder item = additions.get(j);
                restoreTransitionParams(item.itemView);
                dispatchAddFinished(item);
                additions.remove(j);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(additions);
                }
            }
        }
        listCount = mChangesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            count = changes.size();
            for (int j = count - 1; j >= 0; j--) {
                endChangeAnimationIfNecessary(changes.get(j));
                if (changes.isEmpty()) {
                    mChangesList.remove(changes);
                }
            }
        }
        cancelAll(mRemoveAnimations);
        cancelAll(mMoveAnimations);
        cancelAll(mAddAnimations);
        cancelAll(mChangeAnimations);

        dispatchAnimationsFinished();
    }

    protected boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, RecyclerView.ViewHolder item) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("end change if necessary");
        }
        Animator a = animators.remove(item);
        if (a != null) {
            a.cancel();
        }

        boolean oldItem = false;
        if (changeInfo.newHolder == item) {
            changeInfo.newHolder = null;
        } else if (changeInfo.oldHolder == item) {
            changeInfo.oldHolder = null;
            oldItem = true;
        } else {
            return false;
        }
        restoreTransitionParams(item.itemView);
        dispatchChangeFinished(item, oldItem);

        return true;
    }

    public void groupWillTransformToSingleMessage(MessageObject.GroupedMessages groupedMessages) {
        willRemovedGroup.put(groupedMessages.messages.get(0).getId(), groupedMessages);
    }

    public void groupWillChanged(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages.messages.size() == 0) {
            groupedMessages.transitionParams.drawBackgroundForDeletedItems = true;
        } else {
            if (groupedMessages.transitionParams.top == 0 && groupedMessages.transitionParams.bottom == 0 && groupedMessages.transitionParams.left == 0 && groupedMessages.transitionParams.right == 0)  {
                int n = recyclerListView.getChildCount();
                for (int i = 0; i < n; i++) {
                    View child = recyclerListView.getChildAt(i);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        MessageObject messageObject = cell.getMessageObject();
                        if (cell.getTransitionParams().wasDraw && groupedMessages.messages.contains(messageObject)) {
                            groupedMessages.transitionParams.top = cell.getTop() +  cell.getBackgroundDrawableTop();
                            groupedMessages.transitionParams.bottom = cell.getTop() +  cell.getBackgroundDrawableBottom();
                            groupedMessages.transitionParams.left = cell.getLeft() + cell.getBackgroundDrawableLeft();
                            groupedMessages.transitionParams.right = cell.getLeft() + cell.getBackgroundDrawableRight();
                            groupedMessages.transitionParams.drawCaptionLayout = cell.hasCaptionLayout();
                            groupedMessages.transitionParams.pinnedTop = cell.isPinnedTop();
                            groupedMessages.transitionParams.pinnedBotton = cell.isPinnedBottom();
                            groupedMessages.transitionParams.isNewGroup = true;
                            break;
                        }
                    }
                }
            }
            willChangedGroups.add(groupedMessages);
        }
    }

    @Override
    public void animateAddImpl(RecyclerView.ViewHolder holder) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate add impl");
        }
        final View view = holder.itemView;
        mAddAnimations.add(holder);
        if (holder == greetingsSticker) {
            view.setAlpha(1f);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1f);

        animatorSet.playTogether(animator);

        if (view instanceof ChatMessageCell) {
            ChatMessageCell cell = (ChatMessageCell) view;
            if (cell.getAnimationOffsetX() != 0) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(cell, cell.ANIMATION_OFFSET_X, cell.getAnimationOffsetX(), 0f)
                );
            }
            view.animate().translationY(0).setDuration(getAddDuration()).start();
        } else {
            view.animate().translationX(0).translationY(0).setDuration(getAddDuration()).start();
        }

        if (view instanceof ChatMessageCell){
            if (holder == greetingsSticker) {
                if (chatGreetingsView != null) {
                    chatGreetingsView.stickerToSendView.setAlpha(0f);
                }
                recyclerListView.setClipChildren(false);
                ChatMessageCell messageCell = (ChatMessageCell) view;
                View parentForGreetingsView = (View)chatGreetingsView.getParent();
                float fromX = chatGreetingsView.stickerToSendView.getX() + chatGreetingsView.getX() + parentForGreetingsView.getX();
                float fromY = chatGreetingsView.stickerToSendView.getY() + chatGreetingsView.getY() + parentForGreetingsView.getY();
                float toX = messageCell.getPhotoImage().getImageX() + recyclerListView.getX() + messageCell.getX();
                float toY = messageCell.getPhotoImage().getImageY() + recyclerListView.getY() + messageCell.getY();
                float fromW = chatGreetingsView.stickerToSendView.getWidth();
                float fromH = chatGreetingsView.stickerToSendView.getHeight();
                float toW = messageCell.getPhotoImage().getImageWidth();
                float toH = messageCell.getPhotoImage().getImageHeight();
                float deltaX = fromX - toX;
                float deltaY = fromY - toY;

                toX = messageCell.getPhotoImage().getImageX();
                toY = messageCell.getPhotoImage().getImageY();

                messageCell.getTransitionParams().imageChangeBoundsTransition = true;
                messageCell.getTransitionParams().animateDrawingTimeAlpha = true;
                messageCell.getPhotoImage().setImageCoords(toX + deltaX, toX + deltaY, fromW,fromH);

                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0,1f);
                float finalToX = toX;
                float finalToY = toY;
                valueAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    messageCell.getTransitionParams().animateChangeProgress = v;
                    if (messageCell.getTransitionParams().animateChangeProgress > 1) {
                        messageCell.getTransitionParams().animateChangeProgress = 1f;
                    }
                    messageCell.getPhotoImage().setImageCoords(
                            finalToX + deltaX * (1f - v),
                            finalToY + deltaY * (1f - v),
                            fromW * (1f - v) + toW * v,
                            fromH * (1f - v) + toH * v);
                    messageCell.invalidate();
                });
                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        messageCell.getTransitionParams().resetAnimation();
                        messageCell.getPhotoImage().setImageCoords(finalToX, finalToY, toW, toH);
                        if (chatGreetingsView != null) {
                            chatGreetingsView.stickerToSendView.setAlpha(1f);
                        }
                        messageCell.invalidate();
                    }
                });
                animatorSet.play(valueAnimator);
            } else {
                MessageObject.GroupedMessages groupedMessages = ((ChatMessageCell) view).getCurrentMessagesGroup();
                if (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds) {
                    animatorSet.setStartDelay(140);
                }
            }
        }

        if (holder == greetingsSticker) {
            animatorSet.setDuration(350);
            animatorSet.setInterpolator(new OvershootInterpolator());
        } else {
            animatorSet.setDuration(getAddDuration());
        }

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchAddStarting(holder);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                view.setAlpha(1);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animator.removeAllListeners();
                view.setAlpha(1f);
                view.setTranslationY(0f);
                view.setTranslationY(0f);
                if (mAddAnimations.remove(holder)) {
                    dispatchAddFinished(holder);
                    dispatchFinishedWhenDone();
                }
            }
        });
        animators.put(holder, animatorSet);
        animatorSet.start();
    }

    protected void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate remove impl");
        }
        final View view = holder.itemView;
        mRemoveAnimations.add(holder);
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f);

        dispatchRemoveStarting(holder);

        animator.setDuration(getRemoveDuration());
        animator.addListener(
                new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animator.removeAllListeners();
                        view.setAlpha(1);
                        view.setTranslationX(0);
                        view.setTranslationY(0);
                        if (mRemoveAnimations.remove(holder)) {
                            dispatchRemoveFinished(holder);
                            dispatchFinishedWhenDone();
                        }
                    }
                });
        animators.put(holder, animator);
        animator.start();
        recyclerListView.stopScroll();
    }

    public void setShouldAnimateEnterFromBottom(boolean shouldAnimateEnterFromBottom) {
        this.shouldAnimateEnterFromBottom = shouldAnimateEnterFromBottom;
    }

    public void onAnimationStart() {

    }

    protected long getMoveAnimationDelay() {
        return 0;
    }

    @Override
    public long getMoveDuration() {
        return 220;
    }

    @Override
    public long getChangeDuration() {
        return 220;
    }

    public void runOnAnimationEnd(Runnable runnable) {
        runOnAnimationsEnd.add(runnable);
    }

    public void onDestroy() {
        onAllAnimationsDone();
    }

    public boolean willRemoved(View view) {
        RecyclerView.ViewHolder holder = recyclerListView.getChildViewHolder(view);
        if (holder != null) {
            return mPendingRemovals.contains(holder) || mRemoveAnimations.contains(holder);
        }
        return false;
    }

    public boolean willAddedFromAlpha(View view) {
        if (shouldAnimateEnterFromBottom) {
            return false;
        }
        RecyclerView.ViewHolder holder = recyclerListView.getChildViewHolder(view);
        if (holder != null) {
            return mPendingAdditions.contains(holder) || mAddAnimations.contains(holder);
        }
        return false;
    }

    public void onGreetingStickerTransition(RecyclerView.ViewHolder holder, ChatGreetingsView greetingsViewContainer) {
        greetingsSticker = holder;
        chatGreetingsView = greetingsViewContainer;
        shouldAnimateEnterFromBottom = false;
    }

    class MoveInfoExtended extends MoveInfo {

        public float captionDeltaX;
        public float captionDeltaY;

        public int groupOffsetTop;
        public int groupOffsetBottom;
        public int groupOffsetLeft;
        public int groupOffsetRight;
        public boolean animateChangeGroupBackground;
        public boolean animatePinnedBottom;
        public boolean animateBackgroundOnly;
        public boolean animateChangeInternal;

        boolean animateImage;
        boolean drawBackground;

        float imageX;
        float imageY;
        float imageWidth;
        float imageHeight;

        int deltaLeft, deltaRight, deltaTop, deltaBottom;
        boolean animateRemoveGroup;

        MoveInfoExtended(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            super(holder, fromX, fromY, toX, toY);
        }
    }

    class ItemHolderInfoExtended extends ItemHolderInfo {
        float imageX;
        float imageY;
        float imageWidth;
        float imageHeight;
        int captionX;
        int captionY;
    }
}

