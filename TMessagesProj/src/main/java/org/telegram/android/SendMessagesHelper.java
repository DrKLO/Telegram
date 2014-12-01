/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class SendMessagesHelper implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.ChatParticipants currentChatInfo = null;
    private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<String, ArrayList<DelayedMessage>>();
    private HashMap<Integer, MessageObject> unsentMessages = new HashMap<Integer, MessageObject>();

    private class DelayedMessage {
        public TLObject sendRequest;
        public TLRPC.TL_decryptedMessage sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.FileLocation location;
        public TLRPC.TL_video videoLocation;
        public TLRPC.TL_audio audioLocation;
        public TLRPC.TL_document documentLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
    }

    private static volatile SendMessagesHelper Instance = null;
    public static SendMessagesHelper getInstance() {
        SendMessagesHelper localInstance = Instance;
        if (localInstance == null) {
            synchronized (SendMessagesHelper.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new SendMessagesHelper();
                }
            }
        }
        return localInstance;
    }

    public SendMessagesHelper() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidUpload);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailUpload);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FilePreparingStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FilePreparingFailed);
    }

    public void cleanUp() {
        delayedMessages.clear();
        unsentMessages.clear();
        currentChatInfo = null;
    }

    public void setCurrentChatInfo(TLRPC.ChatParticipants info) {
        currentChatInfo = info;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            final String location = (String)args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile)args[1];
            final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile)args[2];

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<DelayedMessage> arr = delayedMessages.get(location);
                    if (arr != null) {
                        for (int a = 0; a < arr.size(); a++) {
                            DelayedMessage message = arr.get(a);
                            TLRPC.InputMedia media = null;
                            if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                                media = ((TLRPC.TL_messages_sendMedia)message.sendRequest).media;
                            } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
                                media = ((TLRPC.TL_messages_sendBroadcast)message.sendRequest).media;
                            }

                            if (file != null && media != null) {
                                if (message.type == 0) {
                                    media.file = file;
                                    performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                } else if (message.type == 1) {
                                    if (media.file == null) {
                                        media.file = file;
                                        if (media.thumb == null && message.location != null) {
                                            performSendDelayedMessage(message);
                                        } else {
                                            performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                        }
                                    } else {
                                        media.thumb = file;
                                        performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                    }
                                } else if (message.type == 2) {
                                    if (media.file == null) {
                                        media.file = file;
                                        if (media.thumb == null && message.location != null) {
                                            performSendDelayedMessage(message);
                                        } else {
                                            performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                        }
                                    } else {
                                        media.thumb = file;
                                        performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                    }
                                } else if (message.type == 3) {
                                    media.file = file;
                                    performSendMessageRequest(message.sendRequest, message.obj.messageOwner, message.originalPath);
                                }
                                arr.remove(a);
                                a--;
                            } else if (encryptedFile != null && message.sendEncryptedRequest != null) {
                                message.sendEncryptedRequest.media.key = encryptedFile.key;
                                message.sendEncryptedRequest.media.iv = encryptedFile.iv;
                                SecretChatHelper.getInstance().performSendEncryptedRequest(message.sendEncryptedRequest, message.obj.messageOwner, message.encryptedChat, encryptedFile, message.originalPath);
                                arr.remove(a);
                                a--;
                            }
                        }
                        if (arr.isEmpty()) {
                            delayedMessages.remove(location);
                        }
                    }
                }
            });
        } else if (id == NotificationCenter.FileDidFailUpload) {
            final String location = (String) args[0];
            final boolean enc = (Boolean) args[1];

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<DelayedMessage> arr = delayedMessages.get(location);
                    if (arr != null) {
                        for (int a = 0; a < arr.size(); a++) {
                            DelayedMessage obj = arr.get(a);
                            if (enc && obj.sendEncryptedRequest != null || !enc && obj.sendRequest != null) {
                                MessagesStorage.getInstance().markMessageAsSendError(obj.obj.messageOwner.id);
                                obj.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                arr.remove(a);
                                a--;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, obj.obj.messageOwner.id);
                                processSentMessage(obj.obj.messageOwner.id);
                            }
                        }
                        if (arr.isEmpty()) {
                            delayedMessages.remove(location);
                        }
                    }
                }
            });
        } else if (id == NotificationCenter.FilePreparingStarted) {
            MessageObject messageObject = (MessageObject)args[0];
            String finalPath = (String)args[1];

            ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.obj == messageObject) {
                        message.videoLocation.videoEditedInfo = null;
                        performSendDelayedMessage(message);
                        arr.remove(a);
                        a--;
                        break;
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(messageObject.messageOwner.attachPath);
                }
            }
        } else if (id == NotificationCenter.FileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject)args[0];
            String finalPath = (String)args[1];
            long finalSize = (Long)args[2];
            boolean isEncrypted = ((int)messageObject.getDialogId()) == 0;
            FileLoader.getInstance().checkUploadNewDataAvailable(finalPath, isEncrypted, finalSize);
            if (finalSize != 0) {
                ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
                if (arr != null) {
                    for (DelayedMessage message : arr) {
                        if (message.obj == messageObject) {
                            message.obj.messageOwner.videoEditedInfo = null;
                            message.obj.messageOwner.message = "-1";
                            message.obj.messageOwner.media.video.size = (int)finalSize;

                            ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                            messages.add(message.obj.messageOwner);
                            MessagesStorage.getInstance().putMessages(messages, false, true, false, 0);
                            break;
                        }
                    }
                    if (arr.isEmpty()) {
                        delayedMessages.remove(messageObject.messageOwner.attachPath);
                    }
                }
            }
        } else if (id == NotificationCenter.FilePreparingFailed) {
            MessageObject messageObject = (MessageObject)args[0];
            String finalPath = (String)args[1];
            stopVideoService(messageObject.messageOwner.attachPath);

            ArrayList<DelayedMessage> arr = delayedMessages.get(finalPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.obj == messageObject) {
                        MessagesStorage.getInstance().markMessageAsSendError(message.obj.messageOwner.id);
                        message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        arr.remove(a);
                        a--;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, message.obj.messageOwner.id);
                        processSentMessage(message.obj.messageOwner.id);
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(finalPath);
                }
            }
        }
    }

    public void cancelSendingMessage(MessageObject object) {
        String keyToRemvoe = null;
        boolean enc = false;
        for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
            ArrayList<DelayedMessage> messages = entry.getValue();
            for (int a = 0; a < messages.size(); a++) {
                DelayedMessage message = messages.get(a);
                if (message.obj.messageOwner.id == object.messageOwner.id) {
                    messages.remove(a);
                    MediaController.getInstance().cancelVideoConvert(message.obj);
                    if (messages.size() == 0) {
                        keyToRemvoe = entry.getKey();
                        if (message.sendEncryptedRequest != null) {
                            enc = true;
                        }
                    }
                    break;
                }
            }
        }
        if (keyToRemvoe != null) {
            FileLoader.getInstance().cancelUploadFile(keyToRemvoe, enc);
            stopVideoService(keyToRemvoe);
        }
        ArrayList<Integer> messages = new ArrayList<Integer>();
        messages.add(object.messageOwner.id);
        MessagesController.getInstance().deleteMessages(messages, null, null);
    }

    public boolean retrySendMessage(MessageObject messageObject, boolean unsent) {
        if (messageObject.messageOwner.id >= 0) {
            return false;
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
            int enc_id = (int) (messageObject.getDialogId() >> 32);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(enc_id);
            if (encryptedChat == null) {
                MessagesStorage.getInstance().markMessageAsSendError(messageObject.messageOwner.id);
                messageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, messageObject.messageOwner.id);
                processSentMessage(messageObject.messageOwner.id);
                return false;
            }
            if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                SecretChatHelper.getInstance().sendTTLMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                SecretChatHelper.getInstance().sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                SecretChatHelper.getInstance().sendClearHistoryMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                SecretChatHelper.getInstance().sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
                SecretChatHelper.getInstance().sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                SecretChatHelper.getInstance().sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionTyping) {

            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionResend) {

            }
            return true;
        }
        if (unsent) {
            unsentMessages.put(messageObject.messageOwner.id, messageObject);
        }
        sendMessage(messageObject);
        return true;
    }

    protected void processSentMessage(int id) {
        int prevSize = unsentMessages.size();
        unsentMessages.remove(id);
        if (prevSize != 0 && unsentMessages.size() == 0) {
            checkUnsentMessages();
        }
    }

    public void processForwardFromMyName(MessageObject messageObject, long did) {
        if (messageObject == null) {
            return;
        }
        if (messageObject.messageOwner.media != null && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                sendMessage((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did);
            } else if (messageObject.messageOwner.media.audio instanceof TLRPC.TL_audio) {
                sendMessage((TLRPC.TL_audio) messageObject.messageOwner.media.audio, messageObject.messageOwner.attachPath, did);
            } else if (messageObject.messageOwner.media.video instanceof TLRPC.TL_video) {
                TLRPC.TL_video video = (TLRPC.TL_video) messageObject.messageOwner.media.video;
                video.videoEditedInfo = messageObject.messageOwner.videoEditedInfo;
                sendMessage(video, null, messageObject.messageOwner.attachPath, did);
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                sendMessage((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did);
            } else if (messageObject.messageOwner.media.geo instanceof TLRPC.TL_geoPoint) {
                sendMessage(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long, did);
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                sendMessage(user, did);
            } else {
                sendMessage(messageObject, did);
            }
        } else if (messageObject.messageOwner.message != null) {
            sendMessage(messageObject.messageOwner.message, did);
        } else {
            sendMessage(messageObject, did);
        }
    }

    public void sendMessage(TLRPC.User user, long peer) {
        sendMessage(null, null, null, null, null, null, user, null, null, null, peer, false, null);
    }

    public void sendMessage(MessageObject message) {
        sendMessage(null, null, null, null, null, message, null, null, null, null, message.getDialogId(), true, message.messageOwner.attachPath);
    }

    public void sendMessage(MessageObject message, long peer) {
        sendMessage(null, null, null, null, null, message, null, null, null, null, peer, false, message.messageOwner.attachPath);
    }

    public void sendMessage(TLRPC.TL_document document, String originalPath, String path, long peer) {
        sendMessage(null, null, null, null, null, null, null, document, null, originalPath, peer, false, path);
    }

    public void sendMessage(String message, long peer) {
        sendMessage(message, null, null, null, null, null, null, null, null, null, peer, false, null);
    }

    public void sendMessage(double lat, double lon, long peer) {
        sendMessage(null, lat, lon, null, null, null, null, null, null, null, peer, false, null);
    }

    public void sendMessage(TLRPC.TL_photo photo, String originalPath, long peer) {
        sendMessage(null, null, null, photo, null, null, null, null, null, originalPath, peer, false, null);
    }

    public void sendMessage(TLRPC.TL_video video, String originalPath, String path, long peer) {
        sendMessage(null, null, null, null, video, null, null, null, null, originalPath, peer, false, path);
    }

    public void sendMessage(TLRPC.TL_audio audio, String path, long peer) {
        sendMessage(null, null, null, null, null, null, null, null, audio, null, peer, false, path);
    }

    private void sendMessage(String message, Double lat, Double lon, TLRPC.TL_photo photo, TLRPC.TL_video video, MessageObject msgObj, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_audio audio, String originalPath, long peer, boolean retry, String path) {
        if (peer == 0) {
            return;
        }

        TLRPC.Message newMsg = null;
        int type = -1;
        int lower_id = (int) peer;
        int high_id = (int) (peer >> 32);
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = null;
        ArrayList<TLRPC.InputUser> sendToPeers = null;
        if (lower_id == 0) {
            encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
        }

        if (retry) {
            newMsg = msgObj.messageOwner;

            if (msgObj.type == 0) {
                if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                    type = 4;
                } else {
                    message = newMsg.message;
                    type = 0;
                }
            } else if (msgObj.type == 4) {
                lat = newMsg.media.geo.lat;
                lon = newMsg.media.geo._long;
                type = 1;
            } else if (msgObj.type == 1) {
                if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                    type = 4;
                } else {
                    photo = (TLRPC.TL_photo) newMsg.media.photo;
                    type = 2;
                }
            } else if (msgObj.type == 3) {
                if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                    type = 4;
                } else {
                    type = 3;
                    video = (TLRPC.TL_video) newMsg.media.video;
                    video.videoEditedInfo = newMsg.videoEditedInfo;
                }
            } else if (msgObj.type == 12 || msgObj.type == 13) {
                user = new TLRPC.TL_userRequest();
                user.phone = newMsg.media.phone_number;
                user.first_name = newMsg.media.first_name;
                user.last_name = newMsg.media.last_name;
                user.id = newMsg.media.user_id;
                type = 6;
            } else if (msgObj.type == 8 || msgObj.type == 9) {
                document = (TLRPC.TL_document) newMsg.media.document;
                type = 7;
            } else if (msgObj.type == 2) {
                audio = (TLRPC.TL_audio) newMsg.media.audio;
                type = 8;
            }
        } else {
            if (message != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaEmpty();
                type = 0;
                newMsg.message = message;
            } else if (lat != null && lon != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaGeo();
                newMsg.media.geo = new TLRPC.TL_geoPoint();
                newMsg.media.geo.lat = lat;
                newMsg.media.geo._long = lon;
                newMsg.message = "";
                type = 1;
            } else if (photo != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaPhoto();
                newMsg.media.photo = photo;
                type = 2;
                newMsg.message = "-1";
                TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                newMsg.attachPath = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + location1.volume_id + "_" + location1.local_id + ".jpg";
            } else if (video != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaVideo();
                newMsg.media.video = video;
                newMsg.videoEditedInfo = video.videoEditedInfo;
                type = 3;
                if (video.videoEditedInfo == null) {
                    newMsg.message = "-1";
                } else {
                    newMsg.message = video.videoEditedInfo.getString();
                }
                newMsg.attachPath = path;
            } else if (msgObj != null) {
                newMsg = new TLRPC.TL_messageForwarded();
                if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                    newMsg.fwd_from_id = msgObj.messageOwner.fwd_from_id;
                    newMsg.fwd_date = msgObj.messageOwner.fwd_date;
                    newMsg.media = msgObj.messageOwner.media;
                    newMsg.message = msgObj.messageOwner.message;
                    newMsg.fwd_msg_id = msgObj.messageOwner.id;
                    newMsg.attachPath = msgObj.messageOwner.attachPath;
                    type = 4;
                } else {
                    newMsg.fwd_from_id = msgObj.messageOwner.from_id;
                    newMsg.fwd_date = msgObj.messageOwner.date;
                    newMsg.media = msgObj.messageOwner.media;
                    newMsg.message = msgObj.messageOwner.message;
                    newMsg.fwd_msg_id = msgObj.messageOwner.id;
                    newMsg.attachPath = msgObj.messageOwner.attachPath;
                    type = 4;
                }
            } else if (user != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaContact();
                newMsg.media.phone_number = user.phone;
                newMsg.media.first_name = user.first_name;
                newMsg.media.last_name = user.last_name;
                newMsg.media.user_id = user.id;
                newMsg.message = "";
                type = 6;
            } else if (document != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaDocument();
                newMsg.media.document = document;
                type = 7;
                newMsg.message = "-1";
                newMsg.attachPath = path;
            } else if (audio != null) {
                if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                    newMsg = new TLRPC.TL_message_secret();
                } else {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaAudio();
                newMsg.media.audio = audio;
                type = 8;
                newMsg.message = "-1";
                newMsg.attachPath = path;
            }
            newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
            newMsg.from_id = UserConfig.getClientUserId();
            newMsg.flags |= TLRPC.MESSAGE_FLAG_OUT;
            UserConfig.saveConfig(false);
        }
        if (newMsg.random_id == 0) {
            newMsg.random_id = getNextRandomId();
        }
        newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
        newMsg.flags |= TLRPC.MESSAGE_FLAG_UNREAD;
        newMsg.dialog_id = peer;
        if (lower_id != 0) {
            if (high_id == 1) {
                if (currentChatInfo == null) {
                    processSentMessage(newMsg.id);
                    return;
                }
                sendToPeers = new ArrayList<TLRPC.InputUser>();
                for (TLRPC.TL_chatParticipant participant : currentChatInfo.participants) {
                    TLRPC.User sendToUser = MessagesController.getInstance().getUser(participant.user_id);
                    TLRPC.InputUser peerUser = MessagesController.getInputUser(sendToUser);
                    if (peerUser != null) {
                        sendToPeers.add(peerUser);
                    }
                }
                newMsg.to_id = new TLRPC.TL_peerChat();
                newMsg.to_id.chat_id = lower_id;
            } else {
                if (lower_id < 0) {
                    newMsg.to_id = new TLRPC.TL_peerChat();
                    newMsg.to_id.chat_id = -lower_id;
                    sendToPeer = new TLRPC.TL_inputPeerChat();
                    sendToPeer.chat_id = -lower_id;
                } else {
                    newMsg.to_id = new TLRPC.TL_peerUser();
                    newMsg.to_id.user_id = lower_id;

                    TLRPC.User sendToUser = MessagesController.getInstance().getUser(lower_id);
                    if (sendToUser == null) {
                        processSentMessage(newMsg.id);
                        return;
                    }
                    if (sendToUser instanceof TLRPC.TL_userForeign || sendToUser instanceof TLRPC.TL_userRequest) {
                        sendToPeer = new TLRPC.TL_inputPeerForeign();
                        sendToPeer.user_id = sendToUser.id;
                        sendToPeer.access_hash = sendToUser.access_hash;
                    } else {
                        sendToPeer = new TLRPC.TL_inputPeerContact();
                        sendToPeer.user_id = sendToUser.id;
                    }
                }
            }
        } else {
            newMsg.to_id = new TLRPC.TL_peerUser();
            if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
                newMsg.to_id.user_id = encryptedChat.admin_id;
            } else {
                newMsg.to_id.user_id = encryptedChat.participant_id;
            }
            newMsg.ttl = encryptedChat.ttl;
            if (newMsg.ttl != 0) {
                if (newMsg.media instanceof TLRPC.TL_messageMediaAudio) {
                    newMsg.ttl = Math.max(encryptedChat.ttl, newMsg.media.audio.duration + 1);
                } else if (newMsg.media instanceof TLRPC.TL_messageMediaVideo) {
                    newMsg.ttl = Math.max(encryptedChat.ttl, newMsg.media.video.duration + 1);
                }
            }
        }

        MessageObject newMsgObj = new MessageObject(newMsg, null, 2);
        newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;

        ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
        MessagesController.getInstance().updateInterfaceWithMessages(peer, objArr);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

        try {
            if (type == 0) {
                if (encryptedChat == null) {
                    if (sendToPeers != null) {
                        TLRPC.TL_messages_sendBroadcast reqSend = new TLRPC.TL_messages_sendBroadcast();
                        reqSend.message = message;
                        reqSend.contacts = sendToPeers;
                        reqSend.media = new TLRPC.TL_inputMediaEmpty();
                        performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                    } else {
                        TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                        reqSend.message = message;
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
                        performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                        reqSend.ttl = newMsg.ttl;
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_old();
                        reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
                        Utilities.random.nextBytes(reqSend.random_bytes);
                    }
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = message;
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
                    SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
                }
            } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8) {
                if (encryptedChat == null) {
                    TLRPC.InputMedia inputMedia = null;
                    DelayedMessage delayedMessage = null;
                    if (type == 1) {
                        inputMedia = new TLRPC.TL_inputMediaGeoPoint();
                        inputMedia.geo_point = new TLRPC.TL_inputGeoPoint();
                        inputMedia.geo_point.lat = lat;
                        inputMedia.geo_point._long = lon;
                    } else if (type == 2) {
                        if (photo.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedPhoto();
                            delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 0;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                        } else {
                            TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                            media.id = new TLRPC.TL_inputPhoto();
                            media.id.id = photo.id;
                            media.id.access_hash = photo.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 3) {
                        if (video.access_hash == 0) {
                            if (video.thumb.location != null) {
                                inputMedia = new TLRPC.TL_inputMediaUploadedThumbVideo();
                            } else {
                                inputMedia = new TLRPC.TL_inputMediaUploadedVideo();
                            }
                            inputMedia.duration = video.duration;
                            inputMedia.w = video.w;
                            inputMedia.h = video.h;
                            inputMedia.mime_type = video.mime_type;
                            delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 1;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.location = video.thumb.location;
                            delayedMessage.videoLocation = video;
                        } else {
                            TLRPC.TL_inputMediaVideo media = new TLRPC.TL_inputMediaVideo();
                            media.id = new TLRPC.TL_inputVideo();
                            media.id.id = video.id;
                            media.id.access_hash = video.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 6) {
                        inputMedia = new TLRPC.TL_inputMediaContact();
                        inputMedia.phone_number = user.phone;
                        inputMedia.first_name = user.first_name;
                        inputMedia.last_name = user.last_name;
                    } else if (type == 7) {
                        if (document.access_hash == 0) {
                            if (document.thumb.location != null && document.thumb.location instanceof TLRPC.TL_fileLocation) {
                                inputMedia = new TLRPC.TL_inputMediaUploadedThumbDocument();
                            } else {
                                inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            }
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.file_name = document.file_name;
                            delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 2;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.documentLocation = document;
                            delayedMessage.location = document.thumb.location;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 8) {
                        if (audio.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedAudio();
                            inputMedia.duration = audio.duration;
                            inputMedia.mime_type = audio.mime_type;
                            delayedMessage = new DelayedMessage();
                            delayedMessage.type = 3;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.audioLocation = audio;
                        } else {
                            TLRPC.TL_inputMediaAudio media = new TLRPC.TL_inputMediaAudio();
                            media.id = new TLRPC.TL_inputAudio();
                            media.id.id = audio.id;
                            media.id.access_hash = audio.access_hash;
                            inputMedia = media;
                        }
                    }

                    TLObject reqSend = null;

                    if (sendToPeers != null) {
                        TLRPC.TL_messages_sendBroadcast request = new TLRPC.TL_messages_sendBroadcast();
                        request.contacts = sendToPeers;
                        request.media = inputMedia;
                        request.message = "";
                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                    } else {
                        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
                        request.peer = sendToPeer;
                        request.random_id = newMsg.random_id;
                        request.media = inputMedia;
                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                    }
                    if (type == 1) {
                        performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                    } else if (type == 2) {
                        if (photo.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                        }
                    } else if (type == 3) {
                        if (video.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                        }
                    } else if (type == 6) {
                        performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                    } else if (type == 7) {
                        if (document.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                        }
                    } else if (type == 8) {
                        if (audio.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
                        }
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                        reqSend.ttl = newMsg.ttl;
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_old();
                        reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
                        Utilities.random.nextBytes(reqSend.random_bytes);
                    }
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = "";
                    if (type == 1) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
                        reqSend.media.lat = lat;
                        reqSend.media._long = lon;
                        SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
                    } else if (type == 2) {
                        TLRPC.PhotoSize small = photo.sizes.get(0);
                        TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
                        reqSend.media.thumb = small.bytes;
                        reqSend.media.thumb_h = small.h;
                        reqSend.media.thumb_w = small.w;
                        reqSend.media.w = big.w;
                        reqSend.media.h = big.h;
                        reqSend.media.size = big.size;
                        if (big.location.key == null) {
                            DelayedMessage delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.sendEncryptedRequest = reqSend;
                            delayedMessage.type = 0;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.encryptedChat = encryptedChat;
                            delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = big.location.volume_id;
                            encryptedFile.access_hash = big.location.secret;
                            reqSend.media.key = big.location.key;
                            reqSend.media.iv = big.location.iv;
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
                        }
                    } else if (type == 3) {
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo();
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo_old();
                        }
                        reqSend.media.duration = video.duration;
                        reqSend.media.size = video.size;
                        reqSend.media.w = video.w;
                        reqSend.media.h = video.h;
                        reqSend.media.thumb = video.thumb.bytes;
                        reqSend.media.thumb_h = video.thumb.h;
                        reqSend.media.thumb_w = video.thumb.w;
                        reqSend.media.mime_type = "video/mp4";
                        if (video.access_hash == 0) {
                            DelayedMessage delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.sendEncryptedRequest = reqSend;
                            delayedMessage.type = 1;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.encryptedChat = encryptedChat;
                            delayedMessage.videoLocation = video;
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = video.id;
                            encryptedFile.access_hash = video.access_hash;
                            reqSend.media.key = video.key;
                            reqSend.media.iv = video.iv;
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
                        }
                    } else if (type == 6) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                        reqSend.media.phone_number = user.phone;
                        reqSend.media.first_name = user.first_name;
                        reqSend.media.last_name = user.last_name;
                        reqSend.media.user_id = user.id;
                        SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
                    } else if (type == 7) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                        reqSend.media.size = document.size;
                        if (!(document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                            reqSend.media.thumb = document.thumb.bytes;
                            reqSend.media.thumb_h = document.thumb.h;
                            reqSend.media.thumb_w = document.thumb.w;
                        } else {
                            reqSend.media.thumb = new byte[0];
                            reqSend.media.thumb_h = 0;
                            reqSend.media.thumb_w = 0;
                        }
                        reqSend.media.file_name = document.file_name;
                        reqSend.media.mime_type = document.mime_type;
                        if (document.access_hash == 0) {
                            DelayedMessage delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.sendEncryptedRequest = reqSend;
                            delayedMessage.type = 2;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.encryptedChat = encryptedChat;
                            delayedMessage.documentLocation = document;
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = document.id;
                            encryptedFile.access_hash = document.access_hash;
                            reqSend.media.key = document.key;
                            reqSend.media.iv = document.iv;
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
                        }
                    } else if (type == 8) {
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio();
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio_old();
                        }
                        reqSend.media.duration = audio.duration;
                        reqSend.media.size = audio.size;
                        reqSend.media.mime_type = "audio/ogg";

                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.type = 3;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.audioLocation = audio;
                        performSendDelayedMessage(delayedMessage);
                    }
                }
            } else if (type == 4) {
                TLRPC.TL_messages_forwardMessage reqSend = new TLRPC.TL_messages_forwardMessage();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (msgObj.messageOwner.id >= 0) {
                    reqSend.id = msgObj.messageOwner.id;
                } else {
                    reqSend.id = msgObj.messageOwner.fwd_msg_id;
                }
                performSendMessageRequest(reqSend, newMsgObj.messageOwner, null);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.messageOwner.id);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.messageOwner.id);
            processSentMessage(newMsgObj.messageOwner.id);
        }
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        if (message.type == 0) {
            String location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
            putToDelayedMessages(location, message);
            if (message.sendRequest != null) {
                FileLoader.getInstance().uploadFile(location, false, true);
            } else {
                FileLoader.getInstance().uploadFile(location, true, true);
            }
        } else if (message.type == 1) {
            if (message.videoLocation.videoEditedInfo != null) {
                String location = message.obj.messageOwner.attachPath;
                if (location == null) {
                    location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.videoLocation.id + ".mp4";
                }
                putToDelayedMessages(location, message);
                MediaController.getInstance().scheduleVideoConvert(message.obj);
            } else {
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media = null;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        if (location == null) {
                            location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.videoLocation.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        if (message.obj.messageOwner.videoEditedInfo != null) {
                            FileLoader.getInstance().uploadFile(location, false, false, message.videoLocation.size);
                        } else {
                            FileLoader.getInstance().uploadFile(location, false, false);
                        }
                    } else {
                        String location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                        putToDelayedMessages(location, message);
                        FileLoader.getInstance().uploadFile(location, false, true);
                    }
                } else {
                    String location = message.obj.messageOwner.attachPath;
                    if (location == null) {
                        location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.videoLocation.id + ".mp4";
                    }
                    putToDelayedMessages(location, message);
                    if (message.obj.messageOwner.videoEditedInfo != null) {
                        FileLoader.getInstance().uploadFile(location, true, false, message.videoLocation.size);
                    } else {
                        FileLoader.getInstance().uploadFile(location, true, false);
                    }
                }
            }
        } else if (message.type == 2) {
            TLRPC.InputMedia media = null;
            if (message.sendRequest != null) {
                if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                    media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
                    media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                }
                if (media.file == null) {
                    String location = message.obj.messageOwner.attachPath;
                    putToDelayedMessages(location, message);
                    if (message.sendRequest != null) {
                        FileLoader.getInstance().uploadFile(location, false, false);
                    } else {
                        FileLoader.getInstance().uploadFile(location, true, false);
                    }
                } else if (media.thumb == null && message.location != null) {
                    String location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, false, true);
                }
            } else {
                String location = message.obj.messageOwner.attachPath;
                putToDelayedMessages(location, message);
                FileLoader.getInstance().uploadFile(location, true, false);
            }
        } else if (message.type == 3) {
            String location = message.obj.messageOwner.attachPath;
            putToDelayedMessages(location, message);
            if (message.sendRequest != null) {
                FileLoader.getInstance().uploadFile(location, false, true);
            } else {
                FileLoader.getInstance().uploadFile(location, true, true);
            }
        }
    }

    protected void stopVideoService(final String path) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stopEncodingService, path);
                    }
                });
            }
        });
    }

    private void performSendMessageRequest(final TLObject req, final TLRPC.Message newMsgObj, final String originalPath) {
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final int oldId = newMsgObj.id;
                    final boolean isBroadcast = req instanceof TLRPC.TL_messages_sendBroadcast;
                    final ArrayList<TLRPC.Message> sentMessages = new ArrayList<TLRPC.Message>();
                    final String attachPath = newMsgObj.attachPath;

                    if (response instanceof TLRPC.messages_SentMessage) {
                        TLRPC.messages_SentMessage res = (TLRPC.messages_SentMessage) response;
                        newMsgObj.id = res.id;
                        newMsgObj.date = res.date;
                        MessagesController.getInstance().processNewDifferenceParams(res.seq, res.pts, res.date);
                    } else if (response instanceof TLRPC.messages_StatedMessage) {
                        TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                        sentMessages.add(res.message);
                        newMsgObj.id = res.message.id;
                        processSentMessage(newMsgObj, res.message, originalPath);
                        MessagesController.getInstance().processNewDifferenceParams(res.seq, res.pts, res.message.date);
                    } else if (response instanceof TLRPC.messages_StatedMessages) {
                        TLRPC.messages_StatedMessages res = (TLRPC.messages_StatedMessages) response;
                        if (!res.messages.isEmpty()) {
                            sentMessages.addAll(res.messages);
                            TLRPC.Message message = res.messages.get(0);
                            if (!isBroadcast) {
                                newMsgObj.id = message.id;
                            }
                            processSentMessage(newMsgObj, message, originalPath);
                        }
                        MessagesController.getInstance().processNewDifferenceParams(res.seq, res.pts, -1);
                    }
                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.random_id, oldId, (isBroadcast ? oldId : newMsgObj.id), 0, false);
                            MessagesStorage.getInstance().putMessages(sentMessages, true, false, isBroadcast, 0);
                            if (isBroadcast) {
                                ArrayList<TLRPC.Message> currentMessage = new ArrayList<TLRPC.Message>();
                                currentMessage.add(newMsgObj);
                                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                MessagesStorage.getInstance().putMessages(currentMessage, true, false, false, 0);
                            }
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                    if (isBroadcast) {
                                        for (TLRPC.Message message : sentMessages) {
                                            ArrayList<MessageObject> arr = new ArrayList<MessageObject>();
                                            MessageObject messageObject = new MessageObject(message, null, 0);
                                            arr.add(messageObject);
                                            MessagesController.getInstance().updateInterfaceWithMessages(messageObject.getDialogId(), arr, isBroadcast);
                                        }
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, (isBroadcast ? oldId : newMsgObj.id), newMsgObj);
                                    processSentMessage(oldId);
                                }
                            });
                            if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                stopVideoService(attachPath);
                            }
                        }
                    });
                } else {
                    MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.id);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                            processSentMessage(newMsgObj.id);
                            if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                stopVideoService(newMsgObj.attachPath);
                            }
                        }
                    });
                }
            }
        }, (req instanceof TLRPC.TL_messages_forwardMessages ? null : new RPCRequest.RPCQuickAckDelegate() {
            @Override
            public void quickAck() {
                final int msg_id = newMsgObj.id;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByAck, msg_id);
                    }
                });
            }
        }), true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassCanCompress, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }

    private void processSentMessage(TLRPC.Message newMsg, TLRPC.Message sentMessage, String originalPath) {
        if (sentMessage == null) {
            return;
        }
        if (sentMessage.media instanceof TLRPC.TL_messageMediaPhoto && sentMessage.media.photo != null && newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
            MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.photo, 0);

            for (TLRPC.PhotoSize size : sentMessage.media.photo.sizes) {
                if (size instanceof TLRPC.TL_photoSizeEmpty) {
                    continue;
                }
                for (TLRPC.PhotoSize size2 : newMsg.media.photo.sizes) {
                    if (size.type.equals(size2.type)) {
                        String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                        String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                        if (fileName.equals(fileName2)) {
                            break;
                        }
                        File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                        File cacheFile2 = null;
                        if (sentMessage.media.photo.sizes.size() == 1 || size.w > 80 || size.h > 80) {
                            cacheFile2 = FileLoader.getPathToAttach(size);
                        } else {
                            cacheFile2 = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                        }
                        cacheFile.renameTo(cacheFile2);
                        ImageLoader.getInstance().replaceImageInCache(fileName, fileName2);
                        size2.location = size.location;
                        break;
                    }
                }
            }
            sentMessage.message = newMsg.message;
            sentMessage.attachPath = newMsg.attachPath;
            newMsg.media.photo.id = sentMessage.media.photo.id;
            newMsg.media.photo.access_hash = sentMessage.media.photo.access_hash;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaVideo && sentMessage.media.video != null && newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
            MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.video, 2);

            TLRPC.PhotoSize size2 = newMsg.media.video.thumb;
            TLRPC.PhotoSize size = sentMessage.media.video.thumb;
            if (size2.location != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                    File cacheFile2 = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                    cacheFile.renameTo(cacheFile2);
                    ImageLoader.getInstance().replaceImageInCache(fileName, fileName2);
                    size2.location = size.location;
                }
            }

            sentMessage.message = newMsg.message;
            newMsg.media.video.dc_id = sentMessage.media.video.dc_id;
            newMsg.media.video.id = sentMessage.media.video.id;
            newMsg.media.video.access_hash = sentMessage.media.video.access_hash;

            if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                File cacheFile = new File(newMsg.attachPath);
                File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.video);
                if (!cacheFile.renameTo(cacheFile2)) {
                    sentMessage.attachPath = newMsg.attachPath;
                }
            } else {
                sentMessage.attachPath = newMsg.attachPath;
            }
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaDocument && sentMessage.media.document != null && newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
            MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.document, 1);

            TLRPC.PhotoSize size2 = newMsg.media.document.thumb;
            TLRPC.PhotoSize size = sentMessage.media.document.thumb;
            if (size2.location != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                    File cacheFile2 = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                    cacheFile.renameTo(cacheFile2);
                    ImageLoader.getInstance().replaceImageInCache(fileName, fileName2);
                    size2.location = size.location;
                }
            }

            newMsg.media.document.dc_id = sentMessage.media.document.dc_id;
            newMsg.media.document.id = sentMessage.media.document.id;
            newMsg.media.document.access_hash = sentMessage.media.document.access_hash;

            if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                File cacheFile = new File(newMsg.attachPath);
                File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.document);
                if (!cacheFile.renameTo(cacheFile2)) {
                    sentMessage.attachPath = newMsg.attachPath;
                    sentMessage.message = newMsg.message;
                } else {
                    newMsg.attachPath = "";
                }
            } else {
                sentMessage.attachPath = newMsg.attachPath;
                sentMessage.message = newMsg.message;
            }
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaAudio && sentMessage.media.audio != null && newMsg.media instanceof TLRPC.TL_messageMediaAudio && newMsg.media.audio != null) {
            sentMessage.message = newMsg.message;

            String fileName = newMsg.media.audio.dc_id + "_" + newMsg.media.audio.id + ".ogg";
            newMsg.media.audio.dc_id = sentMessage.media.audio.dc_id;
            newMsg.media.audio.id = sentMessage.media.audio.id;
            newMsg.media.audio.access_hash = sentMessage.media.audio.access_hash;
            String fileName2 = sentMessage.media.audio.dc_id + "_" + sentMessage.media.audio.id + ".ogg";
            if (!fileName.equals(fileName2)) {
                File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.audio);
                if (!cacheFile.renameTo(cacheFile2)) {
                    sentMessage.attachPath = newMsg.attachPath;
                }
            }
        }
    }

    private void putToDelayedMessages(String location, DelayedMessage message) {
        ArrayList<DelayedMessage> arrayList = delayedMessages.get(location);
        if (arrayList == null) {
            arrayList = new ArrayList<DelayedMessage>();
            delayedMessages.put(location, arrayList);
        }
        arrayList.add(message);
    }

    protected long getNextRandomId() {
        long val = 0;
        while (val == 0) {
            val = Utilities.random.nextLong();
        }
        return val;
    }

    public void checkUnsentMessages() {
        MessagesStorage.getInstance().getUnsentMessages(1000);
    }

    protected void processUnsentMessages(final ArrayList<TLRPC.Message> messages, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().putUsers(users, true);
                MessagesController.getInstance().putChats(chats, true);
                MessagesController.getInstance().putEncryptedChats(encryptedChats, true);
                for (TLRPC.Message message : messages) {
                    MessageObject messageObject = new MessageObject(message, null, 0);
                    retrySendMessage(messageObject, true);
                }
            }
        });
    }

    public TLRPC.TL_photo generatePhotoSizes(String path, Uri imageUri) {
        long time = System.currentTimeMillis();
        Bitmap bitmap = ImageLoader.loadBitmap(path, imageUri, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize());
        if (bitmap == null && AndroidUtilities.getPhotoSize() != 800) {
            bitmap = ImageLoader.loadBitmap(path, imageUri, 800, 800);
        }
        ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<TLRPC.PhotoSize>();
        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
        if (size != null) {
            sizes.add(size);
        }
        size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 80, false);
        if (size != null) {
            sizes.add(size);
        }
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (sizes.isEmpty()) {
            return null;
        } else {
            UserConfig.saveConfig(false);
            TLRPC.TL_photo photo = new TLRPC.TL_photo();
            photo.user_id = UserConfig.getClientUserId();
            photo.date = ConnectionsManager.getInstance().getCurrentTime();
            photo.sizes = sizes;
            photo.caption = "";
            photo.geo = new TLRPC.TL_geoPointEmpty();
            return photo;
        }
    }

    private static boolean prepareSendingDocumentInternal(String path, String originalPath, Uri uri, String mime, final long dialog_id) {
        if ((path == null || path.length() == 0) && uri == null) {
            return false;
        }
        MimeTypeMap myMime = MimeTypeMap.getSingleton();
        if (uri != null) {
            String extension = null;
            if (mime != null) {
                extension = myMime.getExtensionFromMimeType(mime);
            }
            if (extension == null) {
                extension = "txt";
            }
            path = MediaController.copyDocumentToCache(uri, extension);
            if (path == null) {
                return false;
            }
        }
        final File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            return false;
        }

        boolean isEncrypted = (int)dialog_id == 0;

        String name = f.getName();
        if (name == null) {
            name = "noname";
        }
        String ext = "";
        int idx = path.lastIndexOf(".");
        if (idx != -1) {
            ext = path.substring(idx + 1);
        }
        if (originalPath != null) {
            originalPath += "" + f.length();
        }

        TLRPC.TL_document document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 1 : 4);
        if (document == null && !path.equals(originalPath)) {
            document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(path + f.length(), !isEncrypted ? 1 : 4);
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.user_id = UserConfig.getClientUserId();
            document.date = ConnectionsManager.getInstance().getCurrentTime();
            document.file_name = name;
            document.size = (int)f.length();
            document.dc_id = 0;
            if (ext.length() != 0) {
                String mimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                if (mimeType != null) {
                    document.mime_type = mimeType;
                } else {
                    document.mime_type = "application/octet-stream";
                }
            } else {
                document.mime_type = "application/octet-stream";
            }
            if (document.mime_type.equals("image/gif")) {
                try {
                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90);
                    if (bitmap != null) {
                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            if (document.thumb == null) {
                document.thumb = new TLRPC.TL_photoSizeEmpty();
                document.thumb.type = "s";
            }
        }

        final TLRPC.TL_document documentFinal = document;
        final String originalPathFinal = originalPath;
        final String pathFinal = path;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                SendMessagesHelper.getInstance().sendMessage(documentFinal, originalPathFinal, pathFinal, dialog_id);
            }
        });
        return true;
    }

    public static void prepareSendingDocument(String path, String originalPath, Uri uri, String mine, long dialog_id) {
        if ((path == null || originalPath == null) && uri == null) {
            return;
        }
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<String> originalPaths = new ArrayList<String>();
        ArrayList<Uri> uris = null;
        if (uri != null) {
            uris = new ArrayList<Uri>();
        }
        paths.add(path);
        originalPaths.add(originalPath);
        prepareSendingDocuments(paths, originalPaths, uris, mine, dialog_id);
    }

    public static void prepareSendingDocuments(final ArrayList<String> paths, final ArrayList<String> originalPaths, final ArrayList<Uri> uris, final String mime, final long dialog_id) {
        if (paths == null && originalPaths == null && uris == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean error = false;
                if (paths != null) {
                    for (int a = 0; a < paths.size(); a++) {
                        if (!prepareSendingDocumentInternal(paths.get(a), originalPaths.get(a), null, mime, dialog_id)) {
                            error = true;
                        }
                    }
                }
                if (uris != null) {
                    for (int a = 0; a < uris.size(); a++) {
                        if (!prepareSendingDocumentInternal(null, null, uris.get(a), mime, dialog_id)) {
                            error = true;
                        }
                    }
                }
                if (error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Toast toast = Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
                                toast.show();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    public static void prepareSendingPhoto(String imageFilePath, Uri imageUri, long dialog_id) {
        ArrayList<String> paths = null;
        ArrayList<Uri> uris = null;
        if (imageFilePath != null && imageFilePath.length() != 0) {
            paths = new ArrayList<String>();
            paths.add(imageFilePath);
        }
        if (imageUri != null) {
            uris = new ArrayList<Uri>();
            uris.add(imageUri);
        }
        prepareSendingPhotos(paths, uris, dialog_id);
    }

    public static void prepareSendingPhotos(ArrayList<String> paths, ArrayList<Uri> uris, final long dialog_id) {
        if (paths == null && uris == null || paths != null && paths.isEmpty() || uris != null && uris.isEmpty()) {
            return;
        }
        final ArrayList<String> pathsCopy = new ArrayList<String>();
        final ArrayList<Uri> urisCopy = new ArrayList<Uri>();
        if (paths != null) {
            pathsCopy.addAll(paths);
        }
        if (uris != null) {
            urisCopy.addAll(uris);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isEncrypted = (int)dialog_id == 0;

                ArrayList<String> sendAsDocuments = null;
                ArrayList<String> sendAsDocumentsOriginal = null;
                int count = !pathsCopy.isEmpty() ? pathsCopy.size() : urisCopy.size();
                String path = null;
                Uri uri = null;
                for (int a = 0; a < count; a++) {
                    if (!pathsCopy.isEmpty()) {
                        path = pathsCopy.get(a);
                    } else if (!urisCopy.isEmpty()) {
                        uri = urisCopy.get(a);
                    }

                    String originalPath = path;
                    String tempPath = path;
                    if (tempPath == null && uri != null) {
                        tempPath = Utilities.getPath(uri);
                        originalPath = uri.toString();
                    }

                    boolean isGif = false;
                    if (tempPath != null && tempPath.endsWith(".gif")) {
                        isGif = true;
                    } else if (tempPath == null && uri != null) {
                        isGif = MediaController.isGif(uri);
                        if (isGif) {
                            originalPath = uri.toString();
                            tempPath = MediaController.copyDocumentToCache(uri, "gif");
                        }
                    }

                    if (isGif) {
                        if (sendAsDocuments == null) {
                            sendAsDocuments = new ArrayList<String>();
                            sendAsDocumentsOriginal = new ArrayList<String>();
                        }
                        sendAsDocuments.add(tempPath);
                        sendAsDocumentsOriginal.add(originalPath);
                    } else {
                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        } else {
                            originalPath = null;
                        }
                        TLRPC.TL_photo photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 0 : 3);
                        if (photo == null && uri != null) {
                            photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(Utilities.getPath(uri), !isEncrypted ? 0 : 3);
                        }
                        if (photo == null) {
                            photo = SendMessagesHelper.getInstance().generatePhotoSizes(path, uri);
                        }
                        if (photo != null) {
                            final String originalPathFinal = originalPath;
                            final TLRPC.TL_photo photoFinal = photo;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    SendMessagesHelper.getInstance().sendMessage(photoFinal, originalPathFinal, dialog_id);
                                }
                            });
                        }
                    }
                }
                if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                    for (int a = 0; a < sendAsDocuments.size(); a++) {
                        prepareSendingDocumentInternal(sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a), null, "gif", dialog_id);
                    }
                }
            }
        }).start();
    }

    public static void prepareSendingVideo(final String videoPath, final long estimatedSize, final long duration, final int width, final int height, final TLRPC.VideoEditedInfo videoEditedInfo, final long dialog_id) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isEncrypted = (int)dialog_id == 0;

                String path = videoPath;
                String originalPath = videoPath;
                File temp = new File(originalPath);
                originalPath += temp.length() + "_" + temp.lastModified();
                if (videoEditedInfo != null) {
                    originalPath += duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime;
                }
                TLRPC.TL_video video = (TLRPC.TL_video)MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 2 : 5);
                if (video == null) {
                    Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, isEncrypted);
                    video = new TLRPC.TL_video();
                    video.thumb = size;
                    if (video.thumb == null) {
                        video.thumb = new TLRPC.TL_photoSizeEmpty();
                        video.thumb.type = "s";
                    } else {
                        video.thumb.type = "s";
                    }
                    video.caption = "";
                    video.mime_type = "video/mp4";
                    video.id = 0;
                    UserConfig.saveConfig(false);

                    if (videoEditedInfo != null) {
                        video.duration = (int)(duration / 1000);
                        if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                            video.w = height;
                            video.h = width;
                        } else {
                            video.w = width;
                            video.h = height;
                        }
                        video.size = (int)estimatedSize;
                        video.videoEditedInfo = videoEditedInfo;
                        String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
                        UserConfig.lastLocalId--;
                        File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                        UserConfig.saveConfig(false);
                        path = cacheFile.getAbsolutePath();
                    } else {
                        if (temp != null && temp.exists()) {
                            video.size = (int) temp.length();
                        }
                        boolean infoObtained = false;
                        if (Build.VERSION.SDK_INT >= 14) {
                            MediaMetadataRetriever mediaMetadataRetriever = null;
                            try {
                                mediaMetadataRetriever = new MediaMetadataRetriever();
                                mediaMetadataRetriever.setDataSource(videoPath);
                                String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                                if (width != null) {
                                    video.w = Integer.parseInt(width);
                                }
                                String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                                if (height != null) {
                                    video.h = Integer.parseInt(height);
                                }
                                String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                if (duration != null) {
                                    video.duration = (int) Math.ceil(Long.parseLong(duration) / 1000.0f);
                                }
                                infoObtained = true;
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            } finally {
                                try {
                                    if (mediaMetadataRetriever != null) {
                                        mediaMetadataRetriever.release();
                                        mediaMetadataRetriever = null;
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                        if (!infoObtained) {
                            try {
                                MediaPlayer mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(new File(videoPath)));
                                if (mp != null) {
                                    video.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                                    video.w = mp.getVideoWidth();
                                    video.h = mp.getVideoHeight();
                                    mp.release();
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }

                final TLRPC.TL_video videoFinal = video;
                final String originalPathFinal = originalPath;
                final String finalPath = path;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        SendMessagesHelper.getInstance().sendMessage(videoFinal, originalPathFinal, finalPath, dialog_id);
                    }
                });
            }
        }).start();
    }
}
