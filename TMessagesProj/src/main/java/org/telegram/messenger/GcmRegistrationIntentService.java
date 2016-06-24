/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

public class GcmRegistrationIntentService extends IntentService {

    public GcmRegistrationIntentService() {
        super("GcmRegistrationIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            InstanceID instanceID = InstanceID.getInstance(this);
            final String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            FileLog.d("tmessages", "GCM Registration Token: " + token);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    ApplicationLoader.postInitApplication();
                    sendRegistrationToServer(token);
                }
            });
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            final int failCount = intent.getIntExtra("failCount", 0);
            if (failCount < 60) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(ApplicationLoader.applicationContext, GcmRegistrationIntentService.class);
                            intent.putExtra("failCount", failCount + 1);
                            startService(intent);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }, failCount < 20 ? 10000 : 60000 * 30);
            }
        }
    }

    private void sendRegistrationToServer(final String token) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                UserConfig.pushString = token;
                UserConfig.registeredForPush = false;
                UserConfig.saveConfig(false);
                if (UserConfig.getClientUserId() != 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance().registerForPush(token);
                        }
                    });
                }
            }
        });
    }
}