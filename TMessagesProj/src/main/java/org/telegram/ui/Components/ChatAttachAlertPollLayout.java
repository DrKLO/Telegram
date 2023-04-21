package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

public class ChatAttachAlertPollLayout extends ChatAttachAlert.AttachAlertLayout {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private SimpleItemAnimator itemAnimator;
    private FillLastLinearLayoutManager layoutManager;
    private HintView hintView;

    private String[] answers = new String[10];
    private boolean[] answersChecks = new boolean[10];
    private int answersCount = 1;
    private String questionString;
    private CharSequence solutionString;
    private boolean anonymousPoll = true;
    private boolean multipleChoise;
    private boolean quizPoll;
    private boolean hintShowed;
    private int quizOnly;

    private boolean allowNesterScroll;

    private boolean ignoreLayout;

    private PollCreateActivityDelegate delegate;

    private int requestFieldFocusAtPosition = -1;

    private int paddingRow;
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
    private int emptyRow;
    private int rowCount;

    private int topPadding;

    public static final int MAX_QUESTION_LENGTH = 255;
    public static final int MAX_ANSWER_LENGTH = 100;
    public static final int MAX_SOLUTION_LENGTH = 200;

    private static final int done_button = 40;

    public interface PollCreateActivityDelegate {
        void sendPoll(TLRPC.TL_messageMediaPoll poll, HashMap<String, String> params, boolean notify, int scheduleDate);
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
                listView.setItemAnimator(itemAnimator);
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
                viewHolder.itemView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
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
            viewHolder.itemView.setBackground(null);
        }
    }

    public ChatAttachAlertPollLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        updateRows();

        /*if (quiz != null) {
            quizPoll = quiz;
            quizOnly = quizPoll ? 1 : 2;
        }*/

        listAdapter = new ListAdapter(context);

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
        listView.setItemAnimator(itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                if (holder.getAdapterPosition() == 0) {
                    parentAlert.updateLayout(ChatAttachAlertPollLayout.this, true, 0);
                }
            }
        });
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(53), listView) {

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (topPadding - AndroidUtilities.dp(7));
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
            if (position == addAnswerRow) {
                addNewField();
            } else if (view instanceof TextCheckCell) {
                TextCheckCell cell = (TextCheckCell) view;
                boolean checked;
                boolean wasChecksBefore = quizPoll;
                if (position == anonymousRow) {
                    checked = anonymousPoll = !anonymousPoll;
                } else if (position == multipleRow) {
                    checked = multipleChoise = !multipleChoise;
                    if (multipleChoise && quizPoll) {
                        int prevSolutionRow = solutionRow;
                        quizPoll = false;
                        updateRows();
                        listView.setItemAnimator(itemAnimator);
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(quizRow);
                        if (holder != null) {
                            ((TextCheckCell) holder.itemView).setChecked(false);
                        } else {
                            listAdapter.notifyItemChanged(quizRow);
                        }
                        listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2);
                        listAdapter.notifyItemChanged(emptyRow);
                    }
                } else {
                    if (quizOnly != 0) {
                        return;
                    }
                    listView.setItemAnimator(itemAnimator);
                    checked = quizPoll = !quizPoll;
                    int prevSolutionRow = solutionRow;
                    updateRows();
                    if (quizPoll) {
                        listAdapter.notifyItemRangeInserted(solutionRow, 2);
                    } else {
                        listAdapter.notifyItemRangeRemoved(prevSolutionRow, 2);
                    }
                    listAdapter.notifyItemChanged(emptyRow);
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
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertPollLayout.this, true, dy);
                if (dy != 0 && hintView != null) {
                    hintView.hide();
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(1);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(53)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(53));
                        }
                    }
                }
            }
        });

        hintView = new HintView(context, 4);
        hintView.setText(LocaleController.getString("PollTapToSelect", R.string.PollTapToSelect));
        hintView.setAlpha(0.0f);
        hintView.setVisibility(View.INVISIBLE);
        addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));

        checkDoneButton();
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    @Override
    void onResume() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    void onHideShowProgress(float progress) {
        parentAlert.doneItem.setAlpha((parentAlert.doneItem.isEnabled() ? 1.0f : 0.5f) * progress);
    }

    @Override
    void onMenuItemClick(int id) {
        if (id == done_button) {
            if (quizPoll && parentAlert.doneItem.getAlpha() != 1.0f) {
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
            TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
            poll.poll = new TLRPC.TL_poll();
            poll.poll.multiple_choice = multipleChoise;
            poll.poll.quiz = quizPoll;
            poll.poll.public_voters = !anonymousPoll;
            poll.poll.question = getFixedString(questionString).toString();
            SerializedData serializedData = new SerializedData(10);
            for (int a = 0; a < answers.length; a++) {
                if (TextUtils.isEmpty(getFixedString(answers[a]))) {
                    continue;
                }
                TLRPC.TL_pollAnswer answer = new TLRPC.TL_pollAnswer();
                answer.text = getFixedString(answers[a]).toString();
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
            if (chatActivity.isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(chatActivity.getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                    delegate.sendPoll(poll, params, notify, scheduleDate);
                    parentAlert.dismiss(true);
                });
            } else {
                delegate.sendPoll(poll, params, true, 0);
                parentAlert.dismiss(true);
            }
        }
    }

    @Override
    int getCurrentItemTop() {
        if (listView.getChildCount() <= 1) {
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(1);
        if (child == null) {
            return Integer.MAX_VALUE;
        }
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = (int) child.getY() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 1 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 1) {
            newOffset = top;
        }
        return newOffset + AndroidUtilities.dp(25);
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(17);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    int getListTopPadding() {
        return topPadding;
    }

    @Override
    void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
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
        if (topPadding != padding) {
            topPadding = padding;
            listView.setItemAnimator(null);
            listAdapter.notifyItemChanged(paddingRow);
        }
        ignoreLayout = false;
    }

    @Override
    int getButtonsHideOffset() {
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
    void scrollToTop() {
        listView.smoothScrollToPosition(1);
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
                if (!TextUtils.isEmpty(getFixedString(answers[a])) && answersChecks[a]) {
                    checksCount++;
                }
            }
        }
        int count = 0;
        if (!TextUtils.isEmpty(getFixedString(solutionString)) && solutionString.length() > MAX_SOLUTION_LENGTH) {
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
        if (count < 2 || quizPoll && checksCount < 1) {
            enabled = false;
        }
        if (!TextUtils.isEmpty(solutionString) || !TextUtils.isEmpty(questionString) || hasAnswers) {
            allowNesterScroll = false;
        } else {
            allowNesterScroll = true;
        }
        parentAlert.setAllowNestedScroll(allowNesterScroll);
        parentAlert.doneItem.setEnabled(quizPoll && checksCount == 0 || enabled);
        parentAlert.doneItem.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void updateRows() {
        rowCount = 0;
        paddingRow = rowCount++;

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
        TLRPC.Chat chat = ((ChatActivity) parentAlert.baseFragment).getCurrentChat();
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
        emptyRow = rowCount++;
    }

    @Override
    void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        try {
            parentAlert.actionBar.getTitleTextView().setBuildFullLayout(true);
        } catch (Exception ignore) {}
        if (quizOnly == 1) {
            parentAlert.actionBar.setTitle(LocaleController.getString("NewQuiz", R.string.NewQuiz));
        } else {
            parentAlert.actionBar.setTitle(LocaleController.getString("NewPoll", R.string.NewPoll));
        }
        parentAlert.doneItem.setVisibility(VISIBLE);
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    void onHidden() {
        parentAlert.doneItem.setVisibility(INVISIBLE);
    }

    @Override
    boolean onBackPressed() {
        if (!checkDiscard()) {
            return true;
        }
        return super.onBackPressed();
    }

    private boolean checkDiscard() {
        boolean allowDiscard = TextUtils.isEmpty(getFixedString(questionString));
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
            builder.setTitle(LocaleController.getString("CancelPollAlertTitle", R.string.CancelPollAlertTitle));
            builder.setMessage(LocaleController.getString("CancelPollAlertText", R.string.CancelPollAlertText));
            builder.setPositiveButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialogInterface, i) -> parentAlert.dismiss());
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
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
        if (index == questionRow) {
            max = MAX_QUESTION_LENGTH;
            left = MAX_QUESTION_LENGTH - (questionString != null ? questionString.length() : 0);
        } else if (index == solutionRow) {
            max = MAX_SOLUTION_LENGTH;
            left = MAX_SOLUTION_LENGTH - (solutionString != null ? solutionString.length() : 0);
        } else if (index >= answerStartRow && index < answerStartRow + answersCount) {
            index -= answerStartRow;
            max = MAX_ANSWER_LENGTH;
            left = MAX_ANSWER_LENGTH - (answers[index] != null ? answers[index].length() : 0);
        } else {
            return;
        }
        if (left <= max - max * 0.7f) {
            textCell.setText2(String.format("%d", left));
            SimpleTextView textView = textCell.getTextView2();
            String key = left < 0 ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText3;
            textView.setTextColor(getThemedColor(key));
            textView.setTag(key);
        } else {
            textCell.setText2("");
        }
    }

    private void addNewField() {
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
                        cell.getTextView().setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                        cell.setText(LocaleController.getString("PollQuestion", R.string.PollQuestion));
                    } else {
                        cell.getTextView().setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                        if (position == answerHeaderRow) {
                            if (quizOnly == 1) {
                                cell.setText(LocaleController.getString("QuizAnswers", R.string.QuizAnswers));
                            } else {
                                cell.setText(LocaleController.getString("AnswerOptions", R.string.AnswerOptions));
                            }
                        } else if (position == settingsHeaderRow) {
                            cell.setText(LocaleController.getString("Settings", R.string.Settings));
                        }
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    cell.setBackgroundDrawable(combinedDrawable);
                    if (position == solutionInfoRow) {
                        cell.setText(LocaleController.getString("AddAnExplanationInfo", R.string.AddAnExplanationInfo));
                    } else if (position == settingsSectionRow) {
                        if (quizOnly != 0) {
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
                    textCell.setColors(null, Theme.key_windowBackgroundWhiteBlueText4);
                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
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
                case 9: {
                    View view = (View) holder.itemView;
                    view.requestLayout();
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
            if (holder.getItemViewType() == 4) {
                PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                EditTextBoldCursor editText = editTextCell.getTextView();
                if (editText.isFocused()) {
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
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                    view = new TextCell(mContext);
                    break;
                case 4: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, null) {
                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
                        }
                    };
                    cell.createErrorTextView();
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
                            questionString = s.toString();
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(questionRow);
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
                    break;
                case 7: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, true, null) {
                        @Override
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
                        }

                        @Override
                        protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {
                            if (editText.isFocused() && editText.hasSelection()) {
                                Menu menu = actionMode.getMenu();
                                if (menu.findItem(android.R.id.copy) == null) {
                                    return;
                                }
                                ((ChatActivity) parentAlert.baseFragment).fillActionModeMenu(menu);
                            }
                        }
                    };
                    cell.createErrorTextView();
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
                            solutionString = s;
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(solutionRow);
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
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
                    break;
                }
                case 9: {
                    view = new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), topPadding);
                        }
                    };
                    break;
                }
                default: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, v -> {
                        if (v.getTag() != null) {
                            return;
                        }
                        v.setTag(1);
                        PollEditTextCell p = (PollEditTextCell) v.getParent();
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(p);
                        if (holder != null) {
                            int position = holder.getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                listView.setItemAnimator(itemAnimator);
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
                                } else if (editText.isFocused()) {
                                    AndroidUtilities.hideKeyboard(editText);
                                }
                                editText.clearFocus();
                                checkDoneButton();
                                updateRows();
                                listAdapter.notifyItemChanged(answerSectionRow);
                                listAdapter.notifyItemChanged(emptyRow);
                            }
                        }
                    }) {
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
                        protected void onFieldTouchUp(EditTextBoldCursor editText) {
                            parentAlert.makeFocusable(editText, true);
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
                    };
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
                                answers[index] = s.toString();
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
            } else if (position == emptyRow) {
                return 8;
            } else if (position == paddingRow) {
                return 9;
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
            String from = answers[idx1];
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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));

        return themeDescriptions;
    }
}
