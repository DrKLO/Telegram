package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagePreviewParams;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class MessagePreviewView extends FrameLayout {

    TLRPC.Peer sendAsPeer;
    public void setSendAsPeer(TLRPC.Peer defPeer) {
        sendAsPeer = defPeer;
        for (int i = 0; i < viewPager.viewPages.length; ++i) {
            if (viewPager.viewPages[i] != null && ((Page) viewPager.viewPages[i]).currentTab == TAB_FORWARD) {
                ((Page) viewPager.viewPages[i]).updateMessages();
            }
        }
    }

    public interface ResourcesDelegate extends Theme.ResourcesProvider {

        Drawable getWallpaperDrawable();

        boolean isWallpaperMotion();
    }

    public static final int TAB_REPLY = 0;
    public static final int TAB_FORWARD = 1;
    public static final int TAB_LINK = 2;

    final boolean showOutdatedQuote;
    final ChatActivity chatActivity;
    final MessagePreviewParams messagePreviewParams;

    private class Page extends FrameLayout {
        public int currentTab;

        SizeNotifierFrameLayout chatPreviewContainer;
        MessagePreviewView.ActionBar actionBar;
        View textSelectionOverlay;
        TextSelectionHelper.ChatListTextSelectionHelper textSelectionHelper;
        RecyclerListView chatListView;
        ChatListItemAnimator itemAnimator;
        GridLayoutManagerFixed chatLayoutManager;
        Adapter adapter;

        MessagePreviewParams.Messages messages;

        ActionBarPopupWindow.ActionBarPopupWindowLayout menu;
        ActionBarMenuSubItem quoteButton, clearQuoteButton;
        ActionBarMenuSubItem replyAnotherChatButton, quoteAnotherChatButton;
        ActionBarMenuSubItem deleteReplyButton;
        ToggleButton changePositionBtn;
        FrameLayout changeSizeBtnContainer;
        ToggleButton changeSizeBtn;
        ToggleButton videoChangeSizeBtn;
        boolean shownBackMenu;
        int menuBack;
        ChatMessageSharedResources sharedResources;
        private boolean firstLayout = true;

        int scrollToOffset = -1;


        boolean toQuote;
        private AnimatorSet quoteSwitcher;
        private void switchToQuote(boolean _quote, boolean animated) {
            if (showOutdatedQuote) {
                _quote = false;
            }
            final boolean quote = _quote;
            if (animated && toQuote == quote) {
                return;
            }
            toQuote = quote;

            if (quoteSwitcher != null) {
                quoteSwitcher.cancel();
                quoteSwitcher = null;
            }

            if (animated) {
                quoteSwitcher = new AnimatorSet();
                final ArrayList<Animator> animators = new ArrayList<>();
                if (quoteButton != null) {
                    quoteButton.setVisibility(View.VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(quoteButton, View.ALPHA, !quote ? 1f : 0f));
                }
                if (clearQuoteButton != null) {
                    clearQuoteButton.setVisibility(View.VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(clearQuoteButton, View.ALPHA, quote ? 1f : 0f));
                }
                if (replyAnotherChatButton != null) {
                    replyAnotherChatButton.setVisibility(View.VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(replyAnotherChatButton, View.ALPHA, !quote ? 1f : 0f));
                }
                if (quoteAnotherChatButton != null) {
                    quoteAnotherChatButton.setVisibility(View.VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(quoteAnotherChatButton, View.ALPHA, quote ? 1f : 0f));
                }
                quoteSwitcher.playTogether(animators);
                quoteSwitcher.setDuration(360);
                quoteSwitcher.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                quoteSwitcher.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        quoteSwitcher = null;
                        switchToQuote(quote, false);
                    }
                });
                quoteSwitcher.start();
            } else {
                if (quoteButton != null) {
                    quoteButton.setAlpha(!quote ? 1f : 0f);
                    quoteButton.setVisibility(!quote ? View.VISIBLE : View.INVISIBLE);
                }
                if (clearQuoteButton != null) {
                    clearQuoteButton.setAlpha(quote ? 1f : 0f);
                    clearQuoteButton.setVisibility(quote ? View.VISIBLE : View.INVISIBLE);
                }
                if (replyAnotherChatButton != null) {
                    replyAnotherChatButton.setAlpha(!quote ? 1f : 0f);
                    replyAnotherChatButton.setVisibility(!quote ? View.VISIBLE : View.INVISIBLE);
                }
                if (quoteAnotherChatButton != null) {
                    quoteAnotherChatButton.setAlpha(quote ? 1f : 0f);
                    quoteAnotherChatButton.setVisibility(quote ? View.VISIBLE : View.INVISIBLE);
                }
            }
        }

        public boolean isReplyMessageCell(ChatMessageCell cell) {
            if (cell == null || cell.getMessageObject() == null) return false;
            MessageObject replyMessage = getReplyMessage();
            if (replyMessage == null) return false;
            return (cell.getMessageObject() == replyMessage || cell.getMessageObject().getId() == replyMessage.getId());
        }

        public ChatMessageCell getReplyMessageCell() {
            MessageObject replyMessage = getReplyMessage();
            if (replyMessage == null) {
                return null;
            }
            for (int i = 0; i < chatListView.getChildCount(); ++i) {
                ChatMessageCell cell = (ChatMessageCell) chatListView.getChildAt(i);
                if (cell.getMessageObject() == null) continue;
                if (cell.getMessageObject() == replyMessage || cell.getMessageObject().getId() == replyMessage.getId()) {
                    return cell;
                }
            }
            return null;
        }

        public MessageObject getReplyMessage() {
            return getReplyMessage(null);
        }

        public MessageObject getReplyMessage(MessageObject fallback) {
            if (messagePreviewParams.replyMessage != null) {
                if (messagePreviewParams.replyMessage.groupedMessagesMap != null && messagePreviewParams.replyMessage.groupedMessagesMap.size() > 0) {
                    MessageObject.GroupedMessages group = messagePreviewParams.replyMessage.groupedMessagesMap.valueAt(0);
                    if (group != null) {
                        if (group.isDocuments) {
                            if (fallback != null) {
                                return fallback;
                            } else if (messagePreviewParams.quote != null) {
                                return messagePreviewParams.quote.message;
                            }
                        }
                        return group.captionMessage;
                    }
                }
                return messagePreviewParams.replyMessage.messages.get(0);
            }
            return null;
        }

        Rect rect = new Rect();

        int chatTopOffset;
        float yOffset;
        int currentTopOffset;
        float currentYOffset;

        public Page(Context context, int tab) {
            super(context);
            sharedResources = new ChatMessageSharedResources(context);

            currentTab = tab;

            setOnTouchListener((view, motionEvent) -> {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    dismiss(true);
                }
                return true;
            });

            chatPreviewContainer = new SizeNotifierFrameLayout(context) {
                @Override
                protected Drawable getNewDrawable() {
                    Drawable drawable = resourcesProvider.getWallpaperDrawable();
                    return drawable != null ? drawable : super.getNewDrawable();
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (ev.getY() < currentTopOffset) {
                        return false;
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
            chatPreviewContainer.setBackgroundImage(resourcesProvider.getWallpaperDrawable(), resourcesProvider.isWallpaperMotion());
            chatPreviewContainer.setOccupyStatusBar(false);

            if (Build.VERSION.SDK_INT >= 21) {
                chatPreviewContainer.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, (int) (currentTopOffset + 1), view.getMeasuredWidth(), view.getMeasuredHeight(), dp(8));
                    }
                });
                chatPreviewContainer.setClipToOutline(true);
                chatPreviewContainer.setElevation(dp(4));
            }

            actionBar = new MessagePreviewView.ActionBar(context, resourcesProvider);
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));

            textSelectionHelper = new TextSelectionHelper.ChatListTextSelectionHelper() {
                {
                    resourcesProvider = MessagePreviewView.this.resourcesProvider;
                }

                @Override
                protected boolean canCopy() {
                    return messagePreviewParams == null || !messagePreviewParams.noforwards;
                }

                @Override
                protected Theme.ResourcesProvider getResourcesProvider() {
                    return resourcesProvider;
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    if (chatListView != null) {
                        chatListView.invalidate();
                    }
                }

                @Override
                protected boolean canShowQuote() {
                    return currentTab == TAB_REPLY && !messagePreviewParams.isSecret;
                }

                @Override
                protected void onQuoteClick(MessageObject messageObject, int start, int end, CharSequence text) {
                    if (textSelectionHelper.selectionEnd - textSelectionHelper.selectionStart > MessagesController.getInstance(currentAccount).quoteLengthMax) {
                        showQuoteLengthError();
                        return;
                    }
                    messagePreviewParams.quoteStart = textSelectionHelper.selectionStart;
                    messagePreviewParams.quoteEnd = textSelectionHelper.selectionEnd;
                    MessageObject toSelectMessage = getReplyMessage(messageObject);
                    if (toSelectMessage != null && (messagePreviewParams.quote == null || messagePreviewParams.quote.message == null || messagePreviewParams.quote.message.getId() != toSelectMessage.getId())) {
                        messagePreviewParams.quote = ChatActivity.ReplyQuote.from(toSelectMessage, start, end);
                    }
                    onQuoteSelectedPart();
                    dismiss(true);
                }

                @Override
                public boolean isSelected(MessageObject messageObject) {
                    return currentTab == TAB_REPLY && !messagePreviewParams.isSecret && isInSelectionMode();
                }
            };
            textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
                @Override
                public void onStateChanged(boolean isSelected) {
                    if (!showing) {
                        return;
                    }
                    if (!isSelected && menu.getSwipeBack().isForegroundOpen()) {
                        menu.getSwipeBack().closeForeground(true);
                    } else if (isSelected) {
                        if (textSelectionHelper.selectionEnd - textSelectionHelper.selectionStart > MessagesController.getInstance(currentAccount).quoteLengthMax) {
                            showQuoteLengthError();
                            return;
                        }
                        MessageObject msg = null;
                        if (textSelectionHelper.getSelectedCell() != null) {
                            msg = textSelectionHelper.getSelectedCell().getMessageObject();
                        }
                        msg = getReplyMessage(msg);
                        if (messagePreviewParams.quote == null) {
                            messagePreviewParams.quoteStart = textSelectionHelper.selectionStart;
                            messagePreviewParams.quoteEnd = textSelectionHelper.selectionEnd;
                            messagePreviewParams.quote = ChatActivity.ReplyQuote.from(msg, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                            menu.getSwipeBack().openForeground(menuBack);
                        }
                    }
                }
            });

            chatListView = new RecyclerListView(context, resourcesProvider) {

                @Override
                public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        boolean r = super.drawChild(canvas, child, drawingTime);
                        cell.drawCheckBox(canvas);
                        canvas.save();
                        canvas.translate(cell.getX(), cell.getY());

                        canvas.save();
                        canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getPivotX(), cell.getPivotY());
                        cell.drawContent(canvas, true);
                        cell.layoutTextXY(true);
                        cell.drawMessageText(canvas);
                        if ((cell.getCurrentMessagesGroup() == null || cell.getCurrentPosition() != null && ((cell.getCurrentPosition().flags & cell.captionFlag()) != 0 && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) != 0 || cell.getCurrentMessagesGroup() != null && cell.getCurrentMessagesGroup().isDocuments)) || cell.getTransitionParams().animateBackgroundBoundsInner) {
                            cell.drawCaptionLayout(canvas, false, cell.getAlpha());
                            cell.drawReactionsLayout(canvas, cell.getAlpha(), null);
                            cell.drawCommentLayout(canvas, cell.getAlpha());
                        }
                        if (cell.getCurrentMessagesGroup() != null || cell.getTransitionParams().animateBackgroundBoundsInner) {
                            cell.drawNamesLayout(canvas, cell.getAlpha());
                        }
                        if ((cell.getCurrentPosition() != null && cell.getCurrentPosition().last) || cell.getTransitionParams().animateBackgroundBoundsInner) {
                            cell.drawTime(canvas, cell.getAlpha(), true);
                        }
                        cell.drawOverlays(canvas);
                        canvas.restore();
                        cell.getTransitionParams().recordDrawingStatePreview();
                        canvas.restore();
                        return r;
                    }
                    return true;
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            cell.setParentViewSize(chatPreviewContainer.getMeasuredWidth(), chatPreviewContainer.getBackgroundSizeY());
                        }
                    }
                    drawChatBackgroundElements(canvas);
                    super.dispatchDraw(canvas);
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    if (firstLayout) {
                        if (currentTab != TAB_REPLY) {
                            scrollToPosition(0);
                        }
                        firstLayout = false;
                    }
                    super.onLayout(changed, l, t, r, b);
                    updatePositions();
                    checkScroll();
