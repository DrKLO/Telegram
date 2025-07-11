package me.telegraphy.android;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.telegram.messenger.ApplicationLoader; // Might be needed for context
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider; // For Robolectric context

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = ApplicationLoader.class) // Configure Robolectric
public class DatabaseManagerTest {

    private DatabaseManager dbManager;
    private Context context;

    @Before
    public void setUp() throws Exception {
        // Initialize FileLog and BuildVars to avoid NullPointerExceptions if they are used by DatabaseManager indirectly
        ShadowLog.stream = System.out; // Redirect Robolectric logs
        FileLog.d("DatabaseManagerTest setUp");
        BuildVars.LOGS_ENABLED = true; // Ensure logs are enabled for testing if FileLog is used

        context = ApplicationProvider.getApplicationContext();
        // Ensure ApplicationLoader.applicationContext is set for DatabaseManager
        ApplicationLoader.applicationContext = context;

        // It's good practice to use a temporary/in-memory database for tests
        // However, the current DatabaseManager uses a fixed name.
        // We will delete the database file before each test for isolation.
        deleteDatabaseFile();
        dbManager = DatabaseManager.getInstance(0); // Assuming account 0 for tests
        FileLog.d("DatabaseManager instance obtained");
    }

    private void deleteDatabaseFile() {
        try {
            File dbFile = context.getDatabasePath(DatabaseManager.DATABASE_NAME_PREFIX + "0.db");
            if (dbFile.exists()) {
                if (dbFile.delete()) {
                    FileLog.d("Previous test database deleted: " + dbFile.getAbsolutePath());
                } else {
                    FileLog.e("Failed to delete previous test database: " + dbFile.getAbsolutePath());
                }
            }
            File journalFile = context.getDatabasePath(DatabaseManager.DATABASE_NAME_PREFIX + "0.db-journal");
            if (journalFile.exists()) {
                journalFile.delete();
            }
        } catch (Exception e) {
            FileLog.e("Error deleting database file: " + e.getMessage());
        }
    }

    @After
    public void tearDown() throws Exception {
        FileLog.d("DatabaseManagerTest tearDown");
        // dbManager.close(); // Assuming DatabaseManager has a close method
        deleteDatabaseFile(); // Clean up after each test
    }

    @Test
    public void testDatabaseCreation() {
        assertNotNull("DatabaseManager instance should not be null", dbManager);
        // Check if the database is opened or created, e.g., by trying a simple read
        // For example, try to get a user which should return null but not throw an error
        // if the table exists.
        try {
            TLRPC.User user = dbManager.getUser(12345); // Non-existent user
            assertNull("Getting a non-existent user should return null", user);
            FileLog.d("testDatabaseCreation: tables seem to be created.");
        } catch (Exception e) {
            FileLog.e("testDatabaseCreation failed: " + e.getMessage());
            fail("Database creation or basic query failed: " + e.getMessage());
        }
    }

    @Test
    public void testAddAndGetUser() {
        FileLog.d("testAddAndGetUser starting");
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = 1L; // TLRPC.User id is long
        user.first_name = "Test";
        user.last_name = "User";
        user.username = "testuser";
        user.phone = "1234567890";
        user.photo = new TLRPC.TL_userProfilePhotoEmpty(); // Add a placeholder
        user.status = new TLRPC.TL_userStatusEmpty(); // Add a placeholder
        user.access_hash = 12345L;
        user.bot = false;
        user.verified = true;
        user.premium = false;
        user.mutual_contact = false;

        dbManager.addUser(user);
        FileLog.d("testAddAndGetUser: user added");

        TLRPC.User retrievedUser = dbManager.getUser(user.id);
        assertNotNull("Retrieved user should not be null", retrievedUser);
        assertEquals("User ID should match", user.id, retrievedUser.id);
        assertEquals("First name should match", user.first_name, retrievedUser.first_name);
        assertEquals("Username should match", user.username, retrievedUser.username);
        FileLog.d("testAddAndGetUser completed successfully");
    }

    @Test
    public void testAddAndGetChat() {
        FileLog.d("testAddAndGetChat starting");
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = 101L;
        chat.title = "Test Chat";
        chat.participants_count = 2;
        chat.date = (int) (System.currentTimeMillis() / 1000);
        chat.version = 1;
        // chat.photo = new TLRPC.TL_chatPhotoEmpty(); // Add a placeholder

        dbManager.addChat(chat);
        FileLog.d("testAddAndGetChat: chat added");

        TLRPC.Chat retrievedChat = dbManager.getChat(chat.id);
        assertNotNull("Retrieved chat should not be null", retrievedChat);
        assertEquals("Chat ID should match", chat.id, retrievedChat.id);
        assertEquals("Chat title should match", chat.title, retrievedChat.title);
        FileLog.d("testAddAndGetChat completed successfully");
    }

