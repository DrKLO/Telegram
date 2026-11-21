package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleActionBarDoneButton;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SectionsScrollView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;
import java.util.HashSet;

public class LinkEditActivity extends BaseFragment {

    public final static int CREATE_TYPE = 0;
    public final static int EDIT_TYPE = 1;
    private int type;

    private final long chatId;
    private SlideChooseView usesChooseView;
    private SlideChooseView timeChooseView;

    TLRPC.TL_chatInviteExported inviteToEdit;
    private TextCheckCell approveCell;
    private TextInfoPrivacyCell approveHintCell;
    private TextCheckCell subCell;
    private EditTextCell subEditPriceCell;
    private TextView subPriceView;
    private TextInfoPrivacyCell subInfoCell;
    private TextView timeEditText;
    private HeaderCell timeHeaderCell;
    private TextInfoPrivacyCell divider;
    private HeaderCell usesHeaderCell;
    private EditText usesEditText;
    private TextInfoPrivacyCell dividerUses;
    private FrameLayout buttonLayout;
    // private TextView buttonTextView;
    private TextSettingsCell revokeLink;
    private SectionsScrollView scrollView;
    private EditText nameEditText;
    private TextInfoPrivacyCell dividerName;
    private static final int done_button = 1;
    private SimpleActionBarDoneButton createTextView;
    private int shakeDp = -3;

    private boolean ignoreSet;
    private boolean finished;
    private boolean firstLayout = true;

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
    boolean scrollToStart;
    boolean scrollToEnd;
    int currentInviteDate;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (type == CREATE_TYPE) {
            simpleActionBar.setTitle(getString(R.string.NewLink));
        } else if (type == EDIT_TYPE) {
            simpleActionBar.setTitle(getString(R.string.EditLink));
        }
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                    AndroidUtilities.hideKeyboard(usesEditText);
                } else if (id == done_button) {
                    onCreateClicked(createTextView);
                }
            }
        });

        createTextView = simpleActionBar.createDoneButton(done_button, getString(type == CREATE_TYPE ? R.string.Create : R.string.Save));

        LinearLayout linearLayout = new SectionsScrollView.SectionsLinearLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                firstLayout = false;
            }
        };
        scrollView = new SectionsScrollView(context, linearLayout, resourceProvider);
        scrollView.setDrawBackground(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                measureKeyboardHeight();
                if (keyboardHeight != 0 && keyboardHeight < dp(20)) {
                    usesEditText.clearFocus();
                    nameEditText.clearFocus();
                }
                buttonLayout.setVisibility(keyboardHeight > dp(20) ? GONE : VISIBLE);
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
                } else if (scrollToStart) {
                    scrollToStart = false;
                    scrollView.smoothScrollTo(0, 0);
                }
            }
        };

        fragmentView = contentView;

        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(420);
        transition.setInterpolator(LayoutTransition.APPEARING, CubicBezierInterpolator.EASE_OUT_QUINT);
        transition.setInterpolator(LayoutTransition.CHANGE_APPEARING, CubicBezierInterpolator.EASE_OUT_QUINT);
        transition.setInterpolator(LayoutTransition.CHANGING, CubicBezierInterpolator.EASE_OUT_QUINT);
        transition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, CubicBezierInterpolator.EASE_OUT_QUINT);
        transition.setInterpolator(LayoutTransition.DISAPPEARING, CubicBezierInterpolator.EASE_OUT_QUINT);
        linearLayout.setLayoutTransition(transition);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(12), dp(4), dp(12), dp(79 + 12));
        scrollView.addView(linearLayout);
