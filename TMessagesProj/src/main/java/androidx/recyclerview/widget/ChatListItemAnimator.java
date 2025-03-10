package androidx.recyclerview.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.UserInfoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatGreetingsView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.TextMessageEnterTransition;
import org.telegram.ui.VoiceMessageEnterTransition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ChatListItemAnimator extends DefaultItemAnimator {

    public static final long DEFAULT_DURATION = 250;
    public static final Interpolator DEFAULT_INTERPOLATOR = new CubicBezierInterpolator(0.19919472913616398, 0.010644531250000006, 0.27920937042459737, 0.91025390625);

    @Nullable
    private final ChatActivity activity;
    private final RecyclerListView recyclerListView;

    private HashMap<Integer, MessageObject.GroupedMessages> willRemovedGroup = new HashMap<>();
    private ArrayList<MessageObject.GroupedMessages> willChangedGroups = new ArrayList<>();

    HashMap<RecyclerView.ViewHolder,Animator> animators = new HashMap<>();
    ArrayList<View> thanosViews = new ArrayList<>();

    ArrayList<Runnable> runOnAnimationsEnd = new ArrayList<>();
    HashMap<Long, Long> groupIdToEnterDelay = new HashMap<>();

    private boolean shouldAnimateEnterFromBottom;
    private RecyclerView.ViewHolder greetingsSticker;
    private ChatGreetingsView chatGreetingsView;

    private boolean reversePositions;
    private final Theme.ResourcesProvider resourcesProvider;

    public ChatListItemAnimator(ChatActivity activity, RecyclerListView listView, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.activity = activity;
        this.recyclerListView = listView;
        translationInterpolator = DEFAULT_INTERPOLATOR;
        alwaysCreateMoveAnimationIfPossible = true;
        setSupportsChangeAnimations(false);
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
                if (reversePositions) {
                    int itemCount = recyclerListView.getAdapter() == null ? 0 : recyclerListView.getAdapter().getItemCount();
                    if (mPendingAdditions.get(i).getLayoutPosition() == itemCount - 1) {
                        runTranslationFromBottom = true;
                    }
                } else {
                    if (mPendingAdditions.get(i).getLayoutPosition() == 0) {
                        runTranslationFromBottom = true;
                    }
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
            if (activity != null) {
                activity.onListItemAnimatorTick();
            } else {
                recyclerListView.invalidate();
            }
        });
        valueAnimator.setDuration(getRemoveDuration() + getMoveDuration());
        valueAnimator.start();
    }

    long alphaEnterDelay;

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
        boolean hadThanos = false;
        final boolean supportsThanos = getThanosEffectContainer != null && supportsThanosEffectContainer != null && supportsThanosEffectContainer.run();
        if (supportsThanos) {
            LongSparseArray<ArrayList<RecyclerView.ViewHolder>> groupsToRemoveWithThanos = null;
            for (int i = 0; i < mPendingRemovals.size(); ++i) {
                RecyclerView.ViewHolder holder = mPendingRemovals.get(i);
                if (toBeSnapped.contains(holder) && holder.itemView instanceof ChatMessageCell && ((ChatMessageCell) holder.itemView).getCurrentMessagesGroup() != null) {
                    MessageObject msg = ((ChatMessageCell) holder.itemView).getMessageObject();
                    if (msg != null && msg.getGroupId() != 0) {
                        if (groupsToRemoveWithThanos == null) {
                            groupsToRemoveWithThanos = new LongSparseArray<>();
                        }
                        ArrayList<RecyclerView.ViewHolder> holders = groupsToRemoveWithThanos.get(msg.getGroupId());
                        if (holders == null) {
                            groupsToRemoveWithThanos.put(msg.getGroupId(), holders = new ArrayList<>());
                        }
                        toBeSnapped.remove(holder);
                        mPendingRemovals.remove(i);
                        i--;
                        holders.add(holder);
                    }
                }
            }
            if (groupsToRemoveWithThanos != null) {
                for (int i = 0; i < groupsToRemoveWithThanos.size(); ++i) {
                    // check whether we remove the whole group
                    ArrayList<RecyclerView.ViewHolder> holders = groupsToRemoveWithThanos.valueAt(i);
                    if (holders.size() <= 0) continue;
                    boolean wholeGroup = true;
                    RecyclerView.ViewHolder firstHolder = holders.get(0);
                    if (firstHolder.itemView instanceof ChatMessageCell) {
                        MessageObject.GroupedMessages group = ((ChatMessageCell) firstHolder.itemView).getCurrentMessagesGroup();
                        if (group != null) {
                            wholeGroup = group.messages.size() <= holders.size();
                        }
                    }
                    if (!wholeGroup) {
                        // not whole group, fallback to prev animation
                        mPendingRemovals.addAll(holders);
                    } else {
                        animateRemoveGroupImpl(holders);
                        hadThanos = true;
                    }
                }
            }
        }
        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
            boolean thanos = toBeSnapped.remove(holder) && supportsThanos;
            animateRemoveImpl(holder, thanos);
            if (thanos) {
                hadThanos = true;
            }
        }
        final boolean finalThanos = hadThanos;
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
                        animateMoveImpl(moveInfo.holder, moveInfo, finalThanos);
                    }
                    moves.clear();
                    mMovesList.remove(moves);
                }
            };
            if (delayAnimations && removalsPending) {
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, hadThanos ? 0 : getMoveAnimationDelay());
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

            alphaEnterDelay = 0;
            Collections.sort(additions, (i1, i2) -> i2.itemView.getTop() - i1.itemView.getTop());
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

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        holder.itemView.setAlpha(0);
        if (!shouldAnimateEnterFromBottom) {
            holder.itemView.setScaleX(0.9f);
            holder.itemView.setScaleY(0.9f);
        } else {
            if (holder.itemView instanceof ChatMessageCell) {
                ((ChatMessageCell) holder.itemView).getTransitionParams().messageEntering = true;
            }
        }
        mPendingAdditions.add(holder);
        return true;
    }

    public void animateAddImpl(final RecyclerView.ViewHolder holder, int addedItemsHeight) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);
        view.setTranslationY(addedItemsHeight);
        holder.itemView.setScaleX(1);
        holder.itemView.setScaleY(1);
        ChatMessageCell chatMessageCell = holder.itemView instanceof ChatMessageCell ? (ChatMessageCell) holder.itemView : null;
        if (!(chatMessageCell != null && chatMessageCell.getTransitionParams().ignoreAlpha)) {
            holder.itemView.setAlpha(1);
        }
        if (activity != null && chatMessageCell != null && activity.animatingMessageObjects.contains(chatMessageCell.getMessageObject())) {
            activity.animatingMessageObjects.remove(chatMessageCell.getMessageObject());
            if (activity.getChatActivityEnterView().canShowMessageTransition()) {
                if (chatMessageCell.getMessageObject().isVoice()) {
                    if (Math.abs(view.getTranslationY()) < view.getMeasuredHeight() * 3f) {
                        VoiceMessageEnterTransition transition = new VoiceMessageEnterTransition(chatMessageCell, activity.getChatActivityEnterView(), recyclerListView, activity.messageEnterTransitionContainer, resourcesProvider);
                        transition.start();
                    }
                } else {
                    if (SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW && Math.abs(view.getTranslationY()) < recyclerListView.getMeasuredHeight()) {
                        TextMessageEnterTransition transition = new TextMessageEnterTransition(chatMessageCell, activity, recyclerListView, activity.messageEnterTransitionContainer, resourcesProvider);
                        transition.start();
                    }
                }
                activity.getChatActivityEnterView().startMessageTransition();
            }
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
                        if (view instanceof ChatMessageCell) {
                            ((ChatMessageCell) view).getTransitionParams().messageEntering = false;
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        if (view instanceof ChatMessageCell) {
                            ((ChatMessageCell) view).getTransitionParams().messageEntering = false;
                        }
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
        final View view = holder.itemView;
        ChatMessageCell chatMessageCell = null;
        ChatActionCell chatActionCell = null;
        if (holder.itemView instanceof ChatMessageCell) {
            chatMessageCell = ((ChatMessageCell) holder.itemView);
            fromX += (int) chatMessageCell.getAnimationOffsetX();
            if (chatMessageCell.getTransitionParams().lastTopOffset != chatMessageCell.getTopMediaOffset()) {
                fromY += chatMessageCell.getTransitionParams().lastTopOffset - chatMessageCell.getTopMediaOffset();
            }
        } else if (holder.itemView instanceof ChatActionCell) {
            chatActionCell = ((ChatActionCell) holder.itemView);
            fromX += (int) holder.itemView.getTranslationX();
        } else {
            fromX += (int) holder.itemView.getTranslationX();
        }
        fromY += (int) holder.itemView.getTranslationY();
        float imageX = 0;
        float imageY = 0;
        float imageW = 0;
        float imageH = 0;
        int[] roundRadius = new int[4];
        if (chatMessageCell != null) {
            imageX = chatMessageCell.getPhotoImage().getImageX();
            imageY = chatMessageCell.getPhotoImage().getImageY();
            imageW = chatMessageCell.getPhotoImage().getImageWidth();
            imageH = chatMessageCell.getPhotoImage().getImageHeight();
            for (int i = 0; i < 4; i++) {
                roundRadius[i] = chatMessageCell.getPhotoImage().getRoundRadius()[i];
            }
        }
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }

        MoveInfoExtended moveInfo = new MoveInfoExtended(holder, fromX, fromY, toX, toY);

        if (chatMessageCell != null) {
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
                checkIsRunning();
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
                    if (chatMessageCell.getMessageObject().isRoundVideo()) {
                        params.animateToImageX = imageX;
                        params.animateToImageY = imageY;
                        params.animateToImageW = imageW;
                        params.animateToImageH = imageH;
                        params.animateToRadius = roundRadius;
                    } else {
                        params.animateToImageX = newImage.getImageX();
                        params.animateToImageY = newImage.getImageY();
                        params.animateToImageW = newImage.getImageWidth();
                        params.animateToImageH = newImage.getImageHeight();
                        params.animateToRadius = newImage.getRoundRadius();
                    }

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
                            if (params.animateToRadius == newImage.getRoundRadius()) {
                                params.animateToRadius = new int[4];
                                for (int i = 0; i < 4; i++) {
                                    params.animateToRadius[i] = newImage.getRoundRadius()[i];
                                }
                            }
                            newImage.setRoundRadius(params.imageRoundRadius);
                        }
                        chatMessageCell.setImageCoords(moveInfo.imageX, moveInfo.imageY, moveInfo.imageWidth, moveInfo.imageHeight);
                    }
                }

                if (group == null && params.wasDraw) {
                    boolean isOut = chatMessageCell.getMessageObject().isOutOwner();
                    boolean widthChanged = (isOut && params.lastDrawingBackgroundRect.left != chatMessageCell.getBackgroundDrawableLeft()) ||
                            (!isOut && params.lastDrawingBackgroundRect.right != chatMessageCell.getBackgroundDrawableRight());
                    if (widthChanged ||
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
                        params.animateBackgroundWidth = widthChanged;
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
                                int top = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
                                int bottom = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();

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
                    int animateToTop = chatMessageCell.getTop() + chatMessageCell.getPaddingTop() + chatMessageCell.getBackgroundDrawableTop();
                    int animateToBottom = chatMessageCell.getTop() + chatMessageCell.getPaddingTop() + chatMessageCell.getBackgroundDrawableBottom();

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

            moveInfo.animateChangeInternal = params.animateChange();
            if (moveInfo.animateChangeInternal) {
                params.animateChange = true;
                params.animateChangeProgress = 0f;
            }

            if (deltaX == 0 && deltaY == 0 && !moveInfo.animateImage && !moveInfo.animateRemoveGroup && !moveInfo.animateChangeGroupBackground && !moveInfo.animatePinnedBottom && !moveInfo.animateBackgroundOnly && !moveInfo.animateChangeInternal) {
                dispatchMoveFinished(holder);
                return false;
            }
        } else if (chatActionCell != null) {
            ChatActionCell.TransitionParams params = chatActionCell.getTransitionParams();

            if (!params.supportChangeAnimation()) {
                if (deltaX == 0 && deltaY == 0) {
                    dispatchMoveFinished(holder);
                    return false;
                }
                if (deltaX != 0) {
                    view.setTranslationX(-deltaX);
                }
                mPendingMoves.add(moveInfo);
                checkIsRunning();
                return true;
            }

            if (deltaX != 0) {
                view.setTranslationX(-deltaX);
            }

            moveInfo.animateChangeInternal = params.animateChange();
            if (moveInfo.animateChangeInternal) {
                params.animateChange = true;
                params.animateChangeProgress = 0f;
            }

            if (deltaX == 0 && deltaY == 0 && !moveInfo.animateChangeInternal) {
                dispatchMoveFinished(holder);
                return false;
            }
        } else if (holder.itemView instanceof BotHelpCell) {
            BotHelpCell botInfo = (BotHelpCell) holder.itemView;
            botInfo.setAnimating(true);
        } else if (holder.itemView instanceof UserInfoCell) {
            UserInfoCell cell = (UserInfoCell) holder.itemView;
            cell.setAnimating(true);
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
        checkIsRunning();
        return true;
    }

    @Override
    protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
        animateMoveImpl(holder, moveInfo, false);
    }
    protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo, boolean withThanos) {
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

        if (activity != null && holder.itemView instanceof BotHelpCell) {
            BotHelpCell botCell = (BotHelpCell) holder.itemView;
            float animateFrom = botCell.getTranslationY();

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float v = (float) valueAnimator.getAnimatedValue();
                    float top = (recyclerListView.getMeasuredHeight() - activity.getChatListViewPadding() - activity.blurredViewBottomOffset) / 2f - botCell.getMeasuredHeight() / 2f + activity.getChatListViewPadding();
                    float animateTo = 0;
                    if (botCell.getTop() > top) {
                        animateTo = top - botCell.getTop();
                    }
                    botCell.setTranslationY(animateFrom * (1f - v) + animateTo * v);
                }
            });
            animatorSet.playTogether(valueAnimator);
        } else if (activity != null && holder.itemView instanceof UserInfoCell) {
            UserInfoCell cell = (UserInfoCell) holder.itemView ;
            float animateFrom = cell.getTranslationY();

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float v = (float) valueAnimator.getAnimatedValue();
                    float top = (recyclerListView.getMeasuredHeight() - activity.getChatListViewPadding() - activity.blurredViewBottomOffset) / 2f - cell.getMeasuredHeight() / 2f + activity.getChatListViewPadding();
                    float animateTo = 0;
                    if (cell.getTop() > top) {
                        animateTo = top - cell.getTop();
                    }
                    cell.setTranslationY(animateFrom * (1f - v) + animateTo * v);
                }
            });
            animatorSet.playTogether(valueAnimator);
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
                animatorSet.playTogether(valueAnimator);
            }
            if (moveInfoExtended.deltaBottom != 0 || moveInfoExtended.deltaRight != 0 || moveInfoExtended.deltaTop != 0 || moveInfoExtended.deltaLeft != 0) {

                recyclerListView.setClipChildren(false);
                recyclerListView.invalidate();

                ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
                if (moveInfoExtended.animateBackgroundOnly) {
                    params.toDeltaLeft = -moveInfoExtended.deltaLeft;
                    params.toDeltaRight = -moveInfoExtended.deltaRight;
                } else {
                    params.toDeltaLeft = -moveInfoExtended.deltaLeft - chatMessageCell.getAnimationOffsetX();
                    params.toDeltaRight = -moveInfoExtended.deltaRight - chatMessageCell.getAnimationOffsetX();
                }
                valueAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    if (moveInfoExtended.animateBackgroundOnly) {
                        params.deltaLeft = -moveInfoExtended.deltaLeft * v;
                        params.deltaRight = -moveInfoExtended.deltaRight * v;
                        params.deltaTop = -moveInfoExtended.deltaTop * v;
                        params.deltaBottom = -moveInfoExtended.deltaBottom * v;
                    } else {
                        params.deltaLeft = -moveInfoExtended.deltaLeft * v - chatMessageCell.getAnimationOffsetX();
                        params.deltaRight = -moveInfoExtended.deltaRight * v - chatMessageCell.getAnimationOffsetX();
                        params.deltaTop = -moveInfoExtended.deltaTop * v - chatMessageCell.getTranslationY();
                        params.deltaBottom = -moveInfoExtended.deltaBottom * v - chatMessageCell.getTranslationY();
                    }
                    chatMessageCell.invalidate();
                });
                animatorSet.playTogether(valueAnimator);
            } else {
                params.toDeltaLeft = 0;
                params.toDeltaRight = 0;
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
                    if (recyclerListView != null) {
                        recyclerListView.invalidate();
                    }
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
                params.animateChange = true;
                valueAnimator.addUpdateListener(animation -> {
                    params.animateChangeProgress = (float) animation.getAnimatedValue();
                    chatMessageCell.invalidate();
                });
                animatorSet.playTogether(valueAnimator);
            }
        } else if (holder.itemView instanceof ChatActionCell) {
            ChatActionCell chatActionCell = (ChatActionCell) holder.itemView;
            ChatActionCell.TransitionParams params = chatActionCell.getTransitionParams();

            if (moveInfoExtended.animateChangeInternal) {
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                params.animateChange = true;
                valueAnimator.addUpdateListener(animation -> {
                    params.animateChangeProgress = (float) animation.getAnimatedValue();
                    chatActionCell.invalidate();
                });
                animatorSet.playTogether(valueAnimator);
            }
        }

        if (withThanos) {
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        } else if (translationInterpolator != null) {
            animatorSet.setInterpolator(translationInterpolator);
        }
        animatorSet.setDuration((long) (getMoveDuration() * (withThanos ? 1.9f : 1f)));
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
                    ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                    if (cell.makeVisibleAfterChange) {
                        cell.makeVisibleAfterChange = false;
                        cell.setVisibility(View.VISIBLE);
                    }
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
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

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, ItemHolderInfo info,
                                 int fromX, int fromY, int toX, int toY) {
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
        checkIsRunning();
        return true;
    }

    public void animateChangeImpl(final ChangeInfo changeInfo) {
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
                    view.setScaleX(1f);
                    view.setScaleX(1f);
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
                    newView.setScaleX(1f);
                    newView.setScaleX(1f);
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

        recyclerListView.setClipChildren(true);
        while (!runOnAnimationsEnd.isEmpty()) {
            runOnAnimationsEnd.remove(0).run();
        }
        cancelAnimators();
    }

    private void cancelAnimators() {
        ArrayList<Animator> anim = new ArrayList<>(animators.values());
        animators.clear();
        for (Animator animator : anim) {
            if (animator != null) {
                animator.cancel();
            }
        }
        if (!thanosViews.isEmpty()) {
            ThanosEffect thanosEffect = getThanosEffectContainer.run();
            if (thanosEffect != null) {
                thanosEffect.kill();
            }
        }
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
        Animator animator = animators.remove(item);
        if (animator != null) {
            animator.cancel();
        }
        if (thanosViews.contains(item.itemView)) {
            ThanosEffect thanosEffect = getThanosEffectContainer.run();
            if (thanosEffect != null) {
                thanosEffect.cancel(item.itemView);
            }
        }
        super.endAnimation(item);
        restoreTransitionParams(item.itemView);
    }

    private void restoreTransitionParams(View view) {
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
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
        } else if (view instanceof UserInfoCell) {
            UserInfoCell cell = (UserInfoCell) view;
            int top = recyclerListView.getMeasuredHeight() / 2 - view.getMeasuredHeight() / 2;
            cell.setAnimating(false);
            if (view.getTop() > top) {
                view.setTranslationY(top - view.getTop());
            } else {
                view.setTranslationY(0);
            }
        } else if (view instanceof ChatMessageCell) {
            ((ChatMessageCell) view).getTransitionParams().resetAnimation();
            ((ChatMessageCell) view).setAnimationOffsetX(0f);
        } else if (view instanceof ChatActionCell) {
            ((ChatActionCell) view).getTransitionParams().resetAnimation();
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
        if (thanosViews.contains(item.itemView)) {
            ThanosEffect thanosEffect = getThanosEffectContainer.run();
            if (thanosEffect != null) {
                thanosEffect.cancel(item.itemView);
            }
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
        if (groupedMessages == null) {
            return;
        }
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
                            groupedMessages.transitionParams.top = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
                            groupedMessages.transitionParams.bottom = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();
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

        if (view instanceof ChatMessageCell) {
            ChatMessageCell cell = (ChatMessageCell) view;
            if (cell.getAnimationOffsetX() != 0) {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(cell, cell.ANIMATION_OFFSET_X, cell.getAnimationOffsetX(), 0f)
                );
            }
            float pivotX = cell.getBackgroundDrawableLeft() + (cell.getBackgroundDrawableRight() - cell.getBackgroundDrawableLeft()) / 2f;
            cell.setPivotX(pivotX);
            view.animate().translationY(0).setDuration(getAddDuration()).start();
        } else {
            view.animate().translationX(0).translationY(0).setDuration(getAddDuration()).start();
        }

        boolean useScale = true;
        long currentDelay = (long) ((1f - Math.max(0, Math.min(1f, view.getBottom() / (float) recyclerListView.getMeasuredHeight()))) * 100);

        if (view instanceof ChatMessageCell){
            if (holder == greetingsSticker) {
                useScale = false;
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
                if (groupedMessages != null) {

                    Long groupDelay = groupIdToEnterDelay.get(groupedMessages.groupId);
                    if (groupDelay == null) {
                        groupIdToEnterDelay.put(groupedMessages.groupId, currentDelay);
                    } else {
                        currentDelay = groupDelay;
                    }
                }
                if (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds) {
                    animatorSet.setStartDelay(140);
                }
            }
        }

        view.setAlpha(0f);
        animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1f));
        if (useScale) {
            view.setScaleX(0.9f);
            view.setScaleY(0.9f);
            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleY(), 1f));
            animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 1f));
        } else {
            view.setScaleX(1f);
            view.setScaleY(1f);
        }

        if (holder == greetingsSticker) {
            animatorSet.setDuration(350);
            animatorSet.setInterpolator(new OvershootInterpolator());
        } else {
            animatorSet.setStartDelay(currentDelay);
            animatorSet.setDuration(DEFAULT_DURATION);
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
                view.setScaleX(1f);
                view.setScaleY(1f);
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

    protected void animateRemoveImpl(final RecyclerView.ViewHolder holder, boolean thanos) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate remove impl " + (thanos ? " with thanos" : ""));
        }
        final View view = holder.itemView;
        mRemoveAnimations.add(holder);
        if (thanos && getThanosEffectContainer != null) {
            ThanosEffect thanosEffect = getThanosEffectContainer.run();
            dispatchRemoveStarting(holder);
            thanosEffect.animate(view, () -> {
                view.setVisibility(View.VISIBLE);
                if (mRemoveAnimations.remove(holder)) {
                    dispatchRemoveFinished(holder);
                    dispatchFinishedWhenDone();
                }
                thanosViews.remove(view);
            });
            thanosViews.add(view);
        } else {
            ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f);
            dispatchRemoveStarting(holder);
            animator.setDuration(getRemoveDuration());
            animator.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            animator.removeAllListeners();
                            view.setAlpha(1);
                            view.setScaleX(1f);
                            view.setScaleY(1f);
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
        }
        recyclerListView.stopScroll();
    }

    private void animateRemoveGroupImpl(final ArrayList<RecyclerView.ViewHolder> holders) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("animate remove group impl with thanos");
        }
        mRemoveAnimations.addAll(holders);
        ThanosEffect thanosEffect = getThanosEffectContainer.run();
        for (int i = 0; i < holders.size(); ++i) {
            dispatchRemoveStarting(holders.get(i));
        }
        final ArrayList<View> views = new ArrayList<>();
        for (int i = 0; i < holders.size(); ++i) {
            views.add(holders.get(i).itemView);
        }
        thanosEffect.animateGroup(views, () -> {
            for (int i = 0; i < views.size(); ++i) {
                views.get(i).setVisibility(View.VISIBLE);
            }
            if (mRemoveAnimations.removeAll(holders)) {
                for (int i = 0; i < holders.size(); ++i) {
                    dispatchRemoveFinished(holders.get(i));
                }
                dispatchFinishedWhenDone();
            }
            thanosViews.removeAll(views);
        });
        thanosViews.add(views.get(0));
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
        return DEFAULT_DURATION;
    }

    @Override
    public long getChangeDuration() {
        return DEFAULT_DURATION;
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

    public void setReversePositions(boolean reversePositions) {
        this.reversePositions = reversePositions;
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

    private final ArrayList<RecyclerView.ViewHolder> toBeSnapped = new ArrayList<>();
    public void prepareThanos(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder == null) return;
        toBeSnapped.add(viewHolder);
        if (viewHolder.itemView instanceof ChatMessageCell) {
            MessageObject msg = ((ChatMessageCell) viewHolder.itemView).getMessageObject();
            if (msg != null) {
                msg.deletedByThanos = true;
            }
        }
    }

    private Utilities.Callback0Return<Boolean> supportsThanosEffectContainer;
    private Utilities.Callback0Return<ThanosEffect> getThanosEffectContainer;
    public void setOnSnapMessage(
        Utilities.Callback0Return<Boolean> supportsThanosEffectContainer,
        Utilities.Callback0Return<ThanosEffect> getThanosEffectContainer
    ) {
        this.supportsThanosEffectContainer = supportsThanosEffectContainer;
        this.getThanosEffectContainer = getThanosEffectContainer;
    }
}

