package org.telegram.messenger.voip;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPFragment;
import org.telegram.ui.VoIPPermissionActivity;

import java.util.ArrayList;

public class VoIPPreNotificationService { // } extends Service implements AudioManager.OnAudioFocusChangeListener {

    public static final class State implements VoIPServiceState {

        private final int currentAccount;
        private final long userId;
        private final TL_phone.PhoneCall call;
        private boolean destroyed;

        public State(int currentAccount, long userId, TL_phone.PhoneCall phoneCall) {
            this.currentAccount = currentAccount;
            this.userId = userId;
            this.call = phoneCall;
        }

        @Override
        public TLRPC.User getUser() {
            return MessagesController.getInstance(currentAccount).getUser(userId);
        }

        @Override
        public boolean isOutgoing() {
            return false;
        }

        @Override
        public int getCallState() {
            return destroyed ? VoIPService.STATE_ENDED : VoIPService.STATE_WAITING_INCOMING;
        }

        @Override
        public TL_phone.PhoneCall getPrivateCall() {
            return call;
        }

        @Override
        public boolean isCallingVideo() {
            if (call != null) {
                return call.video;
            }
            return false;
        }

        @Override
        public void acceptIncomingCall() {
            answer(ApplicationLoader.applicationContext);
        }

        @Override
        public void declineIncomingCall() {
            decline(ApplicationLoader.applicationContext, VoIPService.DISCARD_REASON_HANGUP);
        }

        @Override
        public void stopRinging() {
            VoIPPreNotificationService.stopRinging();
        }

        public void destroy() {
            if (destroyed) return;
            destroyed = true;
            if (VoIPFragment.getInstance() != null) {
                VoIPFragment.getInstance().onStateChanged(getCallState());
            }
        }

        @Override
        public boolean isConference() {
            return false;
        }

        @Override
        public TLRPC.GroupCall getGroupCall() {
            return null;
        }

        @Override
        public ArrayList<TLRPC.GroupCallParticipant> getGroupParticipants() {
            return null;
        }
    }

    public static State getState() {
        return currentState;
    }

//    public static VoIPPreNotificationService instance;

