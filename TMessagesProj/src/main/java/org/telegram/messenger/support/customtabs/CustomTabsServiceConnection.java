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
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Abstract {@link ServiceConnection} to use while binding to a {@link CustomTabsService}. Any
 * client implementing this is responsible for handling changes related with the lifetime of the
 * connection like rebinding on disconnect.
 */
public abstract class CustomTabsServiceConnection implements ServiceConnection {

    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        onCustomTabsServiceConnected(name, new CustomTabsClient(
                ICustomTabsService.Stub.asInterface(service), name) {
        });
    }

    /**
     * Called when a connection to the {@link CustomTabsService} has been established.
     * @param name   The concrete component name of the service that has been connected.
     * @param client {@link CustomTabsClient} that contains the {@link IBinder} with which the
     *               connection have been established. All further communication should be initiated
     *               using this client.
     */
    public abstract void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client);
}
