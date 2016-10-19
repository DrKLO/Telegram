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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;

public class CustomTabsClient {
    private final ICustomTabsService mService;
    private final ComponentName mServiceComponentName;

    CustomTabsClient(ICustomTabsService service, ComponentName componentName) {
        this.mService = service;
        this.mServiceComponentName = componentName;
    }

    public static boolean bindCustomTabsService(Context context, String packageName, CustomTabsServiceConnection connection) {
        Intent intent = new Intent("android.support.customtabs.action.CustomTabsService");
        if (!TextUtils.isEmpty(packageName)) {
            intent.setPackage(packageName);
        }

        return context.bindService(intent, connection, 33);
    }

    public boolean warmup(long flags) {
        try {
            return this.mService.warmup(flags);
        } catch (RemoteException var4) {
            return false;
        }
    }

    public CustomTabsSession newSession(final CustomTabsCallback callback) {
        ICustomTabsCallback.Stub wrapper = new ICustomTabsCallback.Stub() {
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
                if (callback != null) {
                    callback.onNavigationEvent(navigationEvent, extras);
                }

            }

            public void extraCallback(String callbackName, Bundle args) throws RemoteException {
                if (callback != null) {
                    callback.extraCallback(callbackName, args);
                }

            }
        };

        try {
            if (!this.mService.newSession(wrapper)) {
                return null;
            }
        } catch (RemoteException var4) {
            return null;
        }

        return new CustomTabsSession(this.mService, wrapper, this.mServiceComponentName);
    }

    public Bundle extraCommand(String commandName, Bundle args) {
        try {
            return this.mService.extraCommand(commandName, args);
        } catch (RemoteException var4) {
            return null;
        }
    }
}
