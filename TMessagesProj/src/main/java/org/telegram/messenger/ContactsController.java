/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.SparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.ui.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ContactsController {
    public static ContactsController Instance = new ContactsController();
    private Account currentAccount;
    public boolean loadingContacts = false;
    private boolean ignoreChanges = false;
    private boolean contactsSyncInProgress = false;
    private final Integer observerLock = 1;
    public boolean contactsLoaded = false;
    private boolean contactsBookLoaded = false;
    private ArrayList<Integer> delayedContactsUpdate = new ArrayList<Integer>();

    public static class Contact {
        public int id;
        public ArrayList<String> phones = new ArrayList<String>();
        public ArrayList<String> phoneTypes = new ArrayList<String>();
        public ArrayList<String> shortPhones = new ArrayList<String>();
        public ArrayList<Integer> phoneDeleted = new ArrayList<Integer>();
        public String first_name;
        public String last_name;
    }

    private String[] projectionPhones = {
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL
    };
    private String[] projectionNames = {
        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
    };

    public HashMap<Integer, Contact> contactsBook = new HashMap<Integer, Contact>();
    public HashMap<String, Contact> contactsBookSPhones = new HashMap<String, Contact>();
    public HashMap<String, ArrayList<Contact>> contactsSectionsDict = new HashMap<String, ArrayList<Contact>>();
    public ArrayList<String> sortedContactsSectionsArray = new ArrayList<String>();

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<TLRPC.TL_contact>();
    public SparseArray<TLRPC.TL_contact> contactsDict = new SparseArray<TLRPC.TL_contact>();
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<String>();

    public HashMap<String, TLRPC.TL_contact> contactsByPhone = new HashMap<String, TLRPC.TL_contact>();

    private class MyContentObserver extends ContentObserver {

        public MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            synchronized (observerLock) {
                if (ignoreChanges) {
                    FileLog.e("tmessages", "contacts changed - ignore");
                    return;
                }
            }

            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    MessagesController.Instance.scheduleContactsReload = System.currentTimeMillis() + 2000;
                    FileLog.e("tmessages", "contacts changed schedule - apply in " + MessagesController.Instance.scheduleContactsReload);
                }
            });
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
    }

    public ContactsController() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, new MyContentObserver());
            }
        });
    }

    public void cleanup() {
        contactsBook.clear();
        contactsBookSPhones.clear();
        contactsSectionsDict.clear();
        sortedContactsSectionsArray.clear();
        contacts.clear();
        contactsDict.clear();
        usersSectionsDict.clear();
        sortedUsersSectionsArray.clear();
        delayedContactsUpdate.clear();
        contactsByPhone.clear();

        loadingContacts = false;
        contactsSyncInProgress = false;
        contactsLoaded = false;
        contactsBookLoaded = false;
    }

    public void checkAppAccount() {
        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
        Account[] accounts = am.getAccountsByType("org.telegram.messenger.account");
        boolean recreateAccount = false;
        if (UserConfig.currentUser != null) {
            if (accounts.length == 1) {
                Account acc = accounts[0];
                if (!acc.name.equals(UserConfig.currentUser.phone)) {
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
            for (Account c : accounts) {
                am.removeAccount(c, null, null);
            }
            if (UserConfig.currentUser != null) {
                currentAccount = new Account(UserConfig.currentUser.phone, "org.telegram.messenger.account");
                am.addAccountExplicitly(currentAccount, "", null);
            }
        }
    }

    public void readContacts() {
        if (loadingContacts) {
            return;
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!contacts.isEmpty() || contactsLoaded) {
                    return;
                }
                loadContacts(true, false);
            }
        });
    }

    private HashMap<Integer, Contact> readContactsFromPhoneBook() {
        HashMap<Integer, Contact> contactsMap = new HashMap<Integer, Contact>();
        try {
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();

            HashMap<String, Contact> shortContacts = new HashMap<String, Contact>();
            String ids = "";
            Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectionPhones, null, null, null);
            if (pCur != null) {
                if (pCur.getCount() > 0) {
                    while (pCur.moveToNext()) {
                        String number = pCur.getString(1);
                        if (number == null || number.length() == 0) {
                            continue;
                        }
                        number = PhoneFormat.stripExceptNumbers(number, true);
                        if (number.length() == 0) {
                            continue;
                        }

                        String shortNumber = number;

                        if (number.startsWith("+")) {
                            shortNumber = number.substring(1);
                        }

                        if (shortContacts.containsKey(shortNumber)) {
                            continue;
                        }

                        Integer id = pCur.getInt(0);
                        if (ids.length() != 0) {
                            ids += ",";
                        }
                        ids += id;

                        int type = pCur.getInt(2);
                        Contact contact = contactsMap.get(id);
                        if (contact == null) {
                            contact = new Contact();
                            contact.first_name = "";
                            contact.last_name = "";
                            contact.id = id;
                            contactsMap.put(id, contact);
                        }

                        contact.shortPhones.add(shortNumber);
                        contact.phones.add(number);
                        contact.phoneDeleted.add(0);

                        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                            contact.phoneTypes.add(pCur.getString(3));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) {
                            contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneHome));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                            contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) {
                            contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneWork));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
                            contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMain));
                        } else {
                            contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneOther));
                        }
                        shortContacts.put(shortNumber, contact);
                    }
                }
                pCur.close();
            }

            pCur = cr.query(ContactsContract.Data.CONTENT_URI, projectionNames, ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " IN (" + ids + ") AND " + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
            if (pCur != null && pCur.getCount() > 0) {
                while (pCur.moveToNext()) {
                    int id = pCur.getInt(0);
                    String fname = pCur.getString(1);
                    String sname = pCur.getString(2);
                    String sname2 = pCur.getString(3);
                    String mname = pCur.getString(4);
                    Contact contact = contactsMap.get(id);
                    if (contact != null) {
                        contact.first_name = fname;
                        contact.last_name = sname;
                        if (contact.first_name == null) {
                            contact.first_name = "";
                        }
                        if (mname != null && mname.length() != 0) {
                            if (contact.first_name.length() != 0) {
                                contact.first_name += " " + mname;
                            } else {
                                contact.first_name = mname;
                            }
                        }
                        if (contact.last_name == null) {
                            contact.last_name = "";
                        }
                        if (contact.last_name.length() == 0 && contact.first_name.length() == 0 && sname2 != null && sname2.length() != 0) {
                            contact.first_name = sname2;
                        }
                    }
                }
                pCur.close();
            }

            try {
                pCur = cr.query(ContactsContract.RawContacts.CONTENT_URI, new String[] { "display_name", ContactsContract.RawContacts.SYNC1, ContactsContract.RawContacts.CONTACT_ID }, ContactsContract.RawContacts.ACCOUNT_TYPE + " = " + "'com.whatsapp'", null, null);
                if (pCur != null) {
                    while ((pCur.moveToNext())) {
                        String phone = pCur.getString(1);
                        if (phone == null || phone.length() == 0) {
                            continue;
                        }
                        boolean withPlus = phone.startsWith("+");
                        phone = Utilities.parseIntToString(phone);
                        if (phone == null || phone.length() == 0) {
                            continue;
                        }
                        String shortPhone = phone;
                        if (!withPlus) {
                            phone = "+" + phone;
                        }

                        if (shortContacts.containsKey(shortPhone)) {
                            continue;
                        }

                        String name = pCur.getString(0);
                        if (name == null || name.length() == 0) {
                            name = PhoneFormat.Instance.format(phone);
                        }

                        String[] args = name.split(" ", 2);

                        Contact contact = new Contact();
                        if (args.length > 0) {
                            contact.first_name = args[0];
                        } else {
                            contact.first_name = "";
                        }
                        if (args.length > 1) {
                            contact.last_name = args[1];
                        } else {
                            contact.last_name = "";
                        }
                        contact.id = pCur.getInt(2);
                        contactsMap.put(contact.id, contact);

                        contact.phoneDeleted.add(0);
                        contact.shortPhones.add(shortPhone);
                        contact.phones.add(phone);
                        contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMobile));
                        shortContacts.put(shortPhone, contact);
                    }
                    pCur.close();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            contactsMap.clear();
        }
        return contactsMap;
    }

    public HashMap<Integer, Contact> getContactsCopy(HashMap<Integer, Contact> original) {
        HashMap<Integer, Contact> ret = new HashMap<Integer, Contact>();
        for (HashMap.Entry<Integer, Contact> entry : original.entrySet()) {
            Contact copyContact = new Contact();
            Contact originalContact = entry.getValue();
            copyContact.phoneDeleted.addAll(originalContact.phoneDeleted);
            copyContact.phones.addAll(originalContact.phones);
            copyContact.phoneTypes.addAll(originalContact.phoneTypes);
            copyContact.shortPhones.addAll(originalContact.shortPhones);
            copyContact.first_name = originalContact.first_name;
            copyContact.last_name = originalContact.last_name;
            copyContact.id = originalContact.id;
            ret.put(copyContact.id, copyContact);
        }
        return ret;
    }

    public void performSyncPhoneBook(final HashMap<Integer, Contact> contactHashMap, final boolean requ, final boolean first, final boolean schedule) {
        if (!first && !contactsBookLoaded) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                boolean disableDeletion = true; //disable contacts deletion, because phone numbers can't be compared due to different numbers format
                if (schedule) {
                    try {
                        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
                        Account[] accounts = am.getAccountsByType("org.telegram.messenger.account");
                        boolean recreateAccount = false;
                        if (UserConfig.currentUser != null) {
                            if (accounts.length != 1) {
                                FileLog.e("tmessages", "detected account deletion!");
                                currentAccount = new Account(UserConfig.currentUser.phone, "org.telegram.messenger.account");
                                am.addAccountExplicitly(currentAccount, "", null);
                                performWriteContactsToPhoneBookInternal();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                boolean request = requ;
                if (request && first) {
                    if (UserConfig.importHash != null && UserConfig.importHash.length() != 0 || UserConfig.contactsVersion != 1) {
                        UserConfig.importHash = "";
                        UserConfig.contactsVersion = 1;
                        UserConfig.saveConfig(false);
                        request = false;
                    }
                }

                HashMap<String, Contact> contactShortHashMap = new HashMap<String, Contact>();
                for (HashMap.Entry<Integer, Contact> entry : contactHashMap.entrySet()) {
                    Contact c = entry.getValue();
                    for (String sphone : c.shortPhones) {
                        contactShortHashMap.put(sphone, c);
                    }
                }

                FileLog.e("tmessages", "start read contacts from phone");
                final HashMap<Integer, Contact> contactsMap = readContactsFromPhoneBook();
                final HashMap<String, Contact> contactsBookShort = new HashMap<String, Contact>();
                int oldCount = contactHashMap.size();

                ArrayList<TLRPC.TL_inputPhoneContact> toImport = new ArrayList<TLRPC.TL_inputPhoneContact>();
                if (!contactHashMap.isEmpty()) {
                    for (HashMap.Entry<Integer, Contact> pair : contactsMap.entrySet()) {
                        Integer id = pair.getKey();
                        Contact value = pair.getValue();
                        Contact existing = contactHashMap.get(id);
                        if (existing == null) {
                            for (String s : value.shortPhones) {
                                Contact c = contactShortHashMap.get(s);
                                if (c != null) {
                                    existing = c;
                                    id = existing.id;
                                    break;
                                }
                            }
                        }

                        if (existing == null || existing != null && (!existing.first_name.equals(value.first_name) || !existing.last_name.equals(value.last_name))) {
                            for (int a = 0; a < value.phones.size(); a++) {
                                String sphone = value.shortPhones.get(a);
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
                                    if (contactsByPhone.containsKey(sphone)) {
                                        continue;
                                    }

                                    TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                                    imp.client_id = value.id;
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
                                contactsBookShort.put(sphone, value);
                                int index = existing.shortPhones.indexOf(sphone);
                                if (index == -1) {
                                    if (request) {
                                        if (contactsByPhone.containsKey(sphone)) {
                                            continue;
                                        }

                                        TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                                        imp.client_id = value.id;
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
                        FileLog.e("tmessages", "contacts not changed!");
                        return;
                    }
                    if (request && !contactHashMap.isEmpty() && !contactsMap.isEmpty()) {
                        if (toImport.isEmpty()) {
                            MessagesStorage.Instance.putCachedPhoneBook(contactsMap);
                        }
                        if (!disableDeletion && !contactHashMap.isEmpty()) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (ConnectionsManager.DEBUG_VERSION) {
                                        FileLog.e("tmessages", "need delete contacts");
                                        for (HashMap.Entry<Integer, Contact> c : contactHashMap.entrySet()) {
                                            Contact contact = c.getValue();
                                            FileLog.e("tmessages", "delete contact " + contact.first_name + " " + contact.last_name);
                                            for (String phone : contact.phones) {
                                                FileLog.e("tmessages", phone);
                                            }
                                        }
                                    }

                                    final ArrayList<TLRPC.User> toDelete = new ArrayList<TLRPC.User>();
                                    if (contactHashMap != null && !contactHashMap.isEmpty()) {
                                        try {
                                            final HashMap<String, TLRPC.User> contactsPhonesShort = new HashMap<String, TLRPC.User>();

                                            for (TLRPC.TL_contact value : contacts) {
                                                TLRPC.User user = MessagesController.Instance.users.get(value.user_id);
                                                if (user == null || user.phone == null || user.phone.length() == 0) {
                                                    continue;
                                                }
                                                contactsPhonesShort.put(user.phone, user);
                                            }
                                            int removed = 0;
                                            for (HashMap.Entry<Integer, Contact> entry : contactHashMap.entrySet()) {
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
                                            FileLog.e("tmessages", e);
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
                    for (HashMap.Entry<Integer, Contact> pair : contactsMap.entrySet()) {
                        Contact value = pair.getValue();
                        int id = pair.getKey();
                        for (int a = 0; a < value.phones.size(); a++) {
                            String phone = value.shortPhones.get(a);
                            if (contactsByPhone.containsKey(phone)) {
                                continue;
                            }
                            TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                            imp.client_id = id;
                            imp.first_name = value.first_name;
                            imp.last_name = value.last_name;
                            imp.phone = value.phones.get(a);
                            toImport.add(imp);
                        }
                    }
                }

                FileLog.e("tmessages", "done processing contacts");

                if (request) {
                    if (!toImport.isEmpty()) {
                        if (ConnectionsManager.DEBUG_VERSION) {
                            FileLog.e("tmessages", "start import contacts");
                            for (TLRPC.TL_inputPhoneContact contact : toImport) {
                                FileLog.e("tmessages", "add contact " + contact.first_name + " " + contact.last_name + " " + contact.phone);
                            }
                        }
                        final int count = (int)Math.ceil(toImport.size() / 500.0f);
                        for (int a = 0; a < count; a++) {
                            ArrayList<TLRPC.TL_inputPhoneContact> finalToImport = new ArrayList<TLRPC.TL_inputPhoneContact>();
                            finalToImport.addAll(toImport.subList(a * 500, Math.min((a + 1) * 500, toImport.size())));
                            TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
                            req.contacts = finalToImport;
                            req.replace = false;
                            final boolean isLastQuery = a == count - 1;
                            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    if (error == null) {
                                        FileLog.e("tmessages", "contacts imported");
                                        if (isLastQuery && !contactsMap.isEmpty()) {
                                            MessagesStorage.Instance.putCachedPhoneBook(contactsMap);
                                        }
                                        TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts)response;
                                        if (ConnectionsManager.DEBUG_VERSION) {
                                            for (TLRPC.User user : res.users) {
                                                FileLog.e("tmessages", "received user " + user.first_name + " " + user.last_name + " " + user.phone);
                                            }
                                        }
                                        MessagesStorage.Instance.putUsersAndChats(res.users, null, true, true);
                                        ArrayList<TLRPC.TL_contact> cArr = new ArrayList<TLRPC.TL_contact>();
                                        for (TLRPC.TL_importedContact c : res.imported) {
                                            TLRPC.TL_contact contact = new TLRPC.TL_contact();
                                            contact.user_id = c.user_id;
                                            cArr.add(contact);
                                        }
                                        processLoadedContacts(cArr, res.users, 2);
                                    } else {
                                        FileLog.e("tmessages", "import contacts error " + error.text);
                                    }
                                    if (isLastQuery) {
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
                                    }
                                }
                            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassCanCompress);
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
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUnregisteredContacts(contacts);
                                NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
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
                        MessagesStorage.Instance.putCachedPhoneBook(contactsMap);
                    }
                }
            }
        });
    }

    public void loadContacts(boolean fromCache, boolean cacheEmpty) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                loadingContacts = true;
            }
        });
        if (fromCache) {
            FileLog.e("tmessages", "load contacts from cache");
            MessagesStorage.Instance.getContacts();
        } else {
            FileLog.e("tmessages", "load contacts from server");
            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = cacheEmpty ? "" : UserConfig.contactsHash;
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts)response;
                        if (res instanceof TLRPC.TL_contacts_contactsNotModified) {
                            contactsLoaded = true;
                            if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                                applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                delayedContactsUpdate.clear();
                            }
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    loadingContacts = false;
                                    NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
                                }
                            });
                            FileLog.e("tmessages", "load contacts don't change");
                            return;
                        }
                        processLoadedContacts(res.contacts, res.users, 0);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        //from: 0 - from server, 1 - from db, 2 - from imported contacts
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (TLRPC.User user : usersArr) {
                    if (from == 1) {
                        MessagesController.Instance.users.putIfAbsent(user.id, user);
                    } else {
                        MessagesController.Instance.users.put(user.id, user);
                        if (user.id == UserConfig.clientUserId) {
                            UserConfig.currentUser = user;
                        }
                    }
                }

                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<Integer, TLRPC.User>();

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

                for (TLRPC.TL_contact contact : contactsArr) {
                    TLRPC.User user = MessagesController.Instance.users.get(contact.user_id);
                    if (user != null) {
                        usersDict.put(user.id, user);

                        if (ConnectionsManager.DEBUG_VERSION) {
                            FileLog.e("tmessages", "loaded user contact " + user.first_name + " " + user.last_name + " " + user.phone);
                        }
                    }
                }

                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        FileLog.e("tmessages", "done loading contacts");
                        if (from == 1 && contactsArr.isEmpty()) {
                            loadContacts(false, true);
                            return;
                        }

                        for (TLRPC.TL_contact contact : contactsArr) {
                            if (usersDict.get(contact.user_id) == null && contact.user_id != UserConfig.clientUserId) {
                                loadContacts(false, true);
                                FileLog.e("tmessages", "contacts are broken, load from server");
                                return;
                            }
                        }

                        if (from != 1) {
                            MessagesStorage.Instance.putUsersAndChats(usersArr, null, true, true);
                            MessagesStorage.Instance.putContacts(contactsArr, from != 2);
                            Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
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
                            String ids = "";
                            for (TLRPC.TL_contact aContactsArr : contactsArr) {
                                if (ids.length() != 0) {
                                    ids += ",";
                                }
                                ids += aContactsArr.user_id;
                            }
                            UserConfig.contactsHash = Utilities.MD5(ids);
                            UserConfig.saveConfig(false);
                        }

                        Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                            @Override
                            public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                                TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                                TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                                String name1 = user1.first_name;
                                if (name1 == null || name1.length() == 0) {
                                    name1 = user1.last_name;
                                }
                                String name2 = user2.first_name;
                                if (name2 == null || name2.length() == 0) {
                                    name2 = user2.last_name;
                                }
                                return name1.compareTo(name2);
                            }
                        });

                        final SparseArray<TLRPC.TL_contact> contactsDictionary = new SparseArray<TLRPC.TL_contact>();
                        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
                        final ArrayList<String> sortedSectionsArray = new ArrayList<String>();
                        HashMap<String, TLRPC.TL_contact> contactsByPhonesDict = null;

                        if (!contactsBookLoaded) {
                            contactsByPhonesDict = new HashMap<String, TLRPC.TL_contact>();
                        }

                        final HashMap<String, TLRPC.TL_contact> contactsByPhonesDictFinal = contactsByPhonesDict;

                        for (TLRPC.TL_contact value : contactsArr) {
                            TLRPC.User user = usersDict.get(value.user_id);
                            if (user == null) {
                                continue;
                            }
                            contactsDictionary.put(value.user_id, value);
                            if (contactsByPhonesDict != null) {
                                contactsByPhonesDict.put(user.phone, value);
                            }

                            String key = user.first_name;
                            if (key == null || key.length() == 0) {
                                key = user.last_name;
                            }
                            if (key.length() == 0) {
                                key = "#";
                            } else {
                                key = key.toUpperCase();
                            }
                            if (key.length() > 1) {
                                key = key.substring(0, 1);
                            }
                            ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                            if (arr == null) {
                                arr = new ArrayList<TLRPC.TL_contact>();
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

                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                contacts = contactsArr;
                                contactsDict = contactsDictionary;
                                usersSectionsDict = sectionsDict;
                                sortedUsersSectionsArray = sortedSectionsArray;
                                if (from != 2) {
                                    loadingContacts = false;
                                }
                                performWriteContactsToPhoneBook();
                                updateUnregisteredContacts(contactsArr);

                                NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
                            }
                        });

                        if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }

                        if (contactsByPhonesDictFinal != null) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utilities.globalQueue.postRunnable(new Runnable() {
                                        @Override
                                        public void run() {
                                            contactsByPhone = contactsByPhonesDictFinal;
                                        }
                                    });
                                    if (contactsSyncInProgress) {
                                        return;
                                    }
                                    contactsSyncInProgress = true;
                                    MessagesStorage.Instance.getCachedPhoneBook();
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

    private void updateUnregisteredContacts(final ArrayList<TLRPC.TL_contact> contactsArr) {
        final HashMap<String, TLRPC.TL_contact> contactsPhonesShort = new HashMap<String, TLRPC.TL_contact>();

        for (TLRPC.TL_contact value : contactsArr) {
            TLRPC.User user = MessagesController.Instance.users.get(value.user_id);
            if (user == null || user.phone == null || user.phone.length() == 0) {
                continue;
            }
            contactsPhonesShort.put(user.phone, value);
        }

        final HashMap<String, ArrayList<Contact>> sectionsPhoneDict = new HashMap<String, ArrayList<Contact>>();
        final ArrayList<String> sortedSectionsPhoneArray = new ArrayList<String>();
        for (HashMap.Entry<Integer, Contact> pair : contactsBook.entrySet()) {
            Contact value = pair.getValue();
            int id = pair.getKey();

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

            String key = value.first_name;
            if (key.length() == 0) {
                key = value.last_name;
            }
            if (key.length() == 0) {
                key = "#";
                if (value.phones.size() != 0) {
                    value.first_name = "+" + value.phones.get(0);
                }
            } else {
                key = key.toUpperCase();
            }
            if (key.length() > 1) {
                key = key.substring(0, 1);
            }
            ArrayList<Contact> arr = sectionsPhoneDict.get(key);
            if (arr == null) {
                arr = new ArrayList<Contact>();
                sectionsPhoneDict.put(key, arr);
                sortedSectionsPhoneArray.add(key);
            }
            arr.add(value);
        }
        for (HashMap.Entry<String, ArrayList<Contact>> entry : sectionsPhoneDict.entrySet()) {
            Collections.sort(entry.getValue(), new Comparator<Contact>() {
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
        }
        Collections.sort(sortedSectionsPhoneArray, new Comparator<String>() {
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

        contactsSectionsDict = sectionsPhoneDict;
        sortedContactsSectionsArray = sortedSectionsPhoneArray;
    }

    private void buildContactsSectionsArrays(boolean sort) {
        if (sort) {
            Collections.sort(contacts, new Comparator<TLRPC.TL_contact>() {
                @Override
                public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                    TLRPC.User user1 = MessagesController.Instance.users.get(tl_contact.user_id);
                    TLRPC.User user2 = MessagesController.Instance.users.get(tl_contact2.user_id);
                    String name1 = user1.first_name;
                    if (name1 == null || name1.length() == 0) {
                        name1 = user1.last_name;
                    }
                    String name2 = user2.first_name;
                    if (name2 == null || name2.length() == 0) {
                        name2 = user2.last_name;
                    }
                    return name1.compareTo(name2);
                }
            });
        }

        String ids = "";
        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
        final ArrayList<String> sortedSectionsArray = new ArrayList<String>();

        for (TLRPC.TL_contact value : contacts) {
            TLRPC.User user = MessagesController.Instance.users.get(value.user_id);
            if (user == null) {
                continue;
            }

            String key = user.first_name;
            if (key == null || key.length() == 0) {
                key = user.last_name;
            }
            if (key.length() == 0) {
                key = "#";
            } else {
                key = key.toUpperCase();
            }
            if (key.length() > 1) {
                key = key.substring(0, 1);
            }
            ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
            if (arr == null) {
                arr = new ArrayList<TLRPC.TL_contact>();
                sectionsDict.put(key, arr);
                sortedSectionsArray.add(key);
            }
            arr.add(value);
            if (ids.length() != 0) {
                ids += ",";
            }
            ids += value.user_id;
        }
        UserConfig.contactsHash = Utilities.MD5(ids);
        UserConfig.saveConfig(false);

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

    private void performWriteContactsToPhoneBookInternal() {
        try {
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
            Cursor c1 = ApplicationLoader.applicationContext.getContentResolver().query(rawContactUri, new String[]{BaseColumns._ID, ContactsContract.RawContacts.SYNC2}, null, null, null);
            HashMap<Integer, Long> bookContacts = new HashMap<Integer, Long>();
            if (c1 != null) {
                while (c1.moveToNext()) {
                    bookContacts.put(c1.getInt(1), c1.getLong(0));
                }
                c1.close();

                for (TLRPC.TL_contact u : contacts) {
                    if (!bookContacts.containsKey(u.user_id)) {
                        TLRPC.User user = MessagesController.Instance.users.get(u.user_id);
                        addContactToPhoneBook(user, false);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void performWriteContactsToPhoneBook() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                performWriteContactsToPhoneBookInternal();
            }
        });
    }

    private void applyContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict, ArrayList<TLRPC.TL_contact> newC, ArrayList<Integer> contactsTD) {
        if (newC == null || contactsTD == null) {
            newC = new ArrayList<TLRPC.TL_contact>();
            contactsTD = new ArrayList<Integer>();
            for (Integer uid : ids) {
                if (uid > 0) {
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = uid;
                    newC.add(contact);
                } else if (uid < 0) {
                    contactsTD.add(-uid);
                }
            }
        }
        FileLog.e("tmessages", "process update - contacts add = " + newC.size() + " delete = " + contactsTD.size());

        String toAdd = "";
        String toDelete = "";
        boolean reloadContacts = false;

        for (TLRPC.TL_contact newContact : newC) {
            TLRPC.User user = null;
            if (userDict != null) {
                user = userDict.get(newContact.user_id);
            }
            if (user == null) {
                user = MessagesController.Instance.users.get(newContact.user_id);
            } else {
                MessagesController.Instance.users.putIfAbsent(user.id, user);
            }
            if (user == null || user.phone == null || user.phone.length() == 0) {
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
                toAdd += ",";
            }
            toAdd += user.phone;
        }

        for (final Integer uid : contactsTD) {
            Utilities.globalQueue.postRunnable(new Runnable() {
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
                user = MessagesController.Instance.users.get(uid);
            } else {
                MessagesController.Instance.users.putIfAbsent(user.id, user);
            }
            if (user == null) {
                reloadContacts = true;
                continue;
            }

            if (user.phone != null && user.phone.length() > 0) {
                Contact contact = contactsBookSPhones.get(user.phone);
                if (contact != null) {
                    int index = contact.shortPhones.indexOf(user.phone);
                    if (index != -1) {
                        contact.phoneDeleted.set(index, 1);
                    }
                }
                if (toDelete.length() != 0) {
                    toDelete += ",";
                }
                toDelete += user.phone;
            }
        }

        if (toAdd.length() != 0 || toDelete.length() != 0) {
            MessagesStorage.Instance.applyPhoneBookUpdates(toAdd, toDelete);
        }

        if (reloadContacts) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    loadContacts(false, true);
                }
            });
        } else {
            final ArrayList<TLRPC.TL_contact> newContacts = newC;
            final ArrayList<Integer> contactsToDelete = contactsTD;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    for (TLRPC.TL_contact contact : newContacts) {
                        if (contactsDict.get(contact.user_id) == null) {
                            contacts.add(contact);
                            contactsDict.put(contact.user_id, contact);
                        }
                    }
                    for (Integer uid : contactsToDelete) {
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
                    performSyncPhoneBook(getContactsCopy(contactsBook), false, false, false);
                    buildContactsSectionsArrays(!newContacts.isEmpty());
                    NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
                }
            });
        }
    }

    public void processContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict) {
        final ArrayList<TLRPC.TL_contact> newContacts = new ArrayList<TLRPC.TL_contact>();
        final ArrayList<Integer> contactsToDelete = new ArrayList<Integer>();
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
            MessagesStorage.Instance.deleteContacts(contactsToDelete);
        }
        if (!newContacts.isEmpty()) {
            MessagesStorage.Instance.putContacts(newContacts, false);
        }
        if (!contactsLoaded || !contactsBookLoaded) {
            delayedContactsUpdate.addAll(ids);
            FileLog.e("tmessages", "delay update - contacts add = " + newContacts.size() + " delete = " + contactsToDelete.size());
        } else {
            applyContactsUpdates(ids, userDict, newContacts, contactsToDelete);
        }
    }

    public long addContactToPhoneBook(TLRPC.User user, boolean check) {
        if (currentAccount == null || user == null || user.phone == null || user.phone.length() == 0) {
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
                FileLog.e("tmessages", e);
            }
        }

        ArrayList<ContentProviderOperation> query = new ArrayList<ContentProviderOperation>();

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
        builder.withValue(ContactsContract.Data.DATA1, "+" + user.phone);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Profile");
        builder.withValue(ContactsContract.Data.DATA3, "+" + user.phone);
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());
        try {
            ContentProviderResult[] result = contentResolver.applyBatch(ContactsContract.AUTHORITY, query);
            res = Long.parseLong(result[0].uri.getLastPathSegment());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        synchronized (observerLock) {
            ignoreChanges = false;
        }
        return res;
    }

    private void deleteContactFromPhoneBook(int uid) {
        ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
        synchronized (observerLock) {
            ignoreChanges = true;
        }
        try {
            Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
            int value = contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " = " + uid, null);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        synchronized (observerLock) {
            ignoreChanges = false;
        }
    }

    public void addContact(TLRPC.User user) {
        if (user == null || user.phone == null) {
            return;
        }

        TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        ArrayList<TLRPC.TL_inputPhoneContact> contactsParams = new ArrayList<TLRPC.TL_inputPhoneContact>();
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
        req.replace = false;
        if (ConnectionsManager.DEBUG_VERSION) {
            FileLog.e("tmessages", "add contact " + user.first_name + " " + user.last_name + " " + user.phone);
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, null, true, true);

                if (ConnectionsManager.DEBUG_VERSION) {
                    for (TLRPC.User user : res.users) {
                        FileLog.e("tmessages", "received user " + user.first_name + " " + user.last_name + " " + user.phone);
                    }
                }

                for (final TLRPC.User u : res.users) {
                    Utilities.globalQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            addContactToPhoneBook(u, true);
                        }
                    });
                    TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                    newContact.user_id = u.id;
                    ArrayList<TLRPC.TL_contact> arrayList = new ArrayList<TLRPC.TL_contact>();
                    arrayList.add(newContact);
                    MessagesStorage.Instance.putContacts(arrayList, false);

                    if (u.phone != null && u.phone.length() > 0) {
                        String name = Utilities.formatName(u.first_name, u.last_name);
                        MessagesStorage.Instance.applyPhoneBookUpdates(u.phone, "");
                        Contact contact = contactsBookSPhones.get(u.phone);
                        if (contact != null) {
                            int index = contact.shortPhones.indexOf(u.phone);
                            if (index != -1) {
                                contact.phoneDeleted.set(index, 0);
                            }
                        }
                    }
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : res.users) {
                            MessagesController.Instance.users.put(u.id, u);
                            if (contactsDict.get(u.id) == null) {
                                TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                                newContact.user_id = u.id;
                                contacts.add(newContact);
                                contactsDict.put(newContact.user_id, newContact);
                            }
                        }
                        buildContactsSectionsArrays(true);
                        NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassCanCompress);
    }

    public void deleteContact(final ArrayList<TLRPC.User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        TLRPC.TL_contacts_deleteContacts req = new TLRPC.TL_contacts_deleteContacts();
        final ArrayList<Integer> uids = new ArrayList<Integer>();
        for (TLRPC.User user : users) {
            TLRPC.InputUser inputUser = MessagesController.getInputUser(user);
            if (inputUser == null) {
                continue;
            }
            uids.add(user.id);
            req.id.add(inputUser);
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                MessagesStorage.Instance.deleteContacts(uids);
                Utilities.globalQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : users) {
                            deleteContactFromPhoneBook(user.id);
                        }
                    }
                });

                for (TLRPC.User user : users) {
                    if (user.phone != null && user.phone.length() > 0) {
                        String name = Utilities.formatName(user.first_name, user.last_name);
                        MessagesStorage.Instance.applyPhoneBookUpdates(user.phone, "");
                        Contact contact = contactsBookSPhones.get(user.phone);
                        if (contact != null) {
                            int index = contact.shortPhones.indexOf(user.phone);
                            if (index != -1) {
                                contact.phoneDeleted.set(index, 1);
                            }
                        }
                    }
                }

                Utilities.RunOnUIThread(new Runnable() {
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
                        NotificationCenter.Instance.postNotificationName(MessagesController.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                        NotificationCenter.Instance.postNotificationName(MessagesController.contactsDidLoaded);
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }
}
