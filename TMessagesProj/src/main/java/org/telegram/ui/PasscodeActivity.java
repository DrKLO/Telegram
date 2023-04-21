/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class PasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public final static int TYPE_MANAGE_CODE_SETTINGS = 0,
            TYPE_SETUP_CODE = 1,
            TYPE_ENTER_CODE_TO_MANAGE_SETTINGS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TYPE_MANAGE_CODE_SETTINGS,
            TYPE_SETUP_CODE,
            TYPE_ENTER_CODE_TO_MANAGE_SETTINGS
    })
    public @interface PasscodeActivityType {}

    private final static int ID_SWITCH_TYPE = 1;

    private RLottieImageView lockImageView;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextViewSwitcher descriptionTextSwitcher;
    private OutlineTextContainerView outlinePasswordView;
    private EditTextBoldCursor passwordEditText;
    private CodeFieldContainer codeFieldContainer;
    private TextView passcodesDoNotMatchTextView;

    private ImageView passwordButton;

    private CustomPhoneKeyboardView keyboardView;

    private FrameLayout floatingButtonContainer;
    private VerticalPositionAutoAnimator floatingAutoAnimator;
    private TransformableLoginButtonView floatingButtonIcon;
    private Animator floatingButtonAnimator;

    @PasscodeActivityType
    private int type;
    @SharedConfig.PasscodeType
    private int currentPasswordType = 0;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int utyanRow;
    private int hintRow;

    private int changePasscodeRow;
    private int fingerprintRow;
    private int autoLockRow;
    private int autoLockDetailRow;

    private int captureHeaderRow;
    private int captureRow;
    private int captureDetailRow;

    private int disablePasscodeRow;

    private int rowCount;

    private ActionBarMenuItem otherItem;

    private boolean postedHidePasscodesDoNotMatch;
    private Runnable hidePasscodesDoNotMatch = () -> {
        postedHidePasscodesDoNotMatch = false;
        AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, false);
    };

    private Runnable onShowKeyboardCallback;

    public PasscodeActivity(@PasscodeActivityType int type) {
        super();
        this.type = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        View fragmentContentView;
        FrameLayout frameLayout = new FrameLayout(context);
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            fragmentContentView = frameLayout;
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            scrollView.setFillViewport(true);
            fragmentContentView = scrollView;
        }
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int frameBottom;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() >= AndroidUtilities.dp(20)) {
                    if (isCustomKeyboardVisible()) {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) + measureKeyboardHeight());
                    } else {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                    }
                } else if (keyboardView.getVisibility() != View.GONE) {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                } else {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                }

                keyboardView.layout(0, frameBottom, getMeasuredWidth(), frameBottom + AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                notifyHeightChanged();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);

                int frameHeight = height;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() < AndroidUtilities.dp(20)) {
                    frameHeight -= AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                }
                fragmentContentView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
                keyboardView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP), MeasureSpec.EXACTLY));
            }
        };
        contentView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight >= AndroidUtilities.dp(20) && onShowKeyboardCallback != null) {
                onShowKeyboardCallback.run();
                onShowKeyboardCallback = null;
            }
        });
        fragmentView = contentView;
        contentView.addView(fragmentContentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setVisibility(isCustomKeyboardVisible() ? View.VISIBLE : View.GONE);
        contentView.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        switch (type) {
            case TYPE_MANAGE_CODE_SETTINGS: {
                actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
                frameLayout.setTag(Theme.key_windowBackgroundGray);
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                listView = new RecyclerListView(context);
                listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                    @Override
                    public boolean supportsPredictiveItemAnimations() {
                        return false;
                    }
                });
                listView.setVerticalScrollBarEnabled(false);
                listView.setItemAnimator(null);
                listView.setLayoutAnimation(null);
                frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                listView.setAdapter(listAdapter = new ListAdapter(context));
                listView.setOnItemClickListener((view, position) -> {
                    if (!view.isEnabled()) {
                        return;
                    }
                    if (position == disablePasscodeRow) {
                        AlertDialog alertDialog = new AlertDialog.Builder(getParentActivity())
                                .setTitle(LocaleController.getString(R.string.DisablePasscode))
                                .setMessage(LocaleController.getString(R.string.DisablePasscodeConfirmMessage))
                                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                .setPositiveButton(LocaleController.getString(R.string.DisablePasscodeTurnOff), (dialog, which) -> {
                                    SharedConfig.passcodeHash = "";
                                    SharedConfig.appLocked = false;
                                    SharedConfig.saveConfig();
                                    getMediaDataController().buildShortcuts();
                                    int count = listView.getChildCount();
                                    for (int a = 0; a < count; a++) {
                                        View child = listView.getChildAt(a);
                                        if (child instanceof TextSettingsCell) {
                                            TextSettingsCell textCell = (TextSettingsCell) child;
                                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                                            break;
                                        }
                                    }
                                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
                                    finishFragment();
                                }).create();
                        alertDialog.show();
                        ((TextView)alertDialog.getButton(Dialog.BUTTON_POSITIVE)).setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    } else if (position == changePasscodeRow) {
                        presentFragment(new PasscodeActivity(TYPE_SETUP_CODE));
                    } else if (position == autoLockRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AutoLock", R.string.AutoLock));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(4);
                        if (SharedConfig.autoLockIn == 0) {
                            numberPicker.setValue(0);
                        } else if (SharedConfig.autoLockIn == 60) {
                            numberPicker.setValue(1);
                        } else if (SharedConfig.autoLockIn == 60 * 5) {
                            numberPicker.setValue(2);
                        } else if (SharedConfig.autoLockIn == 60 * 60) {
                            numberPicker.setValue(3);
                        } else if (SharedConfig.autoLockIn == 60 * 60 * 5) {
                            numberPicker.setValue(4);
                        }
                        numberPicker.setFormatter(value -> {
                            if (value == 0) {
                                return LocaleController.getString("AutoLockDisabled", R.string.AutoLockDisabled);
                            } else if (value == 1) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 1));
                            } else if (value == 2) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 5));
                            } else if (value == 3) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 1));
                            } else if (value == 4) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 5));
                            }
                            return "";
                        });
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), (dialog, which) -> {
                            which = numberPicker.getValue();
                            if (which == 0) {
                                SharedConfig.autoLockIn = 0;
                            } else if (which == 1) {
                                SharedConfig.autoLockIn = 60;
                            } else if (which == 2) {
                                SharedConfig.autoLockIn = 60 * 5;
                            } else if (which == 3) {
                                SharedConfig.autoLockIn = 60 * 60;
                            } else if (which == 4) {
                                SharedConfig.autoLockIn = 60 * 60 * 5;
                            }
                            listAdapter.notifyItemChanged(position);
                            UserConfig.getInstance(currentAccount).saveConfig(false);
                        });
                        showDialog(builder.create());
                    } else if (position == fingerprintRow) {
                        SharedConfig.useFingerprint = !SharedConfig.useFingerprint;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                        ((TextCheckCell) view).setChecked(SharedConfig.useFingerprint);
                    } else if (position == captureRow) {
                        SharedConfig.allowScreenCapture = !SharedConfig.allowScreenCapture;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                        ((TextCheckCell) view).setChecked(SharedConfig.allowScreenCapture);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode, false);
                        if (!SharedConfig.allowScreenCapture) {
                            AlertsCreator.showSimpleAlert(PasscodeActivity.this, LocaleController.getString("ScreenCaptureAlert", R.string.ScreenCaptureAlert));
                        }
                    }
                });
                break;
            }
            case TYPE_SETUP_CODE:
            case TYPE_ENTER_CODE_TO_MANAGE_SETTINGS: {
                if (actionBar != null) {
                    actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                    actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
                    actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
                    actionBar.setCastShadows(false);
                    ActionBarMenu menu = actionBar.createMenu();

                    ActionBarMenuSubItem switchItem;
                    if (type == TYPE_SETUP_CODE) {
                        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                        switchItem = otherItem.addSubItem(ID_SWITCH_TYPE, R.drawable.msg_permissions, LocaleController.getString(R.string.PasscodeSwitchToPassword));
                    } else switchItem = null;

                    actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                        @Override
                        public void onItemClick(int id) {
                            if (id == -1) {
                                finishFragment();
                            } else if (id == ID_SWITCH_TYPE) {
                                currentPasswordType = currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? SharedConfig.PASSCODE_TYPE_PASSWORD : SharedConfig.PASSCODE_TYPE_PIN;
                                AndroidUtilities.runOnUIThread(()->{
                                    switchItem.setText(LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.PasscodeSwitchToPassword : R.string.PasscodeSwitchToPIN));
                                    switchItem.setIcon(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.drawable.msg_permissions : R.drawable.msg_pin_code);
                                    showKeyboard();
                                    if (isPinCode()) {
                                        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                                        AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, false);
                                    }
                                }, 150);
                                passwordEditText.setText("");
                                for (CodeNumberField f : codeFieldContainer.codeField) {
                                    f.setText("");
                                }
                                updateFields();
                            }
                        }
                    });
                }

                FrameLayout codeContainer = new FrameLayout(context);

                LinearLayout innerLinearLayout = new LinearLayout(context);
                innerLinearLayout.setOrientation(LinearLayout.VERTICAL);
                innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                frameLayout.addView(innerLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                lockImageView = new RLottieImageView(context);
                lockImageView.setFocusable(false);
                lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
                lockImageView.setAutoRepeat(false);
                lockImageView.playAnimation();
                lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
                innerLinearLayout.addView(lockImageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

                titleTextView = new TextView(context);
                titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                if (type == TYPE_SETUP_CODE) {
                    if (SharedConfig.passcodeHash.length() != 0) {
                        titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
                    } else {
                        titleTextView.setText(LocaleController.getString("CreatePasscode", R.string.CreatePasscode));
                    }
                } else {
                    titleTextView.setText(LocaleController.getString(R.string.EnterYourPasscode));
                }
                titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

                descriptionTextSwitcher = new TextViewSwitcher(context);
                descriptionTextSwitcher.setFactory(() -> {
                    TextView tv = new TextView(context);
                    tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    tv.setGravity(Gravity.CENTER_HORIZONTAL);
                    tv.setLineSpacing(AndroidUtilities.dp(2), 1);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    return tv;
                });
                descriptionTextSwitcher.setInAnimation(context, R.anim.alpha_in);
                descriptionTextSwitcher.setOutAnimation(context, R.anim.alpha_out);
                innerLinearLayout.addView(descriptionTextSwitcher, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 8, 20, 0));

                TextView forgotPasswordButton = new TextView(context);
                forgotPasswordButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                forgotPasswordButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                forgotPasswordButton.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
                forgotPasswordButton.setGravity((isPassword() ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL) | Gravity.CENTER_VERTICAL);

                forgotPasswordButton.setOnClickListener(v -> AlertsCreator.createForgotPasscodeDialog(context).show());
                forgotPasswordButton.setVisibility(type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS ? View.VISIBLE : View.GONE);
                forgotPasswordButton.setText(LocaleController.getString(R.string.ForgotPasscode));
                frameLayout.addView(forgotPasswordButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 56 : 60, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));
                VerticalPositionAutoAnimator.attach(forgotPasswordButton);

                passcodesDoNotMatchTextView = new TextView(context);
                passcodesDoNotMatchTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                passcodesDoNotMatchTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                passcodesDoNotMatchTextView.setText(LocaleController.getString(R.string.PasscodesDoNotMatchTryAgain));
                passcodesDoNotMatchTextView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
                AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, false, 1f, false);
                frameLayout.addView(passcodesDoNotMatchTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

                outlinePasswordView = new OutlineTextContainerView(context);
                outlinePasswordView.setText(LocaleController.getString(R.string.EnterPassword));

                passwordEditText = new EditTextBoldCursor(context);
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passwordEditText.setBackground(null);
                passwordEditText.setMaxLines(1);
                passwordEditText.setLines(1);
                passwordEditText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                passwordEditText.setSingleLine(true);
                if (type == TYPE_SETUP_CODE) {
                    passcodeSetStep = 0;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                } else {
                    passcodeSetStep = 1;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                }
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
                passwordEditText.setCursorSize(AndroidUtilities.dp(20));
                passwordEditText.setCursorWidth(1.5f);

                int padding = AndroidUtilities.dp(16);
                passwordEditText.setPadding(padding, padding, padding, padding);

                passwordEditText.setOnFocusChangeListener((v, hasFocus) -> outlinePasswordView.animateSelection(hasFocus ? 1 : 0));

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setGravity(Gravity.CENTER_VERTICAL);
                linearLayout.addView(passwordEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                passwordButton = new ImageView(context);
                passwordButton.setImageResource(R.drawable.msg_message);
                passwordButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passwordButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1));
                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, type == TYPE_SETUP_CODE && passcodeSetStep == 0, 0.1f, false);

                AtomicBoolean isPasswordShown = new AtomicBoolean(false);
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (type == TYPE_SETUP_CODE && passcodeSetStep == 0) {
                            if (TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.GONE) {
                                if (isPasswordShown.get()) {
                                    passwordButton.callOnClick();
                                }
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, false, 0.1f, true);
                            } else if (!TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.VISIBLE) {
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, true);
                            }
                        }
                    }
                });

                passwordButton.setOnClickListener(v -> {
                    isPasswordShown.set(!isPasswordShown.get());

                    int selectionStart = passwordEditText.getSelectionStart(), selectionEnd = passwordEditText.getSelectionEnd();
                    passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | (isPasswordShown.get() ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                    passwordEditText.setSelection(selectionStart, selectionEnd);

                    passwordButton.setColorFilter(Theme.getColor(isPasswordShown.get() ? Theme.key_windowBackgroundWhiteInputFieldActivated : Theme.key_windowBackgroundWhiteHintText));
                });
                linearLayout.addView(passwordButton, LayoutHelper.createLinearRelatively(24, 24, 0, 0, 0, 14, 0));

                outlinePasswordView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                codeContainer.addView(outlinePasswordView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 0));

                passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (passcodeSetStep == 0) {
                        processNext();
                        return true;
                    } else if (passcodeSetStep == 1) {
                        processDone();
                        return true;
                    }
                    return false;
                });
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (postedHidePasscodesDoNotMatch) {
                            codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                            hidePasscodesDoNotMatch.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

                passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public void onDestroyActionMode(ActionMode mode) {
                    }

                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        return false;
                    }
                });

                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        if (passcodeSetStep == 0) {
                            postDelayed(()->processNext(), 260);
                        } else {
                            processDone();
                        }
                    }
                };
                codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
                    f.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
                    f.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            if (postedHidePasscodesDoNotMatch) {
                                codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                                hidePasscodesDoNotMatch.run();
                            }
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                    f.setOnFocusChangeListener((v, hasFocus) -> {
                        keyboardView.setEditText(f);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    });
                }
                codeContainer.addView(codeFieldContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 10, 40, 0));

                innerLinearLayout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 72));

                if (type == TYPE_SETUP_CODE) {
                    frameLayout.setTag(Theme.key_windowBackgroundWhite);
                }

                floatingButtonContainer = new FrameLayout(context);
                if (Build.VERSION.SDK_INT >= 21) {
                    StateListAnimator animator = new StateListAnimator();
                    animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                    animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                    floatingButtonContainer.setStateListAnimator(animator);
                    floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                        @SuppressLint("NewApi")
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                        }
                    });
                }
                floatingAutoAnimator = VerticalPositionAutoAnimator.attach(floatingButtonContainer);
                frameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 24, 16));
                floatingButtonContainer.setOnClickListener(view -> {
                    if (type == TYPE_SETUP_CODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
                        processDone();
                    }
                });

                floatingButtonIcon = new TransformableLoginButtonView(context);
                floatingButtonIcon.setTransformType(TransformableLoginButtonView.TRANSFORM_ARROW_CHECK);
                floatingButtonIcon.setProgress(0f);
                floatingButtonIcon.setColor(Theme.getColor(Theme.key_chats_actionIcon));
                floatingButtonIcon.setDrawBackground(false);
                floatingButtonContainer.setContentDescription(LocaleController.getString(R.string.Next));
                floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

                Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                    shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                    combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    drawable = combinedDrawable;
                }
                floatingButtonContainer.setBackground(drawable);

                updateFields();
                break;
            }
        }

        return fragmentView;
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return type != TYPE_MANAGE_CODE_SETTINGS;
    }

    /**
     * Sets custom keyboard visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
    private void setCustomKeyboardVisible(boolean visible, boolean animate) {
        if (visible) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        } else {
            AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
        }

        if (!animate) {
            keyboardView.setVisibility(visible ? View.VISIBLE : View.GONE);
            keyboardView.setAlpha(visible ? 1 : 0);
            keyboardView.setTranslationY(visible ? 0 : AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
            fragmentView.requestLayout();
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? CubicBezierInterpolator.DEFAULT : Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                keyboardView.setAlpha(val);
                keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) * 0.75f);
                fragmentView.requestLayout();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        keyboardView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        keyboardView.setVisibility(View.GONE);
                    }
                }
            });
            animator.start();
        }
    }

    /**
     * Sets floating button visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
    private void setFloatingButtonVisible(boolean visible, boolean animate) {
        if (floatingButtonAnimator != null) {
            floatingButtonAnimator.cancel();
            floatingButtonAnimator = null;
        }
        if (!animate) {
            floatingAutoAnimator.setOffsetY(visible ? 0 : AndroidUtilities.dp(70));
            floatingButtonContainer.setAlpha(visible ? 1f : 0f);
            floatingButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? AndroidUtilities.decelerateInterpolator : AndroidUtilities.accelerateInterpolator);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                floatingAutoAnimator.setOffsetY(AndroidUtilities.dp(70) * (1f - val));
                floatingButtonContainer.setAlpha(val);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        floatingButtonContainer.setVisibility(View.GONE);
                    }
                    if (floatingButtonAnimator == animation) {
                        floatingButtonAnimator = null;
                    }
                }
            });
            animator.start();
            floatingButtonAnimator = animator;
        }
    }

    /**
     * @return New fragment to open when Passcode entry gets clicked
     */
    public static BaseFragment determineOpenFragment() {
        if (SharedConfig.passcodeHash.length() != 0) {
            return new PasscodeActivity(TYPE_ENTER_CODE_TO_MANAGE_SETTINGS);
        }
        return new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_SET_PASSCODE);
    }

    private void animateSuccessAnimation(Runnable callback) {
        if (!isPinCode()) {
            callback.run();
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            CodeNumberField field = codeFieldContainer.codeField[i];
            field.postDelayed(()-> field.animateSuccessProgress(1f), i * 75L);
        }
        codeFieldContainer.postDelayed(() -> {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateSuccessProgress(0f);
            }
            callback.run();
        }, codeFieldContainer.codeField.length * 75L + 350L);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setCustomKeyboardVisible(isCustomKeyboardVisible(), false);
        if (lockImageView != null) {
            lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
        }
        if (codeFieldContainer != null && codeFieldContainer.codeField != null) {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != TYPE_MANAGE_CODE_SETTINGS && !isCustomKeyboardVisible()) {
            AndroidUtilities.runOnUIThread(this::showKeyboard, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);

        if (isCustomKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode && (args.length == 0 || (Boolean) args[0])) {
            if (type == TYPE_MANAGE_CODE_SETTINGS) {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        utyanRow = rowCount++;
        hintRow = rowCount++;
        changePasscodeRow = rowCount++;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && AndroidUtilities.isKeyguardSecure()) {
                    fingerprintRow = rowCount++;
                } else {
                    fingerprintRow = -1;
                }
            } else fingerprintRow = -1;
        } catch (Throwable e) {
            FileLog.e(e);
        }
        autoLockRow = rowCount++;
        autoLockDetailRow = rowCount++;
        captureHeaderRow = rowCount++;
        captureRow = rowCount++;
        captureDetailRow = rowCount++;
        disablePasscodeRow = rowCount++;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != TYPE_MANAGE_CODE_SETTINGS) {
            showKeyboard();
        }
    }

    private void showKeyboard() {
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
            if (!isCustomKeyboardVisible()) {
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[0]);
            }
        } else if (isPassword()) {
            passwordEditText.requestFocus();
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateFields() {
        String text;
        if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            text = LocaleController.getString(R.string.EnterYourPasscodeInfo);
        } else if (passcodeSetStep == 0) {
            text = LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreatePasscodeInfoPIN : R.string.CreatePasscodeInfoPassword);
        } else text = descriptionTextSwitcher.getCurrentView().getText().toString();

        boolean animate = !(descriptionTextSwitcher.getCurrentView().getText().equals(text) || TextUtils.isEmpty(descriptionTextSwitcher.getCurrentView().getText()));
        if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscodeInfo), animate);
        } else if (passcodeSetStep == 0) {
            descriptionTextSwitcher.setText(LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreatePasscodeInfoPIN : R.string.CreatePasscodeInfoPassword), animate);
        }
        if (isPinCode()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, true, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, false, 1f, animate);
        } else if (isPassword()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, false, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, true, 1f, animate);
        }
        boolean show = isPassword();
        if (show) {
            onShowKeyboardCallback = () -> {
                setFloatingButtonVisible(show, animate);
                AndroidUtilities.cancelRunOnUIThread(onShowKeyboardCallback);
            };
            AndroidUtilities.runOnUIThread(onShowKeyboardCallback, 3000); // Timeout for floating keyboard
        } else {
            setFloatingButtonVisible(show, animate);
        }
        setCustomKeyboardVisible(isCustomKeyboardVisible(), animate);
        showKeyboard();
    }

    /**
     * @return If custom keyboard should be visible
     */
    private boolean isCustomKeyboardVisible() {
        return isPinCode() && type != TYPE_MANAGE_CODE_SETTINGS && !AndroidUtilities.isTablet() &&
                AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y && !AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private void processNext() {
        if (currentPasswordType == SharedConfig.PASSCODE_TYPE_PASSWORD && passwordEditText.getText().length() == 0 || currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN && codeFieldContainer.getCode().length() != 4) {
            onPasscodeError();
            return;
        }

        if (otherItem != null) {
            otherItem.setVisibility(View.GONE);
        }

        titleTextView.setText(LocaleController.getString("ConfirmCreatePasscode", R.string.ConfirmCreatePasscode));
        descriptionTextSwitcher.setText(AndroidUtilities.replaceTags(LocaleController.getString("PasscodeReinstallNotice", R.string.PasscodeReinstallNotice)));
        firstPassword = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        passwordEditText.setText("");
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for (CodeNumberField f : codeFieldContainer.codeField) f.setText("");
        showKeyboard();
        passcodeSetStep = 1;
    }

    private boolean isPinCode() {
        return type == TYPE_SETUP_CODE && currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ||
                type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN;
    }

    private boolean isPassword() {
        return type == TYPE_SETUP_CODE && currentPasswordType == SharedConfig.PASSCODE_TYPE_PASSWORD ||
                type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD;
    }

    private void processDone() {
        if (isPassword() && passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        String password = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        if (type == TYPE_SETUP_CODE) {
            if (!firstPassword.equals(password)) {
                AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, true);
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
                if (isPinCode()) {
                    codeFieldContainer.codeField[0].requestFocus();
                }
                passwordEditText.setText("");
                onPasscodeError();

                codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                codeFieldContainer.post(()->{
                    codeFieldContainer.postDelayed(hidePasscodesDoNotMatch, 3000);
                    postedHidePasscodesDoNotMatch = true;
                });
                return;
            }

            boolean isFirst = SharedConfig.passcodeHash.length() == 0;
            try {
                SharedConfig.passcodeSalt = new byte[16];
                Utilities.random.nextBytes(SharedConfig.passcodeSalt);
                byte[] passcodeBytes = firstPassword.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                SharedConfig.passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            } catch (Exception e) {
                FileLog.e(e);
            }
            SharedConfig.allowScreenCapture = true;
            SharedConfig.passcodeType = currentPasswordType;
            SharedConfig.saveConfig();

            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.clearFocus();
                AndroidUtilities.hideKeyboard(f);
            }
            keyboardView.setEditText(null);

            animateSuccessAnimation(() -> {
                getMediaDataController().buildShortcuts();
                if (isFirst) {
                    presentFragment(new PasscodeActivity(TYPE_MANAGE_CODE_SETTINGS), true);
                } else {
                    finishFragment();
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            });
        } else if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            if (SharedConfig.passcodeRetryInMs > 0) {
                int value = Math.max(1, (int) Math.ceil(SharedConfig.passcodeRetryInMs / 1000.0));
                Toast.makeText(getParentActivity(), LocaleController.formatString("TooManyTries", R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)), Toast.LENGTH_SHORT).show();

                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
                passwordEditText.setText("");
                if (isPinCode()) {
                    codeFieldContainer.codeField[0].requestFocus();
                }
                onPasscodeError();
                return;
            }
            if (!SharedConfig.checkPasscode(password)) {
                SharedConfig.increaseBadPasscodeTries();
                passwordEditText.setText("");
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
                if (isPinCode()) {
                    codeFieldContainer.codeField[0].requestFocus();
                }
                onPasscodeError();
                return;
            }
            SharedConfig.badPasscodeTries = 0;
            SharedConfig.saveConfig();

            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.clearFocus();
                AndroidUtilities.hideKeyboard(f);
            }
            keyboardView.setEditText(null);

            animateSuccessAnimation(() -> {
                presentFragment(new PasscodeActivity(TYPE_MANAGE_CODE_SETTINGS), true);
            });
        }
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) return;
        try {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
        if (isPinCode()) {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateErrorProgress(1f);
            }
        } else {
            outlinePasswordView.animateError(1f);
        }
        AndroidUtilities.shakeViewSpring(isPinCode() ? codeFieldContainer : outlinePasswordView, isPinCode() ? 10 : 4, () -> AndroidUtilities.runOnUIThread(()->{
            if (isPinCode()) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateErrorProgress(0f);
                }
            } else {
                outlinePasswordView.animateError(0f);
            }
        }, isPinCode() ? 150 : 1000));
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_CHECK = 0,
                VIEW_TYPE_SETTING = 1,
                VIEW_TYPE_INFO = 2,
                VIEW_TYPE_HEADER = 3,
                VIEW_TYPE_UTYAN = 4;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == fingerprintRow || position == autoLockRow || position == captureRow ||
                    position == changePasscodeRow || position == disablePasscodeRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_UTYAN:
                    view = new RLottieImageHolderView(mContext);
                    break;
                case VIEW_TYPE_INFO:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CHECK: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == fingerprintRow) {
                        textCell.setTextAndCheck(LocaleController.getString("UnlockFingerprint", R.string.UnlockFingerprint), SharedConfig.useFingerprint, true);
                    } else if (position == captureRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ScreenCaptureShowContent), SharedConfig.allowScreenCapture, false);
                    }
                    break;
                }
                case VIEW_TYPE_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changePasscodeRow) {
                        textCell.setText(LocaleController.getString("ChangePasscode", R.string.ChangePasscode), true);
                        if (SharedConfig.passcodeHash.length() == 0) {
                            textCell.setTag(Theme.key_windowBackgroundWhiteGrayText7);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                        } else {
                            textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        }
                    } else if (position == autoLockRow) {
                        String val;
                        if (SharedConfig.autoLockIn == 0) {
                            val = LocaleController.formatString("AutoLockDisabled", R.string.AutoLockDisabled);
                        } else if (SharedConfig.autoLockIn < 60 * 60) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", SharedConfig.autoLockIn / 60));
                        } else if (SharedConfig.autoLockIn < 60 * 60 * 24) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", (int) Math.ceil(SharedConfig.autoLockIn / 60.0f / 60)));
                        } else {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Days", (int) Math.ceil(SharedConfig.autoLockIn / 60.0f / 60 / 24)));
                        }
                        textCell.setTextAndValue(LocaleController.getString("AutoLock", R.string.AutoLock), val, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == disablePasscodeRow) {
                        textCell.setText(LocaleController.getString(R.string.DisablePasscode), false);
                        textCell.setTag(Theme.key_text_RedBold);
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setHeight(46);
                    if (position == captureHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.ScreenCaptureHeader));
                    }
                    break;
                }
                case VIEW_TYPE_UTYAN: {
                    RLottieImageHolderView holderView = (RLottieImageHolderView) holder.itemView;
                    holderView.imageView.setAnimation(R.raw.utyan_passcode, 100, 100);
                    holderView.imageView.playAnimation();
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == hintRow) {
                        cell.setText(LocaleController.getString(R.string.PasscodeScreenHint));
                        cell.setBackground(null);
                        cell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
                    } else if (position == autoLockDetailRow) {
                        cell.setText(LocaleController.getString(R.string.AutoLockInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == captureDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ScreenCaptureInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == fingerprintRow || position == captureRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == changePasscodeRow || position == autoLockRow || position == disablePasscodeRow) {
                return VIEW_TYPE_SETTING;
            } else if (position == autoLockDetailRow || position == captureDetailRow || position == hintRow) {
                return VIEW_TYPE_INFO;
            } else if (position == captureHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == utyanRow) {
                return VIEW_TYPE_UTYAN;
            }
            return VIEW_TYPE_CHECK;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }

    private final static class RLottieImageHolderView extends FrameLayout {
        private RLottieImageView imageView;

        private RLottieImageHolderView(@NonNull Context context) {
            super(context);
            imageView = new RLottieImageView(context);
            imageView.setOnClickListener(v -> {
                if (!imageView.getAnimatedDrawable().isRunning()) {
                    imageView.getAnimatedDrawable().setCurrentFrame(0, false);
                    imageView.playAnimation();
                }
            });
            int size = AndroidUtilities.dp(120);
            LayoutParams params = new LayoutParams(size, size);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            addView(imageView, params);

            setPadding(0, AndroidUtilities.dp(32), 0, 0);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
