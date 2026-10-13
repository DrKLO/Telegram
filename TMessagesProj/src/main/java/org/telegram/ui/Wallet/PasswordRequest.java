package org.telegram.ui.Wallet;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.TwoStepVerificationActivity;
import org.telegram.ui.TwoStepVerificationSetupActivity;

public class PasswordRequest {

    public static <Result extends TLObject> void open(
        int currentAccount,
        Utilities.CallbackReturn<TLRPC.InputCheckPasswordSRP, TLMethod<Result>> makeRequest,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done
    ) {
        open(currentAccount, makeRequest, done, false, true);
    }

    public static <Result extends TLObject> void open(
        int currentAccount,
        Utilities.CallbackReturn<TLRPC.InputCheckPasswordSRP, TLMethod<Result>> makeRequestReturn,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done,
        boolean sheet,
        boolean progress
    ) {
        init(currentAccount, makeRequestReturn, null, null, null, done, sheet, progress, new RequestScope(null));
    }

    public static <Result extends TLObject> void open(
        int currentAccount,
        Utilities.Callback2<TLRPC.InputCheckPasswordSRP, Utilities.Callback<TLMethod<Result>>> makeRequestCallback,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done,
        boolean sheet,
        boolean progress
    ) {
        init(currentAccount, null, makeRequestCallback, null, null, done, sheet, progress, new RequestScope(null));
    }

    /** Releases operation-owned secrets after completion or dismissal of password entry. */
    public static <Result extends TLObject> void open(
        int currentAccount,
        Utilities.CallbackReturn<TLRPC.InputCheckPasswordSRP, TLMethod<Result>> makeRequest,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done,
        boolean sheet, boolean progress, Runnable cleanup
    ) {
        init(currentAccount, makeRequest, null, null, null, done, sheet, progress, new RequestScope(cleanup));
    }

    public static <Result extends TLObject> void open(
        int currentAccount,
        Utilities.Callback2<TLRPC.InputCheckPasswordSRP, Utilities.Callback<TLMethod<Result>>> makeRequest,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done,
        boolean sheet, boolean progress, Runnable cleanup
    ) {
        init(currentAccount, null, makeRequest, null, null, done, sheet, progress, new RequestScope(cleanup));
    }

    private static final class RequestScope {
        final Runnable cleanup;
        boolean finished;
        boolean delivering;
        RequestScope(Runnable cleanup) { this.cleanup = cleanup; }
        void close() {
            if (finished) return;
            finished = true;
            if (cleanup != null) cleanup.run();
        }
        TwoStepVerificationActivity passwordFragment() {
            return new TwoStepVerificationActivity() {
                @Override public void onFragmentDestroy() {
                    if (cleanup != null && !delivering) RequestScope.this.close();
                    super.onFragmentDestroy();
                }
            };
        }
    }

    private static <Result extends TLObject> void init(
        int currentAccount,
        Utilities.CallbackReturn<TLRPC.InputCheckPasswordSRP, TLMethod<Result>> makeRequestReturn,
        Utilities.Callback2<TLRPC.InputCheckPasswordSRP, Utilities.Callback<TLMethod<Result>>> makeRequestCallback,
        TLRPC.InputCheckPasswordSRP password,
        TwoStepVerificationActivity passwordFragment,
        Utilities.Callback3<Result, TLRPC.TL_error, TLRPC.InputCheckPasswordSRP> done,
        boolean sheet,
        boolean progress,
        RequestScope scope
    ) {
        if (scope.finished) return;
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment == null || lastFragment.getContext() == null) { scope.close(); return; }
        final Activity parentActivity = lastFragment.getParentActivity();
        final TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (parentActivity == null || currentUser == null) { scope.close(); return; }

