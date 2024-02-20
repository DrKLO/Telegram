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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
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
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSuggestionsAvailable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSuggestionsAvailable) {
            if (currentState != STATE_NONE) {
                checkIsEligible(true);
            }
            invalidateStatus();
        }
    }

    public boolean isAvailable() {
        if (currentState != STATE_NONE) {
            checkIsEligible(false);
            loadStatus(false);
        }
        return currentState != STATE_NONE && (isEligible != null || currentStatus != null);
    }

    public void checkIsEligible(boolean force) {
        if (loadedIsEligible && !force || loadingIsEligible) return;
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
            }
        }));
    }

    public void invalidateStatus() {
        loadedStatus = false;
        if (atStatisticsPage) {
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
                selectedSimCard = null;
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
                    SMSJobController.getInstance(currentAccount).setState(SMSJobController.STATE_JOINED);
                    SMSJobController.getInstance(currentAccount).loadStatus(true);
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
        boolean[] finished = new boolean[1];
        sendSMS(
            ApplicationLoader.applicationContext,
            selectedSimCard,
            phone_number,
            job.text,
            (success, reason) /* sent callback */ -> {
                FileLog.d("[smsjob] sms job " + job.job_id + " sent callback: success=" + success + ", reason=" + reason);
                if (!finished[0] && !success) {
                    finished[0] = true;
                    finishJob(job.job_id, phone_number, reason);
                }
            },
            (success, reason) /* delivered callback */ -> {
                FileLog.d("[smsjob] sms job " + job.job_id + " delivered callback: success=" + success + ", reason=" + reason);
                if (!finished[0]) {
                    finished[0] = true;
                    finishJob(job.job_id, phone_number, success ? null : reason);
                }
            }
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
            pushToJournal(job_id, phone_number, error);
            invalidateStatus();
        }));
    }


    private static class PendingSMS {
        public final int id;
        public final Utilities.Callback2<Boolean, String> whenSent;
        public final Utilities.Callback2<Boolean, String> whenDelivered;
        public final boolean received[] = new boolean[2];
        public PendingSMS(int id, Utilities.Callback2<Boolean, String> whenSent, Utilities.Callback2<Boolean, String> whenDelivered) {
            this.id = id;
            this.whenSent = whenSent;
            this.whenDelivered = whenDelivered;
        }
    }

    private static HashMap<Integer, PendingSMS> pending = new HashMap<>();

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
            default: reason = "UNKNOWN_EXCEPTION_" + resultCode; break;
        }
        if (sent && !pending.received[0]) {
            pending.whenSent.run(success, reason);
            pending.received[0] = true;
        } else if (delivered && !pending.received[1]) {
            pending.whenDelivered.run(success, reason);
            pending.received[1] = true;
        }
        if (pending.received[0] && pending.received[1]) {
            SMSJobController.pending.remove(id);
        }
    }

    private static void sendSMS(
        Context context,
        SIM sim,
        String phone,
        String text,
        final Utilities.Callback2<Boolean, String> whenSent,
        final Utilities.Callback2<Boolean, String> whenDelivered
    ) {
        SmsManager smsManager;
        if (sim != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
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

        pending.put(ID, new PendingSMS(ID, whenSent, whenDelivered));

        FileLog.d("[smsjob] sending sms with id " + ID);
        smsManager.sendTextMessage(phone, null, text, sentPI, deliveredPI);
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

        public SIM(int slot, String country) {
            this.id = slot;
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
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            final List<SubscriptionInfo> infos = subscriptionManager.getCompleteActiveSubscriptionInfoList();
            if (infos != null) {
                for (int i = 0; i < infos.size(); ++i) {
                    SIM sim = SIM.from(subscriptionManager, infos.get(i));
                    if (sim != null) {
                        simInfoList.add(sim);
                    }
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 26) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            for (int i = 0; i < 4; ++i) {
                try {
                    if (telephonyManager.getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                        String country = null;
                        SIM sim = new SIM(i, country);
                        simInfoList.add(sim);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
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
                SMSJobController.getInstance(currentAccount).checkIsEligible(true);
            }
        }));
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
    }

    private void clearJournal() {
        journal.clear();
        journalPrefs.edit().clear().apply();
    }

    private void pushToJournal(String job_id, String phone_number, String error) {
        JobEntry entry = new JobEntry();
        entry.job_id = job_id;
        entry.error = error;
        entry.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        entry.country = getCountryFromPhoneNumber(ApplicationLoader.applicationContext, phone_number);
        journal.add(0, entry);
        journalPrefs.edit().putString(entry.job_id, entry.toString()).apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.smsJobStatusUpdate);
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
        public int date;

        @NonNull
        @Override
        public String toString() {
            return job_id + "," + (error == null ? "" : error) + "," + date + "," + country;
        }

        public static JobEntry fromString(String string) {
            String[] parts = string.split(",");
            if (parts.length != 4) return null;
            JobEntry entry = new JobEntry();
            entry.job_id = parts[0];
            entry.error = TextUtils.isEmpty(parts[1]) ? null : parts[1];
            entry.date = Utilities.parseInt(parts[2]);
            entry.country = parts[3];
            return entry;
        }
    }
}
