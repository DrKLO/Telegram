package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telecom.PhoneAccountHandle;
import android.telephony.CellInfo;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.web.R;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TL_smsjobs;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SMSSubscribeSheet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class SMSJobController implements NotificationCenter.NotificationCenterDelegate {

    private static volatile SMSJobController[] Instance = new SMSJobController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }
    public static SMSJobController getInstance(int num) {
        SMSJobController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new SMSJobController(num);
                }
            }
        }
        return localInstance;
    }

    public final static int STATE_NONE = 0;
    public final static int STATE_ASKING_PERMISSION = 1;
    public final static int STATE_NO_SIM = 2;
    public final static int STATE_JOINED = 3;

    public final int currentAccount;

    private int lastErrorId, seenErrorId;
    public int currentState;

    public TL_smsjobs.TL_smsjobs_status currentStatus;
    private boolean loadingStatus, loadedStatus;
    public boolean atStatisticsPage;

    private boolean loadingIsEligible, loadedIsEligible;
    public TL_smsjobs.TL_smsjobs_eligibleToJoin isEligible;

    public SIM selectedSimCard;

    public boolean isEligible() {
        return isEligible != null || loadingIsEligible;
    }

    private SharedPreferences journalPrefs;

    private SMSJobController(int account) {
        this.currentAccount = account;
        journalPrefs = ApplicationLoader.applicationContext.getSharedPreferences("smsjobs_journal_" + currentAccount, Context.MODE_PRIVATE);
        loadCacheStatus();
        AndroidUtilities.runOnUIThread(() -> {
            readPending();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSuggestionsAvailable);
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSuggestionsAvailable) {
            if (currentState != STATE_NONE && currentState != STATE_JOINED) {
                checkIsEligible(true, null);
            }
            invalidateStatus();
        }
    }

    public boolean isAvailable() {
        if (currentState != STATE_NONE && currentState != STATE_JOINED) {
            checkIsEligible(false, null);
            loadStatus(false);
        }
        return currentState != STATE_NONE && (isEligible != null || currentStatus != null);
    }

    public void checkIsEligible(boolean force, Utilities.Callback<TL_smsjobs.TL_smsjobs_eligibleToJoin> whenDone) {
        if (loadedIsEligible && !force || loadingIsEligible && whenDone == null) return;
        loadingIsEligible = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_isEligibleToJoin(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loadingIsEligible = false;
            loadedIsEligible = true;
            if (res instanceof TL_smsjobs.TL_smsjobs_eligibleToJoin) {
                isEligible = (TL_smsjobs.TL_smsjobs_eligibleToJoin) res;
            } else if (err != null && "NOT_ELIGIBLE".equals(err.text)) {
                isEligible = null;
            } else if (err != null && "ALREADY_JOINED".equals(err.text)) {
                isEligible = null;
            } else if (err != null) {
                BulletinFactory.showError(err);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
            if (whenDone != null) {
                whenDone.run(isEligible);
            }
        }));
    }

    public void loadStatus(boolean force) {
        if (loadingStatus || loadedStatus && !force)
            return;
        loadingStatus = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_getStatus(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            TL_smsjobs.TL_smsjobs_status lastStatus = currentStatus;
            TL_smsjobs.TL_smsjobs_eligibleToJoin lastIsEligible = isEligible;
            int lastState = currentState, state = currentState;
            loadingStatus = false;
            loadedStatus = true;
            if (res instanceof TL_smsjobs.TL_smsjobs_status) {
                state = STATE_JOINED;
                currentStatus = (TL_smsjobs.TL_smsjobs_status) res;
                saveCacheStatus();
            } else if (err != null && "NOT_JOINED".equals(err.text)) {
                if (state == STATE_JOINED) {
                    state = STATE_NONE;
                }
                currentStatus = null;
                saveCacheStatus();
            } else if (err != null && "NOT_ELIGIBLE".equals(err.text)) {
                if (state == STATE_JOINED) {
                    state = STATE_NONE;
                }
                currentStatus = null;
                isEligible = null;
                saveCacheStatus();
            } else {
                BulletinFactory.showError(err);
            }
            if (lastStatus != currentStatus || lastIsEligible != isEligible || lastState != state) {
                setState(state);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);

                SMSJobsNotification.check();
            }
        }));
    }

    public void invalidateStatus() {
        loadedStatus = false;
        if (atStatisticsPage || ApplicationLoader.mainInterfacePaused) {
            loadStatus(false);
        }
    }

    public void init() {
        loadStatus(false);
        checkSelectedSIMCard();
    }

    public void checkSelectedSIMCard() {
        int selectedSimId = MessagesController.getMainSettings(currentAccount).getInt("smsjobs_sim", -1);
        try {
            ArrayList<SIM> sims = getSIMs();
            if (sims.isEmpty()) {
                selectedSimCard = null;
            } else if (selectedSimId == -1) {
                selectedSimCard = sims.get(0);
            } else {
                selectedSimCard = sims.get(0);
                for (int i = 0; i < sims.size(); ++i) {
                    if (sims.get(i).id == selectedSimId) {
                        selectedSimCard = sims.get(i);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            selectedSimCard = null;
        }
        if (selectedSimCard != null && selectedSimCard.id != selectedSimId) {
            MessagesController.getMainSettings(currentAccount).edit().putInt("smsjobs_sim", selectedSimCard.id).apply();
        }
        if (currentState == STATE_NO_SIM && selectedSimCard != null) {
            ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_join(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (err != null) {
                    BulletinFactory.showError(err);
                } else if (res instanceof TLRPC.TL_boolFalse) {
                    BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                } else {
                    setState(SMSJobController.STATE_JOINED);
                    loadStatus(true);
                    SMSSubscribeSheet.showSubscribed(LaunchActivity.instance == null ? ApplicationLoader.applicationContext : LaunchActivity.instance, null);
                }
            }));
        }
    }

    public SIM getSelectedSIM() {
        return selectedSimCard;
    }

    public void setSelectedSIM(SIM sim) {
        if (sim == null) return;
        selectedSimCard = sim;
        MessagesController.getMainSettings(currentAccount).edit().putInt("smsjobs_sim", selectedSimCard.id).apply();
    }

    public int simsCount() {
        try {
            return getSIMs().size();
        } catch (Exception e) {

        }
        return 0;
    }

    public ArrayList<SIM> getSIMs() {
        return getSIMs(ApplicationLoader.applicationContext);
    }

    private void loadCacheStatus() {
        currentState = MessagesController.getMainSettings(currentAccount).getInt("smsjobs_state", STATE_NONE);
        lastErrorId = MessagesController.getMainSettings(currentAccount).getInt("smsjobs_error", 0);
        seenErrorId = MessagesController.getMainSettings(currentAccount).getInt("smsjobs_seen_error", 0);
        String string = MessagesController.getMainSettings(currentAccount).getString("smsjobs_status", null);
        if (string != null) {
            try {
                SerializedData serializedData = new SerializedData(Utilities.hexToBytes(string));
                int constructor = serializedData.readInt32(true);
                if (constructor == TL_smsjobs.TL_smsjobs_status.constructor) {
                    currentStatus = new TL_smsjobs.TL_smsjobs_status();
                    currentStatus.readParams(serializedData, true);
                }
            } catch (Exception e) {
                FileLog.e(e);
                currentStatus = null;
            }
        }
        readJournal();
    }

    public void setState(int state) {
        MessagesController.getMainSettings(currentAccount).edit().putInt("smsjobs_state", this.currentState = state).apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
        if (currentState == STATE_JOINED) {
            MessagesController.getInstance(currentAccount).removeSuggestion(0, "PREMIUM_SMSJOBS");
        }
        SMSJobsNotification.check();
    }

    public int getState() {
        return currentState;
    }

    private void saveCacheStatus() {
        SharedPreferences.Editor edit = MessagesController.getMainSettings(currentAccount).edit();
        if (currentStatus == null) {
            edit.remove("smsjobs_status");
        } else {
            SerializedData data = new SerializedData(currentStatus.getObjectSize());
            currentStatus.serializeToStream(data);
            edit.putString("smsjobs_status", Utilities.bytesToHex(data.toByteArray()));
        }
        edit.apply();
    }

    private HashSet<String> completedJobs = new HashSet<>();
    private HashSet<String> loadingJobs = new HashSet<>();

    public void processJobUpdate(String job_id) {
        if (currentState != STATE_JOINED) {
            FileLog.d("[smsjob] received update on sms job " + job_id + ", but we did not join!!! currentState=" + currentState);
            return;
        }
        if (completedJobs.contains(job_id) || loadingJobs.contains(job_id)) return;
        loadingJobs.add(job_id);
        FileLog.d("[smsjob] received update on sms job " + job_id + ", fetching");
        TL_smsjobs.TL_smsjobs_getSmsJob req = new TL_smsjobs.TL_smsjobs_getSmsJob();
        req.job_id = job_id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_smsjobs.TL_smsJob) {
                runJob((TL_smsjobs.TL_smsJob) res);
            } else {
                FileLog.e("[smsjob] failed to fetch sms job by id " + job_id);
                loadingJobs.remove(job_id);
            }
        }));
    }

    private void runJob(TL_smsjobs.TL_smsJob job) {
        checkSelectedSIMCard();
        final String phone_number = !job.phone_number.startsWith("+") ? "+" + job.phone_number : job.phone_number;
        FileLog.d("[smsjob] running sms job " + job.job_id + (BuildVars.DEBUG_PRIVATE_VERSION ? ": " + job.text + " to " + phone_number : "") + ", selected sim: " + (selectedSimCard == null ? "null" : "{id=" + selectedSimCard.id + ", icc=" + selectedSimCard.iccId + ", name=" + selectedSimCard.name + ", slot=" + selectedSimCard.slot + "}"));
        pushToJournal(job.job_id, 1, phone_number, null);
        sendSMS(
            ApplicationLoader.applicationContext,
            currentAccount,
            job.job_id,
            selectedSimCard,
            phone_number,
            job.text
        );
    }

    private void finishJob(String job_id, String phone_number, String error) {
        FileLog.d("[smsjob] finished sms job " + job_id + ", error=" + error);
        TL_smsjobs.TL_smsjobs_finishJob req = new TL_smsjobs.TL_smsjobs_finishJob();
        req.job_id = job_id;
        if (error != null) {
            req.flags |= 1;
            req.error = error;
        }
        completedJobs.add(job_id);
        loadingJobs.remove(job_id);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_boolTrue) {
                FileLog.d("[smsjob] finished sms job " + job_id + ", received true");
            } else if (res instanceof TLRPC.TL_boolFalse) {
                FileLog.d("[smsjob] finished sms job " + job_id + ", received false");
            } else if (err != null) {
                FileLog.d("[smsjob] finished sms job " + job_id + ", received error " + err.code + " " + err.text);
            }
            pushToJournal(job_id, 0, phone_number, error);
            invalidateStatus();
        }));
    }


    private static class PendingSMS extends TLObject {
        public int id;

        public int currentAccount;
        public String jobId;
        public int simId;
        public String phone, text;

        public final boolean received[] = new boolean[2];
        public boolean finished = false;

        public int triesLeft = 2;

        public long sentTime = System.currentTimeMillis();

        private Runnable timerCallback;

        private PendingSMS() {}

        public PendingSMS(
            int id,
            int currentAccount,
            String jobId,
            SIM sim,
            String phone,
            String text
        ) {
            this.id = id;
            this.currentAccount = currentAccount;
            this.jobId = jobId;
            this.simId = sim == null ? -1 : sim.id;
            this.phone = phone;
            this.text = text;
        }

        public void setup() {
            final long now = System.currentTimeMillis();
            long timeout = sentTime + 2 * 60 * 1000 - now;
            AndroidUtilities.runOnUIThread(timerCallback = () -> {
                whenSent(false, "2MIN_TIMEOUT");
                SMSJobController.pending.remove(id);
                savePending();
            }, Math.max(0, timeout));
        }

        public void whenSent(boolean success, String reason) {
            if (received[0]) return;
            received[0] = true;

            AndroidUtilities.cancelRunOnUIThread(timerCallback);

            FileLog.d("[smsjob] sms job " + jobId + " sent callback: success=" + success + ", reason=" + reason);
            if (!finished) {
                finished = true;
                SMSJobController.getInstance(currentAccount).finishJob(jobId, phone, success ? null : reason);
            }
        }

        public void whenDelivered(boolean success, String reason) {
            if (received[1]) return;
            received[1] = true;

            AndroidUtilities.cancelRunOnUIThread(timerCallback);

            FileLog.d("[smsjob] sms job " + jobId + " delivered callback: success=" + success + ", reason=" + reason);
            if (!finished) {
                finished = true;
                SMSJobController.getInstance(currentAccount).finishJob(jobId, phone, success ? null : reason);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(0x8384213);
            stream.writeInt32(id);
            stream.writeInt32(currentAccount);
            stream.writeString(jobId);
            stream.writeInt32(simId);
            stream.writeString(phone);
            stream.writeString(text);
            int flags = 0;
            flags |= (received[0] ? 1 : 0);
            flags |= (received[1] ? 2 : 0);
            flags |= (finished ? 4 : 0);
            stream.writeInt32(flags);
            stream.writeInt32(triesLeft);
            stream.writeInt64(sentTime);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            currentAccount = stream.readInt32(exception);
            jobId = stream.readString(exception);
            simId = stream.readInt32(exception);
            phone = stream.readString(exception);
            text = stream.readString(exception);
            int flags = stream.readInt32(exception);
            received[0] = (flags & 1) != 0;
            received[1] = (flags & 2) != 0;
            finished = (flags & 4) != 0;
            triesLeft = stream.readInt32(exception);
            sentTime = stream.readInt64(exception);
        }
    }

    private static boolean readCachedPending = false;
    private static HashMap<Integer, PendingSMS> pending = new HashMap<>();

    private static void readPending() {
        if (readCachedPending) return;
        String pendingText = MessagesController.getGlobalMainSettings().getString("smsjobs_pending", null);
        if (pendingText != null) {
            try {
                SerializedData serializedData = new SerializedData(Utilities.hexToBytes(pendingText));
                int count = serializedData.readInt32(true);
                for (int i = 0; i < count; ++i) {
                    int magic = serializedData.readInt32(true);
                    if (magic != 0x8384213) throw new RuntimeException("pending parse unknown magic " + magic);
                    PendingSMS pending = new PendingSMS();
                    pending.readParams(serializedData, true);
                    pending.setup();
                    SMSJobController.pending.put(pending.id, pending);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        readCachedPending = true;
    }

    private static void savePending() {
        if (pending.isEmpty()) {
            MessagesController.getGlobalMainSettings().edit().remove("smsjobs_pending").apply();
            return;
        }
        try {
            int size = pending.size() * 8;
            for (PendingSMS pending : pending.values()) {
                size += pending.getObjectSize();
            }
            SerializedData data = new SerializedData(size);
            data.writeInt32(pending.size());
            for (PendingSMS pending : pending.values()) {
                pending.serializeToStream(data);
            }
            MessagesController.getGlobalMainSettings().edit().putString("smsjobs_pending", Utilities.bytesToHex(data.toByteArray())).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void receivedSMSIntent(Intent intent, final int resultCode) {
        if (intent == null) return;
        final int id = intent.getIntExtra("tg_sms_id", 0);
        final boolean sent = intent.getBooleanExtra("sent", false);
        final boolean delivered = intent.getBooleanExtra("delivered", false);
        PendingSMS pending = SMSJobController.pending.get(id);
        if (pending == null) {
            FileLog.d("[smsjob] received sms callback with id " + id + ", "+ (sent ? "sent" : (delivered ? "delivered" : "null")) +": not found");
            return;
        }
        FileLog.d("[smsjob] received sms callback with id " + id + ", "+ (sent ? "sent" : (delivered ? "delivered" : "null")));
        if (resultCode == SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY && pending.triesLeft > 0) {
            pending.triesLeft--;
            resendPending(pending);
            return;
        }
        boolean success = false;
        String reason = null;
        switch (resultCode) {
            case Activity.RESULT_OK:
                success = true;
                break;
            case SmsManager.RESULT_ERROR_NONE: reason = "RESULT_ERROR_NONE"; break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE: reason = "RESULT_ERROR_GENERIC_FAILURE"; break;
            case SmsManager.RESULT_ERROR_RADIO_OFF: reason = "RESULT_ERROR_RADIO_OFF"; break;
            case SmsManager.RESULT_ERROR_NULL_PDU: reason = "RESULT_ERROR_NULL_PDU"; break;
            case SmsManager.RESULT_ERROR_NO_SERVICE: reason = "RESULT_ERROR_NO_SERVICE"; break;
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED: reason = "RESULT_ERROR_LIMIT_EXCEEDED"; break;
            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE: reason = "RESULT_ERROR_FDN_CHECK_FAILURE"; break;
            case SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED: reason = "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED"; break;
            case SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED: reason = "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED"; break;
            case SmsManager.RESULT_RADIO_NOT_AVAILABLE: reason = "RESULT_RADIO_NOT_AVAILABLE"; break;
            case SmsManager.RESULT_NETWORK_REJECT: reason = "RESULT_NETWORK_REJECT"; break;
            case SmsManager.RESULT_INVALID_ARGUMENTS: reason = "RESULT_INVALID_ARGUMENTS"; break;
            case SmsManager.RESULT_INVALID_STATE: reason = "RESULT_INVALID_STATE"; break;
            case SmsManager.RESULT_NO_MEMORY: reason = "RESULT_NO_MEMORY"; break;
            case SmsManager.RESULT_INVALID_SMS_FORMAT: reason = "RESULT_INVALID_SMS_FORMAT"; break;
            case SmsManager.RESULT_SYSTEM_ERROR: reason = "RESULT_SYSTEM_ERROR"; break;
            case SmsManager.RESULT_MODEM_ERROR: reason = "RESULT_MODEM_ERROR"; break;
            case SmsManager.RESULT_NETWORK_ERROR: reason = "RESULT_NETWORK_ERROR"; break;
            case SmsManager.RESULT_ENCODING_ERROR: reason = "RESULT_ENCODING_ERROR"; break;
            case SmsManager.RESULT_INVALID_SMSC_ADDRESS: reason = "RESULT_INVALID_SMSC_ADDRESS"; break;
            case SmsManager.RESULT_OPERATION_NOT_ALLOWED: reason = "RESULT_OPERATION_NOT_ALLOWED"; break;
            case SmsManager.RESULT_INTERNAL_ERROR: reason = "RESULT_INTERNAL_ERROR"; break;
            case SmsManager.RESULT_NO_RESOURCES: reason = "RESULT_NO_RESOURCES"; break;
            case SmsManager.RESULT_CANCELLED: reason = "RESULT_CANCELLED"; break;
            case SmsManager.RESULT_REQUEST_NOT_SUPPORTED: reason = "RESULT_REQUEST_NOT_SUPPORTED"; break;
            case SmsManager.RESULT_NO_BLUETOOTH_SERVICE: reason = "RESULT_NO_BLUETOOTH_SERVICE"; break;
            case SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS: reason = "RESULT_INVALID_BLUETOOTH_ADDRESS"; break;
            case SmsManager.RESULT_BLUETOOTH_DISCONNECTED: reason = "RESULT_BLUETOOTH_DISCONNECTED"; break;
            case SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING: reason = "RESULT_UNEXPECTED_EVENT_STOP_SENDING"; break;
            case SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY: reason = "RESULT_SMS_BLOCKED_DURING_EMERGENCY"; break;
            case SmsManager.RESULT_SMS_SEND_RETRY_FAILED: reason = "RESULT_SMS_SEND_RETRY_FAILED"; break;
            case SmsManager.RESULT_REMOTE_EXCEPTION: reason = "RESULT_REMOTE_EXCEPTION"; break;
            case SmsManager.RESULT_NO_DEFAULT_SMS_APP: reason = "RESULT_NO_DEFAULT_SMS_APP"; break;
            case SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE: reason = "RESULT_RIL_RADIO_NOT_AVAILABLE"; break;
            case SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY: reason = "RESULT_RIL_SMS_SEND_FAIL_RETRY"; break;
            case SmsManager.RESULT_RIL_NETWORK_REJECT: reason = "RESULT_RIL_NETWORK_REJECT"; break;
            case SmsManager.RESULT_RIL_INVALID_STATE: reason = "RESULT_RIL_INVALID_STATE"; break;
            case SmsManager.RESULT_RIL_INVALID_ARGUMENTS: reason = "RESULT_RIL_INVALID_ARGUMENTS"; break;
            case SmsManager.RESULT_RIL_NO_MEMORY: reason = "RESULT_RIL_NO_MEMORY"; break;
            case SmsManager.RESULT_RIL_REQUEST_RATE_LIMITED: reason = "RESULT_RIL_REQUEST_RATE_LIMITED"; break;
            case SmsManager.RESULT_RIL_INVALID_SMS_FORMAT: reason = "RESULT_RIL_INVALID_SMS_FORMAT"; break;
            case SmsManager.RESULT_RIL_SYSTEM_ERR: reason = "RESULT_RIL_SYSTEM_ERR"; break;
            case SmsManager.RESULT_RIL_ENCODING_ERR: reason = "RESULT_RIL_ENCODING_ERR"; break;
            case SmsManager.RESULT_RIL_INVALID_SMSC_ADDRESS: reason = "RESULT_RIL_INVALID_SMSC_ADDRESS"; break;
            case SmsManager.RESULT_RIL_MODEM_ERR: reason = "RESULT_RIL_MODEM_ERR"; break;
            case SmsManager.RESULT_RIL_NETWORK_ERR: reason = "RESULT_RIL_NETWORK_ERR"; break;
            case SmsManager.RESULT_RIL_INTERNAL_ERR: reason = "RESULT_RIL_INTERNAL_ERR"; break;
            case SmsManager.RESULT_RIL_REQUEST_NOT_SUPPORTED: reason = "RESULT_RIL_REQUEST_NOT_SUPPORTED"; break;
            case SmsManager.RESULT_RIL_INVALID_MODEM_STATE: reason = "RESULT_RIL_INVALID_MODEM_STATE"; break;
            case SmsManager.RESULT_RIL_NETWORK_NOT_READY: reason = "RESULT_RIL_NETWORK_NOT_READY"; break;
            case SmsManager.RESULT_RIL_OPERATION_NOT_ALLOWED: reason = "RESULT_RIL_OPERATION_NOT_ALLOWED"; break;
            case SmsManager.RESULT_RIL_NO_RESOURCES: reason = "RESULT_RIL_NO_RESOURCES"; break;
            case SmsManager.RESULT_RIL_CANCELLED: reason = "RESULT_RIL_CANCELLED"; break;
            case SmsManager.RESULT_RIL_SIM_ABSENT: reason = "RESULT_RIL_SIM_ABSENT"; break;
            case SmsManager.RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED: reason = "RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED"; break;
            case SmsManager.RESULT_RIL_ACCESS_BARRED: reason = "RESULT_RIL_ACCESS_BARRED"; break;
            case SmsManager.RESULT_RIL_BLOCKED_DUE_TO_CALL: reason = "RESULT_RIL_BLOCKED_DUE_TO_CALL"; break;
            case SmsManager.RESULT_RIL_GENERIC_ERROR: reason = "RESULT_RIL_GENERIC_ERROR"; break;
            case SmsManager.RESULT_RECEIVE_DISPATCH_FAILURE: reason = "RESULT_RECEIVE_DISPATCH_FAILURE"; break;
            case SmsManager.RESULT_RECEIVE_INJECTED_NULL_PDU: reason = "RESULT_RECEIVE_INJECTED_NULL_PDU"; break;
            case SmsManager.RESULT_RECEIVE_RUNTIME_EXCEPTION: reason = "RESULT_RECEIVE_RUNTIME_EXCEPTION"; break;
            case SmsManager.RESULT_RECEIVE_NULL_MESSAGE_FROM_RIL: reason = "RESULT_RECEIVE_NULL_MESSAGE_FROM_RIL"; break;
            case SmsManager.RESULT_RECEIVE_WHILE_ENCRYPTED: reason = "RESULT_RECEIVE_WHILE_ENCRYPTED"; break;
            case SmsManager.RESULT_RECEIVE_SQL_EXCEPTION: reason = "RESULT_RECEIVE_SQL_EXCEPTION"; break;
            case SmsManager.RESULT_RECEIVE_URI_EXCEPTION: reason = "RESULT_RECEIVE_URI_EXCEPTION"; break;
            case 33: reason = "RESULT_USER_NOT_ALLOWED"; break;
            default: reason = "UNKNOWN_EXCEPTION_" + resultCode; break;
        }
        int errorCode = intent.getIntExtra("errorCode", -1);
        if (errorCode != -1) {
            reason += "_" + errorCode;
        }
        if (sent) {
            pending.whenSent(success, reason);
        } else if (delivered) {
            pending.whenDelivered(success, reason);
        }
        if (pending.received[0] || pending.received[1]) {
            SMSJobController.pending.remove(id);
            savePending();
        }
    }

    private static void sendSMS(
        Context context,
        int currentAccount,
        String jobId,
        SIM sim,
        String phone,
        String text
    ) {
        SmsManager smsManager;
        if (sim != null && Build.VERSION.SDK_INT >= 31) {
            smsManager = context.getSystemService(SmsManager.class).createForSubscriptionId(sim.id);
        } else if (sim != null && Build.VERSION.SDK_INT >= 22) {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(sim.id);
        } else {
            smsManager = SmsManager.getDefault();
        }

        final int ID = (int) (Math.random() * 1_000_000);
        final Intent sentIntent = new Intent(context, SMSResultService.class);
        sentIntent.putExtra("sent", true);
        sentIntent.putExtra("tg_sms_id", ID);
        final PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        final Intent deliveredIntent = new Intent(context, SMSResultService.class);
        deliveredIntent.putExtra("delivered", true);
        deliveredIntent.putExtra("tg_sms_id", ID);
        final PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingSMS pendingSMS = new PendingSMS(ID, currentAccount, jobId, sim, phone, text);
        pendingSMS.setup();
        pending.put(ID, pendingSMS);
        savePending();

        FileLog.d("[smsjob] sending sms with id " + ID);
        try {
            smsManager.sendTextMessage(phone, null, text, sentPI, deliveredPI);
            FileLog.d("[smsjob] sent sms with id " + ID);
        } catch (Throwable e) {
            FileLog.e("[smsjob] failed to send sms with id " + ID + ", caught error", e);
            pendingSMS.whenSent(false, (e == null ? "CAUGHT_EXCEPTION" : e.getMessage()));
        }
    }

    private static void resendPending(PendingSMS pending) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            context = LaunchActivity.instance;
        }
        if (context == null) {
            FileLog.d("[smsjob] resending failed: no context; with id " + pending.id);
            pending.whenSent(false, "RESENDING_NULL_CONTEXT");
            return;
        }

        SmsManager smsManager;
        if (pending.simId != -1 && Build.VERSION.SDK_INT >= 31) {
            smsManager = context.getSystemService(SmsManager.class).createForSubscriptionId(pending.simId);
        } else if (pending.simId != -1 && Build.VERSION.SDK_INT >= 22) {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(pending.simId);
        } else {
            smsManager = SmsManager.getDefault();
        }

        final Intent sentIntent = new Intent(context, SMSResultService.class);
        sentIntent.putExtra("sent", true);
        sentIntent.putExtra("tg_sms_id", pending.id);
        final PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        final Intent deliveredIntent = new Intent(context, SMSResultService.class);
        deliveredIntent.putExtra("delivered", true);
        deliveredIntent.putExtra("tg_sms_id", pending.id);
        final PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        FileLog.d("[smsjob] resending sms with id " + pending.id);
        try {
            smsManager.sendTextMessage(pending.phone, null, pending.text, sentPI, deliveredPI);
            FileLog.d("[smsjob] resent sms with id " + pending.id);
        } catch (Throwable e) {
            FileLog.e("[smsjob] failed to resend sms with id " + pending.id + ", caught error", e);
            pending.whenSent(false, (e == null ? "CAUGHT_EXCEPTION" : e.getMessage()));
        }
    }

    public static class SIM {
        public final int id;
        public final int slot;
        public final String name;
        public final String iccId;

        public final String country;
        public final String carrier;
        public final String phone_number;
        public SIM(int id, int slot, String name, String country) {
            this.id = id;
            this.slot = slot;
            this.name = name;
            this.iccId = null;
            this.country = country;
            this.carrier = null;
            this.phone_number = null;
        }

        public SIM(int id, int slot, String country) {
            this.id = id;
            this.slot = slot;
            this.name = "SIM" + (1 + slot);
            this.iccId = null;
            this.country = country;
            this.carrier = null;
            this.phone_number = null;
        }

        public SIM(int id, int slot, String name, String iccId, String country, String carrier, String phoneNumber) {
            this.id = id;
            this.slot = slot;
            this.name = name;
            this.iccId = iccId;
            this.country = country;
            this.carrier = carrier;
            this.phone_number = phoneNumber;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
        @RequiresPermission(Manifest.permission.READ_PHONE_NUMBERS)
        public static SIM from(SubscriptionManager subscriptionManager, SubscriptionInfo info) {
            if (info == null) {
                return null;
            }
            String phoneNumber;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                phoneNumber = subscriptionManager.getPhoneNumber(info.getSubscriptionId());
            } else {
                phoneNumber = info.getNumber();
            }
            return new SIM(
                    info.getSubscriptionId(),
                    info.getSimSlotIndex(),
                    info.getDisplayName() == null ? "" : info.getDisplayName().toString(),
                    info.getIccId(),
                    info.getCountryIso(),
                    info.getCarrierName() == null ? null : info.getCarrierName().toString(),
                    phoneNumber
            );
        }

        @NonNull
        @Override
        public String toString() {
            return (country != null ? "[" + country + "] " : "") + name + (carrier != null ? " (" + carrier + ")" : "");
        }
    }

    private static ArrayList<SIM> getSIMs(Context context) {
        final ArrayList<SIM> simInfoList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 22) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            List<SubscriptionInfo> infos = null;
            if (Build.VERSION.SDK_INT >= 30) {
                infos = subscriptionManager.getCompleteActiveSubscriptionInfoList();
            }
            if ((infos == null || infos.isEmpty()) && Build.VERSION.SDK_INT >= 28) {
                infos = subscriptionManager.getAccessibleSubscriptionInfoList();
            }
            if (infos == null || infos.isEmpty()) {
                infos = subscriptionManager.getActiveSubscriptionInfoList();
            }
            if (infos != null) {
                for (int i = 0; i < infos.size(); ++i) {
                    SIM sim = SIM.from(subscriptionManager, infos.get(i));
                    if (sim != null) {
                        simInfoList.add(sim);
                    }
                }
            }
        } else {
            final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
                SIM sim = new SIM(0, 0, telephonyManager.getSimCountryIso());
                simInfoList.add(sim);
            }
        }
        return simInfoList;
    }

    public void leave() {
        currentStatus = null;
        setState(STATE_NONE);
        clearJournal();
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_smsjobs.TL_smsjobs_leave(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                BulletinFactory.showError(err);
            } else if (res instanceof TLRPC.TL_boolFalse) {
                BulletinFactory.global().createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
            } else {
                SMSJobController.getInstance(currentAccount).loadStatus(true);
                SMSJobController.getInstance(currentAccount).checkIsEligible(true, null);
            }
        }));
        SMSJobsNotification.check();
    }

    private int updateSettingsReqId;
    public void toggleAllowInternational(boolean value) {
        if (currentStatus == null) return;
        if (updateSettingsReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(updateSettingsReqId, true);
        }
        if (currentStatus.allow_international == value) return;
        TL_smsjobs.TL_smsjobs_updateSettings req = new TL_smsjobs.TL_smsjobs_updateSettings();
        req.allow_international = value;
        final int[] reqId = new int[1];
        updateSettingsReqId = reqId[0] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (reqId[0] != updateSettingsReqId) return;
            updateSettingsReqId = 0;
            if (currentStatus != null) {
                currentStatus.allow_international = value;
            }
        }));
    }

    public ArrayList<JobEntry> journal = new ArrayList<>();

    private void readJournal() {
        journal.clear();
        for (Object v : journalPrefs.getAll().values()) {
            if (v instanceof String) {
                JobEntry entry = JobEntry.fromString((String) v);
                if (entry != null) {
                    journal.add(entry);
                }
            }
        }
        Collections.sort(journal, (a, b) -> b.date - a.date);

        if (!MessagesController.getMainSettings(currentAccount).getBoolean("smsjobs_checked_journal", false)) {
            for (int i = 0; i < journal.size(); ++i) {
                if (!TextUtils.isEmpty(journal.get(i).error)) {
                    registerError();
                    break;
                }
            }
            MessagesController.getMainSettings(currentAccount).edit().putBoolean("smsjobs_checked_journal", true).apply();
        }
    }

    private void clearJournal() {
        journal.clear();
        journalPrefs.edit().clear().apply();
    }

    private void pushToJournal(String job_id, int state, String phone_number, String error) {
        JobEntry entry = null;
        for (int i = 0; i < journal.size(); ++i) {
            if (TextUtils.equals(journal.get(i).job_id, job_id)) {
                entry = journal.get(i);
                break;
            }
        }
        if (entry == null) {
            journal.add(0, entry = new JobEntry());
        }
        entry.state = state;
        entry.job_id = job_id;
        entry.error = error;
        entry.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        entry.country = getCountryFromPhoneNumber(ApplicationLoader.applicationContext, phone_number);
        journalPrefs.edit().putString(entry.job_id, entry.toString()).apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);

        if (!TextUtils.isEmpty(error)) {
            registerError();
        }
    }

    public static String getCountryFromPhoneNumber(Context context, String phone_number) {
        if (phone_number == null) return null;
        final String phone = PhoneFormat.stripExceptNumbers(phone_number);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                if (phone.startsWith(args[0])) {
                    return args[1];
                }
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
        return "";
    }

    public static class JobEntry {
        public String job_id;
        public String error;
        public String country;
        public int state;
        public int date;

        @NonNull
        @Override
        public String toString() {
            return job_id + "," + (error == null ? "" : error) + "," + date + "," + country + "," + state;
        }

        public static JobEntry fromString(String string) {
            String[] parts = string.split(",");
            if (parts.length != 4 && parts.length != 5) return null;
            JobEntry entry = new JobEntry();
            entry.job_id = parts[0];
            entry.error = TextUtils.isEmpty(parts[1]) ? null : parts[1];
            entry.date = Utilities.parseInt(parts[2]);
            entry.country = parts[3];
            entry.state = parts.length >= 5 ? Utilities.parseInt(parts[4]) : 0;
            return entry;
        }
    }

    public boolean hasError() {
        return lastErrorId > seenErrorId;
    }

    public void registerError() {
        final boolean hadError = hasError();
        MessagesController.getMainSettings(currentAccount).edit().putInt("smsjobs_error", ++lastErrorId).apply();
        if (hasError() != hadError) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);
        }
    }

    public void seenError() {
        if (seenErrorId < lastErrorId) {
            final boolean hadError = hasError();
            MessagesController.getMainSettings(currentAccount).edit().putInt("smsjobs_seen_error", seenErrorId = lastErrorId).apply();
            if (hasError() != hadError) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);
            }
        }
    }
}
