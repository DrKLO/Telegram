/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.telegram.messenger.support.fingerprint;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Handler;

import org.telegram.messenger.FileLog;

import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

@TargetApi(23)
public final class FingerprintManagerCompatApi23 {

    private static FingerprintManager getFingerprintManager(Context ctx) {
        return (FingerprintManager) ctx.getSystemService(Context.FINGERPRINT_SERVICE);
    }

    public static boolean hasEnrolledFingerprints(Context context) {
        try {
            FingerprintManager fingerprintManager = getFingerprintManager(context);
            if (fingerprintManager == null) {
                return false;
            }
            return fingerprintManager.hasEnrolledFingerprints();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isHardwareDetected(Context context) {
        try {
            FingerprintManager fingerprintManager = getFingerprintManager(context);
            if (fingerprintManager == null) {
                return false;
            }
            return fingerprintManager.isHardwareDetected();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static void authenticate(Context context, CryptoObject crypto, int flags, Object cancel,
                                    AuthenticationCallback callback, Handler handler) {
        try {
            getFingerprintManager(context).authenticate(wrapCryptoObject(crypto),
                    (android.os.CancellationSignal) cancel, flags,
                    wrapCallback(callback), handler);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static FingerprintManager.CryptoObject wrapCryptoObject(CryptoObject cryptoObject) {
        if (cryptoObject == null) {
            return null;
        } else if (cryptoObject.getCipher() != null) {
            return new FingerprintManager.CryptoObject(cryptoObject.getCipher());
        } else if (cryptoObject.getSignature() != null) {
            return new FingerprintManager.CryptoObject(cryptoObject.getSignature());
        } else if (cryptoObject.getMac() != null) {
            return new FingerprintManager.CryptoObject(cryptoObject.getMac());
        } else {
            return null;
        }
    }

    private static CryptoObject unwrapCryptoObject(FingerprintManager.CryptoObject cryptoObject) {
        if (cryptoObject == null) {
            return null;
        } else if (cryptoObject.getCipher() != null) {
            return new CryptoObject(cryptoObject.getCipher());
        } else if (cryptoObject.getSignature() != null) {
            return new CryptoObject(cryptoObject.getSignature());
        } else if (cryptoObject.getMac() != null) {
            return new CryptoObject(cryptoObject.getMac());
        } else {
            return null;
        }
    }

    private static FingerprintManager.AuthenticationCallback wrapCallback(
            final AuthenticationCallback callback) {
        return new FingerprintManager.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                callback.onAuthenticationError(errMsgId, errString);
            }

            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                callback.onAuthenticationHelp(helpMsgId, helpString);
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                callback.onAuthenticationSucceeded(new AuthenticationResultInternal(
                        unwrapCryptoObject(result.getCryptoObject())));
            }

            @Override
            public void onAuthenticationFailed() {
                callback.onAuthenticationFailed();
            }
        };
    }

    public static class CryptoObject {

        private final Signature mSignature;
        private final Cipher mCipher;
        private final Mac mMac;

        public CryptoObject(Signature signature) {
            mSignature = signature;
            mCipher = null;
            mMac = null;
        }

        public CryptoObject(Cipher cipher) {
            mCipher = cipher;
            mSignature = null;
            mMac = null;
        }

        public CryptoObject(Mac mac) {
            mMac = mac;
            mCipher = null;
            mSignature = null;
        }

        public Signature getSignature() {
            return mSignature;
        }

        public Cipher getCipher() {
            return mCipher;
        }

        public Mac getMac() {
            return mMac;
        }
    }

    public static final class AuthenticationResultInternal {
        private CryptoObject mCryptoObject;

        public AuthenticationResultInternal(CryptoObject crypto) {
            mCryptoObject = crypto;
        }

        public CryptoObject getCryptoObject() {
            return mCryptoObject;
        }
    }

    public static abstract class AuthenticationCallback {

        public void onAuthenticationError(int errMsgId, CharSequence errString) {
        }

        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        }

        public void onAuthenticationSucceeded(AuthenticationResultInternal result) {
        }

        public void onAuthenticationFailed() {
        }
    }
}
