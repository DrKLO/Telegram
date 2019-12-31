package org.telegram.ui.Components;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import javax.crypto.Cipher;

public class BiometricPromtHelper {

    private BaseFragment parentFragment;
    private CancellationSignal cancellationSignal;
    private BottomSheet bottomSheet;
    private ImageView iconImageView;
    private Button negativeButton;
    private TextView errorTextView;

    private int currentState;

    private static final int STATE_IDLE = 0;
    private static final int STATE_AUTHENTICATING = 1;
    private static final int STATE_ERROR = 2;
    private static final int STATE_PENDING_CONFIRMATION = 3;
    private static final int STATE_AUTHENTICATED = 4;

    private Runnable resetRunnable = this::handleResetMessage;

    public interface ContinueCallback {
        void run(boolean useBiometric);
    }

    public interface CipherCallback {
        void run(Cipher cipher);
    }

    public BiometricPromtHelper(BaseFragment fragment) {
        parentFragment = fragment;
    }

    public void promtWithCipher(Cipher cipher, String text, CipherCallback callback) {
        promtWithCipher(cipher, text, callback, shouldUseFingerprintForCrypto());
    }

    private void promtWithCipher(Cipher cipher, String text, CipherCallback callback, boolean forceFingerprint) {
        if (cipher == null || callback == null || parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        Activity activity = parentFragment.getParentActivity();
        if (Build.VERSION.SDK_INT >= 28 && !forceFingerprint) {
            cancellationSignal = new CancellationSignal();
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity);
            builder.setTitle(LocaleController.getString("Wallet", R.string.Wallet));
            builder.setDescription(text);
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), activity.getMainExecutor(), (dialog, which) -> {
            });
            builder.build().authenticate(new BiometricPrompt.CryptoObject(cipher), cancellationSignal, activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT) {
                        AlertsCreator.showSimpleAlert(parentFragment, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletBiometricTooManyAttempts", R.string.WalletBiometricTooManyAttempts));
                    } else if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS) {
                        promtWithCipher(cipher, text, callback, true);
                    }
                }

                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) {

                }

                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    callback.run(result.getCryptoObject().getCipher());
                }

                @Override
                public void onAuthenticationFailed() {

                }
            });
        } else if (Build.VERSION.SDK_INT >= 23) {
            cancellationSignal = new CancellationSignal();

            Context context = parentFragment.getParentActivity();
            BottomSheet.Builder builder = new BottomSheet.Builder(context);
            builder.setUseFullWidth(false);
            builder.setApplyTopPadding(false);
            builder.setApplyBottomPadding(false);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 4, 4, 4));
            builder.setCustomView(linearLayout);

            TextView titleTextView = new TextView(context);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleTextView.setText(LocaleController.getString("Wallet", R.string.Wallet));
            linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 24, 24, 0));

            TextView descriptionTextView = new TextView(context);
            descriptionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            descriptionTextView.setText(text);
            descriptionTextView.setPadding(0, AndroidUtilities.dp(8), 0, 0);
            linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 0));

            iconImageView = new ImageView(context);
            iconImageView.setScaleType(ImageView.ScaleType.FIT_XY);
            linearLayout.addView(iconImageView, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));

            errorTextView = new TextView(context);
            errorTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            errorTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            errorTextView.setText(LocaleController.getString("Wallet", R.string.Wallet));
            errorTextView.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(24));
            linearLayout.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 0));

            negativeButton = new Button(context);
            negativeButton.setGravity(Gravity.CENTER);
            negativeButton.setTextColor(Theme.getColor(Theme.key_dialogButton));
            negativeButton.setText(LocaleController.getString("Cancel", R.string.Cancel));
            negativeButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            negativeButton.setSingleLine(true);
            negativeButton.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            negativeButton.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(Theme.getColor(Theme.key_dialogButton)));
            linearLayout.addView(negativeButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 14, 42, 0, 14));
            negativeButton.setOnClickListener(v -> builder.getDismissRunnable().run());

            parentFragment.showDialog(bottomSheet = builder.create(), dialog -> {
                if (cancellationSignal != null) {
                    cancellationSignal.cancel();
                    cancellationSignal = null;
                }
                bottomSheet = null;
            });

            FingerprintManager fingerprintManager = getFingerprintManagerOrNull();
            if (fingerprintManager != null) {
                fingerprintManager.authenticate(new FingerprintManager.CryptoObject(cipher), cancellationSignal, 0, new FingerprintManager.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        if (errorCode == FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED) {
                            bottomSheet.dismiss();
                        } else {
                            updateState(STATE_ERROR);
                            showTemporaryMessage(errString);
                        }
                    }

                    @Override
                    public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                        updateState(STATE_ERROR);
                        showTemporaryMessage(helpString);
                    }

                    @Override
                    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                        builder.getDismissRunnable().run();
                        callback.run(result.getCryptoObject().getCipher());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        updateState(STATE_ERROR);
                        showTemporaryMessage(LocaleController.getString("WalletTouchFingerprintNotRecognized", R.string.WalletTouchFingerprintNotRecognized));
                    }
                }, null);
            }

            updateState(STATE_AUTHENTICATING);
            errorTextView.setText(LocaleController.getString("WalletTouchFingerprint", R.string.WalletTouchFingerprint));
            errorTextView.setVisibility(View.VISIBLE);
        }
    }

    private void updateState(int newState) {
        if (newState == STATE_PENDING_CONFIRMATION) {
            AndroidUtilities.cancelRunOnUIThread(resetRunnable);
            errorTextView.setVisibility(View.INVISIBLE);
        } else if (newState == STATE_AUTHENTICATED) {
            negativeButton.setVisibility(View.GONE);
            errorTextView.setVisibility(View.INVISIBLE);
        }

        updateIcon(currentState, newState);
        currentState = newState;
    }

    private void showTemporaryMessage(CharSequence message) {
        AndroidUtilities.cancelRunOnUIThread(resetRunnable);
        errorTextView.setText(message);
        errorTextView.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        errorTextView.setContentDescription(message);
        AndroidUtilities.runOnUIThread(resetRunnable, 2000);
    }

    private void handleResetMessage() {
        if (errorTextView == null) {
            return;
        }
        updateState(STATE_AUTHENTICATING);
        errorTextView.setText(LocaleController.getString("WalletTouchFingerprint", R.string.WalletTouchFingerprint));
        errorTextView.setTextColor(Theme.getColor(Theme.key_dialogButton));
    }

    private void updateIcon(int lastState, int newState) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        final Drawable icon = getAnimationForTransition(lastState, newState);
        if (icon == null) {
            return;
        }

        final AnimatedVectorDrawable animation = icon instanceof AnimatedVectorDrawable ? (AnimatedVectorDrawable) icon : null;

        iconImageView.setImageDrawable(icon);

        if (animation != null && shouldAnimateForTransition(lastState, newState)) {
            animation.start();
        }
    }

    private boolean shouldAnimateForTransition(int oldState, int newState) {
        if (newState == STATE_ERROR) {
            return true;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATING) {
            return true;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_AUTHENTICATED) {
            return false;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATED) {
            return false;
        } else if (newState == STATE_AUTHENTICATING) {
            return false;
        }
        return false;
    }

    private Drawable getAnimationForTransition(int oldState, int newState) {
        if (parentFragment == null || parentFragment.getParentActivity() == null || Build.VERSION.SDK_INT < 21) {
            return null;
        }
        int iconRes;
        if (newState == STATE_ERROR) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATING) {
            iconRes = R.drawable.fingerprint_dialog_error_to_fp;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_AUTHENTICATED) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATED) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (newState == STATE_AUTHENTICATING) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else {
            return null;
        }
        return parentFragment.getParentActivity().getDrawable(iconRes);
    }

    public void onPause() {
        if (bottomSheet != null) {
            bottomSheet.dismiss();
            bottomSheet = null;
        }
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    public void cancelPromt() {
        cancellationSignal.cancel();
        cancellationSignal = null;
    }

    private static FingerprintManager getFingerprintManagerOrNull() {
        if (Build.VERSION.SDK_INT == 23) {
            return ApplicationLoader.applicationContext.getSystemService(FingerprintManager.class);
        } else if (Build.VERSION.SDK_INT > 23 && ApplicationLoader.applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return ApplicationLoader.applicationContext.getSystemService(FingerprintManager.class);
        } else {
            return null;
        }
    }

    public static boolean hasBiometricEnrolled() {
        if (Build.VERSION.SDK_INT >= 29 && !shouldUseFingerprintForCrypto()) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(android.hardware.biometrics.BiometricManager.class);
            return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        } else if (Build.VERSION.SDK_INT >= 23) {
            FingerprintManager fingerprintManager = getFingerprintManagerOrNull();
            return fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
        }
        return false;
    }

    public static boolean canAddBiometric() {
        if (Build.VERSION.SDK_INT >= 29 && !shouldUseFingerprintForCrypto()) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(android.hardware.biometrics.BiometricManager.class);
            return biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
        }
        return hasFingerprintHardware();
    }

    public static boolean hasFingerprintHardware() {
        if (Build.VERSION.SDK_INT >= 23) {
            FingerprintManager fingerprintManager = getFingerprintManagerOrNull();
            return fingerprintManager != null && fingerprintManager.isHardwareDetected();
        }
        return false;
    }

    public static boolean hasLockscreenProtected() {
        if (Build.VERSION.SDK_INT >= 23) {
            KeyguardManager keyguardManager = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
            return keyguardManager.isDeviceSecure();
        }
        return false;
    }

    public static void askForBiometric(BaseFragment fragment, ContinueCallback callback, String continueButton) {
        if (fragment == null || fragment.getParentActivity() == null || callback == null) {
            return;
        }
        boolean hasBiometric = hasBiometricEnrolled();
        if (hasBiometric || Build.VERSION.SDK_INT < 23 || hasLockscreenProtected() && !hasFingerprintHardware()) {
            callback.run(hasBiometric);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(LocaleController.getString("WalletSecurityAlertTitle", R.string.WalletSecurityAlertTitle));
            boolean biometricOnly;
            if (hasLockscreenProtected()) {
                builder.setMessage(LocaleController.getString("WalletSecurityAlertTextBiometric", R.string.WalletSecurityAlertTextBiometric));
                biometricOnly = true;
            } else if (hasFingerprintHardware()) {
                builder.setMessage(LocaleController.getString("WalletSecurityAlertTextLockscreenBiometric", R.string.WalletSecurityAlertTextLockscreenBiometric));
                biometricOnly = false;
            } else {
                builder.setMessage(LocaleController.getString("WalletSecurityAlertTextLockscreen", R.string.WalletSecurityAlertTextLockscreen));
                biometricOnly = false;
            }
            builder.setPositiveButton(LocaleController.getString("WalletSecurityAlertSetup", R.string.WalletSecurityAlertSetup), (dialog, which) -> {
                try {
                    if (biometricOnly && Build.VERSION.SDK_INT >= 28) {
                        fragment.getParentActivity().startActivity(new Intent(Settings.ACTION_FINGERPRINT_ENROLL));
                    } else {
                        fragment.getParentActivity().startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            builder.setNegativeButton(continueButton, (dialog, which) -> callback.run(false));
            fragment.showDialog(builder.create());
        }
    }

    final static String[] badBiometricModels = new String[]{
            "SM-G95",
            "SM-G96",
            "SM-G97",
            "SM-N95",
            "SM-N96",
            "SM-N97",
            "SM-A20"
    };

    final static String[] hideBiometricModels = new String[]{
            "SM-G97",
            "SM-N97"
    };

    private static boolean shouldHideBiometric() {
        return isModelInList(Build.MODEL, hideBiometricModels);
    }

    private static boolean shouldUseFingerprintForCrypto() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            return false;
        }
        return isModelInList(Build.MODEL, badBiometricModels);
    }

    private static boolean isModelInList(String deviceModel, String[] list) {
        if (deviceModel == null) {
            return false;
        }
        for (int a = 0; a < list.length; a++) {
            if (deviceModel.startsWith(list[a])) {
                return true;
            }
        }
        return false;
    }
}
