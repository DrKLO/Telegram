package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ForwardingMessagesParams;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;

public class ForwardingPreviewView extends FrameLayout {

    public interface ResourcesDelegate extends Theme.ResourcesProvider {

        Drawable getWallpaperDrawable();

        boolean isWallpaperMotion();
    }

    SizeNotifierFrameLayout chatPreviewContainer;
    ActionBar actionBar;
    RecyclerListView chatListView;
    ChatListItemAnimator itemAnimator;
    GridLayoutManagerFixed chatLayoutManager;

    ForwardingMessagesParams forwardingMessagesParams;
    Adapter adapter;

    ScrollView menuScrollView;
    LinearLayout menuContainer;
    LinearLayout buttonsLayout;
    LinearLayout buttonsLayout2;

    ActionBarMenuSubItem showSendersNameView;
    ActionBarMenuSubItem hideSendersNameView;

    ActionBarMenuSubItem showCaptionView;
    ActionBarMenuSubItem hideCaptionView;

    ActionBarMenuSubItem changeRecipientView;
    ActionBarMenuSubItem sendMessagesView;

    int chatTopOffset;
    float yOffset;
    int currentTopOffset;
    float currentYOffset;

    ArrayList<ActionBarMenuSubItem> actionItems = new ArrayList<>();

    Rect rect = new Rect();

