package org.telegram.messenger.voip;

import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.json.TLJsonBuilder;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.json.TLJsonParser;
import org.telegram.tgnet.tl.TL_phone;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GroupCallMessagesController extends BaseController {

    public interface CallMessageListener {
        void onNewGroupCallMessage(long callId, GroupCallMessage message);
        void onPopGroupCallMessage();
    }

    public void processUpdate(TLRPC.TL_updateGroupCallMessage update) {
        final long callId = update.call.id;
        final long fromId = DialogObject.getPeerDialogId(update.message.from_id);
        final long id = update.message.id;
        if (getUserConfig().clientUserId == fromId) {
            return;
        }

        // TODO: support ids
        final GroupCallMessage message = new GroupCallMessage(currentAccount, fromId, id, update.message.message);
        AndroidUtilities.runOnUIThread(() -> pushMessageToList(callId, message));
    }

    public void processUpdate(TLRPC.TL_updateGroupCallEncryptedMessage update) {
        final long callId = update.call.id;
        final long fromId = DialogObject.getPeerDialogId(update.from_id);
        final byte[] encrypted = update.encrypted_message;
        if (getUserConfig().clientUserId == fromId) {
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.TL_groupCallMessage result = null;
            try {
                final byte[] decrypted = groupCallMessageDecrypt(callId, fromId, encrypted);
                if (decrypted != null) {
                    final String jsonString = new String(decrypted);
                    final JSONObject jsonObject = new JSONObject(jsonString);

                    result = TLRPC.TL_groupCallMessage.TLJsonDeserialize(new TLJsonParser(jsonObject));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (result != null) {
                final GroupCallMessage message = new GroupCallMessage(currentAccount, fromId, result.random_id, result.message);
                AndroidUtilities.runOnUIThread(() -> pushMessageToList(callId, message));
            } else {
                result = new TLRPC.TL_groupCallMessage();
                result.message = new TLRPC.TL_textWithEntities();
                result.message.text = LocaleController.getString(R.string.GroupCalMessageDecryptionError);
                final GroupCallMessage message = new GroupCallMessage(currentAccount, fromId, 0, result.message);
                AndroidUtilities.runOnUIThread(() -> pushMessageToList(callId, message));
            }
        });
    }

    public boolean sendCallMessage(long sendAsPeerId, TLRPC.TL_textWithEntities message, long callId, TLRPC.InputGroupCall inputGroupCall) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null || service.getAccount() != currentAccount) {
            return false;
        }

        final long randomId = getSendMessagesHelper().getNextRandomId();
        final TLObject request;
        if (service.isConference()) {
            if (service.conference == null || service.conference.groupCall == null) return false;
            if (service.conference.groupCall.id != callId) return false;

            final long localCallId = service.conference.getCallId();
            if (localCallId == -1) return false;

            final TLRPC.TL_groupCallMessage callMessage = new TLRPC.TL_groupCallMessage();
            callMessage.message = message;
            callMessage.random_id = randomId;

            final JSONObject jsonValue = TLJsonBuilder.serialize(callMessage);
            if (jsonValue == null) {
                return false;
            }

            final String jsonString = jsonValue.toString();
            final byte[] buffer = jsonString.getBytes(StandardCharsets.UTF_8);
            final byte[] encrypted = groupCallMessageEncryptImpl(localCallId, buffer);
            if (encrypted == null) {
                return false;
            }

            final TL_phone.sendGroupCallEncryptedMessage req = new TL_phone.sendGroupCallEncryptedMessage();
            req.call = inputGroupCall;
            req.encrypted_message = encrypted;
            request = req;
        } else {
            final TL_phone.sendGroupCallMessage req = new TL_phone.sendGroupCallMessage();
            req.call = inputGroupCall;
            req.message = message;
            req.random_id = randomId;
            request = req;
        }

        GroupCallMessage sendingGroupCallMessage = new GroupCallMessage(currentAccount, sendAsPeerId, randomId, message);
        sendingGroupCallMessage.setIsOut(true);
        pushMessageToList(callId, sendingGroupCallMessage);

        final Runnable markMessageAsDelayed = () -> {
            sendingGroupCallMessage.setIsSendDelayed(true);
            sendingGroupCallMessage.notifyStateUpdate();
        };

        AndroidUtilities.runOnUIThread(markMessageAsDelayed, 1000);
        getConnectionsManager().sendRequest(request, (response, error) -> {
            AndroidUtilities.cancelRunOnUIThread(markMessageAsDelayed);
            sendingGroupCallMessage.setIsSendDelayed(false);
            if (response instanceof TLRPC.Bool) {
                if (response instanceof TLRPC.TL_boolTrue) {
                    sendingGroupCallMessage.setIsSendConfirmed(true);
                } else {
                    sendingGroupCallMessage.setIsSendError(true);
                }
            } else if (response instanceof TLRPC.Updates) {
                sendingGroupCallMessage.setIsSendConfirmed(true);
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
            AndroidUtilities.runOnUIThread(sendingGroupCallMessage::notifyStateUpdate);
        });

        return true;
    }

    public List<GroupCallMessage> getCallMessages(long callId) {
        MessagesList messagesList = callMessagesList.get(callId);
        return messagesList != null ? new ArrayList<>(messagesList.messages) : new ArrayList<>();
    }

    public void subscribeToCallMessages(long callId, CallMessageListener listener) {
        List<CallMessageListener> listeners = callMessagesListeners.get(callId);
        if (listeners == null) {
            listeners = new ArrayList<>();
            callMessagesListeners.put(callId, listeners);
        }

        listeners.add(listener);
    }

    public void unsubscribeFromCallMessages(long callId, CallMessageListener listener) {
        List<CallMessageListener> listeners = callMessagesListeners.get(callId);
        if (listeners == null) {
            return;
        }

        listeners.remove(listener);
        if (listeners.isEmpty()) {
            callMessagesListeners.remove(callId);
        }
    }



    /* * */

    private final LongSparseArray<List<CallMessageListener>> callMessagesListeners = new LongSparseArray<>();
    private final LongSparseArray<MessagesList> callMessagesList = new LongSparseArray<>();

    private void pushMessageToList(long callId, GroupCallMessage message) {
        MessagesList messages = callMessagesList.get(callId);
        if (messages == null) {
            messages = new MessagesList();
            callMessagesList.put(callId, messages);
        }

        if (!messages.push(message)) {
            return;
        }

        List<CallMessageListener> listeners = callMessagesListeners.get(callId);
        if (listeners != null) {
            for (CallMessageListener listener: listeners) {
                listener.onNewGroupCallMessage(callId, message);
            }
        }
        listeners = callMessagesListeners.get(0);
        if (listeners != null) {
            for (CallMessageListener listener: listeners) {
                listener.onNewGroupCallMessage(callId, message);
            }
        }

        AndroidUtilities.runOnUIThread(() -> popMessageFromList(callId), getAppGlobalConfig().groupCallMessageTtl.get(TimeUnit.MILLISECONDS));
    }

    private void popMessageFromList(long callId) {
        MessagesList messages = callMessagesList.get(callId);
        if (messages == null) return;

        messages.pop();
        if (messages.isEmpty()) {
            callMessagesList.remove(callId);
        }

        List<CallMessageListener> listeners = callMessagesListeners.get(callId);
        if (listeners != null) {
            for (CallMessageListener listener: listeners) {
                listener.onPopGroupCallMessage();
            }
        }
        listeners = callMessagesListeners.get(0);
        if (listeners != null) {
            for (CallMessageListener listener: listeners) {
                listener.onPopGroupCallMessage();
            }
        }
    }

    private static class MessagesList {
        private final List<GroupCallMessage> messages = new ArrayList<>();
        private final Set<Long> randomIds = new HashSet<>();

        boolean push(GroupCallMessage message) {
            if (message.randomId != 0) {
                if (!randomIds.add(message.randomId)) {
                    return false;
                }
            }

            messages.add(0, message);
            return true;
        }

        void pop() {
            if (messages.isEmpty()) return;
            GroupCallMessage message = messages.remove(messages.size() - 1);
            if (message.randomId != 0) {
                randomIds.remove(message.randomId);
            }
        }

        boolean isEmpty() {
            return messages.isEmpty();
        }
    }



    /* * */

    @Nullable
    private byte[] groupCallMessageDecrypt(long callId, long userId, byte[] decrypted) {
        final VoIPService service = VoIPService.getSharedInstance();

        if (service == null || service.getAccount() != currentAccount) return null;
        if (service.conference == null || service.conference.groupCall == null) return null;
        if (service.conference.groupCall.id != callId) return null;

        final long localCallId = service.conference.getCallId();
        if (localCallId == -1) return null;

        return groupCallMessageDecryptImpl(localCallId, userId, decrypted);
    }

    private static native byte[] groupCallMessageDecryptImpl(long callId, long userId, byte[] encrypted);
    private static native byte[] groupCallMessageEncryptImpl(long callId, byte[] decrypted);



    /* * */

    private static volatile GroupCallMessagesController[] Instance = new GroupCallMessagesController[UserConfig.MAX_ACCOUNT_COUNT];

    public static GroupCallMessagesController getInstance(int num) {
        GroupCallMessagesController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (GroupCallMessagesController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new GroupCallMessagesController(num);
                }
            }
        }
        return localInstance;
    }

    private GroupCallMessagesController(int accountId) {
        super(accountId);
    }
}
