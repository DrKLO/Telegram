package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PollCreateActivity extends BaseFragment {

    private ActionBarMenuItem doneItem;
    private AnimatorSet doneItemAnimation;
    private ContextProgressView progressView;
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ChatActivity parentFragment;

    private String[] answers = new String[10];
    private int answersCount = 1;
    private String questionString;

    private PollCreateActivityDelegate delegate;

    private int requestFieldFocusAtPosition = -1;

    private int questionHeaderRow;
    private int questionRow;
    private int questionSectionRow;
    private int answerHeaderRow;
    private int answerStartRow;
    private int addAnswerRow;
    private int answerSectionRow;
    private int rowCount;

    private static final int MAX_QUESTION_LENGTH = 255;
    private static final int MAX_ANSWER_LENGTH = 100;

    private static final int done_button = 1;

    public interface PollCreateActivityDelegate {
        void sendPoll(TLRPC.TL_messageMediaPoll poll, boolean notify, int scheduleDate);
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

    public PollCreateActivity(ChatActivity chatActivity) {
        super();
        parentFragment = chatActivity;
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
        actionBar.setTitle(LocaleController.getString("NewPoll", R.string.NewPoll));
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
                    TLRPC.TL_messageMediaPoll poll = new TLRPC.TL_messageMediaPoll();
                    poll.poll = new TLRPC.TL_poll();
                    poll.poll.question = getFixedString(questionString);
                    for (int a = 0; a < answers.length; a++) {
                        if (TextUtils.isEmpty(getFixedString(answers[a]))) {
                            continue;
                        }
                        TLRPC.TL_pollAnswer answer = new TLRPC.TL_pollAnswer();
                        answer.text = getFixedString(answers[a]);
                        answer.option = new byte[1];
                        answer.option[0] = (byte) (48 + poll.poll.answers.size());
                        poll.poll.answers.add(answer);
                    }
                    poll.results = new TLRPC.TL_pollResults();
                    if (parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> {
                            delegate.sendPoll(poll, notify, scheduleDate);
                            finishFragment();
                        });
                    } else {
                        delegate.sendPoll(poll, true, 0);
                        finishFragment();
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);
        doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            public void requestLayout() {
                super.requestLayout();
            }
        };
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == addAnswerRow) {
                addNewField();
            }
        });

        checkDoneButton();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.runOnUIThread(() -> {
                if (listView != null) {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(questionRow);
                    if (holder != null) {
                        PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                        EditTextBoldCursor editText = textCell.getTextView();
                        editText.requestFocus();
                        AndroidUtilities.showKeyboard(editText);
                    }
                }
            }, 100);
        }
    }

    private String getFixedString(String text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        text = AndroidUtilities.getTrimmedString(text).toString();
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n");
        }
        while (text.startsWith("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n");
        }
        return text;
    }

    private void checkDoneButton() {
        boolean enabled = true;
        if (TextUtils.isEmpty(getFixedString(questionString)) || questionString.length() > MAX_QUESTION_LENGTH) {
            enabled = false;
        } else {
            int count = 0;
            for (int a = 0; a < answers.length; a++) {
                if (!TextUtils.isEmpty(getFixedString(answers[a]))) {
                    if (answers[a].length() > MAX_ANSWER_LENGTH) {
                        count = 0;
                        break;
                    }
                    count++;
                }
            }
            if (count < 2) {
                enabled = false;
            }
        }
        doneItem.setEnabled(enabled);
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
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
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
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("CancelPollAlertTitle", R.string.CancelPollAlertTitle));
            builder.setMessage(LocaleController.getString("CancelPollAlertText", R.string.CancelPollAlertText));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> finishFragment());
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        }
        return allowDiscard;
    }

    public void setDelegate(PollCreateActivityDelegate pollCreateActivityDelegate) {
        delegate = pollCreateActivityDelegate;
    }

    private void showEditDoneProgress(final boolean show) {
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            doneItem.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f));
        } else {
            doneItem.getContentView().setVisibility(View.VISIBLE);
            doneItem.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 1.0f));
        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        doneItem.getContentView().setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
    }

    private void setTextLeft(View cell, int index) {
        if (cell instanceof HeaderCell) {
            HeaderCell headerCell = (HeaderCell) cell;
            if (index == -1) {
                int left = MAX_QUESTION_LENGTH - (questionString != null ? questionString.length() : 0);
                if (left <= MAX_QUESTION_LENGTH - MAX_QUESTION_LENGTH * 0.7f) {
                    headerCell.setText2(String.format("%d", left));
                    SimpleTextView textView = headerCell.getTextView2();
                    String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                    textView.setTextColor(Theme.getColor(key));
                    textView.setTag(key);
                } else {
                    headerCell.setText2("");
                }
            } else {
                headerCell.setText2("");
            }
        } else if (cell instanceof PollEditTextCell) {
            if (index >= 0) {
                PollEditTextCell textCell = (PollEditTextCell) cell;
                int left = MAX_ANSWER_LENGTH - (answers[index] != null ? answers[index].length() : 0);
                if (left <= MAX_ANSWER_LENGTH - MAX_ANSWER_LENGTH * 0.7f) {
                    textCell.setText2(String.format("%d", left));
                    SimpleTextView textView = textCell.getTextView2();
                    String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                    textView.setTextColor(Theme.getColor(key));
                    textView.setTag(key);
                } else {
                    textCell.setText2("");
                }
            }
        }
    }

    private void addNewField() {
        answersCount++;
        if (answersCount == answers.length) {
            listAdapter.notifyItemRemoved(addAnswerRow);
        }
        listAdapter.notifyItemInserted(addAnswerRow);
        updateRows();
        requestFieldFocusAtPosition = answerStartRow + answersCount - 1;
        listAdapter.notifyItemChanged(answerSectionRow);
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
                        cell.setText(LocaleController.getString("Question", R.string.Question));
                    } else if (position == answerHeaderRow) {
                        cell.setText(LocaleController.getString("PollOptions", R.string.PollOptions));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    if (10 - answersCount <= 0) {
                        cell.setText(LocaleController.getString("AddAnOptionInfoMax", R.string.AddAnOptionInfoMax));
                    } else {
                        cell.setText(LocaleController.formatString("AddAnOptionInfo", R.string.AddAnOptionInfo, LocaleController.formatPluralString("Option", 10 - answersCount)));
                    }
                    break;
                }
                case 3: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                    textCell.setText(LocaleController.getString("AddAnOption", R.string.AddAnOption), false);
                    break;
                }
                case 4: {
                    PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                    textCell.setTag(1);
                    textCell.setTextAndHint(questionString != null ? questionString : "", LocaleController.getString("QuestionHint", R.string.QuestionHint), false);
                    textCell.setTag(null);
                    break;
                }
                case 5: {
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
                    setTextLeft(holder.itemView, position - answerStartRow);
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 0 || viewType == 5) {
                setTextLeft(holder.itemView, holder.getAdapterPosition() == questionHeaderRow ? -1 : 0);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == addAnswerRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext, false, 21, 15, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, null);
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
                            questionString = s.toString();
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(questionHeaderRow);
                            if (holder != null) {
                                setTextLeft(holder.itemView, -1);
                            }
                            checkDoneButton();
                        }
                    });
                    view = cell;
                    break;
                }
                default: {
                    PollEditTextCell cell = new PollEditTextCell(mContext, v -> {
                        if (v.getTag() != null) {
                            return;
                        }
                        v.setTag(1);
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder((View) v.getParent());
                        if (holder != null) {
                            int position = holder.getAdapterPosition();
                            int index = position - answerStartRow;
                            listAdapter.notifyItemRemoved(holder.getAdapterPosition());
                            System.arraycopy(answers, index + 1, answers, index, answers.length - 1 - index);
                            answers[answers.length - 1] = null;
                            answersCount--;
                            if (answersCount == answers.length - 1) {
                                listAdapter.notifyItemInserted(answerStartRow + answers.length - 1);
                            }
                            holder = listView.findViewHolderForAdapterPosition(position - 1);
                            if (holder != null && holder.itemView instanceof PollEditTextCell) {
                                PollEditTextCell editTextCell = (PollEditTextCell) holder.itemView;
                                editTextCell.getTextView().requestFocus();
                            }
                            checkDoneButton();
                            updateRows();
                            listAdapter.notifyItemChanged(answerSectionRow);
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
                                answers[index] = s.toString();
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
            if (position == questionHeaderRow || position == answerHeaderRow) {
                return 0;
            } else if (position == questionSectionRow) {
                return 1;
            } else if (position == answerSectionRow) {
                return 2;
            } else if (position == addAnswerRow) {
                return 3;
            } else if (position == questionRow) {
                return 4;
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
            notifyItemMoved(fromIndex, toIndex);
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextSettingsCell.class, PollEditTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"deleteImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{PollEditTextCell.class}, new String[]{"deleteImageView"}, null, null, null, Theme.key_stickers_menuSelector),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{PollEditTextCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{PollEditTextCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText),
        };
    }
}
