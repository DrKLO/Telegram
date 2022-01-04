package ua.itaysonlab.catogram.security;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class CGBiometricPrompt {
    private static BiometricPrompt.PromptInfo createPromptInfo() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(LocaleController.getString("CG_AppName", R.string.CG_AppName))
                .setSubtitle(LocaleController.getString("CG_Biometric_Subtitle", R.string.CG_Biometric_Subtitle))
                .setConfirmationRequired(false)
                .setNegativeButtonText(LocaleController.getString("CG_Biometric_Negative", R.string.CG_Biometric_Negative))
                .build();
    }

    public static void callBiometricPrompt(AppCompatActivity activity, CGBiometricListener listener) {
        new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                listener.onError(errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                listener.onSuccess(result);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                listener.onFailed();
            }
        }).authenticate(createPromptInfo());
    }

    public interface CGBiometricListener {
        void onError(CharSequence msg);

        void onFailed();

        void onSuccess(BiometricPrompt.AuthenticationResult result);
    }
}
