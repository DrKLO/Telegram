package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;

public class LinkEditActivity extends BaseFragment {

    public final static int CREATE_TYPE = 0;
    public final static int EDIT_TYPE = 1;
    private int type;

    private final long chatId;
    private SlideChooseView usesChooseView;
    private SlideChooseView timeChooseView;

    TLRPC.TL_chatInviteExported inviteToEdit;
    private TextView timeEditText;
    private HeaderCell timeHeaderCell;
    private TextInfoPrivacyCell divider;
    private HeaderCell usesHeaderCell;
    private EditText usesEditText;
    private TextInfoPrivacyCell dividerUses;
    private TextView buttonTextView;
    private TextSettingsCell revokeLink;
    private boolean ignoreSet;
    private ScrollView scrollView;
    private boolean finished;

    public LinkEditActivity(int type, long chatId) {
        this.type = type;
        this.chatId = chatId;
    }

    private ArrayList<Integer> dispalyedDates = new ArrayList<>();
    private final int[] defaultDates = new int[]{3600, 3600 * 24, 3600 * 24 * 7};
    private ArrayList<Integer> dispalyedUses = new ArrayList<>();
    private final int[] defaultUses = new int[]{1, 10, 100};

    private Callback callback;

    AlertDialog progressDialog;
    boolean loading;
    boolean scrollToEnd;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (type == CREATE_TYPE) {
            actionBar.setTitle(LocaleController.getString("NewLink", R.string.NewLink));
        } else if (type == EDIT_TYPE) {
            actionBar.setTitle(LocaleController.getString("EditLink", R.string.EditLink));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    AndroidUtilities.hideKeyboard(usesEditText);
                }
            }
        });

        scrollView = new ScrollView(context);
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {

            int oldKeyboardHeight;


            @Override
            protected AdjustPanLayoutHelper createAdjustPanLayoutHelper() {
                AdjustPanLayoutHelper panLayoutHelper = new AdjustPanLayoutHelper(this) {

                    @Override
                    protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                        super.onTransitionStart(keyboardVisible, contentHeight);
                        scrollView.getLayoutParams().height = contentHeight;
                    }

                    @Override
                    protected void onTransitionEnd() {
                        super.onTransitionEnd();
                        scrollView.getLayoutParams().height = LinearLayout.LayoutParams.MATCH_PARENT;
                        scrollView.requestLayout();
                    }

                    @Override
                    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                        super.onPanTranslationUpdate(y, progress, keyboardVisible);
                        setTranslationY(0);
                    }

                    @Override
                    protected boolean heightAnimationEnabled() {
                        return !finished;
                    }
                };
                panLayoutHelper.setCheckHierarchyHeight(true);
                return panLayoutHelper;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                adjustPanLayoutHelper.onAttach();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                measureKeyboardHeight();
                if (oldKeyboardHeight != keyboardHeight && keyboardHeight > AndroidUtilities.dp(20)) {
                    scrollToEnd = true;
                    invalidate();
                }

                if (keyboardHeight < AndroidUtilities.dp(20)) {
                    usesEditText.clearFocus();
                }

                oldKeyboardHeight = keyboardHeight;
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int scrollY = scrollView.getScrollY();
                super.onLayout(changed, l, t, r, b);

                if (scrollY != scrollView.getScrollY() && !scrollToEnd) {
                    scrollView.setTranslationY(scrollView.getScrollY() - scrollY);
                    scrollView.animate().cancel();
                    scrollView.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (scrollToEnd) {
                    scrollToEnd = false;
                    scrollView.smoothScrollTo(0, Math.max(0, scrollView.getChildAt(0).getMeasuredHeight() - scrollView.getMeasuredHeight()));
                }
            }
        };

        fragmentView = contentView;

        LinearLayout linearLayout = new LinearLayout(context) {

            boolean firstLayout = true;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int elementsHeight = 0;
                int h = MeasureSpec.getSize(heightMeasureSpec);
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child != buttonTextView) {
                        elementsHeight += child.getMeasuredHeight();
                    }
                }

                int topMargin;
                int buttonH = AndroidUtilities.dp(48) + AndroidUtilities.dp(24) + AndroidUtilities.dp(16);
                if (elementsHeight >= h - buttonH) {
                    topMargin = AndroidUtilities.dp(24);
                } else {
                    topMargin = AndroidUtilities.dp(24) + (h - buttonH) - elementsHeight;
                }

                if (((LayoutParams) buttonTextView.getLayoutParams()).topMargin != topMargin) {
                    int oldMargin = ((LayoutParams) buttonTextView.getLayoutParams()).topMargin;
                    ((LayoutParams) buttonTextView.getLayoutParams()).topMargin = topMargin;
                    if (!firstLayout) {
                        buttonTextView.setTranslationY(oldMargin - topMargin);
                        buttonTextView.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                firstLayout = false;
            }
        };
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout);

        buttonTextView = new TextView(context);

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        if (type == CREATE_TYPE) {
            buttonTextView.setText(LocaleController.getString("CreateLink", R.string.CreateLink));
        } else if (type == EDIT_TYPE) {
            buttonTextView.setText(LocaleController.getString("SaveLink", R.string.SaveLink));
        }

        timeHeaderCell = new HeaderCell(context);
        timeHeaderCell.setText(LocaleController.getString("LimitByPeriod", R.string.LimitByPeriod));
        linearLayout.addView(timeHeaderCell);
        timeChooseView = new SlideChooseView(context);
        linearLayout.addView(timeChooseView);
        timeEditText = new TextView(context);
        timeEditText.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        timeEditText.setGravity(Gravity.CENTER_VERTICAL);
        timeEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        timeEditText.setHint(LocaleController.getString("TimeLimitHint", R.string.TimeLimitHint));
        timeEditText.setOnClickListener(view -> AlertsCreator.createDatePickerDialog(context, -1, (notify, scheduleDate) -> chooseDate(scheduleDate)));

        timeChooseView.setCallback(index -> {
            if (index < dispalyedDates.size()) {
                long date = dispalyedDates.get(index) + getConnectionsManager().getCurrentTime();
                timeEditText.setText(LocaleController.formatDateAudio(date, false));
            } else {
                timeEditText.setText("");
            }
        });
        resetDates();
        linearLayout.addView(timeEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        divider = new TextInfoPrivacyCell(context);
        divider.setText(LocaleController.getString("TimeLimitHelp", R.string.TimeLimitHelp));
        linearLayout.addView(divider);

        usesHeaderCell = new HeaderCell(context);
        usesHeaderCell.setText(LocaleController.getString("LimitNumberOfUses", R.string.LimitNumberOfUses));
        linearLayout.addView(usesHeaderCell);
        usesChooseView = new SlideChooseView(context);
        usesChooseView.setCallback(index -> {
            usesEditText.clearFocus();
            ignoreSet = true;
            if (index < dispalyedUses.size()) {
                usesEditText.setText(dispalyedUses.get(index).toString());
            } else {
                usesEditText.setText("");
            }
            ignoreSet = false;
        });
        resetUses();
        linearLayout.addView(usesChooseView);

        usesEditText = new EditText(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    setCursorVisible(true);
                }
                return super.onTouchEvent(event);
            }
        };
        usesEditText.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        usesEditText.setGravity(Gravity.CENTER_VERTICAL);
        usesEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        usesEditText.setHint(LocaleController.getString("UsesLimitHint", R.string.UsesLimitHint));
        usesEditText.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        usesEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        usesEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (ignoreSet) {
                    return;
                }
                if (editable.toString().equals("0")) {
                    usesEditText.setText("");
                    return;
                }
                int customUses;
                try {
                    customUses = Integer.parseInt(editable.toString());
                } catch (NumberFormatException exception) {
                    resetUses();
                    return;
                }
                if (customUses > 100000) {
                    resetUses();
                } else {
                    chooseUses(customUses);
                }
            }
        });
        linearLayout.addView(usesEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        dividerUses = new TextInfoPrivacyCell(context);
        dividerUses.setText(LocaleController.getString("UsesLimitHelp", R.string.UsesLimitHelp));

        linearLayout.addView(dividerUses);

        if (type == EDIT_TYPE) {
            revokeLink = new TextSettingsCell(context);
            revokeLink.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            revokeLink.setText(LocaleController.getString("RevokeLink", R.string.RevokeLink), false);
            revokeLink.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
            revokeLink.setOnClickListener(view -> {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                builder2.setMessage(LocaleController.getString("RevokeAlert", R.string.RevokeAlert));
                builder2.setTitle(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                builder2.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface2, i2) -> {
                    callback.revokeLink(inviteToEdit);
                    finishFragment();
                });
                builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder2.create());

            });
            linearLayout.addView(revokeLink);
        }

        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16));

        timeHeaderCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        timeChooseView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        timeEditText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        usesHeaderCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        usesChooseView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        usesEditText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        buttonTextView.setOnClickListener(view -> {
            if (loading) {
                return;
            }

            int timeIndex = timeChooseView.getSelectedIndex();
            if (timeIndex < dispalyedDates.size() && dispalyedDates.get(timeIndex) < 0) {
                AndroidUtilities.shakeView(timeEditText, 2, 0);
                Vibrator vibrator = (Vibrator) timeEditText.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
                return;
            }

            if (type == CREATE_TYPE) {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
                loading = true;
                progressDialog = new AlertDialog(context, 3);
                progressDialog.showDelayed(500);
                TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
                req.peer = getMessagesController().getInputPeer(-chatId);
                req.legacy_revoke_permanent = false;

                int i = timeChooseView.getSelectedIndex();
                req.flags |= 1;
                if (i < dispalyedDates.size()) {
                    req.expire_date = dispalyedDates.get(i) + getConnectionsManager().getCurrentTime();
                } else {
                    req.expire_date = 0;
                }

                i = usesChooseView.getSelectedIndex();
                req.flags |= 2;
                if (i < dispalyedUses.size()) {
                    req.usage_limit = dispalyedUses.get(i);
                } else {
                    req.usage_limit = 0;
                }

                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    if (error == null) {
                        if (callback != null) {
                            callback.onLinkCreated(response);
                        }
                        finishFragment();
                    } else {
                        AlertsCreator.showSimpleAlert(LinkEditActivity.this, error.text);
                    }
                }));
            } else if (type == EDIT_TYPE) {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
                loading = true;
                progressDialog = new AlertDialog(context, 3);
                progressDialog.showDelayed(500);

                TLRPC.TL_messages_editExportedChatInvite req = new TLRPC.TL_messages_editExportedChatInvite();
                req.link = inviteToEdit.link;
                req.revoked = false;
                req.peer = getMessagesController().getInputPeer(-chatId);

                boolean edited = false;

                int i = timeChooseView.getSelectedIndex();
                if (i < dispalyedDates.size()) {
                    if (currentInviteDate != dispalyedDates.get(i)) {
                        req.flags |= 1;
                        req.expire_date = dispalyedDates.get(i) + getConnectionsManager().getCurrentTime();
                        edited = true;
                    }
                } else {
                    if (currentInviteDate != 0) {
                        req.flags |= 1;
                        req.expire_date = 0;
                        edited = true;
                    }
                }

                i = usesChooseView.getSelectedIndex();

                if (i < dispalyedUses.size()) {
                    int newLimit = dispalyedUses.get(i);
                    if (inviteToEdit.usage_limit != newLimit) {
                        req.flags |= 2;
                        req.usage_limit = newLimit;
                        edited = true;
                    }
                } else {
                    if (inviteToEdit.usage_limit != 0) {
                        req.flags |= 2;
                        req.usage_limit = 0;
                        edited = true;
                    }
                }

                if (edited) {
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        loading = false;
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                        }
                        if (error == null) {
                            if (callback != null) {
                                callback.onLinkEdited(inviteToEdit, response);
                            }
                            finishFragment();
                        } else {
                            AlertsCreator.showSimpleAlert(LinkEditActivity.this, error.text);
                        }
                    }));
                }
            }
        });
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));

        dividerUses.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        divider.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        usesEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        usesEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        timeEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        timeEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        usesEditText.setCursorVisible(false);
        setInviteToEdit(inviteToEdit);

        contentView.setClipChildren(false);
        scrollView.setClipChildren(false);
        linearLayout.setClipChildren(false);

        return contentView;
    }

    private void chooseUses(int customUses) {
        int position = 0;
        boolean added = false;
        dispalyedUses.clear();
        for (int i = 0; i < defaultUses.length; i++) {
            if (!added && customUses <= defaultUses[i]) {
                if (customUses != defaultUses[i]) {
                    dispalyedUses.add(customUses);

                }
                position = i;
                added = true;
            }
            dispalyedUses.add(defaultUses[i]);
        }
        if (!added) {
            dispalyedUses.add(customUses);
            position = defaultUses.length;
        }
        String[] options = new String[dispalyedUses.size() + 1];
        for (int i = 0; i < options.length; i++) {
            if (i == options.length - 1) {
                options[i] = LocaleController.getString("NoLimit", R.string.NoLimit);
            } else {
                options[i] = dispalyedUses.get(i).toString();
            }
        }
        usesChooseView.setOptions(position, options);
    }

    private void chooseDate(int selectedDate) {
        timeEditText.setText(LocaleController.formatDateAudio(selectedDate, false));

        int originDate = selectedDate;
        selectedDate -= getConnectionsManager().getCurrentTime();

        int position = 0;
        boolean added = false;
        dispalyedDates.clear();
        for (int i = 0; i < defaultDates.length; i++) {
            if (!added && selectedDate < defaultDates[i]) {
                dispalyedDates.add(selectedDate);
                position = i;
                added = true;
            }
            dispalyedDates.add(defaultDates[i]);
        }
        if (!added) {
            dispalyedDates.add(selectedDate);
            position = defaultDates.length;
        }
        String[] options = new String[dispalyedDates.size() + 1];
        for (int i = 0; i < options.length; i++) {
            if (i == options.length - 1) {
                options[i] = LocaleController.getString("NoLimit", R.string.NoLimit);
            } else {
                if (dispalyedDates.get(i) == defaultDates[0]) {
                    options[i] = LocaleController.formatPluralString("Hours", 1);
                } else if (dispalyedDates.get(i) == defaultDates[1]) {
                    options[i] = LocaleController.formatPluralString("Days", 1);
                } else if (dispalyedDates.get(i) == defaultDates[2]) {
                    options[i] = LocaleController.formatPluralString("Weeks", 1);
                } else {
                    if (selectedDate < 86400L) {
                        options[i] = LocaleController.getString("MessageScheduleToday", R.string.MessageScheduleToday);
                    } else if (selectedDate < 364 * 86400L) {
                        options[i] = LocaleController.getInstance().formatterScheduleDay.format(originDate * 1000L);
                    } else {
                        options[i] = LocaleController.getInstance().formatterYear.format(originDate * 1000L);
                    }
                }
            }
        }
        timeChooseView.setOptions(position, options);
    }

    private void resetDates() {
        dispalyedDates.clear();
        for (int i = 0; i < defaultDates.length; i++) {
            dispalyedDates.add(defaultDates[i]);
        }
        timeChooseView.setOptions(4, LocaleController.formatPluralString("Hours", 1), LocaleController.formatPluralString("Days", 1), LocaleController.formatPluralString("Weeks", 1), LocaleController.getString("NoLimit", R.string.NoLimit));
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void resetUses() {
        dispalyedUses.clear();
        for (int i = 0; i < defaultUses.length; i++) {
            dispalyedUses.add(defaultUses[i]);
        }
        usesChooseView.setOptions(4, "1", "10", "100", LocaleController.getString("NoLimit", R.string.NoLimit));
    }

    int currentInviteDate;

    public void setInviteToEdit(TLRPC.TL_chatInviteExported invite) {
        inviteToEdit = invite;
        if (fragmentView != null && invite != null) {
            if (invite.expire_date > 0) {
                chooseDate(invite.expire_date);
                currentInviteDate = dispalyedDates.get(timeChooseView.getSelectedIndex());
            } else {
                currentInviteDate = 0;
            }
            if (invite.usage_limit > 0) {
                chooseUses(invite.usage_limit);
                usesEditText.setText(Integer.toString(invite.usage_limit));
            }
        }
    }

    public interface Callback {
        void onLinkCreated(TLObject response);

        void onLinkEdited(TLRPC.TL_chatInviteExported inviteToEdit, TLObject response);

        void onLinkRemoved(TLRPC.TL_chatInviteExported inviteFinal);

        void revokeLink(TLRPC.TL_chatInviteExported inviteFinal);
    }

    @Override
    public void finishFragment() {
        scrollView.getLayoutParams().height = scrollView.getHeight();
        finished = true;
        super.finishFragment();

    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = () -> {
            if (dividerUses != null) {
                Context context = dividerUses.getContext();
                dividerUses.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                divider.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

                usesEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                usesEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

                timeEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                timeEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                if (revokeLink != null) {
                    revokeLink.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
                }
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(timeHeaderCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(usesHeaderCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(timeHeaderCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(usesHeaderCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(timeChooseView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(usesChooseView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(timeEditText, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(usesEditText, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(revokeLink, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(divider, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(dividerUses, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_addButtonPressed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_featuredStickers_buttonText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteRedText5));

        return themeDescriptions;
    }


}
