package org.telegram.messenger.voip;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.VoIPHelper;

public final class VoIPPendingCall {

    public static VoIPPendingCall startOrSchedule(Activity activity, int userId, boolean video) {
        return new VoIPPendingCall(activity, userId, video, 1000);
    }

    private final NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.didUpdateConnectionState) {
                onConnectionStateUpdated(account, false);
            }
        }
    };

    private final Runnable releaseRunnable = () -> onConnectionStateUpdated(UserConfig.selectedAccount, true);

    private final int userId;
    private final boolean video;
    private final Activity activity;

    private Handler handler;
    private NotificationCenter notificationCenter;
    private boolean released;

    private VoIPPendingCall(Activity activity, int userId, boolean video, long expirationTime) {
        this.activity = activity;
        this.userId = userId;
        this.video = video;
        if (!onConnectionStateUpdated(UserConfig.selectedAccount, false)) {
            notificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount);
            notificationCenter.addObserver(observer, NotificationCenter.didUpdateConnectionState);
            handler = new Handler(Looper.myLooper());
            handler.postDelayed(releaseRunnable, expirationTime);
        }
    }

    private boolean onConnectionStateUpdated(int account, boolean force) {
        if (!released && (force || isConnected(account) || isAirplaneMode())) {
            final MessagesController messagesController = MessagesController.getInstance(account);
            final TLRPC.User user = messagesController.getUser(userId);
            if (user != null) {
                final TLRPC.UserFull userFull = messagesController.getUserFull(user.id);
                VoIPHelper.startCall(user, video, userFull != null && userFull.video_calls_available, activity, userFull);
            } else if (isAirplaneMode()) {
                VoIPHelper.startCall(null, video, false, activity, null);
            }
            release();
            return true;
        }
        return false;
    }

    private boolean isConnected(int account) {
        return ConnectionsManager.getInstance(account).getConnectionState() == ConnectionsManager.ConnectionStateConnected;
    }

    private boolean isAirplaneMode() {
        return Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    public void release() {
        if (!released) {
            if (notificationCenter != null) {
                notificationCenter.removeObserver(observer, NotificationCenter.didUpdateConnectionState);
            }
            if (handler != null) {
                handler.removeCallbacks(releaseRunnable);
            }
            released = true;
        }
    }
}
