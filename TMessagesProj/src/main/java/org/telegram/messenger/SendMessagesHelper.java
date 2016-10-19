/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.messenger.query.SearchQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.QuickAckDelegate;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;

public class SendMessagesHelper implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.ChatFull currentChatInfo = null;
    private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<>();
    private HashMap<Integer, MessageObject> unsentMessages = new HashMap<>();
    private HashMap<Integer, TLRPC.Message> sendingMessages = new HashMap<>();
    private HashMap<String, MessageObject> waitingForLocation = new HashMap<>();
    private HashMap<String, MessageObject> waitingForCallback = new HashMap<>();

    private LocationProvider locationProvider = new LocationProvider(new LocationProvider.LocationProviderDelegate() {
        @Override
        public void onLocationAcquired(Location location) {
            sendLocation(location);
            waitingForLocation.clear();
        }

        @Override
        public void onUnableLocationAcquire() {
            HashMap<String, MessageObject> waitingForLocationCopy = new HashMap<>(waitingForLocation);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.wasUnableToFindCurrentLocation, waitingForLocationCopy);
            waitingForLocation.clear();
        }
    });

    public static class LocationProvider {

        public interface LocationProviderDelegate {
            void onLocationAcquired(Location location);
            void onUnableLocationAcquire();
        }

        private LocationProviderDelegate delegate;
        private LocationManager locationManager;
        private GpsLocationListener gpsLocationListener = new GpsLocationListener();
        private GpsLocationListener networkLocationListener = new GpsLocationListener();
        private Runnable locationQueryCancelRunnable;
        private Location lastKnownLocation;

        private class GpsLocationListener implements LocationListener {

            @Override
            public void onLocationChanged(Location location) {
                if (location == null || locationQueryCancelRunnable == null) {
                    return;
                }
                FileLog.e("tmessages", "found location " + location);
                lastKnownLocation = location;
                if (location.getAccuracy() < 100) {
                    if (delegate != null) {
                        delegate.onLocationAcquired(location);
                    }
                    if (locationQueryCancelRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(locationQueryCancelRunnable);
                    }
                    cleanup();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        }

        public LocationProvider() {

        }

        public LocationProvider(LocationProviderDelegate locationProviderDelegate) {
            delegate = locationProviderDelegate;
        }

        public void setDelegate(LocationProviderDelegate locationProviderDelegate) {
            delegate = locationProviderDelegate;
        }

        private void cleanup() {
            locationManager.removeUpdates(gpsLocationListener);
            locationManager.removeUpdates(networkLocationListener);
            lastKnownLocation = null;
            locationQueryCancelRunnable = null;
        }

        public void start() {
            if (locationManager == null) {
                locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
            }
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, gpsLocationListener);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, networkLocationListener);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (locationQueryCancelRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(locationQueryCancelRunnable);
            }
            locationQueryCancelRunnable = new Runnable() {
                @Override
                public void run() {
                    if (locationQueryCancelRunnable != this) {
                        return;
                    }
                    if (delegate != null) {
                        if (lastKnownLocation != null) {
                            delegate.onLocationAcquired(lastKnownLocation);
                        } else {
                            delegate.onUnableLocationAcquire();
                        }
                    }
                    cleanup();
                }
            };
            AndroidUtilities.runOnUIThread(locationQueryCancelRunnable, 5000);
        }

        public void stop() {
            if (locationManager == null) {
                return;
            }
            if (locationQueryCancelRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(locationQueryCancelRunnable);

            }
            cleanup();
        }
    }

    protected class DelayedMessage {
        public TLObject sendRequest;
        public TLRPC.TL_decryptedMessage sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.FileLocation location;
        public TLRPC.TL_document documentLocation;
        public String httpLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
        public VideoEditedInfo videoEditedInfo;
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
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.httpFileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.httpFileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    public void cleanup() {
        delayedMessages.clear();
        unsentMessages.clear();
        sendingMessages.clear();
        waitingForLocation.clear();
        waitingForCallback.clear();
        currentChatInfo = null;
        locationProvider.stop();
    }

    public void setCurrentChatInfo(TLRPC.ChatFull info) {
        currentChatInfo = info;
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            final String location = (String) args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile) args[1];
            final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile) args[2];
            ArrayList<DelayedMessage> arr = delayedMessages.get(location);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    TLRPC.InputMedia media = null;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    }

                    if (file != null && media != null) {
                        if (message.type == 0) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                        } else if (message.type == 1) {
                            if (media.file == null) {
                                media.file = file;
                                if (media.thumb == null && message.location != null) {
                                    performSendDelayedMessage(message);
                                } else {
                                    performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                }
                            } else {
                                media.thumb = file;
                                performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                            }
                        } else if (message.type == 2) {
                            if (media.file == null) {
                                media.file = file;
                                if (media.thumb == null && message.location != null) {
                                    performSendDelayedMessage(message);
                                } else {
                                    performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                                }
                            } else {
                                media.thumb = file;
                                performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                            }
                        } else if (message.type == 3) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                        }
                        arr.remove(a);
                        a--;
                    } else if (encryptedFile != null && message.sendEncryptedRequest != null) {
                        if (message.sendEncryptedRequest.media instanceof TLRPC.TL_decryptedMessageMediaVideo ||
                                message.sendEncryptedRequest.media instanceof TLRPC.TL_decryptedMessageMediaPhoto ||
                                message.sendEncryptedRequest.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                            long size = (Long) args[5];
                            message.sendEncryptedRequest.media.size = (int) size;
                        }
                        message.sendEncryptedRequest.media.key = (byte[]) args[3];
                        message.sendEncryptedRequest.media.iv = (byte[]) args[4];
                        SecretChatHelper.getInstance().performSendEncryptedRequest(message.sendEncryptedRequest, message.obj.messageOwner, message.encryptedChat, encryptedFile, message.originalPath, message.obj);
                        arr.remove(a);
                        a--;
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(location);
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            final String location = (String) args[0];
            final boolean enc = (Boolean) args[1];
            ArrayList<DelayedMessage> arr = delayedMessages.get(location);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage obj = arr.get(a);
                    if (enc && obj.sendEncryptedRequest != null || !enc && obj.sendRequest != null) {
                        MessagesStorage.getInstance().markMessageAsSendError(obj.obj.messageOwner);
                        obj.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        arr.remove(a);
                        a--;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, obj.obj.getId());
                        processSentMessage(obj.obj.getId());
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(location);
                }
            }
        } else if (id == NotificationCenter.FilePreparingStarted) {
            MessageObject messageObject = (MessageObject) args[0];
            String finalPath = (String) args[1];

            ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.obj == messageObject) {
                        message.videoEditedInfo = null;
                        performSendDelayedMessage(message);
                        arr.remove(a);
                        break;
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(messageObject.messageOwner.attachPath);
                }
            }
        } else if (id == NotificationCenter.FileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            String finalPath = (String) args[1];
            long finalSize = (Long) args[2];
            boolean isEncrypted = ((int) messageObject.getDialogId()) == 0;
            FileLoader.getInstance().checkUploadNewDataAvailable(finalPath, isEncrypted, finalSize);
            if (finalSize != 0) {
                ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
                if (arr != null) {
                    for (int a = 0; a < arr.size(); a++) {
                        DelayedMessage message = arr.get(a);
                        if (message.obj == messageObject) {
                            message.obj.videoEditedInfo = null;
                            message.obj.messageOwner.message = "-1";
                            message.obj.messageOwner.media.document.size = (int) finalSize;

                            ArrayList<TLRPC.Message> messages = new ArrayList<>();
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
            MessageObject messageObject = (MessageObject) args[0];
            String finalPath = (String) args[1];
            stopVideoService(messageObject.messageOwner.attachPath);

            ArrayList<DelayedMessage> arr = delayedMessages.get(finalPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.obj == messageObject) {
                        MessagesStorage.getInstance().markMessageAsSendError(message.obj.messageOwner);
                        message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        arr.remove(a);
                        a--;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, message.obj.getId());
                        processSentMessage(message.obj.getId());
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(finalPath);
                }
            }
        } else if (id == NotificationCenter.httpFileDidLoaded) {
            String path = (String) args[0];
            ArrayList<DelayedMessage> arr = delayedMessages.get(path);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    final DelayedMessage message = arr.get(a);
                    if (message.type == 0) {
                        String md5 = Utilities.MD5(message.httpLocation) + "." + ImageLoader.getHttpUrlExtension(message.httpLocation, "file");
                        final File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        Utilities.globalQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                final TLRPC.TL_photo photo = SendMessagesHelper.getInstance().generatePhotoSizes(cacheFile.toString(), null);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (photo != null) {
                                            message.httpLocation = null;
                                            message.obj.messageOwner.media.photo = photo;
                                            message.obj.messageOwner.attachPath = cacheFile.toString();
                                            message.location = photo.sizes.get(photo.sizes.size() - 1).location;
                                            ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                            messages.add(message.obj.messageOwner);
                                            MessagesStorage.getInstance().putMessages(messages, false, true, false, 0);
                                            performSendDelayedMessage(message);
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateMessageMedia, message.obj);
                                        } else {
                                            FileLog.e("tmessages", "can't load image " + message.httpLocation + " to file " + cacheFile.toString());
                                            MessagesStorage.getInstance().markMessageAsSendError(message.obj.messageOwner);
                                            message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, message.obj.getId());
                                            processSentMessage(message.obj.getId());
                                        }
                                    }
                                });
                            }
                        });
                    } else if (message.type == 2) {
                        String md5 = Utilities.MD5(message.httpLocation) + ".gif";
                        final File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        Utilities.globalQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                if (message.documentLocation.thumb.location instanceof TLRPC.TL_fileLocationUnavailable) {
                                    try {
                                        Bitmap bitmap = ImageLoader.loadBitmap(cacheFile.getAbsolutePath(), null, 90, 90, true);
                                        if (bitmap != null) {
                                            message.documentLocation.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, message.sendEncryptedRequest != null);
                                            bitmap.recycle();
                                        }
                                    } catch (Exception e) {
                                        message.documentLocation.thumb = null;
                                        FileLog.e("tmessages", e);
                                    }
                                    if (message.documentLocation.thumb == null) {
                                        message.documentLocation.thumb = new TLRPC.TL_photoSizeEmpty();
                                        message.documentLocation.thumb.type = "s";
                                    }
                                }
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        message.httpLocation = null;
                                        message.obj.messageOwner.attachPath = cacheFile.toString();
                                        message.location = message.documentLocation.thumb.location;
                                        ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                        messages.add(message.obj.messageOwner);
                                        MessagesStorage.getInstance().putMessages(messages, false, true, false, 0);
                                        performSendDelayedMessage(message);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateMessageMedia, message.obj);
                                    }
                                });
                            }
                        });
                    }
                }
                delayedMessages.remove(path);
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String path = (String) args[0];
            ArrayList<DelayedMessage> arr = delayedMessages.get(path);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    performSendDelayedMessage(arr.get(a));
                }
                delayedMessages.remove(path);
            }
        } else if (id == NotificationCenter.httpFileDidFailedLoad || id == NotificationCenter.FileDidFailedLoad) {
            String path = (String) args[0];

            ArrayList<DelayedMessage> arr = delayedMessages.get(path);
            if (arr != null) {
                for (DelayedMessage message : arr) {
                    MessagesStorage.getInstance().markMessageAsSendError(message.obj.messageOwner);
                    message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, message.obj.getId());
                    processSentMessage(message.obj.getId());
                }
                delayedMessages.remove(path);
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
                if (message.obj.getId() == object.getId()) {
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
            if (keyToRemvoe.startsWith("http")) {
                ImageLoader.getInstance().cancelLoadHttpFile(keyToRemvoe);
            } else {
                FileLoader.getInstance().cancelUploadFile(keyToRemvoe, enc);
            }
            stopVideoService(keyToRemvoe);
        }
        ArrayList<Integer> messages = new ArrayList<>();
        messages.add(object.getId());
        MessagesController.getInstance().deleteMessages(messages, null, null, object.messageOwner.to_id.channel_id);
    }

    public boolean retrySendMessage(MessageObject messageObject, boolean unsent) {
        if (messageObject.getId() >= 0) {
            return false;
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
            int enc_id = (int) (messageObject.getDialogId() >> 32);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(enc_id);
            if (encryptedChat == null) {
                MessagesStorage.getInstance().markMessageAsSendError(messageObject.messageOwner);
                messageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, messageObject.getId());
                processSentMessage(messageObject.getId());
                return false;
            }
            if (messageObject.messageOwner.random_id == 0) {
                messageObject.messageOwner.random_id = getNextRandomId();
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

            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
                SecretChatHelper.getInstance().sendCommitKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
                SecretChatHelper.getInstance().sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                SecretChatHelper.getInstance().sendRequestKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
                SecretChatHelper.getInstance().sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNoop) {
                SecretChatHelper.getInstance().sendNoopMessage(encryptedChat, messageObject.messageOwner);
            }
            return true;
        }
        if (unsent) {
            unsentMessages.put(messageObject.getId(), messageObject);
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
        if (messageObject.messageOwner.media != null && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage)) {
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                sendMessage((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, null, null);
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                sendMessage((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, null, null);
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                sendMessage(messageObject.messageOwner.media, did, messageObject.replyMessageObject, null, null);
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact_old2();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                sendMessage(user, did, messageObject.replyMessageObject, null, null);
            } else {
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                sendMessage(arrayList, did);
            }
        } else if (messageObject.messageOwner.message != null) {
            TLRPC.WebPage webPage = null;
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                webPage = messageObject.messageOwner.media.webpage;
            }
            sendMessage(messageObject.messageOwner.message, did, messageObject.replyMessageObject, webPage, true, messageObject.messageOwner.entities, null, null);
        } else {
            ArrayList<MessageObject> arrayList = new ArrayList<>();
            arrayList.add(messageObject);
            sendMessage(arrayList, did);
        }
    }

    public void sendSticker(TLRPC.Document document, long peer, MessageObject replyingMessageObject) {
        if (document == null) {
            return;
        }
        if ((int) peer == 0) {
            int high_id = (int) (peer >> 32);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
            if (encryptedChat == null) {
                return;
            }
            if (document.thumb instanceof TLRPC.TL_photoSize) {
                File file = FileLoader.getPathToAttach(document.thumb, true);
                if (file.exists()) {
                    try {
                        int len = (int) file.length();
                        byte[] arr = new byte[(int) file.length()];
                        RandomAccessFile reader = new RandomAccessFile(file, "r");
                        reader.readFully(arr);
                        TLRPC.TL_document newDocument = new TLRPC.TL_document();
                        newDocument.thumb = new TLRPC.TL_photoCachedSize();
                        newDocument.thumb.location = document.thumb.location;
                        newDocument.thumb.size = document.thumb.size;
                        newDocument.thumb.w = document.thumb.w;
                        newDocument.thumb.h = document.thumb.h;
                        newDocument.thumb.type = document.thumb.type;
                        newDocument.thumb.bytes = arr;

                        newDocument.id = document.id;
                        newDocument.access_hash = document.access_hash;
                        newDocument.date = document.date;
                        newDocument.mime_type = document.mime_type;
                        newDocument.size = document.size;
                        newDocument.dc_id = document.dc_id;
                        newDocument.attributes = document.attributes;
                        if (newDocument.mime_type == null) {
                            newDocument.mime_type = "";
                        }
                        document = newDocument;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
        }
        SendMessagesHelper.getInstance().sendMessage((TLRPC.TL_document) document, null, null, peer, replyingMessageObject, null, null);
    }

    public void sendMessage(ArrayList<MessageObject> messages, final long peer) {
        if ((int) peer == 0 || messages == null || messages.isEmpty()) {
            return;
        }
        int lower_id = (int) peer;
        final TLRPC.Peer to_id = MessagesController.getPeer((int) peer);
        boolean isMegagroup = false;
        boolean isSignature = false;
        if (lower_id > 0) {
            TLRPC.User sendToUser = MessagesController.getInstance().getUser(lower_id);
            if (sendToUser == null) {
                return;
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
            if (ChatObject.isChannel(chat)) {
                isMegagroup = chat.megagroup;
                isSignature = chat.signatures;
            }
        }

        ArrayList<MessageObject> objArr = new ArrayList<>();
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        ArrayList<Long> randomIds = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        HashMap<Long, TLRPC.Message> messagesByRandomIds = new HashMap<>();
        TLRPC.InputPeer inputPeer = MessagesController.getInputPeer(lower_id);
        long lastDialogId = 0;
        for (int a = 0; a < messages.size(); a++) {
            MessageObject msgObj = messages.get(a);
            if (msgObj.getId() <= 0) {
                continue;
            }

            final TLRPC.Message newMsg = new TLRPC.TL_message();
            if (msgObj.isForwarded()) {
                newMsg.fwd_from = msgObj.messageOwner.fwd_from;
            } else {
                newMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
                if (msgObj.isFromUser()) {
                    newMsg.fwd_from.from_id = msgObj.messageOwner.from_id;
                    newMsg.fwd_from.flags |= 1;
                } else {
                    newMsg.fwd_from.channel_id = msgObj.messageOwner.to_id.channel_id;
                    newMsg.fwd_from.flags |= 2;
                    if (msgObj.messageOwner.post) {
                        newMsg.fwd_from.channel_post = msgObj.getId();
                        newMsg.fwd_from.flags |= 4;
                        if (msgObj.messageOwner.from_id > 0) {
                            newMsg.fwd_from.from_id = msgObj.messageOwner.from_id;
                            newMsg.fwd_from.flags |= 1;
                        }
                    }
                }
                newMsg.date = msgObj.messageOwner.date;
            }
            newMsg.media = msgObj.messageOwner.media;
            newMsg.flags = TLRPC.MESSAGE_FLAG_FWD;
            if (newMsg.media != null) {
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
            }
            if (isMegagroup) {
                newMsg.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
            }
            if (msgObj.messageOwner.via_bot_id != 0) {
                newMsg.via_bot_id = msgObj.messageOwner.via_bot_id;
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
            }
            newMsg.message = msgObj.messageOwner.message;
            newMsg.fwd_msg_id = msgObj.getId();
            newMsg.attachPath = msgObj.messageOwner.attachPath;
            newMsg.entities = msgObj.messageOwner.entities;
            if (!newMsg.entities.isEmpty()) {
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
            }
            if (newMsg.attachPath == null) {
                newMsg.attachPath = "";
            }
            newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
            newMsg.out = true;
            if (to_id.channel_id != 0 && !isMegagroup) {
                newMsg.from_id = isSignature ? UserConfig.getClientUserId() : -to_id.channel_id;
                newMsg.post = true;
            } else {
                newMsg.from_id = UserConfig.getClientUserId();
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
            }
            if (newMsg.random_id == 0) {
                newMsg.random_id = getNextRandomId();
            }
            randomIds.add(newMsg.random_id);
            messagesByRandomIds.put(newMsg.random_id, newMsg);
            ids.add(newMsg.fwd_msg_id);
            newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                if (!isMegagroup) {
                    newMsg.views = 1;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                } else {
                    newMsg.unread = true;
                }
            } else {
                if ((msgObj.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    newMsg.views = msgObj.messageOwner.views;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                }
                newMsg.unread = true;
            }
            newMsg.dialog_id = peer;
            newMsg.to_id = to_id;
            if (MessageObject.isVoiceMessage(newMsg) && newMsg.to_id.channel_id == 0) {
                newMsg.media_unread = true;
            }
            if (msgObj.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                newMsg.ttl = -msgObj.messageOwner.to_id.channel_id;
            }
            MessageObject newMsgObj = new MessageObject(newMsg, null, true);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            objArr.add(newMsgObj);
            arr.add(newMsg);

            putToSendingMessages(newMsg);
            boolean differentDialog = false;

            if (BuildVars.DEBUG_VERSION) {
                FileLog.e("tmessages", "forward message user_id = " + inputPeer.user_id + " chat_id = " + inputPeer.chat_id + " channel_id = " + inputPeer.channel_id + " access_hash = " + inputPeer.access_hash);
            }

            if (arr.size() == 100 || a == messages.size() - 1 || a != messages.size() - 1 && messages.get(a + 1).getDialogId() != msgObj.getDialogId()) {
                MessagesStorage.getInstance().putMessages(new ArrayList<>(arr), false, true, false, 0);
                MessagesController.getInstance().updateInterfaceWithMessages(peer, objArr);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                UserConfig.saveConfig(false);

                TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
                req.to_peer = inputPeer;
                if (req.to_peer instanceof TLRPC.TL_inputPeerChannel) {
                    req.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer, false);
                }
                if (msgObj.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(msgObj.messageOwner.to_id.channel_id);
                    req.from_peer = new TLRPC.TL_inputPeerChannel();
                    req.from_peer.channel_id = msgObj.messageOwner.to_id.channel_id;
                    if (chat != null) {
                        req.from_peer.access_hash = chat.access_hash;
                    }
                } else {
                    req.from_peer = new TLRPC.TL_inputPeerEmpty();
                }
                req.random_id = randomIds;
                req.id = ids;
                req.with_my_score = messages.size() == 1 && messages.get(0).messageOwner.with_my_score;

                final ArrayList<TLRPC.Message> newMsgObjArr = arr;
                final ArrayList<MessageObject> newMsgArr = objArr;
                final HashMap<Long, TLRPC.Message> messagesByRandomIdsFinal = messagesByRandomIds;
                final boolean isMegagroupFinal = isMegagroup;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, final TLRPC.TL_error error) {
                        if (error == null) {
                            HashMap<Integer, Long> newMessagesByIds = new HashMap<>();
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            for (int a = 0; a < updates.updates.size(); a++) {
                                TLRPC.Update update = updates.updates.get(a);
                                if (update instanceof TLRPC.TL_updateMessageID) {
                                    TLRPC.TL_updateMessageID updateMessageID = (TLRPC.TL_updateMessageID) update;
                                    newMessagesByIds.put(updateMessageID.id, updateMessageID.random_id);
                                    updates.updates.remove(a);
                                    a--;
                                }
                            }
                            Integer value = MessagesController.getInstance().dialogs_read_outbox_max.get(peer);
                            if (value == null) {
                                value = MessagesStorage.getInstance().getDialogReadMax(true, peer);
                                MessagesController.getInstance().dialogs_read_outbox_max.put(peer, value);
                            }

                            for (int a = 0; a < updates.updates.size(); a++) {
                                TLRPC.Update update = updates.updates.get(a);
                                if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateNewChannelMessage) {
                                    final TLRPC.Message message;
                                    if (update instanceof TLRPC.TL_updateNewMessage) {
                                        message = ((TLRPC.TL_updateNewMessage) update).message;
                                        MessagesController.getInstance().processNewDifferenceParams(-1, update.pts, -1, update.pts_count);
                                    } else {
                                        message = ((TLRPC.TL_updateNewChannelMessage) update).message;
                                        MessagesController.getInstance().processNewChannelDifferenceParams(update.pts, update.pts_count, message.to_id.channel_id);
                                        if (isMegagroupFinal) {
                                            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                    message.unread = value < message.id;

                                    Long random_id = newMessagesByIds.get(message.id);
                                    if (random_id != null) {
                                        final TLRPC.Message newMsgObj = messagesByRandomIdsFinal.get(random_id);
                                        if (newMsgObj == null) {
                                            continue;
                                        }
                                        int index = newMsgObjArr.indexOf(newMsgObj);
                                        if (index == -1) {
                                            continue;
                                        }
                                        MessageObject msgObj = newMsgArr.get(index);
                                        newMsgObjArr.remove(index);
                                        newMsgArr.remove(index);
                                        final int oldId = newMsgObj.id;
                                        final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                                        sentMessages.add(message);
                                        newMsgObj.id = message.id;
                                        updateMediaPaths(msgObj, message, null, true);
                                        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.random_id, oldId, newMsgObj.id, 0, false, to_id.channel_id);
                                                MessagesStorage.getInstance().putMessages(sentMessages, true, false, false, 0);
                                                AndroidUtilities.runOnUIThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                        SearchQuery.increasePeerRaiting(peer);
                                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, message.id, message, peer);
                                                        processSentMessage(oldId);
                                                        removeFromSendingMessages(oldId);
                                                    }
                                                });
                                                if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                                                    stopVideoService(newMsgObj.attachPath);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (error.text.equals("PEER_FLOOD")) {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 0);
                                    }
                                }
                            });
                        }
                        for (int a = 0; a < newMsgObjArr.size(); a++) {
                            final TLRPC.Message newMsgObj = newMsgObjArr.get(a);
                            MessagesStorage.getInstance().markMessageAsSendError(newMsgObj);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                                    processSentMessage(newMsgObj.id);
                                    if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                                        stopVideoService(newMsgObj.attachPath);
                                    }
                                    removeFromSendingMessages(newMsgObj.id);
                                }
                            });
                        }
                    }
                }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);

                if (a != messages.size() - 1) {
                    objArr = new ArrayList<>();
                    arr = new ArrayList<>();
                    randomIds = new ArrayList<>();
                    ids = new ArrayList<>();
                    messagesByRandomIds = new HashMap<>();
                }
            }
        }
    }

    public int editMessage(MessageObject messageObject, String message, boolean searchLinks, final BaseFragment fragment, ArrayList<TLRPC.MessageEntity> entities, final Runnable callback) {
        if (fragment == null || fragment.getParentActivity() == null || callback == null) {
            return 0;
        }

        TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
        req.peer = MessagesController.getInputPeer((int) messageObject.getDialogId());
        req.message = message;
        req.flags |= 2048;
        req.id = messageObject.getId();
        req.no_webpage = !searchLinks;
        if (entities != null) {
            req.entities = entities;
            req.flags |= 8;
        }
        return ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.run();
                    }
                });
                if (error == null) {
                    MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                } else {
                    if (!error.text.equals("MESSAGE_NOT_MODIFIED")) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setMessage(LocaleController.getString("EditMessageError", R.string.EditMessageError));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                fragment.showDialog(builder.create());
                            }
                        });
                    }
                }
            }
        });
    }

    private void sendLocation(Location location) {
        TLRPC.TL_messageMediaGeo mediaGeo = new TLRPC.TL_messageMediaGeo();
        mediaGeo.geo = new TLRPC.TL_geoPoint();
        mediaGeo.geo.lat = location.getLatitude();
        mediaGeo.geo._long = location.getLongitude();
        for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
            MessageObject messageObject = entry.getValue();
            SendMessagesHelper.getInstance().sendMessage(mediaGeo, messageObject.getDialogId(), messageObject, null, null);
        }
    }

    public void sendCurrentLocation(final MessageObject messageObject, final TLRPC.KeyboardButton button) {
        final String key = messageObject.getId() + "_" + Utilities.bytesToHex(button.data);
        waitingForLocation.put(key, messageObject);
        locationProvider.start();
    }

    public boolean isSendingCurrentLocation(MessageObject messageObject, TLRPC.KeyboardButton button) {
        return !(messageObject == null || button == null) && waitingForLocation.containsKey(messageObject.getId() + "_" + Utilities.bytesToHex(button.data));
    }

    public void sendCallback(final MessageObject messageObject, final TLRPC.KeyboardButton button, final ChatActivity parentFragment) {
        if (messageObject == null || button == null || parentFragment == null) {
            return;
        }
        final String key = messageObject.getId() + "_" + Utilities.bytesToHex(button.data);
        waitingForCallback.put(key, messageObject);
        TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
        req.peer = MessagesController.getInputPeer((int) messageObject.getDialogId());
        req.msg_id = messageObject.getId();
        req.game = button instanceof TLRPC.TL_keyboardButtonGame;
        if (button.data != null) {
            req.flags |= 1;
            req.data = button.data;
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        waitingForCallback.remove(key);
                        if (response != null) {
                            TLRPC.TL_messages_botCallbackAnswer res = (TLRPC.TL_messages_botCallbackAnswer) response;
                            if (res.message != null) {
                                if (res.alert) {
                                    if (parentFragment.getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    builder.setMessage(res.message);
                                    parentFragment.showDialog(builder.create());
                                } else {
                                    int uid = messageObject.messageOwner.from_id;
                                    if (messageObject.messageOwner.via_bot_id != 0) {
                                        uid = messageObject.messageOwner.via_bot_id;
                                    }
                                    String name = null;
                                    if (uid > 0) {
                                        TLRPC.User user = MessagesController.getInstance().getUser(uid);
                                        if (user != null) {
                                            name = ContactsController.formatName(user.first_name, user.last_name);
                                        }
                                    } else {
                                        TLRPC.Chat chat = MessagesController.getInstance().getChat(-uid);
                                        if (chat != null) {
                                            name = chat.title;
                                        }
                                    }
                                    if (name == null) {
                                        name = "bot";
                                    }
                                    parentFragment.showAlert(name, res.message);
                                }
                            } else if (res.url != null) {
                                if (parentFragment.getParentActivity() == null) {
                                    return;
                                }
                                int uid = messageObject.messageOwner.from_id;
                                if (messageObject.messageOwner.via_bot_id != 0) {
                                    uid = messageObject.messageOwner.via_bot_id;
                                }
                                TLRPC.User user = MessagesController.getInstance().getUser(uid);
                                boolean verified = user != null && user.verified;
                                if (button instanceof TLRPC.TL_keyboardButtonGame) {
                                    TLRPC.TL_game game = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame ? messageObject.messageOwner.media.game : null;
                                    if (game == null) {
                                        return;
                                    }
                                    parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("askgame_" + uid, true), uid);
                                } else {
                                    parentFragment.showOpenUrlAlert(res.url, false);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public boolean isSendingCallback(MessageObject messageObject, TLRPC.KeyboardButton button) {
        return !(messageObject == null || button == null) && waitingForCallback.containsKey(messageObject.getId() + "_" + Utilities.bytesToHex(button.data));
    }

    public void sendGame(TLRPC.InputPeer peer, TLRPC.TL_inputMediaGame game, long random_id, final long taskId) {
        if (peer == null || game == null) {
            return;
        }
        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
        request.peer = peer;
        if (request.peer instanceof TLRPC.TL_inputPeerChannel) {
            request.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer.channel_id, false);
        }
        request.random_id = random_id != 0 ? random_id : getNextRandomId();
        request.media = game;
        final long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(peer.getObjectSize() + game.getObjectSize() + 4 + 8);
                data.writeInt32(3);
                data.writeInt64(random_id);
                peer.serializeToStream(data);
                game.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            newTaskId = MessagesStorage.getInstance().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                if (error == null) {
                    MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                }
                if (newTaskId != 0) {
                    MessagesStorage.getInstance().removePendingTask(newTaskId);
                }
            }
        });
    }

    public void sendMessage(MessageObject retryMessageObject) {
        sendMessage(null, null, null, null, null, null, null, retryMessageObject.getDialogId(), retryMessageObject.messageOwner.attachPath, null, null, true, retryMessageObject, null, retryMessageObject.messageOwner.reply_markup, retryMessageObject.messageOwner.params);
    }

    public void sendMessage(TLRPC.User user, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, null, null, user, null, null, peer, null, reply_to_msg, null, true, null, null, replyMarkup, params);
    }

    public void sendMessage(TLRPC.TL_document document, VideoEditedInfo videoEditedInfo, String path, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, null, videoEditedInfo, null, document, null, peer, path, reply_to_msg, null, true, null, null, replyMarkup, params);
    }

    public void sendMessage(String message, long peer, MessageObject reply_to_msg, TLRPC.WebPage webPage, boolean searchLinks, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(message, null, null, null, null, null, null, peer, null, reply_to_msg, webPage, searchLinks, null, entities, replyMarkup, params);
    }

    public void sendMessage(TLRPC.MessageMedia location, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, location, null, null, null, null, null, peer, null, reply_to_msg, null, true, null, null, replyMarkup, params);
    }

    public void sendMessage(TLRPC.TL_game game, long peer, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, null, null, null, null, game, peer, null, null, null, true, null, null, replyMarkup, params);
    }

    public void sendMessage(TLRPC.TL_photo photo, String path, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, photo, null, null, null, null, peer, path, reply_to_msg, null, true, null, null, replyMarkup, params);
    }

    private void sendMessage(String message, TLRPC.MessageMedia location, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_game game, long peer, String path, MessageObject reply_to_msg, TLRPC.WebPage webPage, boolean searchLinks, MessageObject retryMessageObject, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        if (peer == 0) {
            return;
        }

        String originalPath = null;
        if (params != null && params.containsKey("originalPath")) {
            originalPath = params.get("originalPath");
        }

        TLRPC.Message newMsg = null;
        MessageObject newMsgObj = null;
        int type = -1;
        int lower_id = (int) peer;
        int high_id = (int) (peer >> 32);
        boolean isChannel = false;
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = lower_id != 0 ? MessagesController.getInputPeer(lower_id) : null;
        ArrayList<TLRPC.InputUser> sendToPeers = null;
        if (lower_id == 0) {
            encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
            if (encryptedChat == null) {
                if (retryMessageObject != null) {
                    MessagesStorage.getInstance().markMessageAsSendError(retryMessageObject.messageOwner);
                    retryMessageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, retryMessageObject.getId());
                    processSentMessage(retryMessageObject.getId());
                }
                return;
            }
        } else if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(sendToPeer.channel_id);
            isChannel = chat != null && !chat.megagroup;
        }

        try {
            if (retryMessageObject != null) {
                newMsg = retryMessageObject.messageOwner;
                if (retryMessageObject.isForwarded()) {
                    type = 4;
                } else {
                    if (retryMessageObject.type == 0) {
                        if (retryMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            //game = retryMessageObject.messageOwner.media.game;
                        } else {
                            message = newMsg.message;
                        }
                        type = 0;
                    } else if (retryMessageObject.type == 4) {
                        location = newMsg.media;
                        type = 1;
                    } else if (retryMessageObject.type == 1) {
                        photo = (TLRPC.TL_photo) newMsg.media.photo;
                        type = 2;
                    } else if (retryMessageObject.type == 3 || videoEditedInfo != null) {
                        type = 3;
                        document = (TLRPC.TL_document) newMsg.media.document;
                    } else if (retryMessageObject.type == 12) {
                        user = new TLRPC.TL_userRequest_old2();
                        user.phone = newMsg.media.phone_number;
                        user.first_name = newMsg.media.first_name;
                        user.last_name = newMsg.media.last_name;
                        user.id = newMsg.media.user_id;
                        type = 6;
                    } else if (retryMessageObject.type == 8 || retryMessageObject.type == 9 || retryMessageObject.type == 13 || retryMessageObject.type == 14) {
                        document = (TLRPC.TL_document) newMsg.media.document;
                        type = 7;
                    } else if (retryMessageObject.type == 2) {
                        document = (TLRPC.TL_document) newMsg.media.document;
                        type = 8;
                    }
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    }
                }
            } else {
                if (message != null) {
                    if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    if (entities != null && !entities.isEmpty()) {
                        newMsg.entities = entities;
                    }
                    if (encryptedChat != null && webPage instanceof TLRPC.TL_webPagePending) {
                        if (webPage.url != null) {
                            TLRPC.WebPage newWebPage = new TLRPC.TL_webPageUrlPending();
                            newWebPage.url = webPage.url;
                            webPage = newWebPage;
                        } else {
                            webPage = null;
                        }
                    }
                    if (webPage == null) {
                        newMsg.media = new TLRPC.TL_messageMediaEmpty();
                    } else {
                        newMsg.media = new TLRPC.TL_messageMediaWebPage();
                        newMsg.media.webpage = webPage;
                    }
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 0;
                    }
                    newMsg.message = message;
                } else if (location != null) {
                    if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = location;
                    newMsg.message = "";
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 1;
                    }
                } else if (photo != null) {
                    if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = new TLRPC.TL_messageMediaPhoto();
                    newMsg.media.caption = photo.caption != null ? photo.caption : "";
                    newMsg.media.photo = photo;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 2;
                    }
                    newMsg.message = "-1";
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        newMsg.attachPath = path;
                    } else {
                        TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                        newMsg.attachPath = FileLoader.getPathToAttach(location1, true).toString();
                    }
                } else if (game != null) {
                    newMsg = new TLRPC.TL_message();
                    newMsg.media = new TLRPC.TL_messageMediaGame();
                    newMsg.media.caption = "";
                    newMsg.media.game = game;
                    newMsg.message = "";
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
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
                    if (newMsg.media.first_name == null) {
                        user.first_name = newMsg.media.first_name = "";
                    }
                    if (newMsg.media.last_name == null) {
                        user.last_name = newMsg.media.last_name = "";
                    }
                    newMsg.message = "";
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 6;
                    }
                } else if (document != null) {
                    if (encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.caption = document.caption != null ? document.caption : "";
                    newMsg.media.document = document;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else if (MessageObject.isVoiceDocument(document)) {
                        type = 8;
                    } else {
                        type = 7;
                    }
                    if (videoEditedInfo == null) {
                        newMsg.message = "-1";
                    } else {
                        newMsg.message = videoEditedInfo.getString();
                    }
                    if (encryptedChat != null && document.dc_id > 0 && !MessageObject.isStickerDocument(document)) {
                        newMsg.attachPath = FileLoader.getPathToAttach(document).toString();
                    } else {
                        newMsg.attachPath = path;
                    }
                    if (encryptedChat != null && MessageObject.isStickerDocument(document)) {
                        for (int a = 0; a < document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                                if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 46) {
                                    document.attributes.remove(a);
                                    document.attributes.add(new TLRPC.TL_documentAttributeSticker_old());
                                } else {
                                    document.attributes.remove(a);
                                    TLRPC.TL_documentAttributeSticker_layer55 attributeSticker = new TLRPC.TL_documentAttributeSticker_layer55();
                                    document.attributes.add(attributeSticker);
                                    attributeSticker.alt = attribute.alt;
                                    if (attribute.stickerset != null) {
                                        String name = StickersQuery.getStickerSetName(attribute.stickerset.id);
                                        if (name != null && name.length() > 0) {
                                            attributeSticker.stickerset = new TLRPC.TL_inputStickerSetShortName();
                                            attributeSticker.stickerset.short_name = name;
                                        } else {
                                            attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                        }
                                    } else {
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                if (newMsg.attachPath == null) {
                    newMsg.attachPath = "";
                }
                newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
                newMsg.out = true;
                if (isChannel && sendToPeer != null) {
                    newMsg.from_id = -sendToPeer.channel_id;
                } else {
                    newMsg.from_id = UserConfig.getClientUserId();
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                }
                UserConfig.saveConfig(false);
            }
            if (newMsg.random_id == 0) {
                newMsg.random_id = getNextRandomId();
            }
            if (params != null && params.containsKey("bot")) {
                if (encryptedChat != null) {
                    newMsg.via_bot_name = params.get("bot_name");
                    if (newMsg.via_bot_name == null) {
                        newMsg.via_bot_name = "";
                    }
                } else {
                    newMsg.via_bot_id = Utilities.parseInt(params.get("bot"));
                }
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
            }
            newMsg.params = params;
            if (retryMessageObject == null || !retryMessageObject.resendAsIs) {
                newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
                if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
                    if (isChannel) {
                        newMsg.views = 1;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                    }
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(sendToPeer.channel_id);
                    if (chat != null) {
                        if (chat.megagroup) {
                            newMsg.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            newMsg.unread = true;
                        } else {
                            newMsg.post = true;
                            if (chat.signatures) {
                                newMsg.from_id = UserConfig.getClientUserId();
                            }
                        }
                    }
                } else {
                    newMsg.unread = true;
                }
            }
            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
            newMsg.dialog_id = peer;
            if (reply_to_msg != null) {
                if (encryptedChat != null && reply_to_msg.messageOwner.random_id != 0) {
                    newMsg.reply_to_random_id = reply_to_msg.messageOwner.random_id;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                } else {
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                }
                newMsg.reply_to_msg_id = reply_to_msg.getId();
            }
            if (replyMarkup != null && encryptedChat == null) {
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MARKUP;
                newMsg.reply_markup = replyMarkup;
            }
            if (lower_id != 0) {
                if (high_id == 1) {
                    if (currentChatInfo == null) {
                        MessagesStorage.getInstance().markMessageAsSendError(newMsg);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsg.id);
                        processSentMessage(newMsg.id);
                        return;
                    }
                    sendToPeers = new ArrayList<>();
                    for (TLRPC.ChatParticipant participant : currentChatInfo.participants.participants) {
                        TLRPC.User sendToUser = MessagesController.getInstance().getUser(participant.user_id);
                        TLRPC.InputUser peerUser = MessagesController.getInputUser(sendToUser);
                        if (peerUser != null) {
                            sendToPeers.add(peerUser);
                        }
                    }
                    newMsg.to_id = new TLRPC.TL_peerChat();
                    newMsg.to_id.chat_id = lower_id;
                } else {
                    newMsg.to_id = MessagesController.getPeer(lower_id);
                    if (lower_id > 0) {
                        TLRPC.User sendToUser = MessagesController.getInstance().getUser(lower_id);
                        if (sendToUser == null) {
                            processSentMessage(newMsg.id);
                            return;
                        }
                        if (sendToUser.bot) {
                            newMsg.unread = false;
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
                    if (MessageObject.isVoiceMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(encryptedChat.ttl, duration + 1);
                    } else if (MessageObject.isVideoMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(encryptedChat.ttl, duration + 1);
                    }
                }
            }
            if ((encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) && high_id != 1 && MessageObject.isVoiceMessage(newMsg) && newMsg.to_id.channel_id == 0) {
                newMsg.media_unread = true;
            }

            newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj = new MessageObject(newMsg, null, true);
            newMsgObj.replyMessageObject = reply_to_msg;
            if (!newMsgObj.isForwarded() && (newMsgObj.type == 3 || videoEditedInfo != null) && !TextUtils.isEmpty(newMsg.attachPath)) {
                newMsgObj.attachPathExists = true;
            }

            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(newMsg);
            MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
            MessagesController.getInstance().updateInterfaceWithMessages(peer, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

            if (BuildVars.DEBUG_VERSION) {
                if (sendToPeer != null) {
                    FileLog.e("tmessages", "send message user_id = " + sendToPeer.user_id + " chat_id = " + sendToPeer.chat_id + " channel_id = " + sendToPeer.channel_id + " access_hash = " + sendToPeer.access_hash);
                }
            }

            if (type == 0 || type == 9 && message != null && encryptedChat != null) {
                if (encryptedChat == null) {
                    if (sendToPeers != null) {
                        TLRPC.TL_messages_sendBroadcast reqSend = new TLRPC.TL_messages_sendBroadcast();
                        ArrayList<Long> random_ids = new ArrayList<>();
                        for (int a = 0; a < sendToPeers.size(); a++) {
                            random_ids.add(Utilities.random.nextLong());
                        }
                        reqSend.message = message;
                        reqSend.contacts = sendToPeers;
                        reqSend.media = new TLRPC.TL_inputMediaEmpty();
                        reqSend.random_id = random_ids;
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    } else {
                        TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                        reqSend.message = message;
                        reqSend.clear_draft = retryMessageObject == null;
                        if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                            reqSend.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer, false);
                        }
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
                        if (reply_to_msg != null) {
                            reqSend.flags |= 1;
                            reqSend.reply_to_msg_id = reply_to_msg.getId();
                        }
                        if (!searchLinks) {
                            reqSend.no_webpage = true;
                        }
                        if (entities != null && !entities.isEmpty()) {
                            reqSend.entities = entities;
                            reqSend.flags |= 8;
                        }
                        performSendMessageRequest(reqSend, newMsgObj, null);
                        if (retryMessageObject == null) {
                            DraftQuery.cleanDraft(peer, false);
                        }
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                        reqSend.ttl = newMsg.ttl;
                        if (entities != null && !entities.isEmpty()) {
                            reqSend.entities = entities;
                            reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                        }
                        if (reply_to_msg != null && reply_to_msg.messageOwner.random_id != 0) {
                            reqSend.reply_to_random_id = reply_to_msg.messageOwner.random_id;
                            reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                        }
                    } else if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        reqSend = new TLRPC.TL_decryptedMessage_layer17();
                        reqSend.ttl = newMsg.ttl;
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_layer8();
                        reqSend.random_bytes = new byte[15];
                        Utilities.random.nextBytes(reqSend.random_bytes);
                    }
                    if (params != null && params.get("bot_name") != null) {
                        reqSend.via_bot_name = params.get("bot_name");
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                    }
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = message;
                    if (webPage != null && webPage.url != null) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaWebPage();
                        reqSend.media.url = webPage.url;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    } else {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
                    }
                    SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    if (retryMessageObject == null) {
                        DraftQuery.cleanDraft(peer, false);
                    }
                }
            } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 9 && encryptedChat != null) {
                if (encryptedChat == null) {
                    TLRPC.InputMedia inputMedia = null;
                    DelayedMessage delayedMessage = null;
                    if (type == 1) {
                        if (location instanceof TLRPC.TL_messageMediaVenue) {
                            inputMedia = new TLRPC.TL_inputMediaVenue();
                            inputMedia.address = location.address;
                            inputMedia.title = location.title;
                            inputMedia.provider = location.provider;
                            inputMedia.venue_id = location.venue_id;
                        } else {
                            inputMedia = new TLRPC.TL_inputMediaGeoPoint();
                        }
                        inputMedia.geo_point = new TLRPC.TL_inputGeoPoint();
                        inputMedia.geo_point.lat = location.geo.lat;
                        inputMedia.geo_point._long = location.geo._long;
                    } else if (type == 2 || type == 9 && photo != null) {
                        if (photo.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedPhoto();
                            inputMedia.caption = photo.caption != null ? photo.caption : "";
                            if (params != null) {
                                String masks = params.get("masks");
                                if (masks != null) {
                                    SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                                    int count = serializedData.readInt32(false);
                                    for (int a = 0; a < count; a++) {
                                        inputMedia.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                                    }
                                    inputMedia.flags |= 1;
                                }
                            }
                            delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 0;
                            delayedMessage.obj = newMsgObj;
                            if (path != null && path.length() > 0 && path.startsWith("http")) {
                                delayedMessage.httpLocation = path;
                            } else {
                                delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                            }
                        } else {
                            TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                            media.id = new TLRPC.TL_inputPhoto();
                            media.caption = photo.caption != null ? photo.caption : "";
                            media.id.id = photo.id;
                            media.id.access_hash = photo.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 3) {
                        if (document.access_hash == 0) {
                            if (document.thumb.location != null) {
                                inputMedia = new TLRPC.TL_inputMediaUploadedThumbDocument();
                            } else {
                                inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            }
                            inputMedia.caption = document.caption != null ? document.caption : "";
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                            delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 1;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.location = document.thumb.location;
                            delayedMessage.documentLocation = document;
                            delayedMessage.videoEditedInfo = videoEditedInfo;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.caption = document.caption != null ? document.caption : "";
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 6) {
                        inputMedia = new TLRPC.TL_inputMediaContact();
                        inputMedia.phone_number = user.phone;
                        inputMedia.first_name = user.first_name;
                        inputMedia.last_name = user.last_name;
                    } else if (type == 7 || type == 9) {
                        if (document.access_hash == 0) {
                            if (encryptedChat == null && originalPath != null && originalPath.length() > 0 && originalPath.startsWith("http") && params != null) {
                                inputMedia = new TLRPC.TL_inputMediaGifExternal();
                                String args[] = params.get("url").split("\\|");
                                if (args.length == 2) {
                                    ((TLRPC.TL_inputMediaGifExternal) inputMedia).url = args[0];
                                    inputMedia.q = args[1];
                                }
                            } else {
                                if (document.thumb.location != null && document.thumb.location instanceof TLRPC.TL_fileLocation) {
                                    inputMedia = new TLRPC.TL_inputMediaUploadedThumbDocument();
                                } else {
                                    inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                                }
                                delayedMessage = new DelayedMessage();
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.type = 2;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.documentLocation = document;
                                delayedMessage.location = document.thumb.location;
                            }
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                            inputMedia.caption = document.caption != null ? document.caption : "";
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            media.caption = document.caption != null ? document.caption : "";
                            inputMedia = media;
                        }
                    } else if (type == 8) {
                        if (document.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                            inputMedia.caption = document.caption != null ? document.caption : "";
                            delayedMessage = new DelayedMessage();
                            delayedMessage.type = 3;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.documentLocation = document;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.caption = document.caption != null ? document.caption : "";
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            inputMedia = media;
                        }
                    }

                    TLObject reqSend;

                    if (sendToPeers != null) {
                        TLRPC.TL_messages_sendBroadcast request = new TLRPC.TL_messages_sendBroadcast();
                        ArrayList<Long> random_ids = new ArrayList<>();
                        for (int a = 0; a < sendToPeers.size(); a++) {
                            random_ids.add(Utilities.random.nextLong());
                        }
                        request.contacts = sendToPeers;
                        request.media = inputMedia;
                        request.random_id = random_ids;
                        request.message = "";
                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                        if (retryMessageObject == null) {
                            DraftQuery.cleanDraft(peer, false);
                        }
                    } else {
                        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
                        request.peer = sendToPeer;
                        if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                            request.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer, false);
                        }
                        request.random_id = newMsg.random_id;
                        request.media = inputMedia;
                        if (reply_to_msg != null) {
                            request.flags |= 1;
                            request.reply_to_msg_id = reply_to_msg.getId();
                        }
                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                    }
                    if (type == 1) {
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    } else if (type == 2) {
                        if (photo.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, null);
                        }
                    } else if (type == 3) {
                        if (document.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, null);
                        }
                    } else if (type == 6) {
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    } else if (type == 7) {
                        if (document.access_hash == 0 && delayedMessage != null) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, originalPath);
                        }
                    } else if (type == 8) {
                        if (document.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, null);
                        }
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                        reqSend.ttl = newMsg.ttl;
                        if (entities != null && !entities.isEmpty()) {
                            reqSend.entities = entities;
                            reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                        }
                        if (reply_to_msg != null && reply_to_msg.messageOwner.random_id != 0) {
                            reqSend.reply_to_random_id = reply_to_msg.messageOwner.random_id;
                            reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                        }
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    } else if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                        reqSend = new TLRPC.TL_decryptedMessage_layer17();
                        reqSend.ttl = newMsg.ttl;
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_layer8();
                        reqSend.random_bytes = new byte[15];
                        Utilities.random.nextBytes(reqSend.random_bytes);
                    }
                    if (params != null && params.get("bot_name") != null) {
                        reqSend.via_bot_name = params.get("bot_name");
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                    }
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = "";
                    if (type == 1) {
                        if (location instanceof TLRPC.TL_messageMediaVenue && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVenue();
                            reqSend.media.address = location.address;
                            reqSend.media.title = location.title;
                            reqSend.media.provider = location.provider;
                            reqSend.media.venue_id = location.venue_id;
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
                        }
                        reqSend.media.lat = location.geo.lat;
                        reqSend.media._long = location.geo._long;
                        SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    } else if (type == 2 || type == 9 && photo != null) {
                        TLRPC.PhotoSize small = photo.sizes.get(0);
                        TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
                        ImageLoader.fillPhotoSizeWithBytes(small);
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
                            reqSend.media.caption = photo.caption != null ? photo.caption : "";
                            if (small.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = small.bytes;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = new byte[0];
                            }
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto_layer8();
                            if (small.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaPhoto_layer8) reqSend.media).thumb = small.bytes;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaPhoto_layer8) reqSend.media).thumb = new byte[0];
                            }
                        }
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
                            if (path != null && path.length() > 0 && path.startsWith("http")) {
                                delayedMessage.httpLocation = path;
                            } else {
                                delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                            }
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = big.location.volume_id;
                            encryptedFile.access_hash = big.location.secret;
                            reqSend.media.key = big.location.key;
                            reqSend.media.iv = big.location.iv;
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 3) {
                        ImageLoader.fillPhotoSizeWithBytes(document.thumb);
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                            if (MessageObject.isNewGifDocument(document)) {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                                reqSend.media.attributes = document.attributes;
                                if (document.thumb != null && document.thumb.bytes != null) {
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = document.thumb.bytes;
                                } else {
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                                }
                            } else {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo();
                                if (document.thumb != null && document.thumb.bytes != null) {
                                    ((TLRPC.TL_decryptedMessageMediaVideo) reqSend.media).thumb = document.thumb.bytes;
                                } else {
                                    ((TLRPC.TL_decryptedMessageMediaVideo) reqSend.media).thumb = new byte[0];
                                }
                            }
                        } else if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo_layer17();
                            if (document.thumb != null && document.thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaVideo_layer17) reqSend.media).thumb = document.thumb.bytes;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaVideo_layer17) reqSend.media).thumb = new byte[0];
                            }
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo_layer8();
                            if (document.thumb != null && document.thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaVideo_layer8) reqSend.media).thumb = document.thumb.bytes;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaVideo_layer8) reqSend.media).thumb = new byte[0];
                            }
                        }
                        reqSend.media.caption = document.caption != null ? document.caption : "";
                        reqSend.media.mime_type = "video/mp4";
                        reqSend.media.size = document.size;
                        for (int a = 0; a < document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                reqSend.media.w = attribute.w;
                                reqSend.media.h = attribute.h;
                                reqSend.media.duration = attribute.duration;
                                break;
                            }
                        }
                        reqSend.media.thumb_h = document.thumb.h;
                        reqSend.media.thumb_w = document.thumb.w;
                        if (document.access_hash == 0) {
                            DelayedMessage delayedMessage = new DelayedMessage();
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.sendEncryptedRequest = reqSend;
                            delayedMessage.type = 1;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.encryptedChat = encryptedChat;
                            delayedMessage.documentLocation = document;
                            delayedMessage.videoEditedInfo = videoEditedInfo;
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = document.id;
                            encryptedFile.access_hash = document.access_hash;
                            reqSend.media.key = document.key;
                            reqSend.media.iv = document.iv;
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 6) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                        reqSend.media.phone_number = user.phone;
                        reqSend.media.first_name = user.first_name;
                        reqSend.media.last_name = user.last_name;
                        reqSend.media.user_id = user.id;
                        SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    } else if (type == 7 || type == 9 && document != null) {
                        if (MessageObject.isStickerDocument(document)) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaExternalDocument();
                            reqSend.media.id = document.id;
                            reqSend.media.date = document.date;
                            reqSend.media.access_hash = document.access_hash;
                            reqSend.media.mime_type = document.mime_type;
                            reqSend.media.size = document.size;
                            reqSend.media.dc_id = document.dc_id;
                            reqSend.media.attributes = document.attributes;
                            if (document.thumb == null) {
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb = new TLRPC.TL_photoSizeEmpty();
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb.type = "s";
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb = document.thumb;
                            }
                            SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                        } else {
                            ImageLoader.fillPhotoSizeWithBytes(document.thumb);
                            if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                                reqSend.media.attributes = document.attributes;
                                reqSend.media.caption = document.caption != null ? document.caption : "";
                                if (document.thumb != null && document.thumb.bytes != null) {
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = document.thumb.bytes;
                                    reqSend.media.thumb_h = document.thumb.h;
                                    reqSend.media.thumb_w = document.thumb.w;
                                } else {
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                                    reqSend.media.thumb_h = 0;
                                    reqSend.media.thumb_w = 0;
                                }
                            } else {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument_layer8();
                                reqSend.media.file_name = FileLoader.getDocumentFileName(document);
                                if (document.thumb != null && document.thumb.bytes != null) {
                                    ((TLRPC.TL_decryptedMessageMediaDocument_layer8) reqSend.media).thumb = document.thumb.bytes;
                                    reqSend.media.thumb_h = document.thumb.h;
                                    reqSend.media.thumb_w = document.thumb.w;
                                } else {
                                    ((TLRPC.TL_decryptedMessageMediaDocument_layer8) reqSend.media).thumb = new byte[0];
                                    reqSend.media.thumb_h = 0;
                                    reqSend.media.thumb_w = 0;
                                }
                            }
                            reqSend.media.size = document.size;
                            reqSend.media.mime_type = document.mime_type;

                            if (document.key == null) {
                                DelayedMessage delayedMessage = new DelayedMessage();
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.type = 2;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.encryptedChat = encryptedChat;
                                if (path != null && path.length() > 0 && path.startsWith("http")) {
                                    delayedMessage.httpLocation = path;
                                }
                                delayedMessage.documentLocation = document;
                                performSendDelayedMessage(delayedMessage);
                            } else {
                                TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                                encryptedFile.id = document.id;
                                encryptedFile.access_hash = document.access_hash;
                                reqSend.media.key = document.key;
                                reqSend.media.iv = document.iv;
                                SecretChatHelper.getInstance().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                            }
                        }
                    } else if (type == 8) {
                        DelayedMessage delayedMessage = new DelayedMessage();
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.documentLocation = document;
                        delayedMessage.type = 3;

                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                            reqSend.media.attributes = document.attributes;
                            reqSend.media.caption = document.caption != null ? document.caption : "";
                            if (document.thumb != null && document.thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = document.thumb.bytes;
                                reqSend.media.thumb_h = document.thumb.h;
                                reqSend.media.thumb_w = document.thumb.w;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                                reqSend.media.thumb_h = 0;
                                reqSend.media.thumb_w = 0;
                            }
                            reqSend.media.mime_type = document.mime_type;
                            reqSend.media.size = document.size;
                            delayedMessage.originalPath = originalPath;
                        } else {
                            if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio();
                            } else {
                                reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio_layer8();
                            }
                            for (int a = 0; a < document.attributes.size(); a++) {
                                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                    reqSend.media.duration = attribute.duration;
                                    break;
                                }
                            }
                            reqSend.media.mime_type = "audio/ogg";
                            reqSend.media.size = document.size;
                            delayedMessage.type = 3;
                        }
                        performSendDelayedMessage(delayedMessage);
                    }
                    if (retryMessageObject == null) {
                        DraftQuery.cleanDraft(peer, false);
                    }
                }
            } else if (type == 4) {
                TLRPC.TL_messages_forwardMessages reqSend = new TLRPC.TL_messages_forwardMessages();
                reqSend.to_peer = sendToPeer;
                reqSend.with_my_score = retryMessageObject.messageOwner.with_my_score;
                if (retryMessageObject.messageOwner.ttl != 0) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(-retryMessageObject.messageOwner.ttl);
                    reqSend.from_peer = new TLRPC.TL_inputPeerChannel();
                    reqSend.from_peer.channel_id = -retryMessageObject.messageOwner.ttl;
                    if (chat != null) {
                        reqSend.from_peer.access_hash = chat.access_hash;
                    }
                } else {
                    reqSend.from_peer = new TLRPC.TL_inputPeerEmpty();
                }
                if (retryMessageObject.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                    reqSend.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer, false);
                }
                reqSend.random_id.add(newMsg.random_id);
                if (retryMessageObject.getId() >= 0) {
                    reqSend.id.add(retryMessageObject.getId());
                } else {
                    reqSend.id.add(retryMessageObject.messageOwner.fwd_msg_id);
                }
                performSendMessageRequest(reqSend, newMsgObj, null);
            } else if (type == 9) {
                TLRPC.TL_messages_sendInlineBotResult reqSend = new TLRPC.TL_messages_sendInlineBotResult();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (reply_to_msg != null) {
                    reqSend.flags |= 1;
                    reqSend.reply_to_msg_id = reply_to_msg.getId();
                }
                if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                    reqSend.silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + peer, false);
                }
                reqSend.query_id = Utilities.parseLong(params.get("query_id"));
                reqSend.id = params.get("id");
                if (retryMessageObject == null) {
                    reqSend.clear_draft = true;
                    DraftQuery.cleanDraft(peer, false);
                }
                performSendMessageRequest(reqSend, newMsgObj, null);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            MessagesStorage.getInstance().markMessageAsSendError(newMsg);
            if (newMsgObj != null) {
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsg.id);
            processSentMessage(newMsg.id);
        }
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        if (message.type == 0) {
            if (message.httpLocation != null) {
                putToDelayedMessages(message.httpLocation, message);
                ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file");
            } else {
                if (message.sendRequest != null) {
                    String location = FileLoader.getPathToAttach(message.location).toString();
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, false, true);
                } else {
                    String location = FileLoader.getPathToAttach(message.location).toString();
                    if (message.sendEncryptedRequest != null && message.location.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(message.location), message);
                            FileLoader.getInstance().loadFile(message.location, "jpg", 0, false);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, true, true);
                }
            }
        } else if (message.type == 1) {
            if (message.videoEditedInfo != null) {
                String location = message.obj.messageOwner.attachPath;
                if (location == null) {
                    location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                }
                putToDelayedMessages(location, message);
                MediaController.getInstance().scheduleVideoConvert(message.obj);
            } else {
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        if (location == null) {
                            location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        if (message.obj.videoEditedInfo != null) {
                            FileLoader.getInstance().uploadFile(location, false, false, message.documentLocation.size);
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
                        location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                    }
                    putToDelayedMessages(location, message);
                    if (message.obj.videoEditedInfo != null) {
                        FileLoader.getInstance().uploadFile(location, true, false, message.documentLocation.size);
                    } else {
                        FileLoader.getInstance().uploadFile(location, true, false);
                    }
                }
            }
        } else if (message.type == 2) {
            if (message.httpLocation != null) {
                putToDelayedMessages(message.httpLocation, message);
                ImageLoader.getInstance().loadHttpFile(message.httpLocation, "gif");
            } else {
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else {
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
                    if (message.sendEncryptedRequest != null && message.documentLocation.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(message.documentLocation), message);
                            FileLoader.getInstance().loadFile(message.documentLocation, true, false);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance().uploadFile(location, true, false);
                }
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

    protected void putToSendingMessages(TLRPC.Message message) {
        sendingMessages.put(message.id, message);
    }

    protected void removeFromSendingMessages(int mid) {
        sendingMessages.remove(mid);
    }

    public boolean isSendingMessage(int mid) {
        return sendingMessages.containsKey(mid);
    }

    private void performSendMessageRequest(final TLObject req, final MessageObject msgObj, final String originalPath) {
        final TLRPC.Message newMsgObj = msgObj.messageOwner;
        putToSendingMessages(newMsgObj);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean isSentError = false;
                        if (error == null) {
                            final int oldId = newMsgObj.id;
                            final boolean isBroadcast = req instanceof TLRPC.TL_messages_sendBroadcast;
                            final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                            final String attachPath = newMsgObj.attachPath;
                            if (response instanceof TLRPC.TL_updateShortSentMessage) {
                                final TLRPC.TL_updateShortSentMessage res = (TLRPC.TL_updateShortSentMessage) response;
                                newMsgObj.local_id = newMsgObj.id = res.id;
                                newMsgObj.date = res.date;
                                newMsgObj.entities = res.entities;
                                newMsgObj.out = res.out;
                                if (res.media != null) {
                                    newMsgObj.media = res.media;
                                    newMsgObj.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                                }
                                if (res.media instanceof TLRPC.TL_messageMediaGame && !TextUtils.isEmpty(res.message)) {
                                    newMsgObj.message = res.message;
                                }
                                if (!newMsgObj.entities.isEmpty()) {
                                    newMsgObj.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                                }
                                Utilities.stageQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesController.getInstance().processNewDifferenceParams(-1, res.pts, res.date, res.pts_count);
                                    }
                                });
                                sentMessages.add(newMsgObj);
                            } else if (response instanceof TLRPC.Updates) {
                                ArrayList<TLRPC.Update> updates = ((TLRPC.Updates) response).updates;
                                TLRPC.Message message = null;
                                for (int a = 0; a < updates.size(); a++) {
                                    TLRPC.Update update = updates.get(a);
                                    if (update instanceof TLRPC.TL_updateNewMessage) {
                                        final TLRPC.TL_updateNewMessage newMessage = (TLRPC.TL_updateNewMessage) update;
                                        sentMessages.add(message = newMessage.message);
                                        newMsgObj.id = newMessage.message.id;
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                MessagesController.getInstance().processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count);
                                            }
                                        });
                                        break;
                                    } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                                        final TLRPC.TL_updateNewChannelMessage newMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                        sentMessages.add(message = newMessage.message);
                                        if ((newMsgObj.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0) {
                                            newMessage.message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                MessagesController.getInstance().processNewChannelDifferenceParams(newMessage.pts, newMessage.pts_count, newMessage.message.to_id.channel_id);
                                            }
                                        });
                                        break;
                                    }
                                }
                                if (message != null) {
                                    Integer value = MessagesController.getInstance().dialogs_read_outbox_max.get(message.dialog_id);
                                    if (value == null) {
                                        value = MessagesStorage.getInstance().getDialogReadMax(message.out, message.dialog_id);
                                        MessagesController.getInstance().dialogs_read_outbox_max.put(message.dialog_id, value);
                                    }
                                    message.unread = value < message.id;

                                    newMsgObj.id = message.id;
                                    updateMediaPaths(msgObj, message, originalPath, false);
                                } else {
                                    isSentError = true;
                                }
                            }

                            if (!isSentError) {
                                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, (isBroadcast ? oldId : newMsgObj.id), newMsgObj, newMsgObj.dialog_id); //TODO remove later?
                                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.random_id, oldId, (isBroadcast ? oldId : newMsgObj.id), 0, false, newMsgObj.to_id.channel_id);
                                        MessagesStorage.getInstance().putMessages(sentMessages, true, false, isBroadcast, 0);
                                        if (isBroadcast) {
                                            ArrayList<TLRPC.Message> currentMessage = new ArrayList<>();
                                            currentMessage.add(newMsgObj);
                                            MessagesStorage.getInstance().putMessages(currentMessage, true, false, false, 0);
                                        }
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isBroadcast) {
                                                    for (int a = 0; a < sentMessages.size(); a++) {
                                                        TLRPC.Message message = sentMessages.get(a);
                                                        ArrayList<MessageObject> arr = new ArrayList<>();
                                                        MessageObject messageObject = new MessageObject(message, null, false);
                                                        arr.add(messageObject);
                                                        MessagesController.getInstance().updateInterfaceWithMessages(messageObject.getDialogId(), arr, true);
                                                    }
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                                }
                                                SearchQuery.increasePeerRaiting(newMsgObj.dialog_id);
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, (isBroadcast ? oldId : newMsgObj.id), newMsgObj, newMsgObj.dialog_id);
                                                processSentMessage(oldId);
                                                removeFromSendingMessages(oldId);
                                            }
                                        });
                                        if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                                            stopVideoService(attachPath);
                                        }
                                    }
                                });
                            }
                        } else {
                            if (error.text.equals("PEER_FLOOD")) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 0);
                            }
                            isSentError = true;
                        }
                        if (isSentError) {
                            MessagesStorage.getInstance().markMessageAsSendError(newMsgObj);
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                            processSentMessage(newMsgObj.id);
                            if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                                stopVideoService(newMsgObj.attachPath);
                            }
                            removeFromSendingMessages(newMsgObj.id);
                        }
                    }
                });
            }
        }, new QuickAckDelegate() {
            @Override
            public void run() {
                final int msg_id = newMsgObj.id;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByAck, msg_id);
                    }
                });
            }
        }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter | (req instanceof TLRPC.TL_messages_sendMessage ? ConnectionsManager.RequestFlagNeedQuickAck : 0));
    }

    private void updateMediaPaths(MessageObject newMsgObj, TLRPC.Message sentMessage, String originalPath, boolean post) {
        TLRPC.Message newMsg = newMsgObj.messageOwner;
        if (sentMessage == null) {
            return;
        }
        if (sentMessage.media instanceof TLRPC.TL_messageMediaPhoto && sentMessage.media.photo != null && newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
            MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.photo, 0);

            if (newMsg.media.photo.sizes.size() == 1 && newMsg.media.photo.sizes.get(0).location instanceof TLRPC.TL_fileLocationUnavailable) {
                newMsg.media.photo.sizes = sentMessage.media.photo.sizes;
            } else {
                for (int a = 0; a < sentMessage.media.photo.sizes.size(); a++) {
                    TLRPC.PhotoSize size = sentMessage.media.photo.sizes.get(a);
                    if (size == null || size.location == null || size instanceof TLRPC.TL_photoSizeEmpty || size.type == null) {
                        continue;
                    }
                    for (int b = 0; b < newMsg.media.photo.sizes.size(); b++) {
                        TLRPC.PhotoSize size2 = newMsg.media.photo.sizes.get(b);
                        if (size2 == null || size2.location == null || size2.type == null) {
                            continue;
                        }
                        if (size2.location.volume_id == Integer.MIN_VALUE && size.type.equals(size2.type) || size.w == size2.w && size.h == size2.h) {
                            String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                            String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                            if (fileName.equals(fileName2)) {
                                break;
                            }
                            File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                            File cacheFile2;
                            if (sentMessage.media.photo.sizes.size() == 1 || size.w > 90 || size.h > 90) {
                                cacheFile2 = FileLoader.getPathToAttach(size);
                            } else {
                                cacheFile2 = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                            }
                            cacheFile.renameTo(cacheFile2);
                            ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location, post);
                            size2.location = size.location;
                            size2.size = size.size;
                            break;
                        }
                    }
                }
            }
            sentMessage.message = newMsg.message;
            sentMessage.attachPath = newMsg.attachPath;
            newMsg.media.photo.id = sentMessage.media.photo.id;
            newMsg.media.photo.access_hash = sentMessage.media.photo.access_hash;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaDocument && sentMessage.media.document != null && newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
            if (MessageObject.isVideoMessage(sentMessage)) {
                MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.document, 2);
                sentMessage.attachPath = newMsg.attachPath;
            } else if (!MessageObject.isVoiceMessage(sentMessage)) {
                MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.document, 1);
            }

            TLRPC.PhotoSize size2 = newMsg.media.document.thumb;
            TLRPC.PhotoSize size = sentMessage.media.document.thumb;
            if (size2 != null && size2.location != null && size2.location.volume_id == Integer.MIN_VALUE && size != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                    File cacheFile2 = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                    cacheFile.renameTo(cacheFile2);
                    ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location, post);
                    size2.location = size.location;
                    size2.size = size.size;
                }
            } else if (size2 != null && MessageObject.isStickerMessage(sentMessage) && size2.location != null) {
                size.location = size2.location;
            } else if (size2 != null && size2.location instanceof TLRPC.TL_fileLocationUnavailable || size2 instanceof TLRPC.TL_photoSizeEmpty) {
                newMsg.media.document.thumb = sentMessage.media.document.thumb;
            }

            newMsg.media.document.dc_id = sentMessage.media.document.dc_id;
            newMsg.media.document.id = sentMessage.media.document.id;
            newMsg.media.document.access_hash = sentMessage.media.document.access_hash;
            byte[] oldWaveform = null;
            for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    oldWaveform = attribute.waveform;
                    break;
                }
            }
            newMsg.media.document.attributes = sentMessage.media.document.attributes;
            if (oldWaveform != null) {
                for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        attribute.waveform = oldWaveform;
                        attribute.flags |= 4;
                    }
                }
            }
            newMsg.media.document.size = sentMessage.media.document.size;
            newMsg.media.document.mime_type = sentMessage.media.document.mime_type;

            if ((sentMessage.flags & TLRPC.MESSAGE_FLAG_FWD) == 0 && MessageObject.isOut(sentMessage)) {
                if (MessageObject.isNewGifDocument(sentMessage.media.document)) {
                    StickersQuery.addRecentGif(sentMessage.media.document, sentMessage.date);
                } else if (MessageObject.isStickerDocument(sentMessage.media.document)) {
                    StickersQuery.addRecentSticker(StickersQuery.TYPE_IMAGE, sentMessage.media.document, sentMessage.date);
                }
            }

            if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                File cacheFile = new File(newMsg.attachPath);
                File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.document);
                if (!cacheFile.renameTo(cacheFile2)) {
                    sentMessage.attachPath = newMsg.attachPath;
                    sentMessage.message = newMsg.message;
                } else {
                    if (MessageObject.isVideoMessage(sentMessage)) {
                        newMsgObj.attachPathExists = true;
                    } else {
                        newMsgObj.mediaExists = newMsgObj.attachPathExists;
                        newMsgObj.attachPathExists = false;
                        newMsg.attachPath = "";
                        if (originalPath != null && originalPath.startsWith("http")) {
                            MessagesStorage.getInstance().addRecentLocalFile(originalPath, cacheFile2.toString(), newMsg.media.document);
                        }
                    }
                }
            } else {
                sentMessage.attachPath = newMsg.attachPath;
                sentMessage.message = newMsg.message;
            }
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaContact && newMsg.media instanceof TLRPC.TL_messageMediaContact) {
            newMsg.media = sentMessage.media;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaWebPage) {
            newMsg.media = sentMessage.media;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaGame) {
            newMsg.media = sentMessage.media;
            if (newMsg.media instanceof TLRPC.TL_messageMediaGame && !TextUtils.isEmpty(sentMessage.message)) {
                newMsg.entities = sentMessage.entities;
                newMsg.message = sentMessage.message;
            }
        }
    }

    private void putToDelayedMessages(String location, DelayedMessage message) {
        ArrayList<DelayedMessage> arrayList = delayedMessages.get(location);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            delayedMessages.put(location, arrayList);
        }
        arrayList.add(message);
    }

    protected ArrayList<DelayedMessage> getDelayedMessages(String location) {
        return delayedMessages.get(location);
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
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    MessageObject messageObject = new MessageObject(message, null, false);
                    retrySendMessage(messageObject, true);
                }
            }
        });
    }

    public TLRPC.TL_photo generatePhotoSizes(String path, Uri imageUri) {
        Bitmap bitmap = ImageLoader.loadBitmap(path, imageUri, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), true);
        if (bitmap == null && AndroidUtilities.getPhotoSize() != 800) {
            bitmap = ImageLoader.loadBitmap(path, imageUri, 800, 800, true);
        }
        ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<>();
        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
        if (size != null) {
            sizes.add(size);
        }
        size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 80, false, 101, 101);
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
            photo.date = ConnectionsManager.getInstance().getCurrentTime();
            photo.sizes = sizes;
            return photo;
        }
    }

    private static boolean prepareSendingDocumentInternal(String path, String originalPath, Uri uri, String mime, final long dialog_id, final MessageObject reply_to_msg, String caption) {
        if ((path == null || path.length() == 0) && uri == null) {
            return false;
        }
        if (uri != null && AndroidUtilities.isInternalUri(uri)) {
            return false;
        }
        if (path != null && AndroidUtilities.isInternalUri(Uri.fromFile(new File(path)))) {
            return false;
        }
        MimeTypeMap myMime = MimeTypeMap.getSingleton();
        TLRPC.TL_documentAttributeAudio attributeAudio = null;
        if (uri != null) {
            String extension = null;
            if (mime != null) {
                extension = myMime.getExtensionFromMimeType(mime);
            }
            if (extension == null) {
                extension = "txt";
            }
            path = MediaController.copyFileToCache(uri, extension);
            if (path == null) {
                return false;
            }
        }
        final File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            return false;
        }

        boolean isEncrypted = (int) dialog_id == 0;
        boolean allowSticker = !isEncrypted;

        String name = f.getName();
        String ext = "";
        int idx = path.lastIndexOf('.');
        if (idx != -1) {
            ext = path.substring(idx + 1);
        }
        if (ext.toLowerCase().equals("mp3") || ext.toLowerCase().equals("m4a")) {
            AudioInfo audioInfo = AudioInfo.getAudioInfo(f);
            if (audioInfo != null && audioInfo.getDuration() != 0) {
                if (isEncrypted) {
                    int high_id = (int) (dialog_id >> 32);
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
                    if (encryptedChat == null) {
                        return false;
                    }
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 46) {
                        attributeAudio = new TLRPC.TL_documentAttributeAudio();
                    } else {
                        attributeAudio = new TLRPC.TL_documentAttributeAudio_old();
                    }
                } else {
                    attributeAudio = new TLRPC.TL_documentAttributeAudio();
                }
                attributeAudio.duration = (int) (audioInfo.getDuration() / 1000);
                attributeAudio.title = audioInfo.getTitle();
                attributeAudio.performer = audioInfo.getArtist();
                if (attributeAudio.title == null) {
                    attributeAudio.title = "";
                    attributeAudio.flags |= 1;
                }
                if (attributeAudio.performer == null) {
                    attributeAudio.performer = "";
                    attributeAudio.flags |= 2;
                }
            }
        }
        if (originalPath != null) {
            if (attributeAudio != null) {
                originalPath += "audio" + f.length();
            } else {
                originalPath += "" + f.length();
            }
        }

        TLRPC.TL_document document = null;
        if (!isEncrypted) {
            document = (TLRPC.TL_document) MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 1 : 4);
            if (document == null && !path.equals(originalPath) && !isEncrypted) {
                document = (TLRPC.TL_document) MessagesStorage.getInstance().getSentFile(path + f.length(), !isEncrypted ? 1 : 4);
            }
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.date = ConnectionsManager.getInstance().getCurrentTime();
            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
            fileName.file_name = name;
            document.attributes.add(fileName);
            document.size = (int) f.length();
            document.dc_id = 0;
            if (attributeAudio != null) {
                document.attributes.add(attributeAudio);
            }
            if (ext.length() != 0) {
                if (ext.toLowerCase().equals("webp")) {
                    document.mime_type = "image/webp";
                } else {
                    String mimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                    if (mimeType != null) {
                        document.mime_type = mimeType;
                    } else {
                        document.mime_type = "application/octet-stream";
                    }
                }
            } else {
                document.mime_type = "application/octet-stream";
            }
            if (document.mime_type.equals("image/gif")) {
                try {
                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90, true);
                    if (bitmap != null) {
                        fileName.file_name = "animation.gif";
                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                        bitmap.recycle();
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            if (document.mime_type.equals("image/webp") && allowSticker) {
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                try {
                    bmOptions.inJustDecodeBounds = true;
                    RandomAccessFile file = new RandomAccessFile(path, "r");
                    ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
                    Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                    file.close();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (bmOptions.outWidth != 0 && bmOptions.outHeight != 0 && bmOptions.outWidth <= 800 && bmOptions.outHeight <= 800) {
                    TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
                    attributeSticker.alt = "";
                    attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                    document.attributes.add(attributeSticker);
                    TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                    attributeImageSize.w = bmOptions.outWidth;
                    attributeImageSize.h = bmOptions.outHeight;
                    document.attributes.add(attributeImageSize);
                }
            }
            if (document.thumb == null) {
                document.thumb = new TLRPC.TL_photoSizeEmpty();
                document.thumb.type = "s";
            }
        }
        document.caption = caption;

        final HashMap<String, String> params = new HashMap<>();
        if (originalPath != null) {
            params.put("originalPath", originalPath);
        }
        final TLRPC.TL_document documentFinal = document;
        final String pathFinal = path;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                SendMessagesHelper.getInstance().sendMessage(documentFinal, null, pathFinal, dialog_id, reply_to_msg, null, params);
            }
        });
        return true;
    }

    public static void prepareSendingDocument(String path, String originalPath, Uri uri, String mine, long dialog_id, MessageObject reply_to_msg) {
        if ((path == null || originalPath == null) && uri == null) {
            return;
        }
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> originalPaths = new ArrayList<>();
        ArrayList<Uri> uris = null;
        if (uri != null) {
            uris = new ArrayList<>();
        }
        paths.add(path);
        originalPaths.add(originalPath);
        prepareSendingDocuments(paths, originalPaths, uris, mine, dialog_id, reply_to_msg);
    }

    public static void prepareSendingAudioDocuments(final ArrayList<MessageObject> messageObjects, final long dialog_id, final MessageObject reply_to_msg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int size = messageObjects.size();
                for (int a = 0; a < size; a++) {
                    final MessageObject messageObject = messageObjects.get(a);
                    String originalPath = messageObject.messageOwner.attachPath;
                    final File f = new File(originalPath);

                    boolean isEncrypted = (int) dialog_id == 0;


                    if (originalPath != null) {
                        originalPath += "audio" + f.length();
                    }

                    TLRPC.TL_document document = null;
                    if (!isEncrypted) {
                        document = (TLRPC.TL_document) MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 1 : 4);
                    }
                    if (document == null) {
                        document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                    }

                    if (isEncrypted) {
                        int high_id = (int) (dialog_id >> 32);
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
                        if (encryptedChat == null) {
                            return;
                        }
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 46) {
                            for (int b = 0; b < document.attributes.size(); b++) {
                                if (document.attributes.get(b) instanceof TLRPC.TL_documentAttributeAudio) {
                                    TLRPC.TL_documentAttributeAudio_old old = new TLRPC.TL_documentAttributeAudio_old();
                                    old.duration = document.attributes.get(b).duration;
                                    document.attributes.remove(b);
                                    document.attributes.add(old);
                                    break;
                                }
                            }
                        }
                    }

                    final HashMap<String, String> params = new HashMap<>();
                    if (originalPath != null) {
                        params.put("originalPath", originalPath);
                    }
                    final TLRPC.TL_document documentFinal = document;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            SendMessagesHelper.getInstance().sendMessage(documentFinal, null, messageObject.messageOwner.attachPath, dialog_id, reply_to_msg, null, params);
                        }
                    });
                }
            }
        }).start();
    }

    public static void prepareSendingDocuments(final ArrayList<String> paths, final ArrayList<String> originalPaths, final ArrayList<Uri> uris, final String mime, final long dialog_id, final MessageObject reply_to_msg) {
        if (paths == null && originalPaths == null && uris == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean error = false;
                if (paths != null) {
                    for (int a = 0; a < paths.size(); a++) {
                        if (!prepareSendingDocumentInternal(paths.get(a), originalPaths.get(a), null, mime, dialog_id, reply_to_msg, null)) {
                            error = true;
                        }
                    }
                }
                if (uris != null) {
                    for (int a = 0; a < uris.size(); a++) {
                        if (!prepareSendingDocumentInternal(null, null, uris.get(a), mime, dialog_id, reply_to_msg, null)) {
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

    public static void prepareSendingPhoto(String imageFilePath, Uri imageUri, long dialog_id, MessageObject reply_to_msg, CharSequence caption, ArrayList<TLRPC.InputDocument> stickers) {
        ArrayList<String> paths = null;
        ArrayList<Uri> uris = null;
        ArrayList<String> captions = null;
        ArrayList<ArrayList<TLRPC.InputDocument>> masks = null;
        if (imageFilePath != null && imageFilePath.length() != 0) {
            paths = new ArrayList<>();
            paths.add(imageFilePath);
        }
        if (imageUri != null) {
            uris = new ArrayList<>();
            uris.add(imageUri);
        }
        if (caption != null) {
            captions = new ArrayList<>();
            captions.add(caption.toString());
        }
        if (stickers != null && !stickers.isEmpty()) {
            masks = new ArrayList<>();
            masks.add(new ArrayList<>(stickers));
        }
        prepareSendingPhotos(paths, uris, dialog_id, reply_to_msg, captions, masks);
    }

    public static void prepareSendingBotContextResult(final TLRPC.BotInlineResult result, final HashMap<String, String> params, final long dialog_id, final MessageObject reply_to_msg) {
        if (result == null) {
            return;
        }
        if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String finalPath = null;
                    TLRPC.TL_document document = null;
                    TLRPC.TL_photo photo = null;
                    TLRPC.TL_game game = null;
                    if (result instanceof TLRPC.TL_botInlineMediaResult) {
                        if (result.type.equals("game")) {
                            if ((int) dialog_id == 0) {
                                return; //doesn't work in secret chats for now
                            }
                            game = new TLRPC.TL_game();
                            game.title = result.title;
                            game.description = result.description;
                            game.short_name = result.id;
                            game.photo = result.photo;
                            if (result.document instanceof TLRPC.TL_document) {
                                game.document = result.document;
                                game.flags |= 1;
                            }
                        } else if (result.document != null) {
                            if (result.document instanceof TLRPC.TL_document) {
                                document = (TLRPC.TL_document) result.document;
                            }
                        } else if (result.photo != null) {
                            if (result.photo instanceof TLRPC.TL_photo) {
                                photo = (TLRPC.TL_photo) result.photo;
                            }
                        }
                    } else {
                        if (result.content_url != null) {
                            File f = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content_url) + "." + ImageLoader.getHttpUrlExtension(result.content_url, "file"));
                            if (f.exists()) {
                                finalPath = f.getAbsolutePath();
                            } else {
                                finalPath = result.content_url;
                            }
                            switch (result.type) {
                                case "audio":
                                case "voice":
                                case "file":
                                case "video":
                                case "sticker":
                                case "gif": {
                                    document = new TLRPC.TL_document();
                                    document.id = 0;
                                    document.size = 0;
                                    document.dc_id = 0;
                                    document.mime_type = result.content_type;
                                    document.date = ConnectionsManager.getInstance().getCurrentTime();
                                    TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                                    document.attributes.add(fileName);

                                    switch (result.type) {
                                        case "gif": {
                                            fileName.file_name = "animation.gif";
                                            if (finalPath.endsWith("mp4")) {
                                                document.mime_type = "video/mp4";
                                                document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                            } else {
                                                document.mime_type = "image/gif";
                                            }
                                            try {
                                                Bitmap bitmap;
                                                if (finalPath.endsWith("mp4")) {
                                                    bitmap = ThumbnailUtils.createVideoThumbnail(finalPath, MediaStore.Video.Thumbnails.MINI_KIND);
                                                } else {
                                                    bitmap = ImageLoader.loadBitmap(finalPath, null, 90, 90, true);
                                                }
                                                if (bitmap != null) {
                                                    document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                    bitmap.recycle();
                                                }
                                            } catch (Throwable e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            break;
                                        }
                                        case "voice": {
                                            TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                            audio.duration = result.duration;
                                            audio.voice = true;
                                            fileName.file_name = "audio.ogg";
                                            document.attributes.add(audio);

                                            document.thumb = new TLRPC.TL_photoSizeEmpty();
                                            document.thumb.type = "s";

                                            break;
                                        }
                                        case "audio": {
                                            TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                            audio.duration = result.duration;
                                            audio.title = result.title;
                                            audio.flags |= 1;
                                            if (result.description != null) {
                                                audio.performer = result.description;
                                                audio.flags |= 2;
                                            }
                                            fileName.file_name = "audio.mp3";
                                            document.attributes.add(audio);

                                            document.thumb = new TLRPC.TL_photoSizeEmpty();
                                            document.thumb.type = "s";

                                            break;
                                        }
                                        case "file": {
                                            int idx = result.content_type.indexOf('/');
                                            if (idx != -1) {
                                                fileName.file_name = "file." + result.content_type.substring(idx + 1);
                                            } else {
                                                fileName.file_name = "file";
                                            }
                                            break;
                                        }
                                        case "video": {
                                            fileName.file_name = "video.mp4";
                                            TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                            attributeVideo.w = result.w;
                                            attributeVideo.h = result.h;
                                            attributeVideo.duration = result.duration;
                                            document.attributes.add(attributeVideo);
                                            try {
                                                String thumbPath = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb_url) + "." + ImageLoader.getHttpUrlExtension(result.thumb_url, "jpg")).getAbsolutePath();
                                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                                if (bitmap != null) {
                                                    document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                    bitmap.recycle();
                                                }
                                            } catch (Throwable e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            break;
                                        }
                                        case "sticker": {
                                            TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
                                            attributeSticker.alt = "";
                                            attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                            document.attributes.add(attributeSticker);
                                            TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                                            attributeImageSize.w = result.w;
                                            attributeImageSize.h = result.h;
                                            document.attributes.add(attributeImageSize);
                                            fileName.file_name = "sticker.webp";
                                            try {
                                                String thumbPath = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb_url) + "." + ImageLoader.getHttpUrlExtension(result.thumb_url, "webp")).getAbsolutePath();
                                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                                if (bitmap != null) {
                                                    document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                    bitmap.recycle();
                                                }
                                            } catch (Throwable e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            break;
                                        }
                                    }
                                    if (fileName.file_name == null) {
                                        fileName.file_name = "file";
                                    }
                                    if (document.mime_type == null) {
                                        document.mime_type = "application/octet-stream";
                                    }
                                    if (document.thumb == null) {
                                        document.thumb = new TLRPC.TL_photoSize();
                                        document.thumb.w = result.w;
                                        document.thumb.h = result.h;
                                        document.thumb.size = 0;
                                        document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                        document.thumb.type = "x";
                                    }
                                    break;
                                }
                                case "photo": {
                                    if (f.exists()) {
                                        photo = SendMessagesHelper.getInstance().generatePhotoSizes(finalPath, null);
                                    }
                                    if (photo == null) {
                                        photo = new TLRPC.TL_photo();
                                        photo.date = ConnectionsManager.getInstance().getCurrentTime();
                                        TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                        photoSize.w = result.w;
                                        photoSize.h = result.h;
                                        photoSize.size = 1;
                                        photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                                        photoSize.type = "x";
                                        photo.sizes.add(photoSize);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    final String finalPathFinal = finalPath;
                    final TLRPC.TL_document finalDocument = document;
                    final TLRPC.TL_photo finalPhoto = photo;
                    final TLRPC.TL_game finalGame = game;
                    if (params != null && result.content_url != null) {
                        params.put("originalPath", result.content_url);
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (finalDocument != null) {
                                finalDocument.caption = result.send_message.caption;
                                SendMessagesHelper.getInstance().sendMessage(finalDocument, null, finalPathFinal, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
                            } else if (finalPhoto != null) {
                                finalPhoto.caption = result.send_message.caption;
                                SendMessagesHelper.getInstance().sendMessage(finalPhoto, result.content_url, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
                            } else if (finalGame != null) {
                                SendMessagesHelper.getInstance().sendMessage(finalGame, dialog_id, result.send_message.reply_markup, params);
                            }
                        }
                    });
                }
            }).run();
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageText) {
            SendMessagesHelper.getInstance().sendMessage(result.send_message.message, dialog_id, reply_to_msg, null, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue) {
            TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
            venue.geo = result.send_message.geo;
            venue.address = result.send_message.address;
            venue.title = result.send_message.title;
            venue.provider = result.send_message.provider;
            venue.venue_id = result.send_message.venue_id;
            SendMessagesHelper.getInstance().sendMessage(venue, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
            TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
            location.geo = result.send_message.geo;
            SendMessagesHelper.getInstance().sendMessage(location, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaContact) {
            TLRPC.User user = new TLRPC.TL_user();
            user.phone = result.send_message.phone_number;
            user.first_name = result.send_message.first_name;
            user.last_name = result.send_message.last_name;
            SendMessagesHelper.getInstance().sendMessage(user, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
        }
    }

    public static void prepareSendingPhotosSearch(final ArrayList<MediaController.SearchImage> photos, final long dialog_id, final MessageObject reply_to_msg) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isEncrypted = (int) dialog_id == 0;
                for (int a = 0; a < photos.size(); a++) {
                    final MediaController.SearchImage searchImage = photos.get(a);
                    if (searchImage.type == 1) {
                        final HashMap<String, String> params = new HashMap<>();
                        TLRPC.TL_document document = null;
                        File cacheFile;
                        if (searchImage.document instanceof TLRPC.TL_document) {
                            document = (TLRPC.TL_document) searchImage.document;
                            cacheFile = FileLoader.getPathToAttach(document, true);
                        } else {
                            if (!isEncrypted) {
                                TLRPC.Document doc = (TLRPC.Document) MessagesStorage.getInstance().getSentFile(searchImage.imageUrl, !isEncrypted ? 1 : 4);
                                if (doc instanceof TLRPC.TL_document) {
                                    document = (TLRPC.TL_document) doc;
                                }
                            }
                            String md5 = Utilities.MD5(searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(searchImage.imageUrl, "jpg");
                            cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        }
                        if (document == null) {
                            if (searchImage.localUrl != null) {
                                params.put("url", searchImage.localUrl);
                            }
                            File thumbFile = null;
                            document = new TLRPC.TL_document();
                            document.id = 0;
                            document.date = ConnectionsManager.getInstance().getCurrentTime();
                            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                            fileName.file_name = "animation.gif";
                            document.attributes.add(fileName);
                            document.size = searchImage.size;
                            document.dc_id = 0;
                            if (cacheFile.toString().endsWith("mp4")) {
                                document.mime_type = "video/mp4";
                                document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                            } else {
                                document.mime_type = "image/gif";
                            }
                            if (cacheFile.exists()) {
                                thumbFile = cacheFile;
                            } else {
                                cacheFile = null;
                            }
                            if (thumbFile == null) {
                                String thumb = Utilities.MD5(searchImage.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(searchImage.thumbUrl, "jpg");
                                thumbFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), thumb);
                                if (!thumbFile.exists()) {
                                    thumbFile = null;
                                }
                            }
                            if (thumbFile != null) {
                                try {
                                    Bitmap bitmap;
                                    if (thumbFile.getAbsolutePath().endsWith("mp4")) {
                                        bitmap = ThumbnailUtils.createVideoThumbnail(thumbFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
                                    } else {
                                        bitmap = ImageLoader.loadBitmap(thumbFile.getAbsolutePath(), null, 90, 90, true);
                                    }
                                    if (bitmap != null) {
                                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                                        bitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                            if (document.thumb == null) {
                                document.thumb = new TLRPC.TL_photoSize();
                                document.thumb.w = searchImage.width;
                                document.thumb.h = searchImage.height;
                                document.thumb.size = 0;
                                document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                document.thumb.type = "x";
                            }
                        }

                        if (searchImage.caption != null) {
                            document.caption = searchImage.caption.toString();
                        }
                        final TLRPC.TL_document documentFinal = document;
                        final String originalPathFinal = searchImage.imageUrl;
                        final String pathFinal = cacheFile == null ? searchImage.imageUrl : cacheFile.toString();
                        if (params != null && searchImage.imageUrl != null) {
                            params.put("originalPath", searchImage.imageUrl);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                SendMessagesHelper.getInstance().sendMessage(documentFinal, null, pathFinal, dialog_id, reply_to_msg, null, params);
                            }
                        });
                    } else {
                        boolean needDownloadHttp = true;
                        TLRPC.TL_photo photo = null;
                        if (!isEncrypted) {
                            photo = (TLRPC.TL_photo) MessagesStorage.getInstance().getSentFile(searchImage.imageUrl, !isEncrypted ? 0 : 3);
                        }
                        if (photo == null) {
                            String md5 = Utilities.MD5(searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(searchImage.imageUrl, "jpg");
                            File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                            if (cacheFile.exists() && cacheFile.length() != 0) {
                                photo = SendMessagesHelper.getInstance().generatePhotoSizes(cacheFile.toString(), null);
                                if (photo != null) {
                                    needDownloadHttp = false;
                                }
                            }
                            if (photo == null) {
                                md5 = Utilities.MD5(searchImage.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(searchImage.thumbUrl, "jpg");
                                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                                if (cacheFile.exists()) {
                                    photo = SendMessagesHelper.getInstance().generatePhotoSizes(cacheFile.toString(), null);
                                }
                                if (photo == null) {
                                    photo = new TLRPC.TL_photo();
                                    photo.date = ConnectionsManager.getInstance().getCurrentTime();
                                    TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                    photoSize.w = searchImage.width;
                                    photoSize.h = searchImage.height;
                                    photoSize.size = 0;
                                    photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                                    photoSize.type = "x";
                                    photo.sizes.add(photoSize);
                                }
                            }
                        }
                        if (photo != null) {
                            if (searchImage.caption != null) {
                                photo.caption = searchImage.caption.toString();
                            }
                            final TLRPC.TL_photo photoFinal = photo;
                            final boolean needDownloadHttpFinal = needDownloadHttp;
                            final HashMap<String, String> params = new HashMap<>();
                            if (searchImage.imageUrl != null) {
                                params.put("originalPath", searchImage.imageUrl);
                            }
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    SendMessagesHelper.getInstance().sendMessage(photoFinal, needDownloadHttpFinal ? searchImage.imageUrl : null, dialog_id, reply_to_msg, null, params);
                                }
                            });
                        }
                    }
                }
            }
        }).start();
    }

    private static String getTrimmedString(String src) {
        String result = src.trim();
        if (result.length() == 0) {
            return result;
        }
        while (src.startsWith("\n")) {
            src = src.substring(1);
        }
        while (src.endsWith("\n")) {
            src = src.substring(0, src.length() - 1);
        }
        return src;
    }

    public static void prepareSendingText(final String text, final long dialog_id) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                String textFinal = getTrimmedString(text);
                                if (textFinal.length() != 0) {
                                    int count = (int) Math.ceil(textFinal.length() / 4096.0f);
                                    for (int a = 0; a < count; a++) {
                                        String mess = textFinal.substring(a * 4096, Math.min((a + 1) * 4096, textFinal.length()));
                                        SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, null, null, true, null, null, null);
                                    }
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    public static void prepareSendingPhotos(ArrayList<String> paths, ArrayList<Uri> uris, final long dialog_id, final MessageObject reply_to_msg, final ArrayList<String> captions, final ArrayList<ArrayList<TLRPC.InputDocument>> masks) {
        if (paths == null && uris == null || paths != null && paths.isEmpty() || uris != null && uris.isEmpty()) {
            return;
        }
        final ArrayList<String> pathsCopy = new ArrayList<>();
        final ArrayList<Uri> urisCopy = new ArrayList<>();
        if (paths != null) {
            pathsCopy.addAll(paths);
        }
        if (uris != null) {
            urisCopy.addAll(uris);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isEncrypted = (int) dialog_id == 0;

                ArrayList<String> sendAsDocuments = null;
                ArrayList<String> sendAsDocumentsOriginal = null;
                ArrayList<String> sendAsDocumentsCaptions = null;
                int count = !pathsCopy.isEmpty() ? pathsCopy.size() : urisCopy.size();
                String path = null;
                Uri uri = null;
                String extension = null;
                for (int a = 0; a < count; a++) {
                    if (!pathsCopy.isEmpty()) {
                        path = pathsCopy.get(a);
                    } else if (!urisCopy.isEmpty()) {
                        uri = urisCopy.get(a);
                    }

                    String originalPath = path;
                    String tempPath = path;
                    if (tempPath == null && uri != null) {
                        tempPath = AndroidUtilities.getPath(uri);
                        originalPath = uri.toString();
                    }

                    boolean isDocument = false;
                    if (tempPath != null && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp"))) {
                        if (tempPath.endsWith(".gif")) {
                            extension = "gif";
                        } else {
                            extension = "webp";
                        }
                        isDocument = true;
                    } else if (tempPath == null && uri != null) {
                        if (MediaController.isGif(uri)) {
                            isDocument = true;
                            originalPath = uri.toString();
                            tempPath = MediaController.copyFileToCache(uri, "gif");
                            extension = "gif";
                        } else if (MediaController.isWebp(uri)) {
                            isDocument = true;
                            originalPath = uri.toString();
                            tempPath = MediaController.copyFileToCache(uri, "webp");
                            extension = "webp";
                        }
                    }

                    if (isDocument) {
                        if (sendAsDocuments == null) {
                            sendAsDocuments = new ArrayList<>();
                            sendAsDocumentsOriginal = new ArrayList<>();
                            sendAsDocumentsCaptions = new ArrayList<>();
                        }
                        sendAsDocuments.add(tempPath);
                        sendAsDocumentsOriginal.add(originalPath);
                        sendAsDocumentsCaptions.add(captions != null ? captions.get(a) : null);
                    } else {
                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        } else {
                            originalPath = null;
                        }
                        TLRPC.TL_photo photo = null;
                        if (!isEncrypted) {
                            photo = (TLRPC.TL_photo) MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 0 : 3);
                            if (photo == null && uri != null) {
                                photo = (TLRPC.TL_photo) MessagesStorage.getInstance().getSentFile(AndroidUtilities.getPath(uri), !isEncrypted ? 0 : 3);
                            }
                        }
                        if (photo == null) {
                            photo = SendMessagesHelper.getInstance().generatePhotoSizes(path, uri);
                        }
                        if (photo != null) {
                            final TLRPC.TL_photo photoFinal = photo;
                            final HashMap<String, String> params = new HashMap<>();
                            if (captions != null) {
                                photo.caption = captions.get(a);
                            }
                            if (masks != null) {
                                ArrayList<TLRPC.InputDocument> arrayList = masks.get(a);
                                if (photo.has_stickers = arrayList != null && !arrayList.isEmpty()) {
                                    SerializedData serializedData = new SerializedData(4 + arrayList.size() * 20);
                                    serializedData.writeInt32(arrayList.size());
                                    for (int b = 0; b < arrayList.size(); b++) {
                                        arrayList.get(b).serializeToStream(serializedData);
                                    }
                                    params.put("masks", Utilities.bytesToHex(serializedData.toByteArray()));
                                }
                            }
                            if (originalPath != null) {
                                params.put("originalPath", originalPath);
                            }
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    SendMessagesHelper.getInstance().sendMessage(photoFinal, null, dialog_id, reply_to_msg, null, params);
                                }
                            });
                        } else {
                            if (sendAsDocuments == null) {
                                sendAsDocuments = new ArrayList<>();
                                sendAsDocumentsOriginal = new ArrayList<>();
                                sendAsDocumentsCaptions = new ArrayList<>();
                            }
                            sendAsDocuments.add(tempPath);
                            sendAsDocumentsOriginal.add(originalPath);
                            sendAsDocumentsCaptions.add(captions != null ? captions.get(a) : null);
                        }
                    }
                }
                if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                    for (int a = 0; a < sendAsDocuments.size(); a++) {
                        prepareSendingDocumentInternal(sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a), null, extension, dialog_id, reply_to_msg, sendAsDocumentsCaptions.get(a));
                    }
                }
            }
        }).start();
    }

    private static void fillVideoAttribute(String videoPath, TLRPC.TL_documentAttributeVideo attributeVideo, VideoEditedInfo videoEditedInfo) {
        boolean infoObtained = false;

        MediaMetadataRetriever mediaMetadataRetriever = null;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(videoPath);
            String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            if (width != null) {
                attributeVideo.w = Integer.parseInt(width);
            }
            String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (height != null) {
                attributeVideo.h = Integer.parseInt(height);
            }
            String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                attributeVideo.duration = (int) Math.ceil(Long.parseLong(duration) / 1000.0f);
            }
            if (Build.VERSION.SDK_INT >= 17) {
                String rotation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rotation != null) {
                    int val = Utilities.parseInt(rotation);
                    if (videoEditedInfo != null) {
                        videoEditedInfo.rotationValue = val;
                    } else if (val == 90 || val == 270) {
                        int temp = attributeVideo.w;
                        attributeVideo.w = attributeVideo.h;
                        attributeVideo.h = temp;
                    }
                }
            }
            infoObtained = true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (!infoObtained) {
            try {
                MediaPlayer mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(new File(videoPath)));
                if (mp != null) {
                    attributeVideo.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                    attributeVideo.w = mp.getVideoWidth();
                    attributeVideo.h = mp.getVideoHeight();
                    mp.release();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    public static void prepareSendingVideo(final String videoPath, final long estimatedSize, final long duration, final int width, final int height, final VideoEditedInfo videoEditedInfo, final long dialog_id, final MessageObject reply_to_msg, final String caption) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean isEncrypted = (int) dialog_id == 0;

                if (videoEditedInfo != null || videoPath.endsWith("mp4")) {
                    String path = videoPath;
                    String originalPath = videoPath;
                    File temp = new File(originalPath);
                    originalPath += temp.length() + "_" + temp.lastModified();
                    if (videoEditedInfo != null) {
                        originalPath += duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime;
                        if (videoEditedInfo.resultWidth == videoEditedInfo.originalWidth) {
                            originalPath += "_" + videoEditedInfo.resultWidth;
                        }
                    }
                    TLRPC.TL_document document = null;
                    if (!isEncrypted) {
                        //document = (TLRPC.TL_document) MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 2 : 5);
                    }
                    if (document == null) {
                        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, isEncrypted);
                        document = new TLRPC.TL_document();
                        document.thumb = size;
                        if (document.thumb == null) {
                            document.thumb = new TLRPC.TL_photoSizeEmpty();
                            document.thumb.type = "s";
                        } else {
                            document.thumb.type = "s";
                        }
                        document.mime_type = "video/mp4";
                        UserConfig.saveConfig(false);
                        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        document.attributes.add(attributeVideo);
                        if (videoEditedInfo != null) {
                            if (videoEditedInfo.bitrate == -1) {
                                document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                fillVideoAttribute(videoPath, attributeVideo, videoEditedInfo);
                                videoEditedInfo.originalWidth = videoEditedInfo.resultWidth = attributeVideo.w;
                                videoEditedInfo.originalHeight = videoEditedInfo.resultHeight = attributeVideo.h;
                            } else {
                                attributeVideo.duration = (int) (duration / 1000);
                                if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                                    attributeVideo.w = height;
                                    attributeVideo.h = width;
                                } else {
                                    attributeVideo.w = width;
                                    attributeVideo.h = height;
                                }
                            }
                            document.size = (int) estimatedSize;
                            String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
                            UserConfig.lastLocalId--;
                            File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                            UserConfig.saveConfig(false);
                            path = cacheFile.getAbsolutePath();
                        } else {
                            if (temp.exists()) {
                                document.size = (int) temp.length();
                            }
                            fillVideoAttribute(videoPath, attributeVideo, null);
                        }
                    }
                    final TLRPC.TL_document videoFinal = document;
                    final String originalPathFinal = originalPath;
                    final String finalPath = path;
                    final HashMap<String, String> params = new HashMap<>();
                    videoFinal.caption = caption;
                    if (originalPath != null) {
                        params.put("originalPath", originalPath);
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            SendMessagesHelper.getInstance().sendMessage(videoFinal, videoEditedInfo, finalPath, dialog_id, reply_to_msg, null, params);
                        }
                    });
                } else {
                    prepareSendingDocumentInternal(videoPath, videoPath, null, null, dialog_id, reply_to_msg, caption);
                }
            }
        }).start();
    }
}
