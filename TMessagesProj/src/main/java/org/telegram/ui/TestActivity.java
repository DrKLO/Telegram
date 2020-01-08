/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import androidx.core.os.CancellationSignal;

@TargetApi(23)
public class TestActivity extends BaseFragment {

    private EditTextBoldCursor codeField;

    private KeyStore keyStore;
    private KeyPairGenerator keyPairGenerator;
    private String encryptedString;
    private Cipher cipher;

    private FingerprintHelper fingerprintHelper;

    private static final String KEY_NAME = "wallet_key11";

    @Override
    public boolean onFragmentCreate() {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        } catch (Exception exception) {
            FileLog.e(exception);
        }

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (encryptedString != null) {
            SharedPreferences preferences = MessagesController.getMainSettings(UserConfig.selectedAccount);
            preferences.edit().remove("test_enc").commit();
        }
    }

    public boolean createKeyPair() {
        try {
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_NAME, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setKeySize(2048);
            builder.setIsStrongBoxBacked(true);
            builder.setInvalidatedByBiometricEnrollment(true);
            builder.setUserAuthenticationRequired(true);

            keyPairGenerator.initialize(builder.build());
            keyPairGenerator.generateKeyPair();
            return true;
        } catch (InvalidAlgorithmParameterException e) {
            return false;
        }
    }

    private boolean isKeyCreated() {
        try {
            return keyStore.containsAlias(KEY_NAME) || createKeyPair();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private boolean initCipher(int mode) {
        try {
            switch (mode) {
                case Cipher.ENCRYPT_MODE: {
                    PublicKey key = keyStore.getCertificate(KEY_NAME).getPublicKey();
                    PublicKey unrestricted = KeyFactory.getInstance(key.getAlgorithm()).generatePublic(new X509EncodedKeySpec(key.getEncoded()));
                    OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
                    cipher.init(mode, unrestricted, spec);
                    break;
                }
                case Cipher.DECRYPT_MODE: {
                    PrivateKey key = (PrivateKey) keyStore.getKey(KEY_NAME, null);
                    OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
                    cipher.init(mode, key, spec);
                    break;
                }
                default:
                    return false;
            }
            return true;
        } catch (KeyPermanentlyInvalidatedException exception) {
            deleteInvalidKey();
        } catch (UnrecoverableKeyException e) {
            deleteInvalidKey();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private void deleteInvalidKey() {
        try {
            keyStore.deleteEntry(KEY_NAME);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private String encode(String inputString) {
        try {
            if (isKeyCreated() && initCipher(Cipher.ENCRYPT_MODE)) {
                byte[] bytes = cipher.doFinal(inputString.getBytes());
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static String decode(String encodedString, Cipher cipherDecrypter) {
        try {
            byte[] bytes = Base64.decode(encodedString, Base64.NO_WRAP);
            return new String(cipherDecrypter.doFinal(bytes));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Test");

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xffffffff);
        fragmentView = frameLayout;

        codeField = new EditTextBoldCursor(context);
        codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setCursorSize(AndroidUtilities.dp(20));
        codeField.setCursorWidth(1.5f);
        codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        codeField.setMaxLines(1);
        codeField.setPadding(0, 0, 0, 0);
        codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        frameLayout.addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 10, 20, 10, 0));

        Button button = new Button(context);
        button.setText("encrypt");
        frameLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 10, 80, 10, 0));
        button.setOnClickListener(v -> {
            String str = encode(codeField.getText().toString());
            if (str != null) {
                SharedPreferences preferences = MessagesController.getMainSettings(UserConfig.selectedAccount);
                preferences.edit().putString("test_enc", str).commit();
                Toast.makeText(getParentActivity(), "String encoded", Toast.LENGTH_SHORT).show();
                finishFragment();
            }
        });

        SharedPreferences preferences = MessagesController.getMainSettings(UserConfig.selectedAccount);
        encryptedString = preferences.getString("test_enc", null);
        if (encryptedString != null) {
            codeField.setText(encryptedString);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (encryptedString != null) {
            prepareSensor();
        }
    }

    private void prepareSensor() {
        isKeyCreated();
        initCipher(Cipher.DECRYPT_MODE);
        FingerprintManagerCompat.CryptoObject cryptoObject = new FingerprintManagerCompat.CryptoObject(cipher);

        if (cryptoObject != null) {
            Toast.makeText(getParentActivity(), "use fingerprint to login", Toast.LENGTH_LONG).show();
            fingerprintHelper = new FingerprintHelper(getParentActivity());
            fingerprintHelper.startAuth(cryptoObject);
        } else {
            Toast.makeText(getParentActivity(), "new fingerprint enrolled. enter pin again", Toast.LENGTH_SHORT).show();
        }
    }

    public class FingerprintHelper extends FingerprintManagerCompat.AuthenticationCallback {

        private Context mContext;
        private CancellationSignal mCancellationSignal;

        FingerprintHelper(Context context) {
            mContext = context;
        }

        void startAuth(FingerprintManagerCompat.CryptoObject cryptoObject) {
            mCancellationSignal = new CancellationSignal();
            FingerprintManagerCompat manager = FingerprintManagerCompat.from(mContext);
            manager.authenticate(cryptoObject, 0, mCancellationSignal, this, null);
        }

        void cancel() {
            if (mCancellationSignal != null) {
                mCancellationSignal.cancel();
            }
        }

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            Toast.makeText(mContext, errString, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            Toast.makeText(mContext, helpString, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
            Cipher cipher = result.getCryptoObject().getCipher();
            String decoded = decode(encryptedString, cipher);
            codeField.setText(decoded);
            Toast.makeText(mContext, "success", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onAuthenticationFailed() {
            Toast.makeText(mContext, "try again", Toast.LENGTH_SHORT).show();
        }
    }
}
