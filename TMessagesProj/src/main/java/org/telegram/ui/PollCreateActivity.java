package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.ChatAttachAlertPollLayout;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SuggestEmojiView;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PollCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate {

    private ActionBarMenuItem doneItem;
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private RecyclerView.LayoutManager layoutManager;
    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private ChatActivity parentFragment;
    private HintView hintView;

    private CharSequence[] answers = new CharSequence[10];
    private boolean[] answersChecks = new boolean[10];
    private int answersCount = 1;
    private CharSequence questionString;
    private CharSequence solutionString;
    private boolean anonymousPoll = true;
    private boolean multipleChoise;
    private boolean quizPoll;
    private boolean hintShowed;
    private int quizOnly;

    public boolean emojiViewVisible, emojiViewWasVisible;

    private SuggestEmojiView suggestEmojiPanel;
    private EmojiView emojiView;
    private KeyboardNotifier keyboardNotifier;
    private boolean waitingForKeyboardOpen;
    private boolean destroyed;
    private int emojiPadding;
    private int keyboardHeight, keyboardHeightLand;
    private boolean keyboardVisible, isAnimatePopupClosing;
    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;
    private PollEditTextCell currentCell;
    private boolean isPremium;

    private PollCreateActivityDelegate delegate;

    private int requestFieldFocusAtPosition = -1;

    private int questionHeaderRow;
    private int questionRow;
    private int solutionRow;
    private int solutionInfoRow;
    private int questionSectionRow;
    private int answerHeaderRow;
    private int answerStartRow;
    private int addAnswerRow;
    private int answerSectionRow;
    private int settingsHeaderRow;
    private int anonymousRow;
    private int multipleRow;
    private int quizRow;
    private int settingsSectionRow;
    private int rowCount;

    private static final int done_button = 1;

    public interface PollCreateActivityDelegate {
        void sendPoll(TLRPC.TL_messageMediaPoll poll, HashMap<String, String> params, boolean notify, int scheduleDate);
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 5) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    private Runnable openKeyboardRunnable = new Runnable() {
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

    public PollCreateActivity(ChatActivity chatActivity, Boolean quiz) {
        super();
        parentFragment = chatActivity;
        isPremium = AccountInstance.getInstance(currentAccount).getUserConfig().isPremium();
        if (quiz != null) {
            quizPoll = quiz;
            quizOnly = quizPoll ? 1 : 2;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (quizOnly == 1) {
            actionBar.setTitle(LocaleController.getString("NewQuiz", R.string.NewQuiz));
        } else {
            actionBar.setTitle(LocaleController.getString("NewPoll", R.string.NewPoll));
        }
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    if (quizPoll && doneItem.getAlpha() != 1.0f) {
                        int checksCount = 0;
                        for (int a = 0; a < answersChecks.length; a++) {
                            if (!TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(answers[a])) && answersChecks[a]) {
                                checksCount++;
                            }
                        }
                        if (checksCount <= 0) {
                            showQuizHint();
                        }
                        return;
                    }
                    CharSequence questionText = ChatAttachAlertPollLayout.getFixedString(questionString);
                    CharSequence[] questionCharSequence = new CharSequence[]{ questionText };
                    ArrayList<TLRPC.MessageEntity> questionEntities = MediaDataController.getInstance(currentAccount).getEntities(questionCharSequence, true);
                    questionText = questionCharSequence[0];
                    for (int a = 0, N = questionEntities.size(); a < N; a++) {
                        TLRPC.MessageEntity entity = questionEntities.get(a);
                        if (entity.offset + entity.length > questionText.length()) {
                            entity.length = questionText.length() - entity.offset;
                        }
                    }

                    TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
                    poll.poll = new TLRPC.TL_poll();
                    poll.poll.multiple_choice = multipleChoise;
                    poll.poll.quiz = quizPoll;
                    poll.poll.public_voters = !anonymousPoll;
                    poll.poll.question = new TLRPC.TL_textWithEntities();
                    poll.poll.question.text = questionText.toString();
                    poll.poll.question.entities = questionEntities;
                    SerializedData serializedData = new SerializedData(10);
                    for (int a = 0; a < answers.length; a++) {
                        if (TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(answers[a]))) {
                            continue;
                        }
                        CharSequence answerText = ChatAttachAlertPollLayout.getFixedString(answers[a]);
                        CharSequence[] answerCharSequence = new CharSequence[]{ answerText };
                        ArrayList<TLRPC.MessageEntity> answerEntities = MediaDataController.getInstance(currentAccount).getEntities(answerCharSequence, true);
                        answerText = answerCharSequence[0];
                        for (int b = 0, N = answerEntities.size(); b < N; b++) {
                            TLRPC.MessageEntity entity = answerEntities.get(b);
                            if (entity.offset + entity.length > answerText.length()) {
                                entity.length = answerText.length() - entity.offset;
                            }
                        }
                        TLRPC.PollAnswer answer = new TLRPC.TL_pollAnswer();
                        answer.text = new TLRPC.TL_textWithEntities();
                        answer.text.text = answerText.toString();
                        answer.text.entities = answerEntities;
                        answer.option = new byte[1];
                        answer.option[0] = (byte) (48 + poll.poll.answers.size());
                        poll.poll.answers.add(answer);
                        if ((multipleChoise || quizPoll) && answersChecks[a]) {
                            serializedData.writeByte(answer.option[0]);
                        }
                    }
                    HashMap<String, String> params = new HashMap<>();
                    params.put("answers", Utilities.bytesToHex(serializedData.toByteArray()));
                    poll.results = new TLRPC.TL_pollResults();
                    CharSequence solution = ChatAttachAlertPollLayout.getFixedString(solutionString);
                    if (solution != null) {
                        poll.results.solution = solution.toString();
                        CharSequence[] message = new CharSequence[]{solution};
                        ArrayList<TLRPC.MessageEntity> entities = getMediaDataController().getEntities(message, true);
                        if (entities != null && !entities.isEmpty()) {
                            poll.results.solution_entities = entities;
                        }
                        if (!TextUtils.isEmpty(poll.results.solution)) {
                            poll.results.flags |= 16;
                        }
                    }
                    if (parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate) -> {
                            delegate.sendPoll(poll, params, notify, scheduleDate);
                            finishFragment();
                        });
                    } else {
                        delegate.sendPoll(poll, params, true, 0);
                        finishFragment();
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItem(done_button, LocaleController.getString("Create", R.string.Create).toUpperCase());

        listAdapter = new ListAdapter(context);

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20) && !emojiViewVisible) {
                    ignoreLayout = true;
                    hideEmojiView();
                    ignoreLayout = false;
                }

                int keyboardPad = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? getEmojiPadding() : 0;
                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (emojiView != null && emojiView == child) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else if (listView == child) {
                        child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(heightSize - keyboardPad, MeasureSpec.EXACTLY));
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (emojiView != null && emojiView == child) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        sizeNotifierFrameLayout.setDelegate(this);

        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {
            @Override
            protected void requestChildOnScreen(View child, View focused) {
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
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == addAnswerRow) {
                addNewField();
            } else if (view instanceof TextCheckCell) {
                TextCheckCell cell = (TextCheckCell) view;
                boolean checked;
                boolean wasChecksBefore = quizPoll;
                if (suggestEmojiPanel != null) {
                    suggestEmojiPanel.forceClose();
                }
                if (position == anonymousRow) {
                    checked = anonymousPoll = !anonymousPoll;
                } else if (position == multipleRow) {
                    checked = multipleChoise = !multipleChoise;
                    if (multipleChoise && quizPoll) {
                        int prevSolutionRow = solutionRow;
                        quizPoll = false;
                        updateRows();
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(quizRow);
                        if (holder != null) {
                            ((TextCheckCell) holder.itemView).setChecked(false);
                        } else {
                            listAdapter.notifyItemChanged(quizRow);
                        }
                        listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2);
                    }
                } else {
                    if (quizOnly != 0) {
                        return;
                    }
                    checked = quizPoll = !quizPoll;
                    int prevSolutionRow = solutionRow;
                    updateRows();
                    if (quizPoll) {
                        listAdapter.notifyItemRangeInserted(solutionRow, 2);
                    } else {
                        listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2);
                    }
                    if (quizPoll && multipleChoise) {
                        multipleChoise = false;
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(multipleRow);
                        if (holder != null) {
                            ((TextCheckCell) holder.itemView).setChecked(false);
                        } else {
                            listAdapter.notifyItemChanged(multipleRow);
                        }
                    }
                    if (quizPoll) {
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
                        if (pollEditTextCell.getTop() > AndroidUtilities.dp(40) && position == quizRow && !hintShowed) {
                            hintView.showForView(pollEditTextCell.getCheckBox(), true);
                            hintShowed = true;
                        }
                    }
                }

                cell.setChecked(checked);
                checkDoneButton();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0 && hintView != null) {
                    hintView.hide();
                }
                if (suggestEmojiPanel != null && suggestEmojiPanel.isShown()) {
                    SuggestEmojiView.AnchorViewDelegate emojiDelegate = suggestEmojiPanel.getDelegate();
                    if (emojiDelegate instanceof PollEditTextCell) {
                        PollEditTextCell cell = (PollEditTextCell) emojiDelegate;
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(cell);
                        if (holder != null) {
                            if (suggestEmojiPanel.getDirection() == SuggestEmojiView.DIRECTION_TO_BOTTOM) {
                                suggestEmojiPanel.setTranslationY(holder.itemView.getY() - AndroidUtilities.dp(166) + holder.itemView.getMeasuredHeight());
                            } else {
                                suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                            }
                            if (!layoutManager.isViewPartiallyVisible(holder.itemView, true, true)) {
                                suggestEmojiPanel.forceClose();
                            }
                        } else {
                            suggestEmojiPanel.forceClose();
                        }
                    } else {
                        suggestEmojiPanel.forceClose();
                    }
                }
            }
        });

        hintView = new HintView(context, 4);
        hintView.setText(LocaleController.getString("PollTapToSelect", R.string.PollTapToSelect));
        hintView.setAlpha(0.0f);
        hintView.setVisibility(View.INVISIBLE);
        frameLayout.addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));

        if (isPremium) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            suggestEmojiPanel = new SuggestEmojiView(context, currentAccount, null, resourceProvider);
            suggestEmojiPanel.forbidCopy();
            suggestEmojiPanel.forbidSetAsStatus();
            suggestEmojiPanel.setHorizontalPadding(AndroidUtilities.dp(24));
            frameLayout.addView(suggestEmojiPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 160, Gravity.LEFT | Gravity.TOP));
        }
        keyboardNotifier = new KeyboardNotifier(sizeNotifierFrameLayout, null);

        checkDoneButton();

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isPremium) {
            hideEmojiPopup(false);
            if (suggestEmojiPanel != null) {
                suggestEmojiPanel.forceClose();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroyed = true;
        if (isPremium) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
            if (emojiView != null) {
                sizeNotifierFrameLayout.removeView(emojiView);
            }
        }
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

    private void showQuizHint() {
        int count = listView.getChildCount();
        for (int a = answerStartRow; a < answerStartRow + answersCount; a++) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(a);
            if (holder != null && holder.itemView instanceof PollEditTextCell) {
                PollEditTextCell pollEditTextCell = (PollEditTextCell) holder.itemView;
                if (pollEditTextCell.getTop() > AndroidUtilities.dp(40)) {
                    hintView.showForView(pollEditTextCell.getCheckBox(), true);
                    break;
                }
            }
        }
    }

    private void checkDoneButton() {
        boolean enabled = true;
        int checksCount = 0;
        if (quizPoll) {
            for (int a = 0; a < answersChecks.length; a++) {
                if (!TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(answers[a])) && answersChecks[a]) {
                    checksCount++;
                }
            }
        }
        if (!TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(solutionString)) && solutionString.length() > ChatAttachAlertPollLayout.MAX_SOLUTION_LENGTH) {
            enabled = false;
        } else if (TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(questionString)) || questionString.length() > ChatAttachAlertPollLayout.MAX_QUESTION_LENGTH) {
            enabled = false;
        } else {
            int count = 0;
            for (int a = 0; a < answers.length; a++) {
                if (!TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(answers[a]))) {
                    if (answers[a].length() > ChatAttachAlertPollLayout.MAX_ANSWER_LENGTH) {
                        count = 0;
                        break;
                    }
                    count++;
                }
            }
            if (count < 2 || quizPoll && checksCount < 1) {
                enabled = false;
            }
        }
        doneItem.setEnabled(quizPoll && checksCount == 0 || enabled);
        doneItem.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void updateRows() {
        rowCount = 0;
        questionHeaderRow = rowCount++;
        questionRow = rowCount++;
        questionSectionRow = rowCount++;
        answerHeaderRow = rowCount++;
        if (answersCount != 0) {
            answerStartRow = rowCount;
            rowCount += answersCount;
        } else {
            answerStartRow = -1;
        }
        if (answersCount != answers.length) {
            addAnswerRow = rowCount++;
        } else {
            addAnswerRow = -1;
        }
        answerSectionRow = rowCount++;
        settingsHeaderRow = rowCount++;
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (!ChatObject.isChannel(chat) || chat.megagroup) {
            anonymousRow = rowCount++;
        } else {
            anonymousRow = -1;
        }
        if (quizOnly != 1) {
            multipleRow = rowCount++;
        } else {
            multipleRow = -1;
        }
        if (quizOnly == 0) {
            quizRow = rowCount++;
        } else {
            quizRow = -1;
        }
        settingsSectionRow = rowCount++;
        if (quizPoll) {
            solutionRow = rowCount++;
            solutionInfoRow = rowCount++;
        } else {
            solutionRow = -1;
            solutionInfoRow = -1;
        }
    }

    @Override
    public boolean onBackPressed() {
        if (emojiViewVisible) {
            hideEmojiPopup(true);
            return true;
        }
        return checkDiscard();
    }

    private boolean checkDiscard() {
        boolean allowDiscard = TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(questionString));
        if (allowDiscard) {
            for (int a = 0; a < answersCount; a++) {
                allowDiscard = TextUtils.isEmpty(ChatAttachAlertPollLayout.getFixedString(answers[a]));
                if (!allowDiscard) {
                    break;
                }
            }
        }
        if (!allowDiscard) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("CancelPollAlertTitle", R.string.CancelPollAlertTitle));
            builder.setMessage(LocaleController.getString("CancelPollAlertText", R.string.CancelPollAlertText));
            builder.setPositiveButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialogInterface, i) -> finishFragment());
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
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
        if (index == questionRow) {
            max = ChatAttachAlertPollLayout.MAX_QUESTION_LENGTH;
            left = ChatAttachAlertPollLayout.MAX_QUESTION_LENGTH - (questionString != null ? questionString.length() : 0);
        } else if (index == solutionRow) {
            max = ChatAttachAlertPollLayout.MAX_SOLUTION_LENGTH;
            left = ChatAttachAlertPollLayout.MAX_SOLUTION_LENGTH - (solutionString != null ? solutionString.length() : 0);
        } else if (index >= answerStartRow && index < answerStartRow + answersCount) {
            index -= answerStartRow;
            max = ChatAttachAlertPollLayout.MAX_ANSWER_LENGTH;
            left = ChatAttachAlertPollLayout.MAX_ANSWER_LENGTH - (answers[index] != null ? answers[index].length() : 0);
        } else {
            return;
        }
        if (left <= max - max * 0.7f) {
            textCell.setText2(String.format("%d", left));
            SimpleTextView textView = textCell.getTextView2();
            int key = left < 0 ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText3;
            textView.setTextColor(Theme.getColor(key));
            textView.setTag(key);
        } else {
            textCell.setText2("");
        }
    }

    private void addNewField() {
        if (emojiView != null) {
            emojiView.scrollEmojiToTop();
        }
        hideEmojiPopup(false);
        resetSuggestEmojiPanel();
        answersChecks[answersCount] = false;
        answersCount++;
        if (answersCount == answers.length) {
            listAdapter.notifyItemRemoved(addAnswerRow);
        }
        listAdapter.notifyItemInserted(addAnswerRow);
        updateRows();
        requestFieldFocusAtPosition = answerStartRow + answersCount - 1;
        listAdapter.notifyItemChanged(answerSectionRow);
    }

    private void updateSuggestEmojiPanelDelegate(RecyclerView.ViewHolder holder) {
        if (suggestEmojiPanel != null ) {
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

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                emojiView.setLayoutParams(layoutParams);
                emojiPadding = layoutParams.height;
                keyboardNotifier.fire();
                sizeNotifierFrameLayout.requestLayout();
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
            sizeNotifierFrameLayout.requestLayout();
        }

        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
    }

    public boolean isWaitingForKeyboardOpen() {
        return waitingForKeyboardOpen;
    }

    private void onEmojiClicked(PollEditTextCell cell) {
        this.currentCell = cell;
        if (emojiViewVisible) {
            openKeyboardInternal();
        } else {
            showEmojiPopup(1);
        }
    }

    private void openKeyboardInternal() {
        keyboardNotifier.awaitKeyboard();
        final EditTextBoldCursor editText = currentCell.getEditField();
        AndroidUtilities.showKeyboard(editText);
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

            emojiView.setVisibility(View.VISIBLE);
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
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() && currentCell != null) {
                AndroidUtilities.hideKeyboard(currentCell.getEditField());
            }

            emojiPadding = currentHeight;
            keyboardNotifier.fire();
            sizeNotifierFrameLayout.requestLayout();

            ChatActivityEnterViewAnimatedIconView emojiButton = currentCell.getEmojiButton();
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
            ChatActivityEnterViewAnimatedIconView emojiButton = currentCell.getEmojiButton();
            if (emojiButton != null) {
                emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, true);
            }
            if (emojiView != null) {
                emojiViewWasVisible = emojiViewVisible;
                emojiViewVisible = false;
                if (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    emojiView.setVisibility(View.GONE);
                }
            }
            if (show == 0) {
                emojiPadding = 0;
            }
            keyboardNotifier.fire();
            sizeNotifierFrameLayout.requestLayout();
        }
    }

    private void hideEmojiPopup(boolean byBackButton) {
        if (!isPremium) {
            return;
        }
        if (emojiViewVisible) {
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
        if (!emojiViewVisible && emojiView != null && emojiView.getVisibility() != View.GONE) {
            if (currentCell != null) {
                ChatActivityEnterViewAnimatedIconView emojiButton = currentCell.getEmojiButton();
                if (emojiButton != null) {
                    emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                }
            }
            emojiView.setVisibility(View.GONE);
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

    private void createEmojiView() {
        if (emojiView != null && emojiView.currentAccount != UserConfig.selectedAccount) {
            sizeNotifierFrameLayout.removeView(emojiView);
            emojiView = null;
        }
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(null, true, false, false, getContext(), false, null, null, true, resourceProvider, false);
        emojiView.fixBottomTabContainerTranslation = false;
        emojiView.allowEmojisForNonPremium(false);
        emojiView.setVisibility(View.GONE);
        if (AndroidUtilities.isTablet()) {
            emojiView.setForseMultiwindowLayout(true);
        }
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public boolean onBackspace() {
                final EditTextBoldCursor editText = currentCell.getEditField();
                if (editText == null) {
                    return false;
                }
                editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onEmojiSelected(String symbol) {
                final EditTextBoldCursor editText = currentCell.getEditField();
                if (editText == null) {
                    return;
                }
                int i = editText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, editText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);
                    editText.setText(editText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    editText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
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
                    span.cacheType = emojiView.emojiCacheType;
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
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourceProvider);
                builder.setTitle(LocaleController.getString("ClearRecentEmojiTitle", R.string.ClearRecentEmojiTitle));
                builder.setMessage(LocaleController.getString("ClearRecentEmojiText", R.string.ClearRecentEmojiText));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton), (dialogInterface, i) -> emojiView.clearRecentEmoji());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.show();
            }
        });
        sizeNotifierFrameLayout.addView(emojiView);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

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
                case 0: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == questionHeaderRow) {
                        cell.setText(LocaleController.getString("PollQuestion", R.string.PollQuestion));
                    } else if (position == answerHeaderRow) {
                        if (quizOnly == 1) {
                            cell.setText(LocaleController.getString("QuizAnswers", R.string.QuizAnswers));
                        } else {
                            cell.setText(LocaleController.getString("AnswerOptions", R.string.AnswerOptions));
                        }
                    } else if (position == settingsHeaderRow) {
                        cell.setText(LocaleController.getString("Settings", R.string.Settings));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setFixedSize(0);
                    cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    if (position == solutionInfoRow) {
                        cell.setText(LocaleController.getString("AddAnExplanationInfo", R.string.AddAnExplanationInfo));
                    } else if (position == settingsSectionRow) {
                        if (quizOnly != 0) {
                            cell.setFixedSize(12);
                            cell.setText(null);
                        } else {
                            cell.setText(LocaleController.getString("QuizInfo", R.string.QuizInfo));
                        }
                    } else if (10 - answersCount <= 0) {
                        cell.setText(LocaleController.getString("AddAnOptionInfoMax", R.string.AddAnOptionInfoMax));
                    } else {
                        cell.setText(LocaleController.formatString("AddAnOptionInfo", R.string.AddAnOptionInfo, LocaleController.formatPluralString("Option", 10 - answersCount)));
                    }
                    break;
                }
                case 3: {
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText4);
                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                    textCell.setTextAndIcon(LocaleController.getString("AddAnOption", R.string.AddAnOption), combinedDrawable, false);
                    break;
                }
                case 6: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == anonymousRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("PollAnonymous", R.string.PollAnonymous), anonymousPoll, multipleRow != -1 || quizRow != -1);
                        checkCell.setEnabled(true, null);
                    } else if (position == multipleRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("PollMultiple", R.string.PollMultiple), multipleChoise, quizRow != -1);
                        checkCell.setEnabled(true, null);
                    } else if (position == quizRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("PollQuiz", R.string.PollQuiz), quizPoll, false);
                        checkCell.setEnabled(quizOnly == 0, null);
                    }
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 4) {
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(questionString != null ? questionString : "", LocaleController.getString("QuestionHint", R.string.QuestionHint), false);
                textCell.setTag(null);
                setTextLeft(holder.itemView, holder.getAdapterPosition());
            } else if (viewType == 5) {
                int position = holder.getAdapterPosition();
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                int index = position - answerStartRow;
                textCell.setTextAndHint(answers[index], LocaleController.getString("OptionHint", R.string.OptionHint), true);
                textCell.setTag(null);
                if (requestFieldFocusAtPosition == position) {
                    EditTextBoldCursor editText = textCell.getTextView();
                    editText.requestFocus();
                    AndroidUtilities.showKeyboard(editText);
                    requestFieldFocusAtPosition = -1;
                }
                setTextLeft(holder.itemView, position);
            } else if (viewType == 7) {
                PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                textCell.setTag(1);
                textCell.setTextAndHint(solutionString != null ? solutionString : "", LocaleController.getString("AddAnExplanation", R.string.AddAnExplanation), false);
                textCell.setTag(null);
                setTextLeft(holder.itemView, holder.getAdapterPosition());
            }
        }

        @Override
        public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 4 || holder.getItemViewType() == 5) {
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
            return position == addAnswerRow || position == anonymousRow || position == multipleRow || quizOnly == 0 && position == quizRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, null) {
                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (editText.isFocused() && editText.hasSelection()) {
                                Menu menu = actionMode.getMenu();
                                if (menu.findItem(android.R.id.copy) == null) {
                                    return;
                                }
                                ChatActivity.fillActionModeMenu(menu, parentFragment.getCurrentEncryptedChat(), false);
                            }
                        }

                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            super.onFieldTouchUp(editText);
                            if (isPremium) {
                                PollEditTextCell p = (PollEditTextCell) editText.getParent();
                                currentCell = p;
                                if (emojiView != null) {
                                    emojiView.scrollEmojiToTop();
                                }
                                p.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                                hideEmojiPopup(false);
                                updateSuggestEmojiPanelDelegate(listView.findContainingViewHolder(this));
                            }
                        }

                        @Override
                        protected void onEmojiButtonClicked(PollEditTextCell cell1) {
                            onEmojiClicked(cell1);
                        }
                    };
                    cell.createErrorTextView();
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);

                                    suggestEmojiPanel.setDirection(SuggestEmojiView.DIRECTION_TO_TOP);
                                    suggestEmojiPanel.setDelegate(cell);
                                    suggestEmojiPanel.setTranslationY(holder.itemView.getY());
                                    suggestEmojiPanel.fireUpdate();
                                }
                            }
                            questionString = s;
                            if (holder != null) {
                                setTextLeft(holder.itemView, questionRow);
                            }
                            checkDoneButton();
                        }
                    });
                    view = cell;
                    break;
                }
                case 6:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, null) {
                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (editText.isFocused() && editText.hasSelection()) {
                                Menu menu = actionMode.getMenu();
                                if (menu.findItem(android.R.id.copy) == null) {
                                    return;
                                }
                                ChatActivity.fillActionModeMenu(menu, parentFragment.getCurrentEncryptedChat(), false);
                            }
                        }

                        @Override
                        protected void onEmojiButtonClicked(PollEditTextCell cell1) {
                            onEmojiClicked(cell1);
                        }

                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            super.onFieldTouchUp(editText);
                            if (isPremium) {
                                PollEditTextCell p = (PollEditTextCell) editText.getParent();
                                currentCell = p;
                                if (emojiView != null) {
                                    emojiView.scrollEmojiToTop();
                                }
                                p.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                                hideEmojiPopup(false);
                                updateSuggestEmojiPanelDelegate(listView.findContainingViewHolder(this));
                            }
                        }
                    };
                    cell.createErrorTextView();
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);

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
                default: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, false, isPremium ? PollEditTextCell.TYPE_EMOJI : PollEditTextCell.TYPE_DEFAULT, v -> {
                        if (v.getTag() != null) {
                            return;
                        }
                        v.setTag(1);
                        PollEditTextCell p = (PollEditTextCell) v.getParent();
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(p);
                        if (holder != null) {
                            int position = holder.getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                int index = position - answerStartRow;
                                listAdapter.notifyItemRemoved(position);
                                System.arraycopy(answers, index + 1, answers, index, answers.length - 1 - index);
                                System.arraycopy(answersChecks, index + 1, answersChecks, index, answersChecks.length - 1 - index);
                                answers[answers.length - 1] = null;
                                answersChecks[answersChecks.length - 1] = false;
                                answersCount--;
                                if (answersCount == answers.length - 1) {
                                    listAdapter.notifyItemInserted(answerStartRow + answers.length - 1);
                                }
                                holder = listView.findViewHolderForAdapterPosition(position - 1);
                                EditTextBoldCursor editText = p.getTextView();
                                if (holder != null && holder.itemView instanceof PollEditTextCell) {
                                    PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                                    editTextCell.getTextView().requestFocus();
                                    if (isPremium) {
                                        if (emojiViewVisible) {
                                            currentCell = editTextCell;
                                            editTextCell.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.KEYBOARD, false);
                                        } else {
                                            editTextCell.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                                            hideEmojiPopup(false);
                                        }
                                    }
                                } else if (editText.isFocused()) {
                                    AndroidUtilities.hideKeyboard(editText);
                                    if (isPremium) {
                                        hideEmojiPopup(false);
                                    }
                                }
                                if (isPremium) {
                                    if (emojiView != null) {
                                        emojiView.scrollEmojiToTop();
                                    }
                                    p.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                                }
                                editText.clearFocus();
                                checkDoneButton();
                                updateRows();
                                if (suggestEmojiPanel != null) {
                                    suggestEmojiPanel.forceClose();
                                    suggestEmojiPanel.setDelegate(null);
                                }
                                listAdapter.notifyItemChanged(answerSectionRow);
                            }
                        }
                    }) {

                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            super.onFieldTouchUp(editText);
                            if (isPremium) {
                                PollEditTextCell p = (PollEditTextCell) editText.getParent();
                                currentCell = p;
                                if (emojiView != null) {
                                    emojiView.scrollEmojiToTop();
                                }
                                hideEmojiPopup(false);
                                p.getEmojiButton().setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
                                updateSuggestEmojiPanelDelegate(listView.findContainingViewHolder(this));
                            }
                        }

                        @Override
                        protected boolean drawDivider() {
                            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(this);
                            if (holder != null) {
                                int position = holder.getAdapterPosition();
                                if (answersCount == 10 && position == answerStartRow + answersCount - 1) {
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
                        protected void onCheckBoxClick(PollEditTextCell editText, boolean checked) {
                            if (checked && quizPoll) {
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
                    };
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                                    Emoji.replaceEmoji(s, cell.getEditField().getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);
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
                                setTextLeft(cell, index);
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
                                    if (index == answersCount - 1 && answersCount < 10) {
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
            if (position == questionHeaderRow || position == answerHeaderRow || position == settingsHeaderRow) {
                return 0;
            } else if (position == questionSectionRow) {
                return 1;
            } else if (position == answerSectionRow || position == settingsSectionRow || position == solutionInfoRow) {
                return 2;
            } else if (position == addAnswerRow) {
                return 3;
            } else if (position == questionRow) {
                return 4;
            } else if (position == solutionRow) {
                return 7;
            } else if (position == anonymousRow || position == multipleRow || position == quizRow) {
                return 6;
            } else {
                return 5;
            }
        }

        public void swapElements(int fromIndex, int toIndex) {
            int idx1 = fromIndex - answerStartRow;
            int idx2 = toIndex - answerStartRow;
            if (idx1 < 0 || idx2 < 0 || idx1 >= answersCount || idx2 >= answersCount) {
                return;
            }
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCell.class, PollEditTextCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));

        return themeDescriptions;
    }
}
