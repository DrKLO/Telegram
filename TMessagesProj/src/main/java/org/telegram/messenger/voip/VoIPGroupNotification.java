package org.telegram.messenger.voip;

import android.app.Activity;
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
import android.os.Build;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.DragAndDropPermissions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.GroupCallSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPFragment;

import java.util.ArrayList;
import java.util.HashSet;

public class VoIPGroupNotification {

    public static final class State implements VoIPServiceState {

        private final int currentAccount;
        public final long dialogId;
        public final long call_id;
        public final int msg_id;
        private final TLRPC.InputGroupCall inputGroupCall;
        private final boolean video;
        private final TLRPC.GroupCall groupCall;
        private final ArrayList<TLRPC.GroupCallParticipant> participants;
        private boolean destroyed;

        public State(int currentAccount, long dialogId, long call_id, int msg_id, boolean video, TLRPC.GroupCall groupCall, ArrayList<TLRPC.GroupCallParticipant> participants) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.call_id = call_id;
            this.msg_id = msg_id;
            this.inputGroupCall = new TLRPC.TL_inputGroupCallInviteMessage();
            this.inputGroupCall.msg_id = msg_id;
            this.groupCall = groupCall;
            this.participants = participants;
            this.video = video;
        }

        @Override
        public TLRPC.User getUser() {
            return MessagesController.getInstance(currentAccount).getUser(dialogId);
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
            return null;
        }

        @Override
        public boolean isCallingVideo() {
            return video;
        }

        @Override
        public void acceptIncomingCall() {
            answer(ApplicationLoader.applicationContext, currentAccount, msg_id);
        }

        @Override
        public void declineIncomingCall() {
            decline(ApplicationLoader.applicationContext, currentAccount, msg_id);
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
            return true;
        }

        @Override
        public TLRPC.GroupCall getGroupCall() {
            return groupCall;
        }