        final AlertDialog progressDialog = new AlertDialog(lastFragment.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        if (progress) progressDialog.showDelayed(250);

        final Utilities.Callback<TLMethod<Result>> send = request -> {
            if (scope.finished || request == null) {
                progressDialog.dismiss();
                scope.close();
                return;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(request, AndroidUtilities::runOnUIThread, (response, error) -> {
                progressDialog.dismiss();
                if (scope.finished) return;
                if (error != null) {
                    if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                        if (passwordFragment != null) {
                            passwordFragment.needHideProgress();
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(new TL_account.getPassword(), AndroidUtilities::runOnUIThread, (response2, error2) -> {
                            if (scope.finished) return;
                            if (error2 == null) {
                                TwoStepVerificationActivity passwordFragment2 = scope.passwordFragment();
                                passwordFragment2.setCurrentAccount(currentAccount);
                                passwordFragment2.setDelegate(1, enteredPassword -> init(currentAccount, makeRequestReturn, makeRequestCallback, enteredPassword, passwordFragment2, done, sheet, progress, scope));
                                passwordFragment2.setPassword(response2);
                                final BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                if (lastFragment2 == null || lastFragment2.getContext() == null) { scope.close(); return; }
                                if (sheet) {
                                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                    params.transitionFromLeft = true;
                                    params.allowNestedScroll = false;
                                    lastFragment2.showAsSheet(passwordFragment2, params);
                                } else {
                                    lastFragment2.presentFragment(passwordFragment2);
                                }
                            } else {
                                scope.close();
                                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                builder.setTitle(LocaleController.getString(R.string.EditAdminTransferAlertTitle));

                                LinearLayout linearLayout = new LinearLayout(parentActivity);
                                linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                                linearLayout.setOrientation(LinearLayout.VERTICAL);
                                builder.setView(linearLayout);

                                TextView messageTextView = new TextView(parentActivity);
                                messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                                messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.WithdrawChannelAlertText)));
                                linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                                LinearLayout linearLayout2 = new LinearLayout(parentActivity);
                                linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                                linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                                ImageView dotImageView = new ImageView(parentActivity);
                                dotImageView.setImageResource(R.drawable.list_circle);
                                dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                                dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                                messageTextView = new TextView(parentActivity);
                                messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                                messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText1)));
                                if (LocaleController.isRTL) {
                                    linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                                    linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                                } else {
                                    linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                                    linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                                }

                                linearLayout2 = new LinearLayout(parentActivity);
                                linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                                linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                                dotImageView = new ImageView(parentActivity);
                                dotImageView.setImageResource(R.drawable.list_circle);
                                dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                                dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                                messageTextView = new TextView(parentActivity);
                                messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                                messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText2)));
                                if (LocaleController.isRTL) {
                                    linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                                    linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                                } else {
                                    linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                                    linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                                }

                                if ("PASSWORD_MISSING".equals(error.text)) {
                                    builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> lastFragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                                } else {
                                    messageTextView = new TextView(parentActivity);
                                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                                    messageTextView.setText(LocaleController.getString(R.string.EditAdminTransferAlertText3));
                                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                                    builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                                }
                                if (passwordFragment != null) {
                                    passwordFragment.showDialog(builder.create());
                                } else {
                                    lastFragment.showDialog(builder.create());
                                }
                            }
                        }, ConnectionsManager.RequestFlagWithoutLogin);
                    } else if ("SRP_ID_INVALID".equals(error.text)) {
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(new TL_account.getPassword(), AndroidUtilities::runOnUIThread, (response2, error2) -> {
                            if (scope.finished) return;
                            if (error2 == null) {
                                TwoStepVerificationActivity fragment = passwordFragment;
                                if (fragment == null) {
                                    fragment = scope.passwordFragment();
                                    final TwoStepVerificationActivity newFragment = fragment;
                                    fragment.setCurrentAccount(currentAccount);
                                    fragment.setDelegate(1, password2 -> init(currentAccount, makeRequestReturn, makeRequestCallback, password2, newFragment, done, sheet, progress, scope));
                                    fragment.preload(() -> {
                                        final BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                        if (scope.finished) return;
                                        if (lastFragment2 == null) { scope.close(); return; }
                                        if (sheet) {
                                            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                            params.transitionFromLeft = true;
                                            params.allowNestedScroll = false;
                                            lastFragment2.showAsSheet(newFragment, params);
                                        } else {
                                            lastFragment2.presentFragment(newFragment);
                                        }
                                    });
                                }
                                TL_account.Password currentPassword = (TL_account.Password) response2;
                                fragment.setCurrentPasswordInfo(null, currentPassword);
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                init(currentAccount, makeRequestReturn, makeRequestCallback, fragment.getNewSrpPassword(), fragment, done, sheet, progress, scope);
                            } else {
                                scope.close();
                            }
                        }, ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        scope.delivering = true;
                        try {
                            if (passwordFragment != null) {
                                passwordFragment.needHideProgress();
                                passwordFragment.finishFragment();
                            }
                            done.run(response, error, password);
                        } finally { scope.close(); }
                    }
                } else {
                    scope.delivering = true;
                    try {
                        if (passwordFragment != null) {
                            passwordFragment.needHideProgress();
                            passwordFragment.finishFragment();
                        }
                        done.run(response, error, password);
                    } finally { scope.close(); }
                }
            });
        };
        if (makeRequestReturn != null) {
            send.run(makeRequestReturn.run(password));
        } else if (makeRequestCallback != null) {
            makeRequestCallback.run(password, send);
        }
    }

}