/*
        buttonTextView = new TextView(context);
        ScaleStateListAnimator.apply(buttonTextView, 0.02f, 1.5f);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());

        if (type == CREATE_TYPE) {
            buttonTextView.setText(getString(R.string.CreateLink));
        } else if (type == EDIT_TYPE) {
            buttonTextView.setText(getString(R.string.SaveLink));
        }
*/

        timeHeaderCell = new HeaderCell(context);
        timeHeaderCell.setText(getString(R.string.LimitByPeriod));
        linearLayout.addView(timeHeaderCell);
        timeChooseView = new SlideChooseView(context);
        linearLayout.addView(timeChooseView);
        timeEditText = new TextView(context);
        timeEditText.setPadding(dp(22), 0, dp(22), 0);
        timeEditText.setGravity(Gravity.CENTER_VERTICAL);
        timeEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        timeEditText.setHint(getString(R.string.TimeLimitHint));
        timeEditText.setOnClickListener(view -> AlertsCreator.createDatePickerDialog(context, getString(R.string.ExpireAfter), getString(R.string.SetTimeLimit), -1, (notify, scheduleDate, scheduleRepeatPeriod) -> chooseDate(scheduleDate)));

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

        divider = new TextInfoPrivacyCell(context, 12, resourceProvider);
        divider.setText(getString(R.string.TimeLimitHelp));
        linearLayout.addView(divider);

        usesHeaderCell = new HeaderCell(context);
        usesHeaderCell.setText(getString(R.string.LimitNumberOfUses));
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
        usesEditText.setPadding(dp(22), 0, dp(22), 0);
        usesEditText.setGravity(Gravity.CENTER_VERTICAL);
        usesEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        usesEditText.setHint(getString(R.string.UsesLimitHint));
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

        dividerUses = new TextInfoPrivacyCell(context, 12, resourceProvider);
        dividerUses.setText(getString(R.string.UsesLimitHelp));
        linearLayout.addView(dividerUses);

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);

        final boolean approveRestricted = ChatObject.isPublic(chatLocal) && !chatLocal.join_request && !chatLocal.join_to_send;

        approveCell = new TextCheckCell(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, 0, getWidth(), getHeight());
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        ThemeBind.backgroundColor(approveCell, Theme.key_windowBackgroundWhite);
        approveCell.setTag(Theme.key_windowBackgroundWhite);
        approveCell.setTextAndCheck(getString(R.string.ApproveNewMembers2), false, false);
        approveCell.setOnClickListener(view -> {
            if (approveRestricted) {
                return;
            }
            if (subCell != null && subCell.isChecked()) {
                AndroidUtilities.shakeViewSpring(subCell, shakeDp = -shakeDp);
                return;
            }

            TextCheckCell cell = (TextCheckCell) view;
            boolean newIsChecked = !cell.isChecked();
            cell.setChecked(newIsChecked);
            setUsesVisible(!newIsChecked);
            firstLayout = true;

            if (subCell != null) {
                if (cell.isChecked()) {
                    subCell.setChecked(false);
                    subCell.setCheckBoxIcon(R.drawable.permission_locked);
                    subEditPriceCell.setVisibility(View.GONE);
                } else if (inviteToEdit == null) {
                    subCell.setCheckBoxIcon(0);
                }
            }
        });
        linearLayout.addView(approveCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
        approveHintCell = new TextInfoPrivacyCell(context, 12, resourceProvider);
        if (approveRestricted) {
            approveCell.setCheckBoxIcon(R.drawable.permission_locked);
            approveHintCell.setText(getString(R.string.ApproveNewMembersUnavailablePublicGroup));
        } else {
            approveHintCell.setText(getString(R.string.ApproveNewMembersDescription2));
        }
        linearLayout.addView(approveHintCell);

        if (chatLocal == null || chatLocal.username == null) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chatId);
            if (inviteToEdit == null && ChatObject.isChannelAndNotMegaGroup(MessagesController.getInstance(currentAccount).getChat(chatId)) && chatFull != null && chatFull.paid_media_allowed || inviteToEdit != null && inviteToEdit.subscription_pricing != null) {
                subCell = new TextCheckCell(context);
                ThemeBind.backgroundColor(subCell, Theme.key_windowBackgroundWhite);
                subCell.setDrawCheckRipple(true);
                subCell.setTextAndCheck(getString(R.string.RequireMonthlyFee), false, true);
                if (inviteToEdit != null) {
                    subCell.setCheckBoxIcon(R.drawable.permission_locked);
                    subCell.setEnabled(false);
                }
                final Runnable[] keyboardUpdate = new Runnable[1];
                subCell.setOnClickListener(view -> {
                    if (inviteToEdit != null) {
                        return;
                    }
                    if (approveCell.isChecked()) {
                        AndroidUtilities.shakeViewSpring(approveCell, shakeDp = -shakeDp);
                        return;
                    }

                    TextCheckCell cell = (TextCheckCell) view;
                    cell.setChecked(!cell.isChecked());
                    subEditPriceCell.setVisibility(cell.isChecked() ? View.VISIBLE : View.GONE);
                    AndroidUtilities.cancelRunOnUIThread(keyboardUpdate[0]);
                    if (cell.isChecked()) {
                        approveCell.setChecked(false);
                        approveCell.setCheckBoxIcon(R.drawable.permission_locked);
                        approveHintCell.setText(getString(R.string.ApproveNewMembersDescriptionFrozen));
                        AndroidUtilities.runOnUIThread(keyboardUpdate[0] = () -> {
                            subEditPriceCell.editText.requestFocus();
                            AndroidUtilities.showKeyboard(subEditPriceCell.editText);
                        }, 60);
                    } else {
                        approveCell.setCheckBoxIcon(0);
                        approveHintCell.setText(getString(R.string.ApproveNewMembersDescription2));
                        AndroidUtilities.runOnUIThread(keyboardUpdate[0] = () -> {
                            subEditPriceCell.editText.clearFocus();
                            AndroidUtilities.hideKeyboard(subEditPriceCell.editText);
                        });
                    }
                });
                linearLayout.addView(subCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

                subPriceView = new TextView(context);
                subPriceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                ThemeBind.textColor(subPriceView, Theme.key_windowBackgroundWhiteGrayText3);
                subEditPriceCell = new EditTextCell(context, getString(getConnectionsManager().isTestBackend() ? R.string.RequireMonthlyFeePriceHintTest5Minutes : R.string.RequireMonthlyFeePriceHint), false, false, -1, resourceProvider) {
                    private boolean ignoreTextChanged;
                    @Override
                    protected void onTextChanged(CharSequence newText) {
                        super.onTextChanged(newText);
                        if (ignoreTextChanged) return;
                        if (TextUtils.isEmpty(newText)) {
                            subPriceView.setText("");
                        } else {
                            try {
                                long stars = Long.parseLong(newText.toString());
                                if (stars > getMessagesController().starsSubscriptionAmountMax) {
                                    ignoreTextChanged = true;
                                    stars = getMessagesController().starsSubscriptionAmountMax;
                                    setText(Long.toString(stars));
                                    ignoreTextChanged = false;
                                }
                                subPriceView.setText(formatString(
                                        getConnectionsManager().isTestBackend() ? R.string.RequireMonthlyFeePriceTest5Minutes : R.string.RequireMonthlyFeePrice,
                                        BillingController.getInstance().formatCurrency((long) (stars / 1000.0 * MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000), "USD")
                                ));
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                };
                subEditPriceCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                subEditPriceCell.editText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
                ThemeBind.backgroundColor(subEditPriceCell, Theme.key_windowBackgroundWhite, getResourceProvider());
                subEditPriceCell.hideKeyboardOnEnter();
                subEditPriceCell.addView(subPriceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 19, 0));

                ImageView star = subEditPriceCell.setLeftDrawable(getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate());
                star.setScaleX(.83f);
                star.setScaleY(.83f);
                star.setTranslationY(dp(-1));
                star.setTranslationX(dp(1));
                linearLayout.addView(subEditPriceCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                subEditPriceCell.setVisibility(View.GONE);

                subInfoCell = new TextInfoPrivacyCell(context, 12, resourceProvider);
                if (inviteToEdit != null) {
                    subInfoCell.setText(getString(R.string.RequireMonthlyFeeInfoFrozen));
                } else {
                    subInfoCell.setText(AndroidUtilities.withLearnMore(getString(R.string.RequireMonthlyFeeInfo), () -> {
                        Browser.openUrl(getContext(), getString(R.string.RequireMonthlyFeeInfoLink));
                    }));
                }
                linearLayout.addView(subInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        nameEditText = new EditText(context) {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    setCursorVisible(true);
                }
                return super.onTouchEvent(event);
            }
        };
        nameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                Emoji.replaceEmoji(s, nameEditText.getPaint().getFontMetricsInt(), false);
            }
        });
        nameEditText.setCursorVisible(false);
        nameEditText.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(32) });
        nameEditText.setGravity(Gravity.CENTER_VERTICAL);
        nameEditText.setHint(getString(R.string.LinkNameHint));
        ThemeBind.hintTextColor(nameEditText, Theme.key_windowBackgroundWhiteGrayText);
        nameEditText.setLines(1);
        nameEditText.setPadding(dp(22), 0, dp(22), 0);
        nameEditText.setSingleLine();
        ThemeBind.textColor(nameEditText, Theme.key_windowBackgroundWhiteBlackText);
        nameEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linearLayout.addView(nameEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        dividerName = new TextInfoPrivacyCell(context, 12, resourceProvider);
        dividerName.setText(getString(R.string.LinkNameHelp));
        linearLayout.addView(dividerName);

        if (type == EDIT_TYPE) {
            revokeLink = new TextSettingsCell(context);
            ThemeBind.backgroundColor(revokeLink, Theme.key_windowBackgroundWhite);
            revokeLink.setText(getString(R.string.RevokeLink), false);
            ThemeBind.textColor(revokeLink.getTextView(), Theme.key_text_RedRegular);
            revokeLink.setOnClickListener(view -> {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                builder2.setMessage(getString(R.string.RevokeAlert));
                builder2.setTitle(getString(R.string.RevokeLink));
                builder2.setPositiveButton(getString(R.string.RevokeButton), (dialogInterface2, i2) -> {
                    callback.revokeLink(inviteToEdit);
                    finishFragment();
                });
                builder2.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(builder2.create());

            });
            linearLayout.addView(revokeLink);
        }

        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buttonLayout = new FrameLayout(context);
        ThemeBind.backgroundColor(buttonLayout, Theme.key_windowBackgroundGray, getResourceProvider());
        new KeyboardNotifier(contentView, keyboardHeight -> {
//            buttonLayout.setVisibility((keyboardHeight > dp(40)) ? View.GONE : View.VISIBLE);
        });
        //buttonLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 16, 15, 16, 16));
        contentView.addView(buttonLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        ThemeBind.backgroundColor(timeHeaderCell, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(timeChooseView, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(timeEditText, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(usesHeaderCell, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(usesChooseView, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(usesEditText, Theme.key_windowBackgroundWhite);
        ThemeBind.backgroundColor(nameEditText, Theme.key_windowBackgroundWhite);

        //buttonTextView.setOnClickListener(this::onCreateClicked);
        //buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        //buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        ThemeBind.textColor(usesEditText, Theme.key_windowBackgroundWhiteBlackText);
        ThemeBind.hintTextColor(usesEditText, Theme.key_windowBackgroundWhiteGrayText);

        ThemeBind.textColor(timeEditText, Theme.key_windowBackgroundWhiteBlackText);
        ThemeBind.hintTextColor(timeEditText, Theme.key_windowBackgroundWhiteGrayText);

        usesEditText.setCursorVisible(false);
        setInviteToEdit(inviteToEdit);

        contentView.setClipChildren(false);
        scrollView.setClipChildren(false);
        linearLayout.setClipChildren(false);

        return contentView;
    }

    private void onCreateClicked(View view) {
        if (loading) {
            return;
        }

        int timeIndex = timeChooseView.getSelectedIndex();
        if (timeIndex < dispalyedDates.size() && dispalyedDates.get(timeIndex) < 0) {
            AndroidUtilities.shakeView(timeEditText);
            Vibrator vibrator = (Vibrator) timeEditText.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(200);
            }
            return;
        }

        long stars = 0;
        if (subCell != null && subCell.isChecked()) {
            try {
                stars = Long.parseLong(subEditPriceCell.editText.getText().toString());
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (type == CREATE_TYPE) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            loading = true;
            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
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

            req.request_needed = approveCell != null && approveCell.isChecked();
            if (req.request_needed) {
                req.usage_limit = 0;
            }

            req.title = nameEditText.getText().toString();
            if (!TextUtils.isEmpty(req.title)) {
                req.flags |= 16;
            }

            if (stars > 0) {
                req.flags |= 32;
                req.subscription_pricing = new TL_stars.TL_starsSubscriptionPricing();
                req.subscription_pricing.period = getConnectionsManager().isTestBackend() ? StarsController.PERIOD_5MINUTES : StarsController.PERIOD_MONTHLY;
                req.subscription_pricing.amount = stars;
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

            if (inviteToEdit.request_needed != (approveCell != null && approveCell.isChecked())) {
                req.flags |= 8;
                req.request_needed = (approveCell != null && approveCell.isChecked());
                if (req.request_needed) {
                    req.flags |= 2;
                    req.usage_limit = 0;
                }
                edited = true;
            }

            String newTitle = nameEditText.getText().toString();
            if (!TextUtils.equals(inviteToEdit.title, newTitle)) {
                req.title = newTitle;
                req.flags |= 16;
                edited = true;
            }

            if (edited) {
                loading = true;
                progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(500);
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    if (error == null) {
                        if (response instanceof TLRPC.TL_messages_exportedChatInvite) {
                            inviteToEdit = (TLRPC.TL_chatInviteExported) ((TLRPC.TL_messages_exportedChatInvite) response).invite;
                        }
                        if (callback != null) {
                            callback.onLinkEdited(inviteToEdit, response);
                        }
                        finishFragment();
                    } else {
                        AlertsCreator.showSimpleAlert(LinkEditActivity.this, error.text);
                    }
                }));
            } else {
                finishFragment();
            }
        }
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
                options[i] = getString(R.string.NoLimit);
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
                options[i] = getString(R.string.NoLimit);
            } else {
                if (dispalyedDates.get(i) == defaultDates[0]) {
                    options[i] = LocaleController.formatPluralString("Hours", 1);
                } else if (dispalyedDates.get(i) == defaultDates[1]) {
                    options[i] = LocaleController.formatPluralString("Days", 1);
                } else if (dispalyedDates.get(i) == defaultDates[2]) {
                    options[i] = LocaleController.formatPluralString("Weeks", 1);
                } else {
                    if (selectedDate < 86400L) {
                        options[i] = getString(R.string.MessageScheduleToday);
                    } else if (selectedDate < 364 * 86400L) {
                        options[i] = LocaleController.getInstance().getFormatterScheduleDay().format(originDate * 1000L);
                    } else {
                        options[i] = LocaleController.getInstance().getFormatterYear().format(originDate * 1000L);
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
        String[] options = new String[]{ LocaleController.formatPluralString("Hours", 1), LocaleController.formatPluralString("Days", 1), LocaleController.formatPluralString("Weeks", 1), getString(R.string.NoLimit) };
        timeChooseView.setOptions(options.length - 1, options);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void resetUses() {
        dispalyedUses.clear();
        for (int i = 0; i < defaultUses.length; i++) {
            dispalyedUses.add(defaultUses[i]);
        }
        String[] options = new String[]{ "1", "10", "100", getString(R.string.NoLimit) };
        usesChooseView.setOptions(options.length - 1, options);
    }

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
            if (approveCell != null) {
                ThemeBind.backgroundColor(approveCell, Theme.key_windowBackgroundWhite);
                approveCell.setChecked(invite.request_needed);
            }
            setUsesVisible(!invite.request_needed);
            if (!TextUtils.isEmpty(invite.title)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(invite.title);
                Emoji.replaceEmoji(builder, nameEditText.getPaint().getFontMetricsInt(), false);
                nameEditText.setText(builder);
            }

            if (subCell != null) {
                subCell.setChecked(invite.subscription_pricing != null);
            }
            if (invite.subscription_pricing != null) {
                if (approveCell != null) {
                    approveCell.setChecked(false);
                    approveCell.setCheckBoxIcon(R.drawable.permission_locked);
                }
                if (approveHintCell != null) {
                    approveHintCell.setText(getString(R.string.ApproveNewMembersDescriptionFrozen));
                }
            }
            if (subEditPriceCell != null) {
                subEditPriceCell.setVisibility(invite.subscription_pricing != null ? View.VISIBLE : View.GONE);
                subEditPriceCell.setText(Long.toString(invite.subscription_pricing.amount));
                subEditPriceCell.editText.setClickable(false);
                subEditPriceCell.editText.setFocusable(false);
                subEditPriceCell.editText.setFocusableInTouchMode(false);
                subEditPriceCell.editText.setLongClickable(false);
            }
        }
    }

    private void setUsesVisible(boolean isVisible) {
        usesHeaderCell.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        usesChooseView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        usesEditText.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        dividerUses.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    public interface Callback {
        void onLinkCreated(TLObject response);

        void onLinkEdited(TLRPC.TL_chatInviteExported inviteToEdit, TLObject response);

        void onLinkRemoved(TLRPC.TL_chatInviteExported inviteFinal);

        void revokeLink(TLRPC.TL_chatInviteExported inviteFinal);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected SectionsScrollView getScrollViewForSimpleGlass() {
        return scrollView;
    }

    @Override
    public void finishFragment() {
        scrollView.getLayoutParams().height = scrollView.getHeight();
        finished = true;
        super.finishFragment();

    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = this::descriptionDelegateThemeColors;
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();





        return themeDescriptions;
    }



    private void descriptionDelegateThemeColors() {
        if (dividerUses != null) {
            Context context = dividerUses.getContext();
            // buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

            ThemeBind.textColor(usesEditText, Theme.key_windowBackgroundWhiteBlackText);
            ThemeBind.hintTextColor(usesEditText, Theme.key_windowBackgroundWhiteGrayText);

            ThemeBind.textColor(timeEditText, Theme.key_windowBackgroundWhiteBlackText);
            ThemeBind.hintTextColor(timeEditText, Theme.key_windowBackgroundWhiteGrayText);
            // buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            if (revokeLink != null) {
                ThemeBind.textColor(revokeLink.getTextView(), Theme.key_text_RedRegular);
            }

            ThemeBind.textColor(nameEditText, Theme.key_windowBackgroundWhiteBlackText);
            ThemeBind.hintTextColor(nameEditText, Theme.key_windowBackgroundWhiteGrayText);
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_featuredStickers_addButtonPressed);
        keys.add(Theme.key_featuredStickers_addButton);
        keys.add(Theme.key_actionBarDefaultSelector);
        keys.add(Theme.key_actionBarDefaultTitle);
        keys.add(Theme.key_actionBarDefaultIcon);
        keys.add(Theme.key_actionBarDefault);
        keys.add(Theme.key_windowBackgroundWhiteGrayText4);
        keys.add(Theme.key_windowBackgroundWhiteBlueHeader);
    }
}
