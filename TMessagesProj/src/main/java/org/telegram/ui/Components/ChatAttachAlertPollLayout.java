package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.hideKeyboard;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollCreateCheckCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.poll.PollAttachedMedia;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaFile;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaGallery;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaLocation;
import org.telegram.ui.Components.poll.PollAttachedMediaPack;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaMusic;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaSticker;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class ChatAttachAlertPollLayout extends ChatAttachAlert.AttachAlertLayout implements SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, NotificationCenter.NotificationCenterDelegate {

    private final boolean todo;

    private final ListAdapter listAdapter;
    private final RecyclerListView listView;
    private final DefaultItemAnimator itemAnimator;
    private final FillLastLinearLayoutManager layoutManager;
    private SuggestEmojiView suggestEmojiPanel;
    private HintView hintView;
    public EmojiView emojiView;
    private final KeyboardNotifier keyboardNotifier;
    private boolean waitingForKeyboardOpen;
    private boolean destroyed;
    private final boolean isPremium;

    private final int maxAnswersCount;
    private final CharSequence[] answers;
    private final boolean[] answersChecks;
    private int answersCount = 1;
    private CharSequence questionString;
    private CharSequence descriptionString;
    private CharSequence solutionString;

    private boolean allowRevoting = true;
    private boolean shuffleOptions = true;
    private boolean allowAddingOptions = true;

    private int pollLimitDuration; // relative time
    private int pollLimitDeadline; // absolute time

    private boolean hideResults;
    private boolean anonymousPoll;
    private boolean multipleChoise = true;
    private boolean quizPoll;
    private boolean hintShowed;
    private int quizOnly;
    private boolean allowAdding = true;
    private boolean allowMarking = true;

    private boolean allowNesterScroll;

    private boolean ignoreLayout;

    private PollCreateActivityDelegate delegate;

    private int requestFieldFocusAtPosition = -1;

    private int paddingRow;
    private int questionHeaderRow;
    private int questionRow;
    private int descriptionRow;
    private int solutionRowHeader;
    private int solutionRow;
    private int solutionInfoRow;
    private int questionSectionRow;
    private int answerHeaderRow;
    private int answerStartRow;
    private int addAnswerRow;
    private int answerSectionRow;
    private int settingsHeaderRow;
    private int settingsSectionRow;
    private int allowAddingRow;
    private int allowMarkingRow;
    private int emptyRow;

    private int poll2vAnonymousRow;
    private int poll2vAllowAddingRow;
    private int poll2vAllowRevotingRow;
    private int poll2vShuffleRow;
    private int poll2vMultipleRow;
    private int poll2vQuizRow;
    private int poll2vLimitDurationRow;
    private int poll2vLimitDurationTimeRow;
    private int poll2vLimitDurationHideResultsRow;
    private int poll2vLimitDurationHideResultsRowInfo;

    private int rowCount;

    private int topPadding;

    public static final int MAX_QUESTION_LENGTH = 255;
    public static final int MAX_ANSWER_LENGTH = 100;
    public static final int MAX_SOLUTION_LENGTH = 200;
    private final int MAX_CAPTION_LENGTH;

    private static final int done_button = 40;

    private final int[] POLL_DURATION_OPTIONS = new int[] {
        3600, 3 * 3600, 8 * 3600, 24 * 3600, 72 * 3600 };

    public interface PollCreateActivityDelegate {
        void sendPoll(TLRPC.MessageMedia poll, CharSequence caption, PollAttachedMediaPack media, ArrayList<Integer> correctAnswers, boolean notify, int scheduleDate, long payStars);
    }

    private static class EmptyView extends View {

        public EmptyView(Context context) {
            super(context);
        }
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 5) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.setItemAnimator(itemAnimator);
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
                viewHolder.itemView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
            viewHolder.itemView.setBackground(null);
        }
    }

    private final Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentCell != null) {
                EditTextBoldCursor editText = currentCell.getEditField();
                if (!destroyed && editText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow && AndroidUtilities.isTablet()) {
                    editText.requestFocus();
                    AndroidUtilities.showKeyboard(editText);
                    AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                    AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
                }
            }
        }
    };

    public ChatAttachAlertPollLayout(ChatAttachAlert alert, Context context, boolean todo, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);

        this.todo = todo;
        maxAnswersCount = getAnswersMaxCount();
        answers = new CharSequence[maxAnswersCount];
        answersChecks = new boolean[maxAnswersCount];

        updateRows();
        isPremium = AccountInstance.getInstance(parentAlert.currentAccount).getUserConfig().isPremium();
        /*if (quiz != null) {
            quizPoll = quiz;
            quizOnly = quizPoll ? 1 : 2;
        }*/

        checkboxPaint.setColor(getThemedColor(Theme.key_telegram_color));
        parentAlert.sizeNotifierFrameLayout.setDelegate(this);
        listAdapter = new ListAdapter(context);

        listView = new RecyclerListView(context) {
            @Override
            protected void requestChildOnScreen(@NonNull View child, View focused) {
                if (!(child instanceof PollEditTextCell)) {
                    return;
                }
                super.requestChildOnScreen(child, focused);
            }

            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        iBlur3Capture = listView;
        iBlur3CaptureView = listView;
        occupyNavigationBar = true;
        listView.setItemAnimator(itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                if (holder.getAdapterPosition() == 0) {
                    parentAlert.updateLayout(ChatAttachAlertPollLayout.this, true, 0);
                }
            }
        });
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setSections(true);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(53 + 12), listView) {

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, smoothScrollToOption ? SNAP_TO_START : snapPreference);
                        if (smoothScrollToOption) {
                            dy += dp(160);
                        }
                        if (!smoothScrollToOption) {
                            dy -= (topPadding - AndroidUtilities.dp(7));
                        }

                        if (smoothScrollToOption && dy == 0) {
                            if (showMediaHintIndexAfterSmoothScroll >= 0) {
                                showMediaHint(showMediaHintIndexAfterSmoothScroll);
                                showMediaHintIndexAfterSmoothScroll = -1;
                            }
                        }

                        smoothScrollToOption = false;
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }

            @Override
            protected int[] getChildRectangleOnScreenScrollAmount(View child, Rect rect) {
                int[] out = new int[2];
                final int parentTop = 0;
                final int parentBottom = getHeight() - getPaddingBottom();
                final int childTop = child.getTop() + rect.top - child.getScrollY();
                final int childBottom = childTop + rect.height();

                final int offScreenTop = Math.min(0, childTop - parentTop);
                final int offScreenBottom = Math.max(0, childBottom - parentBottom);

                final int dy = offScreenTop != 0 ? offScreenTop : Math.min(childTop - parentTop, offScreenBottom);
                out[0] = 0;
                out[1] = dy;
                return out;
            }
        });
        layoutManager.setSkipFirstItem();
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setPreserveFocusAfterLayout(true);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == poll2vLimitDurationTimeRow) {
                final ItemOptions io = ItemOptions.makeOptions(alert.container, resourcesProvider, view);
                for (int a = 0; a < POLL_DURATION_OPTIONS.length; a++) {
                    final int duration = POLL_DURATION_OPTIONS[a];
                    final Drawable d = TimerDrawable.getTtlIcon(duration);
                    d.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
                    io.add(d, formatPluralString("Hours", duration / 3600), () -> {
                        pollLimitDeadline = 0;
                        pollLimitDuration = duration;
                        if (view instanceof TextCell) {
                            checkDurationInfoRow((TextCell) view, true);
                        } else {
                            listAdapter.notifyItemChanged(poll2vLimitDurationTimeRow);
                        }
                    });
                }
                io.add(R.drawable.msg_customize, getString(R.string.PollV2PollDurationOptionCustom), () -> AlertsCreator.createPollCloseDatePickerDialog(context, pollLimitDeadline, (notify, scheduleDate, scheduleRepeatPeriod) -> {
                    if (notify) {
                        pollLimitDeadline = scheduleDate;
                        pollLimitDuration = 0;
                        if (view instanceof TextCell) {
                            checkDurationInfoRow((TextCell) view, true);
                        } else {
                            listAdapter.notifyItemChanged(poll2vLimitDurationTimeRow);
                        }
                    }
                }, () -> {}, new AlertsCreator.ScheduleDatePickerColors(resourcesProvider), resourcesProvider));
                io.setDrawScrim(false);
                io.setDimAlpha(0);
                io.show();
            } else if (position == addAnswerRow) {
                addNewField();
            } else if (view instanceof TextCheckCell || view instanceof PollCreateCheckCell) {
                boolean checked = false;
                boolean wasChecksBefore = quizPoll;
                if (suggestEmojiPanel != null) {
                    suggestEmojiPanel.forceClose();
                }
                if (position == poll2vAnonymousRow) {
                    anonymousPoll = !anonymousPoll;
                    checked = !anonymousPoll;
                    checkAllowAddingOptionsRow();
                } else if (position == allowAddingRow) {
                    checked = allowAdding = !allowAdding;
                } else if (position == poll2vAllowAddingRow) {
                    if (!quizPoll && !anonymousPoll) {
                        allowAddingOptions = !allowAddingOptions;
                    }
                    checked = allowAddingOptions;
                } else if (position == poll2vShuffleRow) {
                    checked = shuffleOptions = !shuffleOptions;
                } else if (position == poll2vLimitDurationRow) {
                    if (pollLimitDuration == 0 && pollLimitDeadline == 0) {
                        pollLimitDuration = 86400;
                        pollLimitDeadline = 0;
                        int prevPoll2vLimitDurationRow = poll2vLimitDurationTimeRow;
                        updateRows();
                        if (prevPoll2vLimitDurationRow < 0) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(poll2vLimitDurationRow);
                            if (holder != null && holder.itemView instanceof PollCreateCheckCell) {
                                ((PollCreateCheckCell) holder.itemView).setDivider(true);
                            }
                            listView.setItemAnimator(itemAnimator);
                            listAdapter.notifyItemRangeInserted(poll2vLimitDurationTimeRow, 3);
                        }
                    } else {
                        pollLimitDuration = 0;
                        pollLimitDeadline = 0;
                        int prevPoll2vLimitDurationRow = poll2vLimitDurationTimeRow;
                        updateRows();
                        listView.setItemAnimator(itemAnimator);
                        listAdapter.notifyItemRangeRemoved(prevPoll2vLimitDurationRow, 3);

                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(poll2vLimitDurationRow);
                        if (holder != null && holder.itemView instanceof PollCreateCheckCell) {
                            ((PollCreateCheckCell) holder.itemView).setDivider(false);
                        }
                    }
                    checked = pollLimitDuration != 0 || pollLimitDeadline != 0;
                } else if (position == poll2vAllowRevotingRow) {
                    //if (!quizPoll) {
                        allowRevoting = !allowRevoting;
                    //}
                    checked = allowRevoting;
                } else if (position == allowMarkingRow) {
                    checked = allowMarking = !allowMarking;
                    int prevAddingRow = allowAddingRow;
                    updateRows();
                    if (allowAddingRow >= 0 && prevAddingRow < 0) {
                        listView.setItemAnimator(itemAnimator);
                        listAdapter.notifyItemInserted(allowAddingRow);
                    } else if (prevAddingRow >= 0 && allowAddingRow < 0) {
                        listView.setItemAnimator(itemAnimator);
                        listAdapter.notifyItemRemoved(prevAddingRow);
                    }
                } else if (position == poll2vMultipleRow) {
                    checked = multipleChoise = !multipleChoise;
                    if (!multipleChoise && quizPoll) {
                        boolean was = false;
                        for (int a = 0; a < answersChecks.length; a++) {
                            if (was) {
                                answersChecks[a] = false;
                            } else if (answersChecks[a]) {
                                was = true;
                            }
                        }
                    }
                    for (int childCount = listView.getChildCount(), i = 0; i < childCount; ++i) {
                        final RecyclerView.ViewHolder holder = listView.getChildViewHolder(listView.getChildAt(i));
                        if (holder.getItemViewType() == ListAdapter.VIEW_TYPE_ANSWER) {
                            ((PollEditTextCell) holder.itemView).setCheckboxMultiselect(multipleChoise, true);
                        }
                    }
                } else if (position == poll2vLimitDurationHideResultsRow) {
                    checked = hideResults = !hideResults;
                } else if (position == poll2vQuizRow) {
                    if (quizOnly != 0) {
                        return;
                    }
                    listView.setItemAnimator(itemAnimator);
                    checked = quizPoll = !quizPoll;
                    int prevSolutionRow = solutionRowHeader;
                    updateRows();
                    if (quizPoll) {
                        listAdapter.notifyItemRangeInserted(solutionRowHeader, 3);
                    } else {
                        listAdapter.notifyItemRangeRemoved(prevSolutionRow, 3);
                    }
                    listAdapter.notifyItemChanged(emptyRow);

                    if (quizPoll) {
                        allowRevoting = false;
                        if (poll2vAllowRevotingRow >= 0) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(poll2vAllowRevotingRow);
                            if (holder != null) {
                                ((PollCreateCheckCell) holder.itemView).setChecked(false);
                                // ((PollCreateCheckCell) holder.itemView).getCheckBox().setIconVisible(true, true);
                            } else {
                                listAdapter.notifyItemChanged(poll2vAllowRevotingRow);
                            }
                        }
                    } else {
                        if (poll2vAllowRevotingRow >= 0) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(poll2vAllowRevotingRow);
                            if (holder != null) {
                                // ((PollCreateCheckCell) holder.itemView).getCheckBox().setIconVisible(false, true);
                            } else {
                                listAdapter.notifyItemChanged(poll2vAllowRevotingRow);
                            }
                        }
                    }
                    checkAllowAddingOptionsRow();
                    if (quizPoll && !multipleChoise) {
                        boolean was = false;
                        for (int a = 0; a < answersChecks.length; a++) {
                            if (was) {
                                answersChecks[a] = false;
                            } else if (answersChecks[a]) {
                                was = true;
                            }
                        }
                    }
                }
                if (hintShowed && !quizPoll) {
                    hintView.hide();
                }
                int count = listView.getChildCount();
                for (int a = answerStartRow; a < answerStartRow + answersCount; a++) {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(a);
                    if (holder != null && holder.itemView instanceof PollEditTextCell) {
                        PollEditTextCell pollEditTextCell = (PollEditTextCell) holder.itemView;
                        pollEditTextCell.setShowCheckBox(quizPoll, true);
                        pollEditTextCell.setChecked(answersChecks[a - answerStartRow], wasChecksBefore);
                        if (pollEditTextCell.getTop() > AndroidUtilities.dp(40) && position == poll2vQuizRow && !hintShowed) {
                            hintView.setText(getString(R.string.PollTapToSelect));
                            hintView.showForView(pollEditTextCell.getCheckBox(), true);
                            hintShowed = true;
                        }
                    }
                }

                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(checked);
                } else if (view instanceof PollCreateCheckCell) {
                    ((PollCreateCheckCell) view).setChecked(checked);
                }
                checkDoneButton();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertPollLayout.this, true, dy);
                if (suggestEmojiPanel != null && suggestEmojiPanel.isShown()) {
                    SuggestEmojiView.AnchorViewDelegate emojiDelegate = suggestEmojiPanel.getDelegate();
                    if (emojiDelegate instanceof PollEditTextCell) {
                        PollEditTextCell cell = (PollEditTextCell) emojiDelegate;
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
                        if (holder != null) {
                            int position = holder.getAdapterPosition();
                            if (suggestEmojiPanel.getDirection() == SuggestEmojiView.DIRECTION_TO_BOTTOM) {
                                suggestEmojiPanel.setTranslationY(holder.itemView.getY() - AndroidUtilities.dp(166) + holder.itemView.getMeasuredHeight());
                            } else {
                                suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                            }
                            if (position < layoutManager.findFirstVisibleItemPosition() || position > layoutManager.findLastVisibleItemPosition()) {
                                suggestEmojiPanel.forceClose();
                            }
                        } else {
                            suggestEmojiPanel.forceClose();
                        }
                    } else {
                        suggestEmojiPanel.forceClose();
                    }
                }
                if (dy != 0 && hintView != null) {
                    hintView.hide();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(1);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(53 + 12)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(53 + 12));
                        }
                    }

                    if (showMediaHintIndexAfterSmoothScroll >= 0) {
                        showMediaHint(showMediaHintIndexAfterSmoothScroll);
                        showMediaHintIndexAfterSmoothScroll = -1;
                    }
                }
            }
        });

        hintView = new HintView(context, 4);
        hintView.setAlpha(0.0f);
        hintView.setVisibility(View.INVISIBLE);
        addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));

        MAX_CAPTION_LENGTH = MessagesController.getInstance(parentAlert.currentAccount).config.pollCaptionLengthMax.get();

        if (isPremium) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            suggestEmojiPanel = new SuggestEmojiView(context, parentAlert.currentAccount, null, resourcesProvider) {
                @Override
                protected int emojiCacheType() {
                    return AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
                }
            };
            suggestEmojiPanel.forbidCopy();
            suggestEmojiPanel.forbidSetAsStatus();
            suggestEmojiPanel.setHorizontalPadding(AndroidUtilities.dp(24));
            addView(suggestEmojiPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 160, Gravity.LEFT | Gravity.TOP));
        }
        keyboardNotifier = new KeyboardNotifier(parentAlert.sizeNotifierFrameLayout, null);
        checkDoneButton();
    }

    private void checkAllowAddingOptionsRow() {
        final boolean isAllowed = !quizPoll && !anonymousPoll;
        if (!isAllowed) {
            allowAddingOptions = false;
        }

        if (poll2vAllowAddingRow < 0) {
            return;
        }
        final RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(poll2vAllowAddingRow);
        if (holder == null) {
            listAdapter.notifyItemChanged(poll2vAllowAddingRow);
            return;
        }
        final PollCreateCheckCell cell = (PollCreateCheckCell) holder.itemView;
        if (!isAllowed) {
            cell.setChecked(false);
        }
        cell.getCheckBox().setIconVisible(!isAllowed, true);
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (isPremium) {
            hideEmojiPopup(false);
            if (suggestEmojiPanel != null) {
                suggestEmojiPanel.forceClose();
            }
            if (currentCell != null) {
                currentCell.setEmojiButtonVisibility(false);
                currentCell.getTextView().clearFocus();
                hideKeyboard(currentCell.getEditField());
            }
        }
    }

    @Override
    public void onHideShowProgress(float progress) {
        parentAlert.updateDoneItemEnabled();
    }

    @Override
    public void onMenuItemClick(int id) {
        if (id == done_button) {
            if (todo) {
                onTodoDoneButtonClick();
            } else {
                onPollDoneButtonClick();
            }
        }
    }

    private void onTodoDoneButtonClick() {
        CharSequence questionText = getFixedString(questionString);
        CharSequence[] questionCharSequence = new CharSequence[]{questionText};
        ArrayList<TLRPC.MessageEntity> questionEntities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(questionCharSequence, true);
        questionText = questionCharSequence[0];
        if (questionEntities != null) {
            for (int a = 0, N = questionEntities.size(); a < N; a++) {
                TLRPC.MessageEntity entity = questionEntities.get(a);
                if (entity.offset + entity.length > questionText.length()) {
                    entity.length = questionText.length() - entity.offset;
                }
            }
        }

        TLRPC.TL_messageMediaToDo todo = new TLRPC.TL_messageMediaToDo();
        todo.todo = new TLRPC.TodoList();
        todo.todo.others_can_append = allowMarking && allowAdding;
        todo.todo.others_can_complete = allowMarking;
        todo.todo.title = new TLRPC.TL_textWithEntities();
        todo.todo.title.text = questionText.toString();
        todo.todo.title.entities = questionEntities;

        for (int a = 0; a < answers.length; a++) {
            if (TextUtils.isEmpty(getFixedString(answers[a]))) {
                continue;
            }
            CharSequence answerText = getFixedString(answers[a]);
            CharSequence[] answerCharSequence = new CharSequence[]{answerText};
            ArrayList<TLRPC.MessageEntity> answerEntities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(answerCharSequence, true);
            answerText = answerCharSequence[0];
            if (answerEntities != null) {
                for (int b = 0, N = answerEntities.size(); b < N; b++) {
                    TLRPC.MessageEntity entity = answerEntities.get(b);
                    if (entity.offset + entity.length > answerText.length()) {
                        entity.length = answerText.length() - entity.offset;
                    }
                }
            }

            TLRPC.TodoItem task = new TLRPC.TodoItem();
            task.title = new TLRPC.TL_textWithEntities();
            task.title.text = answerText.toString();
            task.title.entities = answerEntities;
            task.id = 1 + todo.todo.list.size();
            todo.todo.list.add(task);
        }
        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
        AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), 1 + parentAlert.getAdditionalMessagesCount(), payStars -> {
            if (chatActivity.isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(chatActivity.getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                    delegate.sendPoll(todo, null, null, null, notify, scheduleDate, payStars);
                    parentAlert.dismiss(true);
                });
            } else {
                delegate.sendPoll(todo, null, null, null, true, 0, payStars);
                parentAlert.dismiss(true);
            }
        });
    }

    private boolean smoothScrollToOption = false;
    private int showMediaHintIndexAfterSmoothScroll = -1;

    private void onPollDoneButtonClick() {
        if (quizPoll && !doneItemEnabled) {
            int checksCount = 0;
            for (int a = 0; a < answersChecks.length; a++) {
                if (!TextUtils.isEmpty(getFixedString(answers[a])) && answersChecks[a]) {
                    checksCount++;
                }
            }
            if (checksCount <= 0) {
                showQuizHint();
            }
            return;
        }


        for (int index = 0; index < answers.length; index++) {
            if (TextUtils.isEmpty(getFixedString(answers[index]))) {
                if (attachedMedia.get(index) != null) {
                    smoothScrollToOption = true;
                    showMediaHintIndexAfterSmoothScroll = index;
                    listView.smoothScrollToPosition(answerStartRow + index);
                    return;
                }
            }
        }


        CharSequence questionText = getFixedString(questionString);
        CharSequence[] questionCharSequence = new CharSequence[]{questionText};
        ArrayList<TLRPC.MessageEntity> questionEntities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(questionCharSequence, true);
        questionText = questionCharSequence[0];
        if (questionEntities != null) {
            for (int a = 0, N = questionEntities.size(); a < N; a++) {
                TLRPC.MessageEntity entity = questionEntities.get(a);
                if (entity.offset + entity.length > questionText.length()) {
                    entity.length = questionText.length() - entity.offset;
                }
            }
        }

        TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
        poll.poll = new TLRPC.TL_poll();
        poll.poll.multiple_choice = multipleChoise;
        poll.poll.quiz = quizPoll;
        poll.poll.public_voters = !anonymousPoll;
        poll.poll.open_answers = allowAddingOptions;
        poll.poll.revoting_disabled = !allowRevoting;
        poll.poll.shuffle_answers = shuffleOptions;
        poll.poll.creator = true;

        if (pollLimitDuration != 0) {
            poll.poll.hide_results_until_close = hideResults;
            poll.poll.close_period = pollLimitDuration;
            poll.poll.flags |= TLObject.FLAG_4;
        } else if (pollLimitDeadline != 0) {
            poll.poll.hide_results_until_close = hideResults;
            poll.poll.close_date = pollLimitDeadline;
            poll.poll.flags |= TLObject.FLAG_5;
        }

        poll.poll.question = new TLRPC.TL_textWithEntities();
        poll.poll.question.text = questionText.toString();
        poll.poll.question.entities = questionEntities;

        ArrayList<Integer> correctAnswers = new ArrayList<>(maxAnswersCount);
        for (int a = 0; a < answers.length; a++) {
            if (TextUtils.isEmpty(getFixedString(answers[a]))) {
                attachedMedia.removeAnswerAndShift(poll.poll.answers.size());
                continue;
            }
            CharSequence answerText = getFixedString(answers[a]);
            CharSequence[] answerCharSequence = new CharSequence[]{answerText};
            ArrayList<TLRPC.MessageEntity> answerEntities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(answerCharSequence, true);
            answerText = answerCharSequence[0];
            if (answerEntities != null) {
                for (int b = 0, N = answerEntities.size(); b < N; b++) {
                    TLRPC.MessageEntity entity = answerEntities.get(b);
                    if (entity.offset + entity.length > answerText.length()) {
                        entity.length = answerText.length() - entity.offset;
                    }
                }
            }

            TLRPC.PollAnswer answer = new TLRPC.TL_pollAnswer();
            answer.text = new TLRPC.TL_textWithEntities();
            answer.text.text = answerText.toString();
            answer.text.entities = answerEntities;
            answer.option = new byte[1];
            answer.option[0] = (byte) (48 + poll.poll.answers.size());
            if ((multipleChoise || quizPoll) && answersChecks[a]) {
                correctAnswers.add(poll.poll.answers.size());
            }
            poll.poll.answers.add(answer);
        }

        poll.results = new TLRPC.TL_pollResults();
        CharSequence solution = getFixedString(solutionString);
        if (solution != null) {
            poll.results.solution = solution.toString();
            CharSequence[] message = new CharSequence[]{solution};
            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(parentAlert.currentAccount).getEntities(message, true);
            if (entities != null && !entities.isEmpty()) {
                poll.results.solution_entities = entities;
            }
            if (!TextUtils.isEmpty(poll.results.solution)) {
                poll.results.flags |= 16;
            }
        }
        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
        AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), 1 + parentAlert.getAdditionalMessagesCount(), payStars -> {
            if (chatActivity.isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(chatActivity.getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                    delegate.sendPoll(poll, descriptionString, attachedMedia, correctAnswers, notify, scheduleDate, payStars);
                    parentAlert.dismiss(true);
                });
            } else {
                delegate.sendPoll(poll, descriptionString, attachedMedia, correctAnswers, true, 0, payStars);
                parentAlert.dismiss(true);
            }
        });
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 1) {
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(1);
        if (child == null) {
            return Integer.MAX_VALUE;
        }
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = (int) child.getY() - AndroidUtilities.dp(8 + 12);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 1 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 1) {
            newOffset = top;
        }
        return newOffset + AndroidUtilities.dp(25);
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(17);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    public int getListTopPadding() {
        return topPadding;
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20) || emojiViewVisible || isAnimatePopupClosing || isEmojiSearchOpened) {
            padding = AndroidUtilities.dp(52);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = (availableHeight / 5 * 2);
            }
            padding -= AndroidUtilities.dp(13);
            if (padding < 0) {
                padding = 0;
            }
            parentAlert.setAllowNestedScroll(allowNesterScroll);
        }
        ignoreLayout = true;
        if (topPadding != padding || listView.getPaddingBottom() != listPaddingBottom) {
            topPadding = padding;
            listView.setPaddingWithoutRequestLayout(0, 0, 0, listPaddingBottom);
            listView.setItemAnimator(null);
            listAdapter.notifyItemChanged(paddingRow);
        }
        ignoreLayout = false;
    }

    @Override
    public int getButtonsHideOffset() {
        return AndroidUtilities.dp(70);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(1);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (currentCell != null) {
                int color = currentCell.getEditField().getCurrentTextColor();
                currentCell.getEditField().setTextColor(0xffffffff);
                currentCell.getEditField().setTextColor(color);
            }
        }
    }

    public static CharSequence getFixedString(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        text = AndroidUtilities.getTrimmedString(text);
        while (TextUtils.indexOf(text, "\n\n\n") >= 0) {
            text = TextUtils.replace(text, new String[]{"\n\n\n"}, new CharSequence[]{"\n\n"});
        }
        while (TextUtils.indexOf(text, "\n\n\n") == 0) {
            text = TextUtils.replace(text, new String[]{"\n\n\n"}, new CharSequence[]{"\n\n"});
        }
        return text;
    }

    private void showMediaHint(int index) {
        final RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(answerStartRow + index);
        if (holder != null && holder.itemView instanceof PollEditTextCell) {
            PollEditTextCell pollEditTextCell = (PollEditTextCell) holder.itemView;
            if (pollEditTextCell.getTop() > AndroidUtilities.dp(40)) {
                if (suggestEmojiPanel != null) {
                    suggestEmojiPanel.forceClose();
                }
                hintView.setText(getString(R.string.PollAddTextOrRemoveMedia));
                hintView.showForView(pollEditTextCell.getCheckBox(), true);
                hintView.arrowImageView.setTranslationX(hintView.arrowImageView.getTranslationX() + dp(48));
                hintView.setTranslationY(hintView.getTranslationY() + dp(10));
            }
        }
    }

    private void showQuizHint() {
        for (int a = answerStartRow; a < answerStartRow + answersCount; a++) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(a);
            if (holder != null && holder.itemView instanceof PollEditTextCell) {
                PollEditTextCell pollEditTextCell = (PollEditTextCell) holder.itemView;
                if (pollEditTextCell.getTop() > AndroidUtilities.dp(40)) {
                    if (suggestEmojiPanel != null) {
                        suggestEmojiPanel.forceClose();
                    }
                    hintView.setText(getString(R.string.PollTapToSelect));
                    hintView.showForView(pollEditTextCell.getCheckBox(), true);
                    break;
                }
            }
        }
    }

    private boolean doneItemEnabled;
    private void checkDoneButton() {
        boolean enabled = true;
        int checksCount = 0;
        if (quizPoll) {
            for (int a = 0; a < answersChecks.length; a++) {
                if (!TextUtils.isEmpty(getFixedString(answers[a])) && answersChecks[a]) {
                    checksCount++;
                }
            }
        }
        int count = 0;
        if (!TextUtils.isEmpty(getFixedString(descriptionString)) && descriptionString.length() > MAX_CAPTION_LENGTH) {
            enabled = false;
        } else if (!TextUtils.isEmpty(getFixedString(solutionString)) && solutionString.length() > MAX_SOLUTION_LENGTH) {
            enabled = false;
        } else if (TextUtils.isEmpty(getFixedString(questionString)) || questionString.length() > MAX_QUESTION_LENGTH) {
            enabled = false;
        }
        boolean hasAnswers = false;
        for (int a = 0; a < answers.length; a++) {
            if (!TextUtils.isEmpty(getFixedString(answers[a]))) {
                hasAnswers = true;
                if (answers[a].length() > MAX_ANSWER_LENGTH) {
                    count = 0;
                    break;
                }
                count++;
            }
        }
        if (count < (todo ? 1 : 2) || quizPoll && checksCount < 1) {
            enabled = false;
        }
        if (!TextUtils.isEmpty(solutionString) || !TextUtils.isEmpty(questionString) || !TextUtils.isEmpty(descriptionString) || hasAnswers || attachedMedia.medias.size() > 0) {
            allowNesterScroll = false;
        } else {
            allowNesterScroll = true;
        }
        parentAlert.setAllowNestedScroll(allowNesterScroll);
        doneItemEnabled = enabled;
        parentAlert.updateDoneItemEnabled();
    }

    @Override
    public boolean isDoneItemEnabled() {
        return doneItemEnabled;
    }

    @Override
    public boolean hasDoneItem() {
        return true;
    }

    private void updateRows() {
        solutionRowHeader = -1;
        solutionRow = -1;
        solutionInfoRow = -1;
        poll2vMultipleRow = -1;
        poll2vAnonymousRow = -1;
        poll2vLimitDurationRow = -1;
        poll2vLimitDurationTimeRow = -1;
        poll2vLimitDurationHideResultsRow = -1;
        poll2vLimitDurationHideResultsRowInfo = -1;
        poll2vAllowAddingRow = -1;
        poll2vShuffleRow = -1;
        poll2vAllowRevotingRow = -1;
        poll2vQuizRow = -1;
        allowAddingRow = -1;
        allowMarkingRow = -1;
        addAnswerRow = -1;
        answerStartRow = -1;
        settingsSectionRow = -1;
        descriptionRow = -1;

        rowCount = 0;
        paddingRow = rowCount++;

        questionHeaderRow = rowCount++;
        questionRow = rowCount++;
        if (!todo) {
            descriptionRow = rowCount++;
        }
        questionSectionRow = rowCount++;
        answerHeaderRow = rowCount++;
        if (answersCount != 0) {
            answerStartRow = rowCount;
            rowCount += answersCount;
        }
        if (answersCount != answers.length) {
            addAnswerRow = rowCount++;
        }
        answerSectionRow = rowCount++;
        settingsHeaderRow = rowCount++;
        if (todo) {
            allowMarkingRow = rowCount++;
            if (allowMarking) {
                allowAddingRow = rowCount++;
            }
        } else {
            TLRPC.Chat chat = ((ChatActivity) parentAlert.baseFragment).getCurrentChat();
            if (!ChatObject.isChannel(chat) || chat.megagroup) {
                poll2vAnonymousRow = rowCount++;
            } else {
                anonymousPoll = true;
            }
            if (quizOnly != 1) {
                poll2vMultipleRow = rowCount++;
            }
            if (!ChatObject.isChannel(chat) || chat.megagroup) {
                poll2vAllowAddingRow = rowCount++;
            } else {
                allowAddingOptions = false;
            }
            poll2vAllowRevotingRow = rowCount++;
            poll2vShuffleRow = rowCount++;
            if (quizOnly == 0) {
                poll2vQuizRow = rowCount++;
            }
            poll2vLimitDurationRow = rowCount++;
            if (pollLimitDuration != 0 || pollLimitDeadline != 0) {
                poll2vLimitDurationTimeRow = rowCount++;
                poll2vLimitDurationHideResultsRow = rowCount++;
                poll2vLimitDurationHideResultsRowInfo = rowCount++;
            }
            settingsSectionRow = rowCount++;
            if (quizPoll) {
                solutionRowHeader = rowCount++;
                solutionRow = rowCount++;
                solutionInfoRow = rowCount++;
            }
        }
        emptyRow = rowCount++;
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        try {
            parentAlert.actionBar.getTitleTextView().setBuildFullLayout(true);
        } catch (Exception ignore) {}
        if (todo) {
            parentAlert.actionBar.setTitle(getString(R.string.TodoTitle));
        } else if (quizOnly == 1) {
            parentAlert.actionBar.setTitle(getString(R.string.NewQuiz));
        } else {
            parentAlert.actionBar.setTitle(getString(R.string.NewPoll));
        }
        parentAlert.updateDoneItemEnabled();
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyed = true;
        if (isPremium) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
            if (emojiView != null) {
                parentAlert.sizeNotifierFrameLayout.removeView(emojiView);
            }
        }
    }

    @Override
    public void onHidden() {
        parentAlert.updateDoneItemEnabled();
    }

    @Override
    public boolean onBackPressed() {
        if (emojiViewVisible) {
            hideEmojiPopup(true);
            return true;
        }
        if (!checkDiscard()) {
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean onDismissWithTouchOutside() {
        if (!checkDiscard()) {
            return false;
        }
        return super.onDismissWithTouchOutside();
    }

    private boolean checkDiscard() {
        boolean allowDiscard = TextUtils.isEmpty(getFixedString(questionString))
            && TextUtils.isEmpty(getFixedString(descriptionString))
            && TextUtils.isEmpty(getFixedString(solutionString))
            && attachedMedia.medias.size() == 0;

        if (allowDiscard) {
            for (int a = 0; a < answersCount; a++) {
                allowDiscard = TextUtils.isEmpty(getFixedString(answers[a]));
                if (!allowDiscard) {
                    break;
                }
            }
        }
        if (!allowDiscard) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentAlert.baseFragment.getParentActivity());
            builder.setTitle(getString(todo ? R.string.CancelTodoAlertTitle : R.string.CancelPollAlertTitle));
            builder.setMessage(getString(todo ? R.string.CancelTodoAlertText : R.string.CancelPollAlertText));
            builder.setPositiveButton(getString(R.string.PassportDiscard), (dialogInterface, i) -> parentAlert.dismiss());
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.show();
        }
        return allowDiscard;
    }

    public void setDelegate(PollCreateActivityDelegate pollCreateActivityDelegate) {
        delegate = pollCreateActivityDelegate;
    }

    private void setTextLeft(View cell, int index) {
        if (!(cell instanceof PollEditTextCell)) {
            return;
        }
        PollEditTextCell textCell = (PollEditTextCell) cell;
        int max;
        int left;
        if (index == descriptionRow) {
            max = MAX_CAPTION_LENGTH;
            left = MAX_CAPTION_LENGTH - (descriptionString != null ? descriptionString.length() : 0);
        } else if (index == questionRow) {
            if (todo) {
                max = getMessagesController().todoTitleLengthMax;
            } else {
                max = MAX_QUESTION_LENGTH;
            }
            left = max - (questionString != null ? questionString.length() : 0);
        } else if (index == solutionRow) {
            max = MAX_SOLUTION_LENGTH;
            left = MAX_SOLUTION_LENGTH - (solutionString != null ? solutionString.length() : 0);
        } else if (index >= answerStartRow && index < answerStartRow + answersCount) {
            index -= answerStartRow;
            if (todo) {
                max = getMessagesController().todoItemLengthMax;
            } else {
                max = MAX_ANSWER_LENGTH;
            }
            left = max - (answers[index] != null ? answers[index].length() : 0);
        } else {
            return;
        }
        if (left <= max - max * 0.7f) {
            textCell.setText2(String.format("%d", left));
            SimpleTextView textView = textCell.getTextView2();
            int key = left < 0 ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText3;
            textView.setTextColor(getThemedColor(key));
            textView.setTag(key);
        } else {
            textCell.setText2("");
        }
    }

    private void addNewField() {
        resetSuggestEmojiPanel();
        listView.setItemAnimator(itemAnimator);
        answersChecks[answersCount] = false;
        answersCount++;
        if (answersCount == answers.length) {
            listAdapter.notifyItemRemoved(addAnswerRow);
        }
        listAdapter.notifyItemInserted(addAnswerRow);
        updateRows();
        requestFieldFocusAtPosition = answerStartRow + answersCount - 1;
        listAdapter.notifyItemChanged(answerSectionRow);
        listAdapter.notifyItemChanged(emptyRow);
    }

    private void updateSuggestEmojiPanelDelegate(RecyclerView.ViewHolder holder) {
        if (suggestEmojiPanel != null) {
            suggestEmojiPanel.forceClose();
            if (suggestEmojiPanel != null && holder != null && holder.itemView instanceof PollEditTextCell && suggestEmojiPanel.getDelegate() != holder.itemView) {
                suggestEmojiPanel.setDelegate((PollEditTextCell) holder.itemView);
            }
        }
    }

    private void resetSuggestEmojiPanel() {
        if (suggestEmojiPanel != null) {
            suggestEmojiPanel.setDelegate(null);
            suggestEmojiPanel.forceClose();
        }
    }

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (!isPremium) {
            return;
        }
        if (height > dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (emojiViewVisible) {
            int newHeight = (isWidthGreater ? keyboardHeightLand : keyboardHeight);
            if (isEmojiSearchOpened) {
                newHeight += AndroidUtilities.dp(120);
            }
            newHeight += AndroidUtilities.navigationBarHeight;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight || wasEmojiSearchOpened != isEmojiSearchOpened) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                emojiView.setLayoutParams(layoutParams);
                emojiPadding = layoutParams.height;
                keyboardNotifier.fire();
                parentAlert.sizeNotifierFrameLayout.requestLayout();
                if (wasEmojiSearchOpened != isEmojiSearchOpened) {
                    animateEmojiViewTranslationY(wasEmojiSearchOpened ? -AndroidUtilities.dp(120) : AndroidUtilities.dp(120), 0);
                }
                wasEmojiSearchOpened = isEmojiSearchOpened;
            }
        }

        if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
            return;
        }
        lastSizeChangeValue1 = height;
        lastSizeChangeValue2 = isWidthGreater;

        boolean oldValue = keyboardVisible;
        if (currentCell != null) {
            final EditTextBoldCursor editText = currentCell.getEditField();
            keyboardVisible = editText.isFocused() && keyboardNotifier.keyboardVisible() && height > 0;
        } else {
            keyboardVisible = false;
        }
        if (keyboardVisible && emojiViewVisible) {
            showEmojiPopup(0);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !emojiViewVisible) {
            emojiPadding = 0;
            keyboardNotifier.fire();
            parentAlert.sizeNotifierFrameLayout.requestLayout();
        }

        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
    }

    public boolean isWaitingForKeyboardOpen() {
        return waitingForKeyboardOpen;
    }

    public boolean emojiViewVisible, emojiViewWasVisible;
    private int emojiPadding;
    private int keyboardHeight, keyboardHeightLand;
    private boolean keyboardVisible, isAnimatePopupClosing;
    private PollEditTextCell currentCell;

    private void onEmojiClicked(PollEditTextCell cell) {
        this.currentCell = cell;
        if (emojiViewVisible) {
            collapseSearchEmojiView();
            openKeyboardInternal();
        } else {
            showEmojiPopup(1);
        }
    }

    private void collapseSearchEmojiView() {
        if (isEmojiSearchOpened) {
            emojiView.closeSearch(false);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            layoutParams.height -= AndroidUtilities.dp(120);
            emojiView.setLayoutParams(layoutParams);
            emojiPadding = layoutParams.height;
            wasEmojiSearchOpened = isEmojiSearchOpened;
            isEmojiSearchOpened = false;
            animateEmojiViewTranslationY(-AndroidUtilities.dp(120), 0);
        }
    }

    private void openKeyboardInternal() {
        if (currentCell != null) {
            keyboardNotifier.awaitKeyboard();
            final EditTextBoldCursor editText = currentCell.getEditField();
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }
        showEmojiPopup(AndroidUtilities.usingHardwareInput ? 0 : 2);

        if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
            waitingForKeyboardOpen = true;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    private void showEmojiPopup(int show) {
        if (!isPremium) {
            return;
        }
        if (show == 1) {
            boolean emojiWasVisible = emojiView != null && emojiView.getVisibility() == View.VISIBLE;
            createEmojiView();

            emojiView.setVisibility(VISIBLE);
            emojiViewWasVisible = emojiViewVisible;
            emojiViewVisible = true;
            View currentView = emojiView;

            if (keyboardHeight <= 0) {
                if (AndroidUtilities.isTablet()) {
                    keyboardHeight = dp(150);
                } else {
                    keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", dp(200));
                }
            }
            if (keyboardHeightLand <= 0) {
                if (AndroidUtilities.isTablet()) {
                    keyboardHeightLand = dp(150);
                } else {
                    keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", dp(200));
                }
            }
            int currentHeight = (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight);

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            layoutParams.height = currentHeight + AndroidUtilities.navigationBarHeight;
            currentView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() && currentCell != null) {
                AndroidUtilities.hideKeyboard(currentCell.getEditField());
            }

            emojiPadding = currentHeight;
            keyboardNotifier.fire();
            parentAlert.sizeNotifierFrameLayout.requestLayout();

            ChatActivityEnterViewAnimatedIconView emojiButton = currentCell == null ? null : currentCell.getEmojiButton();
            if (emojiButton != null) {
                emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.KEYBOARD, true);
            }
            if (!emojiWasVisible && !keyboardVisible) {
                ValueAnimator animator = ValueAnimator.ofFloat(emojiPadding, 0);
                animator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    emojiView.setTranslationY(v);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        emojiView.setTranslationY(0);
                    }
                });
                animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                animator.start();
            }
        } else {
            ChatActivityEnterViewAnimatedIconView emojiButton = currentCell == null ? null : currentCell.getEmojiButton();
            if (emojiButton != null) {
                emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, true);
            }
            if (emojiView != null) {
                emojiViewWasVisible = emojiViewVisible;
                emojiViewVisible = false;
                isEmojiSearchOpened = false;
                if (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    emojiView.setVisibility(GONE);
                }
            }
            if (show == 0) {
                emojiPadding = 0;
            }
            keyboardNotifier.fire();
            parentAlert.sizeNotifierFrameLayout.requestLayout();
        }
    }

    private void onCellFocusChanges(PollEditTextCell cell, boolean focused) {
        if (isPremium && focused) {
            if (currentCell == cell && emojiViewVisible && isEmojiSearchOpened) {
                collapseSearchEmojiView();
                emojiViewVisible = false;
            }
            PollEditTextCell prevCell = currentCell;
            currentCell = cell;
            cell.setEmojiButtonVisibility(true);
            cell.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
            updateSuggestEmojiPanelDelegate(listView.findContainingViewHolder(cell));
            if (prevCell != null && prevCell != cell) {
                if (emojiViewVisible) {
                    collapseSearchEmojiView();
                    hideEmojiPopup(false);
                    openKeyboardInternal();
                }
                prevCell.setEmojiButtonVisibility(false);
                prevCell.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
            }
        }
    }

    private void hideEmojiPopup(boolean byBackButton) {
        if (!isPremium) {
            return;
        }
        if (emojiViewVisible) {
            emojiView.scrollEmojiToTop();
            emojiView.closeSearch(false);
            if (byBackButton) {
                emojiView.hideSearchKeyboard();
            }
            isEmojiSearchOpened = false;
            showEmojiPopup(0);
        }
        if (byBackButton) {
            if (emojiView != null && emojiView.getVisibility() == View.VISIBLE) {
                int height = emojiView.getMeasuredHeight();
                ValueAnimator animator = ValueAnimator.ofFloat(0, height);
                animator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    emojiView.setTranslationY(v);
                });
                isAnimatePopupClosing = true;
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimatePopupClosing = false;
                        emojiView.setTranslationY(0);
                        hideEmojiView();
                    }
                });
                animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                animator.start();
            } else {
                hideEmojiView();
            }
        }
    }

    public void hideEmojiView() {
        if (!emojiViewVisible && emojiView != null && emojiView.getVisibility() != GONE) {
            if (currentCell != null) {
                ChatActivityEnterViewAnimatedIconView emojiButton = currentCell.getEmojiButton();
                if (emojiButton != null) {
                    emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                }
            }
            emojiView.setVisibility(GONE);
        }
        int wasEmojiPadding = emojiPadding;
        emojiPadding = 0;
        if (wasEmojiPadding != emojiPadding) {
            keyboardNotifier.fire();
        }
    }

    public boolean isAnimatePopupClosing() {
        return isAnimatePopupClosing;
    }

    public boolean isPopupShowing() {
        return emojiViewVisible;
    }

    public boolean isPopupVisible() {
        return emojiView != null && emojiView.getVisibility() == View.VISIBLE;
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    public boolean isEmojiSearchOpened = false;
    public boolean wasEmojiSearchOpened = false;

    private void createEmojiView() {
        if (emojiView != null && emojiView.currentAccount != UserConfig.selectedAccount) {
            parentAlert.sizeNotifierFrameLayout.removeView(emojiView);
            emojiView = null;
        }
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(null, true, false, false, getContext(), true, null, null, true, resourcesProvider, false);
        emojiView.emojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
        emojiView.shouldLightenBackground = false;
        emojiView.fixBottomTabContainerTranslation = false;
        emojiView.setShouldDrawBackground(false);
        emojiView.allowEmojisForNonPremium(false);
        emojiView.setVisibility(GONE);
        if (AndroidUtilities.isTablet()) {
            emojiView.setForseMultiwindowLayout(true);
        }
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public boolean onBackspace() {
                if (currentCell == null) {
                    return false;
                }
                final EditTextBoldCursor editText = currentCell.getEditField();
                if (editText == null) {
                    return false;
                }
                editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onEmojiSelected(String symbol) {
                if (currentCell == null) {
                    return;
                }
                final EditTextBoldCursor editText = currentCell.getEditField();
                if (editText == null) {
                    return;
                }
                int i = editText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, editText.getPaint().getFontMetricsInt(), false);
                    editText.setText(editText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    editText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
                if (currentCell == null) {
                    return;
                }
                final EditTextBoldCursor editText = currentCell.getEditField();
                if (editText == null) {
                    return;
                }
                int i = editText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    SpannableString spannable = new SpannableString(emoticon);
                    AnimatedEmojiSpan span;
                    if (document != null) {
                        span = new AnimatedEmojiSpan(document, editText.getPaint().getFontMetricsInt());
                    } else {
                        span = new AnimatedEmojiSpan(documentId, editText.getPaint().getFontMetricsInt());
                    }
                    span.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
                    spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    editText.setText(editText.getText().insert(i, spannable));
                    int j = i + spannable.length();
                    editText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onClearEmojiRecent() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                builder.setTitle(getString(R.string.ClearRecentEmojiTitle));
                builder.setMessage(getString(R.string.ClearRecentEmojiText));
                builder.setPositiveButton(getString(R.string.ClearButton), (dialogInterface, i) -> emojiView.clearRecentEmoji());
                builder.setNegativeButton(getString(R.string.Cancel), null);
                builder.show();
            }

            @Override
            public void onSearchOpenClose(int type) {
                isEmojiSearchOpened = type != 0;
                parentAlert.sizeNotifierFrameLayout.requestLayout();
            }

            @Override
            public boolean isSearchOpened() {
                return isEmojiSearchOpened;
            }
        });
        parentAlert.sizeNotifierFrameLayout.addView(emojiView);
        emojiView.setBottomInset(AndroidUtilities.navigationBarHeight);
    }

    private void animateEmojiViewTranslationY(float fromY, float toY) {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            emojiView.setTranslationY(AndroidUtilities.lerp(fromY, toY, v));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                emojiView.setTranslationY(toY);
            }
        });
        animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
        animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
        animator.start();
    }

    private final Paint checkboxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void checkDurationInfoRow(TextCell cell, boolean animated) {
        if (pollLimitDeadline != 0) {
            cell.setTextAndValue(getString(R.string.PollV2PollEnds), LocaleController.formatShortDateTime(pollLimitDeadline), animated, false);
        } else if (pollLimitDuration != 0) {
            cell.setTextAndValue(getString(R.string.PollV2PollDuration), formatPluralString("Hours", pollLimitDuration / 3600), animated, false);
        } else {
            cell.setTextAndValue(getString(R.string.PollV2PollEnds), null, animated, false);
        }
    }


    private void deletePollAnswerView(View deleteButton, PollEditTextCell cell, boolean askAboutMedia) {
        if (deleteButton.getTag() != null) {
            return;
        }
        deleteButton.setTag(1);

        final RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
        if (holder == null) {
            return;
        }

        final int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        final int index = position - answerStartRow;
        final boolean hasMedia = attachedMedia.get(index) != null;
        if (askAboutMedia && hasMedia && parentAlert.baseFragment != null) {
            AlertDialog dialog = new AlertDialog.Builder(parentAlert.baseFragment.getParentActivity(), resourcesProvider)
                .setTitle(getString(!quizPoll ? R.string.DiscardPollOptionWithMediaAlertTitle : R.string.DiscardQuizOptionWithMediaAlertTitle))
                .setMessage(getString(!quizPoll ? R.string.DiscardPollOptionWithMediaMessage : R.string.DiscardQuizOptionWithMediaMessage))
                .setPositiveButton(getString(R.string.Delete), (dialogInterface, i) -> {
                    deleteButton.setTag(null);
                    deletePollAnswerView(deleteButton, cell, false);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .setOnCancelListener((dialogInterface) -> deleteButton.setTag(null))
                .create();

            dialog.show();
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }

            return;
        }


        attachedMedia.removeAnswerAndShift(index);

        listView.setItemAnimator(itemAnimator);
        listAdapter.notifyItemRemoved(position);
        System.arraycopy(answers, index + 1, answers, index, answers.length - 1 - index);
        System.arraycopy(answersChecks, index + 1, answersChecks, index, answersChecks.length - 1 - index);
        answers[answers.length - 1] = null;
        answersChecks[answersChecks.length - 1] = false;
        answersCount--;
        if (answersCount == answers.length - 1) {
            listAdapter.notifyItemInserted(answerStartRow + answers.length - 1);
        }

        final RecyclerView.ViewHolder prevHolder = listView.findViewHolderForAdapterPosition(position - 1);
        EditTextBoldCursor editText = cell.getTextView();
        if (prevHolder != null && prevHolder.itemView instanceof PollEditTextCell) {
            PollEditTextCell editTextCell = (PollEditTextCell) prevHolder.itemView;
            editTextCell.getTextView().requestFocus();
        } else if (editText.isFocused()) {
            AndroidUtilities.hideKeyboard(editText);
            hideEmojiPopup(true);
        } else if (isEmojiSearchOpened) {
            hideEmojiPopup(true);
        }
        editText.clearFocus();
        checkDoneButton();
        updateRows();
        if (suggestEmojiPanel != null) {
            suggestEmojiPanel.forceClose();
            suggestEmojiPanel.setDelegate(null);
        }
        listAdapter.notifyItemChanged(answerSectionRow);
        listAdapter.notifyItemChanged(emptyRow);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_ANSWER = 5;
        private static final int VIEW_TYPE_INPUT_QUESTION = 4;
        private static final int VIEW_TYPE_INPUT_DESCRIPTION = 11;
        private static final int VIEW_TYPE_CHECK_V2 = 10;

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == questionHeaderRow) {
                        cell.getTextView().setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                        cell.setText(getString(todo ? R.string.TodoTitle : R.string.PollQuestion2));
                    } else if (position == solutionRowHeader) {
                        cell.getTextView().setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                        cell.setText(getString(R.string.AddAnExplanationHeader));
                    } else {
                        cell.getTextView().setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                        if (position == answerHeaderRow) {
                            if (quizOnly == 1) {
                                cell.setText(getString(R.string.QuizAnswers));
                            } else {
                                cell.setText(getString(todo ? R.string.TodoItemsTitle : R.string.AnswerOptions2));
                            }
                        } else if (position == settingsHeaderRow) {
                            cell.setText(getString(R.string.Settings));
                        }
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setFixedSize(0);
                    Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    // cell.setBackgroundDrawable(combinedDrawable);
                    if (position == solutionInfoRow) {
                        cell.setText(getString(R.string.AddAnExplanationInfo));
                    } else if (position == settingsSectionRow) {
                        cell.setFixedSize(12);
                        cell.setText(null);
                    } else if (maxAnswersCount - answersCount <= 0) {
                        cell.setText(getString(todo ? R.string.TodoAddTaskInfoMax : R.string.AddAnOptionInfoMax));
                    } else if (todo) {
                        cell.setText(LocaleController.formatPluralStringComma("TodoNewTaskInfo", maxAnswersCount - answersCount));
                    } else if (position == poll2vLimitDurationHideResultsRowInfo) {
                        cell.setText(getString(R.string.PollV2HideResultsInfo));
                    } else {
                        cell.setText(LocaleController.formatString(R.string.AddAnOptionInfo, LocaleController.formatPluralString("Option", maxAnswersCount - answersCount)));
                    }
                    break;
                }
                case 3: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == poll2vLimitDurationTimeRow) {
                        checkDurationInfoRow(textCell, false);
                    } else {
                        textCell.setColors(-1, Theme.key_telegram_color_text);
                        Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                        Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                        drawable1.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                        drawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                        textCell.setTextAndIcon(getString(todo ? R.string.TodoNewTask : R.string.AddAnOption), combinedDrawable, false);
                        textCell.imageLeft = 20;
                        textCell.offsetFromImage = 58;
                    }
                    break;
                }
                case 6: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == allowAddingRow) {
                        checkCell.setTextAndCheck(getString(R.string.TodoAllowAddingTasks), allowAdding, allowMarkingRow != -1);
                        checkCell.setEnabled(true, null);
                    } else if (position == allowMarkingRow) {
                        checkCell.setTextAndCheck(getString(R.string.TodoAllowMarkingDone), allowMarking, false);
                        checkCell.setEnabled(true, null);
                    } else if (position == poll2vLimitDurationHideResultsRow) {
                        checkCell.setTextAndCheck(getString(R.string.PollV2HideResults), hideResults, false);
                        checkCell.setEnabled(true, null);
                    }
                    break;
                }
                case 9: {
                    View view = (View) holder.itemView;
                    view.requestLayout();
                    break;
                }
                case VIEW_TYPE_CHECK_V2: {
                    PollCreateCheckCell cell = (PollCreateCheckCell) holder.itemView;
                    cell.setDivider(false);
                    if (position == poll2vAnonymousRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2ShowWhoVoted),
                            getString(R.string.PollV2ShowWhoVotedInfo),
                            IconBackgroundColors.BLUE, R.drawable.filled_poll_view_24,
                            !anonymousPoll);
                    } else if (position == poll2vMultipleRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2AllowMultipleAnswers),
                            getString(R.string.PollV2AllowMultipleAnswersInfo),
                            IconBackgroundColors.ORANGE, R.drawable.filled_poll_multiple_24,
                            multipleChoise);
                    } else if (position == poll2vAllowRevotingRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2AllowRevoting),
                            getString(R.string.PollV2AllowRevotingInfo),
                            IconBackgroundColors.PURPLE, R.drawable.filled_poll_revote_24,
                            allowRevoting);
                    } else if (position == poll2vAllowAddingRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2AllowAddingOptions),
                            getString(R.string.PollV2AllowAddingOptionsInfo),
                            IconBackgroundColors.CYAN, R.drawable.filled_poll_add_24,
                            allowAddingOptions);
                    } else if (position == poll2vShuffleRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2ShuffleOptions),
                            getString(R.string.PollV2ShuffleOptionsInfo),
                            IconBackgroundColors.ORANGE_DEEP, R.drawable.filled_poll_shuffle_24,
                            shuffleOptions);
                    } else if (position == poll2vQuizRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2SetCorrectAnswer),
                            getString(R.string.PollV2SetCorrectAnswerInfo),
                            IconBackgroundColors.GREEN, R.drawable.filled_poll_correct_24,
                            quizPoll);
                    } else if (position == poll2vLimitDurationRow) {
                        cell.setTextAndValueAndIconAndCheck(
                            getString(R.string.PollV2LimitDuration),
                            getString(R.string.PollV2LimitDurationInfo),
                            IconBackgroundColors.RED, R.drawable.filled_poll_deadline_24,
                            pollLimitDuration != 0 || pollLimitDeadline != 0);
                        cell.setDivider(pollLimitDuration != 0 || pollLimitDeadline != 0);
                    }

                    if (position == poll2vAllowAddingRow) {
                        cell.getCheckBox().setIconVisible(quizPoll || anonymousPoll, false);
                    } else {
                        cell.getCheckBox().setIconVisible(false, false);
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_INPUT_QUESTION) {
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(questionString != null ? questionString : "", getString(todo ? R.string.TodoTitlePlaceholder : R.string.QuestionHint), true);
                textCell.setTag(null);
                setTextLeft(holder.itemView, holder.getAdapterPosition());
            } else if (viewType == VIEW_TYPE_INPUT_DESCRIPTION) {
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(descriptionString != null ? descriptionString : "", getString(R.string.QuestionDescriptionHint), false);
                textCell.setTag(null);
                textCell.attachView.setAttachedMedia(attachedMedia.get(PollAttachedMediaPack.INDEX_DESCRIPTION), false);
                setTextLeft(holder.itemView, holder.getAdapterPosition());
            } else if (viewType == VIEW_TYPE_ANSWER) {
                int position = holder.getAdapterPosition();
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setCheckboxMultiselect(multipleChoise, false);
                int index = position - answerStartRow;
                textCell.setTextAndHint(answers[index], getString(todo ? R.string.TodoTaskPlaceholder : R.string.OptionHint), true);
                textCell.setTag(null);
                if (requestFieldFocusAtPosition == position) {
                    EditTextBoldCursor editText = textCell.getTextView();
                    editText.requestFocus();
                    AndroidUtilities.showKeyboard(editText);
                    requestFieldFocusAtPosition = -1;
                }
                if (!todo) {
                    textCell.attachView.setAttachedMedia(attachedMedia.get(index), false);
                }
                setTextLeft(holder.itemView, position);
            } else if (viewType == 7) {
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(solutionString != null ? solutionString : "", getString(R.string.AddAnExplanation), false);
                textCell.setTag(null);
                if (!todo) {
                    textCell.attachView.setAttachedMedia(attachedMedia.get(PollAttachedMediaPack.INDEX_EXPLANATION), false);
                }
                setTextLeft(holder.itemView, holder.getAdapterPosition());
            }
        }

        @Override
        public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == VIEW_TYPE_INPUT_QUESTION || holder.getItemViewType() == VIEW_TYPE_INPUT_DESCRIPTION || holder.getItemViewType() == 5) {
                PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                EditTextBoldCursor editText = editTextCell.getTextView();
                if (editText.isFocused()) {
                    if (isPremium) {
                        if (suggestEmojiPanel != null) {
                            suggestEmojiPanel.forceClose();
                        }
                        hideEmojiPopup(true);
                    }
                    currentCell = null;
                    editText.clearFocus();
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == addAnswerRow
                || quizOnly == 0 && position == poll2vQuizRow
                || position == poll2vAnonymousRow || position == poll2vMultipleRow
                || position == poll2vAllowAddingRow || position == poll2vLimitDurationRow
                || position == poll2vAllowRevotingRow || position == poll2vShuffleRow
                || position == poll2vLimitDurationTimeRow || position == poll2vLimitDurationHideResultsRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, false, resourcesProvider);
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext, resourcesProvider);
                    Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    // view.setBackgroundDrawable(combinedDrawable);
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext, resourcesProvider);
                    break;
                case 3:
                    view = new TextCell(mContext, resourcesProvider);
                    break;
                case VIEW_TYPE_INPUT_DESCRIPTION:
                case VIEW_TYPE_INPUT_QUESTION: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, null, resourcesProvider) {
                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
                        }

                        @Override
                        protected void onEditTextFocusChanged(boolean focused) {
                            onCellFocusChanges(this, focused);
                        }

                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (!todo && viewType == VIEW_TYPE_INPUT_DESCRIPTION) {
                                if (editText.isFocused() && editText.hasSelection()) {
                                    Menu menu = actionMode.getMenu();
                                    if (menu.findItem(android.R.id.copy) == null) {
                                        return;
                                    }
                                    ChatActivity.fillActionModeMenu(menu, ((ChatActivity) parentAlert.baseFragment).getCurrentEncryptedChat(), false, true);
                                }
                            } else {
                                super.onActionModeStart(editText, actionMode);
                            }
                        }

                        @Override
                        protected void onEmojiButtonClicked(PollEditTextCell cell1) {
                            onEmojiClicked(cell1);
                        }

                        @Override
                        public boolean onPastedMultipleLines(ArrayList<CharSequence> parts) {
                            if (parts.isEmpty()) return false;
                            int index = -1;
                            textView.getText().replace(textView.getSelectionStart(), textView.getSelectionEnd(), parts.remove(0));
                            index++;
                            while (!parts.isEmpty() && index < maxAnswersCount) {
                                for (int i = answers.length - 1; i > index; --i) {
                                    answers[i] = answers[i - 1];
                                }
                                answers[index] = parts.remove(0);
                                answersCount++;
                                index++;
                            }
                            updateRows();
                            requestFieldFocusAtPosition = answerStartRow + index - 1;
                            listView.setItemAnimator(itemAnimator);
                            listAdapter.notifyDataSetChanged();
                            return true;
                        }
                    };

                    if (viewType == VIEW_TYPE_INPUT_DESCRIPTION) {
                        if (!todo) {
                            cell.setTextRight(98);
                            cell.addAttachView().setOnClickListener(v -> {
                                openAttachOrReplaceMenuForOptions(PollAttachedMediaPack.INDEX_DESCRIPTION);
                            });
                        }
                    }
                    cell.createErrorTextView();
                    cell.setIconsColor(Theme.key_pollCreateIcons);
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (cell.getTag() != null) {
                                return;
                            }

                            final int index = viewType == VIEW_TYPE_INPUT_DESCRIPTION ? descriptionRow : questionRow;
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(index);
                            if (holder != null) {
                                if (suggestEmojiPanel != null) {
                                    ImageSpan[] spans = s.getSpans(0, s.length(), ImageSpan.class);
                                    for (ImageSpan span : spans) {
                                        s.removeSpan(span);
                                    }
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), false);

                                    suggestEmojiPanel.setDirection(SuggestEmojiView.DIRECTION_TO_TOP);
                                    suggestEmojiPanel.setDelegate(cell);
                                    suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                                    suggestEmojiPanel.fireUpdate();
                                }
                            }
                            if (viewType == VIEW_TYPE_INPUT_DESCRIPTION) {
                                descriptionString = s;
                            } else {
                                questionString = s;
                            }
                            if (holder != null) {
                                setTextLeft(holder.itemView, index);
                            }
                            checkDoneButton();
                        }
                    });
                    view = cell;
                    break;
                }
                case 6:
                    view = new TextCheckCell(mContext, resourcesProvider);
                    break;
                case 7: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, null) {
                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
                        }

                        @Override
                        protected void onEditTextFocusChanged(boolean focused) {
                            onCellFocusChanges(this, focused);
                        }

                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (editText.isFocused() && editText.hasSelection()) {
                                Menu menu = actionMode.getMenu();
                                if (menu.findItem(android.R.id.copy) == null) {
                                    return;
                                }
                                ChatActivity.fillActionModeMenu(menu, ((ChatActivity) parentAlert.baseFragment).getCurrentEncryptedChat(), false, true);
                            }
                        }

                        @Override
                        protected void onEmojiButtonClicked(PollEditTextCell cell1) {
                            onEmojiClicked(cell1);
                        }
                    };
                    cell.createErrorTextView();
                    if (!todo) {
                        cell.setTextRight(98);
                        cell.addAttachView().setOnClickListener(v -> {
                            openAttachOrReplaceMenuForOptions(PollAttachedMediaPack.INDEX_EXPLANATION);
                        });
                    }
                    cell.setIconsColor(Theme.key_pollCreateIcons);
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (cell.getTag() != null) {
                                return;
                            }
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(questionRow);
                            if (holder != null) {
                                if (suggestEmojiPanel != null) {
                                    ImageSpan[] spans = s.getSpans(0, s.length(), ImageSpan.class);
                                    for (ImageSpan span : spans) {
                                        s.removeSpan(span);
                                    }
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), false);

                                    suggestEmojiPanel.setDirection(SuggestEmojiView.DIRECTION_TO_TOP);
                                    suggestEmojiPanel.setDelegate(cell);
                                    suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                                    suggestEmojiPanel.fireUpdate();
                                }
                            }
                            solutionString = s;
                            if (holder != null) {
                                setTextLeft(holder.itemView, solutionRow);
                            }
                            checkDoneButton();
                        }
                    });
                    view = cell;
                    break;
                }
                case 8: {
                    view = new EmptyView(mContext);
                    // view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                }
                case 9: {
                    view = new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), topPadding);
                        }
                    };
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                }
                case VIEW_TYPE_CHECK_V2: {
                    PollCreateCheckCell cell = new PollCreateCheckCell(mContext, resourcesProvider);
                    cell.getCheckBox().setIcon(R.drawable.permission_locked);
                    view = cell;
                    break;
                }
                case VIEW_TYPE_ANSWER:
                default: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, v -> {
                        deletePollAnswerView(v, (PollEditTextCell) v.getParent(), true);
                    }, resourcesProvider) {

                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (todo) {
                                if (editText.isFocused() && editText.hasSelection()) {
                                    Menu menu = actionMode.getMenu();
                                    if (menu.findItem(android.R.id.copy) == null) {
                                        return;
                                    }
                                    ChatActivity.fillActionModeMenu(menu, ((ChatActivity) parentAlert.baseFragment).getCurrentEncryptedChat(), false, true);
                                }
                            } else {
                                super.onActionModeStart(editText, actionMode);
                            }
                        }

                        @Override
                        protected boolean drawDivider() {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(this);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                if (answersCount == maxAnswersCount && position == answerStartRow + answersCount - 1) {
                                    return false;
                                }
                            }
                            return true;
                        }

                        @Override
                        protected boolean shouldShowCheckBox() {
                            return quizPoll;
                        }

                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
                        }

                        @Override
                        protected void onEditTextFocusChanged(boolean focused) {
                            onCellFocusChanges(this, focused);
                        }

                        @Override
                        protected void onCheckBoxClick(PollEditTextCell editText, boolean checked) {
                            if (checked && quizPoll && !multipleChoise) {
                                Arrays.fill(answersChecks, false);
                                int count = listView.getChildCount();
                                for (int a = answerStartRow; a < answerStartRow + answersCount; a++) {
                                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(a);
                                    if (holder != null && holder.itemView instanceof PollEditTextCell) {
                                        PollEditTextCell pollEditTextCell = (PollEditTextCell) holder.itemView;
                                        pollEditTextCell.setChecked(false, true);
                                    }
                                }
                            }
                            super.onCheckBoxClick(editText, checked);
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(editText);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                if (position != RecyclerView.NO_POSITION) {
                                    int index = position - answerStartRow;
                                    answersChecks[index] = checked;
                                }
                            }
                            checkDoneButton();
                        }

                        @Override
                        protected boolean isChecked(PollEditTextCell editText) {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(editText);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                if (position != RecyclerView.NO_POSITION) {
                                    int index = position - answerStartRow;
                                    return answersChecks[index];
                                }
                            }
                            return false;
                        }

                        @Override
                        protected void onEmojiButtonClicked(PollEditTextCell cell1) {
                            onEmojiClicked(cell1);
                        }

                        @Override
                        public boolean onPastedMultipleLines(ArrayList<CharSequence> parts) {
                            if (parts.isEmpty()) return false;
                            int index = listView.getChildAdapterPosition(this) - answerStartRow;
                            if (index < 0) return false;
                            textView.getText().replace(textView.getSelectionStart(), textView.getSelectionEnd(), parts.remove(0));
                            index++;
                            while (!parts.isEmpty() && index < maxAnswersCount) {
                                for (int i = answers.length - 1; i > index; --i) {
                                    answers[i] = answers[i - 1];
                                }
                                answers[index] = parts.remove(0);
                                answersCount++;
                                index++;
                            }
                            updateRows();
                            requestFieldFocusAtPosition = answerStartRow + index - 1;
                            listView.setItemAnimator(itemAnimator);
                            listAdapter.notifyDataSetChanged();
                            return true;
                        }
                    };
                    if (!todo) {
                        cell.setTextRight(140);
                        cell.addAttachView().setOnClickListener(v -> {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                int index = position - answerStartRow;
                                if (index < 0 || index >= answers.length) {
                                    return;
                                }
                                openAttachOrReplaceMenuForOptions(index);
                            }
                        });
                    }
                    cell.setIconsColor(Theme.key_pollCreateIcons);
                    cell.supportMultiselect();
                    cell.getCheckBox().setColor(-1, Theme.key_pollCreateIcons, Theme.key_checkboxCheck);
                    // cell.getCheckBox().setCirclePaintProvider(obj -> checkboxPaint);
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                int index = position - answerStartRow;
                                if (index < 0 || index >= answers.length) {
                                    return;
                                }
                                if (suggestEmojiPanel != null) {
                                    ImageSpan[] spans = s.getSpans(0, s.length(), ImageSpan.class);
                                    for (ImageSpan span : spans) {
                                        s.removeSpan(span);
                                    }
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), false);
                                    float y = holder.itemView.getY() - AndroidUtilities.dp(166) + holder.itemView.getMeasuredHeight();
                                    if (y > 0) {
                                        suggestEmojiPanel.setDirection(SuggestEmojiView.DIRECTION_TO_BOTTOM);
                                        suggestEmojiPanel.setTranslationY(y);
                                    } else {
                                        suggestEmojiPanel.setDirection(SuggestEmojiView.DIRECTION_TO_TOP);
                                        suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                                    }
                                    suggestEmojiPanel.setDelegate(cell);
                                    suggestEmojiPanel.fireUpdate();
                                }

                                answers[index] = s;
                                setTextLeft(cell, position);
                                checkDoneButton();
                            }
                        }
                    });
                    cell.setShowNextButton(true);
                    EditTextBoldCursor editText = cell.getTextView();
                    editText.setImeOptions(editText.getImeOptions() | EditorInfo.IME_ACTION_NEXT);
                    editText.setOnEditorActionListener((v, actionId, event) -> {
                        if (actionId == EditorInfo.IME_ACTION_NEXT) {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                if (position != RecyclerView.NO_POSITION) {
                                    int index = position - answerStartRow;
                                    if (index == answersCount - 1 && answersCount < maxAnswersCount) {
                                        addNewField();
                                    } else {
                                        if (index == answersCount - 1) {
                                            AndroidUtilities.hideKeyboard(cell.getTextView());
                                        } else {
                                            holder = listView.findViewHolderForAdapterPosition(position + 1);
                                            if (holder != null && holder.itemView instanceof PollEditTextCell) {
                                                PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                                                editTextCell.getTextView().requestFocus();
                                            }
                                        }
                                    }
                                }
                            }
                            return true;
                        }
                        return false;
                    });
                    editText.setOnKeyListener((v, keyCode, event) -> {
                        EditTextBoldCursor field = (EditTextBoldCursor) v;
                        if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && field.length() == 0) {
                            cell.callOnDelete();
                            return true;
                        }
                        return false;
                    });
                    view = cell;
                    break;
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == poll2vAnonymousRow || position == poll2vMultipleRow || position == poll2vQuizRow ||
                    position == poll2vAllowAddingRow || position == poll2vAllowRevotingRow ||
                    position == poll2vShuffleRow || position == poll2vLimitDurationRow) {
                return VIEW_TYPE_CHECK_V2;
            } else if (position == questionHeaderRow || position == answerHeaderRow || position == settingsHeaderRow || position == solutionRowHeader) {
                return VIEW_TYPE_HEADER;
            } else if (position == questionSectionRow) {
                return 1;
            } else if (position == answerSectionRow || position == settingsSectionRow || position == solutionInfoRow || position == poll2vLimitDurationHideResultsRowInfo) {
                return 2;
            } else if (position == addAnswerRow || position == poll2vLimitDurationTimeRow) {
                return 3;
            } else if (position == questionRow) {
                return VIEW_TYPE_INPUT_QUESTION;
            } else if (position == descriptionRow) {
                return VIEW_TYPE_INPUT_DESCRIPTION;
            } else if (position == solutionRow) {
                return 7;
            } else if (position == allowAddingRow || position == allowMarkingRow || position == poll2vLimitDurationHideResultsRow) {
                return 6;
            } else if (position == emptyRow) {
                return 8;
            } else if (position == paddingRow) {
                return 9;
            } else {
                return VIEW_TYPE_ANSWER;
            }
        }

        public void swapElements(int fromIndex, int toIndex) {
            int idx1 = fromIndex - answerStartRow;
            int idx2 = toIndex - answerStartRow;
            if (idx1 < 0 || idx2 < 0 || idx1 >= answersCount || idx2 >= answersCount) {
                return;
            }

            PollAttachedMedia mTemp = attachedMedia.get(idx1);
            attachedMedia.set(idx1, attachedMedia.get(idx2));
            attachedMedia.set(idx2, mTemp);
            CharSequence from = answers[idx1];
            answers[idx1] = answers[idx2];
            answers[idx2] = from;
            boolean temp = answersChecks[idx1];
            answersChecks[idx1] = answersChecks[idx2];
            answersChecks[idx2] = temp;
            notifyItemMoved(fromIndex, toIndex);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyView.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"deleteImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"moveImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{PollEditTextCell.class}, new String[]{"deleteImageView"}, null, null, null, Theme.key_stickers_menuSelector));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{PollEditTextCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{PollEditTextCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{PollEditTextCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_telegram_color_text));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));

        return themeDescriptions;
    }

    private int getAnswersMaxCount() {
        if (todo) {
            return getMessagesController().todoItemsMax;
        } else {
            return getMessagesController().config.pollAnswersMax.get();
        }
    }

    private int getCurrentAccount() {
        return parentAlert != null ? parentAlert.currentAccount : UserConfig.selectedAccount;
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(getCurrentAccount());
    }




    private void openEditOrReplaceMenu(int index) {
        final PollAttachedMedia pollAttachedMedia = attachedMedia.get(index);
        if (pollAttachedMedia == null || parentAlert == null || parentAlert.baseFragment == null) {
            return;
        }

        final Activity activity = parentAlert.baseFragment.getParentActivity();
        if (pollAttachedMedia instanceof PollAttachedMediaGallery) {
            final PollAttachedMediaGallery gallery = (PollAttachedMediaGallery) pollAttachedMedia;

            ArrayList<Object> arrayList = new ArrayList<>(1);
            arrayList.add(gallery.photoEntry);

            PhotoViewer.getInstance().setParentActivity(activity);
            PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, PhotoViewer.SELECT_TYPE_POLL_MEDIA_EDIT, false, new PhotoViewer.EmptyPhotoViewerProvider() {
                private boolean openReplace;

                @Override
                public void onPollAttachReplace() {
                    openReplace = true;
                }

                @Override
                public void onPollAttachDelete() {
                    setAttachedMedia(index, null);
                }

                @Override
                public boolean allowCaption() {
                    return false;
                }

                @Override
                public void onClose() {
                    super.onClose();
                    if (openReplace) {
                        openAttachMenuForOptions(index);
                    }
                }
            }, null);
        } else if (pollAttachedMedia instanceof PollAttachedMediaSticker) {
            final PollAttachedMediaSticker sticker = (PollAttachedMediaSticker) pollAttachedMedia;
            ContentPreviewViewer.getInstance().setParentActivity(activity);
            ContentPreviewViewer.getInstance().setDelegate(new ContentPreviewViewer.ContentPreviewViewerDelegate() {
                @Override
                public ItemOptions getCustomItemOptions(@NonNull ViewGroup container, @NonNull View scrimView) {
                    final ItemOptions io = ItemOptions.makeOptions(container, new View(getContext()));
                    return io.setDimAlpha(0).setDrawScrim(false)
                        .add(R.drawable.msg_replace, getString(R.string.ReplaceAttachedPollMedia), () -> openAttachMenuForOptions(index))
                        .add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> setAttachedMedia(index, null));
                }

                @Override
                public long getDialogId() {
                    return 0;
                }
            });
            ContentPreviewViewer.getInstance().open(sticker.sticker, null, "", null, null,
                MessageObject.isAnimatedEmoji(sticker.sticker) ? ContentPreviewViewer.CONTENT_TYPE_EMOJI : ContentPreviewViewer.CONTENT_TYPE_STICKER,
                false, sticker.parent, resourcesProvider, 200);
        } else if (pollAttachedMedia instanceof PollAttachedMediaFile) {
            final PollAttachedMediaFile file = (PollAttachedMediaFile) pollAttachedMedia;
            final String title = file.name;
            final String subtitle = AndroidUtilities.formatFileSize(file.size, true, true) + " " + file.ext;
            showOptionsForDrawable(index, v -> PollAttachedMediaFile.createMessagePreviewDrawable(v, title, subtitle, null, null), dp(240), dp(60));
        } else if (pollAttachedMedia instanceof PollAttachedMediaMusic) {
            final PollAttachedMediaMusic music = (PollAttachedMediaMusic) pollAttachedMedia;
            final TLRPC.Document document = music.messageObject.getDocument();
            final String title =  MessageObject.getMusicTitle(document, true);
            final String subtitle = (MessageObject.getMusicAuthor(document, true) + " - " + LocaleController.formatShortDuration((int) MessageObject.getDocumentDuration(document)));
            showOptionsForDrawable(index, v -> PollAttachedMediaFile.createMessagePreviewDrawable(v, title, subtitle, music.messageObject.getDocument(), music.messageObject), dp(240), dp(60));
        } else if (pollAttachedMedia instanceof PollAttachedMediaLocation) {
            PollAttachedMediaLocation location = (PollAttachedMediaLocation) pollAttachedMedia;
            showOptionsForDrawable(index, location::createMessagePreviewDrawable, dp(300), dp(300) * 9 / 16);
        } else {
            openAttachMenuForOptions(index);
        }
    }

    private void showOptionsForDrawable(int index, Utilities.CallbackReturn<View, Drawable> callback, int width, int height) {
        final ItemOptions options = ItemOptions.makeOptions(this, new View(getContext()))
                .setDimAlpha(0).setDrawScrim(false)
                .add(R.drawable.msg_replace, getString(R.string.ReplaceAttachedPollMedia), () -> openAttachMenuForOptions(index))
                .add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> setAttachedMedia(index, null));

        final ScrimOptions dialog = new ScrimOptions(getContext(), resourcesProvider);
        options.setOnDismiss(dialog::dismiss);
        options.setMinWidth(dp(185));
        options.setupSelectors();
        dialog.setItemOptions(options);
        dialog.setScrimDrawable(callback.run(dialog.getWindowView()), width, height);
        dialog.setOptionsAtCenter();
        dialog.show();
    }

    private void openAttachOrReplaceMenuForOptions(int index) {
        if (attachedMedia.get(index) != null) {
            openEditOrReplaceMenu(index);
        } else {
            openAttachMenuForOptions(index);
        }
    }



    public static ChatAttachAlert openPollAttachMenu(BaseFragment fragment, int layoutToShow, int allowedLayouts, Utilities.Callback<PollAttachedMedia> callback, Runnable onDismiss) {
        if (fragment == null) {
            return null;
        }

        ChatAttachAlert chatAttachAlert = new ChatAttachAlert(fragment.getContext(), fragment, false, false, true, fragment.getResourceProvider()) {
            @Override
            public void dismissInternal() {
                super.dismissInternal();
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        };
        chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                if (button == 7 || button == 8) {
                    HashMap<Object, Object> photos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                    ArrayList<Object> order = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();
                    for (int a = 0; a < order.size(); a++) {
                        Object object = photos.get(order.get(a));
                        SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                        if (object instanceof MediaController.PhotoEntry) {
                            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                            if (photoEntry.imagePath != null) {
                                info.path = photoEntry.imagePath;
                            } else {
                                info.path = photoEntry.path;
                            }
                            info.thumbPath = photoEntry.thumbPath;
                            info.coverPath = photoEntry.coverPath;
                            info.videoEditedInfo = photoEntry.editedInfo;
                            info.isLivePhoto = photoEntry.isLivePhoto;
                            info.livePhotoVideoOffset = photoEntry.livePhotoVideoOffset;
                            info.discardLivePhoto = true;
                            info.isVideo = photoEntry.isVideo;
                            info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                            info.entities = photoEntry.entities;
                            info.masks = photoEntry.stickers;
                            info.ttl = photoEntry.ttl;
                            info.emojiMarkup = photoEntry.emojiMarkup;
                            info.originalPhotoEntry = photoEntry;
                        } else if (object instanceof MediaController.SearchImage) {
                            MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                            if (searchImage.imagePath != null) {
                                info.path = searchImage.imagePath;
                            } else {
                                info.searchImage = searchImage;
                            }
                            info.thumbPath = searchImage.thumbPath;
                            info.coverPath = searchImage.coverPath;
                            info.videoEditedInfo = searchImage.editedInfo;
                            info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                            info.entities = searchImage.entities;
                            info.masks = searchImage.stickers;
                            info.ttl = searchImage.ttl;
                            if (searchImage.inlineResult != null && searchImage.type == 1) {
                                info.inlineResult = searchImage.inlineResult;
                                info.params = searchImage.params;
                            }
                            searchImage.date = (int) (System.currentTimeMillis() / 1000);
                        }
                        callback.run(new PollAttachedMediaGallery(info));
                        break;
                    }
                }
                chatAttachAlert.dismiss(true);
            }

            /*
            @Override
            public View getRevealView() {
                return chatActivityEnterView.getAttachButton();
            }
            */

            @Override
            public void didSelectBot(TLRPC.User user) {
            }

            @Override
            public void onCameraOpened() {
            }

            @Override
            public boolean needEnterComment() {
                return false;
            }

            @Override
            public void doOnIdle(Runnable runnable) {
                NotificationCenter.getInstance(fragment.getCurrentAccount()).doOnIdle(runnable);
            }
        });
        chatAttachAlert.setEmojiViewDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
                callback.run(new PollAttachedMediaSticker(document, null));
                chatAttachAlert.dismiss(true);
            }

            @Override
            public void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                callback.run(new PollAttachedMediaSticker(sticker, parent));
                chatAttachAlert.dismiss(true);
            }
        });

        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            // chatActivityEnterView.closeKeyboard();
        }

        chatAttachAlert.setMaxSelectedPhotos(1, true);
        chatAttachAlert.enablePollAttachMode(layoutToShow, allowedLayouts);
        chatAttachAlert.setLocationActivityDelegate((location, live, notify, scheduleDate, payStars) -> {
            callback.run(new PollAttachedMediaLocation(location));
        });
        chatAttachAlert.setDocumentsDelegate(new ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<TLRPC.MessageEntity> captionEntities, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, long payStars) {
                if (files != null && !files.isEmpty()) {
                    callback.run(new PollAttachedMediaFile(files.get(0)));
                }
                chatAttachAlert.dismiss(true);
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
                if (photos != null && !photos.isEmpty()) {
                    callback.run(new PollAttachedMediaGallery(photos.get(0)));
                }
                chatAttachAlert.dismiss(true);
            }

            @Override
            public void startDocumentSelectActivity() {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    fragment.getParentActivity().startActivityForResult(intent, 28);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        chatAttachAlert.setAudioSelectDelegate((audios, caption, notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars) -> {
            if (audios != null && !audios.isEmpty()) {
                callback.run(new PollAttachedMediaMusic(audios.get(0)));
            }
            chatAttachAlert.dismiss(true);
        });
        chatAttachAlert.init();
        chatAttachAlert.setFocusable(true);
        // chatAttachAlert.parentThemeDelegate = resourcesProvider;

        chatAttachAlert.show();
        // parentAlert.baseFragment.showDialog(chatAttachAlert);
        return chatAttachAlert;
    }

    private ChatAttachAlert currentAttachAlert;
    private int currentAttachAlertIndex;

    private void openAttachMenuForOptions(int index) {
        currentAttachAlertIndex = index;

        currentAttachAlert = openPollAttachMenu(parentAlert.baseFragment, getStartLayoutForMedia(attachedMedia.get(index)), getAllowedLayoutsForIndex(index), media -> {
            setAttachedMedia(index, media);
        }, () -> {
            currentAttachAlertIndex = PollAttachedMediaPack.INDEX_NONE;
            currentAttachAlert = null;
        });
    }

    public void onPollAttachFilePicker(Intent data) {
        if (currentAttachAlertIndex == PollAttachedMediaPack.INDEX_NONE || currentAttachAlert == null) {
            return;
        }

        Uri uri = null;
        if (data != null) {
            if (data.getData() != null) {
                uri = data.getData();
            } else if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uri = clipData.getItemAt(i).getUri();
                    break;
                }
            }
        }

        if (uri == null) {
            BulletinFactory.of(parentAlert.container, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.UnsupportedAttachment), resourcesProvider).show();
            return;
        }

        setAttachedMedia(currentAttachAlertIndex, new PollAttachedMediaFile(uri));

        if (currentAttachAlert != null) {
            currentAttachAlert.dismiss(true);
        }
    }

    public static int getStartLayoutForMedia(PollAttachedMedia attachedMedia) {
        if (attachedMedia instanceof PollAttachedMediaMusic) {
            return ChatAttachAlert.LAYOUT_TYPE_MUSIC;
        } else if (attachedMedia instanceof PollAttachedMediaFile) {
            return ChatAttachAlert.LAYOUT_TYPE_DOCUMENTS;
        } else if (attachedMedia instanceof PollAttachedMediaSticker) {
            if (((PollAttachedMediaSticker) attachedMedia).isEmoji) {
                return ChatAttachAlert.LAYOUT_TYPE_EMOJI;
            } else {
                return ChatAttachAlert.LAYOUT_TYPE_STICKERS;
            }
        } else if (attachedMedia instanceof PollAttachedMediaLocation) {
            return ChatAttachAlert.LAYOUT_TYPE_LOCATION;
        }
        return ChatAttachAlert.LAYOUT_TYPE_PHOTO;
    }

    public static int getAllowedLayoutsForIndex(int index) {
        if (index == PollAttachedMediaPack.INDEX_DESCRIPTION) {
            return (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_DOCUMENTS)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_MUSIC)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);
        } else if (index == PollAttachedMediaPack.INDEX_EXPLANATION) {
            return (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_DOCUMENTS)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_MUSIC)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);
        } else {
            return (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_STICKERS)
                // | (1 << ChatAttachAlert.LAYOUT_TYPE_EMOJI)
                | (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);
        }
    }



    /* * */


    private final PollAttachedMediaPack attachedMedia = new PollAttachedMediaPack();

    private int mediaIndexToAdapterPosition(int index) {
        if (index == PollAttachedMediaPack.INDEX_DESCRIPTION) {
            return descriptionRow;
        } else if (index == PollAttachedMediaPack.INDEX_EXPLANATION) {
            return solutionRow;
        } else if (answerStartRow >= 0 && index >= 0 && index < answersCount) {
            return answerStartRow + index;
        }
        return -1;
    }

    private void setAttachedMedia(int index, PollAttachedMedia media) {
        if (media != null) {
            attachedMedia.set(index, media);
        } else {
            attachedMedia.remove(index);
        }

        final int position = mediaIndexToAdapterPosition(index);
        if (position >= 0) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof PollEditTextCell) {
                PollEditTextCell cell = (PollEditTextCell) holder.itemView;
                cell.attachView.setAttachedMedia(media, true);
            } else {
                listAdapter.notifyItemChanged(position);
            }
        }

        checkDoneButton();
    }
}