    @Test
    public void testAddAndGetMessage() {
        FileLog.d("testAddAndGetMessage starting");
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 201;
        message.peer_id = new TLRPC.TL_peerUser(); // Create an actual instance
        message.peer_id.user_id = 1L; // Ensure this peer exists or handle appropriately
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = 1L;
        message.dialog_id = 1L; // Ensure this is consistent
        message.message = "Hello, database!";
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.out = true;
        message.unread = false;
        message.media_unread = false;
        message.mentioned = false;

        // Add a user first for foreign key if necessary, or ensure your addMessage handles it
        TLRPC.TL_user sender = new TLRPC.TL_user();
        sender.id = 1L;
        sender.first_name = "Sender";
        dbManager.addUser(sender);

        dbManager.addMessage(message, 1L); // Assuming dialogId is 1L
        FileLog.d("testAddAndGetMessage: message added");

        TLRPC.Message retrievedMessage = dbManager.getMessage(message.id);
        assertNotNull("Retrieved message should not be null", retrievedMessage);
        assertEquals("Message ID should match", message.id, retrievedMessage.id);
        assertEquals("Message text should match", message.message, retrievedMessage.message);
        FileLog.d("testAddAndGetMessage completed successfully");
    }

    @Test
    public void testUpdateUser() {
        FileLog.d("testUpdateUser starting");
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = 2L;
        user.first_name = "Initial Name";
        user.username = "initialuser";
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        user.status = new TLRPC.TL_userStatusEmpty();

        dbManager.addUser(user);
        FileLog.d("testUpdateUser: initial user added");

        user.first_name = "Updated Name";
        user.username = "updateduser";
        dbManager.updateUser(user);
        FileLog.d("testUpdateUser: user updated");

        TLRPC.User retrievedUser = dbManager.getUser(user.id);
        assertNotNull(retrievedUser);
        assertEquals("Updated first name should match", "Updated Name", retrievedUser.first_name);
        assertEquals("Updated username should match", "updateduser", retrievedUser.username);
        FileLog.d("testUpdateUser completed successfully");
    }

    @Test
    public void testDeleteUser() {
        FileLog.d("testDeleteUser starting");
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = 3L;
        user.first_name = "To Be Deleted";
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        user.status = new TLRPC.TL_userStatusEmpty();

        dbManager.addUser(user);
        FileLog.d("testDeleteUser: user added");

        dbManager.deleteUser(user.id);
        FileLog.d("testDeleteUser: user deleted");

        TLRPC.User retrievedUser = dbManager.getUser(user.id);
        assertNull("Deleted user should not be found", retrievedUser);
        FileLog.d("testDeleteUser completed successfully");
    }

    @Test
    public void testGetNonExistentUser() {
        FileLog.d("testGetNonExistentUser starting");
        TLRPC.User retrievedUser = dbManager.getUser(9999L); // Non-existent ID
        assertNull("Retrieving a non-existent user should return null", retrievedUser);
        FileLog.d("testGetNonExistentUser completed successfully");
    }

    // TODO: Add more tests for other CRUD operations (dialogs, contacts, etc.)
    // TODO: Add tests for onUpgrade if more complex logic is added.
    // TODO: Test edge cases, null inputs, and error handling if applicable.

    @Test
    public void testAddAndGetDialog() {
        FileLog.d("testAddAndGetDialog starting");
        // Ensure user and chat for the dialog exist or are created
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = 50L;
        user.first_name = "DialogUser";
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        user.status = new TLRPC.TL_userStatusEmpty();
        dbManager.addUser(user);

        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
        dialog.id = 50L; // Dialog with user 50L
        dialog.peer = new TLRPC.TL_peerUser();
        dialog.peer.user_id = 50L;
        dialog.top_message = 1000;
        dialog.unread_count = 5;
        dialog.last_message_date = (int) (System.currentTimeMillis() / 1000L - 3600); // 1 hour ago
        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        dialog.pts = 10;
        dialog.draft = null; // No draft for this test

        dbManager.addDialog(dialog);
        FileLog.d("testAddAndGetDialog: dialog added");

        TLRPC.Dialog retrievedDialog = dbManager.getDialog(dialog.id);
        assertNotNull("Retrieved dialog should not be null", retrievedDialog);
        assertEquals("Dialog ID should match", dialog.id, retrievedDialog.id);
        assertEquals("Dialog unread count should match", dialog.unread_count, retrievedDialog.unread_count);
        assertTrue("Dialog peer should be user", retrievedDialog.peer instanceof TLRPC.TL_peerUser);
        assertEquals("Dialog peer ID should match", dialog.peer.user_id, retrievedDialog.peer.user_id);
        FileLog.d("testAddAndGetDialog completed successfully");
    }

