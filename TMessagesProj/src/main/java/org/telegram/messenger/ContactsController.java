/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import com.google.android.exoplayer2.util.Log;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContactsController extends BaseController {

    private Account systemAccount;
    private boolean loadingContacts;
    private final Object loadContactsSync = new Object();
    private boolean ignoreChanges;
    private boolean contactsSyncInProgress;
    private final Object observerLock = new Object();
    public boolean contactsLoaded;
    public boolean doneLoadingContacts;
    private boolean contactsBookLoaded;
    private boolean migratingContacts;
    private String lastContactsVersions = "";
    private ArrayList<Long> delayedContactsUpdate = new ArrayList<>();
    private String inviteLink;
    private boolean updatingInviteLink;
    private HashMap<String, String> sectionsToReplace = new HashMap<>();

    private int loadingGlobalSettings;
    private int loadingDeleteInfo;
    private int deleteAccountTTL;
    private int[] loadingPrivacyInfo = new int[PRIVACY_RULES_TYPE_COUNT];
    private ArrayList<TLRPC.PrivacyRule> lastseenPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> groupPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> callPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> p2pPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> profilePhotoPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> bioPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> forwardsPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> phonePrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> addedByPhonePrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> voiceMessagesRules;
    private ArrayList<TLRPC.PrivacyRule> birthdayPrivacyRules;
    private TLRPC.TL_globalPrivacySettings globalPrivacySettings;

    public final static int PRIVACY_RULES_TYPE_LASTSEEN = 0;
    public final static int PRIVACY_RULES_TYPE_INVITE = 1;
    public final static int PRIVACY_RULES_TYPE_CALLS = 2;
    public final static int PRIVACY_RULES_TYPE_P2P = 3;
    public final static int PRIVACY_RULES_TYPE_PHOTO = 4;
    public final static int PRIVACY_RULES_TYPE_FORWARDS = 5;
    public final static int PRIVACY_RULES_TYPE_PHONE = 6;
    public final static int PRIVACY_RULES_TYPE_ADDED_BY_PHONE = 7;
    public final static int PRIVACY_RULES_TYPE_VOICE_MESSAGES = 8;
    public final static int PRIVACY_RULES_TYPE_BIO = 9;
    public final static int PRIVACY_RULES_TYPE_MESSAGES = 10;
    public final static int PRIVACY_RULES_TYPE_BIRTHDAY = 11;

    public final static int PRIVACY_RULES_TYPE_COUNT = 12;

    private class MyContentObserver extends ContentObserver {

        private Runnable checkRunnable = () -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                    ContactsController.getInstance(a).checkContacts();
                }
            }
        };

        public MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            synchronized (observerLock) {
                if (ignoreChanges) {
                    return;
                }
            }
            Utilities.globalQueue.cancelRunnable(checkRunnable);
            Utilities.globalQueue.postRunnable(checkRunnable, 500);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
    }

    private static Locale cachedCollatorLocale;
    private static Collator cachedCollator;
    public static Collator getLocaleCollator() {
        if (cachedCollator == null || cachedCollatorLocale != Locale.getDefault()) {
            try {
                cachedCollator = Collator.getInstance(cachedCollatorLocale = Locale.getDefault());
                cachedCollator.setStrength(Collator.SECONDARY);
            } catch (Exception e) {
                FileLog.e(e, true);
            }
        }
        if (cachedCollator == null) {
            try {
                cachedCollator = Collator.getInstance();
                cachedCollator.setStrength(Collator.SECONDARY);
            } catch (Exception e) {
                FileLog.e(e, true);
            }
        }
        if (cachedCollator == null) {
            cachedCollator = new Collator() {
                @Override
                public int compare(String source, String target) {
                    if (source == null || target == null) {
                        return 0;
                    }
                    return source.compareTo(target);
                }
                @Override
                public CollationKey getCollationKey(String source) {
                    return null;
                }
                @Override
                public int hashCode() {
                    return 0;
                }
            };
        }
        return cachedCollator;
    }

    public static class Contact {
        public int contact_id;
        public String key;
        public String provider;
        public boolean isGoodProvider;
        public ArrayList<String> phones = new ArrayList<>(4);
        public ArrayList<String> phoneTypes = new ArrayList<>(4);
        public ArrayList<String> shortPhones = new ArrayList<>(4);
        public ArrayList<Integer> phoneDeleted = new ArrayList<>(4);
        public String first_name;
        public String last_name;
        public boolean namesFilled;
        public int imported;
        public TLRPC.User user;

        public String getLetter() {
            return getLetter(first_name, last_name);
        }

        public static String getLetter(String first_name, String last_name) {
            String key;
            if (!TextUtils.isEmpty(first_name)) {
                return first_name.substring(0, 1);
            } else if (!TextUtils.isEmpty(last_name)) {
                return last_name.substring(0, 1);
            } else {
                return "#";
            }
        }
    }

    private String[] projectionPhones = {
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
    };
    private String[] projectionNames = {
            ContactsContract.CommonDataKinds.StructuredName.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
    };

    public HashMap<String, Contact> contactsBook = new HashMap<>();
    public HashMap<String, Contact> contactsBookSPhones = new HashMap<>();
    public ArrayList<Contact> phoneBookContacts = new ArrayList<>();
    public HashMap<String, ArrayList<Object>> phoneBookSectionsDict = new HashMap<>();
    public ArrayList<String> phoneBookSectionsArray = new ArrayList<>();
    public HashMap<String, Contact> phoneBookByShortPhones = new HashMap<>();

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>();
    public ConcurrentHashMap<Long, TLRPC.TL_contact> contactsDict = new ConcurrentHashMap<>(20, 1.0f, 2);
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<>();

    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersMutualSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersMutualSectionsArray = new ArrayList<>();

    public HashMap<String, TLRPC.TL_contact> contactsByPhone = new HashMap<>();
    public HashMap<String, TLRPC.TL_contact> contactsByShortPhone = new HashMap<>();

    private int completedRequestsCount;
    
    private static volatile ContactsController[] Instance = new ContactsController[UserConfig.MAX_ACCOUNT_COUNT];
    public static ContactsController getInstance(int num) {
        ContactsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ContactsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ContactsController(num);
                }
            }
        }
        return localInstance;
    }

    public ContactsController(int instance) {
        super(instance);
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        if (preferences.getBoolean("needGetStatuses", false)) {
            reloadContactsStatuses();
        }

        sectionsToReplace.put("À", "A");
        sectionsToReplace.put("Á", "A");
        sectionsToReplace.put("Ä", "A");
        sectionsToReplace.put("Ù", "U");
        sectionsToReplace.put("Ú", "U");
        sectionsToReplace.put("Ü", "U");
        sectionsToReplace.put("Ì", "I");
        sectionsToReplace.put("Í", "I");
        sectionsToReplace.put("Ï", "I");
        sectionsToReplace.put("È", "E");
        sectionsToReplace.put("É", "E");
        sectionsToReplace.put("Ê", "E");
        sectionsToReplace.put("Ë", "E");
        sectionsToReplace.put("Ò", "O");
        sectionsToReplace.put("Ó", "O");
        sectionsToReplace.put("Ö", "O");
        sectionsToReplace.put("Ç", "C");
        sectionsToReplace.put("Ñ", "N");
        sectionsToReplace.put("Ÿ", "Y");
        sectionsToReplace.put("Ý", "Y");
        sectionsToReplace.put("Ţ", "Y");

        if (instance == 0) {
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    if (hasContactsPermission()) {
                        ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, new MyContentObserver());
                    }
                } catch (Throwable ignore) {

                }
            });
        }
    }

    public void cleanup() {
        contactsBook.clear();
        contactsBookSPhones.clear();
        phoneBookContacts.clear();
        contacts.clear();
        contactsDict.clear();
        usersSectionsDict.clear();
        usersMutualSectionsDict.clear();
        sortedUsersSectionsArray.clear();
        sortedUsersMutualSectionsArray.clear();
        delayedContactsUpdate.clear();
        contactsByPhone.clear();
        contactsByShortPhone.clear();
        phoneBookSectionsDict.clear();
        phoneBookSectionsArray.clear();
        phoneBookByShortPhones.clear();

        loadingContacts = false;
        contactsSyncInProgress = false;
        doneLoadingContacts = false;
        contactsLoaded = false;
        contactsBookLoaded = false;
        lastContactsVersions = "";
        loadingGlobalSettings = 0;
        loadingDeleteInfo = 0;
        deleteAccountTTL = 0;
        Arrays.fill(loadingPrivacyInfo, 0);
        lastseenPrivacyRules = null;
        groupPrivacyRules = null;
        callPrivacyRules = null;
        p2pPrivacyRules = null;
        profilePhotoPrivacyRules = null;
        bioPrivacyRules = null;
        birthdayPrivacyRules = null;
        forwardsPrivacyRules = null;
        phonePrivacyRules = null;

        Utilities.globalQueue.postRunnable(() -> {
            migratingContacts = false;
            completedRequestsCount = 0;
        });
    }

    public void checkInviteText() {
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        inviteLink = preferences.getString("invitelink", null);
        int time = preferences.getInt("invitelinktime", 0);
        if (!updatingInviteLink && (inviteLink == null || Math.abs(System.currentTimeMillis() / 1000 - time) >= 86400)) {
            updatingInviteLink = true;
            TLRPC.TL_help_getInviteText req = new TLRPC.TL_help_getInviteText();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    final TLRPC.TL_help_inviteText res = (TLRPC.TL_help_inviteText) response;
                    if (res.message.length() != 0) {
                        AndroidUtilities.runOnUIThread(() -> {
                            updatingInviteLink = false;
                            SharedPreferences preferences1 = MessagesController.getMainSettings(currentAccount);
                            SharedPreferences.Editor editor = preferences1.edit();
                            editor.putString("invitelink", inviteLink = res.message);
                            editor.putInt("invitelinktime", (int) (System.currentTimeMillis() / 1000));
                            editor.commit();
                        });
                    }
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    public String getInviteText(int contacts) {
        String link = inviteLink == null ? "https://telegram.org/dl" : inviteLink;
        if (contacts <= 1) {
            return LocaleController.formatString("InviteText2", R.string.InviteText2, link);
        } else {
            try {
                return String.format(LocaleController.getPluralString("InviteTextNum", contacts), contacts, link);
            } catch (Exception e) {
                return LocaleController.formatString("InviteText2", R.string.InviteText2, link);
            }
        }
    }

    public void checkAppAccount() {
        systemAccount = null;
        Utilities.globalQueue.postRunnable(() -> {
            AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
            try {
                Account[] accounts = am.getAccountsByType("org.telegram.messenger");
                for (int a = 0; a < accounts.length; a++) {
                    Account acc = accounts[a];
                    boolean found = false;
                    for (int b = 0; b < UserConfig.MAX_ACCOUNT_COUNT; b++) {
                        TLRPC.User user = UserConfig.getInstance(b).getCurrentUser();
                        if (user != null) {
                            if (acc.name.equals("" + user.id)) {
                                if (b == currentAccount) {
                                    systemAccount = acc;
                                }
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        try {
                            am.removeAccount(accounts[a], null, null);
                        } catch (Exception ignore) {

                        }
                    }

                }
            } catch (Throwable ignore) {

            }
            if (getUserConfig().isClientActivated()) {
                readContacts();
                if (systemAccount == null) {
                    try {
                        systemAccount = new Account("" + getUserConfig().getClientUserId(), "org.telegram.messenger");
                        am.addAccountExplicitly(systemAccount, "", null);
                    } catch (Exception ignore) {

                    }
                }
            }
        });
    }

    public void deleteUnknownAppAccounts() {
        try {
            systemAccount = null;
            AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
            Account[] accounts = am.getAccountsByType("org.telegram.messenger");
            for (int a = 0; a < accounts.length; a++) {
                Account acc = accounts[a];
                boolean found = false;
                for (int b = 0; b < UserConfig.MAX_ACCOUNT_COUNT; b++) {
                    TLRPC.User user = UserConfig.getInstance(b).getCurrentUser();
                    if (user != null) {
                        if (acc.name.equals("" + user.id)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    try {
                        am.removeAccount(accounts[a], null, null);
                    } catch (Exception ignore) {

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkContacts() {
        Utilities.globalQueue.postRunnable(() -> {
            if (checkContactsInternal()) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("detected contacts change");
                }
                performSyncPhoneBook(getContactsCopy(contactsBook), true, false, true, false, true, false);
            }
        });
    }

    public void forceImportContacts() {
        Utilities.globalQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("force import contacts");
            }
            performSyncPhoneBook(new HashMap<>(), true, true, true, true, false, false);
        });
    }

    public void syncPhoneBookByAlert(final HashMap<String, Contact> contacts, final boolean first, final boolean schedule, final boolean cancel) {
        Utilities.globalQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("sync contacts by alert");
            }
            performSyncPhoneBook(contacts, true, first, schedule, false, false, cancel);
        });
    }

    public void deleteAllContacts(final Runnable runnable) {
        resetImportedContacts();
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        for (int a = 0, size = contacts.size(); a < size; a++) {
            TLRPC.TL_contact contact = contacts.get(a);
            req.id.add(getMessagesController().getInputUser(contact.user_id));
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                contactsBookSPhones.clear();
                contactsBook.clear();
                completedRequestsCount = 0;
                migratingContacts = false;
                contactsSyncInProgress = false;
                contactsLoaded = false;
                loadingContacts = false;
                contactsBookLoaded = false;
                lastContactsVersions = "";
                AndroidUtilities.runOnUIThread(() -> {
                    AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
                    try {
                        Account[] accounts = am.getAccountsByType("org.telegram.messenger");
                        systemAccount = null;
                        for (int a = 0; a < accounts.length; a++) {
                            Account acc = accounts[a];
                            for (int b = 0; b < UserConfig.MAX_ACCOUNT_COUNT; b++) {
                                TLRPC.User user = UserConfig.getInstance(b).getCurrentUser();
                                if (user != null) {
                                    if (acc.name.equals("" + user.id)) {
                                        am.removeAccount(acc, null, null);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignore) {

                    }
                    try {
                        systemAccount = new Account("" + getUserConfig().getClientUserId(), "org.telegram.messenger");
                        am.addAccountExplicitly(systemAccount, "", null);
                    } catch (Exception ignore) {

                    }
                    getMessagesStorage().putCachedPhoneBook(new HashMap<>(), false, true);
                    getMessagesStorage().putContacts(new ArrayList<>(), true);
                    phoneBookContacts.clear();
                    contacts.clear();
                    contactsDict.clear();
                    usersSectionsDict.clear();
                    usersMutualSectionsDict.clear();
                    sortedUsersSectionsArray.clear();
                    phoneBookSectionsDict.clear();
                    phoneBookSectionsArray.clear();
                    phoneBookByShortPhones.clear();
                    delayedContactsUpdate.clear();
                    sortedUsersMutualSectionsArray.clear();
                    contactsByPhone.clear();
                    contactsByShortPhone.clear();
                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                    loadContacts(false, 0);
                    runnable.run();
                });
            } else {
                AndroidUtilities.runOnUIThread(runnable);
            }
        });
    }

    public void resetImportedContacts() {
        TLRPC.TL_contacts_resetSaved req = new TLRPC.TL_contacts_resetSaved();
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    private boolean checkContactsInternal() {
        boolean reload = false;
        try {
            if (!hasContactsPermission()) {
                return false;
            }
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
            try (Cursor pCur = cr.query(ContactsContract.RawContacts.CONTENT_URI, new String[]{ContactsContract.RawContacts.VERSION}, null, null, null)) {
                if (pCur != null) {
                    StringBuilder currentVersion = new StringBuilder();
                    while (pCur.moveToNext()) {
                        currentVersion.append(pCur.getString(pCur.getColumnIndex(ContactsContract.RawContacts.VERSION)));
                    }
                    String newContactsVersion = currentVersion.toString();
                    if (lastContactsVersions.length() != 0 && !lastContactsVersions.equals(newContactsVersion)) {
                        reload = true;
                    }
                    lastContactsVersions = newContactsVersion;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return reload;
    }

    public void readContacts() {
        synchronized (loadContactsSync) {
            if (loadingContacts) {
                return;
            }
            loadingContacts = true;
        }

        Utilities.stageQueue.postRunnable(() -> {
            if (!contacts.isEmpty() || contactsLoaded) {
                synchronized (loadContactsSync) {
                    loadingContacts = false;
                }
                return;
            }
            loadContacts(true, 0);
        });
    }

    private boolean isNotValidNameString(String src) {
        if (TextUtils.isEmpty(src)) {
            return true;
        }
        int count = 0;
        for (int a = 0, len = src.length(); a < len; a++) {
            char c = src.charAt(a);
            if (c >= '0' && c <= '9') {
                count++;
            }
        }
        return count > 3;
    }

    public HashMap<String, Contact> readContactsFromPhoneBook() {
        if (!getUserConfig().syncContacts) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("contacts sync disabled");
            }
            return new HashMap<>();
        }
        if (!hasContactsPermission()) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("app has no contacts permissions");
            }
            return new HashMap<>();
        }
        Cursor pCur = null;
        HashMap<String, Contact> contactsMap = null;
        try {
            StringBuilder escaper = new StringBuilder();

            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();

            HashMap<String, Contact> shortContacts = new HashMap<>();
            ArrayList<String> idsArr = new ArrayList<>();
            pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectionPhones, null, null, null);

            long time = System.currentTimeMillis();
            int lastContactId = 1;
            if (pCur != null) {
                int count = pCur.getCount();
                if (count > 0) {
                    contactsMap = new HashMap<>(count);
                    while (pCur.moveToNext()) {
                        String number = pCur.getString(1);
                        String accountType = pCur.getString(5);
                        if (accountType == null) {
                            accountType = "";
                        }
                        boolean isGoodAccountType = accountType.indexOf(".sim") != 0;
                        if (TextUtils.isEmpty(number)) {
                            continue;
                        }
                        number = PhoneFormat.stripExceptNumbers(number, true);
                        if (TextUtils.isEmpty(number)) {
                            continue;
                        }

                        String shortNumber = number;

                        if (number.startsWith("+")) {
                            shortNumber = number.substring(1);
                        }

                        String lookup_key = pCur.getString(0);
                        escaper.setLength(0);
                        DatabaseUtils.appendEscapedSQLString(escaper, lookup_key);
                        String key = escaper.toString();

                        Contact existingContact = shortContacts.get(shortNumber);
                        if (existingContact != null) {
                            if (!existingContact.isGoodProvider && !accountType.equals(existingContact.provider)) {
                                escaper.setLength(0);
                                DatabaseUtils.appendEscapedSQLString(escaper, existingContact.key);
                                idsArr.remove(escaper.toString());
                                idsArr.add(key);
                                existingContact.key = lookup_key;
                                existingContact.isGoodProvider = isGoodAccountType;
                                existingContact.provider = accountType;
                            }
                            continue;
                        }

                        if (!idsArr.contains(key)) {
                            idsArr.add(key);
                        }

                        int type = pCur.getInt(2);
                        Contact contact = contactsMap.get(lookup_key);
                        if (contact == null) {
                            contact = new Contact();
                            String displayName = pCur.getString(4);
                            if (displayName == null) {
                                displayName = "";
                            } else {
                                displayName = displayName.trim();
                            }
                            if (isNotValidNameString(displayName)) {
                                contact.first_name = displayName;
                                contact.last_name = "";
                            } else {
                                int spaceIndex = displayName.lastIndexOf(' ');
                                if (spaceIndex != -1) {
                                    contact.first_name = displayName.substring(0, spaceIndex).trim();
                                    contact.last_name = displayName.substring(spaceIndex + 1).trim();
                                } else {
                                    contact.first_name = displayName;
                                    contact.last_name = "";
                                }
                            }
                            contact.provider = accountType;
                            contact.isGoodProvider = isGoodAccountType;
                            contact.key = lookup_key;
                            contact.contact_id = lastContactId++;
                            contactsMap.put(lookup_key, contact);
                        }

                        contact.shortPhones.add(shortNumber);
                        contact.phones.add(number);
                        contact.phoneDeleted.add(0);

                        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                            String custom = pCur.getString(3);
                            contact.phoneTypes.add(custom != null ? custom : LocaleController.getString(R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) {
                            contact.phoneTypes.add(LocaleController.getString(R.string.PhoneHome));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                            contact.phoneTypes.add(LocaleController.getString(R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) {
                            contact.phoneTypes.add(LocaleController.getString(R.string.PhoneWork));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
                            contact.phoneTypes.add(LocaleController.getString(R.string.PhoneMain));
                        } else {
                            contact.phoneTypes.add(LocaleController.getString(R.string.PhoneOther));
                        }
                        shortContacts.put(shortNumber, contact);
                    }
                }
                try {
                    pCur.close();
                } catch (Exception ignore) {

                }
                pCur = null;
            }
            String ids = TextUtils.join(",", idsArr);

            pCur = cr.query(ContactsContract.Data.CONTENT_URI, projectionNames, ContactsContract.CommonDataKinds.StructuredName.LOOKUP_KEY + " IN (" + ids + ") AND " + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
            if (pCur != null) {
                while (pCur.moveToNext()) {
                    String lookup_key = pCur.getString(0);
                    String fname = pCur.getString(1);
                    String sname = pCur.getString(2);
                    String mname = pCur.getString(3);
                    Contact contact = contactsMap != null ? contactsMap.get(lookup_key) : null;
                    if (contact != null && !contact.namesFilled) {
                        if (contact.isGoodProvider) {
                            if (fname != null) {
                                contact.first_name = fname;
                            } else {
                                contact.first_name = "";
                            }
                            if (sname != null) {
                                contact.last_name = sname;
                            } else {
                                contact.last_name = "";
                            }
                            if (!TextUtils.isEmpty(mname)) {
                                if (!TextUtils.isEmpty(contact.first_name)) {
                                    contact.first_name += " " + mname;
                                } else {
                                    contact.first_name = mname;
                                }
                            }
                        } else {
                            if (!isNotValidNameString(fname) && (contact.first_name.contains(fname) || fname.contains(contact.first_name)) ||
                                    !isNotValidNameString(sname) && (contact.last_name.contains(sname) || fname.contains(contact.last_name))) {
                                if (fname != null) {
                                    contact.first_name = fname;
                                } else {
                                    contact.first_name = "";
                                }
                                if (!TextUtils.isEmpty(mname)) {
                                    if (!TextUtils.isEmpty(contact.first_name)) {
                                        contact.first_name += " " + mname;
                                    } else {
                                        contact.first_name = mname;
                                    }
                                }
                                if (sname != null) {
                                    contact.last_name = sname;
                                } else {
                                    contact.last_name = "";
                                }
                            }
                        }
                        contact.namesFilled = true;
                    }
                }
                try {
                    pCur.close();
                } catch (Exception ignore) {

                }
                pCur = null;
            }
            FileLog.d("loading contacts 1 query time = " + (System.currentTimeMillis() - time) + " contactsSize = " + (contactsMap == null ? 0 : contactsMap.size()));
            time = System.currentTimeMillis();

            HashMap<String, PhoneBookContact> phoneBookContactHashMap = new HashMap<>();
            ArrayList<String> phonebookContactsId = new ArrayList<>();

            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, new String[]{
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME
            }, ContactsContract.Contacts.HAS_PHONE_NUMBER + " = ?", new String[]{"0"}, null);

            if (cur != null) {
                while (cur.moveToNext()) {
                    PhoneBookContact phoneBookContact = new PhoneBookContact();
                    phoneBookContact.id = cur.getString(0);
                    phoneBookContact.lookup_key = cur.getString(1);
                    phoneBookContact.name = cur.getString(2);
                    if ((contactsMap != null && contactsMap.get(phoneBookContact.lookup_key) != null) || TextUtils.isEmpty(phoneBookContact.name)) {
                        continue;
                    }
                    phoneBookContactHashMap.put(phoneBookContact.id, phoneBookContact);
                    phonebookContactsId.add(phoneBookContact.id);
                }
                cur.close();
            }
            FileLog.d("loading contacts 2 query time = " + (System.currentTimeMillis() - time) + " phoneBookConacts size = " + phonebookContactsId.size());
            time = System.currentTimeMillis();

            if (!phonebookContactsId.isEmpty()) {
                String[] metadata = new String[4];
                Pattern phonePattern = Pattern.compile(".*(\\+[0-9 \\-]+).*");
                pCur = cr.query(
                        ContactsContract.Data.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                ContactsContract.Data.DATA1,
                                ContactsContract.Data.DATA2,
                                ContactsContract.Data.DATA3,
                                ContactsContract.Data.DATA4,
                        },
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID  + " IN (" + TextUtils.join(", ", phonebookContactsId) + ")",
                        null, null);
                if (pCur != null) {
                    while (pCur.moveToNext()) {
                        String id = pCur.getString(0);
                        PhoneBookContact phoneBookContact = phoneBookContactHashMap.get(id);
                        if (phoneBookContact != null) {
                            metadata[0] = pCur.getString(1);
                            metadata[1] = pCur.getString(2);
                            metadata[2] = pCur.getString(3);
                            metadata[3] = pCur.getString(4);
                            for (int i = 0; i < metadata.length; i++) {
                                if (metadata[i] == null) {
                                    continue;
                                }
                                Matcher matcher = phonePattern.matcher(metadata[i]);
                                if (matcher.matches()) {
                                    phoneBookContact.phone = matcher.group(1).replace(" ", "").replace("-", "");
                                }

                                if (phoneBookContact.phone != null) {
                                    String shortNumber = phoneBookContact.phone;
                                    if (phoneBookContact.phone.startsWith("+")) {
                                        shortNumber = phoneBookContact.phone.substring(1);
                                    }

                                    Contact contact = new Contact();
                                    contact.first_name = phoneBookContact.name;
                                    contact.last_name = "";
                                    contact.contact_id = lastContactId++;
                                    contact.key = phoneBookContact.lookup_key;
                                    contact.phones.add(phoneBookContact.phone);
                                    contact.shortPhones.add(shortNumber);
                                    contact.phoneDeleted.add(0);
                                    contact.phoneTypes.add(LocaleController.getString(R.string.PhoneOther));
                                    if (contactsMap == null) {
                                        contactsMap = new HashMap<>();
                                    }
                                    contactsMap.put(phoneBookContact.lookup_key, contact);
                                    break;
                                }
                            }
                        }
                    }
                    pCur.close();
                }
            }

            FileLog.d("loading contacts 3 query time = " + (System.currentTimeMillis() - time));

        } catch (Throwable e) {
            FileLog.e(e);
            if (contactsMap != null) {
                contactsMap.clear();
            }
        } finally {
            try {
                if (pCur != null) {
                    pCur.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        /*if (BuildVars.LOGS_ENABLED && contactsMap != null) {
            for (HashMap.Entry<String, Contact> entry : contactsMap.entrySet()) {
                Contact contact = entry.getValue();
                FileLog.e("contact = " + contact.first_name + " " + contact.last_name);
                if (contact.first_name.length() == 0 && contact.last_name.length() == 0 && contact.phones.size() > 0) {
                    FileLog.e("warning, empty name for contact = " + contact.key);
                }
                FileLog.e("phones:");
                for (String s : contact.phones) {
                    FileLog.e("phone = " + s);
                }
                FileLog.e("short phones:");
                for (String s : contact.shortPhones) {
                    FileLog.e("short phone = " + s);
                }
            }
        }*/
        return contactsMap != null ? contactsMap : new HashMap<>();
    }

    public HashMap<String, Contact> getContactsCopy(HashMap<String, Contact> original) {
        HashMap<String, Contact> ret = new HashMap<>();
        for (HashMap.Entry<String, Contact> entry : original.entrySet()) {
            Contact copyContact = new Contact();
            Contact originalContact = entry.getValue();
            copyContact.phoneDeleted.addAll(originalContact.phoneDeleted);
            copyContact.phones.addAll(originalContact.phones);
            copyContact.phoneTypes.addAll(originalContact.phoneTypes);
            copyContact.shortPhones.addAll(originalContact.shortPhones);
            copyContact.first_name = originalContact.first_name;
            copyContact.last_name = originalContact.last_name;
            copyContact.contact_id = originalContact.contact_id;
            copyContact.key = originalContact.key;
            ret.put(copyContact.key, copyContact);
        }
        return ret;
    }

    protected void migratePhoneBookToV7(final SparseArray<Contact> contactHashMap) {
        Utilities.globalQueue.postRunnable(() -> {
            if (migratingContacts) {
                return;
            }
            migratingContacts = true;
            HashMap<String, Contact> migratedMap = new HashMap<>();
            HashMap<String, Contact> contactsMap = readContactsFromPhoneBook();
            final HashMap<String, String> contactsBookShort = new HashMap<>();
            for (HashMap.Entry<String, Contact> entry : contactsMap.entrySet()) {
                Contact value = entry.getValue();
                for (int a = 0; a < value.shortPhones.size(); a++) {
                    contactsBookShort.put(value.shortPhones.get(a), value.key);
                }
            }
            for (int b = 0; b < contactHashMap.size(); b++) {
                Contact value = contactHashMap.valueAt(b);
                for (int a = 0; a < value.shortPhones.size(); a++) {
                    String sphone = value.shortPhones.get(a);
                    String key = contactsBookShort.get(sphone);
                    if (key != null) {
                        value.key = key;
                        migratedMap.put(key, value);
                        break;
                    }
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("migrated contacts " + migratedMap.size() + " of " + contactHashMap.size());
            }
            getMessagesStorage().putCachedPhoneBook(migratedMap, true, false);
        });
    }

    protected void performSyncPhoneBook(final HashMap<String, Contact> contactHashMap, final boolean request, final boolean first, final boolean schedule, final boolean force, final boolean checkCount, final boolean canceled) {
        if (!first && !contactsBookLoaded) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            int newPhonebookContacts = 0;
            int serverContactsInPhonebook = 0;
            boolean disableDeletion = true; //disable contacts deletion, because phone numbers can't be compared due to different numbers format
            /*if (schedule) {
                try {
                    AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
                    Account[] accounts = am.getAccountsByType("org.telegram.account");
                    boolean recreateAccount = false;
                    if (getUserConfig().isClientActivated()) {
                        if (accounts.length != 1) {
                            FileLog.e("detected account deletion!");
                            currentAccount = new Account(getUserConfig().getCurrentUser().phone, "org.telegram.account");
                            am.addAccountExplicitly(currentAccount, "", null);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    performWriteContactsToPhoneBook();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }*/

            HashMap<String, Contact> contactShortHashMap = new HashMap<>();
            for (HashMap.Entry<String, Contact> entry : contactHashMap.entrySet()) {
                Contact c = entry.getValue();
                for (int a = 0; a < c.shortPhones.size(); a++) {
                    contactShortHashMap.put(c.shortPhones.get(a), c);
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("start read contacts from phone");
            }
            if (!schedule) {
                checkContactsInternal();
            }
            final HashMap<String, Contact> contactsMap = readContactsFromPhoneBook();
            final HashMap<String, ArrayList<Object>> phoneBookSectionsDictFinal = new HashMap<>();
            final HashMap<String, Contact> phoneBookByShortPhonesFinal = new HashMap<>();
            final ArrayList<String> phoneBookSectionsArrayFinal = new ArrayList<>();

            for (HashMap.Entry<String, Contact> entry : contactsMap.entrySet()) {
                Contact contact = entry.getValue();
                for (int a = 0, size = contact.shortPhones.size(); a < size; a++) {
                    String phone = contact.shortPhones.get(a);
                    phoneBookByShortPhonesFinal.put(phone.substring(Math.max(0, phone.length() - 7)), contact);
                }

                String key = contact.getLetter();
                ArrayList<Object> arrayList = phoneBookSectionsDictFinal.get(key);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    phoneBookSectionsDictFinal.put(key, arrayList);
                    phoneBookSectionsArrayFinal.add(key);
                }
                arrayList.add(contact);
            }

            final HashMap<String, Contact> contactsBookShort = new HashMap<>();
            int alreadyImportedContacts = contactHashMap.size();

            ArrayList<TLRPC.TL_inputPhoneContact> toImport = new ArrayList<>();
            if (!contactHashMap.isEmpty()) {
                for (HashMap.Entry<String, Contact> pair : contactsMap.entrySet()) {
                    String id = pair.getKey();
                    Contact value = pair.getValue();
                    Contact existing = contactHashMap.get(id);
                    if (existing == null) {
                        for (int a = 0; a < value.shortPhones.size(); a++) {
                            Contact c = contactShortHashMap.get(value.shortPhones.get(a));
                            if (c != null) {
                                existing = c;
                                id = existing.key;
                                break;
                            }
                        }
                    }
                    if (existing != null) {
                        value.imported = existing.imported;
                    }

                    boolean nameChanged = existing != null && (!TextUtils.isEmpty(value.first_name) && !existing.first_name.equals(value.first_name) || !TextUtils.isEmpty(value.last_name) && !existing.last_name.equals(value.last_name));
                    if (existing == null || nameChanged) {
                        for (int a = 0; a < value.phones.size(); a++) {
                            String sphone = value.shortPhones.get(a);
                            String sphone9 = sphone.substring(Math.max(0, sphone.length() - 7));
                            contactsBookShort.put(sphone, value);
                            if (existing != null) {
                                int index = existing.shortPhones.indexOf(sphone);
                                if (index != -1) {
                                    Integer deleted = existing.phoneDeleted.get(index);
                                    value.phoneDeleted.set(a, deleted);
                                    if (deleted == 1) {
                                        continue;
                                    }
                                }
                            }
                            if (request) {
                                if (!nameChanged) {
                                    if (contactsByPhone.containsKey(sphone)) {
                                        serverContactsInPhonebook++;
                                        continue;
                                    }
                                    newPhonebookContacts++;
                                }

                                TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                                imp.client_id = value.contact_id;
                                imp.client_id |= ((long) a) << 32;
                                imp.first_name = value.first_name;
                                imp.last_name = value.last_name;
                                imp.phone = value.phones.get(a);
                                toImport.add(imp);
                            }
                        }
                        if (existing != null) {
                            contactHashMap.remove(id);
                        }
                    } else {
                        for (int a = 0; a < value.phones.size(); a++) {
                            String sphone = value.shortPhones.get(a);
                            String sphone9 = sphone.substring(Math.max(0, sphone.length() - 7));
                            contactsBookShort.put(sphone, value);
                            int index = existing.shortPhones.indexOf(sphone);
                            boolean emptyNameReimport = false;
                            if (request) {
                                TLRPC.TL_contact contact = contactsByPhone.get(sphone);
                                if (contact != null) {
                                    TLRPC.User user = getMessagesController().getUser(contact.user_id);
                                    if (user != null) {
                                        serverContactsInPhonebook++;
                                        if (TextUtils.isEmpty(user.first_name) && TextUtils.isEmpty(user.last_name) && (!TextUtils.isEmpty(value.first_name) || !TextUtils.isEmpty(value.last_name))) {
                                            index = -1;
                                            emptyNameReimport = true;
                                        }
                                    }
                                } else if (contactsByShortPhone.containsKey(sphone9)) {
                                    serverContactsInPhonebook++;
                                }
                            }
                            if (index == -1) {
                                if (request) {
                                    if (!emptyNameReimport) {
                                        TLRPC.TL_contact contact = contactsByPhone.get(sphone);
                                        if (contact != null) {
                                            TLRPC.User user = getMessagesController().getUser(contact.user_id);
                                            if (user != null) {
                                                serverContactsInPhonebook++;
                                                String firstName = user.first_name != null ? user.first_name : "";
                                                String lastName = user.last_name != null ? user.last_name : "";
                                                if (firstName.equals(value.first_name) && lastName.equals(value.last_name) || TextUtils.isEmpty(value.first_name) && TextUtils.isEmpty(value.last_name)) {
                                                    continue;
                                                }
                                            } else {
                                                newPhonebookContacts++;
                                            }
                                        } else if (contactsByShortPhone.containsKey(sphone9)) {
                                            serverContactsInPhonebook++;
                                        }
                                    }

                                    TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                                    imp.client_id = value.contact_id;
                                    imp.client_id |= ((long) a) << 32;
                                    imp.first_name = value.first_name;
                                    imp.last_name = value.last_name;
                                    imp.phone = value.phones.get(a);
                                    toImport.add(imp);
                                }
                            } else {
                                value.phoneDeleted.set(a, existing.phoneDeleted.get(index));
                                existing.phones.remove(index);
                                existing.shortPhones.remove(index);
                                existing.phoneDeleted.remove(index);
                                existing.phoneTypes.remove(index);
                            }
                        }
                        if (existing.phones.isEmpty()) {
                            contactHashMap.remove(id);
                        }
                    }
                }
                if (!first && contactHashMap.isEmpty() && toImport.isEmpty() && alreadyImportedContacts == contactsMap.size()) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("contacts not changed!");
                    }
                    return;
                }
                if (request && !contactHashMap.isEmpty() && !contactsMap.isEmpty()) {
                    if (toImport.isEmpty()) {
                        getMessagesStorage().putCachedPhoneBook(contactsMap, false, false);
                    }
                    if (!disableDeletion && !contactHashMap.isEmpty()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            /*if (BuildVars.DEBUG_VERSION) {
                                FileLog.e("need delete contacts");
                                for (HashMap.Entry<Integer, Contact> c : contactHashMap.entrySet()) {
                                    Contact contact = c.getValue();
                                    FileLog.e("delete contact " + contact.first_name + " " + contact.last_name);
                                    for (String phone : contact.phones) {
                                        FileLog.e(phone);
                                    }
                                }
                            }*/

                            final ArrayList<TLRPC.User> toDelete = new ArrayList<>();
                            if (contactHashMap != null && !contactHashMap.isEmpty()) {
                                try {
                                    final HashMap<String, TLRPC.User> contactsPhonesShort = new HashMap<>();

                                    for (int a = 0; a < contacts.size(); a++) {
                                        TLRPC.TL_contact value = contacts.get(a);
                                        TLRPC.User user = getMessagesController().getUser(value.user_id);
                                        if (user == null || TextUtils.isEmpty(user.phone)) {
                                            continue;
                                        }
                                        contactsPhonesShort.put(user.phone, user);
                                    }
                                    int removed = 0;
                                    for (HashMap.Entry<String, Contact> entry : contactHashMap.entrySet()) {
                                        Contact contact = entry.getValue();
                                        boolean was = false;
                                        for (int a = 0; a < contact.shortPhones.size(); a++) {
                                            String phone = contact.shortPhones.get(a);
                                            TLRPC.User user = contactsPhonesShort.get(phone);
                                            if (user != null) {
                                                was = true;
                                                toDelete.add(user);
                                                contact.shortPhones.remove(a);
                                                a--;
                                            }
                                        }
                                        if (!was || contact.shortPhones.size() == 0) {
                                            removed++;
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }

                            if (!toDelete.isEmpty()) {
                                deleteContact(toDelete, false);
                            }
                        });
                    }
                }
            } else if (request) {
                for (HashMap.Entry<String, Contact> pair : contactsMap.entrySet()) {
                    Contact value = pair.getValue();
                    String key = pair.getKey();
                    for (int a = 0; a < value.phones.size(); a++) {
                        if (!force) {
                            String sphone = value.shortPhones.get(a);
                            String sphone9 = sphone.substring(Math.max(0, sphone.length() - 7));
                            TLRPC.TL_contact contact = contactsByPhone.get(sphone);
                            if (contact != null) {
                                TLRPC.User user = getMessagesController().getUser(contact.user_id);
                                if (user != null) {
                                    serverContactsInPhonebook++;
                                    String firstName = user.first_name != null ? user.first_name : "";
                                    String lastName = user.last_name != null ? user.last_name : "";
                                    if (firstName.equals(value.first_name) && lastName.equals(value.last_name) || TextUtils.isEmpty(value.first_name) && TextUtils.isEmpty(value.last_name)) {
                                        continue;
                                    }
                                }
                            } else if (contactsByShortPhone.containsKey(sphone9)) {
                                serverContactsInPhonebook++;
                            }
                        }
                        TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                        imp.client_id = value.contact_id;
                        imp.client_id |= ((long) a) << 32;
                        imp.first_name = value.first_name;
                        imp.last_name = value.last_name;
                        imp.phone = value.phones.get(a);
                        toImport.add(imp);
                    }
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("done processing contacts");
            }

            if (request) {
                if (!toImport.isEmpty()) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("start import contacts");
                        /*for (TLRPC.TL_inputPhoneContact contact : toImport) {
                            FileLog.e("add contact " + contact.first_name + " " + contact.last_name + " " + contact.phone);
                        }*/
                    }

                    final int checkType;
                    if (checkCount && newPhonebookContacts != 0) {
                        if (newPhonebookContacts >= 30) {
                            checkType = 1;
                        } else if (first && alreadyImportedContacts == 0 && contactsByPhone.size() - serverContactsInPhonebook > contactsByPhone.size() / 3 * 2) {
                            checkType = 2;
                        } else {
                            checkType = 0;
                        }
                    } else {
                        checkType = 0;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("new phone book contacts " + newPhonebookContacts + " serverContactsInPhonebook " + serverContactsInPhonebook + " totalContacts " + contactsByPhone.size());
                    }
                    if (checkType != 0) {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.hasNewContactsToImport, checkType, contactHashMap, first, schedule));
                        return;
                    } else if (canceled) {
                        Utilities.stageQueue.postRunnable(() -> {
                            contactsBookSPhones = contactsBookShort;
                            contactsBook = contactsMap;
                            contactsSyncInProgress = false;
                            contactsBookLoaded = true;
                            if (first) {
                                contactsLoaded = true;
                            }
                            if (!delayedContactsUpdate.isEmpty() && contactsLoaded) {
                                applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                delayedContactsUpdate.clear();
                            }
                            getMessagesStorage().putCachedPhoneBook(contactsMap, false, false);
                            AndroidUtilities.runOnUIThread(() -> {
                                mergePhonebookAndTelegramContacts(phoneBookSectionsDictFinal, phoneBookSectionsArrayFinal, phoneBookByShortPhonesFinal);
                                updateUnregisteredContacts();
                                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                                getNotificationCenter().postNotificationName(NotificationCenter.contactsImported);
                            });
                        });
                        return;
                    }

                    final boolean[] hasErrors = new boolean[]{false};
                    final HashMap<String, Contact> contactsMapToSave = new HashMap<>(contactsMap);
                    final SparseArray<String> contactIdToKey = new SparseArray<>();
                    for (HashMap.Entry<String, Contact> entry : contactsMapToSave.entrySet()) {
                        Contact value = entry.getValue();
                        contactIdToKey.put(value.contact_id, value.key);
                    }
                    completedRequestsCount = 0;
                    final int count = (int) Math.ceil(toImport.size() / 500.0);
                    for (int a = 0; a < count; a++) {
                        final TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
                        int start = a * 500;
                        int end = Math.min(start + 500, toImport.size());
                        req.contacts = new ArrayList<>(toImport.subList(start, end));
                        getConnectionsManager().sendRequest(req, (response, error) -> {
                            completedRequestsCount++;
                            if (error == null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("contacts imported");
                                }
                                final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                                if (!res.retry_contacts.isEmpty()) {
                                    for (int a1 = 0; a1 < res.retry_contacts.size(); a1++) {
                                        long id = res.retry_contacts.get(a1);
                                        contactsMapToSave.remove(contactIdToKey.get((int) id));
                                    }
                                    hasErrors[0] = true;
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("result has retry contacts");
                                    }
                                }
                                for (int a1 = 0; a1 < res.popular_invites.size(); a1++) {
                                    TLRPC.TL_popularContact popularContact = res.popular_invites.get(a1);
                                    Contact contact = contactsMap.get(contactIdToKey.get((int) popularContact.client_id));
                                    if (contact != null) {
                                        contact.imported = popularContact.importers;
                                    }
                                }

                                /*if (BuildVars.LOGS_ENABLED) {
                                    for (TLRPC.User user : res.users) {
                                        FileLog.e("received user " + user.first_name + " " + user.last_name + " " + user.phone);
                                    }
                                }*/
                                getMessagesStorage().putUsersAndChats(res.users, null, true, true);
                                ArrayList<TLRPC.TL_contact> cArr = new ArrayList<>();
                                for (int a1 = 0; a1 < res.imported.size(); a1++) {
                                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                                    contact.user_id = res.imported.get(a1).user_id;
                                    cArr.add(contact);
                                }
                                processLoadedContacts(cArr, res.users, 2);
                            } else {
                                for (int a1 = 0; a1 < req.contacts.size(); a1++) {
                                    TLRPC.TL_inputPhoneContact contact = req.contacts.get(a1);
                                    contactsMapToSave.remove(contactIdToKey.get((int) contact.client_id));
                                }
                                hasErrors[0] = true;
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("import contacts error " + error.text);
                                }
                            }
                            if (completedRequestsCount == count) {
                                if (!contactsMapToSave.isEmpty()) {
                                    getMessagesStorage().putCachedPhoneBook(contactsMapToSave, false, false);
                                }
                                Utilities.stageQueue.postRunnable(() -> {
                                    contactsBookSPhones = contactsBookShort;
                                    contactsBook = contactsMap;
                                    contactsSyncInProgress = false;
                                    contactsBookLoaded = true;
                                    if (first) {
                                        contactsLoaded = true;
                                    }
                                    if (!delayedContactsUpdate.isEmpty() && contactsLoaded) {
                                        applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                        delayedContactsUpdate.clear();
                                    }
                                    AndroidUtilities.runOnUIThread(() -> {
                                        mergePhonebookAndTelegramContacts(phoneBookSectionsDictFinal, phoneBookSectionsArrayFinal, phoneBookByShortPhonesFinal);
                                        getNotificationCenter().postNotificationName(NotificationCenter.contactsImported);
                                    });
                                    if (hasErrors[0]) {
                                        Utilities.globalQueue.postRunnable(() -> getMessagesStorage().getCachedPhoneBook(true), 60000 * 5);
                                    }
                                });
                            }
                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagCanCompress);
                    }
                } else {
                    Utilities.stageQueue.postRunnable(() -> {
                        contactsBookSPhones = contactsBookShort;
                        contactsBook = contactsMap;
                        contactsSyncInProgress = false;
                        contactsBookLoaded = true;
                        if (first) {
                            contactsLoaded = true;
                        }
                        if (!delayedContactsUpdate.isEmpty() && contactsLoaded) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            mergePhonebookAndTelegramContacts(phoneBookSectionsDictFinal, phoneBookSectionsArrayFinal, phoneBookByShortPhonesFinal);
                            updateUnregisteredContacts();
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsImported);
                        });
                    });
                }
            } else {
                Utilities.stageQueue.postRunnable(() -> {
                    contactsBookSPhones = contactsBookShort;
                    contactsBook = contactsMap;
                    contactsSyncInProgress = false;
                    contactsBookLoaded = true;
                    if (first) {
                        contactsLoaded = true;
                    }
                    if (!delayedContactsUpdate.isEmpty() && contactsLoaded) {
                        applyContactsUpdates(delayedContactsUpdate, null, null, null);
                        delayedContactsUpdate.clear();
                    }
                    AndroidUtilities.runOnUIThread(() -> mergePhonebookAndTelegramContacts(phoneBookSectionsDictFinal, phoneBookSectionsArrayFinal, phoneBookByShortPhonesFinal));
                });
                if (!contactsMap.isEmpty()) {
                    getMessagesStorage().putCachedPhoneBook(contactsMap, false, false);
                }
            }
        });
    }

    public boolean isLoadingContacts() {
        synchronized (loadContactsSync) {
            return loadingContacts;
        }
    }

    private long getContactsHash(ArrayList<TLRPC.TL_contact> contacts) {
        long acc = 0;
        contacts = new ArrayList<>(contacts);
        Collections.sort(contacts, (tl_contact, tl_contact2) -> {
            if (tl_contact.user_id > tl_contact2.user_id) {
                return 1;
            } else if (tl_contact.user_id < tl_contact2.user_id) {
                return -1;
            }
            return 0;
        });
        int count = contacts.size();
        for (int a = -1; a < count; a++) {
            if (a == -1) {
                acc = MediaDataController.calcHash(acc, getUserConfig().contactsSavedCount);
            } else {
                TLRPC.TL_contact set = contacts.get(a);
                acc = MediaDataController.calcHash(acc, set.user_id);
            }
        }
        return acc;
    }

    public void loadContacts(boolean fromCache, final long hash) {
        synchronized (loadContactsSync) {
            loadingContacts = true;
        }
        if (fromCache) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("load contacts from cache");
            }
            getMessagesStorage().getContacts();
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("load contacts from server");
            }

            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = hash;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts) response;
                    if (hash != 0 && res instanceof TLRPC.TL_contacts_contactsNotModified) {
                        contactsLoaded = true;
                        if (!delayedContactsUpdate.isEmpty() && contactsBookLoaded) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }
                        getUserConfig().lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                        getUserConfig().saveConfig(false);
                        AndroidUtilities.runOnUIThread(() -> {
                            synchronized (loadContactsSync) {
                                loadingContacts = false;
                            }
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                        });
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("load contacts don't change");
                        }
                        return;
                    } else {
                        getUserConfig().contactsSavedCount = res.saved_count;
                        getUserConfig().saveConfig(false);
                    }
                    processLoadedContacts(res.contacts, res.users, 0);
                }
            });
        }
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        //from: 0 - from server, 1 - from db, 2 - from imported contacts
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesController().putUsers(usersArr, from == 1);

            final LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();

            final boolean isEmpty = contactsArr.isEmpty();

            if (from == 2 && !contacts.isEmpty()) {
                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact contact = contactsArr.get(a);
                    if (contactsDict.get(contact.user_id) != null) {
                        contactsArr.remove(a);
                        a--;
                    }
                }
                contactsArr.addAll(contacts);
            }

            for (int a = 0; a < contactsArr.size(); a++) {
                TLRPC.User user = getMessagesController().getUser(contactsArr.get(a).user_id);
                if (user != null) {
                    usersDict.put(user.id, user);
                    //if (BuildVars.DEBUG_VERSION) {
                    //    FileLog.e("loaded user contact " + user.first_name + " " + user.last_name + " " + user.phone);
                    //}
                }
            }

            Utilities.stageQueue.postRunnable(() -> {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("done loading contacts");
                }
                if (from == 1 && (contactsArr.isEmpty() || Math.abs(System.currentTimeMillis() / 1000 - getUserConfig().lastContactsSyncTime) >= 24 * 60 * 60)) {
                    loadContacts(false, getContactsHash(contactsArr));
                    if (contactsArr.isEmpty()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            doneLoadingContacts = true;
                            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                        });
                        return;
                    }
                }
                if (from == 0) {
                    getUserConfig().lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                    getUserConfig().saveConfig(false);
                }

                boolean reloadContacts = false;
                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact contact = contactsArr.get(a);
                    if (MessagesController.getInstance(currentAccount).getUser(contact.user_id) == null && contact.user_id != getUserConfig().getClientUserId()) {
                        contactsArr.remove(a);
                        a--;
                        reloadContacts = true;
                    }
                }
