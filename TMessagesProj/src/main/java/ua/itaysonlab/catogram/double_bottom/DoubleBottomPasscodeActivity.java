/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package ua.itaysonlab.catogram.double_bottom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class DoubleBottomPasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int done_button = 1;
    private final static int pin_item = 2;
    private final static int password_item = 3;
    private final int type;
    private final DBPAListener listener;
    private TextView titleTextView;
    private EditTextBoldCursor passwordEditText;
    private int currentPasswordType = SharedConfig.passcodeType;
    private int passcodeSetStep = 0;
    private String firstPassword;

    public DoubleBottomPasscodeActivity(int type, DBPAListener listener) {
        super();
        this.type = type;
        this.listener = listener;
    }

    public static String getHash(String passcode) {
        try {
            byte[] passcodeBytes = passcode.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + passcodeBytes.length];
            System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
            System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
            System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
            return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (type == 0) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        }
    }

    @Override
    public View createView(Context context) {
        if (type != 3) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (passcodeSetStep == 0) {
                        processNext();
                    } else if (passcodeSetStep == 1) {
                        processDone();
                    }
                } else if (id == pin_item) {
                    currentPasswordType = 0;
                    updateDropDownTextView();
                } else if (id == password_item) {
                    currentPasswordType = 1;
                    updateDropDownTextView();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        if (type != 0) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            if (type == 1) {
                titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
            } else {
                titleTextView.setText(LocaleController.getString("EnterCurrentPasscode", R.string.EnterCurrentPasscode));
            }
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            frameLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            passwordEditText = new EditTextBoldCursor(context);
            passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            passwordEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            passwordEditText.setMaxLines(1);
            passwordEditText.setLines(1);
            passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
            passwordEditText.setSingleLine(true);
            if (type == 1) {
                passcodeSetStep = 0;
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            } else {
                passcodeSetStep = 1;
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            }
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordEditText.setTypeface(Typeface.DEFAULT);
            passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            passwordEditText.setCursorSize(AndroidUtilities.dp(20));
            passwordEditText.setCursorWidth(1.5f);
            frameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 40, 90, 40, 0));
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

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (passwordEditText.length() == 4) {
                        if (type == 1 && currentPasswordType == 0) {
                            if (passcodeSetStep == 0) {
                                processNext();
                            } else if (passcodeSetStep == 1) {
                                processDone();
                            }
                        }
                    }
                }
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

            if (type == 1) {
                frameLayout.setTag(Theme.key_windowBackgroundWhite);
            } else {
                actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            }

            updateDropDownTextView();
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (type != 0) {
            AndroidUtilities.runOnUIThread(() -> {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }, 200);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != 0) {
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateDropDownTextView() {
        if (type == 1 && currentPasswordType == 0) {
            InputFilter[] filterArray = new InputFilter[1];
            filterArray[0] = new InputFilter.LengthFilter(4);
            passwordEditText.setFilters(filterArray);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            passwordEditText.setKeyListener(DigitsKeyListener.getInstance("1234567890"));
        } else if (type == 1 && currentPasswordType == 1) {
            passwordEditText.setFilters(new InputFilter[0]);
            passwordEditText.setKeyListener(null);
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    private void processNext() {
        if (passwordEditText.getText().length() == 0 || currentPasswordType == 0 && passwordEditText.getText().length() != 4) {
            onPasscodeError();
            return;
        }
        if (currentPasswordType == 0) {
            actionBar.setTitle(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
        } else {
            actionBar.setTitle(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
        }
        titleTextView.setText(LocaleController.getString("ReEnterYourPasscode", R.string.ReEnterYourPasscode));
        firstPassword = passwordEditText.getText().toString();
        passwordEditText.setText("");
        passcodeSetStep = 1;
    }

    private void processDone() {
        if (passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        if (type == 1) {
            if (!firstPassword.equals(passwordEditText.getText().toString())) {
                try {
                    Toast.makeText(getParentActivity(), LocaleController.getString("PasscodeDoNotMatch", R.string.PasscodeDoNotMatch), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.shakeView(titleTextView, 2, 0);
                passwordEditText.setText("");
                return;
            }

            try {
                byte[] passcodeSalt = SharedConfig.passcodeSalt;
                byte[] passcodeBytes = firstPassword.getBytes(StandardCharsets.UTF_8);
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                String passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));

                listener.onPasscodeEntered(passcodeHash, passcodeSalt, currentPasswordType);
            } catch (Exception e) {
                FileLog.e(e);
            }

            finishFragment();
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
        }
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            ua.itaysonlab.extras.CatogramExtras.vibrate(v, 200);
        }
        AndroidUtilities.shakeView(titleTextView, 2, 0);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        return themeDescriptions;
    }

    interface DBPAListener {
        void onPasscodeEntered(String passcodeHash, byte[] passcodeSalt, int type);
    }
}
