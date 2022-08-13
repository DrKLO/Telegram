package org.telegram.messenger;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import java.util.Map;

public class HuaweiPushListenerService extends HmsMessageService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Utilities.globalQueue.postRunnable(() -> {
            String from = remoteMessage.getFrom();
            String data = remoteMessage.getData();
            long time = remoteMessage.getSentTime();

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("HCM received data: " + data + " from: " + from);
            }

            PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_HUAWEI, data, time);
        });
    }

    @Override
    public void onNewToken(String token) {
        AndroidUtilities.runOnUIThread(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Refreshed HCM token: " + token);
            }
            ApplicationLoader.postInitApplication();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_HUAWEI, token);
        });
    }
}