        @Override
        public ArrayList<TLRPC.GroupCallParticipant> getGroupParticipants() {
            return participants;
        }
    }

    public static long currentCallId;
    public static State currentState;
    private static HashSet<Integer> ignoreCalls;
    private static Runnable missRunnable;
    public static void request(Context context, int account, long dialogId, String names, long call_id, int msg_id, boolean video) {
        if (Build.VERSION.SDK_INT < 26 || currentCallId == call_id || currentState != null && currentState.call_id == call_id) return;

        if (VoIPService.getSharedInstance() != null) {
            if (currentState != null) {
                hide(context);
            }
            return;
        }
        if (MessagesController.getInstance(account).callRequestsDisabled) return;
        if (ignoreCalls != null && ignoreCalls.contains(msg_id))
            return;

        currentCallId = call_id;
        final TL_phone.getGroupCall req = new TL_phone.getGroupCall();
        req.call = new TLRPC.TL_inputGroupCallInviteMessage();
        req.call.msg_id = msg_id;
        req.limit = 3;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_phone.groupCall) {
                final TL_phone.groupCall r = (TL_phone.groupCall) res;
                MessagesController.getInstance(account).putUsers(r.users, false);
                MessagesController.getInstance(account).putChats(r.chats, false);

                currentState = new State(account, dialogId, call_id, msg_id, video, r.call, r.participants);
                showNotification(context, account, call_id, msg_id, dialogId, names);
            } else {
                if (ignoreCalls == null) ignoreCalls = new HashSet<>();
                ignoreCalls.add(msg_id);
            }
        }));
    }

    private static void showNotification(Context context, int account, long call_id, int msg_id, long dialogId, String names) {
        if (Build.VERSION.SDK_INT < 26) return;
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        final Intent intent = new Intent(context, LaunchActivity.class)
            .setAction("voip")
            .putExtra("group_call_invite_msg_id", msg_id)
            .putExtra("currentAccount", account);
        final Notification.Builder builder = new Notification.Builder(context)
            .setContentTitle(LocaleController.getString(R.string.VoipGroupInCallBranding))
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
            }
        }
        builder.setChannelId("incoming_calls4" + chanIndex);

        final Intent endIntent = new Intent(context, VoIPActionsReceiver.class);
        endIntent.setAction(context.getPackageName() + ".DECLINE_CALL");
        endIntent.putExtra("call_id", call_id);
        endIntent.putExtra("group_call_invite_msg_id", msg_id);
        endIntent.putExtra("currentAccount", account);
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
        answerIntent.putExtra("group_call_invite_msg_id", msg_id);
        answerIntent.putExtra("currentAccount", account);
        CharSequence answerTitle = LocaleController.getString(R.string.VoipAnswerCall);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            answerTitle = new SpannableString(answerTitle);
            ((SpannableString) answerTitle).setSpan(new ForegroundColorSpan(0xFF00AA00), 0, answerTitle.length(), 0);
        }
        final PendingIntent answerPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                new Intent(context, LaunchActivity.class)
                    .setAction("voip_answer")
                    .putExtra("group_call_invite_msg_id", msg_id)
                    .putExtra("currentAccount", account),
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
        }

        final Intent hideIntent = new Intent(ApplicationLoader.applicationContext, VoIPActionsReceiver.class);
        hideIntent.setAction(context.getPackageName() + ".HIDE_CALL");
        hideIntent.putExtra("group_call_invite_msg_id", msg_id);
        hideIntent.putExtra("currentAccount", account);
        final PendingIntent hidePendingIntent =
            PendingIntent.getBroadcast(
                ApplicationLoader.applicationContext,
                0,
                hideIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
        builder.setDeleteIntent(hidePendingIntent);

        TLObject obj = MessagesController.getInstance(account).getUserOrChat(dialogId);
        Bitmap avatar = VoIPService.getRoundAvatarBitmap(context, account, obj);
        String personName = !TextUtils.isEmpty(names) ? names : ContactsController.formatName(obj);
        if (TextUtils.isEmpty(personName)) {
            //java.lang.IllegalArgumentException: person must have a non-empty a name
            personName = "___";
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Person person = new Person.Builder()
                .setName(personName)
                .setIcon(Icon.createWithAdaptiveBitmap(avatar)).build();
            Notification.CallStyle notificationStyle = Notification.CallStyle.forIncomingCall(person, endPendingIntent, answerPendingIntent);
            builder.setStyle(notificationStyle);
        }

        Notification notification = builder.build();

        nm.notify(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION, notification);
        VoIPPreNotificationService.startRinging(context, account, dialogId);

        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        AndroidUtilities.runOnUIThread(missRunnable = () -> decline(context, account, msg_id), MessagesController.getInstance(account).callRingTimeout);
    }

    public static void open(Context context, int account, int msg_id) {
        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        VoIPPreNotificationService.stopRinging();
        if (currentState == null || currentState.msg_id != msg_id) {
            return;
        }
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);
        VoIPPreNotificationService.stopRinging();

        Activity activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (activity == null) activity = LaunchActivity.instance;
        if (activity != null) {
            VoIPFragment.show(activity, account);
        }
    }

    public static void answer(Context context, int account, int msg_id) {
        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        VoIPPreNotificationService.stopRinging();
        if (currentState == null || currentState.msg_id != msg_id) {
            return;
        }
        final TLRPC.GroupCall groupCall = currentState.groupCall;
        final boolean video = currentState.isCallingVideo();
        currentState = null;
        currentCallId = 0;
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);

        final TLRPC.TL_inputGroupCallInviteMessage call = new TLRPC.TL_inputGroupCallInviteMessage();
        call.msg_id = msg_id;
        VoIPHelper.joinConference(LaunchActivity.instance, account, call, video, groupCall);
    }

    public static void decline(Context context, int account, int msg_id) {
        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        currentState = null;
        currentCallId = 0;
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);
        VoIPPreNotificationService.stopRinging();

        final TL_phone.declineConferenceCallInvite req = new TL_phone.declineConferenceCallInvite();
        req.msg_id = msg_id;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.Updates) {
                MessagesController.getInstance(account).processUpdates((TLRPC.Updates) res, false);
            }
        });

        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().finish();
        }
    }

    public static void hide(Context context) {
        if (currentState == null) return;
        hide(context, currentState.currentAccount, currentState.msg_id);
    }
    public static void hide(Context context, int currentAccount, int msg_id) {
        if (currentState == null || currentState.currentAccount != currentAccount || currentState.msg_id != msg_id) {
            return;
        }
        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        currentState = null;
        currentCallId = 0;
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);
        VoIPPreNotificationService.stopRinging();

        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().finish();
        }
    }
    public static void hideByCallId(Context context, int currentAccount, long call_id) {
        if (currentState == null || currentState.currentAccount != currentAccount || currentState.call_id != call_id) {
            return;
        }
        if (missRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(missRunnable);
        }
        currentState = null;
        currentCallId = 0;
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(VoIPService.ID_INCOMING_CALL_PRENOTIFICATION);
        VoIPPreNotificationService.stopRinging();

        if (VoIPFragment.getInstance() != null) {
            VoIPFragment.getInstance().finish();
        }
    }

}
