package org.telegram.ui.Business;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;

public class TimezonesController {

    private static volatile TimezonesController[] Instance = new TimezonesController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }
    public static TimezonesController getInstance(int num) {
        TimezonesController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new TimezonesController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;
    private TimezonesController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    private boolean loading, loaded;
    private final ArrayList<TLRPC.TL_timezone> timezones = new ArrayList<>();

    public ArrayList<TLRPC.TL_timezone> getTimezones() {
        load();
        return timezones;
    }

    public void load() {
        if (loading || loaded) return;
        loading = true;

        SharedPreferences prefs = MessagesController.getInstance(currentAccount).getMainSettings();
        String value = prefs.getString("timezones", null);
        TLRPC.help_timezonesList list = null;
        if (value != null) {
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            list = TLRPC.help_timezonesList.TLdeserialize(serializedData, serializedData.readInt32(false), false);
        }

        timezones.clear();
        if (list != null) {
            timezones.addAll(list.timezones);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.timezonesUpdated);

        TLRPC.TL_help_getTimezonesList req = new TLRPC.TL_help_getTimezonesList();
        req.hash = list == null ? 0 : list.hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_help_timezonesList) {
                timezones.clear();
                timezones.addAll(((TLRPC.TL_help_timezonesList) res).timezones);
                SerializedData data = new SerializedData(res.getObjectSize());
                res.serializeToStream(data);
                prefs.edit().putString("timezones", Utilities.bytesToHex(data.toByteArray())).apply();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.timezonesUpdated);
            }
            loaded = true;
            loading = false;
        }));
    }

    public String getSystemTimezoneId() {
        String systemDefaultId = null;
        ZoneId zone = ZoneId.systemDefault();
        if (zone != null) {
            systemDefaultId = zone.getId();
        }

        if (loading || !loaded) {
            load();
            return systemDefaultId;
        }

        for (int i = 0; i < timezones.size(); ++i) {
            TLRPC.TL_timezone timezone = timezones.get(i);
            if (TextUtils.equals(timezone.id, systemDefaultId)) {
                return systemDefaultId;
            }
        }

        int systemUtcOffset = 0;
        if (zone != null) {
            systemUtcOffset = zone.getRules().getOffset(Instant.now()).getTotalSeconds();
        }
        for (int i = 0; i < timezones.size(); ++i) {
            TLRPC.TL_timezone timezone = timezones.get(i);
            if (systemUtcOffset == timezone.utc_offset) {
                return timezone.id;
            }
        }

        if (!timezones.isEmpty()) {
            return timezones.get(0).id;
        }

        return systemDefaultId;
    }

    public TLRPC.TL_timezone findTimezone(String id) {
        if (id == null) return null;
        load();
        for (int i = 0; i < timezones.size(); ++i) {
            TLRPC.TL_timezone timezone = timezones.get(i);
            if (TextUtils.equals(timezone.id, id)) {
                return timezone;
            }
        }
        return null;
    }

    public String getTimezoneName(TLRPC.TL_timezone timezone, boolean withOffset) {
        if (timezone == null) return null;
        if (withOffset) {
            return timezone.name + ", " + getTimezoneOffsetName(timezone);
        }
        return timezone.name;
    }

    public String getTimezoneOffsetName(TLRPC.TL_timezone timezone) {
        String offset = "GMT";
        if (timezone.utc_offset != 0) {
            offset += (timezone.utc_offset < 0 ? "-" : "+");
            int val = Math.abs(timezone.utc_offset) / 60;
            int hr = (int) (val / 60);
            int min = val % 60;
            offset += (hr < 10 ? "0" : "") + hr;
            offset += ":";
            offset += (min < 10 ? "0" : "") + min;
        }
        return offset;
    }

    public String getTimezoneName(String id, boolean withOffset) {
        TLRPC.TL_timezone timezone = findTimezone(id);
        if (timezone != null) {
            return getTimezoneName(timezone, withOffset);
        }

        ZoneId timeZone = ZoneId.of(id);
        if (timeZone == null) return "";
        String offset = null;
        if (withOffset) {
            offset = timeZone.getRules().getOffset(Instant.now()).getDisplayName(TextStyle.FULL, LocaleController.getInstance().getCurrentLocale());
            if (offset.length() == 1 && offset.charAt(0) == 'Z') {
                offset = "GMT";
            } else {
                offset = "GMT" + offset;
            }
        }
        return timeZone.getId().replace("/", ", ").replace("_", " ") + (offset != null ? ", " + offset : "");
    }
}
