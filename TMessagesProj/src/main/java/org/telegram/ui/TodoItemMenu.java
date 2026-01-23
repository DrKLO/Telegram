package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AudioVisualizerDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EarListener;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessagePreviewView;
import org.telegram.ui.Components.MessagePrivateSeenView;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.TimerParticles;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.recorder.HintView2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TodoItemMenu extends Dialog {

    public final Context context;
    public final Theme.ResourcesProvider resourcesProvider;

    private FrameLayout windowView;
    private FrameLayout containerView;
    private FrameLayout menuContainer;
    private ViewPagerFixed viewPager;
    private TextView hintTextView;
    private MessagePreviewView.TabsView tabsView;

    private final Rect insets = new Rect();
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;

    private boolean open;
    private float openProgress;
    private float openProgress2;

    public TodoItemMenu(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.context = context;
        this.resourcesProvider = resourcesProvider;

        windowView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (openProgress > 0 && blurBitmapPaint != null) {
                    blurMatrix.reset();
                    final float s = (float) getWidth() / blurBitmap.getWidth();
                    blurMatrix.postScale(s, s);
                    blurBitmapShader.setLocalMatrix(blurMatrix);

                    blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), blurBitmapPaint);
                }
                if (setCellInvisible && cell != null) {
                    cell.setVisibility(View.INVISIBLE);
                    setCellInvisible = false;
                }
                if (setTaskInvisible && cell != null) {
                    cell.doNotDrawTaskId = taskId;
                    cell.invalidate();
                    setTaskInvisible = false;
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                setupTranslation();
            }
        };
        windowView.setOnClickListener(v -> {
            dismiss();
        });
        containerView = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == myCell || child == myTaskCell) {
                    canvas.save();
                    canvas.clipRect(0, lerp(clipTop, 0, openProgress), getWidth(), lerp(clipBottom, getHeight(), openProgress));
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        containerView.setClipToPadding(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                updateTranslation();
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                FrameLayout view = new FrameLayout(context);
                view.setOnClickListener(v -> dismiss(true));
                return view;
            }

            @Override
            public void bindView(View view, int position, int viewType) {}
        });
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        menuContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int w = MeasureSpec.getSize(widthMeasureSpec);
                final int h = MeasureSpec.getSize(heightMeasureSpec);
                updateTranslation();
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child == messageOptionsView && messageOptionsViewMaxWidth > 0) {
                        messageOptionsView.measure(
                            MeasureSpec.makeMeasureSpec(Math.min(w, (int) messageOptionsViewMaxWidth), MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
                        );
                    } else if (child == taskOptionsView && taskOptionsViewMaxWidth > 0) {
                        taskOptionsView.measure(
                            MeasureSpec.makeMeasureSpec(Math.min(w, (int) taskOptionsViewMaxWidth), MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
                        );
                    } else if (child == reactionsView) {
                        child.measure(
                            MeasureSpec.makeMeasureSpec(reactionsView.getTotalWidth(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
                        );
                    } else {
                        child.measure(
                            MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
                        );
                    }
                }
                setMeasuredDimension(w, h);
            }
        };
        containerView.addView(menuContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        tabsView = new MessagePreviewView.TabsView(context, resourcesProvider);
        tabsView.addTab(0, getString(R.string.TodoMenuTabTask));
        tabsView.addTab(1, getString(R.string.TodoMenuTabList));
        containerView.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.BOTTOM));
        tabsView.setOnTabClick(viewPager::scrollToPosition);

        hintTextView = new TextView(context);
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintTextView.setTextColor(tabsView.getColor());
        hintTextView.setText(LocaleController.getString(R.string.TodoMenuHint));
        hintTextView.setGravity(Gravity.CENTER);
        containerView.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 66));

        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        TodoItemMenu.this.insets.set(r.left, r.top, r.right, r.bottom);
                    } else {
                        TodoItemMenu.this.insets.set(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                    }
                    containerView.setPadding(TodoItemMenu.this.insets.left, TodoItemMenu.this.insets.top, TodoItemMenu.this.insets.right, TodoItemMenu.this.insets.bottom);
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.flags &=~ WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        AndroidUtilities.setLightNavigationBar(windowView, !Theme.isCurrentThemeDark());
    }

    private MessageObject messageObject;
    private boolean isOut;
    private ChatMessageCell myTaskCell;
    private ChatMessageCell myCell;
    private ChatMessageCell cell;

    private float clipTop = 0, clipBottom = 0;

    private AudioVisualizerDrawable audioVisualizerDrawable;
    private boolean setCellInvisible;
    private boolean setTaskInvisible;

    private int taskId;

    private ReactionsContainerLayout reactionsView;
    private View taskOptionsView;
    private float taskOptionsViewMaxWidth = -1;
    private View messageOptionsView;
    private float messageOptionsViewMaxWidth = -1;

    public void setCell(ChatActivity chatActivity, ChatMessageCell messageCell, int taskId) {
        cell = messageCell;
        this.taskId = taskId;
        messageObject = cell != null ? cell.getMessageObject() : null;
        isOut = messageObject != null && messageObject.isOutOwner();
        if (cell != null) {

            clipTop = chatActivity.getChatListViewPadding() - dp(4);
            clipBottom = messageCell.parentBoundsBottom;
            if (messageCell.getParent() instanceof View) {
                View parent = (View) messageCell.getParent();
                clipTop += parent.getY();
                clipBottom += parent.getY();
            }

            int width = cell.getWidth();
            int height = cell.getHeight();
            heightdiff = height - cell.getHeight();
            final int finalWidth = width;
            final int finalHeight = height;

            myTaskCell = new ChatMessageCell(getContext(), UserConfig.selectedAccount, false, null, cell.getResourcesProvider()) {
                @Override
                public void setPressed(boolean pressed) {}

                private final Path clipPath = new Path();
                private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    canvas.save();
                    final int index = getTodoIndex(taskId);
                    final float top = getPollButtonTop(index);
                    final float bottom = getPollButtonBottom(index);
                    AndroidUtilities.rectTmp.set(getPollButtonsLeft(), top, getPollButtonsRight(), bottom);
                    clipPath.rewind();
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), Path.Direction.CW);
                    shadowPaint.setColor(0);
                    shadowPaint.setShadowLayer(dp(2), 0, dp(.66f), Theme.multAlpha(0xFF000000, .20f * openProgress));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8), dp(8), shadowPaint);
                    canvas.clipPath(clipPath);
                    super.onDraw(canvas);
                    canvas.restore();
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    setMeasuredDimension(finalWidth, finalHeight);
                }

                @Override
                public void drawOverlays(Canvas canvas) {
                    firstVisiblePollButton = 0;
                    lastVisiblePollButton = pollButtons.size() - 1;
                    super.drawOverlays(canvas);
                }
            };
            cell.copyParamsTo(myTaskCell);
            myTaskCell.copySpoilerEffect2AttachIndexFrom(cell);
            myTaskCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }

                @Override
                public boolean didPressToDoButton(ChatMessageCell cell, TLRPC.TodoItem task, boolean enable) {
                    if (TodoItemMenu.this.cell.getDelegate() != null) {
                        return TodoItemMenu.this.cell.getDelegate().didPressToDoButton(TodoItemMenu.this.cell, task, enable);
                    } else {
                        return false;
                    }
                }
            });
            myTaskCell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.pinnedBottom, cell.pinnedTop, cell.firstInChat);
            containerView.addView(myTaskCell, new FrameLayout.LayoutParams(cell.getWidth(), height, Gravity.TOP | Gravity.LEFT));

            myCell = new ChatMessageCell(getContext(), UserConfig.selectedAccount, false, null, cell.getResourcesProvider()) {
                @Override
                public void setPressed(boolean pressed) {}

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    setMeasuredDimension(finalWidth, finalHeight);
                }

                @Override
                public void drawOverlays(Canvas canvas) {
                    firstVisiblePollButton = 0;
                    lastVisiblePollButton = pollButtons.size() - 1;
                    super.drawOverlays(canvas);
                }
            };
            cell.copyVisiblePartTo(myCell);
            cell.copyParamsTo(myCell);
            myCell.copySpoilerEffect2AttachIndexFrom(cell);
            myCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }
            });
            myCell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.pinnedBottom, cell.pinnedTop, cell.firstInChat);
            containerView.addView(myCell, new FrameLayout.LayoutParams(cell.getWidth(), height, Gravity.TOP | Gravity.LEFT));
        }

        viewPager.bringToFront();
        menuContainer.bringToFront();
        tabsView.bringToFront();
        viewPager.onTabAnimationUpdate(false);

        ItemOptions taskOptions = ItemOptions.makeOptions(containerView, resourcesProvider, null);

        TLRPC.TodoItem _task = null;
        TLRPC.TodoCompletion _completion = null;
        int _index = -1;
        TLRPC.TL_messageMediaToDo media = (TLRPC.TL_messageMediaToDo) MessageObject.getMedia(messageObject);
        for (int i = 0; i < media.todo.list.size(); ++i) {
            if (media.todo.list.get(i).id == taskId) {
                _task = media.todo.list.get(_index = i);
                break;
            }
        }
        for (int i = 0; i < media.completions.size(); ++i) {
            if (media.completions.get(i).id == taskId) {
                _completion = media.completions.get(i);
                break;
            }
        }
        final TLRPC.TodoItem task = _task;
        final TLRPC.TodoCompletion completion = _completion;
        final int index = _index;
        if (messageObject.canCompleteTodo()) {
            if (completion != null) {
                taskOptions.addText(LocaleController.formatTodoCompletedDate(completion.date), 14);
                taskOptions.addGap();
                taskOptions.add(R.drawable.msg_cancel, getString(R.string.TodoUncheck), () -> {
                    if (chatActivity.isInScheduleMode()) {
                        Toast.makeText(getContext(), getString(R.string.MessageScheduledTodo), Toast.LENGTH_LONG).show();
                    } else {
                        myTaskCell.toggleTodoCheck(myTaskCell.getTodoIndex(taskId), false);
                    }
                    dismiss(true);
                });
            } else {
                taskOptions.add(R.drawable.msg_select, getString(R.string.TodoCheck), () -> {
                    if (chatActivity.isInScheduleMode()) {
                        Toast.makeText(getContext(), getString(R.string.MessageScheduledTodo), Toast.LENGTH_LONG).show();
                    } else {
                        myTaskCell.toggleTodoCheck(myTaskCell.getTodoIndex(taskId), false);
                    }
                    dismiss(true);
                });
            }
        }
        if (task != null) {
            if (chatActivity != null) {
                taskOptions.add(R.drawable.menu_reply, getString(R.string.TodoItemQuote), () -> {
                    chatActivity.showFieldPanelForReplyQuote(messageObject, ChatActivity.ReplyQuote.from(messageObject, task.id));
                    dismiss(false);
                });
            }
            if (messageObject.getDialogId() < 0) {
                final MessagesController messagesController = MessagesController.getInstance(messageObject.currentAccount);
                final String username = DialogObject.getPublicUsername(messagesController.getUserOrChat(messageObject.getDialogId()));
                final String link = "https://" + messagesController.linkPrefix + "/" + (TextUtils.isEmpty(username) ? "c/" + (-messageObject.getDialogId()) : username) + "/" + messageObject.getId() + "?task=" + task.id;
                taskOptions.add(R.drawable.msg_link, getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    dismiss(true);
                });
            }
            taskOptions.add(R.drawable.msg_copy, getString(R.string.Copy), () -> {
                AndroidUtilities.addToClipboard(MessageObject.formatTextWithEntities(task.title, false));
                dismiss(true);
            });
        }
        if (messageObject.canEditMessage(chatActivity.currentChat)) {
            taskOptions.add(R.drawable.msg_edit, getString(R.string.TodoEditItem), () -> {
                PollCreateActivity pollCreateActivity = new PollCreateActivity(chatActivity, true, false);
                pollCreateActivity.setEditing(MessageObject.getMedia(messageObject), false, index);
                pollCreateActivity.setDelegate((poll, params, notify, scheduleDate) -> {
                    if (poll instanceof TLRPC.TL_messageMediaToDo && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                        ((TLRPC.TL_messageMediaToDo) poll).completions = ((TLRPC.TL_messageMediaToDo) messageObject.messageOwner.media).completions;
                    }
                    messageObject.messageOwner.media = poll;
                    chatActivity.getSendMessagesHelper().editMessage(messageObject, null, null, null, null, null, null, false, false, null);
                });
                chatActivity.presentFragment(pollCreateActivity);
                dismiss(false);
            });
            if (media.todo.list.size() > 1) {
                taskOptions.add(R.drawable.msg_delete, getString(R.string.TodoDeleteItem), () -> {
                    for (int i = 0; i < media.todo.list.size(); ++i) {
                        final TLRPC.TodoItem item = media.todo.list.get(i);
                        if (item.id == taskId) {
                            media.todo.list.remove(i);
                            i--;
                        }
                    }
                    for (int i = 0; i < media.completions.size(); ++i) {
                        final TLRPC.TodoCompletion c = media.completions.get(i);
                        if (c.id == taskId) {
                            media.completions.remove(i);
                            if (media.completions.isEmpty()) {
                                media.flags &=~ 1;
                            }
                            i--;
                        }
                    }
                    messageObject.messageOwner.media = media;
                    chatActivity.getSendMessagesHelper().editMessage(messageObject, null, null, null, null, null, null, false, false, null);
                    chatActivity.updateVisibleRows();

                    dismiss(false);
                });
            }
        }

        taskOptions.setupSelectors();
        taskOptionsView = taskOptions.getLayout();
        taskOptionsView.setPivotX(0);
        taskOptionsView.setPivotY(0);
        menuContainer.addView(taskOptionsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
    }

    public void setupMessageOptions(
        ChatActivity chatActivity,

        ArrayList<Integer> icons,
        ArrayList<CharSequence> items,
        ArrayList<Integer> options,

        Utilities.Callback<Integer> onOptionClick
    ) {
        final MessageObject message = messageObject;

        List<TLRPC.TL_availableReaction> availableReacts = chatActivity.getMediaDataController().getEnabledReactionsList();
        final boolean isReactionsViewAvailable = !chatActivity.isSecretChat() && !chatActivity.isInScheduleMode() && chatActivity.currentUser == null && message.hasReactions() && (!ChatObject.isChannel(chatActivity.currentChat) || chatActivity.currentChat.megagroup) && !ChatObject.isMonoForum(chatActivity.currentChat) && !availableReacts.isEmpty() && message.messageOwner.reactions.can_see_list && !message.isSecretMedia();
        final boolean isReactionsAvailable;
        if (message.isForwardedChannelPost()) {
            TLRPC.ChatFull chatInfo = chatActivity.getMessagesController().getChatFull(-message.getFromChatId());
            if (chatInfo == null) {
                isReactionsAvailable = true;
            } else {
                isReactionsAvailable = !chatActivity.isSecretChat() && chatActivity.getChatMode() != ChatActivity.MODE_QUICK_REPLIES && !chatActivity.isInScheduleMode() && message.isReactionsAvailable() && (chatInfo != null && (!(chatInfo.available_reactions instanceof TLRPC.TL_chatReactionsNone) || chatInfo.paid_reactions_available)) && !availableReacts.isEmpty();
            }
        } else {
            isReactionsAvailable = !message.isSecretMedia() && chatActivity.getChatMode() != ChatActivity.MODE_QUICK_REPLIES && !chatActivity.isSecretChat() && !chatActivity.isInScheduleMode() && message.isReactionsAvailable() && (chatActivity.chatInfo != null && (!(chatActivity.chatInfo.available_reactions instanceof TLRPC.TL_chatReactionsNone) || chatActivity.chatInfo.paid_reactions_available) || (chatActivity.chatInfo == null && !ChatObject.isChannel(chatActivity.currentChat)) || chatActivity.currentUser != null || ChatObject.isMonoForum(chatActivity.currentChat)) && !availableReacts.isEmpty();
        }
        final boolean showMessageSeen = !isReactionsViewAvailable && !chatActivity.isInScheduleMode() && chatActivity.currentChat != null && message.isOutOwner() && message.isSent() && !message.isEditing() && !message.isSending() && !message.isSendError() && !message.isContentUnread() && !message.isUnread() && (ConnectionsManager.getInstance(chatActivity.getCurrentAccount()).getCurrentTime() - message.messageOwner.date < chatActivity.getMessagesController().chatReadMarkExpirePeriod) && (ChatObject.isMegagroup(chatActivity.currentChat) || !ChatObject.isChannel(chatActivity.currentChat)) && chatActivity.chatInfo != null && chatActivity.chatInfo.participants_count <= chatActivity.getMessagesController().chatReadMarkSizeThreshold && !(message.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) && chatActivity.getChatMode() != ChatActivity.MODE_SAVED && message.canSetReaction() && !ChatObject.isMonoForum(chatActivity.currentChat);
        final boolean showMessageAuthor = chatActivity.currentChat != null && !message.isOut() && ChatObject.isMonoForum(chatActivity.currentChat) && ChatObject.canManageMonoForum(chatActivity.getCurrentAccount(), chatActivity.currentChat) && -chatActivity.currentChat.linked_monoforum_id == message.getFromChatId();
        final boolean showPrivateMessageSeen = !isReactionsViewAvailable && chatActivity.currentChat == null && chatActivity.currentEncryptedChat == null && (chatActivity.currentUser != null && !UserObject.isUserSelf(chatActivity.currentUser) && !UserObject.isReplyUser(chatActivity.currentUser) && !UserObject.isAnonymous(chatActivity.currentUser) && !chatActivity.currentUser.bot && !UserObject.isService(chatActivity.currentUser.id)) && (chatActivity.userInfo == null || !chatActivity.userInfo.read_dates_private) && !chatActivity.isInScheduleMode() && message.isOutOwner() && message.isSent() && !message.isEditing() && !message.isSending() && !message.isSendError() && !message.isContentUnread() && !message.isUnread() && (chatActivity.getConnectionsManager().getCurrentTime() - message.messageOwner.date < chatActivity.getMessagesController().pmReadDateExpirePeriod) && !(message.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest);
        final boolean showPrivateMessageEdit = (chatActivity.currentUser == null || !UserObject.isReplyUser(chatActivity.currentUser) && !UserObject.isAnonymous(chatActivity.currentUser)) && !chatActivity.isInScheduleMode() && message.isEdited() && !(message.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest);
        ItemOptions messageOptions = ItemOptions.makeOptions(containerView, chatActivity.getResourceProvider(), null, isReactionsViewAvailable || showMessageSeen);

        MessageSeenView messageSeenView;
        if (showMessageSeen) {
            messageSeenView = new MessageSeenView(getContext(), chatActivity.getCurrentAccount(), message, chatActivity.currentChat);
            FrameLayout messageSeenLayout = new FrameLayout(getContext());
            messageSeenLayout.addView(messageSeenView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            MessageSeenView finalMessageSeenView = messageSeenView;

            ItemOptions swipeback = messageOptions.makeSwipeback();

            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext(), true, false, resourcesProvider);
            cell.setItemHeight(44);
            cell.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
            cell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);

            FrameLayout backContainer = new FrameLayout(getContext());

            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            RecyclerListView listView2 = finalMessageSeenView.createListView();
            backContainer.addView(cell);
            linearLayout.addView(backContainer);
            linearLayout.addView(new ActionBarPopupWindow.GapView(getContext(), resourcesProvider), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
            backContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Bulletin.hideVisible();
                    messageOptions.closeSwipeback();
                }
            });

            messageSeenView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (finalMessageSeenView.users.isEmpty()) {
                        return;
                    }
                    if (finalMessageSeenView.users.size() == 1 && (finalMessageSeenView.dates.size() <= 0 || finalMessageSeenView.dates.get(0) <= 0)) {
                        TLObject object = finalMessageSeenView.users.get(0);
                        if (object == null) {
                            return;
                        }
                        Bundle args = new Bundle();
                        if (object instanceof TLRPC.User) {
                            args.putLong("user_id", ((TLRPC.User) object).id);
                        } else if (object instanceof TLRPC.Chat) {
                            args.putLong("chat_id", ((TLRPC.Chat) object).id);
                        }
                        ProfileActivity fragment = new ProfileActivity(args);
                        chatActivity.presentFragment(fragment);
                        dismiss(false);
                        return;
                    }

                    if (SharedConfig.messageSeenHintCount > 0 && chatActivity.contentView.getKeyboardHeight() < AndroidUtilities.dp(20)) {
                        chatActivity.messageSeenPrivacyBulletin = BulletinFactory.of(Bulletin.BulletinWindow.make(getContext()), resourcesProvider).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MessageSeenTooltipMessage)));
                        chatActivity.messageSeenPrivacyBulletin.setDuration(4000);
                        chatActivity.messageSeenPrivacyBulletin.show();
                        SharedConfig.updateMessageSeenHintCount(SharedConfig.messageSeenHintCount - 1);
                    }

                    listView2.requestLayout();
                    linearLayout.requestLayout();
                    listView2.getAdapter().notifyDataSetChanged();
                    messageOptions.openSwipeback(swipeback);
                }
            });
            linearLayout.addView(listView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            swipeback.addView(linearLayout);

            messageOptions.addView(messageSeenLayout);
            messageOptions.addGap();
        } else if (showPrivateMessageSeen) {
            MessagePrivateSeenView messagePrivateSeenView = new MessagePrivateSeenView(getContext(), MessagePrivateSeenView.TYPE_SEEN, message, () -> {
                dismiss(false);
            }, resourcesProvider);
            messageOptions.addView(messagePrivateSeenView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
            messageOptions.addGap();
        } else if (showPrivateMessageEdit) {
            MessagePrivateSeenView messagePrivateSeenView = new MessagePrivateSeenView(getContext(), MessagePrivateSeenView.TYPE_EDIT, message, () -> {
                dismiss(false);
            }, resourcesProvider);
            messageOptions.addView(messagePrivateSeenView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
            messageOptions.addGap();
        }

        for (int i = 0, N = icons.size(); i < N; ++i) {
            final int option = options.get(i);
            messageOptions.add(icons.get(i), items.get(i), () -> {
                onOptionClick.run(option);
                dismiss(option == ChatActivity.OPTION_DELETE || option == ChatActivity.OPTION_PIN);
            });
        }

        messageOptions.setupSelectors();
        messageOptionsView = messageOptions.getLayout();
        messageOptionsView.setPivotX(0);
        messageOptionsView.setPivotY(0);
        menuContainer.addView(messageOptionsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        if (messageOptionsView instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
            ((ActionBarPopupWindow.ActionBarPopupWindowLayout) messageOptionsView).setOnSizeChangedListener(this::updateTranslation);
            messageOptionsView.setOnTouchListener((v, e) -> {
                if (messageOptionsView != null && e.getAction() == MotionEvent.ACTION_DOWN) {
                    Drawable backgroundDrawable = ((ActionBarPopupWindow.ActionBarPopupWindowLayout) messageOptionsView).getBackgroundDrawable();
                    AndroidUtilities.rectTmp.set(backgroundDrawable.getBounds());
                    AndroidUtilities.rectTmp.offset(messageOptionsView.getX(), messageOptionsView.getY());
                    if (!AndroidUtilities.rectTmp.contains(e.getX(), e.getY())) {
                        dismiss(true);
                        return true;
                    }
                }
                return false;
            });
        }

        if (isReactionsAvailable) {
            final boolean tags = chatActivity.getUserConfig().getClientUserId() == chatActivity.getDialogId();
            ReactionsContainerLayout reactionsLayout = new ReactionsContainerLayout(tags ? ReactionsContainerLayout.TYPE_TAGS : ReactionsContainerLayout.TYPE_DEFAULT, chatActivity, getContext(), chatActivity.getCurrentAccount(), resourcesProvider);
            reactionsLayout.forceAttachToParent = true;
            int pad = 22;
            int sPad = 24;
            reactionsLayout.setPadding(dp(4) + (LocaleController.isRTL ? 0 : sPad), dp(4), dp(4) + (LocaleController.isRTL ? sPad : 0), dp(pad));

            ReactionsContainerLayout finalReactionsLayout = reactionsLayout;
            reactionsLayout.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
                @Override
                public void onReactionClicked(View v, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                    float x = 0, y = 0;
                    BaseCell cell = chatActivity.findMessageCell(message.getId(), true);
                    if (cell instanceof ChatMessageCell) {
                        final ChatMessageCell messageCell = (ChatMessageCell) cell;
                        final ReactionsLayoutInBubble.ReactionButton btn = messageCell.reactionsLayoutInBubble.getReactionButton(visibleReaction);
                        if (btn != null) {
                            x = messageCell.reactionsLayoutInBubble.x + btn.x + btn.width / 2f;
                            y = messageCell.reactionsLayoutInBubble.y + btn.y + btn.height / 2f;
                        }
                    } else if (cell instanceof ChatActionCell) {
                        final ChatActionCell actionCell = (ChatActionCell) cell;
                        final ReactionsLayoutInBubble.ReactionButton btn = actionCell.reactionsLayoutInBubble.getReactionButton(visibleReaction);
                        if (btn != null) {
                            x = actionCell.reactionsLayoutInBubble.x + btn.x + btn.width / 2f;
                            y = actionCell.reactionsLayoutInBubble.y + btn.y + btn.height / 2f;
                        }
                    }
                    if (visibleReaction != null && visibleReaction.isStar) {
                        longpress = true;
                    }
                    chatActivity.selectReaction(cell, message, finalReactionsLayout, v, x, y, visibleReaction,false, longpress, addToRecent, false);
                    dismiss(false);
                }

                @Override
                public void hideMenu() {
                    dismiss(false);
                }
            });
            menuContainer.addView(reactionsView = reactionsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, (int) (52 + reactionsLayout.getTopOffset() / AndroidUtilities.density + pad), Gravity.TOP | Gravity.LEFT));

            reactionsLayout.setMessage(message, chatActivity.chatInfo, true);
            reactionsView.setTransitionProgress(1);
        }

        updateTranslation();
    }

    private float tx, ty;
    private boolean hasTranslation;
    private float dtx1, dty1;
    private float dtx2, dty2;
    private boolean hasDestTranslation;
    private float heightdiff;
    private void setupTranslation() {
        if (hasTranslation || windowView.getWidth() <= 0) return;
        if (cell != null) {
            int[] loc = new int[2];
            cell.getLocationOnScreen(loc);
            tx = loc[0] - insets.left;
            ty = loc[1] - insets.top;
            if (!hasDestTranslation) {
                hasDestTranslation = true;

                dtx1 = 0;
                dty1 = ty;
                if (messageOptionsView != null && dty1 + cell.getHeight() + messageOptionsView.getHeight() > windowView.getHeight() - insets.top - insets.bottom - dp(66)) {
                    dty1 = windowView.getHeight() - insets.top - insets.bottom - dp(66) - cell.getHeight() - messageOptionsView.getHeight();
                }

                final int index = myTaskCell.getTodoIndex(taskId);
                final float top = myTaskCell.getPollButtonTop(index);
                final float bottom = myTaskCell.getPollButtonBottom(index);

                dtx2 = 0;
                dty2 = ty;
                if (dty2 + (int) bottom > windowView.getHeight() - insets.top - insets.bottom - dp(66 + 12) - hintTextView.getHeight()) {
                    dty2 = windowView.getHeight() - insets.top - insets.bottom - dp(66 + 12) - hintTextView.getHeight() - (int) bottom;
                }
                if (taskOptionsView != null && dty2 + (int) bottom + taskOptionsView.getHeight() > windowView.getHeight() - insets.top - insets.bottom - dp(66 + 12) - hintTextView.getHeight()) {
                    dty2 = windowView.getHeight() - insets.top - insets.bottom - dp(66 + 12) - hintTextView.getHeight() - (int) bottom - taskOptionsView.getHeight();
                }
            }
            updateTranslation();
        } else {
            tx = ty = 0;
        }
        hasTranslation = true;
    }
    private void updateTranslation() {
        final float t = viewPager.getPositionAnimated();

        final float page0x = lerp(0, -viewPager.getWidth(), t);
        final float page1x = lerp(viewPager.getWidth(), 0, t);

        if (hasTranslation && messageOptionsView instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
            ActionBarPopupWindow.ActionBarPopupWindowLayout l = (ActionBarPopupWindow.ActionBarPopupWindowLayout) messageOptionsView;

            dtx1 = 0;
            dty1 = ty;
            if (messageOptionsView != null && dty1 + cell.getHeight() + l.getVisibleHeight() > windowView.getHeight() - insets.top - insets.bottom - dp(66)) {
                dty1 = windowView.getHeight() - insets.top - insets.bottom - dp(66) - cell.getHeight() - l.getVisibleHeight();
            }
        }

        myCell.setTranslationX(lerp(tx, dtx1, dismissingWithAlpha ? 1.0f : openProgress) + page1x);
        myCell.setTranslationY(lerp(ty, dty1, dismissingWithAlpha ? 1.0f : openProgress));
        if (messageOptionsView != null) {
            if (isOut) {
                messageOptionsView.setTranslationX(page1x + dtx1 + myCell.getLeft() + myCell.getPollButtonsLeft() - dp(8) - messageOptionsView.getLeft());
            } else {
                messageOptionsView.setTranslationX(page1x + dtx1 + (myCell.needDrawAvatar() ? dp(48) : 0) + myCell.getLeft() - messageOptionsView.getLeft());
            }
            messageOptionsViewMaxWidth = menuContainer.getMeasuredWidth() - (messageOptionsView.getX() - page1x);

            messageOptionsView.setTranslationY(myCell.getY() + myCell.getHeight() - messageOptionsView.getTop() - menuContainer.getTop());
            messageOptionsView.setAlpha(openProgress);
            final float s = lerp(0.75f, 1.0f, openProgress);
            messageOptionsView.setScaleX(s);
            messageOptionsView.setScaleY(s);
        }

        myTaskCell.setTranslationX(lerp(tx, dtx2, dismissingWithAlpha ? 1.0f : openProgress) + page0x);
        myTaskCell.setTranslationY(lerp(ty, dty2, dismissingWithAlpha ? 1.0f : openProgress));
        if (taskOptionsView != null) {
            final int index = myTaskCell.getTodoIndex(taskId);
            final float top = myTaskCell.getPollButtonTop(index);
            final float bottom = myTaskCell.getPollButtonBottom(index);
            if (isOut) {
                taskOptionsView.setTranslationX(page0x + dtx2 + myTaskCell.getLeft() + myTaskCell.getPollButtonsLeft() - dp(8) - taskOptionsView.getLeft());
            } else {
                taskOptionsView.setTranslationX(page0x + dtx2 + (myTaskCell.needDrawAvatar() ? dp(48) : 0) + myTaskCell.getLeft() - taskOptionsView.getLeft());
            }
            taskOptionsViewMaxWidth = menuContainer.getMeasuredWidth() - (taskOptionsView.getX() - page1x);
            taskOptionsView.setTranslationY(myTaskCell.getY() + (int) bottom - taskOptionsView.getTop() - menuContainer.getTop());
            taskOptionsView.setAlpha(openProgress);
            final float s = lerp(0.75f, 1.0f, openProgress);
            taskOptionsView.setScaleX(s);
            taskOptionsView.setScaleY(s);
        }
        if (dismissingWithAlpha) {
            myCell.setAlpha(openProgress);
            myTaskCell.setAlpha(openProgress);
        }

        if (reactionsView != null) {
            final float rtx = Math.max(0, (myCell.getBoundsRight() + myCell.getBoundsLeft()) / 2f - reactionsView.getWidth() * .8f);
            reactionsView.setTranslationX(page1x + rtx);
            reactionsView.setTranslationY(Math.max(0, myCell.getY() - reactionsView.getHeight() + dp(22) - menuContainer.getTop()));
            reactionsView.setAlpha(openProgress);

            View reactionsWindow = reactionsView.getWindowView();
            if (reactionsWindow != null) {
                reactionsWindow.setTranslationX(page1x + rtx);
                reactionsWindow.setAlpha(openProgress);
            }
        }

        hintTextView.setTranslationX(page0x);
        hintTextView.setAlpha(openProgress);

        tabsView.setSelectedTab(t);
        tabsView.setAlpha(openProgress);
    }

    private void prepareBlur(View withoutView) {
        if (withoutView != null) {
            withoutView.setVisibility(View.INVISIBLE);
        }
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            if (withoutView != null) {
                withoutView.setVisibility(View.VISIBLE);
            }
            blurBitmap = bitmap;

            blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .05f : +.25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? -.02f : -.04f);
            blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            blurMatrix = new Matrix();
        }, 14);
    }

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        super.show();

        prepareBlur(null);
