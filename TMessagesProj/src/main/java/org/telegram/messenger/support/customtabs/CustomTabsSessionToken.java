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
 * limitations under the License.
 */

package org.telegram.messenger.support.customtabs;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.BundleCompat;
import android.util.Log;

/**
 * Wrapper class that can be used as a unique identifier for a session. Also contains an accessor
 * for the {@link CustomTabsCallback} for the session if there was any.
 */
public class CustomTabsSessionToken {
    private static final String TAG = "CustomTabsSessionToken";
    private final ICustomTabsCallback mCallbackBinder;
    private final CustomTabsCallback mCallback;

    public static CustomTabsSessionToken getSessionTokenFromIntent(Intent intent) {
        Bundle b = intent.getExtras();
        IBinder binder = BundleCompat.getBinder(b, "android.support.customtabs.extra.SESSION");
        return binder == null ? null : new CustomTabsSessionToken(ICustomTabsCallback.Stub.asInterface(binder));
    }

    CustomTabsSessionToken(ICustomTabsCallback callbackBinder) {
        this.mCallbackBinder = callbackBinder;
        this.mCallback = new CustomTabsCallback() {
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
                try {
                    CustomTabsSessionToken.this.mCallbackBinder.onNavigationEvent(navigationEvent, extras);
                } catch (RemoteException var4) {
                    Log.e("CustomTabsSessionToken", "RemoteException during ICustomTabsCallback transaction");
                }

            }
        };
    }

    IBinder getCallbackBinder() {
        return this.mCallbackBinder.asBinder();
    }

    public int hashCode() {
        return this.getCallbackBinder().hashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof CustomTabsSessionToken)) {
            return false;
        } else {
            CustomTabsSessionToken token = (CustomTabsSessionToken) o;
            return token.getCallbackBinder().equals(this.mCallbackBinder.asBinder());
        }
    }

    public CustomTabsCallback getCallback() {
        return this.mCallback;
    }
}