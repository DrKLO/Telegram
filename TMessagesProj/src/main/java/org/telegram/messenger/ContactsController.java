/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ContactsController {

    private Account currentAccount;
    private boolean loadingContacts;
    private static final Object loadContactsSync = new Object();
    private boolean ignoreChanges;
    private boolean contactsSyncInProgress;
    private final Object observerLock = new Object();
    public boolean contactsLoaded;
    private boolean contactsBookLoaded;
    private boolean migratingContacts;
    private String lastContactsVersions = "";
    private ArrayList<Integer> delayedContactsUpdate = new ArrayList<>();
    private String inviteLink;
    private boolean updatingInviteLink;
    private HashMap<String, String> sectionsToReplace = new HashMap<>();

    private int loadingDeleteInfo;
    private int deleteAccountTTL;
    private int loadingLastSeenInfo;
    private int loadingCallsInfo;
    private int loadingGroupInfo;
    private ArrayList<TLRPC.PrivacyRule> privacyRules;
    private ArrayList<TLRPC.PrivacyRule> groupPrivacyRules;
    private ArrayList<TLRPC.PrivacyRule> callPrivacyRules;

    public static class Contact {
        public int contact_id;
        public String key;
        public ArrayList<String> phones = new ArrayList<>();
        public ArrayList<String> phoneTypes = new ArrayList<>();
        public ArrayList<String> shortPhones = new ArrayList<>();
        public ArrayList<Integer> phoneDeleted = new ArrayList<>();
        public String first_name;
        public String last_name;
        public int imported;
    }

    private String[] projectionPhones = {
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
    };
    private String[] projectionNames = {
            ContactsContract.CommonDataKinds.StructuredName.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
    };

    public HashMap<String, Contact> contactsBook = new HashMap<>();
    public HashMap<String, Contact> contactsBookSPhones = new HashMap<>();
    public ArrayList<Contact> phoneBookContacts = new ArrayList<>();

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>();
    public ConcurrentHashMap<Integer, TLRPC.TL_contact> contactsDict = new ConcurrentHashMap<>(20, 1.0f, 2);
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<>();

    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersMutualSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersMutualSectionsArray = new ArrayList<>();

    public HashMap<String, TLRPC.TL_contact> contactsByPhone = new HashMap<>();
    public HashMap<String, TLRPC.TL_contact> contactsByShortPhone = new HashMap<>();

    private int completedRequestsCount;

    private static volatile ContactsController Instance = null;

    public static ContactsController getInstance() {
        ContactsController localInstance = Instance;
        if (localInstance == null) {
            synchronized (ContactsController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ContactsController();
                }
            }
        }
        return localInstance;
    }

    public ContactsController() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
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

        loadingContacts = false;
        contactsSyncInProgress = false;
        contactsLoaded = false;
        contactsBookLoaded = false;
        lastContactsVersions = "";
        loadingDeleteInfo = 0;
        deleteAccountTTL = 0;
        loadingLastSeenInfo = 0;
        loadingGroupInfo = 0;
        loadingCallsInfo = 0;
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                migratingContacts = false;
                completedRequestsCount = 0;
            }
        });
        privacyRules = null;
    }

    public void checkInviteText() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        inviteLink = preferences.getString("invitelink", null);
        int time = preferences.getInt("invitelinktime", 0);
        if (!updatingInviteLink && (inviteLink == null || Math.abs(System.currentTimeMillis() / 1000 - time) >= 86400)) {
            updatingInviteLink = true;
            TLRPC.TL_help_getInviteText req = new TLRPC.TL_help_getInviteText();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (response != null) {
                        final TLRPC.TL_help_inviteText res = (TLRPC.TL_help_inviteText) response;
                        if (res.message.length() != 0) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    updatingInviteLink = false;
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putString("invitelink", inviteLink = res.message);
                                    editor.putInt("invitelinktime", (int) (System.currentTimeMillis() / 1000));
                                    editor.commit();
                                }
                            });
                        }
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
        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
        Account[] accounts;
        try {
            accounts = am.getAccountsByType("org.telegram.account");
            if (accounts != null && accounts.length > 0) {
                for (int a = 0; a < accounts.length; a++) {
                    am.removeAccount(accounts[a], null, null);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        accounts = am.getAccountsByType("org.telegram.messenger");
        boolean recreateAccount = false;
        if (UserConfig.isClientActivated()) {
            if (accounts.length == 1) {
                Account acc = accounts[0];
                if (!acc.name.equals("" + UserConfig.getClientUserId())) {
                    recreateAccount = true;
                } else {
                    currentAccount = acc;
                }
            } else {
                recreateAccount = true;
            }
            readContacts();
        } else {
            if (accounts.length > 0) {
                recreateAccount = true;
            }
        }
        if (recreateAccount) {
            try {
                for (int a = 0; a < accounts.length; a++) {
                    am.removeAccount(accounts[a], null, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (UserConfig.isClientActivated()) {
                try {
                    currentAccount = new Account("" + UserConfig.getClientUserId(), "org.telegram.messenger");
                    am.addAccountExplicitly(currentAccount, "", null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void deleteAllAppAccounts() {
        try {
            AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
            Account[] accounts = am.getAccountsByType("org.telegram.messenger");
            for (int a = 0; a < accounts.length; a++) {
                am.removeAccount(accounts[a], null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkContacts() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (checkContactsInternal()) {
                    FileLog.e("detected contacts change");
                    ContactsController.getInstance().performSyncPhoneBook(ContactsController.getInstance().getContactsCopy(ContactsController.getInstance().contactsBook), true, false, true, false, true, false);
                }
            }
        });
    }

    public void forceImportContacts() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ContactsController.getInstance().performSyncPhoneBook(new HashMap<String, Contact>(), true, true, true, true, false, false);
            }
        });
    }

    public void syncPhoneBookByAlert(final HashMap<String, Contact> contacts, final boolean first, final boolean schedule, final boolean cancel) {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ContactsController.getInstance().performSyncPhoneBook(contacts, true, first, schedule, false, false, cancel);
            }
        });
    }

    public void resetImportedContacts() {
        TLRPC.TL_contacts_resetSaved req = new TLRPC.TL_contacts_resetSaved();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    private boolean checkContactsInternal() {
        boolean reload = false;
        try {
            if (!hasContactsPermission()) {
                return false;
            }
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
            Cursor pCur = null;
            try {
                pCur = cr.query(ContactsContract.RawContacts.CONTENT_URI, new String[]{ContactsContract.RawContacts.VERSION}, null, null, null);
                if (pCur != null) {
                    StringBuilder currentVersion = new StringBuilder();
                    while (pCur.moveToNext()) {
                        int col = pCur.getColumnIndex(ContactsContract.RawContacts.VERSION);
                        currentVersion.append(pCur.getString(col));
                    }
                    String newContactsVersion = currentVersion.toString();
                    if (lastContactsVersions.length() != 0 && !lastContactsVersions.equals(newContactsVersion)) {
                        reload = true;
                    }
                    lastContactsVersions = newContactsVersion;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (pCur != null) {
                    pCur.close();
                }
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

        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!contacts.isEmpty() || contactsLoaded) {
                    synchronized (loadContactsSync) {
                        loadingContacts = false;
                    }
                    return;
                }
                loadContacts(true, 0);
            }
        });
    }

    private HashMap<String, Contact> readContactsFromPhoneBook() {
        HashMap<String, Contact> contactsMap = new HashMap<>();
        Cursor pCur = null;
        try {
            if (!hasContactsPermission()) {
                return contactsMap;
            }
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();

            HashMap<String, Contact> shortContacts = new HashMap<>();
            ArrayList<String> idsArr = new ArrayList<>();
            pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectionPhones, null, null, null);

            int lastContactId = 1;
            if (pCur != null) {
                if (pCur.getCount() > 0) {
                    while (pCur.moveToNext()) {
                        String number = pCur.getString(1);
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

                        if (shortContacts.containsKey(shortNumber)) {
                            continue;
                        }

                        String lookup_key = pCur.getString(0);
                        String key = "'" + lookup_key + "'";
                        if (!idsArr.contains(key)) {
                            idsArr.add(key);
                        }

                        int type = pCur.getInt(2);
                        Contact contact = contactsMap.get(lookup_key);
                        if (contact == null) {
                            contact = new Contact();
                            contact.first_name = "";
                            contact.last_name = "";
                            contact.key = lookup_key;
                            contact.contact_id = lastContactId++;
                            contactsMap.put(lookup_key, contact);
                        }

                        contact.shortPhones.add(shortNumber);
                        contact.phones.add(number);
                        contact.phoneDeleted.add(0);

                        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                            String custom = pCur.getString(3);
                            contact.phoneTypes.add(custom != null ? custom : LocaleController.getString("PhoneMobile", R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneHome", R.string.PhoneHome));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneMobile", R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneWork", R.string.PhoneWork));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneMain", R.string.PhoneMain));
                        } else {
                            contact.phoneTypes.add(LocaleController.getString("PhoneOther", R.string.PhoneOther));
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
                    String sname2 = pCur.getString(3);
                    String mname = pCur.getString(4);
                    Contact contact = contactsMap.get(lookup_key);
                    if (contact != null && TextUtils.isEmpty(contact.first_name) && TextUtils.isEmpty(contact.last_name)) {
                        contact.first_name = fname;
                        contact.last_name = sname;
                        if (contact.first_name == null) {
                            contact.first_name = "";
                        }
                        if (!TextUtils.isEmpty(mname)) {
                            if (contact.first_name.length() != 0) {
                                contact.first_name += " " + mname;
                            } else {
                                contact.first_name = mname;
                            }
                        }
                        if (contact.last_name == null) {
                            contact.last_name = "";
                        }
                        if (TextUtils.isEmpty(contact.last_name) && TextUtils.isEmpty(contact.first_name) && !TextUtils.isEmpty(sname2)) {
                            contact.first_name = sname2;
                        }
                    }
                }
                try {
                    pCur.close();
                } catch (Exception ignore) {

                }
                pCur = null;
            }

        } catch (Exception e) {
            FileLog.e(e);
            contactsMap.clear();
        } finally {
            try {
                if (pCur != null) {
                    pCur.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        /*if (BuildVars.DEBUG_VERSION) {
            for (HashMap.Entry<Integer, Contact> entry : contactsMap.entrySet()) {
                Contact contact = entry.getValue();
                FileLog.e("contact = " + contact.first_name + " " + contact.last_name);
                if (contact.first_name.length() == 0 && contact.last_name.length() == 0 && contact.phones.size() > 0) {
                    FileLog.e("warning, empty name for contact = " + contact.id);
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
        return contactsMap;
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

    protected void migratePhoneBookToV7(final HashMap<Integer, Contact> contactHashMap) {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
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
                for (HashMap.Entry<Integer, Contact> entry : contactHashMap.entrySet()) {
                    Contact value = entry.getValue();
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
                FileLog.d("migrated contacts " + migratedMap.size() + " of " + contactHashMap.size());
                MessagesStorage.getInstance().putCachedPhoneBook(migratedMap, true);
            }
        });
    }

    protected void performSyncPhoneBook(final HashMap<String, Contact> contactHashMap, final boolean request, final boolean first, final boolean schedule, final boolean force, final boolean checkCount, final boolean canceled) {
        if (!first && !contactsBookLoaded) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                int newPhonebookContacts = 0;
                int serverContactsInPhonebook = 0;
                boolean disableDeletion = true; //disable contacts deletion, because phone numbers can't be compared due to different numbers format
                /*if (schedule) {
                    try {
                        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
                        Account[] accounts = am.getAccountsByType("org.telegram.account");
                        boolean recreateAccount = false;
                        if (UserConfig.isClientActivated()) {
                            if (accounts.length != 1) {
                                FileLog.e("detected account deletion!");
                                currentAccount = new Account(UserConfig.getCurrentUser().phone, "org.telegram.account");
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

                FileLog.e("start read contacts from phone");
                if (!schedule) {
                    checkContactsInternal();
                }
                final HashMap<String, Contact> contactsMap = readContactsFromPhoneBook();
                final HashMap<String, Contact> contactsBookShort = new HashMap<>();
                int oldCount = contactHashMap.size();

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
                                        TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
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
                                                TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
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
                    if (!first && contactHashMap.isEmpty() && toImport.isEmpty() && oldCount == contactsMap.size()) {
                        FileLog.e("contacts not changed!");
                        return;
                    }
                    if (request && !contactHashMap.isEmpty() && !contactsMap.isEmpty()) {
                        if (toImport.isEmpty()) {
                            MessagesStorage.getInstance().putCachedPhoneBook(contactsMap, false);
                        }
                        if (!disableDeletion && !contactHashMap.isEmpty()) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
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
                                                TLRPC.User user = MessagesController.getInstance().getUser(value.user_id);
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
                                        deleteContact(toDelete);
                                    }
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
                                    TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
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

                FileLog.e("done processing contacts");

                if (request) {
                    if (!toImport.isEmpty()) {
                        /*if (BuildVars.DEBUG_VERSION) {
                            FileLog.e("start import contacts");
                            for (TLRPC.TL_inputPhoneContact contact : toImport) {
                                FileLog.e("add contact " + contact.first_name + " " + contact.last_name + " " + contact.phone);
                            }
                        }*/

                        final int checkType;
                        if (checkCount) {
                            if (newPhonebookContacts >= 30) {
                                checkType = 1;
                            } else if (first && contactHashMap.isEmpty() && contactsByPhone.size() - serverContactsInPhonebook > contactsByPhone.size() / 3 * 2) {
                                checkType = 2;
                            } else {
                                checkType = 0;
                            }
                        } else {
                            checkType = 0;
                        }
                        FileLog.d("new phone book contacts " + newPhonebookContacts + " serverContactsInPhonebook " + serverContactsInPhonebook + " totalContacts " + contactsByPhone.size());
                        if (checkType != 0) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.hasNewContactsToImport, checkType, contactHashMap, first, schedule);
                                }
                            });
                            return;
                        } else if (canceled) {
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
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
                                    MessagesStorage.getInstance().putCachedPhoneBook(contactsMap, false);
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            updateUnregisteredContacts(contacts);
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsImported);
                                        }
                                    });
                                }
                            });
                            return;
                        }

                        final boolean[] hasErrors = new boolean[]{false};
                        final HashMap<String, Contact> contactsMapToSave = new HashMap<>(contactsMap);
                        final HashMap<Integer, String> contactIdToKey = new HashMap<>();
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
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    completedRequestsCount++;
                                    if (error == null) {
                                        FileLog.e("contacts imported");
                                        final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                                        if (!res.retry_contacts.isEmpty()) {
                                            for (int a = 0; a < res.retry_contacts.size(); a++) {
                                                long id = res.retry_contacts.get(a);
                                                contactsMapToSave.remove(contactIdToKey.get((int) id));
                                            }
                                            hasErrors[0] = true;
                                        }
                                        for (int a = 0; a < res.popular_invites.size(); a++) {
                                            TLRPC.TL_popularContact popularContact = res.popular_invites.get(a);
                                            Contact contact = contactsMap.get(contactIdToKey.get((int) popularContact.client_id));
                                            if (contact != null) {
                                                contact.imported = popularContact.importers;
                                            }
                                        }

                                        /*if (BuildVars.DEBUG_VERSION) {
                                            for (TLRPC.User user : res.users) {
                                                FileLog.e("received user " + user.first_name + " " + user.last_name + " " + user.phone);
                                            }
                                        }*/
                                        MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
                                        ArrayList<TLRPC.TL_contact> cArr = new ArrayList<>();
                                        for (int a = 0; a < res.imported.size(); a++) {
                                            TLRPC.TL_contact contact = new TLRPC.TL_contact();
                                            contact.user_id = res.imported.get(a).user_id;
                                            cArr.add(contact);
                                        }
                                        processLoadedContacts(cArr, res.users, 2);
                                    } else {
                                        for (int a = 0; a < req.contacts.size(); a++) {
                                            TLRPC.TL_inputPhoneContact contact = req.contacts.get(a);
                                            contactsMapToSave.remove(contactIdToKey.get((int) contact.client_id));
                                        }
                                        hasErrors[0] = true;
                                        FileLog.e("import contacts error " + error.text);
                                    }
                                    if (completedRequestsCount == count) {
                                        if (!contactsMapToSave.isEmpty()) {
                                            MessagesStorage.getInstance().putCachedPhoneBook(contactsMapToSave, false);
                                        }
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
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
                                                AndroidUtilities.runOnUIThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsImported);
                                                    }
                                                });
                                                if (hasErrors[0]) {
                                                    Utilities.globalQueue.postRunnable(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            MessagesStorage.getInstance().getCachedPhoneBook(true);
                                                        }
                                                    }, 5000);
                                                }
                                            }
                                        });
                                    }
                                }
                            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagCanCompress);
                        }
                    } else {
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
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
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateUnregisteredContacts(contacts);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsImported);
                                    }
                                });
                            }
                        });
                    }
                } else {
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            contactsBookSPhones = contactsBookShort;
                            contactsBook = contactsMap;
                            contactsSyncInProgress = false;
                            contactsBookLoaded = true;
                            if (first) {
                                contactsLoaded = true;
                            }
                            if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                                applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                delayedContactsUpdate.clear();
                            }
                        }
                    });
                    if (!contactsMap.isEmpty()) {
                        MessagesStorage.getInstance().putCachedPhoneBook(contactsMap, false);
                    }
                }
            }
        });
    }

    public boolean isLoadingContacts() {
        synchronized (loadContactsSync) {
            return loadingContacts;
        }
    }

    private int getContactsHash(ArrayList<TLRPC.TL_contact> contacts) {
        long acc = 0;
        contacts = new ArrayList<>(contacts);
        Collections.sort(contacts, new Comparator<TLRPC.TL_contact>() {
            @Override
            public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                if (tl_contact.user_id > tl_contact2.user_id) {
                    return 1;
                } else if (tl_contact.user_id < tl_contact2.user_id) {
                    return -1;
                }
                return 0;
            }
        });
        int count = contacts.size();
        for (int a = -1; a < count; a++) {
            if (a == -1) {
                acc = ((acc * 20261) + 0x80000000L + UserConfig.contactsSavedCount) % 0x80000000L;
            } else {
                TLRPC.TL_contact set = contacts.get(a);
                acc = ((acc * 20261) + 0x80000000L + set.user_id) % 0x80000000L;
            }
        }
        return (int) acc;
    }

    public void loadContacts(boolean fromCache, final int hash) {
        synchronized (loadContactsSync) {
            loadingContacts = true;
        }
        if (fromCache) {
            FileLog.e("load contacts from cache");
            MessagesStorage.getInstance().getContacts();
        } else {
            FileLog.e("load contacts from server");

            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = hash;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts) response;
                        if (hash != 0 && res instanceof TLRPC.TL_contacts_contactsNotModified) {
                            contactsLoaded = true;
                            if (!delayedContactsUpdate.isEmpty() && contactsBookLoaded) {
                                applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                delayedContactsUpdate.clear();
                            }
                            UserConfig.lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                            UserConfig.saveConfig(false);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (loadContactsSync) {
                                        loadingContacts = false;
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                                }
                            });
                            FileLog.e("load contacts don't change");
                            return;
                        } else {
                            UserConfig.contactsSavedCount = res.saved_count;
                            UserConfig.saveConfig(false);
                        }
                        processLoadedContacts(res.contacts, res.users, 0);
                    }
                }
            });
        }
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        //from: 0 - from server, 1 - from db, 2 - from imported contacts
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().putUsers(usersArr, from == 1);

                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();

                final boolean isEmpty = contactsArr.isEmpty();

                if (!contacts.isEmpty()) {
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
                    TLRPC.User user = MessagesController.getInstance().getUser(contactsArr.get(a).user_id);
                    if (user != null) {
                        usersDict.put(user.id, user);
                        //if (BuildVars.DEBUG_VERSION) {
                        //    FileLog.e("loaded user contact " + user.first_name + " " + user.last_name + " " + user.phone);
                        //}
                    }
                }

                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        FileLog.e("done loading contacts");
                        if (from == 1 && (contactsArr.isEmpty() || Math.abs(System.currentTimeMillis() / 1000 - UserConfig.lastContactsSyncTime) >= 24 * 60 * 60)) {
                            loadContacts(false, getContactsHash(contactsArr));
                            if (contactsArr.isEmpty()) {
                                return;
                            }
                        }
                        if (from == 0) {
                            UserConfig.lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000);
                            UserConfig.saveConfig(false);
                        }

                        for (int a = 0; a < contactsArr.size(); a++) {
                            TLRPC.TL_contact contact = contactsArr.get(a);
                            if (usersDict.get(contact.user_id) == null && contact.user_id != UserConfig.getClientUserId()) {
                                loadContacts(false, 0);
                                FileLog.e("contacts are broken, load from server");
                                return;
                            }
                        }

                        if (from != 1) {
                            MessagesStorage.getInstance().putUsersAndChats(usersArr, null, true, true);
                            MessagesStorage.getInstance().putContacts(contactsArr, from != 2);
                        }

                        Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                            @Override
                            public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                                TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                                TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                                String name1 = UserObject.getFirstName(user1);
                                String name2 = UserObject.getFirstName(user2);
                                return name1.compareTo(name2);
                            }
                        });

                        final ConcurrentHashMap<Integer, TLRPC.TL_contact> contactsDictionary = new ConcurrentHashMap<>(20, 1.0f, 2);
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

                        Collections.sort(sortedSectionsArray, new Comparator<String>() {
                            @Override
                            public int compare(String s, String s2) {
                                char cv1 = s.charAt(0);
                                char cv2 = s2.charAt(0);
                                if (cv1 == '#') {
                                    return 1;
                                } else if (cv2 == '#') {
                                    return -1;
                                }
                                return s.compareTo(s2);
                            }
                        });

                        Collections.sort(sortedSectionsArrayMutual, new Comparator<String>() {
                            @Override
                            public int compare(String s, String s2) {
                                char cv1 = s.charAt(0);
                                char cv2 = s2.charAt(0);
                                if (cv1 == '#') {
                                    return 1;
                                } else if (cv2 == '#') {
                                    return -1;
                                }
                                return s.compareTo(s2);
                            }
                        });

                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                contacts = contactsArr;
                                contactsDict = contactsDictionary;
                                usersSectionsDict = sectionsDict;
                                usersMutualSectionsDict = sectionsDictMutual;
                                sortedUsersSectionsArray = sortedSectionsArray;
                                sortedUsersMutualSectionsArray = sortedSectionsArrayMutual;
                                if (from != 2) {
                                    synchronized (loadContactsSync) {
                                        loadingContacts = false;
                                    }
                                }
                                performWriteContactsToPhoneBook();
                                updateUnregisteredContacts(contactsArr);

                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);

                                if (from != 1 && !isEmpty) {
                                    saveContactsLoadTime();
                                } else {
                                    reloadContactsStatusesMaybe();
                                }
                            }
                        });

                        if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }

                        if (contactsByPhonesDictFinal != null) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utilities.globalQueue.postRunnable(new Runnable() {
                                        @Override
                                        public void run() {
                                            contactsByPhone = contactsByPhonesDictFinal;
                                            contactsByShortPhone = contactsByPhonesShortDictFinal;
                                        }
                                    });
                                    if (contactsSyncInProgress) {
                                        return;
                                    }
                                    contactsSyncInProgress = true;
                                    MessagesStorage.getInstance().getCachedPhoneBook(false);
                                }
                            });
                        } else {
                            contactsLoaded = true;
                        }
                    }
                });
            }
        });
    }

    private void reloadContactsStatusesMaybe() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            long lastReloadStatusTime = preferences.getLong("lastReloadStatusTime", 0);
            if (lastReloadStatusTime < System.currentTimeMillis() - 1000 * 60 * 60 * 24) {
                reloadContactsStatuses();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveContactsLoadTime() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().putLong("lastReloadStatusTime", System.currentTimeMillis()).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void updateUnregisteredContacts(final ArrayList<TLRPC.TL_contact> contactsArr) {
        final HashMap<String, TLRPC.TL_contact> contactsPhonesShort = new HashMap<>();

        for (int a = 0; a < contactsArr.size(); a++) {
            TLRPC.TL_contact value = contactsArr.get(a);
            TLRPC.User user = MessagesController.getInstance().getUser(value.user_id);
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
        Collections.sort(sortedPhoneBookContacts, new Comparator<Contact>() {
            @Override
            public int compare(Contact contact, Contact contact2) {
                String toComapre1 = contact.first_name;
                if (toComapre1.length() == 0) {
                    toComapre1 = contact.last_name;
                }
                String toComapre2 = contact2.first_name;
                if (toComapre2.length() == 0) {
                    toComapre2 = contact2.last_name;
                }
                return toComapre1.compareTo(toComapre2);
            }
        });

        phoneBookContacts = sortedPhoneBookContacts;
    }

    private void buildContactsSectionsArrays(boolean sort) {
        if (sort) {
            Collections.sort(contacts, new Comparator<TLRPC.TL_contact>() {
                @Override
                public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                    TLRPC.User user1 = MessagesController.getInstance().getUser(tl_contact.user_id);
                    TLRPC.User user2 = MessagesController.getInstance().getUser(tl_contact2.user_id);
                    String name1 = UserObject.getFirstName(user1);
                    String name2 = UserObject.getFirstName(user2);
                    return name1.compareTo(name2);
                }
            });
        }

        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
        final ArrayList<String> sortedSectionsArray = new ArrayList<>();

        for (int a = 0; a < contacts.size(); a++) {
            TLRPC.TL_contact value = contacts.get(a);
            TLRPC.User user = MessagesController.getInstance().getUser(value.user_id);
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

        Collections.sort(sortedSectionsArray, new Comparator<String>() {
            @Override
            public int compare(String s, String s2) {
                char cv1 = s.charAt(0);
                char cv2 = s2.charAt(0);
                if (cv1 == '#') {
                    return 1;
                } else if (cv2 == '#') {
                    return -1;
                }
                return s.compareTo(s2);
            }
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

    private void performWriteContactsToPhoneBookInternal(ArrayList<TLRPC.TL_contact> contactsArray) {
        try {
            if (!hasContactsPermission()) {
                return;
            }
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
            Cursor c1 = ApplicationLoader.applicationContext.getContentResolver().query(rawContactUri, new String[]{BaseColumns._ID, ContactsContract.RawContacts.SYNC2}, null, null, null);
            HashMap<Integer, Long> bookContacts = new HashMap<>();
            if (c1 != null) {
                while (c1.moveToNext()) {
                    bookContacts.put(c1.getInt(1), c1.getLong(0));
                }
                c1.close();

                for (int a = 0; a < contactsArray.size(); a++) {
                    TLRPC.TL_contact u = contactsArray.get(a);
                    if (!bookContacts.containsKey(u.user_id)) {
                        TLRPC.User user = MessagesController.getInstance().getUser(u.user_id);
                        addContactToPhoneBook(user, false);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void performWriteContactsToPhoneBook() {
        final ArrayList<TLRPC.TL_contact> contactsArray = new ArrayList<>();
        contactsArray.addAll(contacts);
        Utilities.phoneBookQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                performWriteContactsToPhoneBookInternal(contactsArray);
            }
        });
    }

    private void applyContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict, ArrayList<TLRPC.TL_contact> newC, ArrayList<Integer> contactsTD) {
        if (newC == null || contactsTD == null) {
            newC = new ArrayList<>();
            contactsTD = new ArrayList<>();
            for (int a = 0; a < ids.size(); a++) {
                Integer uid = ids.get(a);
                if (uid > 0) {
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = uid;
                    newC.add(contact);
                } else if (uid < 0) {
                    contactsTD.add(-uid);
                }
            }
        }
        FileLog.e("process update - contacts add = " + newC.size() + " delete = " + contactsTD.size());

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
                user = MessagesController.getInstance().getUser(newContact.user_id);
            } else {
                MessagesController.getInstance().putUser(user, true);
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
            final Integer uid = contactsTD.get(a);
            Utilities.phoneBookQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    deleteContactFromPhoneBook(uid);
                }
            });

            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(uid);
            }
            if (user == null) {
                user = MessagesController.getInstance().getUser(uid);
            } else {
                MessagesController.getInstance().putUser(user, true);
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
            MessagesStorage.getInstance().applyPhoneBookUpdates(toAdd.toString(), toDelete.toString());
        }

        if (reloadContacts) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    loadContacts(false, 0);
                }
            });
        } else {
            final ArrayList<TLRPC.TL_contact> newContacts = newC;
            final ArrayList<Integer> contactsToDelete = contactsTD;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    for (int a = 0; a < newContacts.size(); a++) {
                        TLRPC.TL_contact contact = newContacts.get(a);
                        if (contactsDict.get(contact.user_id) == null) {
                            contacts.add(contact);
                            contactsDict.put(contact.user_id, contact);
                        }
                    }
                    for (int a = 0; a < contactsToDelete.size(); a++) {
                        Integer uid = contactsToDelete.get(a);
                        TLRPC.TL_contact contact = contactsDict.get(uid);
                        if (contact != null) {
                            contacts.remove(contact);
                            contactsDict.remove(uid);
                        }
                    }
                    if (!newContacts.isEmpty()) {
                        updateUnregisteredContacts(contacts);
                        performWriteContactsToPhoneBook();
                    }
                    performSyncPhoneBook(getContactsCopy(contactsBook), false, false, false, false, true, false);
                    buildContactsSectionsArrays(!newContacts.isEmpty());
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                }
            });
        }
    }

    public void processContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict) {
        final ArrayList<TLRPC.TL_contact> newContacts = new ArrayList<>();
        final ArrayList<Integer> contactsToDelete = new ArrayList<>();
        for (Integer uid : ids) {
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
            MessagesStorage.getInstance().deleteContacts(contactsToDelete);
        }
        if (!newContacts.isEmpty()) {
            MessagesStorage.getInstance().putContacts(newContacts, false);
        }
        if (!contactsLoaded || !contactsBookLoaded) {
            delayedContactsUpdate.addAll(ids);
            FileLog.e("delay update - contacts add = " + newContacts.size() + " delete = " + contactsToDelete.size());
        } else {
            applyContactsUpdates(ids, userDict, newContacts, contactsToDelete);
        }
    }

    public long addContactToPhoneBook(TLRPC.User user, boolean check) {
        if (currentAccount == null || user == null || TextUtils.isEmpty(user.phone)) {
            return -1;
        }
        if (!hasContactsPermission()) {
            return -1;
        }
        long res = -1;
        synchronized (observerLock) {
            ignoreChanges = true;
        }
        ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
        if (check) {
            try {
                Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
                int value = contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " = " + user.id, null);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        ArrayList<ContentProviderOperation> query = new ArrayList<>();

        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type);
        builder.withValue(ContactsContract.RawContacts.SYNC1, user.phone);
        builder.withValue(ContactsContract.RawContacts.SYNC2, user.id);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, user.first_name);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, user.last_name);
        query.add(builder.build());

