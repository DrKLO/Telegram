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

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.support.v4.util.ArrayMap;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class CustomTabsService extends Service {
    public static final String ACTION_CUSTOM_TABS_CONNECTION = "android.support.customtabs.action.CustomTabsService";
    public static final String KEY_URL = "android.support.customtabs.otherurls.URL";
    private final Map<IBinder, DeathRecipient> mDeathRecipientMap = new ArrayMap();
    private ICustomTabsService.Stub mBinder = new ICustomTabsService.Stub() {
        public boolean warmup(long flags) {
            return CustomTabsService.this.warmup(flags);
        }

        public boolean newSession(ICustomTabsCallback callback) {
            final CustomTabsSessionToken sessionToken = new CustomTabsSessionToken(callback);

            try {
                DeathRecipient e = new DeathRecipient() {
                    public void binderDied() {
                        CustomTabsService.this.cleanUpSession(sessionToken);
                    }
                };
                synchronized (CustomTabsService.this.mDeathRecipientMap) {
                    callback.asBinder().linkToDeath(e, 0);
                    CustomTabsService.this.mDeathRecipientMap.put(callback.asBinder(), e);
                }

                return CustomTabsService.this.newSession(sessionToken);
            } catch (RemoteException var7) {
                return false;
            }
        }

        public boolean mayLaunchUrl(ICustomTabsCallback callback, Uri url, Bundle extras, List<Bundle> otherLikelyBundles) {
            return CustomTabsService.this.mayLaunchUrl(new CustomTabsSessionToken(callback), url, extras, otherLikelyBundles);
        }

        public Bundle extraCommand(String commandName, Bundle args) {
            return CustomTabsService.this.extraCommand(commandName, args);
        }

        public boolean updateVisuals(ICustomTabsCallback callback, Bundle bundle) {
            return CustomTabsService.this.updateVisuals(new CustomTabsSessionToken(callback), bundle);
        }
    };

    public CustomTabsService() {
    }

    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    protected boolean cleanUpSession(CustomTabsSessionToken sessionToken) {
        try {
            Map e = this.mDeathRecipientMap;
            synchronized (this.mDeathRecipientMap) {
                IBinder binder = sessionToken.getCallbackBinder();
                DeathRecipient deathRecipient = this.mDeathRecipientMap.get(binder);
                binder.unlinkToDeath(deathRecipient, 0);
                this.mDeathRecipientMap.remove(binder);
                return true;
            }
        } catch (NoSuchElementException var7) {
            return false;
        }
    }

    protected abstract boolean warmup(long var1);

    protected abstract boolean newSession(CustomTabsSessionToken var1);

    protected abstract boolean mayLaunchUrl(CustomTabsSessionToken var1, Uri var2, Bundle var3, List<Bundle> var4);

    protected abstract Bundle extraCommand(String var1, Bundle var2);

    protected abstract boolean updateVisuals(CustomTabsSessionToken var1, Bundle var2);
}