//                    if (scrollToOffset != -1) {
//                        final int offset = scrollToOffset;
//                        post(() -> chatLayoutManager.scrollToPositionWithOffset(0, offset, false));
//                        scrollToOffset = -1;
//                    }
                }

                private void drawChatBackgroundElements(Canvas canvas) {
                    int count = getChildCount();
                    MessageObject.GroupedMessages lastDrawnGroup = null;

                    for (int a = 0; a < count; a++) {
                        View child = getChildAt(a);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                            if (group != null && group == lastDrawnGroup) {
                                continue;
                            }
                            lastDrawnGroup = group;
                            MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                            MessageBackgroundDrawable backgroundDrawable = cell.getBackgroundDrawable();
                        }
                    }
                    MessageObject.GroupedMessages scrimGroup = null;
                    for (int k = 0; k < 3; k++) {
                        drawingGroups.clear();
                        if (k == 2 && !chatListView.isFastScrollAnimationRunning()) {
                            continue;
                        }
                        for (int i = 0; i < count; i++) {
                            View child = chatListView.getChildAt(i);
                            if (child instanceof ChatMessageCell) {
                                ChatMessageCell cell = (ChatMessageCell) child;
                                if (child.getY() > chatListView.getHeight() || child.getY() + child.getHeight() < 0) {
                                    continue;
                                }
                                MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                                if (group == null || (k == 0 && group.messages.size() == 1) || (k == 1 && !group.transitionParams.drawBackgroundForDeletedItems)) {
                                    continue;
                                }
                                if ((k == 0 && cell.getMessageObject().deleted) || (k == 1 && !cell.getMessageObject().deleted)) {
                                    continue;
                                }
                                if ((k == 2 && !cell.willRemovedAfterAnimation()) || (k != 2 && cell.willRemovedAfterAnimation())) {
                                    continue;
                                }

                                if (!drawingGroups.contains(group)) {
                                    group.transitionParams.left = 0;
                                    group.transitionParams.top = 0;
                                    group.transitionParams.right = 0;
                                    group.transitionParams.bottom = 0;

                                    group.transitionParams.pinnedBotton = false;
                                    group.transitionParams.pinnedTop = false;
                                    group.transitionParams.cell = cell;
                                    drawingGroups.add(group);
                                }

                                group.transitionParams.pinnedTop = cell.isPinnedTop();
                                group.transitionParams.pinnedBotton = cell.isPinnedBottom();

                                int left = (cell.getLeft() + cell.getBackgroundDrawableLeft());
                                int right = (cell.getLeft() + cell.getBackgroundDrawableRight());
                                int top = (cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop());
                                int bottom = (cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom());

                                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                    top -= dp(10);
                                }

                                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                    bottom += dp(10);
                                }

                                if (cell.willRemovedAfterAnimation()) {
                                    group.transitionParams.cell = cell;
                                }

                                if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                                    group.transitionParams.top = top;
                                }
                                if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                                    group.transitionParams.bottom = bottom;
                                }
                                if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                                    group.transitionParams.left = left;
                                }
                                if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                                    group.transitionParams.right = right;
                                }
                            }
                        }

                        for (int i = 0; i < drawingGroups.size(); i++) {
                            MessageObject.GroupedMessages group = drawingGroups.get(i);
                            if (group == scrimGroup) {
                                continue;
                            }
                            float x = group.transitionParams.cell.getNonAnimationTranslationX(true);
                            float l = (group.transitionParams.left + x + group.transitionParams.offsetLeft);
                            float t = (group.transitionParams.top + group.transitionParams.offsetTop);
                            float r = (group.transitionParams.right + x + group.transitionParams.offsetRight);
                            float b = (group.transitionParams.bottom + group.transitionParams.offsetBottom);

                            if (!group.transitionParams.backgroundChangeBounds) {
                                t += group.transitionParams.cell.getTranslationY();
                                b += group.transitionParams.cell.getTranslationY();
                            }

                            if (t < -dp(20)) {
                                t = -dp(20);
                            }

                            if (b > chatListView.getMeasuredHeight() + dp(20)) {
                                b = chatListView.getMeasuredHeight() + dp(20);
                            }

                            boolean useScale = group.transitionParams.cell.getScaleX() != 1f || group.transitionParams.cell.getScaleY() != 1f;
                            if (useScale) {
                                canvas.save();
                                canvas.scale(group.transitionParams.cell.getScaleX(), group.transitionParams.cell.getScaleY(), l + (r - l) / 2, t + (b - t) / 2);
                            }

                            group.transitionParams.cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, false, 0);
                            group.transitionParams.cell = null;
                            group.transitionParams.drawCaptionLayout = group.hasCaption;
                            if (useScale) {
                                canvas.restore();
                                for (int ii = 0; ii < count; ii++) {
                                    View child = chatListView.getChildAt(ii);
                                    if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getCurrentMessagesGroup() == group) {
                                        ChatMessageCell cell = ((ChatMessageCell) child);
                                        int left = cell.getLeft();
                                        int top = cell.getTop();
                                        child.setPivotX(l - left + (r - l) / 2);
                                        child.setPivotY(t - top + (b - t) / 2);
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onScrollStateChanged(int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        textSelectionHelper.stopScrolling();
                    }
                    super.onScrollStateChanged(newState);
                }

                @Override
                public void onScrolled(int dx, int dy) {
                    super.onScrolled(dx, dy);
                    textSelectionHelper.onParentScrolled();
                }
            };
            chatListView.setItemAnimator(itemAnimator = new ChatListItemAnimator(null, chatListView, resourcesProvider) {

                int scrollAnimationIndex = -1;

                @Override
                public void onAnimationStart() {
                    super.onAnimationStart();
                    AndroidUtilities.cancelRunOnUIThread(changeBoundsRunnable);
                    changeBoundsRunnable.run();

                    if (scrollAnimationIndex == -1) {
                        scrollAnimationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(scrollAnimationIndex, null, false);
                    }
                    if (finishRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                        finishRunnable = null;
                    }
                }


                Runnable finishRunnable;

                @Override
                protected void onAllAnimationsDone() {
                    super.onAllAnimationsDone();
                    if (finishRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    }
                    AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                        if (scrollAnimationIndex != -1) {
                            NotificationCenter.getInstance(currentAccount).onAnimationFinish(scrollAnimationIndex);
                            scrollAnimationIndex = -1;
                        }
                    });

                    if (updateAfterAnimations) {
                        updateAfterAnimations = false;
                        AndroidUtilities.runOnUIThread(() -> updateMessages());
                    }
                }

                @Override
                public void endAnimations() {
                    super.endAnimations();
                    if (finishRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    }
                    AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                        if (scrollAnimationIndex != -1) {
                            NotificationCenter.getInstance(currentAccount).onAnimationFinish(scrollAnimationIndex);
                            scrollAnimationIndex = -1;
                        }
                    });
                }
            });
            chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    for (int i = 0; i < chatListView.getChildCount(); i++) {
                        ChatMessageCell cell = (ChatMessageCell) chatListView.getChildAt(i);
                        cell.setParentViewSize(chatPreviewContainer.getMeasuredWidth(), chatPreviewContainer.getBackgroundSizeY());
                    }

                    if (textSelectionHelper != null) {
                        textSelectionHelper.invalidate();
                    }
                }
            });
            chatListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    if (currentTab != TAB_FORWARD || messages.previewMessages.size() <= 1) {
                        return;
                    }
                    int id = messages.previewMessages.get(position).getId();
                    boolean newSelected = !messages.selectedIds.get(id, false);
                    if (messages.selectedIds.size() == 1 && !newSelected) {
                        return;
                    }
                    if (!newSelected) {
                        messages.selectedIds.delete(id);
                    } else {
                        messages.selectedIds.put(id, newSelected);
                    }
                    ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                    chatMessageCell.setChecked(newSelected, newSelected, true);
                    updateSubtitle(true);
                }
            });

            chatListView.setAdapter(adapter = new Adapter());
            chatListView.setPadding(0, dp(4), 0, dp(4));
            chatLayoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {

                @Override
                public boolean shouldLayoutChildFromOpositeSide(View child) {
                    return false;
                }

                @Override
                protected boolean hasSiblingChild(int position) {
                    MessageObject message = messages.previewMessages.get(position);
                    MessageObject.GroupedMessages group = getValidGroupedMessage(message);
                    if (group != null) {
                        MessageObject.GroupedMessagePosition pos = group.getPosition(message);
                        if (pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                            return false;
                        }
                        int count = group.posArray.size();
                        for (int a = 0; a < count; a++) {
                            MessageObject.GroupedMessagePosition p = group.posArray.get(a);
                            if (p == pos) {
                                continue;
                            }
                            if (p.minY <= pos.minY && p.maxY >= pos.minY) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                @Override
                public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                        super.onLayoutChildren(recycler, state);
                    } else {
                        try {
                            super.onLayoutChildren(recycler, state);
                        } catch (Exception e) {
                            FileLog.e(e);
                            AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
                        }
                    }
                }
            };
            chatLayoutManager.setSpanSizeLookup(new GridLayoutManagerFixed.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    int idx = position;
                    if (idx >= 0 && idx < messages.previewMessages.size()) {
                        MessageObject message = messages.previewMessages.get(idx);
                        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
                        if (groupedMessages != null) {
                            return groupedMessages.getPosition(message).spanSize;
                        }
                    }
                    return 1000;
                }
            });
            chatListView.setClipToPadding(false);
            chatListView.setLayoutManager(chatLayoutManager);
            chatListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    outRect.bottom = 0;
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                        if (group != null) {
                            MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                            if (position != null && position.siblingHeights != null) {
                                float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                                int h = cell.getExtraInsetHeight();
                                for (int a = 0; a < position.siblingHeights.length; a++) {
                                    h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                                }
                                h += (position.maxY - position.minY) * Math.round(7 * AndroidUtilities.density);
                                int count = group.posArray.size();
                                for (int a = 0; a < count; a++) {
                                    MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                                    if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                                        continue;
                                    }
                                    if (pos.minY == position.minY) {
                                        h -= (int) Math.ceil(maxHeight * pos.ph) - dp(4);
                                        break;
                                    }
                                }
                                outRect.bottom = -h;
                            }
                        }
                    }
                }
            });

            chatPreviewContainer.addView(chatListView);
            addView(chatPreviewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 400, 0, 8, 0, 8, 0));
            chatPreviewContainer.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            menu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), R.drawable.popup_fixed_alert2, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
            menu.getSwipeBack().setOnForegroundOpenFinished(() -> {
                switchToQuote(true, false);
            });
            addView(menu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            if (tab == TAB_REPLY && messagePreviewParams.replyMessage != null) {
                if (messagePreviewParams.replyMessage.hasText && !messagePreviewParams.isSecret) {
                    LinearLayout swipeback = new LinearLayout(context);
                    swipeback.setOrientation(LinearLayout.VERTICAL);

                    if (!showOutdatedQuote) {
                        ActionBarMenuSubItem backButton = new ActionBarMenuSubItem(context, false, true, false, resourcesProvider);
                        backButton.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
                        backButton.setOnClickListener(v -> {
                            messagePreviewParams.quote = null;
                            textSelectionHelper.clear();
                            switchToQuote(false, false);
                            menu.getSwipeBack().closeForeground();
                        });
                        swipeback.addView(backButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
                        gap.setTag(R.id.fit_width_tag, 1);
                        swipeback.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                        ActionBarMenuSubItem selectQuoteButton = new ActionBarMenuSubItem(context, false, false, true, resourcesProvider);
                        selectQuoteButton.setTextAndIcon(LocaleController.getString(R.string.QuoteSelectedPart), R.drawable.menu_quote_specific);
                        selectQuoteButton.setOnClickListener(v -> {
                            MessageObject replyMessage = getReplyMessage();
                            if (replyMessage != null) {
                                if (textSelectionHelper.selectionEnd - textSelectionHelper.selectionStart > MessagesController.getInstance(currentAccount).quoteLengthMax) {
                                    showQuoteLengthError();
                                    return;
                                }
                                MessageObject msg = null;
                                if (textSelectionHelper.getSelectedCell() != null) {
                                    msg = textSelectionHelper.getSelectedCell().getMessageObject();
                                }
                                msg = getReplyMessage(msg);
                                messagePreviewParams.quoteStart = textSelectionHelper.selectionStart;
                                messagePreviewParams.quoteEnd = textSelectionHelper.selectionEnd;
                                messagePreviewParams.quote = ChatActivity.ReplyQuote.from(msg, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                                onQuoteSelectedPart();
                                dismiss(true);
                            }
                        });
                        swipeback.addView(selectQuoteButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    }

                    menuBack = menu.addViewToSwipeBack(swipeback);
                    menu.getSwipeBack().setStickToRight(true);

                    FrameLayout btn1 = new FrameLayout(context);
                    quoteButton = new ActionBarMenuSubItem(context, true, true, false, resourcesProvider) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (getVisibility() != View.VISIBLE || getAlpha() < .5f) {
                                return false;
                            }
                            return super.onTouchEvent(event);
                        }

                        @Override
                        public void updateBackground() {
                            setBackground(null);
                        }
                    };
                    quoteButton.setTextAndIcon(LocaleController.getString(showOutdatedQuote ? R.string.QuoteSelectedPart : R.string.SelectSpecificQuote), R.drawable.menu_select_quote);
                    clearQuoteButton = new ActionBarMenuSubItem(context, true, true, false, resourcesProvider) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (getVisibility() != View.VISIBLE || getAlpha() < .5f) {
                                return false;
                            }
                            return super.onTouchEvent(event);
                        }

                        @Override
                        public void updateBackground() {
                            setBackground(null);
                        }
                    };
                    clearQuoteButton.setTextAndIcon(LocaleController.getString(R.string.ClearQuote), R.drawable.menu_quote_delete);
                    btn1.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 6, 0));
                    btn1.setOnClickListener(v -> {
                        if (messagePreviewParams.quote != null && !showOutdatedQuote) {
                            // clear
                            messagePreviewParams.quote = null;
                            textSelectionHelper.clear();
                            switchToQuote(false, true);
                            updateSubtitle(true);
                        } else {
                            // switch to select
                            if (textSelectionHelper.selectionEnd - textSelectionHelper.selectionStart > MessagesController.getInstance(currentAccount).quoteLengthMax) {
                                showQuoteLengthError();
                                return;
                            }
                            MessageObject replyMessage = getReplyMessage();
                            if (replyMessage != null) {
                                if (textSelectionHelper.isInSelectionMode()) {
                                    messagePreviewParams.quoteStart = textSelectionHelper.selectionStart;
                                    messagePreviewParams.quoteEnd = textSelectionHelper.selectionEnd;
                                    MessageObject msg = null;
                                    if (textSelectionHelper.getSelectedCell() != null) {
                                        msg = textSelectionHelper.getSelectedCell().getMessageObject();
                                    }
                                    msg = getReplyMessage(msg);
                                    messagePreviewParams.quote = ChatActivity.ReplyQuote.from(msg, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                                    onQuoteSelectedPart();
                                    dismiss(true);
                                    return;
                                } else {
                                    messagePreviewParams.quoteStart = 0;
                                    messagePreviewParams.quoteEnd = Math.min(MessagesController.getInstance(currentAccount).quoteLengthMax, replyMessage.messageOwner.message.length());
                                    messagePreviewParams.quote = ChatActivity.ReplyQuote.from(replyMessage, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                                    textSelectionHelper.select(getReplyMessageCell(), messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                                }
                                if (!showOutdatedQuote) {
                                    menu.getSwipeBack().openForeground(menuBack);
                                }
                                switchToQuote(true, true);
                            }
                        }
                    });

                    btn1.addView(quoteButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                    btn1.addView(clearQuoteButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                    menu.addView(btn1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                }

                if (!messagePreviewParams.noforwards && !messagePreviewParams.hasSecretMessages) {
                    FrameLayout btn2 = new FrameLayout(context);
                    replyAnotherChatButton = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                    replyAnotherChatButton.setTextAndIcon(LocaleController.getString(R.string.ReplyToAnotherChat), R.drawable.msg_forward_replace);
                    replyAnotherChatButton.setOnClickListener(v -> selectAnotherChat(false));
                    quoteAnotherChatButton = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                    quoteAnotherChatButton.setTextAndIcon(LocaleController.getString(R.string.QuoteToAnotherChat), R.drawable.msg_forward_replace);
                    quoteAnotherChatButton.setOnClickListener(v -> selectAnotherChat(false));
                    btn2.addView(quoteAnotherChatButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                    btn2.addView(replyAnotherChatButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                    menu.addView(btn2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                }

                if (!messagePreviewParams.noforwards && !messagePreviewParams.hasSecretMessages) {
                    ActionBarPopupWindow.GapView gap2 = new ActionBarPopupWindow.GapView(context, resourcesProvider);
                    gap2.setTag(R.id.fit_width_tag, 1);
                    menu.addView(gap2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                }

                switchToQuote(messagePreviewParams.quote != null, false);

                ActionBarMenuSubItem applyChanges = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                applyChanges.setTextAndIcon(LocaleController.getString(R.string.ApplyChanges), R.drawable.msg_select);
                applyChanges.setOnClickListener(v -> dismiss(true));
                menu.addView(applyChanges, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                deleteReplyButton = new ActionBarMenuSubItem(context, true, false, true, resourcesProvider);
                deleteReplyButton.setTextAndIcon(LocaleController.getString(showOutdatedQuote ? R.string.DoNotQuote : R.string.DoNotReply), R.drawable.msg_delete);
                deleteReplyButton.setColors(getThemedColor(Theme.key_text_RedBold), getThemedColor(Theme.key_text_RedRegular));
                deleteReplyButton.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
                deleteReplyButton.setOnClickListener(v -> {
                    if (showOutdatedQuote) {
                        removeQuote();
                    } else {
                        removeReply();
                    }
                });
                menu.addView(deleteReplyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            } else if (tab == TAB_FORWARD && messagePreviewParams.forwardMessages != null) {

                ToggleButton sendersNameButton = new ToggleButton(
                    context,
                    R.raw.name_hide, messagePreviewParams.multipleUsers ? LocaleController.getString(R.string.ShowSenderNames) : LocaleController.getString(R.string.ShowSendersName),
                    R.raw.name_show, messagePreviewParams.multipleUsers ? LocaleController.getString(R.string.HideSenderNames) : LocaleController.getString(R.string.HideSendersName),
                    resourcesProvider
                );
                menu.addView(sendersNameButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                final ToggleButton captionButton;
                if (messagePreviewParams.hasCaption) {
                    captionButton = new ToggleButton(
                        context,
                        R.raw.caption_hide, LocaleController.getString(R.string.ShowCaption),
                        R.raw.caption_show, LocaleController.getString(R.string.HideCaption),
                        resourcesProvider
                    );
                    captionButton.setState(messagePreviewParams.hideCaption, false);
                    menu.addView(captionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                } else {
                    captionButton = null;
                }

                ActionBarMenuSubItem changeRecipientView = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
                changeRecipientView.setOnClickListener(view -> selectAnotherChat(true));
                changeRecipientView.setTextAndIcon(LocaleController.getString(R.string.ChangeRecipient), R.drawable.msg_forward_replace);
                menu.addView(changeRecipientView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
                gap.setTag(R.id.fit_width_tag, 1);
                menu.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                ActionBarMenuSubItem applyChanges = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                applyChanges.setTextAndIcon(LocaleController.getString(R.string.ApplyChanges), R.drawable.msg_select);
                applyChanges.setOnClickListener(v -> dismiss(true));
                menu.addView(applyChanges, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                ActionBarMenuSubItem deleteLink = new ActionBarMenuSubItem(context, true, false, true, resourcesProvider);
                deleteLink.setTextAndIcon(LocaleController.getString(R.string.DoNotForward), R.drawable.msg_delete);
                deleteLink.setColors(getThemedColor(Theme.key_text_RedBold), getThemedColor(Theme.key_text_RedRegular));
                deleteLink.setOnClickListener(v -> removeForward());
                deleteLink.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
                menu.addView(deleteLink, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

//                ActionBarMenuSubItem sendMessagesView = new ActionBarMenuSubItem(context, false, true, resourcesProvider);
//                sendMessagesView.setTextAndIcon(LocaleController.getString(R.string.ForwardSendMessages), R.drawable.msg_send);
//                menu.addView(sendMessagesView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
//                sendMessagesView.setOnClickListener(View -> didSendPressed());

                sendersNameButton.setState(messagePreviewParams.hideForwardSendersName, false);
                sendersNameButton.setOnClickListener(view -> {
                    messagePreviewParams.hideForwardSendersName = !messagePreviewParams.hideForwardSendersName;
                    returnSendersNames = false;
                    if (!messagePreviewParams.hideForwardSendersName) {
                        messagePreviewParams.hideCaption = false;
                        if (captionButton != null) {
                            captionButton.setState(messagePreviewParams.hideCaption, true);
                        }
                    }
                    sendersNameButton.setState(messagePreviewParams.hideForwardSendersName, true);
                    updateMessages();
                    updateSubtitle(true);
                });

                if (captionButton != null) {
                    captionButton.setOnClickListener(view -> {
                        messagePreviewParams.hideCaption = !messagePreviewParams.hideCaption;
                        if (messagePreviewParams.hideCaption) {
                            if (!messagePreviewParams.hideForwardSendersName) {
                                messagePreviewParams.hideForwardSendersName = true;
                                returnSendersNames = true;
                            }
                        } else {
                            if (returnSendersNames) {
                                messagePreviewParams.hideForwardSendersName = false;
                            }
                            returnSendersNames = false;
                        }
                        captionButton.setState(messagePreviewParams.hideCaption, true);
                        sendersNameButton.setState(messagePreviewParams.hideForwardSendersName, true);
                        updateMessages();
                        updateSubtitle(true);
                    });
                }

            } else if (tab == TAB_LINK && messagePreviewParams.linkMessage != null) {

                changePositionBtn = new ToggleButton(
                    context,
                    R.raw.position_below, LocaleController.getString(R.string.LinkAbove),
                    R.raw.position_above, LocaleController.getString(R.string.LinkBelow),
                    resourcesProvider
                );
                changePositionBtn.setState(!messagePreviewParams.webpageTop, false);
                menu.addView(changePositionBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                changeSizeBtnContainer = new FrameLayout(context);
                changeSizeBtnContainer.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 0, 0));
                changeSizeBtn = new ToggleButton(
                    context,
                    R.raw.media_shrink, LocaleController.getString(R.string.LinkMediaLarger),
                    R.raw.media_enlarge, LocaleController.getString(R.string.LinkMediaSmaller),
                    resourcesProvider
                );
                changeSizeBtn.setBackground(null);
                changeSizeBtn.setVisibility(messagePreviewParams.isVideo ? View.INVISIBLE : View.VISIBLE);
                changeSizeBtnContainer.addView(changeSizeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                videoChangeSizeBtn = new ToggleButton(
                    context,
                    R.raw.media_shrink, LocaleController.getString(R.string.LinkVideoLarger),
                    R.raw.media_enlarge, LocaleController.getString(R.string.LinkVideoSmaller),
                    resourcesProvider
                );
                videoChangeSizeBtn.setBackground(null);
                videoChangeSizeBtn.setVisibility(!messagePreviewParams.isVideo ? View.INVISIBLE : View.VISIBLE);
                changeSizeBtnContainer.setAlpha(messagePreviewParams.hasMedia ? 1f : .5f);
                changeSizeBtnContainer.addView(videoChangeSizeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                menu.addView(changeSizeBtnContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                changeSizeBtnContainer.setVisibility(messagePreviewParams.singleLink && !messagePreviewParams.hasMedia ? View.GONE : View.VISIBLE);
                changeSizeBtn.setState(messagePreviewParams.webpageSmall, false);
                videoChangeSizeBtn.setState(messagePreviewParams.webpageSmall, false);

                ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
                gap.setTag(R.id.fit_width_tag, 1);
                menu.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                ActionBarMenuSubItem applyChanges = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                applyChanges.setTextAndIcon(LocaleController.getString(R.string.ApplyChanges), R.drawable.msg_select);
                applyChanges.setOnClickListener(v -> dismiss(true));
                menu.addView(applyChanges, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                ActionBarMenuSubItem deleteLink = new ActionBarMenuSubItem(context, true, false, true, resourcesProvider);
                deleteLink.setTextAndIcon(LocaleController.getString(R.string.DoNotLinkPreview), R.drawable.msg_delete);
                deleteLink.setColors(getThemedColor(Theme.key_text_RedBold), getThemedColor(Theme.key_text_RedRegular));
                deleteLink.setOnClickListener(v -> removeLink());
                deleteLink.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
                menu.addView(deleteLink, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                changeSizeBtnContainer.setOnClickListener(view -> {
                    if (!messagePreviewParams.hasMedia) {
                        return;
                    }
                    messagePreviewParams.webpageSmall = !messagePreviewParams.webpageSmall;
                    changeSizeBtn.setState(messagePreviewParams.webpageSmall, true);
                    videoChangeSizeBtn.setState(messagePreviewParams.webpageSmall, true);
                    if (messages.messages.size() > 0) {
                        TLRPC.Message msg = messages.messages.get(0).messageOwner;
                        if (msg != null && msg.media != null) {
                            msg.media.force_small_media = messagePreviewParams.webpageSmall;
                            msg.media.force_large_media = !messagePreviewParams.webpageSmall;
                        }
                    }
                    if (messages.previewMessages.size() > 0) {
                        TLRPC.Message msg = messages.previewMessages.get(0).messageOwner;
                        if (msg != null && msg.media != null) {
                            msg.media.force_small_media = messagePreviewParams.webpageSmall;
                            msg.media.force_large_media = !messagePreviewParams.webpageSmall;
                        }
                    }
                    updateMessages();
                    updateScroll = true;
                });

                changePositionBtn.setOnClickListener(view -> {
                    messagePreviewParams.webpageTop = !messagePreviewParams.webpageTop;
                    changePositionBtn.setState(!messagePreviewParams.webpageTop, true);
                    if (messages.messages.size() > 0) {
                        TLRPC.Message msg = messages.messages.get(0).messageOwner;
                        if (msg != null) {
                            msg.invert_media = messagePreviewParams.webpageTop;
                        }
                    }
                    if (messages.previewMessages.size() > 0) {
                        TLRPC.Message msg = messages.previewMessages.get(0).messageOwner;
                        if (msg != null) {
                            msg.invert_media = messagePreviewParams.webpageTop;
                        }
                    }
                    updateMessages();
                    updateScroll = true;
                });
            }

            if (currentTab == TAB_FORWARD) {
                messages = messagePreviewParams.forwardMessages;
            } else if (currentTab == TAB_REPLY) {
                messages = messagePreviewParams.replyMessage;
            } else if (currentTab == TAB_LINK) {
                messages = messagePreviewParams.linkMessage;
            }


            textSelectionOverlay = textSelectionHelper.getOverlayView(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textSelectionOverlay.setElevation(dp(8));
                textSelectionOverlay.setOutlineProvider(null);
            }
            if (textSelectionOverlay != null) {
                if (textSelectionOverlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) textSelectionOverlay.getParent()).removeView(textSelectionOverlay);
                }
                addView(textSelectionOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() / AndroidUtilities.density, 0, 0));
            }
            textSelectionHelper.setParentView(chatListView);

        }

        private boolean updateScroll = false;
        private void checkScroll() {
            if (!updateScroll) return;
            if (chatListView.computeVerticalScrollRange() > chatListView.computeVerticalScrollExtent()) {
                postDelayed(() -> {
                    if (messagePreviewParams.webpageTop) {
                        chatListView.smoothScrollBy(0, - chatListView.computeVerticalScrollOffset(), (int) (ChatListItemAnimator.DEFAULT_DURATION), ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                    } else {
                        chatListView.smoothScrollBy(0, chatListView.computeVerticalScrollRange() - (chatListView.computeVerticalScrollOffset() + chatListView.computeVerticalScrollExtent()), (int) (ChatListItemAnimator.DEFAULT_DURATION), ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                    }
                }, 0);
            }
            updateScroll = false;
        }

        private void showQuoteLengthError() {
            BulletinFactory.of(MessagePreviewView.this, resourcesProvider).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.QuoteMaxError), LocaleController.getString(R.string.QuoteMaxErrorMessage)).show();
        }

        public void bind() {
            updateMessages();
            updateSubtitle(false);
        }

        private void updateSubtitle(boolean animated) {
            if (currentTab == TAB_FORWARD) {
                actionBar.setTitle(LocaleController.formatPluralString("PreviewForwardMessagesCount", messagePreviewParams.forwardMessages == null ? 0 : messagePreviewParams.forwardMessages.selectedIds.size()), animated);
                CharSequence subtitle = "";
                if (!messagePreviewParams.hasSenders) {
                    if (messagePreviewParams.willSeeSenders) {
                        if (currentUser != null) {
                            subtitle = LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                        } else {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameVisibleChannel);
                            } else {
                                subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameVisibleGroup);
                            }
                        }
                    } else {
                        if (currentUser != null) {
                            subtitle = LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                        } else {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameHiddenChannel);
                            } else {
                                subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameHiddenGroup);
                            }
                        }
                    }
                } else if (!messagePreviewParams.hideForwardSendersName) {
                    if (currentUser != null) {
                        subtitle = LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                    } else {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameVisibleChannel);
                        } else {
                            subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameVisibleGroup);
                        }
                    }
                } else {
                    if (currentUser != null) {
                        subtitle = LocaleController.formatString("ForwardPreviewSendersNameHidden", R.string.ForwardPreviewSendersNameHidden, ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                    } else {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameHiddenChannel);
                        } else {
                            subtitle = LocaleController.getString(R.string.ForwardPreviewSendersNameHiddenGroup);
                        }
                    }
                }
                actionBar.setSubtitle(subtitle, animated);
            } else if (currentTab == TAB_REPLY) {
                if (messagePreviewParams.quote != null && messagePreviewParams.replyMessage.hasText) {
                    actionBar.setTitle(LocaleController.getString(R.string.PreviewQuoteUpdate), animated);
                    actionBar.setSubtitle(LocaleController.getString(R.string.PreviewQuoteUpdateSubtitle), animated);
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.MessageOptionsReplyTitle), animated);
                    actionBar.setSubtitle(messagePreviewParams.replyMessage.hasText ? LocaleController.getString(R.string.MessageOptionsReplySubtitle) : "", animated);
                }
            } else if (currentTab == TAB_LINK) {
                actionBar.setTitle(LocaleController.getString(R.string.MessageOptionsLinkTitle), animated);
                actionBar.setSubtitle(LocaleController.getString(R.string.MessageOptionsLinkSubtitle), animated);
            }
        }

        boolean updateAfterAnimations;
        private void updateMessages() {
            if (itemAnimator.isRunning()) {
                updateAfterAnimations = true;
                return;
            }
            for (int i = 0; i < messages.previewMessages.size(); i++) {
                MessageObject messageObject = messages.previewMessages.get(i);
                messageObject.forceUpdate = true;
                messageObject.sendAsPeer = sendAsPeer;
                if (!messagePreviewParams.hideForwardSendersName) {
                    messageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_FWD;
                    messageObject.hideSendersName = false;
                } else {
                    messageObject.messageOwner.flags &= ~TLRPC.MESSAGE_FLAG_FWD;
                    messageObject.hideSendersName = true;
                }
                if (currentTab == TAB_LINK) {
                    if (messagePreviewParams.webpage != null && (messageObject.messageOwner.media == null || messageObject.messageOwner.media.webpage != messagePreviewParams.webpage)) {
                        messageObject.messageOwner.flags |= 512;
                        messageObject.messageOwner.media = new TLRPC.TL_messageMediaWebPage();
                        messageObject.messageOwner.media.webpage = messagePreviewParams.webpage;
                        messageObject.messageOwner.media.force_large_media = !messagePreviewParams.webpageSmall;
                        messageObject.messageOwner.media.force_small_media = messagePreviewParams.webpageSmall;
                        messageObject.messageOwner.media.manual = true;
                        messageObject.linkDescription = null;
                        messageObject.generateLinkDescription();
                        messageObject.photoThumbs = null;
                        messageObject.photoThumbs2 = null;
                        messageObject.photoThumbsObject = null;
                        messageObject.photoThumbsObject2 = null;
                        messageObject.generateThumbs(true);
                        messageObject.checkMediaExistance();
                    } else if (messagePreviewParams.webpage == null) {
                        messageObject.messageOwner.flags &=~ 512;
                        messageObject.messageOwner.media = null;
                    }
                }
                if (messagePreviewParams.hideCaption) {
                    messageObject.caption = null;
                } else {
                    messageObject.generateCaption();
                }

                if (messageObject.isPoll()) {
                    MessagePreviewParams.PreviewMediaPoll mediaPoll = (MessagePreviewParams.PreviewMediaPoll) messageObject.messageOwner.media;
                    mediaPoll.results.total_voters = messagePreviewParams.hideCaption ? 0 : mediaPoll.totalVotersCached;
                }
            }
            for (int i = 0; i < messages.pollChosenAnswers.size(); i++) {
                messages.pollChosenAnswers.get(i).chosen = !messagePreviewParams.hideForwardSendersName;
            }
            for (int i = 0; i < messages.groupedMessagesMap.size(); i++) {
                itemAnimator.groupWillChanged(messages.groupedMessagesMap.valueAt(i));
            }
            adapter.notifyItemRangeChanged(0, messages.previewMessages.size());
        }


        private int buttonsHeight;
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            isLandscapeMode = MeasureSpec.getSize(widthMeasureSpec) > MeasureSpec.getSize(heightMeasureSpec);

            buttonsHeight = 0;
            menu.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
            buttonsHeight = Math.max(buttonsHeight, menu.getMeasuredHeight() + rect.top + rect.bottom);

            ((MarginLayoutParams) chatListView.getLayoutParams()).topMargin = org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight();
            if (isLandscapeMode) {
                chatPreviewContainer.getLayoutParams().height = LayoutHelper.MATCH_PARENT;
                ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).topMargin = dp(8);
                ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).bottomMargin = dp(8);
                chatPreviewContainer.getLayoutParams().width = (int) Math.min(MeasureSpec.getSize(widthMeasureSpec), Math.max(dp(340), MeasureSpec.getSize(widthMeasureSpec) * 0.6f));
                menu.getLayoutParams().height = LayoutHelper.MATCH_PARENT;
            } else {
                ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).topMargin = 0;
                ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).bottomMargin = 0;
                chatPreviewContainer.getLayoutParams().height = MeasureSpec.getSize(heightMeasureSpec) - dp(16 - 10) - buttonsHeight;
                if (chatPreviewContainer.getLayoutParams().height < MeasureSpec.getSize(heightMeasureSpec) * 0.5f) {
                    chatPreviewContainer.getLayoutParams().height = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.5f);
                }
                chatPreviewContainer.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
                menu.getLayoutParams().height = MeasureSpec.getSize(heightMeasureSpec) - chatPreviewContainer.getLayoutParams().height;
            }

            int size = MeasureSpec.getSize(widthMeasureSpec) + MeasureSpec.getSize(heightMeasureSpec) << 16;
            if (lastSize != size) {
                for (int i = 0; i < messages.previewMessages.size(); i++) {
                    if (isLandscapeMode) {
                        messages.previewMessages.get(i).parentWidth = chatPreviewContainer.getLayoutParams().width;
                    } else {
                        messages.previewMessages.get(i).parentWidth = MeasureSpec.getSize(widthMeasureSpec) - dp(16);
                    }
                    messages.previewMessages.get(i).resetLayout();
                    messages.previewMessages.get(i).forceUpdate = true;
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
                firstLayout = true;
            }
            lastSize = size;

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        int lastSize;

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updatePositions();
            firstLayout = false;
        }

        private boolean firstAttach = true;
        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            updateSelection();
            firstAttach = true;
            firstLayout = true;
        }

        public void updateSelection() {
            if (currentTab == TAB_REPLY) {
                if (textSelectionHelper.selectionEnd - textSelectionHelper.selectionStart > MessagesController.getInstance(currentAccount).quoteLengthMax) {
                    return;
                }
                MessageObject msg = null;
                if (textSelectionHelper.getSelectedCell() != null) {
                    msg = textSelectionHelper.getSelectedCell().getMessageObject();
                }
                msg = getReplyMessage(msg);
                if (messagePreviewParams.quote != null && textSelectionHelper.isInSelectionMode()) {
                    messagePreviewParams.quoteStart = textSelectionHelper.selectionStart;
                    messagePreviewParams.quoteEnd = textSelectionHelper.selectionEnd;
                    if (msg != null && (messagePreviewParams.quote.message == null || messagePreviewParams.quote.message.getId() != msg.getId())) {
                        messagePreviewParams.quote = ChatActivity.ReplyQuote.from(msg, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                        onQuoteSelectedPart();
                    }
                }
                textSelectionHelper.clear();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (currentTab == TAB_REPLY) {
                AndroidUtilities.forEachViews(chatListView, child -> {
                    adapter.onViewAttachedToWindow(chatListView.getChildViewHolder(child));
                });
            }
        }

        private void updatePositions() {
            int lastTopOffset = chatTopOffset;
            float lastYOffset = yOffset;

            if (!isLandscapeMode) {
                int childCount = 0;
                int minTop = chatListView.getMeasuredHeight();
                for (int i = 0; i < chatListView.getChildCount(); i++) {
                    View child = chatListView.getChildAt(i);
                    if (chatListView.getChildAdapterPosition(child) != RecyclerView.NO_POSITION) {
                        minTop = Math.min(minTop, child.getTop());
                        childCount++;
                    }
                }
                if (messages == null || childCount == 0 || childCount > messages.previewMessages.size()) {
                    chatTopOffset = 0;
                } else {
                    minTop -= dp(4);
                    chatTopOffset = Math.max(0, minTop);
                    chatTopOffset = Math.min((chatTopOffset + (chatListView.getMeasuredHeight() - chatTopOffset)) - (int) (AndroidUtilities.displaySize.y * .8f - buttonsHeight - dp(8)), chatTopOffset);
                }

                float totalViewsHeight = buttonsHeight - dp(8) + (chatPreviewContainer.getMeasuredHeight() - chatTopOffset);
                float totalHeight = getMeasuredHeight() - dp(16);
                yOffset = dp(8) + (totalHeight - totalViewsHeight) / 2 - chatTopOffset;
                if (yOffset > dp(8)) {
                    yOffset = dp(8);
                }
                float buttonX = getMeasuredWidth() - menu.getMeasuredWidth();
                menu.setTranslationX(buttonX);
            } else {
                yOffset = 0;
                chatTopOffset = 0;
                menu.setTranslationX(chatListView.getMeasuredWidth() + dp(8));
            }

            if (!firstLayout && (chatTopOffset != lastTopOffset || yOffset != lastYOffset)) {
                if (offsetsAnimator != null) {
                    offsetsAnimator.cancel();
                }
                offsetsAnimator = ValueAnimator.ofFloat(0, 1f);
                offsetsAnimator.addUpdateListener(valueAnimator -> {
                    float p = (float) valueAnimator.getAnimatedValue();
                    currentTopOffset = (int) (lastTopOffset * (1f - p) + chatTopOffset * p);
                    currentYOffset = lastYOffset * (1f - p) + yOffset * p;
                    setOffset(currentYOffset, currentTopOffset);
                });
                offsetsAnimator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
                offsetsAnimator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                offsetsAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        offsetsAnimator = null;
                        setOffset(yOffset, chatTopOffset);
                    }
                });

                AndroidUtilities.runOnUIThread(changeBoundsRunnable, 50);

                currentTopOffset = lastTopOffset;
                currentYOffset = lastYOffset;
                setOffset(lastYOffset, lastTopOffset);
            } else if (firstLayout) {
                setOffset(currentYOffset = yOffset, currentTopOffset = chatTopOffset);
            }
        }

        private void setOffset(float yOffset, int chatTopOffset) {
            if (isLandscapeMode) {
                actionBar.setTranslationY(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    chatPreviewContainer.invalidateOutline();
                }
                chatPreviewContainer.setTranslationY(0);
                menu.setTranslationY(0);
            } else {
                actionBar.setTranslationY(chatTopOffset);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    chatPreviewContainer.invalidateOutline();
                }
                chatPreviewContainer.setTranslationY(yOffset);
                menu.setTranslationY(yOffset + chatPreviewContainer.getMeasuredHeight() - dp(2));
            }

            textSelectionOverlay.setTranslationX(chatPreviewContainer.getX());
            textSelectionOverlay.setTranslationY(chatPreviewContainer.getY());
        }

        private void updateLinkHighlight(ChatMessageCell cell) {
            if (currentTab == TAB_LINK && !messagePreviewParams.singleLink && messagePreviewParams.currentLink != null && !(messagePreviewParams.webpage == null || messagePreviewParams.webpage instanceof TLRPC.TL_webPagePending)) {
                cell.setHighlightedSpan(messagePreviewParams.currentLink);
            } else {
                cell.setHighlightedSpan(null);
            }
        }

        private class Adapter extends RecyclerView.Adapter {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ChatMessageCell chatMessageCell = new ChatMessageCell(parent.getContext(), currentAccount, false, sharedResources, resourcesProvider) {
                    @Override
                    public void invalidate() {
                        super.invalidate();
                        chatListView.invalidate();
                    }

                    @Override
                    public void invalidate(int l, int t, int r, int b) {
                        super.invalidate(l, t, r, b);
                        chatListView.invalidate();
                    }

                    @Override
                    public void setMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean bottomNear, boolean topNear) {
                        super.setMessageObject(messageObject, groupedMessages, bottomNear, topNear);
                        updateLinkHighlight(this);
                    }

                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        super.onLayout(changed, left, top, right, bottom);
                        updateLinkHighlight(this);
                    }
                };
                chatMessageCell.setClipChildren(false);
                chatMessageCell.setClipToPadding(false);
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                        return textSelectionHelper;
                    }

                    @Override
                    public boolean hasSelectedMessages() {
                        return true;
                    }

                    @Override
                    public boolean canPerformActions() {
                        return currentTab == TAB_LINK && !messagePreviewParams.singleLink && !messagePreviewParams.isSecret;
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                        if (currentTab != TAB_LINK || messagePreviewParams.currentLink == url) {
                            return;
                        }
                        MessageObject msg = cell.getMessageObject();
                        if (msg == null) {
                            return;
                        }
                        if (!(url instanceof URLSpan)) {
                            return;
                        }
                        String urlText = ((URLSpan) url).getURL();

                        messagePreviewParams.currentLink = url;
                        messagePreviewParams.webpage = null;

                        if (chatActivity != null && urlText != null) {
                            chatActivity.searchLinks(urlText, true);
                        }
                        updateLinkHighlight(cell);
                    }

                    @Override
                    public CharacterStyle getProgressLoadingLink(ChatMessageCell cell) {
                        if (currentTab != TAB_LINK || messagePreviewParams.singleLink) {
                            return null;
                        }
                        return messagePreviewParams.currentLink;
                    }

                    @Override
                    public boolean isProgressLoading(ChatMessageCell cell, int type) {
                        if (currentTab != TAB_LINK || type != ChatActivity.PROGRESS_LINK || messagePreviewParams.singleLink) {
                            return false;
                        }
                        return messagePreviewParams.webpage == null || messagePreviewParams.webpage instanceof TLRPC.TL_webPagePending;
                    }
                });
                return new RecyclerListView.Holder(chatMessageCell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (messages == null) {
                    return;
                }

                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                cell.setInvalidateSpoilersParent(messages.hasSpoilers);
                cell.setParentViewSize(chatListView.getMeasuredWidth(), chatListView.getMeasuredHeight());
                int id = cell.getMessageObject() != null ? cell.getMessageObject().getId() : 0;
                if (currentTab == TAB_LINK) {
                    messagePreviewParams.checkCurrentLink(messages.previewMessages.get(position));
                }
                cell.setMessageObject(messages.previewMessages.get(position), messages.groupedMessagesMap.get(messages.previewMessages.get(position).getGroupId()), true, true);
                if (currentTab == TAB_FORWARD) {
                    cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                    });
                }

                if (messages.previewMessages.size() > 1) {
                    cell.setCheckBoxVisible(currentTab == TAB_FORWARD, false);
                    boolean animated = id == messages.previewMessages.get(position).getId();
                    boolean checked = messages.selectedIds.get(messages.previewMessages.get(position).getId(), false);
                    cell.setChecked(checked, checked, animated);
                }
            }

            @Override
            public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                if (messages == null || currentTab == TAB_FORWARD) {
                    return;
                }
                ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                if (currentTab == TAB_REPLY) {
                    MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(messageCell.getMessageObject());
                    messageCell.setDrawSelectionBackground(groupedMessages == null);
                    messageCell.setChecked(true, groupedMessages == null, false);

                    if (!messagePreviewParams.isSecret && messagePreviewParams.quote != null && isReplyMessageCell(messageCell) && !textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.select(messageCell, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                        if (firstAttach) {
                            scrollToOffset = offset(messageCell, messagePreviewParams.quoteStart);
                            firstAttach = false;
                        }
                    }
                } else {
                    messageCell.setDrawSelectionBackground(false);
                }
            }

            private int offset(ChatMessageCell cell, int index) {
                if (cell == null) {
                    return 0;
                }
                MessageObject object = cell.getMessageObject();
                if (object == null) {
                    return 0;
                }
                int offsetY;
                CharSequence text;
                ArrayList<MessageObject.TextLayoutBlock> textLayoutBlocks;
                if (object.getGroupId() != 0) {
                    return 0;
                } else if (!TextUtils.isEmpty(object.caption) && cell.captionLayout != null) {
                    offsetY = (int) cell.captionY;
                    text = object.caption;
                    textLayoutBlocks = cell.captionLayout.textLayoutBlocks;
                } else {
                    cell.layoutTextXY(true);
                    offsetY = cell.textY;
                    text = object.messageText;
                    textLayoutBlocks = object.textLayoutBlocks;
                    if (cell != null && cell.linkPreviewAbove) {
                        offsetY += cell.linkPreviewHeight + AndroidUtilities.dp(10);
                    }
                }
                if (textLayoutBlocks == null || text == null) {
                    return 0;
                }
                for (int i = 0; i < textLayoutBlocks.size(); ++i) {
                    MessageObject.TextLayoutBlock block = textLayoutBlocks.get(i);
                    StaticLayout layout = block.textLayout;
                    String layoutText = layout.getText().toString();
                    if (index > block.charactersOffset) {
                        final float y;
                        if (index - block.charactersOffset > layoutText.length() - 1) {
                            y = offsetY + (int) (block.textYOffset(textLayoutBlocks, cell.transitionParams) + block.padTop + block.height);
                        } else {
                            y = offsetY + block.textYOffset(textLayoutBlocks, cell.transitionParams) + block.padTop + layout.getLineTop(layout.getLineForOffset(index - block.charactersOffset));
                        }
                        return (int) y;
                    }
                }
                return 0;
            }

            @Override
            public int getItemCount() {
                if (messages == null) {
                    return 0;
                }
                return messages.previewMessages.size();
            }
        }

        private MessageObject.GroupedMessages getValidGroupedMessage(MessageObject message) {
            MessageObject.GroupedMessages groupedMessages = null;
            if (message.getGroupId() != 0) {
                groupedMessages = messages.groupedMessagesMap.get(message.getGroupId());
                if (groupedMessages != null && (groupedMessages.messages.size() <= 1 || groupedMessages.getPosition(message) == null)) {
                    groupedMessages = null;
                }
            }
            return groupedMessages;
        }
    }

