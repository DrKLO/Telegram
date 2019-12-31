/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BiometricPromtHelper;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import javax.crypto.Cipher;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class WalletCreateActivity extends BaseFragment {

    private RLottieImageView imageView;
    private TextView buttonTextView;
    private TextView titleTextView;
    private TextView descriptionText;
    private TextView descriptionText2;
    private TextView importButton;
    private NumericEditText[] editTexts;
    private LinearLayout editTextContainer;
    private ScrollView scrollView;
    private View actionBarBackground;
    private RecyclerListView hintListView;
    private LinearLayoutManager hintLayoutManager;
    private EditTextBoldCursor passcodeEditText;
    private View passcodeNumbersView;
    private LinearLayout leftColumn;
    private LinearLayout rightColumn;

    private BiometricPromtHelper biometricPromtHelper;

    private Runnable cancelOnDestroyRunnable;

    private ActionBarPopupWindow hintPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout hintPopupLayout;
    private HintAdapter hintAdapter;
    private NumericEditText hintEditText;

    private AnimatorSet actionBarAnimator;

    private int currentType;

    private boolean changingPasscode;
    private boolean exportingWords;
    private boolean resumeCreation;
    private String[] secretWords;
    private String[] hintWords;
    private int passcodeType;
    private String checkingPasscode;
    private CharSequence sendText;
    private boolean backToWallet;

    private boolean globalIgnoreTextChange;

    private WalletCreateActivity fragmentToRemove;

    private long showTime;
    private ArrayList<Integer> checkWordIndices;

    public static final int TYPE_CREATE = 0;
    public static final int TYPE_KEY_GENERATED = 1;
    public static final int TYPE_READY = 2;
    public static final int TYPE_TOO_BAD = 3;
    public static final int TYPE_24_WORDS = 4;
    public static final int TYPE_WORDS_CHECK = 5;
    public static final int TYPE_IMPORT = 6;
    public static final int TYPE_PERFECT = 7;
    public static final int TYPE_SET_PASSCODE = 8;
    public static final int TYPE_SEND_DONE = 9;

    private static int item_logout = 1;

    private int maxNumberWidth;
    private int maxEditNumberWidth;

    private class HintAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int[] location = new int[2];
        private Runnable searchRunnable;
        private ArrayList<String> searchResult = new ArrayList<>();

        public HintAdapter(Context c) {
            context = c;
        }

        public void searchHintsFor(NumericEditText editText) {
            String text = editText.getText().toString();
            if (text.length() == 0) {
                if (hintPopupWindow.isShowing()) {
                    hintPopupWindow.dismiss();
                }
                return;
            }
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                ArrayList<String> newSearchResults = new ArrayList<>();
                for (int a = 0; a < hintWords.length; a++) {
                    if (hintWords[a].startsWith(text)) {
                        newSearchResults.add(hintWords[a]);
                    }
                }
                if (newSearchResults.size() == 1 && newSearchResults.get(0).equals(text)) {
                    newSearchResults.clear();
                }
                AndroidUtilities.runOnUIThread(() -> {
                    searchRunnable = null;
                    searchResult = newSearchResults;
                    notifyDataSetChanged();
                    if (searchResult.isEmpty()) {
                        hideHint();
                    } else {
                        if (hintEditText != editText || !hintPopupWindow.isShowing()) {
                            hintPopupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                            editText.getLocationInWindow(location);
                            hintLayoutManager.scrollToPositionWithOffset(0, 10000);
                            hintPopupWindow.showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, location[0] - AndroidUtilities.dp(48), location[1] - AndroidUtilities.dp(48 + 16));
                        }
                        hintEditText = editText;
                    }
                });
            }, 200);
        }

        public String getItem(int position) {
            return searchResult.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            textView.setGravity(Gravity.CENTER_VERTICAL);
            textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerListView.Holder(textView);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TextView textView = (TextView) holder.itemView;
            textView.setPadding(AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9), 0);
            textView.setText(searchResult.get(position));
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getItemCount() {
            return searchResult.size();
        }
    }

    private class NumericTextView extends TextView {

        private TextPaint numericPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String number;
        private int numberWidth;

        public NumericTextView(Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(31), 0, 0, 0);

            numericPaint.setTextSize(AndroidUtilities.dp(16));
        }

        public void setNumber(String value) {
            number = value;
            numberWidth = (int) Math.ceil(numericPaint.measureText(number));
            maxNumberWidth = Math.max(maxNumberWidth, numberWidth);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (number != null) {
                numericPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                canvas.drawText(number, maxNumberWidth - numberWidth, AndroidUtilities.dp(17), numericPaint);
            }
        }
    }

    private class NumericEditText extends FrameLayout {

        private ImageView deleteImageView;
        private EditTextBoldCursor editText;
        private TextPaint numericPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String number;
        private int numberWidth;
        private boolean ignoreSearch;

        public NumericEditText(Context context, int number) {
            super(context);
            setWillNotDraw(false);
            numericPaint.setTextSize(AndroidUtilities.dp(17));

            editText = new EditTextBoldCursor(context);
            editText.setTag(number);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            if (currentType == TYPE_WORDS_CHECK) {
                setNumber(String.format(Locale.US, "%d:", checkWordIndices.get(number) + 1));
            } else {
                setNumber(String.format(Locale.US, "%d:", number + 1));
            }
            editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            editText.setPadding(AndroidUtilities.dp(31), AndroidUtilities.dp(2), AndroidUtilities.dp(30), 0);
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setCursorWidth(1.5f);
            editText.setMaxLines(1);
            editText.setLines(1);
            editText.setSingleLine(true);
            editText.setImeOptions((number != editTexts.length - 1 ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE) | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            editText.setCursorSize(AndroidUtilities.dp(20));
            editText.setGravity(Gravity.LEFT);
            addView(editText, LayoutHelper.createFrame(220, 36));
            editText.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    if (num < editTexts.length - 1) {
                        editTexts[num + 1].editText.requestFocus();
                        editTexts[num + 1].editText.setSelection(editTexts[num + 1].length());
                    }
                    hideHint();
                    return true;
                } else if (i == EditorInfo.IME_ACTION_DONE) {
                    buttonTextView.callOnClick();
                    return true;
                }
                return false;
            });
            editText.addTextChangedListener(new TextWatcher() {

                private boolean ignoreTextChange;
                private boolean isPaste;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (currentType == TYPE_IMPORT) {
                        isPaste = after > count && after > 40;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange || globalIgnoreTextChange) {
                        return;
                    }
                    ignoreTextChange = true;
                    if (isPaste) {
                        globalIgnoreTextChange = true;
                        try {
                            String[] args = s.toString().split("\n");
                            if (args.length == 24) {
                                for (int a = 0; a < 24; a++) {
                                    editTexts[a].editText.setText(args[a].toLowerCase());
                                }
                            }
                            editTexts[23].editText.requestFocus();
                            return;
                        } catch (Exception e) {
                            FileLog.e(e);
                        } finally {
                            globalIgnoreTextChange = false;
                        }
                    }
                    s.replace(0, s.length(), s.toString().toLowerCase().trim());
                    ignoreTextChange = false;
                    updateClearButton();
                    if (hintAdapter != null && !ignoreSearch) {
                        hintAdapter.searchHintsFor(NumericEditText.this);
                    }
                }
            });
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                updateClearButton();
                hideHint();
            });

            deleteImageView = new ImageView(context);
            deleteImageView.setFocusable(false);
            deleteImageView.setScaleType(ImageView.ScaleType.CENTER);
            deleteImageView.setImageResource(R.drawable.miniplayer_close);
            deleteImageView.setAlpha(0.0f);
            deleteImageView.setScaleX(0.0f);
            deleteImageView.setScaleY(0.0f);
            deleteImageView.setRotation(45);
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7), PorterDuff.Mode.MULTIPLY));
            deleteImageView.setContentDescription(LocaleController.getString("ClearButton", R.string.ClearButton));
            addView(deleteImageView, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.TOP));
            deleteImageView.setOnClickListener(v -> {
                if (deleteImageView.getAlpha() != 1.0f) {
                    return;
                }
                editText.setText("");
            });
        }

        private void updateClearButton() {
            boolean show = editText.length() > 0 && editText.hasFocus();
            boolean visible = deleteImageView.getTag() != null;
            if (show != visible) {
                deleteImageView.setTag(show ? 1 : null);
                deleteImageView.animate().alpha(show ? 1.0f : 0.0f).scaleX(show ? 1.0f : 0.0f).scaleY(show ? 1.0f : 0.0f).rotation(show ? 0 : 45).setDuration(150).start();
            }
        }

        public int length() {
            return editText.length();
        }

        public Editable getText() {
            return editText.getText();
        }

        public void setNumber(String value) {
            number = value;
            numberWidth = (int) Math.ceil(numericPaint.measureText(number));
            maxEditNumberWidth = Math.max(maxEditNumberWidth, numberWidth);
        }

        public void setText(CharSequence text) {
            ignoreSearch = true;
            editText.setText(text);
            editText.setSelection(editText.length());
            ignoreSearch = false;
            int num = (Integer) editText.getTag();
            if (num < editTexts.length - 1) {
                editTexts[num + 1].editText.requestFocus();
                editTexts[num + 1].editText.setSelection(editTexts[num + 1].length());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (number != null) {
                numericPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                canvas.drawText(number, (maxEditNumberWidth - numberWidth) / 2, AndroidUtilities.dp(20), numericPaint);
            }
        }
    }

    public WalletCreateActivity(int type) {
        super();
        currentType = type;
        showTime = SystemClock.uptimeMillis();
    }

    private void hideHint() {
        if (hintPopupWindow != null && hintPopupWindow.isShowing()) {
            hintPopupWindow.dismiss();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        if (currentType == TYPE_24_WORDS || currentType == TYPE_WORDS_CHECK) {
            if (secretWords == null || secretWords.length != 24) {
                return false;
            }
        }
        if (currentType == TYPE_IMPORT || currentType == TYPE_WORDS_CHECK) {
            hintWords = getTonController().getHintWords();
            if (hintWords == null) {
                return false;
            }
        }
        if (currentType == TYPE_READY) {
            getTonController().saveWalletKeys(true);
        }
        if (currentType == TYPE_WORDS_CHECK) {
            checkWordIndices = new ArrayList<>();
            while (checkWordIndices.size() < 3) {
                int index = Utilities.random.nextInt(24);
                if (checkWordIndices.contains(index)) {
                    continue;
                }
                checkWordIndices.add(index);
            }
            Collections.sort(checkWordIndices);
        }
        if (currentType == TYPE_24_WORDS && !exportingWords) {
            cancelOnDestroyRunnable = () -> {
                if (fragmentView != null) {
                    fragmentView.setKeepScreenOn(false);
                }
                cancelOnDestroyRunnable = null;
            };
            AndroidUtilities.runOnUIThread(cancelOnDestroyRunnable, 60 * 1000);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (currentType == TYPE_WORDS_CHECK || currentType == TYPE_SET_PASSCODE) {
            AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        }
        if (currentType == TYPE_SET_PASSCODE) {
            getTonController().finishSettingUserPasscode();
        }
        if (cancelOnDestroyRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(cancelOnDestroyRunnable);
            cancelOnDestroyRunnable = null;
        }
        if (Build.VERSION.SDK_INT >= 23 && AndroidUtilities.allowScreenCapture() && currentType != TYPE_SEND_DONE && currentType != TYPE_CREATE) {
            AndroidUtilities.setFlagSecure(this, false);
        }
    }

    @Override
    public View createView(Context context) {
        if (swipeBackEnabled = canGoBack() && (currentType != TYPE_CREATE || !BuildVars.TON_WALLET_STANDALONE)) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            if (currentType == TYPE_WORDS_CHECK) {
                swipeBackEnabled = false;
            }
        }
        actionBar.setBackgroundDrawable(null);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == item_logout) {
                    getTonController().cleanup();
                    UserConfig userConfig = getUserConfig();
                    userConfig.clearTonConfig();
                    userConfig.saveConfig(false);
                    finishFragment();
                }
            }
        });

        if (currentType == TYPE_CREATE) {
            importButton = new TextView(context);
            importButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
            importButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            importButton.setText(LocaleController.getString("ImportExistingWallet", R.string.ImportExistingWallet));
            importButton.setGravity(Gravity.CENTER_VERTICAL);
            actionBar.addView(importButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT, 0, 0, 22, 0));
            importButton.setOnClickListener(v -> {
                WalletCreateActivity fragment = new WalletCreateActivity(TYPE_IMPORT);
                fragment.fragmentToRemove = this;
                presentFragment(fragment);
            });
        } else if (currentType == TYPE_IMPORT || currentType == TYPE_WORDS_CHECK) {
            hintPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
            hintPopupLayout.setAnimationEnabled(false);
            hintPopupLayout.setOnTouchListener(new View.OnTouchListener() {

                private android.graphics.Rect popupRect = new android.graphics.Rect();

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (hintPopupWindow != null && hintPopupWindow.isShowing()) {
                            v.getHitRect(popupRect);
                            if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                hintPopupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            hintPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && hintPopupWindow != null && hintPopupWindow.isShowing()) {
                    hintPopupWindow.dismiss();
                }
            });
            hintPopupLayout.setShowedFromBotton(false);

            hintListView = new RecyclerListView(context);
            hintListView.setAdapter(hintAdapter = new HintAdapter(context));
            hintListView.setPadding(AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9), 0);
            hintListView.setLayoutManager(hintLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            hintListView.setClipToPadding(false);
            hintPopupLayout.addView(hintListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
            hintListView.setOnItemClickListener((view, position) -> {
                hintEditText.setText(hintAdapter.getItem(position));
                hintPopupWindow.dismiss();
            });

            hintPopupWindow = new ActionBarPopupWindow(hintPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            hintPopupWindow.setAnimationEnabled(false);
            hintPopupWindow.setAnimationStyle(R.style.PopupAnimation);
            hintPopupWindow.setClippingEnabled(true);
            hintPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            hintPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            hintPopupWindow.getContentView().setFocusableInTouchMode(true);
            hintPopupWindow.setFocusable(false);
        } else if (currentType == TYPE_KEY_GENERATED) {
            if (resumeCreation) {
                biometricPromtHelper = new BiometricPromtHelper(this);
                if (BuildVars.DEBUG_VERSION) {
                    ActionBarMenu menu = actionBar.createMenu();
                    menu.addItemWithWidth(item_logout, R.drawable.ic_ab_delete, AndroidUtilities.dp(56));
                }
            }
        }

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);

        descriptionText2 = new TextView(context);
        descriptionText2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText2.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText2.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText2.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        descriptionText2.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        descriptionText2.setVisibility(View.GONE);

        buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        buttonTextView.setOnClickListener(v -> {
            if (getParentActivity() == null) {
                return;
            }
            hideHint();
            switch (currentType) {
                case TYPE_CREATE: {
                    createWallet();
                    break;
                }
                case TYPE_KEY_GENERATED: {
                    if (resumeCreation) {
                        switch (getTonController().getKeyProtectionType()) {
                            case TonController.KEY_PROTECTION_TYPE_LOCKSCREEN: {
                                if (Build.VERSION.SDK_INT >= 23) {
                                    KeyguardManager keyguardManager = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
                                    Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletExportConfirmContinue", R.string.WalletExportConfirmContinue));
                                    getParentActivity().startActivityForResult(intent, WalletSettingsActivity.SEND_ACTIVITY_RESULT_CODE);
                                }
                                break;
                            }
                            case TonController.KEY_PROTECTION_TYPE_BIOMETRIC: {
                                biometricPromtHelper.promtWithCipher(getTonController().getCipherForDecrypt(), LocaleController.getString("WalletExportConfirmContinue", R.string.WalletExportConfirmContinue), this::doExport);
                                break;
                            }
                        }
                    } else {
                        WalletCreateActivity fragment = new WalletCreateActivity(TYPE_24_WORDS);
                        fragment.secretWords = secretWords;
                        presentFragment(fragment, true);
                    }
                    break;
                }
                case TYPE_24_WORDS: {
                    if (exportingWords) {
                        finishFragment();
                        return;
                    }
                    if (SystemClock.uptimeMillis() - showTime < 60 * 1000) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTopAnimation(R.raw.wallet_science, Theme.getColor(Theme.key_dialogBackgroundGray));
                        builder.setTitle(LocaleController.getString("WalletSecretWordsAlertTitle", R.string.WalletSecretWordsAlertTitle));
                        builder.setMessage(LocaleController.getString("WalletSecretWordsAlertText", R.string.WalletSecretWordsAlertText));
                        builder.setPositiveButton(LocaleController.getString("WalletSecretWordsAlertButton", R.string.WalletSecretWordsAlertButton), null);
                        showDialog(builder.create());
                        return;
                    }
                    WalletCreateActivity fragment = new WalletCreateActivity(TYPE_WORDS_CHECK);
                    fragment.fragmentToRemove = this;
                    fragment.secretWords = secretWords;
                    presentFragment(fragment);
                    break;
                }
                case TYPE_WORDS_CHECK: {
                    if (!checkEditTexts()) {
                        return;
                    }
                    for (int a = 0, N = checkWordIndices.size(); a < N; a++) {
                        int index = checkWordIndices.get(a);
                        if (!secretWords[index].equals(editTexts[a].getText().toString())) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("WalletTestTimeAlertTitle", R.string.WalletTestTimeAlertTitle));
                            builder.setMessage(LocaleController.getString("WalletTestTimeAlertText", R.string.WalletTestTimeAlertText));
                            builder.setNegativeButton(LocaleController.getString("WalletTestTimeAlertButtonSee", R.string.WalletTestTimeAlertButtonSee), (dialog, which) -> finishFragment());
                            builder.setPositiveButton(LocaleController.getString("WalletTestTimeAlertButtonTry", R.string.WalletTestTimeAlertButtonTry), null);
                            showDialog(builder.create());
                            return;
                        }
                    }
                    if (fragmentToRemove != null) {
                        fragmentToRemove.removeSelfFromStack();
                    }
                    if (getTonController().isWaitingForUserPasscode()) {
                        presentFragment(new WalletCreateActivity(TYPE_PERFECT), true);
                    } else {
                        presentFragment(new WalletCreateActivity(TYPE_READY), true);
                    }
                    break;
                }
                case TYPE_READY: {
                    presentFragment(new WalletActivity(), true);
                    break;
                }
                case TYPE_IMPORT: {
                    if (!checkEditTexts()) {
                        return;
                    }
                    createWallet();
                    break;
                }
                case TYPE_TOO_BAD: {
                    finishFragment();
                    break;
                }
                case TYPE_PERFECT: {
                    presentFragment(new WalletCreateActivity(TYPE_SET_PASSCODE), true);
                    break;
                }
                case TYPE_SEND_DONE: {
                    if (backToWallet) {
                        finishFragment();
                    } else {
                        presentFragment(new WalletActivity(), true);
                    }
                    break;
                }
            }
        });

        switch (currentType) {
            case TYPE_CREATE:
            case TYPE_TOO_BAD:
            case TYPE_KEY_GENERATED:
            case TYPE_READY:
            case TYPE_PERFECT:
            case TYPE_SEND_DONE: {
                buttonTextView.setMinWidth(AndroidUtilities.dp(150));
                if (currentType == TYPE_TOO_BAD) {
                    descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    descriptionText2.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                    descriptionText2.setTag(Theme.key_windowBackgroundWhiteBlueText2);
                } else {
                    descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    descriptionText2.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    descriptionText2.setTag(Theme.key_windowBackgroundWhiteGrayText6);
                }

                ViewGroup container = new ViewGroup(context) {

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int height = MeasureSpec.getSize(heightMeasureSpec);

                        if (importButton != null && Build.VERSION.SDK_INT >= 21) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) importButton.getLayoutParams();
                            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                        }

                        actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);

                        if (width > height) {
                            imageView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.68f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText2.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.399f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText2.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        }

                        setMeasuredDimension(width, height);
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        actionBar.layout(0, 0, r, actionBar.getMeasuredHeight());

                        int width = r - l;
                        int height = b - t;

                        if (r > b) {
                            int y = (height - imageView.getMeasuredHeight()) / 2;
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            int x = (int) (width * 0.4f);
                            y = (int) (height * 0.22f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.39f);
                            descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            if (currentType == TYPE_KEY_GENERATED || currentType == TYPE_SEND_DONE) {
                                y = (int) (height * 0.74f);
                            } else {
                                y = (int) (height * 0.64f);
                            }
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.8f);
                            descriptionText2.layout(x, y, x + descriptionText2.getMeasuredWidth(), y + descriptionText2.getMeasuredHeight());
                        } else {
                            int y = (int) (height * 0.148f);
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y = (int) (height * 0.458f);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y = (int) (height * 0.52f);
                            descriptionText.layout(0, y, descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            int x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = (int) (height * 0.791f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                            y = (int) (height * 0.894f);
                            descriptionText2.layout(0, y, descriptionText2.getMeasuredWidth(), y + descriptionText2.getMeasuredHeight());
                        }
                    }
                };
                container.setOnTouchListener((v, event) -> true);
                container.addView(actionBar);
                container.addView(imageView);
                container.addView(titleTextView);
                container.addView(descriptionText);
                container.addView(descriptionText2);
                container.addView(buttonTextView);
                fragmentView = container;
                break;
            }
            case TYPE_24_WORDS:
            case TYPE_WORDS_CHECK:
            case TYPE_IMPORT: {
                buttonTextView.setMinWidth(AndroidUtilities.dp(220));
                descriptionText2.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                descriptionText2.setTag(Theme.key_windowBackgroundWhiteBlueText2);
                if (currentType == TYPE_IMPORT) {
                    descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                } else {
                    descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                }

                ViewGroup container = new ViewGroup(context) {

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int height = MeasureSpec.getSize(heightMeasureSpec);

                        if (importButton != null) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) importButton.getLayoutParams();
                            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                        }

                        actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                        actionBarBackground.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(actionBar.getMeasuredHeight() + AndroidUtilities.dp(3), MeasureSpec.EXACTLY));
                        scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);

                        setMeasuredDimension(width, height);
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        actionBar.layout(0, 0, actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
                        actionBarBackground.layout(0, 0, actionBarBackground.getMeasuredWidth(), actionBarBackground.getMeasuredHeight());
                        scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                    }
                };

                scrollView = new ScrollView(context) {

                    private int[] location = new int[2];
                    private Rect tempRect = new Rect();
                    private boolean isLayoutDirty = true;
                    private View scrollingToChild;
                    private int scrollingUp;

                    @Override
                    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                        super.onScrollChanged(l, t, oldl, oldt);
                        hideHint();

                        if (titleTextView == null) {
                            return;
                        }
                        titleTextView.getLocationOnScreen(location);
                        boolean show = location[1] + titleTextView.getMeasuredHeight() < actionBar.getBottom();
                        boolean visible = titleTextView.getTag() == null;
                        if (show != visible) {
                            titleTextView.setTag(show ? null : 1);
                            if (actionBarAnimator != null) {
                                actionBarAnimator.cancel();
                                actionBarAnimator = null;
                            }
                            actionBarAnimator = new AnimatorSet();
                            actionBarAnimator.playTogether(
                                    ObjectAnimator.ofFloat(actionBarBackground, View.ALPHA, show ? 1.0f : 0.0f),
                                    ObjectAnimator.ofFloat(actionBar.getTitleTextView(), View.ALPHA, show ? 1.0f : 0.0f)
                            );
                            actionBarAnimator.setDuration(150);
                            actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (animation.equals(actionBarAnimator)) {
                                        actionBarAnimator = null;
                                    }
                                }
                            });
                            actionBarAnimator.start();
                        }
                    }

                    @Override
                    public void scrollToDescendant(View child) {
                        scrollingToChild = child;
                        child.getDrawingRect(tempRect);
                        offsetDescendantRectToMyCoords(child, tempRect);
                        if (editTexts != null && editTexts[editTexts.length - 1].editText == child) {
                            tempRect.bottom += AndroidUtilities.dp(90);
                        } else {
                            tempRect.bottom += AndroidUtilities.dp(10);
                        }
                        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(tempRect);
                        if (scrollDelta < 0) {
                            scrollDelta -= (scrollingUp = (getMeasuredHeight() - child.getMeasuredHeight()) / 2);
                        } else {
                            scrollingUp = 0;
                        }
                        if (scrollDelta != 0) {
                            smoothScrollBy(0, scrollDelta);
                        }
                    }

                    @Override
                    public void requestChildFocus(View child, View focused) {
                        if (Build.VERSION.SDK_INT < 29) {
                            if (focused != null && !isLayoutDirty) {
                                scrollToDescendant(focused);
                            }
                        }
                        super.requestChildFocus(child, focused);
                    }

                    @Override
                    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                        if (Build.VERSION.SDK_INT < 23) {
                            if (editTexts != null && editTexts[editTexts.length - 1].editText == scrollingToChild) {
                                rectangle.bottom += AndroidUtilities.dp(90);
                            } else {
                                rectangle.bottom += AndroidUtilities.dp(16);
                            }
                            if (scrollingUp != 0) {
                                rectangle.top -= scrollingUp;
                                rectangle.bottom -= scrollingUp;
                                scrollingUp = 0;
                            }
                        }
                        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
                    }

                    @Override
                    public void requestLayout() {
                        isLayoutDirty = true;
                        super.requestLayout();
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        isLayoutDirty = false;
                        super.onLayout(changed, l, t, r, b);
                    }
                };
                scrollView.setVerticalScrollBarEnabled(false);
                container.addView(scrollView);

                LinearLayout scrollViewLinearLayout = new LinearLayout(context);
                scrollViewLinearLayout.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(scrollViewLinearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

                scrollViewLinearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 69, 0, 0));
                scrollViewLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));
                scrollViewLinearLayout.addView(descriptionText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 9, 0, 0));
                scrollViewLinearLayout.addView(descriptionText2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 17, 0, 0));

                if (currentType == TYPE_24_WORDS) {
                    leftColumn = new LinearLayout(context);
                    leftColumn.setOrientation(LinearLayout.VERTICAL);

                    rightColumn = new LinearLayout(context);
                    rightColumn.setOrientation(LinearLayout.VERTICAL);

                    LinearLayout columnsLayout = new LinearLayout(context);
                    columnsLayout.setOrientation(LinearLayout.HORIZONTAL);
                    columnsLayout.addView(leftColumn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                    columnsLayout.addView(rightColumn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 57, 0, 0, 0));
                    scrollViewLinearLayout.addView(columnsLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 30, 0, 0));

                    maxNumberWidth = 0;
                    for (int a = 0; a < 12; a++) {
                        for (int b = 0; b < 2; b++) {
                            NumericTextView textView = new NumericTextView(context);
                            textView.setGravity(Gravity.LEFT | Gravity.TOP);
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                            textView.setNumber(String.format(Locale.US, "%d.", b == 0 ? 1 + a : 13 + a));
                            textView.setText(secretWords[b == 0 ? a : 12 + a]);
                            (b == 0 ? leftColumn : rightColumn).addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, a == 0 ? 0 : 10, 0, 0));
                        }
                    }
                } else if (currentType == TYPE_WORDS_CHECK || currentType == TYPE_IMPORT) {
                    maxEditNumberWidth = 0;
                    editTexts = new NumericEditText[currentType == TYPE_WORDS_CHECK ? 3 : 24];
                    editTextContainer = scrollViewLinearLayout;
                    for (int a = 0; a < editTexts.length; a++) {
                        scrollViewLinearLayout.addView(editTexts[a] = new NumericEditText(context, a), LayoutHelper.createLinear(220, 36, Gravity.CENTER_HORIZONTAL, 0, a == 0 ? 21 : 13, 0, 0));
                    }
                }

                scrollViewLinearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 36, 0, 33));
                fragmentView = container;

                actionBarBackground = new View(context) {

                    private Paint paint = new Paint();

                    @Override
                    protected void onDraw(Canvas canvas) {
                        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        int h = getMeasuredHeight() - AndroidUtilities.dp(3);
                        canvas.drawRect(0, 0, getMeasuredWidth(), h, paint);
                        parentLayout.drawHeaderShadow(canvas, h);
                    }
                };
                actionBarBackground.setAlpha(0.0f);
                container.addView(actionBarBackground);
                container.addView(actionBar);

                break;
            }
            case TYPE_SET_PASSCODE: {
                descriptionText2.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                descriptionText2.setTag(Theme.key_windowBackgroundWhiteBlueText2);
                descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

                scrollView = new ScrollView(context);
                scrollView.setFillViewport(true);

                FrameLayout frameLayout = new FrameLayout(context);
                scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

                ViewGroup container = new ViewGroup(context) {

                    private boolean ignoreLayout;

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int height = MeasureSpec.getSize(heightMeasureSpec);

                        ignoreLayout = true;
                        actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                        frameLayout.setPadding(0, actionBar.getMeasuredHeight(), 0, 0);
                        scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        ignoreLayout = false;

                        setMeasuredDimension(width, height);
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        actionBar.layout(0, 0, actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
                        int y = actionBar.getMeasuredHeight();
                        scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                    }

                    @Override
                    public void requestLayout() {
                        if (ignoreLayout) {
                            return;
                        }
                        super.requestLayout();
                    }
                };

                container.addView(scrollView);

                frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
                frameLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 120, 0, 0));
                frameLayout.addView(descriptionText2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 260, 0, 22));

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                framePaint.setStyle(Paint.Style.STROKE);
                framePaint.setStrokeWidth(AndroidUtilities.dp(1));

                passcodeNumbersView = new View(context) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (passcodeEditText == null || passcodeType == 2) {
                            return;
                        }
                        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        framePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                        int count = passcodeType == 0 ? 4 : 6;
                        int rad = AndroidUtilities.dp(8);
                        int d = AndroidUtilities.dp(11);
                        int width = d * (count - 1) + rad * 2 * count;
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (getMeasuredHeight() - rad * 2) / 2;
                        int charactersCount = passcodeEditText.length();
                        for (int a = 0; a < count; a++) {
                            canvas.drawCircle(x + rad, y, rad, a < charactersCount ? paint : framePaint);
                            x += rad * 2 + d;
                        }
                    }
                };
                frameLayout.addView(passcodeNumbersView, LayoutHelper.createFrame(220, 36, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 190, 0, 0));

                passcodeEditText = new EditTextBoldCursor(context);
                passcodeEditText.setAlpha(0.0f);
                passcodeEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                passcodeEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
                passcodeEditText.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                passcodeEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passcodeEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passcodeEditText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passcodeEditText.setCursorWidth(1.5f);
                passcodeEditText.setMaxLines(1);
                passcodeEditText.setHint(LocaleController.getString("WalletSetPasscodeEnterCode", R.string.WalletSetPasscodeEnterCode));
                passcodeEditText.setLines(1);
                passcodeEditText.setSingleLine(true);
                passcodeEditText.setGravity(Gravity.CENTER_HORIZONTAL);
                passcodeEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                passcodeEditText.setCursorSize(AndroidUtilities.dp(20));
                passcodeEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passcodeEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passcodeEditText.setTypeface(Typeface.DEFAULT);
                setCurrentPasscodeLengthLimit();
                frameLayout.addView(passcodeEditText, LayoutHelper.createFrame(220, 36, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 190, 0, 0));
                passcodeEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_DONE) {
                        onPasscodeEnter();
                        return true;
                    }
                    return false;
                });
                passcodeEditText.addTextChangedListener(new TextWatcher() {

                    private boolean ignoreTextChange;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreTextChange) {
                            return;
                        }
                        ignoreTextChange = true;
                        for (int a = 0; a < s.length(); a++) {
                            char c = s.charAt(a);
                            if (c < '0' || c > '9') {
                                s.delete(a, a + 1);
                                a--;
                            }
                        }
                        ignoreTextChange = false;
                        if (passcodeType == 0) {
                            if (s.length() == 4) {
                                onPasscodeEnter();
                            }
                        } else if (passcodeType == 1) {
                            if (s.length() == 6) {
                                onPasscodeEnter();
                            }
                        }
                        passcodeNumbersView.invalidate();
                    }
                });
                passcodeEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
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

                fragmentView = container;
                container.addView(actionBar);

                break;
            }
        }

        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        switch (currentType) {
            case TYPE_CREATE: {
                imageView.setAutoRepeat(true);
                imageView.setAnimation(R.raw.wallet_crystal, 120, 120);
                titleTextView.setText(LocaleController.getString("GramWallet", R.string.GramWallet));
                descriptionText.setText(LocaleController.getString("GramWalletInfo", R.string.GramWalletInfo));
                buttonTextView.setText(LocaleController.getString("CreateMyWallet", R.string.CreateMyWallet));

                if (!BuildVars.TON_WALLET_STANDALONE) {
                    String str = LocaleController.getString("CreateMyWalletTerms", R.string.CreateMyWalletTerms);
                    SpannableStringBuilder spanned = new SpannableStringBuilder(str);
                    int index1 = str.indexOf('*');
                    int index2 = str.lastIndexOf('*');
                    if (index1 != -1 && index2 != -1) {
                        spanned.replace(index2, index2 + 1, "");
                        spanned.replace(index1, index1 + 1, "");
                        spanned.setSpan(new URLSpan(LocaleController.getString("WalletTosUrl", R.string.WalletTosUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    descriptionText2.setText(spanned);
                    descriptionText2.setVisibility(View.VISIBLE);
                }
                break;
            }
            case TYPE_KEY_GENERATED: {
                imageView.setAnimation(R.raw.wallet_congrats, 120, 120);
                titleTextView.setText(LocaleController.getString("WalletCongratulations", R.string.WalletCongratulations));
                descriptionText.setText(LocaleController.getString("WalletCongratulationsinfo", R.string.WalletCongratulationsinfo));
                buttonTextView.setText(LocaleController.getString("WalletContinue", R.string.WalletContinue));
                break;
            }
            case TYPE_24_WORDS: {
                imageView.setAnimation(R.raw.wallet_note, 112, 112);
                fragmentView.setKeepScreenOn(true);
                actionBar.setTitle(LocaleController.getString("WalletSecretWordsTitle", R.string.WalletSecretWordsTitle));
                titleTextView.setText(LocaleController.getString("WalletSecretWords", R.string.WalletSecretWords));
                descriptionText.setText(LocaleController.getString("WalletSecretWordsInfo", R.string.WalletSecretWordsInfo));
                buttonTextView.setText(LocaleController.getString("WalletDone", R.string.WalletDone));
                actionBar.getTitleTextView().setAlpha(0.0f);
                break;
            }
            case TYPE_WORDS_CHECK: {
                imageView.setAnimation(R.raw.wallet_science, 104, 104);
                actionBar.setTitle(LocaleController.getString("WalletTestTimeTitle", R.string.WalletTestTimeTitle));
                titleTextView.setText(LocaleController.getString("WalletTestTime", R.string.WalletTestTime));
                descriptionText.setText(AndroidUtilities.replaceTags(LocaleController.formatString("WalletTestTimeInfo", R.string.WalletTestTimeInfo, checkWordIndices.get(0) + 1, checkWordIndices.get(1) + 1, checkWordIndices.get(2) + 1)));
                buttonTextView.setText(LocaleController.getString("WalletContinue", R.string.WalletContinue));
                actionBar.getTitleTextView().setAlpha(0.0f);
                break;
            }
            case TYPE_READY: {
                imageView.setAnimation(R.raw.wallet_allset, 130, 130);
                imageView.setPadding(AndroidUtilities.dp(27), 0, 0, 0);
                titleTextView.setText(LocaleController.getString("WalletReady", R.string.WalletReady));
                descriptionText.setText(LocaleController.getString("WalletReadyInfo", R.string.WalletReadyInfo));
                buttonTextView.setText(LocaleController.getString("WalletView", R.string.WalletView));
                break;
            }
            case TYPE_PERFECT: {
                imageView.setAutoRepeat(true);
                imageView.setAnimation(R.raw.wallet_perfect, 130, 130);
                titleTextView.setText(LocaleController.getString("WalletPerfect", R.string.WalletPerfect));
                descriptionText.setText(LocaleController.getString("WalletPerfectInfo", R.string.WalletPerfectInfo));
                buttonTextView.setText(LocaleController.getString("WalletPerfectSetPasscode", R.string.WalletPerfectSetPasscode));
                break;
            }
            case TYPE_SEND_DONE: {
                imageView.setAnimation(R.raw.wallet_allset, 130, 130);
                imageView.setPadding(AndroidUtilities.dp(27), 0, 0, 0);
                titleTextView.setText(LocaleController.getString("WalletSendDone", R.string.WalletSendDone));
                descriptionText.setText(sendText);
                buttonTextView.setText(LocaleController.getString("WalletView", R.string.WalletView));
                break;
            }
            case TYPE_SET_PASSCODE: {
                imageView.setAutoRepeat(true);
                imageView.setAnimation(R.raw.wallet_lock, 120, 120);
                titleTextView.setText(LocaleController.getString("WalletSetPasscode", R.string.WalletSetPasscode));
                descriptionText2.setVisibility(View.VISIBLE);

                SpannableStringBuilder spanned = new SpannableStringBuilder(LocaleController.getString("WalletSetPasscodeOptions", R.string.WalletSetPasscodeOptions));
                spanned.setSpan(new URLSpanNoUnderline("") {
                    @Override
                    public void onClick(View widget) {
                        BottomSheet.Builder builder = new BottomSheet.Builder(context);
                        builder.setTitle(LocaleController.getString("WalletSetPasscodeChooseType", R.string.WalletSetPasscodeChooseType), true);
                        CharSequence[] items = new CharSequence[]{
                                LocaleController.getString("WalletSetPasscode4Digit", R.string.WalletSetPasscode4Digit),
                                LocaleController.getString("WalletSetPasscode6Digit", R.string.WalletSetPasscode6Digit),
                                LocaleController.getString("WalletSetPasscodeCustom", R.string.WalletSetPasscodeCustom)
                        };
                        builder.setItems(items, (dialogInterface, i) -> {
                            if (passcodeType == i) {
                                return;
                            }
                            passcodeType = i;
                            passcodeEditText.setAlpha(i == 2 ? 1.0f : 0.0f);
                            passcodeNumbersView.setAlpha(i == 2 ? 0.0f : 1.0f);
                            passcodeEditText.setText("");
                            setCurrentPasscodeLengthLimit();
                        });
                        showDialog(builder.create());
                    }
                }, 0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                descriptionText2.setText(spanned);
                break;
            }
            case TYPE_IMPORT: {
                imageView.setAnimation(R.raw.wallet_note, 112, 112);
                actionBar.setTitle(LocaleController.getString("WalletSecretWordsTitle", R.string.WalletSecretWordsTitle));
                titleTextView.setText(LocaleController.getString("WalletSecretWords", R.string.WalletSecretWords));
                descriptionText.setText(LocaleController.getString("WalletImportInfo", R.string.WalletImportInfo));
                buttonTextView.setText(LocaleController.getString("WalletContinue", R.string.WalletContinue));
                actionBar.getTitleTextView().setAlpha(0.0f);
                descriptionText2.setVisibility(View.VISIBLE);

                SpannableStringBuilder spanned = new SpannableStringBuilder(LocaleController.getString("WalletImportDontHave", R.string.WalletImportDontHave));
                spanned.setSpan(new URLSpanNoUnderline("") {
                    @Override
                    public void onClick(View widget) {
                        WalletCreateActivity fragment = new WalletCreateActivity(TYPE_TOO_BAD);
                        fragment.fragmentToRemove = WalletCreateActivity.this;
                        presentFragment(fragment);
                    }
                }, 0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                descriptionText2.setText(spanned);
                break;
            }
            case TYPE_TOO_BAD: {
                imageView.setAutoRepeat(true);
                imageView.setAnimation(R.raw.wallet_toobad, 120, 120);
                titleTextView.setText(LocaleController.getString("WalletTooBad", R.string.WalletTooBad));
                descriptionText.setText(LocaleController.getString("WalletTooBadInfo", R.string.WalletTooBadInfo));
                buttonTextView.setText(LocaleController.getString("WalletTooBadEnter", R.string.WalletTooBadEnter));
                descriptionText2.setVisibility(View.VISIBLE);

                SpannableStringBuilder spanned = new SpannableStringBuilder(LocaleController.getString("WalletTooBadCreate", R.string.WalletTooBadCreate));
                spanned.setSpan(new URLSpanNoUnderline("") {
                    @Override
                    public void onClick(View widget) {
                        if (fragmentToRemove != null) {
                            fragmentToRemove.removeSelfFromStack();
                        }
                        finishFragment();
                    }
                }, 0, spanned.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                descriptionText2.setText(spanned);
                break;
            }
        }
        imageView.playAnimation();

        return fragmentView;
    }

    private void createWallet() {
        BiometricPromtHelper.askForBiometric(this, new BiometricPromtHelper.ContinueCallback() {

            private AlertDialog progressDialog;

            private void doCreate(boolean useBiometric) {
                String[] importWords;
                if (currentType == TYPE_CREATE) {
                    importWords = null;
                } else {
                    importWords = new String[24];
                    for (int a = 0; a < 24; a++) {
                        importWords[a] = editTexts[a].getText().toString();
                    }
                }
                getTonController().createWallet(importWords, useBiometric, (words) -> {
                    progressDialog.dismiss();
                    if (currentType == TYPE_CREATE) {
                        WalletCreateActivity fragment = new WalletCreateActivity(TYPE_KEY_GENERATED);
                        fragment.secretWords = words;
                        presentFragment(fragment, true);
                    } else {
                        if (fragmentToRemove != null) {
                            fragmentToRemove.removeSelfFromStack();
                        }
                        if (getTonController().isWaitingForUserPasscode()) {
                            presentFragment(new WalletCreateActivity(TYPE_PERFECT), true);
                        } else {
                            presentFragment(new WalletCreateActivity(TYPE_READY), true);
                        }
                    }
                }, (text, error) -> {
                    progressDialog.dismiss();
                    if (currentType == TYPE_CREATE) {
                        AlertsCreator.showSimpleAlert(WalletCreateActivity.this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
                    } else {
                        if (text.startsWith("TONLIB")){
                            AlertsCreator.showSimpleAlert(WalletCreateActivity.this, LocaleController.getString("WalletImportAlertTitle", R.string.WalletImportAlertTitle), LocaleController.getString("WalletImportAlertText", R.string.WalletImportAlertText));
                        } else{
                            AlertsCreator.showSimpleAlert(WalletCreateActivity.this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + text);
                        }
                    }
                });
            }

            @Override
            public void run(boolean useBiometric) {
                progressDialog = new AlertDialog(getParentActivity(), 3);
                progressDialog.setCanCacnel(false);
                progressDialog.show();
                UserConfig userConfig = getUserConfig();
                if (userConfig.walletConfigType == TonController.CONFIG_TYPE_URL && TextUtils.isEmpty(userConfig.walletConfigFromUrl)) {
                    WalletConfigLoader.loadConfig(userConfig.walletConfigUrl, result -> {
                        if (TextUtils.isEmpty(result)) {
                            progressDialog.dismiss();
                            AlertsCreator.showSimpleAlert(WalletCreateActivity.this, LocaleController.getString("WalletError", R.string.WalletError), LocaleController.getString("WalletCreateBlockchainConfigLoadError", R.string.WalletCreateBlockchainConfigLoadError));
                            return;
                        }
                        userConfig.walletConfigFromUrl = result;
                        userConfig.saveConfig(false);
                        doCreate(useBiometric);
                    });
                } else {
                    doCreate(useBiometric);
                }
            }
        }, LocaleController.getString("WalletSecurityAlertCreateContinue", R.string.WalletSecurityAlertCreateContinue));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentType == TYPE_WORDS_CHECK || currentType == TYPE_IMPORT || currentType == TYPE_SET_PASSCODE) {
            AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        hideHint();
        if (getParentActivity() != null) {
            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        }
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        hideHint();
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            if (currentType == TYPE_WORDS_CHECK && !backward || currentType == TYPE_IMPORT) {
                editTexts[0].editText.requestFocus();
                AndroidUtilities.showKeyboard(editTexts[0].editText);
            } else if (currentType == TYPE_SET_PASSCODE && !backward) {
                passcodeEditText.requestFocus();
                AndroidUtilities.showKeyboard(passcodeEditText);
            }
            if (Build.VERSION.SDK_INT >= 23 && AndroidUtilities.allowScreenCapture() && currentType != TYPE_SEND_DONE && currentType != TYPE_CREATE) {
                AndroidUtilities.setFlagSecure(this, true);
            }
        }
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        hideHint();
    }

    @Override
    public boolean onBackPressed() {
        return canGoBack();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == WalletSettingsActivity.SEND_ACTIVITY_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                doExport(null);
            }
        }
    }

    private void doExport(Cipher cipher) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
        getTonController().getSecretWords(null, cipher, (words) -> {
            progressDialog.dismiss();
            WalletCreateActivity fragment = new WalletCreateActivity(TYPE_24_WORDS);
            fragment.secretWords = words;
            presentFragment(fragment, true);
        }, (text, error) -> {
            progressDialog.dismiss();
            if (text.equals("KEYSTORE_FAIL")) {
                getTonController().cleanup();
                UserConfig userConfig = getUserConfig();
                userConfig.clearTonConfig();
                userConfig.saveConfig(false);
                finishFragment();
            } else {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
            }
        });
    }

    private boolean checkEditTexts() {
        if (editTexts == null) {
            return true;
        }
        for (int a = 0; a < editTexts.length; a++) {
            if (editTexts[a].length() == 0) {
                editTexts[a].editText.clearFocus();
                editTexts[a].editText.requestFocus();
                AndroidUtilities.shakeView(editTexts[a], 2, 0);
                try {
                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        v.vibrate(200);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }
        }
        return true;
    }

    private boolean canGoBack() {
        return currentType != TYPE_READY && currentType != TYPE_SEND_DONE;
    }

    public void setSecretWords(String[] words) {
        secretWords = words;
        exportingWords = true;
    }

    public void setResumeCreation() {
        resumeCreation = true;
    }

    public void setChangingPasscode() {
        changingPasscode = true;
    }

    private void setCurrentPasscodeLengthLimit() {
        int maxCount;
        if (passcodeType == 0) {
            maxCount = 4;
        } else if (passcodeType == 1) {
            maxCount = 6;
        } else if (passcodeType == 2) {
            maxCount = 32;
        } else {
            return;
        }
        passcodeEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxCount)});
    }

    private void showPasscodeConfirm() {
        if (passcodeEditText == null) {
            return;
        }
        checkingPasscode = passcodeEditText.getText().toString();
        passcodeEditText.setText("");
        passcodeNumbersView.invalidate();
        descriptionText2.setVisibility(View.INVISIBLE);
        titleTextView.setText(LocaleController.getString("WalletSetPasscodeRepeat", R.string.WalletSetPasscodeRepeat));
    }

    private void onPasscodeEnter() {
        if (checkingPasscode == null) {
            int length = passcodeEditText.length();
            if (passcodeType != 2) {
                int maxCount;
                if (passcodeType == 0) {
                    maxCount = 4;
                } else {
                    maxCount = 6;
                }
                if (length != maxCount) {
                    onPasscodeError();
                    return;
                }
            } else if (length < 4) {
                onPasscodeError();
                Toast.makeText(getParentActivity(), LocaleController.getString("WalletSetPasscodeMinLength", R.string.WalletSetPasscodeMinLength), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentType == 2) {
                showPasscodeConfirm();
            } else {
                AndroidUtilities.runOnUIThread(this::showPasscodeConfirm, 150);
            }
        } else {
            String passcode = passcodeEditText.getText().toString();
            if (!passcode.equals(checkingPasscode)) {
                Toast.makeText(getParentActivity(), LocaleController.getString("WalletSetPasscodeError", R.string.WalletSetPasscodeError), Toast.LENGTH_SHORT).show();
                titleTextView.setText(LocaleController.getString("WalletSetPasscode", R.string.WalletSetPasscode));
                onPasscodeError();
                checkingPasscode = null;
                passcodeEditText.setText("");
                descriptionText2.setVisibility(View.VISIBLE);
                passcodeNumbersView.invalidate();
                return;
            }
            AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
            getTonController().setUserPasscode(passcode, passcodeType, () -> {
                progressDialog.dismiss();
                if (changingPasscode) {
                    getTonController().saveWalletKeys(true);
                    finishFragment();
                } else {
                    presentFragment(new WalletCreateActivity(TYPE_READY), true);
                }
            });
        }
    }

    private void onPasscodeError() {
        AndroidUtilities.shakeView(passcodeType == 2 ? passcodeEditText : passcodeNumbersView, 2, 0);
        try {
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setSendText(CharSequence text, boolean back) {
        sendText = text;
        backToWallet = back;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector),

                new ThemeDescription(passcodeNumbersView, 0, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(passcodeNumbersView, 0, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(actionBarBackground, 0, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(hintListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(editTextContainer, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{NumericEditText.class}, new String[]{"editText"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(editTextContainer, ThemeDescription.FLAG_CURSORCOLOR, new Class[]{NumericEditText.class}, new String[]{"editText"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(editTextContainer, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{NumericEditText.class}, new String[]{"deleteImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7),
                new ThemeDescription(editTextContainer, 0, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(leftColumn, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{NumericTextView.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(rightColumn, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{NumericTextView.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(leftColumn, 0, new Class[]{NumericTextView.class}, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(rightColumn, 0, new Class[]{NumericTextView.class}, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(passcodeEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(passcodeEditText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(passcodeEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(importButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText2),
                new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(descriptionText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(descriptionText2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(descriptionText2, ThemeDescription.FLAG_LINKCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText2),
                new ThemeDescription(descriptionText2, ThemeDescription.FLAG_LINKCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(buttonTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_featuredStickers_buttonText),
                new ThemeDescription(buttonTextView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, null, null, null, null, Theme.key_featuredStickers_addButton),
                new ThemeDescription(buttonTextView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed),
        };
    }
}
