/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Vibrator;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;

public class PasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private ListView listView;
    private TextView titleTextView;
    private EditText passwordEditText;
    private TextView dropDown;
    private ActionBarMenuItem dropDownContainer;

    private int type;
    private int currentPasswordType = 0;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int passcodeRow;
    private int changePasscodeRow;
    private int passcodeDetailRow;
    private int fingerprintRow;
    private int autoLockRow;
    private int autoLockDetailRow;
    private int rowCount;

    private final static int done_button = 1;
    private final static int pin_item = 2;
    private final static int password_item = 3;

    public PasscodeActivity(int type) {
        super();
        this.type = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == 0) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetPasscode);
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
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            titleTextView = new TextView(context);
            titleTextView.setTextColor(0xff757575);
            if (type == 1) {
                if (UserConfig.passcodeHash.length() != 0) {
                    titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
                } else {
                    titleTextView.setText(LocaleController.getString("EnterNewFirstPasscode", R.string.EnterNewFirstPasscode));
                }
            } else {
                titleTextView.setText(LocaleController.getString("EnterCurrentPasscode", R.string.EnterCurrentPasscode));
            }
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            frameLayout.addView(titleTextView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) titleTextView.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            layoutParams.topMargin = AndroidUtilities.dp(38);
            titleTextView.setLayoutParams(layoutParams);

            passwordEditText = new EditText(context);
            passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            passwordEditText.setTextColor(0xff000000);
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
            AndroidUtilities.clearCursorDrawable(passwordEditText);
            frameLayout.addView(passwordEditText);
            layoutParams = (FrameLayout.LayoutParams) passwordEditText.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(90);
            layoutParams.height = AndroidUtilities.dp(36);
            layoutParams.leftMargin = AndroidUtilities.dp(40);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.rightMargin = AndroidUtilities.dp(40);
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            passwordEditText.setLayoutParams(layoutParams);
            passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (passcodeSetStep == 0) {
                        processNext();
                        return true;
                    } else if (passcodeSetStep == 1) {
                        processDone();
                        return true;
                    }
                    return false;
                }
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
                        if (type == 2 && UserConfig.passcodeType == 0) {
                            processDone();
                        } else if (type == 1 && currentPasswordType == 0) {
                            if (passcodeSetStep == 0) {
                                processNext();
                            } else if (passcodeSetStep == 1) {
                                processDone();
                            }
                        }
                    }
                }
            });
            if (android.os.Build.VERSION.SDK_INT < 11) {
                passwordEditText.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                        menu.clear();
                    }
                });
            } else {
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
            }

            if (type == 1) {
                dropDownContainer = new ActionBarMenuItem(context, menu, 0);
                dropDownContainer.setSubMenuOpenSide(1);
                dropDownContainer.addSubItem(pin_item, LocaleController.getString("PasscodePIN", R.string.PasscodePIN), 0);
                dropDownContainer.addSubItem(password_item, LocaleController.getString("PasscodePassword", R.string.PasscodePassword), 0);
                actionBar.addView(dropDownContainer);
                layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                layoutParams.height = LayoutHelper.MATCH_PARENT;
                layoutParams.width = LayoutHelper.WRAP_CONTENT;
                layoutParams.rightMargin = AndroidUtilities.dp(40);
                layoutParams.leftMargin = AndroidUtilities.isTablet() ? AndroidUtilities.dp(64) : AndroidUtilities.dp(56);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                dropDownContainer.setLayoutParams(layoutParams);
                dropDownContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dropDownContainer.toggleSubMenu();
                    }
                });

                dropDown = new TextView(context);
                dropDown.setGravity(Gravity.LEFT);
                dropDown.setSingleLine(true);
                dropDown.setLines(1);
                dropDown.setMaxLines(1);
                dropDown.setEllipsize(TextUtils.TruncateAt.END);
                dropDown.setTextColor(0xffffffff);
                dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                dropDown.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0);
                dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
                dropDownContainer.addView(dropDown);
                layoutParams = (FrameLayout.LayoutParams) dropDown.getLayoutParams();
                layoutParams.width = LayoutHelper.WRAP_CONTENT;
                layoutParams.height = LayoutHelper.WRAP_CONTENT;
                layoutParams.leftMargin = AndroidUtilities.dp(16);
                layoutParams.gravity = Gravity.CENTER_VERTICAL;
                layoutParams.bottomMargin = AndroidUtilities.dp(1);
                dropDown.setLayoutParams(layoutParams);
            } else {
                actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            }

            updateDropDownTextView();
        } else {
            actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            frameLayout.setBackgroundColor(0xfff0f0f0);
            listView = new ListView(context);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDrawSelectorOnTop(true);
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter = new ListAdapter(context));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == changePasscodeRow) {
                        presentFragment(new PasscodeActivity(1));
                    } else if (i == passcodeRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        if (UserConfig.passcodeHash.length() != 0) {
                            UserConfig.passcodeHash = "";
                            UserConfig.appLocked = false;
                            UserConfig.saveConfig(false);
                            int count = listView.getChildCount();
                            for (int a = 0; a < count; a++) {
                                View child = listView.getChildAt(a);
                                if (child instanceof TextSettingsCell) {
                                    TextSettingsCell textCell = (TextSettingsCell) child;
                                    textCell.setTextColor(0xffc6c6c6);
                                    break;
                                }
                            }
                            cell.setChecked(UserConfig.passcodeHash.length() != 0);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetPasscode);
                        } else {
                            presentFragment(new PasscodeActivity(1));
                        }
                    } else if (i == autoLockRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AutoLock", R.string.AutoLock));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(4);
                        if (UserConfig.autoLockIn == 0) {
                            numberPicker.setValue(0);
                        } else if (UserConfig.autoLockIn == 60) {
                            numberPicker.setValue(1);
                        } else if (UserConfig.autoLockIn == 60 * 5) {
                            numberPicker.setValue(2);
                        } else if (UserConfig.autoLockIn == 60 * 60) {
                            numberPicker.setValue(3);
                        } else if (UserConfig.autoLockIn == 60 * 60 * 5) {
                            numberPicker.setValue(4);
                        }
                        numberPicker.setFormatter(new NumberPicker.Formatter() {
                            @Override
                            public String format(int value) {
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
                            }
                        });
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                which = numberPicker.getValue();
                                if (which == 0) {
                                    UserConfig.autoLockIn = 0;
                                } else if (which == 1) {
                                    UserConfig.autoLockIn = 60;
                                } else if (which == 2) {
                                    UserConfig.autoLockIn = 60 * 5;
                                } else if (which == 3) {
                                    UserConfig.autoLockIn = 60 * 60;
                                } else if (which == 4) {
                                    UserConfig.autoLockIn = 60 * 60 * 5;
                                }
                                listView.invalidateViews();
                                UserConfig.saveConfig(false);
                            }
                        });
                        showDialog(builder.create());
                    } else if (i == fingerprintRow) {
                        UserConfig.useFingerprint = !UserConfig.useFingerprint;
                        UserConfig.saveConfig(false);
                        ((TextCheckCell) view).setChecked(UserConfig.useFingerprint);
                    }
                }
            });
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != 0) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (passwordEditText != null) {
                        passwordEditText.requestFocus();
                        AndroidUtilities.showKeyboard(passwordEditText);
                    }
                }
            }, 200);
        }
        fixLayoutInternal();
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.didSetPasscode) {
            if (type == 0) {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        passcodeRow = rowCount++;
        changePasscodeRow = rowCount++;
        passcodeDetailRow = rowCount++;
        if (UserConfig.passcodeHash.length() > 0) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                    if (fingerprintManager.isHardwareDetected()) {
                        fingerprintRow = rowCount++;
                    }
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
            autoLockRow = rowCount++;
            autoLockDetailRow = rowCount++;
        } else {
            fingerprintRow = -1;
            autoLockRow = -1;
            autoLockDetailRow = -1;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    fixLayoutInternal();
                    return true;
                }
            });
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != 0) {
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateDropDownTextView() {
        if (dropDown != null) {
            if (currentPasswordType == 0) {
                dropDown.setText(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
            } else if (currentPasswordType == 1) {
                dropDown.setText(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
            }
        }
        if (type == 1 && currentPasswordType == 0 || type == 2 && UserConfig.passcodeType == 0) {
            InputFilter[] filterArray = new InputFilter[1];
            filterArray[0] = new InputFilter.LengthFilter(4);
            passwordEditText.setFilters(filterArray);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            passwordEditText.setKeyListener(DigitsKeyListener.getInstance("1234567890"));
        } else if (type == 1 && currentPasswordType == 1 || type == 2 && UserConfig.passcodeType == 1) {
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
        dropDownContainer.setVisibility(View.GONE);
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
                    FileLog.e("tmessages", e);
                }
                AndroidUtilities.shakeView(titleTextView, 2, 0);
                passwordEditText.setText("");
                return;
            }

            try {
                UserConfig.passcodeSalt = new byte[16];
                Utilities.random.nextBytes(UserConfig.passcodeSalt);
                byte[] passcodeBytes = firstPassword.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(UserConfig.passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(UserConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                UserConfig.passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            UserConfig.passcodeType = currentPasswordType;
            UserConfig.saveConfig(false);
            finishFragment();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetPasscode);
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
        } else if (type == 2) {
            if (!UserConfig.checkPasscode(passwordEditText.getText().toString())) {
                passwordEditText.setText("");
                onPasscodeError();
                return;
            }
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            presentFragment(new PasscodeActivity(0), true);
        }
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(titleTextView, 2, 0);
    }

    private void fixLayoutInternal() {
        if (dropDownContainer != null) {
            if (!AndroidUtilities.isTablet()) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                dropDownContainer.setLayoutParams(layoutParams);
            }
            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dropDown.setTextSize(18);
            } else {
                dropDown.setTextSize(20);
            }
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i == passcodeRow || i == fingerprintRow || i == autoLockRow || UserConfig.passcodeHash.length() != 0 && i == changePasscodeRow;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int viewType = getItemViewType(i);
            if (viewType == 0) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextCheckCell textCell = (TextCheckCell) view;

                if (i == passcodeRow) {
                    textCell.setTextAndCheck(LocaleController.getString("Passcode", R.string.Passcode), UserConfig.passcodeHash.length() > 0, true);
                } else if (i == fingerprintRow) {
                    textCell.setTextAndCheck(LocaleController.getString("UnlockFingerprint", R.string.UnlockFingerprint), UserConfig.useFingerprint, true);
                }
            } else if (viewType == 1) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == changePasscodeRow) {
                    textCell.setText(LocaleController.getString("ChangePasscode", R.string.ChangePasscode), false);
                    textCell.setTextColor(UserConfig.passcodeHash.length() == 0 ? 0xffc6c6c6 : 0xff000000);
                } else if (i == autoLockRow) {
                    String val;
                    if (UserConfig.autoLockIn == 0) {
                        val = LocaleController.formatString("AutoLockDisabled", R.string.AutoLockDisabled);
                    } else if (UserConfig.autoLockIn < 60 * 60) {
                        val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", UserConfig.autoLockIn / 60));
                    } else if (UserConfig.autoLockIn < 60 * 60 * 24) {
                        val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", (int) Math.ceil(UserConfig.autoLockIn / 60.0f / 60)));
                    } else {
                        val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Days", (int) Math.ceil(UserConfig.autoLockIn / 60.0f / 60 / 24)));
                    }
                    textCell.setTextAndValue(LocaleController.getString("AutoLock", R.string.AutoLock), val, true);
                    textCell.setTextColor(0xff000000);
                }
            } else if (viewType == 2) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (i == passcodeDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("ChangePasscodeInfo", R.string.ChangePasscodeInfo));
                    if (autoLockDetailRow != -1) {
                        view.setBackgroundResource(R.drawable.greydivider);
                    } else {
                        view.setBackgroundResource(R.drawable.greydivider_bottom);
                    }
                } else if (i == autoLockDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("AutoLockInfo", R.string.AutoLockInfo));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == passcodeRow || i == fingerprintRow) {
                return 0;
            } else if (i == changePasscodeRow || i == autoLockRow) {
                return 1;
            } else if (i == passcodeDetailRow || i == autoLockDetailRow) {
                return 2;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