//    ValueAnimator tabsAnimator;
    TabsView tabsView;
    ViewPagerFixed viewPager;

    ValueAnimator offsetsAnimator;
    TLRPC.User currentUser;
    TLRPC.Chat currentChat;
    boolean showing;
    boolean isLandscapeMode;
    private final int currentAccount;
    boolean returnSendersNames;

    Runnable changeBoundsRunnable = new Runnable() {
        @Override
        public void run() {
            if (offsetsAnimator != null && !offsetsAnimator.isRunning()) {
                offsetsAnimator.start();
            }
        }
    };

    private final ArrayList<MessageObject.GroupedMessages> drawingGroups = new ArrayList<>(10);
    private final ResourcesDelegate resourcesProvider;

    @SuppressLint("ClickableViewAccessibility")
    public MessagePreviewView(@NonNull Context context, ChatActivity chatActivity, MessagePreviewParams params, TLRPC.User user, TLRPC.Chat chat, int currentAccount, ResourcesDelegate resourcesProvider, int startTab, boolean showOutdatedQuote)  {
        super(context);
        this.showOutdatedQuote = showOutdatedQuote;
        this.chatActivity = chatActivity;
        this.currentAccount = currentAccount;
        currentUser = user;
        currentChat = chat;
        messagePreviewParams = params;
        this.resourcesProvider = resourcesProvider;

        viewPager = new ViewPagerFixed(context, resourcesProvider) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                MessagePreviewView.this.tabsView.setSelectedTab(viewPager.getPositionAnimated());

                if (viewPages[0] instanceof Page) {
                    ((Page) viewPages[0]).textSelectionHelper.onParentScrolled();
                }
                if (viewPages[1] instanceof Page) {
                    ((Page) viewPages[1]).textSelectionHelper.onParentScrolled();
                }
            }

            @Override
            protected void onScrollEnd() {
                if (viewPages[0] instanceof Page) {
                    ((Page) viewPages[0]).textSelectionHelper.stopScrolling();
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (isTouchedHandle()) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }
        };

        tabsView = new TabsView(context, resourcesProvider);
        int p = 0;
        for (int i = 0; i < 3; ++i) {
            if (i == TAB_REPLY && params.replyMessage != null) {
                tabsView.addTab(TAB_REPLY, LocaleController.getString(R.string.MessageOptionsReply));
            } else if (i == TAB_FORWARD && params.forwardMessages != null && !showOutdatedQuote) {
                tabsView.addTab(TAB_FORWARD, LocaleController.getString(R.string.MessageOptionsForward));
            } else if (i == TAB_LINK && params.linkMessage != null && !showOutdatedQuote) {
                tabsView.addTab(TAB_LINK, LocaleController.getString(R.string.MessageOptionsLink));
            } else {
                continue;
            }
            if (i == startTab) {
                p = tabsView.tabs.size() - 1;
            }
        }

        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return tabsView.tabs.size();
            }

            @Override
            public int getItemViewType(int position) {
                return tabsView.tabs.get(position).id;
            }

            @Override
            public View createView(int viewType) {
                return new Page(context, viewType);
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind();
            }
        });
        viewPager.setPosition(p);
        tabsView.setSelectedTab(p);

