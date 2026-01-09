package org.telegram.messenger.scenario;

import android.os.Environment;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScenarioManager {
    private static final String SCENARIO_DIRECTORY = "TelegrammScripts";
    private static final String SCENARIO_FILE = "scenario.json";
    private static final int BASE_USER_ID = 1_000_000;

    private static ScenarioData scenario;
    private static boolean enabled;
    private static boolean started;

    private ScenarioManager() {
    }

    public static void loadScenarioIfPresent() {
        if (enabled || scenario != null) {
            return;
        }
        File file = getScenarioFile();
        if (file == null || !file.exists()) {
            return;
        }
        try {
            String json = readFile(file);
            scenario = parseScenario(json);
            if (scenario != null) {
                enabled = true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void bootstrapIfNeeded(int account) {
        if (!enabled || scenario == null) {
            return;
        }
        if (UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        ScenarioParticipant me = scenario.participants.get("me");
        if (me == null) {
            return;
        }
        TLRPC.User meUser = new TLRPC.TL_user();
        meUser.id = me.userId;
        meUser.first_name = me.firstName;
        meUser.last_name = me.lastName;
        meUser.self = true;
        meUser.flags |= 1024;
        UserConfig.getInstance(account).setCurrentUser(meUser);
        UserConfig.getInstance(account).saveConfig(false);
        MessagesController.getInstance(account).putUser(meUser, true);

        ArrayList<TLRPC.User> users = new ArrayList<>();
        for (ScenarioParticipant participant : scenario.participants.values()) {
            if (participant.userId == me.userId) {
                continue;
            }
            TLRPC.User user = new TLRPC.TL_user();
            user.id = participant.userId;
            user.first_name = participant.firstName;
            user.last_name = participant.lastName;
            user.flags |= 1024;
            users.add(user);
        }
        if (!users.isEmpty()) {
            MessagesController.getInstance(account).putUsers(users, true);
            MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
        }
    }

    public static void startIfReady(LaunchActivity activity) {
        if (!enabled || scenario == null || started || activity == null) {
            return;
        }
        started = true;
        int account = UserConfig.selectedAccount;
        bootstrapIfNeeded(account);
        ScenarioPlayer player = new ScenarioPlayer(account, scenario, activity);
        player.start();
    }

    private static File getScenarioFile() {
        File root = Environment.getExternalStorageDirectory();
        if (root == null) {
            return null;
        }
        File directory = new File(root, SCENARIO_DIRECTORY);
        return new File(directory, SCENARIO_FILE);
    }

    private static String readFile(File file) throws Exception {
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static ScenarioData parseScenario(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        ScenarioData data = new ScenarioData();
        data.title = root.optString("title", "Scenario");

        JSONObject participants = root.optJSONObject("participants");
        if (participants == null) {
            return null;
        }
        int nextId = BASE_USER_ID;
        JSONArray participantNames = participants.names();
        if (participantNames == null) {
            return null;
        }
        for (int i = 0; i < participantNames.length(); i++) {
            String key = participantNames.getString(i);
            JSONObject participant = participants.getJSONObject(key);
            String displayName = participant.optString("displayName", key);
            ScenarioParticipant scenarioParticipant = new ScenarioParticipant();
            scenarioParticipant.id = key;
            scenarioParticipant.displayName = displayName;
            String[] parts = displayName.trim().split("\\s+", 2);
            scenarioParticipant.firstName = parts.length > 0 ? parts[0] : displayName;
            scenarioParticipant.lastName = parts.length > 1 ? parts[1] : "";
            scenarioParticipant.userId = nextId++;
            data.participants.put(key, scenarioParticipant);
        }

        JSONArray chats = root.optJSONArray("chats");
        if (chats != null) {
            for (int i = 0; i < chats.length(); i++) {
                JSONObject chatObject = chats.getJSONObject(i);
                ScenarioChat chat = new ScenarioChat();
                chat.id = chatObject.optString("id");
                chat.title = chatObject.optString("title", chat.id);
                chat.type = chatObject.optString("type", "private");
                JSONArray chatParticipants = chatObject.optJSONArray("participants");
                if (chatParticipants != null) {
                    for (int j = 0; j < chatParticipants.length(); j++) {
                        chat.participants.add(chatParticipants.getString(j));
                    }
                }
                data.chats.put(chat.id, chat);
            }
        }

        JSONArray scenes = root.optJSONArray("scenes");
        if (scenes == null || scenes.length() == 0) {
            return null;
        }
        for (int i = 0; i < scenes.length(); i++) {
            JSONObject sceneObject = scenes.getJSONObject(i);
            ScenarioScene scene = new ScenarioScene();
            scene.id = sceneObject.optString("id", "scene_" + i);
            scene.title = sceneObject.optString("title", scene.id);
            JSONArray events = sceneObject.optJSONArray("events");
            if (events != null) {
                for (int j = 0; j < events.length(); j++) {
                    JSONObject eventObject = events.getJSONObject(j);
                    ScenarioEvent event = new ScenarioEvent();
                    event.t = eventObject.optLong("t", 0);
                    event.type = eventObject.optString("type");
                    event.chatId = eventObject.optString("chatId");
                    event.from = eventObject.optString("from");
                    event.text = eventObject.optString("text");
                    event.duration = eventObject.optLong("duration", 0);
                    scene.events.add(event);
                }
            }
            data.scenes.add(scene);
        }

        return data;
    }

    private static class ScenarioPlayer {
        private final int account;
        private final ScenarioData scenario;
        private final LaunchActivity activity;

        private ScenarioPlayer(int account, ScenarioData scenario, LaunchActivity activity) {
            this.account = account;
            this.scenario = scenario;
            this.activity = activity;
        }

        private void start() {
            long offset = 0;
            for (ScenarioScene scene : scenario.scenes) {
                long maxEventTime = 0;
                for (ScenarioEvent event : scene.events) {
                    long scheduled = offset + event.t;
                    scheduleEvent(event, scheduled);
                    if (event.t > maxEventTime) {
                        maxEventTime = event.t;
                    }
                }
                offset += maxEventTime;
            }
        }

        private void scheduleEvent(ScenarioEvent event, long delayMs) {
            AndroidUtilities.runOnUIThread(() -> handleEvent(event), delayMs);
        }

        private void handleEvent(ScenarioEvent event) {
            switch (event.type) {
                case "open_chat":
                    openChat(event.chatId);
                    break;
                case "message":
                    sendMessage(event.chatId, event.from, event.text);
                    break;
                case "typing":
                    setTyping(event.chatId, event.from, event.duration);
                    break;
                default:
                    FileLog.d("Scenario: skipping unsupported event " + event.type);
                    break;
            }
        }

        private void openChat(String chatId) {
            if (activity == null || chatId == null) {
                return;
            }
            ScenarioChat chat = scenario.chats.get(chatId);
            if (chat == null) {
                return;
            }
            long dialogId = resolveDialogId(chat);
            if (dialogId == 0) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("user_id", dialogId);
            activity.presentFragment(new ChatActivity(args));
        }

        private void sendMessage(String chatId, String from, String text) {
            ScenarioChat chat = scenario.chats.get(chatId);
            if (chat == null || text == null) {
                return;
            }
            ScenarioParticipant sender = scenario.participants.get(from);
            if (sender == null) {
                return;
            }
            long dialogId = resolveDialogId(chat);
            if (dialogId == 0) {
                return;
            }
            int messageId = UserConfig.getInstance(account).getNewMessageId();
            TLRPC.Message message = new TLRPC.TL_message();
            message.id = messageId;
            message.local_id = messageId;
            message.dialog_id = dialogId;
            message.from_id = MessagesController.getInstance(account).getPeer(sender.userId);
            message.flags |= 256;
            message.peer_id = MessagesController.getInstance(account).getPeer(dialogId);
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.message = text;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.flags |= 512;
            message.out = sender.id.equals("me");
            message.random_id = Utilities.random.nextLong();

            ArrayList<TLRPC.Message> messages = new ArrayList<>();
            messages.add(message);
            MessagesStorage.getInstance(account).putMessages(messages, true, true, false, 0, 0, 0);

            ArrayList<org.telegram.messenger.MessageObject> objects = new ArrayList<>();
            objects.add(new org.telegram.messenger.MessageObject(account, message, true, true));
            MessagesController.getInstance(account).updateInterfaceWithMessages(dialogId, objects, 0);
        }

        private void setTyping(String chatId, String from, long duration) {
            ScenarioChat chat = scenario.chats.get(chatId);
            if (chat == null) {
                return;
            }
            ScenarioParticipant sender = scenario.participants.get(from);
            if (sender == null) {
                return;
            }
            long dialogId = resolveDialogId(chat);
            if (dialogId == 0) {
                return;
            }
            MessagesController.getInstance(account).setTypingStatus(dialogId, sender.userId, (int) duration);
        }

        private long resolveDialogId(ScenarioChat chat) {
            if (chat == null) {
                return 0;
            }
            if (!"private".equals(chat.type)) {
                return 0;
            }
            for (String participantId : chat.participants) {
                if (!"me".equals(participantId)) {
                    ScenarioParticipant participant = scenario.participants.get(participantId);
                    if (participant != null) {
                        return participant.userId;
                    }
                }
            }
            return 0;
        }
    }

    private static class ScenarioData {
        private String title;
        private final Map<String, ScenarioParticipant> participants = new HashMap<>();
        private final Map<String, ScenarioChat> chats = new HashMap<>();
        private final List<ScenarioScene> scenes = new ArrayList<>();
    }

    private static class ScenarioParticipant {
        private String id;
        private String displayName;
        private String firstName;
        private String lastName;
        private int userId;
    }

    private static class ScenarioChat {
        private String id;
        private String title;
        private String type;
        private final List<String> participants = new ArrayList<>();
    }

    private static class ScenarioScene {
        private String id;
        private String title;
        private final List<ScenarioEvent> events = new ArrayList<>();
    }

    private static class ScenarioEvent {
        private long t;
        private String type;
        private String chatId;
        private String from;
        private String text;
        private long duration;
    }
}