//                loadContacts(false, 0);
//                if (BuildVars.LOGS_ENABLED) {
//                    FileLog.d("contacts are broken, load from server");
//                }
//                AndroidUtilities.runOnUIThread(() -> {
//                    doneLoadingContacts = true;
//                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
//                });

                if (from != 1) {
                    getMessagesStorage().putUsersAndChats(usersArr, null, true, true);
                    getMessagesStorage().putContacts(contactsArr, from != 2);
                }

                final Collator collator = getLocaleCollator();
                Collections.sort(contactsArr, (tl_contact, tl_contact2) -> {
                    TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                    TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                    String name1 = UserObject.getFirstName(user1);
                    String name2 = UserObject.getFirstName(user2);
                    return collator.compare(name1, name2);
                });

                final ConcurrentHashMap<Long, TLRPC.TL_contact> contactsDictionary = new ConcurrentHashMap<>(20, 1.0f, 2);
                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDictMutual = new HashMap<>();
                final ArrayList<String> sortedSectionsArray = new ArrayList<>();
                final ArrayList<String> sortedSectionsArrayMutual = new ArrayList<>();
                HashMap<String, TLRPC.TL_contact> contactsByPhonesDict = null;
                HashMap<String, TLRPC.TL_contact> contactsByPhonesShortDict = null;

                if (!contactsBookLoaded) {
                    contactsByPhonesDict = new HashMap<>();
                    contactsByPhonesShortDict = new HashMap<>();
                }

                final HashMap<String, TLRPC.TL_contact> contactsByPhonesDictFinal = contactsByPhonesDict;
                final HashMap<String, TLRPC.TL_contact> contactsByPhonesShortDictFinal = contactsByPhonesShortDict;

                for (int a = 0; a < contactsArr.size(); a++) {
                    TLRPC.TL_contact value = contactsArr.get(a);
                    TLRPC.User user = usersDict.get(value.user_id);
                    if (user == null) {
                        continue;
                    }
                    contactsDictionary.put(value.user_id, value);
                    if (contactsByPhonesDict != null && !TextUtils.isEmpty(user.phone)) {
                        contactsByPhonesDict.put(user.phone, value);
                        contactsByPhonesShortDict.put(user.phone.substring(Math.max(0, user.phone.length() - 7)), value);
                    }

                    String key = UserObject.getFirstName(user);
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    if (key.length() == 0) {
                        key = "#";
                    } else {
                        key = key.toUpperCase();
                    }
                    String replace = sectionsToReplace.get(key);
                    if (replace != null) {
                        key = replace;
                    }
                    ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        sectionsDict.put(key, arr);
                        sortedSectionsArray.add(key);
                    }
                    arr.add(value);
                    if (user.mutual_contact) {
                        arr = sectionsDictMutual.get(key);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            sectionsDictMutual.put(key, arr);
                            sortedSectionsArrayMutual.add(key);
                        }
                        arr.add(value);
                    }
                }

                Collections.sort(sortedSectionsArray, (s, s2) -> {
                    char cv1 = s.charAt(0);
                    char cv2 = s2.charAt(0);
                    if (cv1 == '#') {
                        return 1;
                    } else if (cv2 == '#') {
                        return -1;
                    }
                    return collator.compare(s, s2);
                });

                Collections.sort(sortedSectionsArrayMutual, (s, s2) -> {
                    char cv1 = s.charAt(0);
                    char cv2 = s2.charAt(0);
                    if (cv1 == '#') {
                        return 1;
                    } else if (cv2 == '#') {
                        return -1;
                    }
                    return collator.compare(s, s2);
                });

                boolean finalReloadContacts = reloadContacts;
                AndroidUtilities.runOnUIThread(() -> {
                    contacts = contactsArr;
                    contactsDict = contactsDictionary;
                    usersSectionsDict = sectionsDict;
                    usersMutualSectionsDict = sectionsDictMutual;
                    sortedUsersSectionsArray = sortedSectionsArray;
                    sortedUsersMutualSectionsArray = sortedSectionsArrayMutual;
                    doneLoadingContacts = true;
                    if (from != 2) {
                        synchronized (loadContactsSync) {
                            loadingContacts = false;
                        }
                    }
                    performWriteContactsToPhoneBook();
                    updateUnregisteredContacts();

                    getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);

                    if (from != 1 && !isEmpty) {
                        saveContactsLoadTime();
                    } else {
                        reloadContactsStatusesMaybe(false);
                    }
                    if (finalReloadContacts) {
                        loadContacts(false, 0);
                    }
                });

                if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                    applyContactsUpdates(delayedContactsUpdate, null, null, null);
                    delayedContactsUpdate.clear();
                }

                if (contactsByPhonesDictFinal != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        Utilities.globalQueue.postRunnable(() -> {
                            contactsByPhone = contactsByPhonesDictFinal;
                            contactsByShortPhone = contactsByPhonesShortDictFinal;
                        });
                        if (contactsSyncInProgress) {
                            return;
                        }
                        contactsSyncInProgress = true;
                        getMessagesStorage().getCachedPhoneBook(false);
                    });
                } else {
                    contactsLoaded = true;
                }
            });
        });
    }

    public boolean isContact(long userId) {
        return contactsDict.get(userId) != null;
    }

    public void reloadContactsStatusesMaybe(boolean force) {
        try {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            long lastReloadStatusTime = preferences.getLong("lastReloadStatusTime", 0);
            if (lastReloadStatusTime < System.currentTimeMillis() - 1000 * 60 * 60 * 3 || force) {
                reloadContactsStatuses();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveContactsLoadTime() {
        try {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            preferences.edit().putLong("lastReloadStatusTime", System.currentTimeMillis()).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void mergePhonebookAndTelegramContacts(final HashMap<String, ArrayList<Object>> phoneBookSectionsDictFinal, final ArrayList<String> phoneBookSectionsArrayFinal, final HashMap<String, Contact> phoneBookByShortPhonesFinal) {
        mergePhonebookAndTelegramContacts(phoneBookSectionsDictFinal, phoneBookSectionsArrayFinal,phoneBookByShortPhonesFinal, true);
    }

    private void mergePhonebookAndTelegramContacts(final HashMap<String, ArrayList<Object>> phoneBookSectionsDictFinal, final ArrayList<String> phoneBookSectionsArrayFinal, final HashMap<String, Contact> phoneBookByShortPhonesFinal, boolean needUpdateLists) {
        final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(contacts);
        Utilities.globalQueue.postRunnable(() -> {
            if(needUpdateLists) {
                for (int a = 0, size = contactsCopy.size(); a < size; a++) {
                    TLRPC.TL_contact value = contactsCopy.get(a);
                    TLRPC.User user = getMessagesController().getUser(value.user_id);
                    if (user == null || TextUtils.isEmpty(user.phone)) {
                        continue;
                    }
                    String phone = user.phone.substring(Math.max(0, user.phone.length() - 7));
                    Contact contact = phoneBookByShortPhonesFinal.get(phone);
                    if (contact != null) {
                        if (contact.user == null) {
                            contact.user = user;
                        }
                    } else {
                        String key = Contact.getLetter(user.first_name, user.last_name);
                        ArrayList<Object> arrayList = phoneBookSectionsDictFinal.get(key);
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                            phoneBookSectionsDictFinal.put(key, arrayList);
                            phoneBookSectionsArrayFinal.add(key);
                        }
                        arrayList.add(user);
                    }
                }
            }
            final Collator collator = getLocaleCollator();
            for (ArrayList<Object> arrayList : phoneBookSectionsDictFinal.values()) {
                Collections.sort(arrayList, (o1, o2) -> {
                    String name1;
                    String name2;
                    if (o1 instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) o1;
                        name1 = ContactsController.formatName(user.first_name, user.last_name);
                    } else if (o1 instanceof Contact) {
                        Contact contact = (Contact) o1;
                        if (contact.user != null) {
                            name1 = ContactsController.formatName(contact.user.first_name, contact.user.last_name);
                        } else {
                            name1 = ContactsController.formatName(contact.first_name, contact.last_name);
                        }
                    } else {
                        name1 = "";
                    }

                    if (o2 instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) o2;
                        name2 = ContactsController.formatName(user.first_name, user.last_name);
                    } else if (o2 instanceof Contact) {
                        Contact contact = (Contact) o2;
                        if (contact.user != null) {
                            name2 = ContactsController.formatName(contact.user.first_name, contact.user.last_name);
                        } else {
                            name2 = ContactsController.formatName(contact.first_name, contact.last_name);
                        }
                    } else {
                        name2 = "";
                    }

                    return collator.compare(name1, name2);
                });
            }
            Collections.sort(phoneBookSectionsArrayFinal, (s, s2) -> {
                char cv1 = s.charAt(0);
                char cv2 = s2.charAt(0);
                if (cv1 == '#') {
                    return 1;
                } else if (cv2 == '#') {
                    return -1;
                }
                return collator.compare(s, s2);
            });
            AndroidUtilities.runOnUIThread(() -> {
                phoneBookSectionsArray = phoneBookSectionsArrayFinal;
                phoneBookByShortPhones = phoneBookByShortPhonesFinal;
                phoneBookSectionsDict = phoneBookSectionsDictFinal;
            });
        });
    }

    private void updateUnregisteredContacts() {
        final HashMap<String, TLRPC.TL_contact> contactsPhonesShort = new HashMap<>();

        for (int a = 0, size = contacts.size(); a < size; a++) {
            TLRPC.TL_contact value = contacts.get(a);
            TLRPC.User user = getMessagesController().getUser(value.user_id);
            if (user == null || TextUtils.isEmpty(user.phone)) {
                continue;
            }
            contactsPhonesShort.put(user.phone, value);
        }

        final ArrayList<Contact> sortedPhoneBookContacts = new ArrayList<>();
        for (HashMap.Entry<String, Contact> pair : contactsBook.entrySet()) {
            Contact value = pair.getValue();

            boolean skip = false;
            for (int a = 0; a < value.phones.size(); a++) {
                String sphone = value.shortPhones.get(a);
                if (contactsPhonesShort.containsKey(sphone) || value.phoneDeleted.get(a) == 1) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }

            sortedPhoneBookContacts.add(value);
        }
        final Collator collator = getLocaleCollator();
        Collections.sort(sortedPhoneBookContacts, (contact, contact2) -> {
            String toCompare1 = contact.first_name;
            if (toCompare1.length() == 0) {
                toCompare1 = contact.last_name;
            }
            String toCompare2 = contact2.first_name;
            if (toCompare2.length() == 0) {
                toCompare2 = contact2.last_name;
            }
            return collator.compare(toCompare1, toCompare2);
        });

        phoneBookContacts = sortedPhoneBookContacts;
    }

    private void buildContactsSectionsArrays(boolean sort) {
        final Collator collator = getLocaleCollator();
        if (sort) {
            Collections.sort(contacts, (tl_contact, tl_contact2) -> {
                TLRPC.User user1 = getMessagesController().getUser(tl_contact.user_id);
                TLRPC.User user2 = getMessagesController().getUser(tl_contact2.user_id);
                String name1 = UserObject.getFirstName(user1);
                String name2 = UserObject.getFirstName(user2);
                return collator.compare(name1, name2);
            });
        }

        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
        final ArrayList<String> sortedSectionsArray = new ArrayList<>();

        for (int a = 0; a < contacts.size(); a++) {
            TLRPC.TL_contact value = contacts.get(a);
            TLRPC.User user = getMessagesController().getUser(value.user_id);
            if (user == null) {
                continue;
            }

            String key = UserObject.getFirstName(user);
            if (key.length() > 1) {
                key = key.substring(0, 1);
            }
            if (key.length() == 0) {
                key = "#";
            } else {
                key = key.toUpperCase();
            }
            String replace = sectionsToReplace.get(key);
            if (replace != null) {
                key = replace;
            }
            ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
            if (arr == null) {
                arr = new ArrayList<>();
                sectionsDict.put(key, arr);
                sortedSectionsArray.add(key);
            }
            arr.add(value);
        }

        Collections.sort(sortedSectionsArray, (s, s2) -> {
            char cv1 = s.charAt(0);
            char cv2 = s2.charAt(0);
            if (cv1 == '#') {
                return 1;
            } else if (cv2 == '#') {
                return -1;
            }
            return collator.compare(s, s2);
        });

        usersSectionsDict = sectionsDict;
        sortedUsersSectionsArray = sortedSectionsArray;
    }

    private boolean hasContactsPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            return ApplicationLoader.applicationContext.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        }
        Cursor cursor = null;
        try {
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
            cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectionPhones, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return false;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return true;
    }

    private boolean hasContactsWritePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            return ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void performWriteContactsToPhoneBookInternal(ArrayList<TLRPC.TL_contact> contactsArray) {
        Cursor cursor = null;
        long time = System.currentTimeMillis();
        try {
            Account account = systemAccount;
            if (!hasContactsPermission() || account == null || !hasContactsWritePermission()) {
                return;
            }
            final SharedPreferences settings = MessagesController.getMainSettings(currentAccount);
            final boolean forceUpdate = !settings.getBoolean("contacts_updated_v7", false);
            if (forceUpdate) {
                settings.edit().putBoolean("contacts_updated_v7", true).commit();
            }
            final ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI;
            cursor = contentResolver.query(rawContactUri, new String[]{BaseColumns._ID, ContactsContract.RawContacts.SYNC2}, null, null, null);
            LongSparseArray<Long> bookContacts = new LongSparseArray<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    bookContacts.put(cursor.getLong(1), cursor.getLong(0));
                }
                cursor.close();
                cursor = null;

                FileLog.d("performWriteContactsToPhoneBookInternal contacts array " + contactsArray.size() + " " + forceUpdate + " bookContactsSize=" + bookContacts.size() + " currentAccount=" + currentAccount);
                ArrayList<ContentProviderOperation> query = null;
                for (int a = 0; a < contactsArray.size(); a++) {
                    TLRPC.TL_contact u = contactsArray.get(a);
                    if (forceUpdate || bookContacts.indexOfKey(u.user_id) < 0) {
                        if (query == null) {
                            query = new ArrayList<>();
                        }
                        applyContactToPhoneBook(query, getMessagesController().getUser(u.user_id));
                        if (query.size() > 450) {
                            contentResolver.applyBatch(ContactsContract.AUTHORITY, query);
                            query.clear();
                        }
                    }
                }
                if (query != null && !query.isEmpty()) {
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, query);
                    query.clear();
                }

            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        FileLog.d("performWriteContactsToPhoneBookInternal " + (System.currentTimeMillis() - time)) ;
    }

    private void performWriteContactsToPhoneBook() {
        final ArrayList<TLRPC.TL_contact> contactsArray = new ArrayList<>(contacts);
        Utilities.phoneBookQueue.postRunnable(() -> performWriteContactsToPhoneBookInternal(contactsArray));
    }

    private void applyContactsUpdates(ArrayList<Long> ids, ConcurrentHashMap<Long, TLRPC.User> userDict, ArrayList<TLRPC.TL_contact> newC, ArrayList<Long> contactsTD) {
        if (newC == null || contactsTD == null) {
            newC = new ArrayList<>();
            contactsTD = new ArrayList<>();
            for (int a = 0; a < ids.size(); a++) {
                Long uid = ids.get(a);
                if (uid > 0) {
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = uid;
                    newC.add(contact);
                } else if (uid < 0) {
                    contactsTD.add(-uid);
                }
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("process update - contacts add = " + newC.size() + " delete = " + contactsTD.size());
        }

        StringBuilder toAdd = new StringBuilder();
        StringBuilder toDelete = new StringBuilder();
        boolean reloadContacts = false;

        for (int a = 0; a < newC.size(); a++) {
            TLRPC.TL_contact newContact = newC.get(a);
            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(newContact.user_id);
            }
            if (user == null) {
                user = getMessagesController().getUser(newContact.user_id);
            } else {
                getMessagesController().putUser(user, true);
            }
            if (user == null || TextUtils.isEmpty(user.phone)) {
                reloadContacts = true;
                continue;
            }

            Contact contact = contactsBookSPhones.get(user.phone);
            if (contact != null) {
                int index = contact.shortPhones.indexOf(user.phone);
                if (index != -1) {
                    contact.phoneDeleted.set(index, 0);
                }
            }
            if (toAdd.length() != 0) {
                toAdd.append(",");
            }
            toAdd.append(user.phone);
        }

        for (int a = 0; a < contactsTD.size(); a++) {
            final Long uid = contactsTD.get(a);
            Utilities.phoneBookQueue.postRunnable(() -> deleteContactFromPhoneBook(uid));

            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(uid);
            }
            if (user == null) {
                user = getMessagesController().getUser(uid);
            } else {
                getMessagesController().putUser(user, true);
            }
            if (user == null) {
                reloadContacts = true;
                continue;
            }

            if (!TextUtils.isEmpty(user.phone)) {
                Contact contact = contactsBookSPhones.get(user.phone);
                if (contact != null) {
                    int index = contact.shortPhones.indexOf(user.phone);
                    if (index != -1) {
                        contact.phoneDeleted.set(index, 1);
                    }
                }
                if (toDelete.length() != 0) {
                    toDelete.append(",");
                }
                toDelete.append(user.phone);
            }
        }

        if (toAdd.length() != 0 || toDelete.length() != 0) {
            getMessagesStorage().applyPhoneBookUpdates(toAdd.toString(), toDelete.toString());
        }

        if (reloadContacts) {
            Utilities.stageQueue.postRunnable(() -> loadContacts(false, 0));
        } else {
            final ArrayList<TLRPC.TL_contact> newContacts = newC;
            final ArrayList<Long> contactsToDelete = contactsTD;
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < newContacts.size(); a++) {
                    TLRPC.TL_contact contact = newContacts.get(a);
                    if (contactsDict.get(contact.user_id) == null) {
                        contacts.add(contact);
                        contactsDict.put(contact.user_id, contact);
                    }
                }
                for (int a = 0; a < contactsToDelete.size(); a++) {
                    Long uid = contactsToDelete.get(a);
                    TLRPC.TL_contact contact = contactsDict.get(uid);
                    if (contact != null) {
                        contacts.remove(contact);
                        contactsDict.remove(uid);
                    }
                }
                if (!newContacts.isEmpty()) {
                    updateUnregisteredContacts();
                    performWriteContactsToPhoneBook();
                }
                performSyncPhoneBook(getContactsCopy(contactsBook), false, false, false, false, true, false);
                buildContactsSectionsArrays(!newContacts.isEmpty());
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
            });
        }
    }

    public void processContactsUpdates(ArrayList<Long> ids, ConcurrentHashMap<Long, TLRPC.User> userDict) {
        final ArrayList<TLRPC.TL_contact> newContacts = new ArrayList<>();
        final ArrayList<Long> contactsToDelete = new ArrayList<>();
        for (Long uid : ids) {
            if (uid > 0) {
                TLRPC.TL_contact contact = new TLRPC.TL_contact();
                contact.user_id = uid;
                newContacts.add(contact);
                if (!delayedContactsUpdate.isEmpty()) {
                    int idx = delayedContactsUpdate.indexOf(-uid);
                    if (idx != -1) {
                        delayedContactsUpdate.remove(idx);
                    }
                }
            } else if (uid < 0) {
                contactsToDelete.add(-uid);
                if (!delayedContactsUpdate.isEmpty()) {
                    int idx = delayedContactsUpdate.indexOf(-uid);
                    if (idx != -1) {
                        delayedContactsUpdate.remove(idx);
                    }
                }
            }
        }
        if (!contactsToDelete.isEmpty()) {
            getMessagesStorage().deleteContacts(contactsToDelete);
        }
        if (!newContacts.isEmpty()) {
            getMessagesStorage().putContacts(newContacts, false);
        }
        if (!contactsLoaded || !contactsBookLoaded) {
            delayedContactsUpdate.addAll(ids);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delay update - contacts add = " + newContacts.size() + " delete = " + contactsToDelete.size());
            }
        } else {
            applyContactsUpdates(ids, userDict, newContacts, contactsToDelete);
        }
    }

    public long addContactToPhoneBook(TLRPC.User user, boolean check) {
        if (systemAccount == null || user == null) {
            return -1;
        }
        if (!hasContactsWritePermission()) {
            return -1;
        }
        long res = -1;
        synchronized (observerLock) {
            ignoreChanges = true;
        }
        ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
        if (check) {
            try {
                Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, systemAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, systemAccount.type).build();
                int value = contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " = " + user.id, null);
            } catch (Exception ignore) {

            }
        }

        ArrayList<ContentProviderOperation> query = new ArrayList<>();
        applyContactToPhoneBook(query, user);

        try {
            ContentProviderResult[] result = contentResolver.applyBatch(ContactsContract.AUTHORITY, query);
            if (result != null && result.length > 0 && result[0].uri != null) {
                res = Long.parseLong(result[0].uri.getLastPathSegment());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        synchronized (observerLock) {
            ignoreChanges = false;
        }
        return res;
    }

    private void applyContactToPhoneBook(ArrayList<ContentProviderOperation> query, TLRPC.User user) {
        if (user == null) {
            return;
        }
        int rawContactId = query.size();
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, systemAccount.name);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, systemAccount.type);
        builder.withValue(ContactsContract.RawContacts.SYNC1, TextUtils.isEmpty(user.phone) ? "" : user.phone);
        builder.withValue(ContactsContract.RawContacts.SYNC2, user.id);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rawContactId);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, user.first_name);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, user.last_name);
        query.add(builder.build());

        final String phoneOrName = TextUtils.isEmpty(user.phone) ? ContactsController.formatName(user.first_name, user.last_name) : "+" + user.phone;

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        builder.withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile");
        builder.withValue(ContactsContract.Data.DATA1, user.id);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Profile");
        builder.withValue(ContactsContract.Data.DATA3, LocaleController.formatString("ContactShortcutMessage", R.string.ContactShortcutMessage, phoneOrName));
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        builder.withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call");
        builder.withValue(ContactsContract.Data.DATA1, user.id);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Voice Call");
        builder.withValue(ContactsContract.Data.DATA3, LocaleController.formatString("ContactShortcutVoiceCall", R.string.ContactShortcutVoiceCall, phoneOrName));
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        builder.withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call.video");
        builder.withValue(ContactsContract.Data.DATA1, user.id);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Video Call");
        builder.withValue(ContactsContract.Data.DATA3, LocaleController.formatString("ContactShortcutVideoCall", R.string.ContactShortcutVideoCall, phoneOrName));
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());
    }

    private void deleteContactFromPhoneBook(long uid) {
        if (!hasContactsPermission()) {
            return;
        }
        synchronized (observerLock) {
            ignoreChanges = true;
        }
        try {
            ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, systemAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, systemAccount.type).build();
            int value = contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " = " + uid, null);
        } catch (Exception e) {
            FileLog.e(e, false);
        }
        synchronized (observerLock) {
            ignoreChanges = false;
        }
    }

    protected void markAsContacted(final String contactId) {
        if (contactId == null) {
            return;
        }
        Utilities.phoneBookQueue.postRunnable(() -> {
            Uri uri = Uri.parse(contactId);
            ContentValues values = new ContentValues();
            values.put(ContactsContract.Contacts.LAST_TIME_CONTACTED, System.currentTimeMillis());
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
            cr.update(uri, values, null, null);
        });
    }

    public void addContact(TLRPC.User user, boolean exception) {
        if (user == null) {
            return;
        }

        TLRPC.TL_contacts_addContact req = new TLRPC.TL_contacts_addContact();
        req.id = getMessagesController().getInputUser(user);
        req.first_name = user.first_name;
        req.last_name = user.last_name;
        req.phone = user.phone;
        req.add_phone_privacy_exception = exception;
        if (req.phone == null) {
            req.phone = "";
        } else if (req.phone.length() > 0 && !req.phone.startsWith("+")) {
            req.phone = "+" + req.phone;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            final TLRPC.Updates res = (TLRPC.Updates) response;
            if (user.photo != null && user.photo.personal) {
                for (int i = 0; i < res.users.size(); i++) {
                    if (res.users.get(i).id == user.id) {
                        res.users.get(i).photo = user.photo;
                    }
                }
            }
            getMessagesController().processUpdates(res, false);

            for (int a = 0; a < res.users.size(); a++) {
                final TLRPC.User u = res.users.get(a);
                if (u.id != user.id) {
                    continue;
                }
                Utilities.phoneBookQueue.postRunnable(() -> addContactToPhoneBook(u, true));
                TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                newContact.user_id = u.id;
                ArrayList<TLRPC.TL_contact> arrayList = new ArrayList<>();
                arrayList.add(newContact);
                getMessagesStorage().putContacts(arrayList, false);

                if (!TextUtils.isEmpty(u.phone)) {
                    CharSequence name = formatName(u.first_name, u.last_name);
                    getMessagesStorage().applyPhoneBookUpdates(u.phone, "");
                    Contact contact = contactsBookSPhones.get(u.phone);
                    if (contact != null) {
                        int index = contact.shortPhones.indexOf(u.phone);
                        if (index != -1) {
                            contact.phoneDeleted.set(index, 0);
                        }
                    }
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                boolean needResort = false;
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User u = res.users.get(a);
                    if (u.contact) {
                        Contact phoneBookContact = contactsBookSPhones.get(u.phone);
                        if (phoneBookContact != null) {
                            String oldKey = phoneBookContact.getLetter();
                            String newKey = Contact.getLetter(user.first_name, user.last_name);
                            if (phoneBookContact.user == null) {
                                phoneBookContact.user = user;
                                if (!oldKey.equals(newKey)) {
                                    ArrayList<Object> arrayList = phoneBookSectionsDict.get(newKey);
                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        phoneBookSectionsDict.put(newKey, arrayList);
                                        phoneBookSectionsArray.add(newKey);
                                    }
                                    arrayList.add(phoneBookContact);

                                    arrayList = phoneBookSectionsDict.get(oldKey);
                                    if (arrayList != null) {
                                        for (Object obj : arrayList) {
                                            if (obj instanceof Contact) {
                                                Contact oldContact = (Contact) obj;
                                                if (oldContact.contact_id == phoneBookContact.contact_id) {
                                                    boolean removed = arrayList.remove(oldContact);
                                                    if (removed && arrayList.isEmpty()) {
                                                        phoneBookSectionsDict.remove(oldKey);
                                                        phoneBookSectionsArray.remove(oldKey);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                needResort = true;
                            }
                        }
                    }
                    if (!u.contact || contactsDict.get(u.id) != null) {
                        continue;
                    }
                    TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                    newContact.user_id = u.id;
                    contacts.add(newContact);
                    contactsDict.put(newContact.user_id, newContact);
                }
                buildContactsSectionsArrays(true);
                if (needResort) {
                    mergePhonebookAndTelegramContacts(phoneBookSectionsDict, phoneBookSectionsArray, phoneBookByShortPhones, false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagCanCompress);
    }

    public void deleteContactsUndoable(Context context, BaseFragment fragment, final ArrayList<TLRPC.User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        HashMap<TLRPC.User, TLRPC.TL_contact> deletedContacts = new HashMap<>();

        for (int i = 0, N = users.size(); i < N; i++) {
            TLRPC.User user = users.get(i);
            TLRPC.TL_contact contact = contactsDict.get(user.id);

            user.contact = false;
            contacts.remove(contact);
            contactsDict.remove(user.id);

            deletedContacts.put(user, contact);
        }
        buildContactsSectionsArrays(false);
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);

        Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(context, fragment.getResourceProvider());
        layout.setTimer();
        layout.textView.setText(LocaleController.formatPluralString("ContactsDeletedUndo", deletedContacts.size()));
        Bulletin.UndoButton undoButton = new Bulletin.UndoButton(context, true, true, fragment.getResourceProvider());
        undoButton.setUndoAction(() -> {
            for (HashMap.Entry<TLRPC.User, TLRPC.TL_contact> entry : deletedContacts.entrySet()) {
                TLRPC.User user = entry.getKey();
                TLRPC.TL_contact contact = entry.getValue();

                user.contact = true;
                contacts.add(contact);
                contactsDict.put(user.id, contact);
            }
            buildContactsSectionsArrays(true);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
        });
        undoButton.setDelayedAction(() -> {
            deleteContact(users, false);
        });
        layout.setButton(undoButton);
        Bulletin bulletin = Bulletin.make(fragment, layout, Bulletin.DURATION_PROLONG);
        bulletin.show();
    }

    public void deleteContact(final ArrayList<TLRPC.User> users, boolean showBulletin) {
        if (users == null || users.isEmpty()) {
            return;
        }
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        final ArrayList<Long> uids = new ArrayList<>();
        for (int a = 0, N = users.size(); a < N; a++) {
            TLRPC.User user = users.get(a);
            getMessagesController().getStoriesController().removeContact(user.id);
            TLRPC.InputUser inputUser = getMessagesController().getInputUser(user);
            if (inputUser == null) {
                continue;
            }
            user.contact = false;
            uids.add(user.id);
            req.id.add(inputUser);
        }
        String userName = users.get(0).first_name;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            getMessagesController().processUpdates((TLRPC.Updates) response, false);
            getMessagesStorage().deleteContacts(uids);
            Utilities.phoneBookQueue.postRunnable(() -> {
                for (TLRPC.User user : users) {
                    deleteContactFromPhoneBook(user.id);
                }
            });

            for (int a = 0; a < users.size(); a++) {
                TLRPC.User user = users.get(a);
                if (TextUtils.isEmpty(user.phone)) {
                    continue;
                }
                getMessagesStorage().applyPhoneBookUpdates(user.phone, "");
                Contact contact = contactsBookSPhones.get(user.phone);
                if (contact != null) {
                    int index = contact.shortPhones.indexOf(user.phone);
                    if (index != -1) {
                        contact.phoneDeleted.set(index, 1);
                    }
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                boolean remove = false;
                for (TLRPC.User user : users) {
                    TLRPC.TL_contact contact = contactsDict.get(user.id);
                    if (contact != null) {
                        remove = true;
                        contacts.remove(contact);
                        contactsDict.remove(user.id);
                    }
                }
                if (remove) {
                    buildContactsSectionsArrays(false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                getNotificationCenter().postNotificationName(NotificationCenter.contactsDidLoad);
                if (showBulletin) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.formatString("DeletedFromYourContacts", R.string.DeletedFromYourContacts, userName));
                }
            });
        });
    }

    private void reloadContactsStatuses() {
        saveContactsLoadTime();
        getMessagesController().clearFullUsers();
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("needGetStatuses", true).commit();
        TLRPC.TL_contacts_getStatuses req = new TLRPC.TL_contacts_getStatuses();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    editor.remove("needGetStatuses").commit();
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    if (!vector.objects.isEmpty()) {
                        ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                        for (Object object : vector.objects) {
                            TLRPC.User toDbUser = new TLRPC.TL_user();
                            TLRPC.TL_contactStatus status = (TLRPC.TL_contactStatus) object;

                            if (status == null) {
                                continue;
                            }
                            if (status.status instanceof TLRPC.TL_userStatusRecently) {
                                status.status.expires = status.status.by_me ? -1000 : -100;
                            } else if (status.status instanceof TLRPC.TL_userStatusLastWeek) {
                                status.status.expires = status.status.by_me ? -1001 : -101;
                            } else if (status.status instanceof TLRPC.TL_userStatusLastMonth) {
                                status.status.expires = status.status.by_me ? -1002 : -102;
                            }

                            TLRPC.User user = getMessagesController().getUser(status.user_id);
                            if (user != null) {
                                user.status = status.status;
                            }
                            toDbUser.status = status.status;
                            dbUsersStatus.add(toDbUser);
                        }
                        getMessagesStorage().updateUsers(dbUsersStatus, true, true, true);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
                });
            }
        });
    }

    public void loadGlobalPrivacySetting() {
        if (loadingGlobalSettings == 0) {
            loadingGlobalSettings = 1;
            TLRPC.TL_account_getGlobalPrivacySettings req = new TLRPC.TL_account_getGlobalPrivacySettings();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    globalPrivacySettings = (TLRPC.TL_globalPrivacySettings) response;
                    loadingGlobalSettings = 2;
                } else {
                    loadingGlobalSettings = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
    }

    public void loadPrivacySettings() {
        if (loadingDeleteInfo == 0) {
            loadingDeleteInfo = 1;
            TLRPC.TL_account_getAccountTTL req = new TLRPC.TL_account_getAccountTTL();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_accountDaysTTL ttl = (TLRPC.TL_accountDaysTTL) response;
                    deleteAccountTTL = ttl.days;
                    loadingDeleteInfo = 2;
                } else {
                    loadingDeleteInfo = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
        loadGlobalPrivacySetting();
        for (int a = 0; a < loadingPrivacyInfo.length; a++) {
            if (loadingPrivacyInfo[a] != 0) {
                continue;
            }
            loadingPrivacyInfo[a] = 1;
            final int num = a;

            TLRPC.TL_account_getPrivacy req = new TLRPC.TL_account_getPrivacy();

            switch (num) {
                case PRIVACY_RULES_TYPE_LASTSEEN:
                    req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
                    break;
                case PRIVACY_RULES_TYPE_INVITE:
                    req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
                    break;
                case PRIVACY_RULES_TYPE_CALLS:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
                    break;
                case PRIVACY_RULES_TYPE_P2P:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneP2P();
                    break;
                case PRIVACY_RULES_TYPE_PHOTO:
                    req.key = new TLRPC.TL_inputPrivacyKeyProfilePhoto();
                    break;
                case PRIVACY_RULES_TYPE_BIO:
                    req.key = new TLRPC.TL_inputPrivacyKeyAbout();
                    break;
                case PRIVACY_RULES_TYPE_FORWARDS:
                    req.key = new TLRPC.TL_inputPrivacyKeyForwards();
                    break;
                case PRIVACY_RULES_TYPE_PHONE:
                    req.key = new TLRPC.TL_inputPrivacyKeyPhoneNumber();
                    break;
                case PRIVACY_RULES_TYPE_VOICE_MESSAGES:
                    req.key = new TLRPC.TL_inputPrivacyKeyVoiceMessages();
                    break;
                case PRIVACY_RULES_TYPE_BIRTHDAY:
                    req.key = new TLRPC.TL_inputPrivacyKeyBirthday();
                    break;
                case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                    req.key = new TLRPC.TL_inputPrivacyKeyAddedByPhone();
                    break;
                default:
                    continue;
            }

            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                    getMessagesController().putUsers(rules.users, false);
                    getMessagesController().putChats(rules.chats, false);

                    switch (num) {
                        case PRIVACY_RULES_TYPE_LASTSEEN:
                            lastseenPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_INVITE:
                            groupPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_CALLS:
                            callPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_P2P:
                            p2pPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_PHOTO:
                            profilePhotoPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_BIO:
                            bioPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_BIRTHDAY:
                            birthdayPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_FORWARDS:
                            forwardsPrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_PHONE:
                            phonePrivacyRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_VOICE_MESSAGES:
                            voiceMessagesRules = rules.rules;
                            break;
                        case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                        default:
                            addedByPhonePrivacyRules = rules.rules;
                            break;
                    }
                    loadingPrivacyInfo[num] = 2;
                } else {
                    loadingPrivacyInfo[num] = 0;
                }
                getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
            }));
        }
        getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
    }

    public void setDeleteAccountTTL(int ttl) {
        deleteAccountTTL = ttl;
    }

    public int getDeleteAccountTTL() {
        return deleteAccountTTL;
    }

    public boolean getLoadingDeleteInfo() {
        return loadingDeleteInfo != 2;
    }

    public boolean getLoadingGlobalSettings() {
        return loadingGlobalSettings != 2;
    }

    public boolean getLoadingPrivacyInfo(int type) {
        return loadingPrivacyInfo[type] != 2;
    }

    public TLRPC.TL_globalPrivacySettings getGlobalPrivacySettings() {
        return globalPrivacySettings;
    }

    public ArrayList<TLRPC.PrivacyRule> getPrivacyRules(int type) {
        switch (type) {
            case PRIVACY_RULES_TYPE_LASTSEEN:
                return lastseenPrivacyRules;
            case PRIVACY_RULES_TYPE_INVITE:
                return groupPrivacyRules;
            case PRIVACY_RULES_TYPE_CALLS:
                return callPrivacyRules;
            case PRIVACY_RULES_TYPE_P2P:
                return p2pPrivacyRules;
            case PRIVACY_RULES_TYPE_PHOTO:
                return profilePhotoPrivacyRules;
            case PRIVACY_RULES_TYPE_BIO:
                return bioPrivacyRules;
            case PRIVACY_RULES_TYPE_BIRTHDAY:
                return birthdayPrivacyRules;
            case PRIVACY_RULES_TYPE_FORWARDS:
                return forwardsPrivacyRules;
            case PRIVACY_RULES_TYPE_PHONE:
                return phonePrivacyRules;
            case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                return addedByPhonePrivacyRules;
            case PRIVACY_RULES_TYPE_VOICE_MESSAGES:
                return voiceMessagesRules;
        }
        return null;
    }

    public void setPrivacyRules(ArrayList<TLRPC.PrivacyRule> rules, int type) {
        switch (type) {
            case PRIVACY_RULES_TYPE_LASTSEEN:
                lastseenPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_INVITE:
                groupPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_CALLS:
                callPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_P2P:
                p2pPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_PHOTO:
                profilePhotoPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_BIO:
                bioPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_BIRTHDAY:
                birthdayPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_FORWARDS:
                forwardsPrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_PHONE:
                phonePrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_ADDED_BY_PHONE:
                addedByPhonePrivacyRules = rules;
                break;
            case PRIVACY_RULES_TYPE_VOICE_MESSAGES:
                voiceMessagesRules = rules;
                break;
        }
        getNotificationCenter().postNotificationName(NotificationCenter.privacyRulesUpdated);
        reloadContactsStatuses();
    }

    public void createOrUpdateConnectionServiceContact(long id, String firstName, String lastName) {
        if (!hasContactsPermission()) {
            return;
        }
        try {
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();

            final Uri groupsURI = ContactsContract.Groups.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
            final Uri rawContactsURI = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

            // 1. Check if we already have the invisible group/label and create it if we don't
            Cursor cursor = resolver.query(groupsURI, new String[]{ContactsContract.Groups._ID},
                    ContactsContract.Groups.TITLE + "=? AND " + ContactsContract.Groups.ACCOUNT_TYPE + "=? AND " + ContactsContract.Groups.ACCOUNT_NAME + "=?",
                    new String[]{"TelegramConnectionService", systemAccount.type, systemAccount.name}, null);
            int groupID;
            if (cursor != null && cursor.moveToFirst()) {
                groupID = cursor.getInt(0);
                /*ops.add(ContentProviderOperation.newUpdate(groupsURI)
                        .withSelection(ContactsContract.Groups._ID+"=?", new String[]{groupID+""})
                        .withValue(ContactsContract.Groups.DELETED, 0)
                        .build());*/
            } else {
                ContentValues values = new ContentValues();
                values.put(ContactsContract.Groups.ACCOUNT_TYPE, systemAccount.type);
                values.put(ContactsContract.Groups.ACCOUNT_NAME, systemAccount.name);
                values.put(ContactsContract.Groups.GROUP_VISIBLE, 0);
                values.put(ContactsContract.Groups.GROUP_IS_READ_ONLY, 1);
                values.put(ContactsContract.Groups.TITLE, "TelegramConnectionService");
                Uri res = resolver.insert(groupsURI, values);
                groupID = Integer.parseInt(res.getLastPathSegment());
            }
            if (cursor != null)
                cursor.close();

            // 2. Find the existing ConnectionService contact and update it or create it
            cursor = resolver.query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Data.RAW_CONTACT_ID},
                    ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + "=?",
                    new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupID + ""}, null);
            int backRef = ops.size();
            if (cursor != null && cursor.moveToFirst()) {
                int contactID = cursor.getInt(0);
                ops.add(ContentProviderOperation.newUpdate(rawContactsURI)
                        .withSelection(ContactsContract.RawContacts._ID + "=?", new String[]{contactID + ""})
                        .withValue(ContactsContract.RawContacts.DELETED, 0)
                        .build());
                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                                new String[]{contactID + "", ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE})
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+99084" + id)
                        .build());
                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                                new String[]{contactID + "", ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE})
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                        .build());
            } else {
                ops.add(ContentProviderOperation.newInsert(rawContactsURI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, systemAccount.type)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, systemAccount.name)
                        .withValue(ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY, 1)
                        .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
                        .build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                        .build());
                // The prefix +990 isn't assigned to anything, so our "phone number" is going to be +990-TG-UserID
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+99084" + id)
                        .build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupID)
                        .build());
            }
            if (cursor != null)
                cursor.close();

            resolver.applyBatch(ContactsContract.AUTHORITY, ops);

        } catch (Exception x) {
            FileLog.e(x);
        }
    }

    public void deleteConnectionServiceContact() {
        if (!hasContactsPermission())
            return;
        try {
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();

            Cursor cursor = resolver.query(ContactsContract.Groups.CONTENT_URI, new String[]{ContactsContract.Groups._ID},
                    ContactsContract.Groups.TITLE + "=? AND " + ContactsContract.Groups.ACCOUNT_TYPE + "=? AND " + ContactsContract.Groups.ACCOUNT_NAME + "=?",
                    new String[]{"TelegramConnectionService", systemAccount.type, systemAccount.name}, null);
            int groupID;
            if (cursor != null && cursor.moveToFirst()) {
                groupID = cursor.getInt(0);
                cursor.close();
            } else {
                if (cursor != null)
                    cursor.close();
                return;
            }
            cursor = resolver.query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Data.RAW_CONTACT_ID},
                    ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + "=?",
                    new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupID + ""}, null);
            int contactID;
            if (cursor != null && cursor.moveToFirst()) {
                contactID = cursor.getInt(0);
                cursor.close();
            } else {
                if (cursor != null)
                    cursor.close();
                return;
            }
            resolver.delete(ContactsContract.RawContacts.CONTENT_URI, ContactsContract.RawContacts._ID + "=?", new String[]{contactID + ""});
            //resolver.delete(ContactsContract.Groups.CONTENT_URI, ContactsContract.Groups._ID+"=?", new String[]{groupID+""});
        } catch (Exception x) {
            FileLog.e(x);
        }
    }

    public static String formatName(TLObject object) {
        if (object instanceof TLRPC.User) {
            return formatName((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            return chat.title;
        } else {
            return LocaleController.getString(R.string.HiddenName);
        }
    }

    @NonNull
    public static String formatName(TLRPC.User user) {
        if (user == null) {
            return "";
        }
        return formatName(user.first_name, user.last_name, 0);
    }

    @NonNull
    public static String formatName(String firstName, String lastName) {
        return formatName(firstName, lastName, 0);
    }

    @NonNull
    public static String formatName(String firstName, String lastName, int maxLength) {
        /*if ((firstName == null || firstName.length() == 0) && (lastName == null || lastName.length() == 0)) {
            return LocaleController.getString(R.string.HiddenName);
        }*/
        if (firstName != null) {
            firstName = firstName.trim();
        }
        if (firstName != null && lastName == null && maxLength > 0 && firstName.contains(" ") ) {
            int i = firstName.indexOf(" ");
            lastName = firstName.substring(i + 1);
            firstName = firstName.substring(0, i);
        }
        if (lastName != null) {
            lastName = lastName.trim();
        }
        StringBuilder result = new StringBuilder((firstName != null ? firstName.length() : 0) + (lastName != null ? lastName.length() : 0) + 1);
        if (LocaleController.nameDisplayOrder == 1) {
            if (firstName != null && firstName.length() > 0) {
                if (maxLength > 0 && firstName.length() > maxLength + 2) {
                    return firstName.substring(0, maxLength) + "…";
                }
                result.append(firstName);
                if (lastName != null && lastName.length() > 0) {
                    result.append(" ");
                    if (maxLength > 0 && result.length() + lastName.length() > maxLength) {
                        result.append(lastName.charAt(0));
                    } else {
                        result.append(lastName);
                    }
                }
            } else if (lastName != null && lastName.length() > 0) {
                if (maxLength > 0 && lastName.length() > maxLength + 2) {
                    return lastName.substring(0, maxLength) + "…";
                }
                result.append(lastName);
            }
        } else {
            if (lastName != null && lastName.length() > 0) {
                if (maxLength > 0 && lastName.length() > maxLength + 2) {
                    return lastName.substring(0, maxLength) + "…";
                }
                result.append(lastName);
                if (firstName != null && firstName.length() > 0) {
                    result.append(" ");
                    if (maxLength > 0 && result.length() + firstName.length() > maxLength) {
                        result.append(firstName.charAt(0));
                    } else {
                        result.append(firstName);
                    }
                }
            } else if (firstName != null && firstName.length() > 0) {
                if (maxLength > 0 && firstName.length() > maxLength + 2) {
                    return firstName.substring(0, maxLength) + "…";
                }
                result.append(firstName);
            }
        }
        return result.toString();
    }

    private class PhoneBookContact {
        String id;
        String lookup_key;
        String name;
        String phone;
    }
}
