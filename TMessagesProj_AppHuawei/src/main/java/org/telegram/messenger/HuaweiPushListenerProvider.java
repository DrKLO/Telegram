package org.telegram.messenger;

import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.text.TextUtils;

import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;

public class HuaweiPushListenerProvider implements PushListenerController.IPushListenerServiceProvider {
    public final static HuaweiPushListenerProvider INSTANCE = new HuaweiPushListenerProvider();

    private Boolean hasServices;

    private HuaweiPushListenerProvider() {}

    @Override
    public boolean hasServices() {
        if (hasServices == null) {
            try {
                ApplicationLoader.applicationContext.getPackageManager().getPackageInfo("com.huawei.hwid", 0);
                hasServices = true;
            } catch (PackageManager.NameNotFoundException ignored) {
                hasServices = false;
            } catch (Exception e) {
                FileLog.e(e);
                hasServices = false;
            }
        }
        return hasServices;
    }

    @Override
    public String getLogTitle() {
        return "HMS Core";
    }

    @Override
    public void onRequestPushToken() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String token = HmsInstanceId.getInstance(ApplicationLoader.applicationContext).getToken(BuildVars.HUAWEI_APP_ID, "HCM");
                SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();

                if (!TextUtils.isEmpty(token)) {
                    PushListenerController.sendRegistrationToServer(getPushType(), token);
                }
            } catch (ApiException e) {
                FileLog.e(e);

                SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();

                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Failed to get regid");
                }
                SharedConfig.pushStringStatus = "__HUAWEI_FAILED__";
                PushListenerController.sendRegistrationToServer(getPushType(), null);
            }
        });
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_HUAWEI;
    }
}