    private static Notification makeNotification(Context context, int account, long user_id, long call_id, boolean video) {
        if (Build.VERSION.SDK_INT < 33) return null;

        final TLRPC.User user = MessagesController.getInstance(account).getUser(user_id);

        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        final Intent intent = new Intent(context, LaunchActivity.class).setAction("voip");
        final Notification.Builder builder = new Notification.Builder(context)
            .setContentTitle(LocaleController.getString(video ? R.string.VoipInVideoCallBranding : R.string.VoipInCallBranding))
            .setSmallIcon(R.drawable.ic_call)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
                )
            );

        SharedPreferences nprefs = MessagesController.getGlobalNotificationsSettings();
        int chanIndex = nprefs.getInt("calls_notification_channel", 0);
        NotificationChannel oldChannel = nm.getNotificationChannel("incoming_calls2" + chanIndex);
        if (oldChannel != null) {
            nm.deleteNotificationChannel(oldChannel.getId());
        }
        oldChannel = nm.getNotificationChannel("incoming_calls3" + chanIndex);
        if (oldChannel != null) {
            nm.deleteNotificationChannel(oldChannel.getId());
        }
        NotificationChannel existingChannel = nm.getNotificationChannel("incoming_calls4" + chanIndex);
        boolean needCreate = true;
        if (existingChannel != null) {
            if (existingChannel.getImportance() < NotificationManager.IMPORTANCE_HIGH || existingChannel.getSound() != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("User messed up the notification channel; deleting it and creating a proper one");
                }
                nm.deleteNotificationChannel("incoming_calls4" + chanIndex);
                chanIndex++;
                nprefs.edit().putInt("calls_notification_channel", chanIndex).commit();
            } else {
                needCreate = false;
            }
        }
        if (needCreate) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(AudioManager.STREAM_RING)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build();
            NotificationChannel chan = new NotificationChannel("incoming_calls4" + chanIndex, LocaleController.getString(R.string.IncomingCallsSystemSetting), NotificationManager.IMPORTANCE_HIGH);
            try {
                chan.setSound(null, attrs);
            } catch (Exception e) {
                FileLog.e(e);
            }
            chan.setDescription(LocaleController.getString(R.string.IncomingCallsSystemSettingDescription));
            chan.enableVibration(false);
            chan.enableLights(false);
            chan.setBypassDnd(true);
            try {
                nm.createNotificationChannel(chan);
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
        builder.setChannelId("incoming_calls4" + chanIndex);

        final Intent endIntent = new Intent(context, VoIPActionsReceiver.class);
        endIntent.setAction(context.getPackageName() + ".DECLINE_CALL");
        endIntent.putExtra("call_id", call_id);
        CharSequence endTitle = LocaleController.getString(R.string.VoipDeclineCall);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            endTitle = new SpannableString(endTitle);
            ((SpannableString) endTitle).setSpan(new ForegroundColorSpan(0xFFF44336), 0, endTitle.length(), 0);
        }
        final PendingIntent endPendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                endIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
            );

        final Intent answerIntent = new Intent(context, VoIPActionsReceiver.class);
        answerIntent.setAction(context.getPackageName() + ".ANSWER_CALL");
        answerIntent.putExtra("call_id", call_id);
        CharSequence answerTitle = LocaleController.getString(R.string.VoipAnswerCall);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            answerTitle = new SpannableString(answerTitle);
            ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
        }
        final PendingIntent answerPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                new Intent(context, LaunchActivity.class).setAction("voip_answer"),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
            );

        builder.setPriority(Notification.PRIORITY_MAX);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            builder.setShowWhen(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(0xff2ca5e0);
            builder.setVibrate(new long[0]);
            builder.setCategory(Notification.CATEGORY_CALL);
            builder.setFullScreenIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE), true);
            if (user != null && !TextUtils.isEmpty(user.phone)) {
                builder.addPerson("tel:" + user.phone);
            }
        }

        final Intent hideIntent = new Intent(ApplicationLoader.applicationContext, VoIPActionsReceiver.class);
        hideIntent.setAction(context.getPackageName() + ".HIDE_CALL");
        final PendingIntent hidePendingIntent =
            PendingIntent.getBroadcast(
                ApplicationLoader.applicationContext,
                0,
                hideIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
        builder.setDeleteIntent(hidePendingIntent);

        Bitmap avatar = VoIPService.getRoundAvatarBitmap(context, account, user);
        String personName = ContactsController.formatName(user);
        if (TextUtils.isEmpty(personName)) {
            //java.lang.IllegalArgumentException: person must have a non-empty a name
            personName = "___";
        }
        Person person = new Person.Builder()
                .setName(personName)
                .setIcon(Icon.createWithAdaptiveBitmap(avatar)).build();
        Notification.CallStyle notificationStyle = Notification.CallStyle.forIncomingCall(person, endPendingIntent, answerPendingIntent);

        builder.setStyle(notificationStyle);
        return builder.build();
    }

//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        instance = this;
//        startRinging();
//        return START_NOT_STICKY;
//    }

