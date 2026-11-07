package org.telegram.ui.Wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.hardware.fingerprint.FingerprintManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owns platform callbacks directly; never replaces AndroidX Biometric's activity-wide callback. */
public final class GramWalletStorageAuth {
    // Reserved wallet result range. Distinct codes prevent a late result from a timed-out
    // credential screen from completing a subsequent request on the same activity.
    private static final int CREDENTIAL_FIRST = 0x5740, CREDENTIAL_LAST = 0x577f;
    private static int nextCredentialCode = CREDENTIAL_FIRST;
    private static Request active; // Main thread only.
    interface Current { boolean get(); }

    /** Called on the storage worker, never the main thread. All UI state lives on the main thread. */
    static String authenticate(String walletId, boolean credentialOnly, Current isCurrent) throws InterruptedException {
        Request request = new Request();
        request.walletId = walletId;
        AndroidUtilities.runOnUIThread(() -> {
            if (request.expired) return;
            try {
                if (!isCurrent.get()) { request.finish("STORAGE_CANCELED"); return; }
                active = request;
                LaunchActivity activity = LaunchActivity.instance;
                if (Build.VERSION.SDK_INT < 23 || activity == null || activity.isFinishing()
                        || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) {
                    request.finish("AUTH_UNAVAILABLE");
                    return;
                }
                request.activity = activity;
                request.start(credentialOnly);
            } catch (Exception e) {
                FileLog.e(e);
                request.finish("AUTH_UNAVAILABLE");
            }
        });
        try {
            if (!request.latch.await(90, TimeUnit.SECONDS)) return "AUTH_TIMEOUT";
            return request.error;
        } finally {
            request.expired = true;
            AndroidUtilities.runOnUIThread(() -> {
                request.finish("AUTH_CANCELED");
                request.stopBiometrics();
                if (active == request) active = null;
            });
        }
    }

    static void cancel(String walletId) {
        AndroidUtilities.runOnUIThread(() -> {
            if (active != null && walletId.equals(active.walletId)) {
                active.finish("STORAGE_CANCELED");
                active.stopBiometrics();
            }
        });
    }

    /** Called by LaunchActivity before dispatching activity results to its screens. */
    public static boolean onActivityResult(Activity activity, int requestCode, int resultCode) {
        if (requestCode < CREDENTIAL_FIRST || requestCode > CREDENTIAL_LAST) return false;
        if (active != null && active.activity == activity && active.credentialStarted && active.credentialCode == requestCode) {
            active.finish(resultCode == Activity.RESULT_OK ? null : "AUTH_CANCELED");
        }
        return true;
    }

    public static void onActivityStopped(Activity activity) {
        if (active != null && active.activity == activity && Build.VERSION.SDK_INT < 30 && !active.credentialStarted) {
            active.finish("AUTH_CANCELED");
            active.stopBiometrics();
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        if (active != null && active.activity == activity) {
            active.finish("AUTH_CANCELED");
            active.stopBiometrics();
        }
    }

    private static final class Request {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean expired;
        String error;
        String walletId;
        LaunchActivity activity;
        boolean credentialStarted;
        int credentialCode;
        CancellationSignal cancellation;
        AlertDialog fingerprintDialog;

        void finish(String error) {
            if (latch.getCount() == 0) return;
            this.error = error;
            latch.countDown();
        }

        void start(boolean credentialOnly) {
            try {
                if (credentialOnly) credentials();
                else if (Build.VERSION.SDK_INT >= 30) modernBiometrics();
                else fingerprints();
            } catch (Exception e) { FileLog.e(e); credentials(); }
        }

        @androidx.annotation.RequiresApi(30)
        private void modernBiometrics() {
            cancellation = new CancellationSignal();
            new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.WalletUnlock))
                    .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticate(cancellation, activity.getMainExecutor(),
                            new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) { finish(null); }
                                @Override public void onAuthenticationError(int code, CharSequence message) {
                                    if (!waiting() || credentialStarted) return;
                                    if (code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                            || code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED) finish("AUTH_CANCELED");
                                    else credentials();
                                }
                            });
        }

        // On M-Q, own the FingerprintManager callback rather than sharing AndroidX 1.1.0's
        // activity ViewModel with BotBiometry. No Cipher crosses the framework/OEM boundary.
        @SuppressWarnings("deprecation")
        private void fingerprints() {
            FingerprintManager manager = (FingerprintManager) activity.getSystemService(Context.FINGERPRINT_SERVICE);
            if (manager == null || !manager.isHardwareDetected() || !manager.hasEnrolledFingerprints()) {
                credentials();
                return;
            }
            cancellation = new CancellationSignal();
            fingerprintDialog = new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.WalletUnlock))
                    .setMessage(LocaleController.getString(R.string.WalletTouchFingerprint))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> finish("AUTH_CANCELED"))
                    .setPositiveButton(LocaleController.getString(R.string.WalletUseDevicePasscode), (dialog, which) -> credentials())
                    .setOnCancelListener(dialog -> finish("AUTH_CANCELED"))
                    .show();
            manager.authenticate(null, cancellation, 0, new FingerprintManager.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) { if (!credentialStarted) finish(null); }
                @Override public void onAuthenticationFailed() {
                    if (waiting() && fingerprintDialog != null) fingerprintDialog.setMessage(LocaleController.getString(R.string.WalletFingerprintRetry));
                }
                @Override public void onAuthenticationHelp(int code, CharSequence message) {
                    if (waiting() && fingerprintDialog != null) fingerprintDialog.setMessage(message);
                }
                @Override public void onAuthenticationError(int code, CharSequence message) {
                    if (!waiting() || credentialStarted) return;
                    if (code == FingerprintManager.FINGERPRINT_ERROR_CANCELED) finish("AUTH_CANCELED");
                    else credentials();
                }
            }, new Handler(Looper.getMainLooper()));
        }

        private boolean waiting() { return !expired && latch.getCount() != 0; }

        @SuppressWarnings("deprecation")
        private void credentials() {
            if (credentialStarted || !waiting()) return;
            credentialStarted = true;
            stopBiometrics();
            try {
                KeyguardManager manager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
                Intent intent = manager == null ? null : manager.createConfirmDeviceCredentialIntent(
                        LocaleController.getString(R.string.WalletUnlock), null);
                if (intent == null) { finish("AUTH_UNAVAILABLE"); return; }
                credentialCode = nextCredentialCode++;
                if (nextCredentialCode > CREDENTIAL_LAST) nextCredentialCode = CREDENTIAL_FIRST;
                activity.startActivityForResult(intent, credentialCode);
            } catch (Exception e) { FileLog.e(e); finish("AUTH_UNAVAILABLE"); }
        }

        private void stopBiometrics() {
            try { if (cancellation != null) cancellation.cancel(); } catch (Exception e) { FileLog.e(e); }
            try { if (fingerprintDialog != null) fingerprintDialog.dismiss(); } catch (Exception e) { FileLog.e(e); }
            fingerprintDialog = null;
        }

    }
}
