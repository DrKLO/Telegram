package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class BirthdayController {

    private static volatile BirthdayController[] Instance = new BirthdayController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static BirthdayController getInstance(int num) {
        BirthdayController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BirthdayController(num);
                }
            }
        }
        return localInstance;
    }

    private final int currentAccount;

    private long lastCheckDate;
    private BirthdayState state;
    private final Set<String> hiddenDays;

    private BirthdayController(int currentAccount) {
        this.currentAccount = currentAccount;

        SharedPreferences prefs = MessagesController.getInstance(currentAccount).getMainSettings();
        lastCheckDate = prefs.getLong("bday_check", 0);
        String contactsString = prefs.getString("bday_contacts", null);
        if (contactsString != null) {
            try {
                final SerializedData data = new SerializedData(Utilities.hexToBytes(contactsString));
                TL_birthdays birthdays = TL_birthdays.TLdeserialize(data, data.readInt32(true), true);
                if (birthdays != null && !birthdays.contacts.isEmpty()) {
                    final ArrayList<Long> uids = new ArrayList<>();
                    for (int i = 0; i < birthdays.contacts.size(); ++i) {
                        uids.add(birthdays.contacts.get(i).contact_id);
                    }
                    MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                        final ArrayList<TLRPC.User> users = MessagesStorage.getInstance(currentAccount).getUsers(uids);
                        AndroidUtilities.runOnUIThread(() -> {
                            TL_account.contactBirthdays contacts = new TL_account.contactBirthdays();
                            contacts.contacts = birthdays.contacts;
                            contacts.users = users;
                            state = BirthdayState.from(contacts);
                        });
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        hiddenDays = prefs.getStringSet("bday_hidden", new HashSet<>());
    }

    private boolean loading;

    public void check() {
        if (loading) return;

        final long now = System.currentTimeMillis();
        boolean shouldCheck = lastCheckDate == 0;
        if (!shouldCheck) {
            shouldCheck = now - lastCheckDate > (BuildVars.DEBUG_PRIVATE_VERSION ? 1000 * 25 : 1000 * 60 * 60 * 12);
        }
        if (!shouldCheck) {
            Calendar checkDate = Calendar.getInstance();
            checkDate.setTimeInMillis(lastCheckDate);
            Calendar nowDate = Calendar.getInstance();
            nowDate.setTimeInMillis(now);

            shouldCheck = (
                checkDate.get(Calendar.DAY_OF_MONTH) != nowDate.get(Calendar.DAY_OF_MONTH) ||
                checkDate.get(Calendar.MONTH) != nowDate.get(Calendar.MONTH) ||
                checkDate.get(Calendar.YEAR) != nowDate.get(Calendar.YEAR)
            );
        }

        if (!shouldCheck) {
            return;
        }

        loading = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new TL_account.getBirthdays(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_account.contactBirthdays) {
                lastCheckDate = System.currentTimeMillis();
                TL_account.contactBirthdays response = (TL_account.contactBirthdays) res;
                state = BirthdayState.from(response);

                MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, null, true, true);

                SharedPreferences.Editor edit = MessagesController.getInstance(currentAccount).getMainSettings().edit();
                edit.putLong("bday_check", lastCheckDate);
                TL_birthdays birthdays = new TL_birthdays();
                birthdays.contacts = response.contacts;
                SerializedData data = new SerializedData(birthdays.getObjectSize());
                birthdays.serializeToStream(data);
                edit.putString("bday_contacts", Utilities.bytesToHex(data.toByteArray()));
                edit.apply();

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);

                loading = false;
            }
        }));
    }

    public boolean contains() {
        final BirthdayState state = getState();
        return state != null && !state.isTodayEmpty();
    }

    public boolean contains(long dialogId) {
        final BirthdayState state = getState();
        return state != null && state.contains(dialogId);
    }

    public BirthdayState getState() {
        if (state == null)
            return null;
        if (hiddenDays.contains(state.todayKey))
            return null;
        return state;
    }

    public void hide() {
        if (state == null) return;
        if (hiddenDays.contains(state.todayKey))
            return;
        hiddenDays.add(state.todayKey);

        SharedPreferences.Editor edit = MessagesController.getInstance(currentAccount).getMainSettings().edit();
        edit.putStringSet("bday_hidden", hiddenDays);
        edit.apply();

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumPromoUpdated);
    }

    public static class BirthdayState {

        public String yesterdayKey;
        public String todayKey;
        public String tomorrowKey;

        public final ArrayList<TLRPC.User> yesterday = new ArrayList<>();
        public final ArrayList<TLRPC.User> today = new ArrayList<>();
        public final ArrayList<TLRPC.User> tomorrow = new ArrayList<>();

        private BirthdayState(String yesterdayKey, String todayKey, String tomorrowKey) {
            this.yesterdayKey = yesterdayKey;
            this.todayKey = todayKey;
            this.tomorrowKey = tomorrowKey;
        }

        public static BirthdayState from(TL_account.contactBirthdays tl) {
            Calendar calendar = Calendar.getInstance();
            int todayDay = calendar.get(Calendar.DAY_OF_MONTH);
            int todayMonth = 1 + calendar.get(Calendar.MONTH);
            int todayYear = calendar.get(Calendar.YEAR);

            calendar.add(Calendar.DATE, -1);
            int yesterdayDay = calendar.get(Calendar.DAY_OF_MONTH);
            int yesterdayMonth = 1 + calendar.get(Calendar.MONTH);
            int yesterdayYear = calendar.get(Calendar.YEAR);

            calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, +1);
            int tomorrowDay = calendar.get(Calendar.DAY_OF_MONTH);
            int tomorrowMonth = 1 + calendar.get(Calendar.MONTH);
            int tomorrowYear = calendar.get(Calendar.YEAR);

            BirthdayState state = new BirthdayState(
                yesterdayDay + "_" + yesterdayMonth + "_" + yesterdayYear,
                todayDay + "_" + todayMonth + "_" + todayYear,
                tomorrowDay + "_" + tomorrowMonth + "_" + tomorrowYear
            );

            for (TL_account.TL_contactBirthday contact : tl.contacts) {
                ArrayList<TLRPC.User> array = null;
                if (contact.birthday.day == todayDay && contact.birthday.month == todayMonth) {
                    array = state.today;
                } else if (contact.birthday.day == yesterdayDay && contact.birthday.month == yesterdayMonth) {
                    array = state.yesterday;
                } else if (contact.birthday.day == tomorrowDay && contact.birthday.month == tomorrowMonth) {
                    array = state.tomorrow;
                }
                if (array != null) {
                    TLRPC.User user = null;
                    for (int i = 0; i < tl.users.size(); ++i) {
                        if (tl.users.get(i).id == contact.contact_id) {
                            user = tl.users.get(i);
                            break;
                        }
                    }
                    if (user != null && !UserObject.isUserSelf(user)) {
                        array.add(user);
                    }
                }
            }

            return state;
        }

        public boolean isTodayEmpty() {
            return today.isEmpty();
        }

        public boolean contains(long did) {
            for (TLRPC.User user : yesterday) {
                if (user.id == did)
                    return true;
            }
            for (TLRPC.User user : today) {
                if (user.id == did)
                    return true;
            }
            for (TLRPC.User user : tomorrow) {
                if (user.id == did)
                    return true;
            }
            return false;
        }
    }

    private static class TL_birthdays extends TLObject {
        public static final int constructor = 0x114ff30d;

        public ArrayList<TL_account.TL_contactBirthday> contacts = new ArrayList<>();

        public static TL_birthdays TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_birthdays.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_birthdays", constructor));
                }
                return null;
            }
            TL_birthdays result = new TL_birthdays();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                contacts.add(TL_account.TL_contactBirthday.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = contacts.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                contacts.get(i).serializeToStream(stream);
            }
        }
    }

    public boolean isToday(long userId) {
        if (state != null && state.contains(userId))
            return true;
        final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(userId);
        if (userFull != null && isToday(userFull.birthday) && !UserObject.areGiftsDisabled(userFull))
            return true;
        return false;
    }

    public static boolean isToday(TLRPC.UserFull userFull) {
        if (userFull == null) return false;
        return isToday(userFull.birthday) && !UserObject.areGiftsDisabled(userFull);
    }

    public static boolean isToday(TL_account.TL_birthday birthday) {
        if (birthday == null) return false;
        Calendar calendar = Calendar.getInstance();
        int todayDay = calendar.get(Calendar.DAY_OF_MONTH);
        int todayMonth = 1 + calendar.get(Calendar.MONTH);
        return birthday.day == todayDay && birthday.month == todayMonth;
    }

}