    private boolean firstLayout = true;
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
    public ForwardingPreviewView(@NonNull Context context, ForwardingMessagesParams params, TLRPC.User user, TLRPC.Chat chat, int currentAccount, ResourcesDelegate resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        currentUser = user;
        currentChat = chat;
        forwardingMessagesParams = params;
        this.resourcesProvider = resourcesProvider;

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
                    outline.setRoundRect(0, (int) (currentTopOffset + 1), view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(6));
                }
            });
            chatPreviewContainer.setClipToOutline(true);
            chatPreviewContainer.setElevation(AndroidUtilities.dp(4));
        }

        actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setOccupyStatusBar(false);

        chatListView = new RecyclerListView(context, resourcesProvider) {

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) child;
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    cell.drawCheckBox(canvas);
                    canvas.save();
                    canvas.translate(cell.getX(), cell.getY());
                    cell.drawMessageText(canvas, cell.getMessageObject().textLayoutBlocks, true, 1f, false);

                    if (cell.getCurrentMessagesGroup() != null || cell.getTransitionParams().animateBackgroundBoundsInner) {
                        cell.drawNamesLayout(canvas, 1f);
                    }
                    if ((cell.getCurrentPosition() != null && cell.getCurrentPosition().last) || cell.getTransitionParams().animateBackgroundBoundsInner) {
                        cell.drawTime(canvas, 1f, true);
                    }
                    if ((cell.getCurrentPosition() != null && cell.getCurrentPosition().last) || cell.getCurrentPosition() == null) {
                        cell.drawCaptionLayout(canvas, false, 1f);
                    }
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
                super.onLayout(changed, l, t, r, b);
                updatePositions();
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
                            int top = (cell.getTop() + cell.getBackgroundDrawableTop());
                            int bottom = (cell.getTop() + cell.getBackgroundDrawableBottom());

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                top -= AndroidUtilities.dp(10);
                            }

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                bottom += AndroidUtilities.dp(10);
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

                        if (t < -AndroidUtilities.dp(20)) {
                            t = -AndroidUtilities.dp(20);
                        }

                        if (b > chatListView.getMeasuredHeight() + AndroidUtilities.dp(20)) {
                            b = chatListView.getMeasuredHeight() + AndroidUtilities.dp(20);
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
            }
        });
        chatListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (forwardingMessagesParams.previewMessages.size() <= 1) {
                    return;
                }
                int id = params.previewMessages.get(position).getId();
                boolean newSelected = !params.selectedIds.get(id, false);
                if (forwardingMessagesParams.selectedIds.size() == 1 && !newSelected) {
                    return;
                }
                if (!newSelected) {
                    params.selectedIds.delete(id);
                } else {
                    params.selectedIds.put(id, newSelected);
                }
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setChecked(newSelected, newSelected, true);
                actionBar.setTitle(LocaleController.formatPluralString("PreviewForwardMessagesCount", params.selectedIds.size()));
            }
        });

        chatListView.setAdapter(adapter = new Adapter());
        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        chatLayoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {

            @Override
            public boolean shouldLayoutChildFromOpositeSide(View child) {
                return false;
            }

            @Override
            protected boolean hasSiblingChild(int position) {
                MessageObject message = params.previewMessages.get(position);
                MessageObject.GroupedMessages group = getValidGroupedMessage(message);
                if (group != null) {
                    MessageObject.GroupedMessagePosition pos = group.positions.get(message);
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
                if (idx >= 0 && idx < params.previewMessages.size()) {
                    MessageObject message = params.previewMessages.get(idx);
                    MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
                    if (groupedMessages != null) {
                        return groupedMessages.positions.get(message).spanSize;
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
                                    h -= (int) Math.ceil(maxHeight * pos.ph) - AndroidUtilities.dp(4);
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

        menuScrollView = new ScrollView(context);
        addView(menuScrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        menuContainer = new LinearLayout(context);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuScrollView.addView(menuContainer);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        Drawable shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        buttonsLayout.setBackground(shadowDrawable);
        menuContainer.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        showSendersNameView = new ActionBarMenuSubItem(context, true, true, false, resourcesProvider);
        buttonsLayout.addView(showSendersNameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        showSendersNameView.setTextAndIcon(forwardingMessagesParams.multiplyUsers ? LocaleController.getString("ShowSenderNames", R.string.ShowSenderNames) : LocaleController.getString("ShowSendersName", R.string.ShowSendersName), 0);
        showSendersNameView.setChecked(true);

        hideSendersNameView = new ActionBarMenuSubItem(context, true, false, !params.hasCaption, resourcesProvider);
        buttonsLayout.addView(hideSendersNameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        hideSendersNameView.setTextAndIcon(forwardingMessagesParams.multiplyUsers ? LocaleController.getString("HideSenderNames", R.string.HideSenderNames) : LocaleController.getString("HideSendersName", R.string.HideSendersName), 0);
        hideSendersNameView.setChecked(false);

        if (forwardingMessagesParams.hasCaption) {
            View dividerView = new View(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(2, MeasureSpec.EXACTLY));
                }
            };
            dividerView.setBackgroundColor(getThemedColor(Theme.key_divider));
            buttonsLayout.addView(dividerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


            showCaptionView = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
            buttonsLayout.addView(showCaptionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
            showCaptionView.setTextAndIcon(LocaleController.getString("ShowCaption", R.string.ShowCaption), 0);
            showCaptionView.setChecked(true);

            hideCaptionView = new ActionBarMenuSubItem(context, true, false, true, resourcesProvider);
            buttonsLayout.addView(hideCaptionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
            hideCaptionView.setTextAndIcon(LocaleController.getString("HideCaption", R.string.HideCaption), 0);
            hideCaptionView.setChecked(false);
        }

        buttonsLayout2 = new LinearLayout(context);
        buttonsLayout2.setOrientation(LinearLayout.VERTICAL);
        shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        buttonsLayout2.setBackground(shadowDrawable);
        menuContainer.addView(buttonsLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, forwardingMessagesParams.hasSenders ? -8 : 0, 0, 0));

        changeRecipientView = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
        buttonsLayout2.addView(changeRecipientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        changeRecipientView.setTextAndIcon(LocaleController.getString("ChangeRecipient", R.string.ChangeRecipient), R.drawable.msg_forward_replace);

        sendMessagesView = new ActionBarMenuSubItem(context, false, true, resourcesProvider);
        buttonsLayout2.addView(sendMessagesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        sendMessagesView.setTextAndIcon(LocaleController.getString("ForwardSendMessages", R.string.ForwardSendMessages), R.drawable.msg_forward_send);

        if (forwardingMessagesParams.hasSenders) {
            actionItems.add(showSendersNameView);
            actionItems.add(hideSendersNameView);

            if (params.hasCaption) {
                actionItems.add(showCaptionView);
                actionItems.add(hideCaptionView);
            }
        }

        actionItems.add(changeRecipientView);
        actionItems.add(sendMessagesView);

        showSendersNameView.setOnClickListener(view -> {
            if (params.hideForwardSendersName) {
                returnSendersNames = false;
                showSendersNameView.setChecked(true);
                hideSendersNameView.setChecked(false);
                if (showCaptionView != null) {
                    showCaptionView.setChecked(true);
                    hideCaptionView.setChecked(false);
                }
                params.hideForwardSendersName = false;
                params.hideCaption = false;
                updateMessages();
                updateSubtitle();
            }
        });

        hideSendersNameView.setOnClickListener(view -> {
            if (!params.hideForwardSendersName) {
                returnSendersNames = false;
                showSendersNameView.setChecked(false);
                hideSendersNameView.setChecked(true);
                params.hideForwardSendersName = true;
                updateMessages();
                updateSubtitle();
            }
        });

        if (params.hasCaption) {
            showCaptionView.setOnClickListener(view -> {
                if (params.hideCaption) {
                    if (returnSendersNames) {
                        params.hideForwardSendersName = false;
                    }
                    returnSendersNames = false;
                    showCaptionView.setChecked(true);
                    hideCaptionView.setChecked(false);
                    showSendersNameView.setChecked(!params.hideForwardSendersName);
                    hideSendersNameView.setChecked(params.hideForwardSendersName);
                    params.hideCaption = false;
                    updateMessages();
                    updateSubtitle();
                }
            });

            hideCaptionView.setOnClickListener(view -> {
                if (!params.hideCaption) {
                    showCaptionView.setChecked(false);
                    hideCaptionView.setChecked(true);
                    showSendersNameView.setChecked(false);
                    hideSendersNameView.setChecked(true);
                    if (!params.hideForwardSendersName) {
                        params.hideForwardSendersName = true;
                        returnSendersNames = true;
                    }
                    params.hideCaption = true;
                    updateMessages();
                    updateSubtitle();
                }
            });
        }

        showSendersNameView.setChecked(!params.hideForwardSendersName);
        hideSendersNameView.setChecked(params.hideForwardSendersName);

        if (params.hasCaption) {
            showCaptionView.setChecked(!params.hideCaption);
            hideCaptionView.setChecked(params.hideCaption);
        }

        if (!params.hasSenders) {
            buttonsLayout.setVisibility(View.GONE);
        }

        sendMessagesView.setOnClickListener(View -> didSendPressed());
        changeRecipientView.setOnClickListener(view -> selectAnotherChat());

        updateMessages();
        updateSubtitle();
        actionBar.setTitle(LocaleController.formatPluralString("PreviewForwardMessagesCount", params.selectedIds.size()));

        menuScrollView.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                dismiss(true);
            }
            return true;
        });
        setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
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

    private void updateSubtitle() {
        if (!forwardingMessagesParams.hasSenders) {
            if (forwardingMessagesParams.willSeeSenders) {
                if (currentUser != null) {
                    actionBar.setSubtitle(LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
                } else {
                    if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                        actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameVisibleChannel", R.string.ForwardPreviewSendersNameVisibleChannel));
                    } else {
                        actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameVisibleGroup", R.string.ForwardPreviewSendersNameVisibleGroup));
                    }
                }
            } else {
                if (currentUser != null) {
                    actionBar.setSubtitle(LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
                } else {
                    if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                        actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameHiddenChannel", R.string.ForwardPreviewSendersNameHiddenChannel));
                    } else {
                        actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameHiddenGroup", R.string.ForwardPreviewSendersNameHiddenGroup));
                    }
                }
            }
        } else if (!forwardingMessagesParams.hideForwardSendersName) {
            if (currentUser != null) {
                actionBar.setSubtitle(LocaleController.formatString("ForwardPreviewSendersNameVisible", R.string.ForwardPreviewSendersNameVisible, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
            } else {
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameVisibleChannel", R.string.ForwardPreviewSendersNameVisibleChannel));
                } else {
                    actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameVisibleGroup", R.string.ForwardPreviewSendersNameVisibleGroup));
                }
            }
        } else {
            if (currentUser != null) {
                actionBar.setSubtitle(LocaleController.formatString("ForwardPreviewSendersNameHidden", R.string.ForwardPreviewSendersNameHidden, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
            } else {
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameHiddenChannel", R.string.ForwardPreviewSendersNameHiddenChannel));
                } else {
                    actionBar.setSubtitle(LocaleController.getString("ForwardPreviewSendersNameHiddenGroup", R.string.ForwardPreviewSendersNameHiddenGroup));
                }
            }
        }
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
                        parent.removeView(ForwardingPreviewView.this);
                    }
                }
            });
            onDismiss(canShowKeyboard);
        }
    }

    protected void onDismiss(boolean canShowKeyboard) {

    }

    boolean updateAfterAnimations;

    private void updateMessages() {
        if (itemAnimator.isRunning()) {
            updateAfterAnimations = true;
            return;
        }
        for (int i = 0; i < forwardingMessagesParams.previewMessages.size(); i++) {
            MessageObject messageObject = forwardingMessagesParams.previewMessages.get(i);
            messageObject.forceUpdate = true;
            if (!forwardingMessagesParams.hideForwardSendersName) {
                messageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_FWD;
            } else {
                messageObject.messageOwner.flags &= ~TLRPC.MESSAGE_FLAG_FWD;
            }
            if (forwardingMessagesParams.hideCaption) {
                messageObject.caption = null;
            } else {
                messageObject.generateCaption();
            }

            if (messageObject.isPoll()) {
                ForwardingMessagesParams.PreviewMediaPoll mediaPoll = (ForwardingMessagesParams.PreviewMediaPoll) messageObject.messageOwner.media;
                mediaPoll.results.total_voters = forwardingMessagesParams.hideCaption ? 0 : mediaPoll.totalVotersCached;
            }
        }
        for (int i = 0; i < forwardingMessagesParams.pollChoosenAnswers.size(); i++) {
            forwardingMessagesParams.pollChoosenAnswers.get(i).chosen = !forwardingMessagesParams.hideForwardSendersName;
        }
        for (int i = 0; i < forwardingMessagesParams.groupedMessagesMap.size(); i++) {
            itemAnimator.groupWillChanged(forwardingMessagesParams.groupedMessagesMap.valueAt(i));
        }
        adapter.notifyItemRangeChanged(0, forwardingMessagesParams.previewMessages.size());
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxActionWidth = 0;
        isLandscapeMode = MeasureSpec.getSize(widthMeasureSpec) > MeasureSpec.getSize(heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (isLandscapeMode) {
            width = (int) (MeasureSpec.getSize(widthMeasureSpec) * 0.38f);
        }
        for (int i = 0; i < actionItems.size(); i++) {
            actionItems.get(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
            if (actionItems.get(i).getMeasuredWidth() > maxActionWidth) {
                maxActionWidth = actionItems.get(i).getMeasuredWidth();
            }
        }
        buttonsLayout.getBackground().getPadding(rect);
        int buttonsWidth = maxActionWidth + rect.left + rect.right;
        buttonsLayout.getLayoutParams().width = buttonsWidth;
        buttonsLayout2.getLayoutParams().width = buttonsWidth;

        buttonsLayout.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
        buttonsLayout2.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));

        ((MarginLayoutParams) chatListView.getLayoutParams()).topMargin = ActionBar.getCurrentActionBarHeight();
        if (isLandscapeMode) {
            chatPreviewContainer.getLayoutParams().height = LayoutHelper.MATCH_PARENT;
            ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).topMargin = AndroidUtilities.dp(8);
            ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).bottomMargin = AndroidUtilities.dp(8);
            chatPreviewContainer.getLayoutParams().width = (int) Math.min(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(340), MeasureSpec.getSize(widthMeasureSpec) * 0.6f));
            menuScrollView.getLayoutParams().height = LayoutHelper.MATCH_PARENT;
        } else {
            ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).topMargin = 0;
            ((MarginLayoutParams) chatPreviewContainer.getLayoutParams()).bottomMargin = 0;
            chatPreviewContainer.getLayoutParams().height = MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(16 - 10) - buttonsLayout.getMeasuredHeight() - buttonsLayout2.getMeasuredHeight();
            if (chatPreviewContainer.getLayoutParams().height < MeasureSpec.getSize(heightMeasureSpec) * 0.5f) {
                chatPreviewContainer.getLayoutParams().height = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.5f);
            }
            chatPreviewContainer.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
            menuScrollView.getLayoutParams().height = MeasureSpec.getSize(heightMeasureSpec) - chatPreviewContainer.getLayoutParams().height;
        }

        int size = MeasureSpec.getSize(widthMeasureSpec) + MeasureSpec.getSize(heightMeasureSpec) << 16;
        if (lastSize != size) {
            for (int i = 0; i < forwardingMessagesParams.previewMessages.size(); i++) {
                if (isLandscapeMode) {
                    forwardingMessagesParams.previewMessages.get(i).parentWidth = chatPreviewContainer.getLayoutParams().width;
                } else {
                    forwardingMessagesParams.previewMessages.get(i).parentWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(16);
                }
                forwardingMessagesParams.previewMessages.get(i).resetLayout();
                forwardingMessagesParams.previewMessages.get(i).forceUpdate = true;
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


    private void updatePositions() {
        int lastTopOffset = chatTopOffset;
        float lastYOffset = yOffset;

        if (!isLandscapeMode) {
            if (chatListView.getChildCount() == 0 || chatListView.getChildCount() > forwardingMessagesParams.previewMessages.size()) {
                chatTopOffset = 0;
            } else {
                int minTop = chatListView.getChildAt(0).getTop();
                for (int i = 1; i < chatListView.getChildCount(); i++) {
                    if (chatListView.getChildAt(i).getTop() < minTop) {
                        minTop = chatListView.getChildAt(i).getTop();
                    }
                }
                minTop -= AndroidUtilities.dp(4);
                if (minTop < 0) {
                    chatTopOffset = 0;
                } else {
                    chatTopOffset = minTop;
                }
            }

            float totalViewsHeight = buttonsLayout.getMeasuredHeight() + buttonsLayout2.getMeasuredHeight() - AndroidUtilities.dp(8) + (chatPreviewContainer.getMeasuredHeight() - chatTopOffset);
            float totalHeight = getMeasuredHeight() - AndroidUtilities.dp(16);
            yOffset = AndroidUtilities.dp(8) + (totalHeight - totalViewsHeight) / 2 - chatTopOffset;
            if (yOffset > AndroidUtilities.dp(8)) {
                yOffset = AndroidUtilities.dp(8);
            }
            float buttonX = getMeasuredWidth() - menuScrollView.getMeasuredWidth();
            menuScrollView.setTranslationX(buttonX);
        } else {
            yOffset = 0;
            chatTopOffset = 0;
            menuScrollView.setTranslationX(chatListView.getMeasuredWidth() + AndroidUtilities.dp(8));
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
            menuScrollView.setTranslationY(0);
        } else {
            actionBar.setTranslationY(chatTopOffset);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                chatPreviewContainer.invalidateOutline();
            }
            chatPreviewContainer.setTranslationY(yOffset);
            menuScrollView.setTranslationY(yOffset + chatPreviewContainer.getMeasuredHeight() - AndroidUtilities.dp(2));
        }
    }

    public boolean isShowing() {
        return showing;
    }

    private class Adapter extends RecyclerView.Adapter {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ChatMessageCell chatMessageCell = new ChatMessageCell(parent.getContext(), false, resourcesProvider);
            return new RecyclerListView.Holder(chatMessageCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            cell.setInvalidateSpoilersParent(forwardingMessagesParams.hasSpoilers);
            cell.setParentViewSize(chatListView.getMeasuredWidth(), chatListView.getMeasuredHeight());
            int id = cell.getMessageObject() != null ? cell.getMessageObject().getId() : 0;
            cell.setMessageObject(forwardingMessagesParams.previewMessages.get(position), forwardingMessagesParams.groupedMessagesMap.get(forwardingMessagesParams.previewMessages.get(position).getGroupId()), true, true);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

            });

            if (forwardingMessagesParams.previewMessages.size() > 1) {
                cell.setCheckBoxVisible(true, false);
                boolean animated = id == forwardingMessagesParams.previewMessages.get(position).getId();
                boolean checked = forwardingMessagesParams.selectedIds.get(forwardingMessagesParams.previewMessages.get(position).getId(), false);
                cell.setChecked(checked, checked, animated);
            }

        }

        @Override
        public int getItemCount() {
            return forwardingMessagesParams.previewMessages.size();
        }
    }

    protected void selectAnotherChat() {

    }

    protected void didSendPressed() {

    }

    private MessageObject.GroupedMessages getValidGroupedMessage(MessageObject message) {
        MessageObject.GroupedMessages groupedMessages = null;
        if (message.getGroupId() != 0) {
            groupedMessages = forwardingMessagesParams.groupedMessagesMap.get(message.getGroupId());
            if (groupedMessages != null && (groupedMessages.messages.size() <= 1 || groupedMessages.positions.get(message) == null)) {
                groupedMessages = null;
            }
        }
        return groupedMessages;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
