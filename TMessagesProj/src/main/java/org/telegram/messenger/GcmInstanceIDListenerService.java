/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Intent;

import com.google.android.gms.iid.InstanceIDListenerService;

public class GcmInstanceIDListenerService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.postInitApplication();
                Intent intent = new Intent(ApplicationLoader.applicationContext, GcmRegistrationIntentService.class);
                startService(intent);
            }
        });
    }
}