//        setCellInvisible = true;
        setTaskInvisible = true;
        animateOpenTo(open = true, null);
    }

    public boolean isShown() {
        return !dismissing;
    }

    private boolean dismissing = false;

    @Override
    public void dismiss() {
        dismiss(true);
    }

    private boolean dismissingWithAlpha;
    public void dismiss(boolean backIntoMessage) {
        if (backIntoMessage && reactionsView != null && reactionsView.getReactionsWindow() != null && reactionsView.getReactionsWindow().isShowing()) {
            reactionsView.dismissWindow();
            return;
        }
        if (dismissing) return;
        dismissing = true;
        hasTranslation = false;
        viewPager.cancelTouches();
        final boolean fullMessage = viewPager.getCurrentPosition() == 1;
        if (backIntoMessage && fullMessage) {
            if (cell != null) {
                cell.setVisibility(View.INVISIBLE);
                cell.invalidate();
            }
        } else if (!backIntoMessage) {
            if (cell != null) {
                cell.setVisibility(View.VISIBLE);
                cell.doNotDrawTaskId = -1;
                cell.invalidate();
            }
        }
        dismissingWithAlpha = !backIntoMessage;
        setupTranslation();
        animateOpenTo(open = false, () -> {
            AndroidUtilities.runOnUIThread(super::dismiss);
            if (cell != null) {
                cell.setVisibility(View.VISIBLE);
                if (!fullMessage) {
                    cell.syncTodoCheck(cell.getTodoIndex(taskId), myTaskCell);
                }
                cell.doNotDrawTaskId = -1;
                cell.invalidate();
            }
            if (dismissListener != null) {
                AndroidUtilities.runOnUIThread(dismissListener);
                dismissListener = null;
            }
        });
        windowView.invalidate();
    }

    private Runnable dismissListener;
    public void setOnDismissListener(Runnable onDismiss) {
        dismissListener = onDismiss;
    }

    private ValueAnimator openAnimator;
    private ValueAnimator open2Animator;
    private void animateOpenTo(boolean open, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        if (open2Animator != null) {
            open2Animator.cancel();
        }
        setupTranslation();
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            windowView.invalidate();
            containerView.invalidate();
            updateTranslation();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1 : 0;
                windowView.invalidate();
                containerView.invalidate();
                updateTranslation();
                if (after != null) {
                    after.run();
                }
            }
        });
        final long duration = !open ? 330 : 520;
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(duration);
        openAnimator.start();

        open2Animator = ValueAnimator.ofFloat(openProgress2, open ? 1 : 0);
        open2Animator.addUpdateListener(anm -> {
            openProgress2 = (float) anm.getAnimatedValue();
        });
        open2Animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress2 = open ? 1 : 0;
            }
        });
        open2Animator.setDuration((long) (1.5f * duration));
        open2Animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        open2Animator.start();
    }
}