    @Test
    public void testUpdateDialog() {
        FileLog.d("testUpdateDialog starting");
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = 51L;
        user.first_name = "DialogUserForUpdate";
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        user.status = new TLRPC.TL_userStatusEmpty();
        dbManager.addUser(user);

        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
        dialog.id = 51L;
        dialog.peer = new TLRPC.TL_peerUser();
        dialog.peer.user_id = 51L;
        dialog.top_message = 1001;
        dialog.unread_count = 1;
        dialog.last_message_date = (int) (System.currentTimeMillis() / 1000L - 1800);
        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        dbManager.addDialog(dialog);
        FileLog.d("testUpdateDialog: initial dialog added");

        dialog.unread_count = 0;
        dialog.top_message = 1002; // Simulate a new top message
        dbManager.updateDialog(dialog);
        FileLog.d("testUpdateDialog: dialog updated");

        TLRPC.Dialog retrievedDialog = dbManager.getDialog(dialog.id);
        assertNotNull(retrievedDialog);
        assertEquals("Dialog unread count should be updated", 0, retrievedDialog.unread_count);
        assertEquals("Dialog top message should be updated", 1002, retrievedDialog.top_message);
        FileLog.d("testUpdateDialog completed successfully");
    }


    @Test
    public void testAddAndGetContact() {
        FileLog.d("testAddAndGetContact starting");
        TLRPC.TL_contact contact = new TLRPC.TL_contact();
        contact.user_id = 60L;
        contact.mutual = true;

        // Add the user associated with the contact first
        TLRPC.TL_user contactUser = new TLRPC.TL_user();
        contactUser.id = 60L;
        contactUser.first_name = "Contact";
        contactUser.last_name = "Person";
        contactUser.photo = new TLRPC.TL_userProfilePhotoEmpty();
        contactUser.status = new TLRPC.TL_userStatusEmpty();
        dbManager.addUser(contactUser);
        FileLog.d("testAddAndGetContact: contact user added to users table");

        dbManager.addContact(contact);
        FileLog.d("testAddAndGetContact: contact added");

        TLRPC.Contact retrievedContact = dbManager.getContact(contact.user_id);
        assertNotNull("Retrieved contact should not be null", retrievedContact);
        assertEquals("Contact user ID should match", contact.user_id, retrievedContact.user_id);
        assertEquals("Contact mutual status should match", contact.mutual, retrievedContact.mutual);
        FileLog.d("testAddAndGetContact completed successfully");
    }

    @Test
    public void testGetAllDialogs() {
        FileLog.d("testGetAllDialogs starting");
        // Add a couple of users and dialogs
        TLRPC.TL_user user1 = new TLRPC.TL_user(); user1.id = 70L; user1.first_name = "DialogUser1"; user1.photo = new TLRPC.TL_userProfilePhotoEmpty(); user1.status = new TLRPC.TL_userStatusEmpty();
        dbManager.addUser(user1);
        TLRPC.TL_dialog dialog1 = new TLRPC.TL_dialog(); dialog1.id = 70L; dialog1.peer = new TLRPC.TL_peerUser(); dialog1.peer.user_id = 70L; dialog1.top_message = 1; dialog1.unread_count = 1; dialog1.last_message_date = (int) (System.currentTimeMillis() / 1000L);dialog1.notify_settings = new TLRPC.TL_peerNotifySettings();
        dbManager.addDialog(dialog1);

        TLRPC.TL_chat chat2 = new TLRPC.TL_chat(); chat2.id = 71L; chat2.title = "DialogChat2"; /* set other required fields for chat */
        dbManager.addChat(chat2);
        TLRPC.TL_dialog dialog2 = new TLRPC.TL_dialog(); dialog2.id = -71L; dialog2.peer = new TLRPC.TL_peerChat(); dialog2.peer.chat_id = 71L; dialog2.top_message = 2; dialog2.unread_count = 2; dialog2.last_message_date = (int) (System.currentTimeMillis() / 1000L - 100); dialog2.notify_settings = new TLRPC.TL_peerNotifySettings();
        dbManager.addDialog(dialog2);

        java.util.List<TLRPC.Dialog> allDialogs = dbManager.getAllDialogs();
        assertNotNull("List of all dialogs should not be null", allDialogs);
        // The exact count might be tricky if other tests leave data or if there are default dialogs.
        // For isolated tests, it should be 2. Let's aim for at least 2.
        assertTrue("Should retrieve at least the two added dialogs. Found: " + allDialogs.size(), allDialogs.size() >= 2);

        boolean foundDialog1 = false;
        boolean foundDialog2 = false;
        for (TLRPC.Dialog d : allDialogs) {
            if (d.id == dialog1.id) foundDialog1 = true;
            if (d.id == dialog2.id) foundDialog2 = true;
        }
        assertTrue("Dialog1 should be in the retrieved list", foundDialog1);
        assertTrue("Dialog2 should be in the retrieved list", foundDialog2);
        FileLog.d("testGetAllDialogs completed successfully. Found " + allDialogs.size() + " dialogs.");
    }
}