//    @Override
//    public void onCreate() {
//        if (pendingVoIP != null) {
//            account = pendingVoIP.getIntExtra("account", UserConfig.selectedAccount);
//            user_id = pendingVoIP.getLongExtra("user_id", 0);
//            call_id = pendingVoIP.getLongExtra("call_id", 0);
//            video = pendingVoIP.getBooleanExtra("video", false);
//        }
//        if (Build.VERSION.SDK_INT >= 33) {
//            startForeground(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
//        } else {
//            startForeground(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION, getNotification());
//        }
//    }
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    @Override
//    public void onDestroy() {
//        stopForeground(true);
//        stopRinging();
//        super.onDestroy();
//    }

    private static final Object sync = new Object();
    private static MediaPlayer ringtonePlayer;
    private static Vibrator vibrator;

    public static void startRinging(Context context, int account, long user_id) {
        SharedPreferences prefs = MessagesController.getNotificationsSettings(account);
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean needRing = am.getRingerMode() != AudioManager.RINGER_MODE_SILENT;
        final boolean isHeadsetPlugged = am.isWiredHeadsetOn();
        if (needRing) {
            if (ringtonePlayer != null) {
                return;
            }
            synchronized (sync) {
                if (ringtonePlayer != null) {
                    return;
                }
                ringtonePlayer = new MediaPlayer();
                ringtonePlayer.setOnPreparedListener(mediaPlayer -> {
                    try {
                        ringtonePlayer.start();
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                });
                ringtonePlayer.setLooping(true);
                if (isHeadsetPlugged) {
                    ringtonePlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                } else {
                    ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
                }
                try {
                    String notificationUri;
                    if (prefs.getBoolean("custom_" + user_id, false)) {
                        notificationUri = prefs.getString("ringtone_path_" + user_id, null);
                    } else {
                        notificationUri = prefs.getString("CallsRingtonePath", null);
                    }
                    Uri ringtoneUri;
                    boolean isDafaultUri = false;
                    if (notificationUri == null) {
                        ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                        isDafaultUri = true;
                    } else {
                        Uri defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
                        if (defaultUri != null && notificationUri.equalsIgnoreCase(defaultUri.getPath())) {
                            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                            isDafaultUri = true;
                        } else {
                            ringtoneUri = Uri.parse(notificationUri);
                        }
                    }
                    FileLog.d("start ringtone with " + isDafaultUri + " " + ringtoneUri);
                    ringtonePlayer.setDataSource(context, ringtoneUri);
                    ringtonePlayer.prepareAsync();
                } catch (Exception e) {
                    FileLog.e(e);
                    if (ringtonePlayer != null) {
                        ringtonePlayer.release();
                        ringtonePlayer = null;
                    }
                }
                int vibrate;
                if (prefs.getBoolean("custom_" + user_id, false)) {
                    vibrate = prefs.getInt("calls_vibrate_" + user_id, 0);
                } else {
                    vibrate = prefs.getInt("vibrate_calls", 0);
                }
                if ((vibrate != 2 && vibrate != 4 && (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)) || (vibrate == 4 && am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)) {
                    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    long duration = 700;
                    if (vibrate == 1) {
                        duration /= 2;
                    } else if (vibrate == 3) {
                        duration *= 2;
                    }
                    vibrator.vibrate(new long[]{0, duration, 500}, 0);
                }
            }
        }
    }

    public static void stopRinging() {
        synchronized (sync) {
            if (ringtonePlayer != null) {
                ringtonePlayer.stop();
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

//    public void onAudioFocusChange(int focusChange) {
//        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
//            hasAudioFocus = true;
//        } else {
//            hasAudioFocus = false;
//        }
//    }

    public static TL_phone.PhoneCall pendingCall;
    public static Intent pendingVoIP;
    public static State currentState;
//    public static Intent pendingNotificationService;

    public static void show(Context context, Intent intent, TL_phone.PhoneCall call) {
        FileLog.d("VoIPPreNotification.show()");

        if (call == null || intent == null) {
            dismiss(context, false);
            FileLog.d("VoIPPreNotification.show(): call or intent is null");
            return;
        }

        if (pendingCall != null && pendingCall.id == call.id) {
            return;
        }

        dismiss(context, false);

        pendingVoIP = intent;
        pendingCall = call;

        final int account = intent.getIntExtra("account", UserConfig.selectedAccount);
        final long user_id = intent.getLongExtra("user_id", 0);
        final boolean video = call != null && call.video;

        currentState = new State(account, user_id, call);

        acknowledge(context, account, call, () -> {
            pendingVoIP = intent;
            pendingCall = call;

            final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION, makeNotification(context, account, user_id, call.id, video));
            startRinging(context, account, user_id);
        });
    }

    private static void acknowledge(Context context, int currentAccount, TL_phone.PhoneCall call, Runnable whenAcknowledged) {
        if (call instanceof TL_phone.TL_phoneCallDiscarded) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.w("Call " + call.id + " was discarded before the voip pre notification started, stopping");
            }
            pendingVoIP = null;
            pendingCall = null;
            if (currentState != null) {
                currentState.destroy();
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 19 && XiaomiUtilities.isMIUI() && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
            if (((KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode()) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("MIUI: no permission to show when locked but the screen is locked. ¯\\_(ツ)_/¯");
                }
                pendingVoIP = null;
                pendingCall = null;
                if (currentState != null) {
                    currentState.destroy();
                }
                return;
            }
        }
        final TL_phone.receivedCall req = new TL_phone.receivedCall();
        req.peer = new TLRPC.TL_inputPhoneCall();
        req.peer.id = call.id;
        req.peer.access_hash = call.access_hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.w("(VoIPPreNotification) receivedCall response = " + response);
            }
            if (error != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("error on receivedCall: " + error);
                }
                pendingVoIP = null;
                pendingCall = null;
                if (currentState != null) {
                    currentState.destroy();
                }
                dismiss(context, false);
            } else if (whenAcknowledged != null) {
                whenAcknowledged.run();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static boolean open(Context context) {
        if (VoIPService.getSharedInstance() != null) {
            return true;
        }
        if (pendingVoIP == null || pendingCall == null) return false;
        final int account = pendingVoIP.getIntExtra("account", UserConfig.selectedAccount);
        pendingVoIP.putExtra("openFragment", true);
        pendingVoIP.putExtra("accept", false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(pendingVoIP);
        } else {
            context.startService(pendingVoIP);
        }
        pendingVoIP = null;
        dismiss(context, true);
        return true;
    }

    public static boolean isVideo() {
        return pendingVoIP != null && pendingVoIP.getBooleanExtra("video", false);
    }

    public static void answer(Context context) {
        FileLog.d("VoIPPreNotification.answer()");
        if (pendingVoIP == null) {
            FileLog.d("VoIPPreNotification.answer(): pending intent is not found");
            return;
        }
        currentState = null;
        final int account = pendingVoIP.getIntExtra("account", UserConfig.selectedAccount);
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().acceptIncomingCall();
        } else {
            pendingVoIP.putExtra("openFragment", true);
            if (
                !PermissionRequest.hasPermission(Manifest.permission.RECORD_AUDIO) ||
                isVideo() && !PermissionRequest.hasPermission(Manifest.permission.CAMERA)
            ) {
                try {
                    PendingIntent.getActivity(context, 0, new Intent(context, VoIPPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT).send();
                } catch (Exception x) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("Error starting permission activity", x);
                    }
                }
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(pendingVoIP);
            } else {
                context.startService(pendingVoIP);
            }
            pendingVoIP = null;
        }
        dismiss(context, true);
    }

    public static void decline(Context context, int reason) {
        FileLog.d("VoIPPreNotification.decline("+reason+")");
        if (pendingVoIP == null || pendingCall == null) {
            FileLog.d("VoIPPreNotification.decline("+reason+"): pending intent or call is not found");
            return;
        }
        final int account = pendingVoIP.getIntExtra("account", UserConfig.selectedAccount);
        final TL_phone.discardCall req = new TL_phone.discardCall();
        req.peer = new TLRPC.TL_inputPhoneCall();
        req.peer.access_hash = pendingCall.access_hash;
        req.peer.id = pendingCall.id;
        req.duration = 0;
        req.connection_id = 0;
        switch (reason) {
            case VoIPService.DISCARD_REASON_DISCONNECT:
                req.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
                break;
            case VoIPService.DISCARD_REASON_MISSED:
                req.reason = new TLRPC.TL_phoneCallDiscardReasonMissed();
                break;
            case VoIPService.DISCARD_REASON_LINE_BUSY:
                req.reason = new TLRPC.TL_phoneCallDiscardReasonBusy();
                break;
            case VoIPService.DISCARD_REASON_HANGUP:
            default:
                req.reason = new TLRPC.TL_phoneCallDiscardReasonHangup();
                break;
        }
        FileLog.e("discardCall " + req.reason);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("(VoIPPreNotification) error on phone.discardCall: " + error);
                }
            } else {
                if (response instanceof TLRPC.TL_updates) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    MessagesController.getInstance(account).processUpdates(updates, false);
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("(VoIPPreNotification) phone.discardCall " + response);
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        dismiss(context, false);
    }

    public static void dismiss(Context context, boolean answered) {
        FileLog.d("VoIPPreNotification.dismiss()");
        pendingVoIP = null;
        pendingCall = null;
        if (currentState != null) {
            currentState.destroy();
        }
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);
        stopRinging();
        if (!answered) {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                MessagesController.getInstance(i).ignoreSetOnline = false;
            }
        }
//        if (pendingNotificationService != null) {
//            context.stopService(pendingNotificationService);
//        }
//        pendingNotificationService = null;
    }

}