//        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
//        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
//        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
//        builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+" + user.phone);
//        builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
//        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile");
        builder.withValue(ContactsContract.Data.DATA1, user.id);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Profile");
        builder.withValue(ContactsContract.Data.DATA3, "+" + user.phone);
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());
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

    private void deleteContactFromPhoneBook(int uid) {
        if (!hasContactsPermission()) {
            return;
        }
        synchronized (observerLock) {
            ignoreChanges = true;
        }
        try {
            ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
            int value = contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " = " + uid, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
        synchronized (observerLock) {
            ignoreChanges = false;
        }
    }

    protected void markAsContacted(final String contactId) {
        if (contactId == null) {
            return;
        }
        Utilities.phoneBookQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                Uri uri = Uri.parse(contactId);
                ContentValues values = new ContentValues();
                values.put(ContactsContract.Contacts.LAST_TIME_CONTACTED, System.currentTimeMillis());
                ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
                cr.update(uri, values, null, null);
            }
        });
    }

    public void addContact(TLRPC.User user) {
        if (user == null || TextUtils.isEmpty(user.phone)) {
            return;
        }

        TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        ArrayList<TLRPC.TL_inputPhoneContact> contactsParams = new ArrayList<>();
        TLRPC.TL_inputPhoneContact c = new TLRPC.TL_inputPhoneContact();
        c.phone = user.phone;
        if (!c.phone.startsWith("+")) {
            c.phone = "+" + c.phone;
        }
        c.first_name = user.first_name;
        c.last_name = user.last_name;
        c.client_id = 0;
        contactsParams.add(c);
        req.contacts = contactsParams;
        /*if (BuildVars.DEBUG_VERSION) {
            FileLog.e("add contact " + user.first_name + " " + user.last_name + " " + user.phone);
        }*/
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);

                /*if (BuildVars.DEBUG_VERSION) {
                    for (TLRPC.User user : res.users) {
                        FileLog.e("received user " + user.first_name + " " + user.last_name + " " + user.phone);
                    }
                }*/

                for (int a = 0; a < res.users.size(); a++) {
                    final TLRPC.User u = res.users.get(a);
                    Utilities.phoneBookQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            addContactToPhoneBook(u, true);
                        }
                    });
                    TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                    newContact.user_id = u.id;
                    ArrayList<TLRPC.TL_contact> arrayList = new ArrayList<>();
                    arrayList.add(newContact);
                    MessagesStorage.getInstance().putContacts(arrayList, false);

                    if (!TextUtils.isEmpty(u.phone)) {
                        CharSequence name = formatName(u.first_name, u.last_name);
                        MessagesStorage.getInstance().applyPhoneBookUpdates(u.phone, "");
                        Contact contact = contactsBookSPhones.get(u.phone);
                        if (contact != null) {
                            int index = contact.shortPhones.indexOf(u.phone);
                            if (index != -1) {
                                contact.phoneDeleted.set(index, 0);
                            }
                        }
                    }
                }

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : res.users) {
                            MessagesController.getInstance().putUser(u, false);
                            if (contactsDict.get(u.id) == null) {
                                TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                                newContact.user_id = u.id;
                                contacts.add(newContact);
                                contactsDict.put(newContact.user_id, newContact);
                            }
                        }
                        buildContactsSectionsArrays(true);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagCanCompress);
    }

    public void deleteContact(final ArrayList<TLRPC.User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        final ArrayList<Integer> uids = new ArrayList<>();
        for (TLRPC.User user : users) {
            TLRPC.InputUser inputUser = MessagesController.getInputUser(user);
            if (inputUser == null) {
                continue;
            }
            uids.add(user.id);
            req.id.add(inputUser);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                MessagesStorage.getInstance().deleteContacts(uids);
                Utilities.phoneBookQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : users) {
                            deleteContactFromPhoneBook(user.id);
                        }
                    }
                });

                for (int a = 0; a < users.size(); a++) {
                    TLRPC.User user = users.get(a);
                    if (TextUtils.isEmpty(user.phone)) {
                        continue;
                    }
                    CharSequence name = UserObject.getUserName(user);
                    MessagesStorage.getInstance().applyPhoneBookUpdates(user.phone, "");
                    Contact contact = contactsBookSPhones.get(user.phone);
                    if (contact != null) {
                        int index = contact.shortPhones.indexOf(user.phone);
                        if (index != -1) {
                            contact.phoneDeleted.set(index, 1);
                        }
                    }
                }

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
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
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                    }
                });
            }
        });
    }

    public void reloadContactsStatuses() {
        saveContactsLoadTime();
        MessagesController.getInstance().clearFullUsers();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("needGetStatuses", true).commit();
        TLRPC.TL_contacts_getStatuses req = new TLRPC.TL_contacts_getStatuses();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
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
                                        status.status.expires = -100;
                                    } else if (status.status instanceof TLRPC.TL_userStatusLastWeek) {
                                        status.status.expires = -101;
                                    } else if (status.status instanceof TLRPC.TL_userStatusLastMonth) {
                                        status.status.expires = -102;
                                    }

                                    TLRPC.User user = MessagesController.getInstance().getUser(status.user_id);
                                    if (user != null) {
                                        user.status = status.status;
                                    }
                                    toDbUser.status = status.status;
                                    dbUsersStatus.add(toDbUser);
                                }
                                MessagesStorage.getInstance().updateUsers(dbUsersStatus, true, true, true);
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
                        }
                    });
                }
            }
        });
    }

    public void loadPrivacySettings() {
        if (loadingDeleteInfo == 0) {
            loadingDeleteInfo = 1;
            TLRPC.TL_account_getAccountTTL req = new TLRPC.TL_account_getAccountTTL();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_accountDaysTTL ttl = (TLRPC.TL_accountDaysTTL) response;
                                deleteAccountTTL = ttl.days;
                                loadingDeleteInfo = 2;
                            } else {
                                loadingDeleteInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        if (loadingLastSeenInfo == 0) {
            loadingLastSeenInfo = 1;
            TLRPC.TL_account_getPrivacy req = new TLRPC.TL_account_getPrivacy();
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                                MessagesController.getInstance().putUsers(rules.users, false);
                                privacyRules = rules.rules;
                                loadingLastSeenInfo = 2;
                            } else {
                                loadingLastSeenInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        if (loadingCallsInfo == 0) {
            loadingCallsInfo = 1;
            TLRPC.TL_account_getPrivacy req = new TLRPC.TL_account_getPrivacy();
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                                MessagesController.getInstance().putUsers(rules.users, false);
                                callPrivacyRules = rules.rules;
                                loadingCallsInfo = 2;
                            } else {
                                loadingCallsInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        if (loadingGroupInfo == 0) {
            loadingGroupInfo = 1;
            TLRPC.TL_account_getPrivacy req = new TLRPC.TL_account_getPrivacy();
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                                MessagesController.getInstance().putUsers(rules.users, false);
                                groupPrivacyRules = rules.rules;
                                loadingGroupInfo = 2;
                            } else {
                                loadingGroupInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
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

    public boolean getLoadingLastSeenInfo() {
        return loadingLastSeenInfo != 2;
    }

    public boolean getLoadingCallsInfo() {
        return loadingCallsInfo != 2;
    }

    public boolean getLoadingGroupInfo() {
        return loadingGroupInfo != 2;
    }

    public ArrayList<TLRPC.PrivacyRule> getPrivacyRules(int type) {
        if (type == 2) {
            return callPrivacyRules;
        } else if (type == 1) {
            return groupPrivacyRules;
        } else {
            return privacyRules;
        }
    }

    public void setPrivacyRules(ArrayList<TLRPC.PrivacyRule> rules, int type) {
        if (type == 2) {
            callPrivacyRules = rules;
        } else if (type == 1) {
            groupPrivacyRules = rules;
        } else {
            privacyRules = rules;
        }
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
        reloadContactsStatuses();
    }

    public static String formatName(String firstName, String lastName) {
        /*if ((firstName == null || firstName.length() == 0) && (lastName == null || lastName.length() == 0)) {
            return LocaleController.getString("HiddenName", R.string.HiddenName);
        }*/
        if (firstName != null) {
            firstName = firstName.trim();
        }
        if (lastName != null) {
            lastName = lastName.trim();
        }
        StringBuilder result = new StringBuilder((firstName != null ? firstName.length() : 0) + (lastName != null ? lastName.length() : 0) + 1);
        if (LocaleController.nameDisplayOrder == 1) {
            if (firstName != null && firstName.length() > 0) {
                result.append(firstName);
                if (lastName != null && lastName.length() > 0) {
                    result.append(" ");
                    result.append(lastName);
                }
            } else if (lastName != null && lastName.length() > 0) {
                result.append(lastName);
            }
        } else {
            if (lastName != null && lastName.length() > 0) {
                result.append(lastName);
                if (firstName != null && firstName.length() > 0) {
                    result.append(" ");
                    result.append(firstName);
                }
            } else if (firstName != null && firstName.length() > 0) {
                result.append(firstName);
            }
        }
        return result.toString();
    }
}