//        if (tabsView.tabs.size() > 1) {
            addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
            addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 66));
//        } else {
//            addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
//        }

        tabsView.setOnTabClick(id -> {
            if (tabsView.tabs.get(viewPager.getCurrentPosition()).id == id) {
                return;
            }

            int pos = 0;
            for (int i = 0; i < tabsView.tabs.size(); ++i) {
                if (tabsView.tabs.get(i).id == id) {
                    pos = i;
                    break;
                }
            }

            if (viewPager.getCurrentPosition() == pos) {
                return;
            }

            viewPager.scrollToPosition(pos);
        });

        setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP && !showOutdatedQuote) {
                dismiss(true);
            }
            return true;
        });
        showing = true;
        setAlpha(0);
        setScaleX(0.95f);
        setScaleY(0.95f);
        animate().alpha(1f).scaleX(1f).setDuration(ChatListItemAnimator.DEFAULT_DURATION).setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR).scaleY(1f);

        updateColors();
    }

    private void updateColors() {

    }

    public void dismiss(boolean canShowKeyboard) {
        if (showing) {
            showing = false;
            animate().alpha(0).scaleX(0.95f).scaleY(0.95f).setDuration(ChatListItemAnimator.DEFAULT_DURATION).setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (getParent() != null) {
                        ViewGroup parent = (ViewGroup) getParent();
                        parent.removeView(MessagePreviewView.this);
                    }
                    onFullDismiss(canShowKeyboard);
                }
            });
            for (int i = 0; i < viewPager.viewPages.length; ++i) {
                if (viewPager.viewPages[i] instanceof Page) {
                    Page page = (Page) viewPager.viewPages[i];
                    if (page.currentTab == TAB_REPLY) {
                        page.updateSelection();
                        break;
                    }
                }
            }
            onDismiss(canShowKeyboard);
        }
    }

    protected void onQuoteSelectedPart() {

    }

    protected void onDismiss(boolean canShowKeyboard) {

    }

    protected void onFullDismiss(boolean canShowKeyboard) {

    }

    public boolean isShowing() {
        return showing;
    }

    protected void removeLink() {

    }

    protected void removeReply() {

    }

    protected void removeQuote() {

    }

    protected void removeForward() {

    }

    protected void selectAnotherChat(boolean forward) {

    }

    protected void didSendPressed() {

    }

    private static class TabsView extends View {
        private static class Tab {
            final int id;
            final Text text;
            final RectF bounds = new RectF();
            final RectF clickBounds = new RectF();

            public Tab(int id, String name) {
                this.id = id;
                text = new Text(name, 14, AndroidUtilities.bold());
            }
        }

        public final ArrayList<Tab> tabs = new ArrayList<>();
//        private final AnimatedFloat tab = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private float selectedTab;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;
        private int selectedColor;

        public TabsView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            if (Theme.isCurrentThemeDark()) {
                color = 0x90ffffff;
                selectedColor = 0xb0ffffff;
                bgPaint.setColor(0x10ffffff);
            } else {
                int wallpaperColor = Theme.getColor(Theme.key_chat_wallpaper, resourcesProvider);
                if (resourcesProvider instanceof ChatActivity.ThemeDelegate && ((ChatActivity.ThemeDelegate) resourcesProvider).getWallpaperDrawable() instanceof MotionBackgroundDrawable) {
                    MotionBackgroundDrawable background = (MotionBackgroundDrawable) ((ChatActivity.ThemeDelegate) resourcesProvider).getWallpaperDrawable();
                    int[] colors = background.getColors();
                    if (colors != null) {
                        wallpaperColor = AndroidUtilities.getAverageColor(
                            AndroidUtilities.getAverageColor(colors[0], colors[1]),
                            AndroidUtilities.getAverageColor(colors[2], colors[3])
                        );
                    }
                }
                color = Theme.adaptHue(0xa0434e3b, wallpaperColor);
                selectedColor = Theme.adaptHue(0xe5434e3b, wallpaperColor);
                bgPaint.setColor(Theme.adaptHue(0x30939C78, wallpaperColor));
            }
        }

        public boolean containsTab(int id) {
            for (int i = 0; i < tabs.size(); ++i) {
                if (tabs.get(i).id == id) {
                    return true;
                }
            }
            return false;
        }

        public void addTab(int id, String text) {
            tabs.add(new Tab(id, text));
        }

        public void setSelectedTab(float position) {
            selectedTab = position;
            invalidate();
        }

        private float tabInnerPadding = dp(12);
        private float marginBetween = dp(13);
        private RectF selectRect = new RectF();

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            tabInnerPadding = dp(12);
            marginBetween = dp(13);
            float W = 0;
            for (int i = 0; i < tabs.size(); ++i) {
                if (i > 0) {
                    W += marginBetween;
                }
                W += tabInnerPadding + tabs.get(i).text.getWidth() + tabInnerPadding;
            }

            int fullWidth = getMeasuredWidth();
            int fullHeight = getMeasuredHeight();
            float top    = (fullHeight - dp(26)) / 2f,
                  bottom = (fullHeight + dp(26)) / 2f;
            float x = (fullWidth - W) / 2f;
            for (int i = 0; i < tabs.size(); ++i) {
                float w = tabInnerPadding + tabs.get(i).text.getWidth() + tabInnerPadding;
                tabs.get(i).bounds.set(x, top, x + w, bottom);
                tabs.get(i).clickBounds.set(tabs.get(i).bounds);
                tabs.get(i).clickBounds.inset(-marginBetween / 2f, -top);
                x += w + marginBetween;
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (tabs.size() <= 1) {
                return;
            }

            float tabT = selectedTab;
            int lower = (int) Math.floor(tabT);
            boolean lowerInRange = lower >= 0 && lower < tabs.size();
            int higher = (int) Math.ceil(tabT);
            boolean higherInRange = higher >= 0 && higher < tabs.size();

            if (lowerInRange && higherInRange) {
                AndroidUtilities.lerp(
                    tabs.get(lower).bounds,
                    tabs.get(higher).bounds,
                    tabT - lower,
                    selectRect
                );
            } else if (lowerInRange) {
                selectRect.set(tabs.get(lower).bounds);
            } else if (higherInRange) {
                selectRect.set(tabs.get(higher).bounds);
            }
            if (lowerInRange || higherInRange) {
                canvas.drawRoundRect(selectRect, dp(13), dp(13), bgPaint);
            }

            for (int i = 0; i < tabs.size(); ++i) {
                Tab tab = tabs.get(i);
                tab.text.draw(canvas, tab.bounds.left + tabInnerPadding, getMeasuredHeight() / 2f, ColorUtils.blendARGB(color, selectedColor, 1f - Math.abs(tabT - i)), 1f);
            }
        }

        private Utilities.Callback<Integer> onTabClick;
        public void setOnTabClick(Utilities.Callback<Integer> onTabClick) {
            this.onTabClick = onTabClick;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (tabs.size() <= 1) {
                return false;
            }
            int tab = getHitTab(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return tab != -1;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (tab != -1 && onTabClick != null) {
                    onTabClick.run(tab);
                }
            }
            return false;
        }

        private int getHitTab(float x, float y) {
            for (int i = 0; i < tabs.size(); ++i) {
                if (tabs.get(i).clickBounds.contains(x, y)) {
                    return tabs.get(i).id;
                }
            }
            return -1;
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private class ActionBar extends FrameLayout {
        private Theme.ResourcesProvider resourcesProvider;

        private boolean forward;
        private final AnimatedTextView.AnimatedTextDrawable title;
        private final AnimatedTextView.AnimatedTextDrawable subtitle;

        public ActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            title = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
            title.setAnimationProperties(.3f, 0, 430, CubicBezierInterpolator.EASE_OUT_QUINT);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle, resourcesProvider));
            title.setTextSize(dp(18));
            title.setEllipsizeByGradient(!LocaleController.isRTL);
            title.setCallback(this);
            title.setOverrideFullWidth(AndroidUtilities.displaySize.x);

            subtitle = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
            subtitle.setAnimationProperties(.3f, 0, 430, CubicBezierInterpolator.EASE_OUT_QUINT);
            subtitle.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle, resourcesProvider));
            subtitle.setTextSize(dp(14));
            subtitle.setEllipsizeByGradient(!LocaleController.isRTL);
            subtitle.setCallback(this);
            subtitle.setOverrideFullWidth(AndroidUtilities.displaySize.x);
        }

        public void setAnimationForward(boolean forward) {
            this.forward = forward;
            invalidate();
        }

        public void setTitle(CharSequence text, boolean animated) {
            title.setText(text, animated && !LocaleController.isRTL);
        }

        public void setSubtitle(CharSequence text, boolean animated) {
            subtitle.setText(text, animated && !LocaleController.isRTL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
            setPadding(dp(18), 0, dp(18), 0);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return title == who || subtitle == who || super.verifyDrawable(who);
        }

        private void setBounds(Drawable drawable, float cy) {
            drawable.setBounds(
                getPaddingLeft(),
                (int) cy - dp(32),
                getMeasuredWidth() - getPaddingRight(),
                (int) cy + dp(32)
            );
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            setBounds(title, AndroidUtilities.lerp(dp(29), dp(18.83f), subtitle.isNotEmpty()));
            title.draw(canvas);

            setBounds(subtitle, dp(39.5f));
            subtitle.draw(canvas);
        }
    }

    public void updateLink() {
        for (int i = 0; i < viewPager.viewPages.length; ++i) {
            if (viewPager.viewPages[i] != null && ((Page) viewPager.viewPages[i]).currentTab == TAB_LINK) {
                Page page = (Page) viewPager.viewPages[i];
                page.changeSizeBtnContainer.setVisibility(messagePreviewParams.singleLink && !messagePreviewParams.hasMedia ? View.GONE : View.VISIBLE);
                page.changeSizeBtn.setVisibility(messagePreviewParams.isVideo ? View.INVISIBLE : View.VISIBLE);
                page.videoChangeSizeBtn.setVisibility(!messagePreviewParams.isVideo ? View.INVISIBLE : View.VISIBLE);
                page.changeSizeBtnContainer.animate().alpha(messagePreviewParams.hasMedia ? 1f : .5f).start();
                page.changeSizeBtn.setState(messagePreviewParams.webpageSmall, true);
                page.videoChangeSizeBtn.setState(messagePreviewParams.webpageSmall, true);
                page.changePositionBtn.setState(!messagePreviewParams.webpageTop, true);
                page.updateMessages();
            }
        }
    }

    public void updateAll() {
        for (int i = 0; i < viewPager.viewPages.length; ++i) {
            if (viewPager.viewPages[i] instanceof Page) {
                Page page = (Page) viewPager.viewPages[i];
                if (page.currentTab == TAB_FORWARD) {
                    page.messages = messagePreviewParams.forwardMessages;
                } else if (page.currentTab == TAB_REPLY) {
                    page.messages = messagePreviewParams.replyMessage;
                } else if (page.currentTab == TAB_LINK) {
                    page.messages = messagePreviewParams.linkMessage;
                }
                page.updateMessages();
                if (page.currentTab == TAB_REPLY) {
                    if (showOutdatedQuote && !messagePreviewParams.isSecret) {
                        MessageObject msg = null;
                        if (page.textSelectionHelper.getSelectedCell() != null) {
                            msg = page.textSelectionHelper.getSelectedCell().getMessageObject();
                        }
                        msg = page.getReplyMessage(msg);
                        if (msg != null) {
                            messagePreviewParams.quoteStart = 0;
                            messagePreviewParams.quoteEnd = Math.min(MessagesController.getInstance(currentAccount).quoteLengthMax, msg.messageOwner.message.length());
                            messagePreviewParams.quote = ChatActivity.ReplyQuote.from(msg, messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                            page.textSelectionHelper.select(page.getReplyMessageCell(), messagePreviewParams.quoteStart, messagePreviewParams.quoteEnd);
                        }
                    } else {
                        messagePreviewParams.quote = null;
                        page.textSelectionHelper.clear();
                        page.switchToQuote(false, true);
                    }
                    page.updateSubtitle(true);
                }
                if (page.changeSizeBtn != null) {
                    page.changeSizeBtn.animate().alpha(messagePreviewParams.hasMedia ? 1f : .5f).start();
                }
            }
        }
    }

    public boolean isTouchedHandle() {
        for (int i = 0; i < viewPager.viewPages.length; ++i) {
            if (viewPager.viewPages[i] != null && ((Page) viewPager.viewPages[i]).currentTab == TAB_REPLY) {
                return ((Page) viewPager.viewPages[i]).textSelectionHelper.isTouched();
            }
        }
        return false;
    }

    public static class ToggleButton extends View {
        AnimatedTextView.AnimatedTextDrawable textDrawable;
        RLottieToggleDrawable iconDrawable;

        private boolean first = true;
        private boolean isState1;
        final String text1, text2;
        final int minWidth;

        public ToggleButton(
            Context context,
            int iconRes1, String text1,
            int iconRes2, String text2,
            Theme.ResourcesProvider resourcesProvider
        ) {
            super(context);

            this.text1 = text1;
            this.text2 = text2;

            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));

            textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
            textDrawable.setAnimationProperties(.35f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            textDrawable.setTextSize(dp(16));
            textDrawable.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
            textDrawable.setCallback(this);
            textDrawable.setEllipsizeByGradient(!LocaleController.isRTL);
            if (LocaleController.isRTL) {
                textDrawable.setGravity(Gravity.RIGHT);
            }
            minWidth = (int) (dp(59 + 18) + Math.max(textDrawable.getPaint().measureText(text1), textDrawable.getPaint().measureText(text2)));
            textDrawable.setOverrideFullWidth(minWidth);

            iconDrawable = new RLottieToggleDrawable(this, iconRes1, iconRes2);
            iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }

        public void setState(boolean state1, boolean animated) {
            if (!first && state1 == isState1) {
                return;
            }
            isState1 = state1;
            textDrawable.setText(state1 ? text1 : text2, animated && !LocaleController.isRTL);
            iconDrawable.setState(state1, animated);
            first = false;
        }

        public boolean getState() {
            return isState1;
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == textDrawable || super.verifyDrawable(who);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (LocaleController.isRTL) {
                iconDrawable.setBounds(
                    getMeasuredWidth() - dp(17 + 24),
                    (getMeasuredHeight() - dp(24)) / 2,
                    getMeasuredWidth() - dp(17),
                    (getMeasuredHeight() + dp(24)) / 2
                );
                textDrawable.setBounds(
                    0,
                    0,
                    getMeasuredWidth() - dp(59),
                    getMeasuredHeight()
                );
            } else {
                iconDrawable.setBounds(
                    dp(17),
                    (getMeasuredHeight() - dp(24)) / 2,
                    dp(17 + 24),
                    (getMeasuredHeight() + dp(24)) / 2
                );
                textDrawable.setBounds(
                    dp(59),
                    0,
                    getMeasuredWidth(),
                    getMeasuredHeight()
                );
            }

            textDrawable.draw(canvas);
            iconDrawable.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(
                    widthMode == MeasureSpec.EXACTLY ?
                        Math.max(MeasureSpec.getSize(widthMeasureSpec), minWidth) :
                        Math.min(MeasureSpec.getSize(widthMeasureSpec), minWidth),
                    widthMode
                ),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (getVisibility() != View.VISIBLE || getAlpha() < .5f) {
                return false;
            }
            return super.onTouchEvent(event);
        }
    }

    private static  class RLottieToggleDrawable extends Drawable {

        private RLottieDrawable state1, state2;
        private RLottieDrawable currentState;

        // R.raw.media_shrink, R.raw.media_enlarge
        public RLottieToggleDrawable(View view, int state1Res, int state2Res) {
            state1 = new RLottieDrawable(state1Res, "" + state1Res, dp(24), dp(24));
            state1.setMasterParent(view);
            state1.setAllowDecodeSingleFrame(true);
            state1.setPlayInDirectionOfCustomEndFrame(true);
            state1.setAutoRepeat(0);

            state2 = new RLottieDrawable(state2Res, "" + state2Res, dp(24), dp(24));
            state2.setMasterParent(view);
            state2.setAllowDecodeSingleFrame(true);
            state2.setPlayInDirectionOfCustomEndFrame(true);
            state2.setAutoRepeat(0);

            currentState = state1;
        }

        private boolean detached;
        public void detach() {
            detached = true;
            state1.recycle(true);
            state2.recycle(true);
        }

        private boolean isState1;
        public void setState(boolean isState1, boolean animated) {
            this.isState1 = isState1;
            if (animated) {
                currentState = isState1 ? state1 : state2;
                state1.setCurrentFrame(0);
                state2.setCurrentFrame(0);
                currentState.start();
            } else {
                currentState = isState1 ? state1 : state2;
                currentState.setCurrentFrame(currentState.getFramesCount() - 1);
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (detached) {
                return;
            }
            AndroidUtilities.rectTmp2.set(
                getBounds().centerX() - dp(12),
                getBounds().centerY() - dp(12),
                getBounds().centerX() + dp(12),
                getBounds().centerY() + dp(12)
            );
            if (currentState.isLastFrame() && currentState != (isState1 ? state1 : state2)) {
                currentState = isState1 ? state1 : state2;
                currentState.setCurrentFrame(currentState.getFramesCount() - 1);
            }
            currentState.setBounds(AndroidUtilities.rectTmp2);
            currentState.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            state1.setAlpha(alpha);
            state2.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            state1.setColorFilter(colorFilter);
            state2.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }
    }
}
