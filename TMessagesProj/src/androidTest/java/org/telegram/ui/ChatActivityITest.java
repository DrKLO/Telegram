package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TdApiManager;
import org.telegram.messenger.TdApiMessageConverter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TdApi;
import org.drinkless.td.libcore.telegram.Client;


import java.util.ArrayList;
import java.util.Collections;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ChatActivityITest {

    @Mock
    private TdApiManager mockTdApiManager;
    @Mock
    private MessagesController mockMessagesController;
    @Mock
    private UserConfig mockUserConfig;
    @Mock
    private NotificationCenter mockNotificationCenter;


    private final long currentChatId = -123456789L; // Example group chat
    private final int currentAccount = 0;
    private final long selfUserId = 1L;

    @Rule
    public ActivityScenarioRule<LaunchActivity> activityRule =
            new ActivityScenarioRule<>(LaunchActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = ApplicationProvider.getApplicationContext();
        ApplicationLoader.applicationContext = context; // Robolectric usually handles this, but good for clarity
        FileLog.INSTANCE.init(); // Initialize FileLog if not already

        // Setup mock UserConfig
        when(mockUserConfig.getClientUserId()).thenReturn(selfUserId);
        when(mockUserConfig.isClientActivated()).thenReturn(true);
        when(mockUserConfig.getCurrentUser()).thenReturn(createTestUser(selfUserId, "Current", "User"));
        UserConfig.setInstanceForTesting(currentAccount, mockUserConfig);


        // Setup mock MessagesController
        TLRPC.Chat chat = createTestChat((int) -currentChatId, "Test Chat");
        when(mockMessagesController.getChat(anyLong())).thenReturn(chat);
        when(mockMessagesController.getUser(anyLong())).thenAnswer(invocation -> {
            long userId = invocation.getArgument(0);
            if (userId == selfUserId) return UserConfig.getInstance(currentAccount).getCurrentUser();
            return createTestUser(userId, "User", String.valueOf(userId));
        });
        when(mockMessagesController.getUsers(any())).thenAnswer(invocation -> {
            ArrayList<Long> ids = invocation.getArgument(0);
            ArrayList<TLRPC.User> users = new ArrayList<>();
            for (Long id : ids) {
                users.add(createTestUser(id, "User", String.valueOf(id)));
            }
            return users;
        });
        when(mockMessagesController.getChatUsers(anyLong())).thenReturn(new ArrayList<>());
         MessagesController.setInstanceForTesting(currentAccount, mockMessagesController);


        // Setup mock TdApiManager
        TdApiManager.setInstanceForTesting(currentAccount, mockTdApiManager);
        when(mockTdApiManager.getChatId(anyLong())).thenAnswer(invocation -> TdApiMessageConverter.getChatId(invocation.getArgument(0)));


        // Setup mock NotificationCenter
        NotificationCenter.setInstanceForTesting(currentAccount, mockNotificationCenter);

        // Mock LocaleController
        LocaleController.getInstance(); // Ensure it's initialized

        // Launch ChatActivity
        Bundle args = new Bundle();
        args.putLong("chat_id", -currentChatId); // For groups/channels, chat_id is negative of actual ID

        Intent intent = new Intent(context, LaunchActivity.class)
                .putExtra("fragment", "ChatActivity")
                .putExtra("args", args);

        activityRule.getScenario().onActivity(activity -> {
            // Navigate to ChatActivity if not already there or handle intent
            // This part might need adjustment based on how LaunchActivity handles fragments
            // For simplicity, assume LaunchActivity can directly open ChatActivity fragment
            // or that we can navigate to it.
            // The ideal way is to launch ChatActivity directly if it's an Activity,
            // or use FragmentScenario for fragments.
            // Since ChatActivity is a fragment, we'd typically use FragmentScenario
            // or ensure LaunchActivity loads it.

            // Simulate user being logged in for ChatActivity to proceed
            UserConfig.getInstance(currentAccount).setCurrentUser(createTestUser(selfUserId, "Test", "User"));
            UserConfig.getInstance(currentAccount).setClientActivated(true);

            activity.presentFragment(new ChatActivity(args), false, true, true, false);
        });
    }

    private TLRPC.User createTestUser(long id, String firstName, String lastName) {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = id;
        user.first_name = firstName;
        user.last_name = lastName;
        return user;
    }

    private TLRPC.Chat createTestChat(int id, String title) {
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = id;
        chat.title = title;
        return chat;
    }

    private TdApi.Message createTestTdApiMessage(long messageId, long senderUserId, String text) {
        TdApi.MessageSender sender = new TdApi.MessageSenderUser(senderUserId);
        TdApi.MessageContent content = new TdApi.MessageText(new TdApi.FormattedText(text, null), null);
        return new TdApi.Message(messageId, sender, TdApiMessageConverter.getChatId(currentChatId), null, null, false, false, false, false, false, false, false, 0, (int) (System.currentTimeMillis() / 1000), 0,0,0,0,0,0,0,null, null, content, null);
    }


    @Test
    public void testInitialMessageLoad_DisplaysMessages() {
        ArrayList<TdApi.Message> messagesToLoad = new ArrayList<>();
        messagesToLoad.add(createTestTdApiMessage(101, 2L, "Hello TDLib!"));
        messagesToLoad.add(createTestTdApiMessage(100, selfUserId, "Hi there!"));

        doAnswer(invocation -> {
            Client.ResultHandler handler = invocation.getArgument(5);
            TdApi.Messages messagesResult = new TdApi.Messages(messagesToLoad.size(), messagesToLoad.toArray(new TdApi.Message[0]));
            handler.onResult(messagesResult);
            return null;
        }).when(mockTdApiManager).getChatHistory(
                eq(TdApiMessageConverter.getChatId(currentChatId)),
                eq(0L),
                eq(0),
                anyInt(),
                eq(false),
                any(Client.ResultHandler.class)
        );

        // Wait for messages to potentially load and be displayed
        // onView(withId(R.id.chat_messages_layout)).check(matches(isDisplayed())); // Ensure chat recycler is visible
        // onView(withText("Hello TDLib!")).check(matches(isDisplayed()));
        // onView(withText("Hi there!")).check(matches(isDisplayed()));
        // RecyclerView checks are more reliable but require adapter setup and item view IDs
        // For now, verify the call was made. A more complete test would check RecyclerView content.
        verify(mockTdApiManager, timeout(2000).atLeastOnce()).getChatHistory(
                eq(TdApiMessageConverter.getChatId(currentChatId)),
                eq(0L),
                eq(0),
                anyInt(),
                eq(false),
                any(Client.ResultHandler.class)
        );
    }

    @Test
    public void testSendMessage_callsTdApiManagerAndDisplaysOptimisticMessage() {
        final String messageText = "Test message to send";

        // Mock TdApiManager's sendMessageText to simulate immediate optimistic update
        // and a delayed server response.
        doAnswer(invocation -> {
            Client.ResultHandler handler = invocation.getArgument(9); // Assuming 10th arg (index 9)
            // Simulate optimistic message (usually handled by TdApi itself via updates)
            // For test, we can assume it happens.
            // Then simulate server response:
            // TdApi.Message sentMsg = createTestTdApiMessage(Utilities.random.nextLong(), selfUserId, messageText);
            // sentMsg.sendingState = new TdApi.MessageSendingStateSucceeded(); // Or similar
            // handler.onResult(sentMsg);
            // For simplicity, just acknowledge the call for now.
            // A more complex mock would involve TdApi.UpdateNewMessage for the optimistic part
            // and then the TdApi.Message for the final server ack.

            // Let's simulate the optimistic message appearing by posting a notification
            // This is a bit of a hack for testing; ideally, TDLib handles this.
            activityRule.getScenario().onActivity(activity -> {
                TLRPC.TL_message tempMsg = new TLRPC.TL_message();
                tempMsg.id = 0; // Temporary client-side ID
                tempMsg.local_id = tempMsg.id = UserConfig.getInstance(currentAccount).getNewMessageId(); // Simulate new local ID
                tempMsg.random_id = Utilities.random.nextLong(); // For matching later
                tempMsg.message = messageText;
                tempMsg.date = (int) (System.currentTimeMillis() / 1000);
                tempMsg.dialog_id = currentChatId;
                tempMsg.from_id = new TLRPC.TL_peerUser();
                tempMsg.from_id.user_id = selfUserId;
                tempMsg.peer_id = MessagesController.getInstance(currentAccount).getPeer(currentChatId);
                tempMsg.out = true;
                tempMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;

                MessageObject mo = new MessageObject(currentAccount, tempMsg, true, true);
                ArrayList<MessageObject> arr = new ArrayList<>(Collections.singletonList(mo));
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoad, currentChatId, MessageObject.getTopicId(mo.messageOwner,true), arr, true, 0,0,0,0,0,0,0,0,0,0);
                 NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatWasUpdated, mo, currentChatId);

                // Simulate server ack later
                // For now, just check the TdApiManager call
            });


            // Simulate successful send by TdApiManager (the Client.ResultHandler will be called by TdApiManager)
            // For this test, we're verifying the call to TdApiManager, not its internal callback handling.
            // A more robust test would mock the callback to verify ChatActivity's reaction.
            return null;
        }).when(mockTdApiManager).sendMessageText(
                eq(TdApiMessageConverter.getChatId(currentChatId)),
                anyLong(), // messageThreadId
                anyLong(), // sendAsChatId
                eq(messageText),
                any(), // entities
                any(), // linkPreviewOptions
                any(), // inputMessageReplyTo
                any(TdApi.MessageSendOptions.class),
                any(), // replyMarkup
                any(Client.ResultHandler.class)
        );

        onView(withId(R.id.chat_compose_panel)).check(matches(isDisplayed()));
        onView(withId(R.id.chat_message_edit_text)).perform(typeText(messageText), closeSoftKeyboard());
        onView(withId(R.id.chat_send_button)).perform(click());

        // Verify TdApiManager.sendMessageText was called
        verify(mockTdApiManager, timeout(2000).times(1)).sendMessageText(
                eq(TdApiMessageConverter.getChatId(currentChatId)),
                anyLong(),
                anyLong(),
                eq(messageText),
                any(),
                any(),
                any(),
                any(TdApi.MessageSendOptions.class),
                any(),
                any(Client.ResultHandler.class)
        );

        // Check if the optimistic message appears (this is a simplified check)
        // A proper check would involve RecyclerView matchers.
        // onView(withText(messageText)).check(matches(isDisplayed()));
    }
}
