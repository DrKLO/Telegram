package org.telegram.ui.Components.Reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Collections;
import java.util.List;

public class ChatSelectionReactionMenuOverlay extends FrameLayout {
    private ChatActivity parentFragment;
    private ReactionsContainerLayout reactionsContainerLayout;

    private List<MessageObject> selectedMessages = Collections.emptyList();
    private boolean isVisible;
    private MessageObject currentPrimaryObject;

    private int mPadding = 22;
    private int mSidePadding = 24;

    private float currentOffsetY;
    private float toOffsetY;
    private float translationOffsetY;
    private long lastUpdate;
    private boolean hiddenByScroll;

    private boolean messageSet;

    public ChatSelectionReactionMenuOverlay(ChatActivity fragment, Context context) {
        super(context);
        setVisibility(GONE);

        this.parentFragment = fragment;

        setClipToPadding(false);
        setClipChildren(false);

        fragment.getChatListView().addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                invalidatePosition();
            }
        });
    }

    private void checkCreateReactionsLayout() {
        if (reactionsContainerLayout == null) {
            final boolean tags = parentFragment.getUserConfig().getClientUserId() == parentFragment.getDialogId();

            reactionsContainerLayout = new ReactionsContainerLayout(tags ? ReactionsContainerLayout.TYPE_TAGS : ReactionsContainerLayout.TYPE_DEFAULT, parentFragment, getContext(), parentFragment.getCurrentAccount(), parentFragment.getResourceProvider()) {
                float enabledAlpha = 1f;
                long lastUpdate;

                {
                    setWillNotDraw(false);
                }

                @Override
                public void draw(Canvas canvas) {
                    long dt = Math.min(16, System.currentTimeMillis() - lastUpdate);
                    lastUpdate = System.currentTimeMillis();

                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (0xFF * enabledAlpha), Canvas.ALL_SAVE_FLAG);
                    super.draw(canvas);
                    canvas.restore();

                    if (!isEnabled() && enabledAlpha != 0f) {
                        enabledAlpha = Math.max(0, enabledAlpha - dt / 150f);
                        invalidate();

                        if (enabledAlpha == 0) {
                            setVisibility(GONE);
                        }
                    } else if (isEnabled() && enabledAlpha != 1f) {
                        enabledAlpha = Math.min(1, enabledAlpha + dt / 150f);
                        invalidate();
                    }
                }

                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);

                    if (visibility == View.GONE && enabledAlpha != 0) {
                        enabledAlpha = 0;
                    }
                }
            };
            reactionsContainerLayout.setPadding(AndroidUtilities.dp(4) + (LocaleController.isRTL ? 0 : mSidePadding), AndroidUtilities.dp(4), AndroidUtilities.dp(4) + (LocaleController.isRTL ? mSidePadding : 0), AndroidUtilities.dp(mPadding));
            reactionsContainerLayout.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
                @Override
                public void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                    parentFragment.selectReaction(currentPrimaryObject, reactionsContainerLayout, view, 0, 0, visibleReaction, false, longpress, addToRecent, false);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (reactionsContainerLayout != null) {
                            reactionsContainerLayout.dismissParent(true);
                        }
                        hideMenu();
                    });
                }

                @Override
                public void hideMenu() {
                    parentFragment.clearSelectionMode(true);
                }
            });
            reactionsContainerLayout.setClipChildren(false);
            reactionsContainerLayout.setClipToPadding(false);
            addView(reactionsContainerLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 70 + mPadding, Gravity.RIGHT));
        }
    }

    public boolean isVisible() {
        return isVisible && !hiddenByScroll;
    }

    public void invalidatePosition() {
        invalidatePosition(true);
    }

    private int[] pos = new int[2];
    public void invalidatePosition(boolean animate) {
        if (!isVisible || currentPrimaryObject == null || reactionsContainerLayout == null) {
            return;
        }

        long dt = Math.min(16, System.currentTimeMillis() - lastUpdate);
        lastUpdate = System.currentTimeMillis();
        if (currentOffsetY != toOffsetY) {
            float a = dt / 220f;
            if (toOffsetY > currentOffsetY) {
                currentOffsetY = Math.min(currentOffsetY + a, toOffsetY);
            } else if (toOffsetY < currentOffsetY) {
                currentOffsetY = Math.max(currentOffsetY - a, toOffsetY);
            }
            AndroidUtilities.runOnUIThread(this::invalidatePosition);
        }

        RecyclerListView listView = parentFragment.getChatListView();
        listView.getLocationInWindow(pos);
        float listY = pos[1];
        getLocationInWindow(pos);
        float offsetY = listY - pos[1] - parentFragment.getPullingDownOffset();

        for (int i = 0; i < listView.getChildCount(); i++) {
            View ch = listView.getChildAt(i);
            if (ch instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) ch;

                MessageObject obj = cell.getMessageObject();
                if (obj.getId() == currentPrimaryObject.getId()) {
                    boolean mirrorX = obj.isOutOwner();
                    if (reactionsContainerLayout != null) {
                        reactionsContainerLayout.setMirrorX(mirrorX);
                        reactionsContainerLayout.setPadding(AndroidUtilities.dp(4) + (LocaleController.isRTL || mirrorX ? 0 : mSidePadding), AndroidUtilities.dp(mPadding), AndroidUtilities.dp(4) + (LocaleController.isRTL || mirrorX ? mSidePadding : 0), AndroidUtilities.dp(mPadding));
                    }
                    int height = getHeight() != 0 ? getHeight() : listView.getHeight();
                    int groupHeight;

                    if (cell.getCurrentMessagesGroup() != null) {
                        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                        groupHeight = group.transitionParams.bottom - group.transitionParams.top;
                    } else {
                        groupHeight = cell.getHeight();
                    }

                    float y = cell.getY() + offsetY - AndroidUtilities.dp(74);
                    float min = AndroidUtilities.dp(14), max = height - AndroidUtilities.dp(218);
                    FragmentContextView fragmentContextView = parentFragment.getFragmentContextView();
                    if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                        min += fragmentContextView.getHeight();
                    }
                    boolean newVisibleOffset;
                    boolean flippedVertically;
                    if (y > min - groupHeight / 2f && y < max) {
                        newVisibleOffset = true;
                        flippedVertically = false;
                        toOffsetY = 0f;
                    } else if (y < min - groupHeight - AndroidUtilities.dp(92) || y > max) {
                        newVisibleOffset = false;
                        flippedVertically = false;
                    } else {
                        newVisibleOffset = true;
                        translationOffsetY = groupHeight + AndroidUtilities.dp(56);
                        flippedVertically = true;
                        toOffsetY = 1f;
                    }
                    if (!animate) {
                        currentOffsetY = toOffsetY;
                    }

                    y += CubicBezierInterpolator.DEFAULT.getInterpolation(currentOffsetY) * translationOffsetY;
                    if (reactionsContainerLayout == null) {
                        return;
                    }
                    if (flippedVertically != reactionsContainerLayout.isFlippedVertically()) {
                        reactionsContainerLayout.setFlippedVertically(flippedVertically);
                        AndroidUtilities.runOnUIThread(this::invalidatePosition);
                    }
                    if (newVisibleOffset != reactionsContainerLayout.isEnabled()) {
                        reactionsContainerLayout.setEnabled(newVisibleOffset);
                        reactionsContainerLayout.invalidate();
                        if (newVisibleOffset) {
                            reactionsContainerLayout.setVisibility(VISIBLE);
                            if (!messageSet) {
                                messageSet = true;
                                reactionsContainerLayout.setMessage(currentPrimaryObject, parentFragment.getCurrentChatInfo(), true);
                            }
                        }
                    }
                    reactionsContainerLayout.setTranslationY(MathUtils.clamp(y, min, max));
                    reactionsContainerLayout.setTranslationX(cell.getNonAnimationTranslationX(true));

                    boolean invalidate = false;
                    LayoutParams params = (LayoutParams) reactionsContainerLayout.getLayoutParams();
                    int left = Math.max(0, cell.getBackgroundDrawableLeft() - AndroidUtilities.dp(32));
                    int right = Math.max((int) cell.getNonAnimationTranslationX(true), cell.getWidth() - cell.getBackgroundDrawableRight() - AndroidUtilities.dp(32));

                    int minWidth = AndroidUtilities.dp(40) * 8;
                    if (getWidth() - right - left < minWidth) {
                        if (mirrorX) {
                            right = 0;
                            left = Math.min(left, getWidth() - right - minWidth);
                        } else {
                            left = 0;
                            right = Math.min(right, getWidth() - left - minWidth);
                        }
                    }

                    int gravity = mirrorX ? Gravity.RIGHT : Gravity.LEFT;
                    if (gravity != params.gravity) {
                        params.gravity = gravity;
                        invalidate = true;
                    }
                    if (left != params.leftMargin) {
                        params.leftMargin = left;
                        invalidate = true;
                    }
                    if (right != params.rightMargin) {
                        params.rightMargin = right;
                        invalidate = true;
                    }
                    if (invalidate) {
                        reactionsContainerLayout.requestLayout();
                    }
                    return;
                }
            }
        }

        if (reactionsContainerLayout != null && reactionsContainerLayout.isEnabled()) {
            reactionsContainerLayout.setEnabled(false);
        }
    }

    private MessageObject findPrimaryObject() {
        if (isVisible && !selectedMessages.isEmpty()) {
            MessageObject msg = selectedMessages.get(0);

            if (msg.getGroupId() != 0) {
                MessageObject.GroupedMessages groupedMessages = parentFragment.getGroup(msg.getGroupId());
                if (groupedMessages != null && groupedMessages.messages != null) {
                    for (MessageObject obj : groupedMessages.messages) {
                        if (obj.messageOwner != null && obj.messageOwner.reactions != null && obj.messageOwner.reactions.results != null &&
                                !obj.messageOwner.reactions.results.isEmpty()) {
                            return obj;
                        }
                    }
                }
            }

            return msg;
        }
        return null;
    }

    private boolean isMessageTypeAllowed(MessageObject obj) {
        return obj != null && !obj.needDrawBluredPreview() && (
            MessageObject.isPhoto(obj.messageOwner) && MessageObject.getMedia(obj.messageOwner).webpage == null ||
            obj.getDocument() != null && (
                MessageObject.isVideoDocument(obj.getDocument()) ||
                MessageObject.isGifDocument(obj.getDocument())
            )
        );
    }

    public void setSelectedMessages(List<MessageObject> messages) {
        this.selectedMessages = messages;

        boolean visible = false;

        if (parentFragment.isSecretChat() || parentFragment.getCurrentChatInfo() != null && parentFragment.getCurrentChatInfo().available_reactions instanceof TLRPC.TL_chatReactionsNone) {
            visible = false;
        } else if (!messages.isEmpty()) {
            visible = true;

            boolean hasGroupId = false;
            long groupId = 0;
            for (MessageObject obj : messages) {
                if (!isMessageTypeAllowed(obj)) {
                    visible = false;
                    break;
                }
                if (!hasGroupId) {
                    hasGroupId = true;
                    groupId = obj.getGroupId();
                } else if (groupId != obj.getGroupId() || groupId == 0) {
                    visible = false;
                    break;
                }
            }
        }

        if (visible != isVisible) {
            isVisible = visible;
            hiddenByScroll = false;
            animateVisible(visible);
        } else if (visible) {
            currentPrimaryObject = findPrimaryObject();
        }
    }

    private void animateVisible(boolean visible) {
        if (visible) {
            setVisibility(VISIBLE);
            post(() -> {
                currentPrimaryObject = findPrimaryObject();
                checkCreateReactionsLayout();
                invalidatePosition(false);

                if (reactionsContainerLayout.isEnabled()) {
                    messageSet = true;
                    reactionsContainerLayout.setMessage(currentPrimaryObject, parentFragment.getCurrentChatInfo(), true);
                    reactionsContainerLayout.startEnterAnimation(false);
                } else {
                    messageSet = false;
                    reactionsContainerLayout.setTransitionProgress(1f);
                }
            });
        } else {
            messageSet = false;
            ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(150);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                if (reactionsContainerLayout != null) {
                    reactionsContainerLayout.setAlpha(val);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(GONE);
                    if (reactionsContainerLayout != null) {
                        removeView(reactionsContainerLayout);
                        reactionsContainerLayout = null;
                    }
                    currentPrimaryObject = null;
                }
            });
            animator.start();
        }
    }

    public boolean onBackPressed() {
        if (reactionsContainerLayout != null && reactionsContainerLayout.getReactionsWindow() != null) {
            reactionsContainerLayout.dismissWindow();
            return false;
        }
        return true;
    }

    public void setHiddenByScroll(boolean hiddenByScroll) {
        this.hiddenByScroll = hiddenByScroll;

        if (hiddenByScroll) {
            animateVisible(false);
        }
    }
}
