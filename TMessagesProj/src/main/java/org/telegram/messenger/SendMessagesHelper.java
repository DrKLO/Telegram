/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaCodecInfo;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.UiThread;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.PaymentFormActivity;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SendMessagesHelper implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.ChatFull currentChatInfo = null;
    private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<>();
    private SparseArray<MessageObject> unsentMessages = new SparseArray<>();
    private SparseArray<TLRPC.Message> sendingMessages = new SparseArray<>();
    private HashMap<String, MessageObject> waitingForLocation = new HashMap<>();
    private HashMap<String, Boolean> waitingForCallback = new HashMap<>();

    private static DispatchQueue mediaSendQueue = new DispatchQueue("mediaSendQueue");
    private static ThreadPoolExecutor mediaSendThreadPool;

    static {
        int cores;
        if (Build.VERSION.SDK_INT >= 17) {
            cores = Runtime.getRuntime().availableProcessors();
        } else {
            cores = 2;
        }
        mediaSendThreadPool = new ThreadPoolExecutor(cores, cores, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    private static class MediaSendPrepareWorker {
        public volatile TLRPC.TL_photo photo;
        public CountDownLatch sync;
    }

    private LocationProvider locationProvider = new LocationProvider(new LocationProvider.LocationProviderDelegate() {
        @Override
        public void onLocationAcquired(Location location) {
            sendLocation(location);
            waitingForLocation.clear();
        }

        @Override
        public void onUnableLocationAcquire() {
            HashMap<String, MessageObject> waitingForLocationCopy = new HashMap<>(waitingForLocation);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.wasUnableToFindCurrentLocation, waitingForLocationCopy);
            waitingForLocation.clear();
        }
    });

    public static class SendingMediaInfo {
        public Uri uri;
        public String path;
        public String caption;
        public int ttl;
        public ArrayList<TLRPC.MessageEntity> entities;
        public ArrayList<TLRPC.InputDocument> masks;
        public VideoEditedInfo videoEditedInfo;
        public MediaController.SearchImage searchImage;
        public boolean isVideo;
    }

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
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("found location " + location);
                }
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
                FileLog.e(e);
            }
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, networkLocationListener);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            } catch (Exception e) {
                FileLog.e(e);
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

    protected class DelayedMessageSendAfterRequest {
        public TLObject request;
        public MessageObject msgObj;
        public ArrayList<MessageObject> msgObjs;
        public String originalPath;
        public ArrayList<String> originalPaths;
    }

    protected class DelayedMessage {

        public long peer;
        ArrayList<DelayedMessageSendAfterRequest> requests;

        public TLObject sendRequest;
        public TLObject sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.FileLocation location;
        public String httpLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
        public VideoEditedInfo videoEditedInfo;

        public ArrayList<MessageObject> messageObjects;
        public ArrayList<TLRPC.Message> messages;
        public ArrayList<String> originalPaths;
        public HashMap<Object, Object> extraHashMap;
        public long groupId;
        public int finalGroupMessage;
        public boolean upload;

        public DelayedMessage(long peer) {
            this.peer = peer;
        }

        public void addDelayedRequest(final TLObject req, final MessageObject msgObj, final String originalPath) {
            DelayedMessageSendAfterRequest request = new DelayedMessageSendAfterRequest();
            request.request = req;
            request.msgObj = msgObj;
            request.originalPath = originalPath;
            if (requests == null) {
                requests = new ArrayList<>();
            }
            requests.add(request);
        }

        public void addDelayedRequest(final TLObject req, final ArrayList<MessageObject> msgObjs, final ArrayList<String> originalPaths) {
            DelayedMessageSendAfterRequest request = new DelayedMessageSendAfterRequest();
            request.request = req;
            request.msgObjs = msgObjs;
            request.originalPaths = originalPaths;
            if (requests == null) {
                requests = new ArrayList<>();
            }
            requests.add(request);
        }

        public void sendDelayedRequests() {
            if (requests == null || type != 4 && type != 0) {
                return;
            }
            int size = requests.size();
            for (int a = 0; a < size; a++) {
                DelayedMessageSendAfterRequest request = requests.get(a);
                if (request.request instanceof TLRPC.TL_messages_sendEncryptedMultiMedia) {
                    SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest((TLRPC.TL_messages_sendEncryptedMultiMedia) request.request, this);
                } else if (request.request instanceof TLRPC.TL_messages_sendMultiMedia) {
                    performSendMessageRequestMulti((TLRPC.TL_messages_sendMultiMedia) request.request, request.msgObjs, request.originalPaths);
                } else {
                    performSendMessageRequest(request.request, request.msgObj, request.originalPath);
                }
            }
            requests = null;
        }

        public void markAsError() {
            if (type == 4) {
                for (int a = 0; a < messageObjects.size(); a++) {
                    MessageObject obj = messageObjects.get(a);
                    MessagesStorage.getInstance(currentAccount).markMessageAsSendError(obj.messageOwner);
                    obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, obj.getId());
                    processSentMessage(obj.getId());
                }
                delayedMessages.remove( "group_" + groupId);
            } else {
                MessagesStorage.getInstance(currentAccount).markMessageAsSendError(obj.messageOwner);
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, obj.getId());
                processSentMessage(obj.getId());
            }
            sendDelayedRequests();
        }
    }

    private int currentAccount;
    private static volatile SendMessagesHelper[] Instance = new SendMessagesHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public static SendMessagesHelper getInstance(int num) {
        SendMessagesHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (SendMessagesHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new SendMessagesHelper(num);
                }
            }
        }
        return localInstance;
    }

    public SendMessagesHelper(int instance) {
        currentAccount = instance;

        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FileDidUpload);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FileDidFailUpload);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FilePreparingStarted);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FileNewChunkAvailable);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FilePreparingFailed);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.httpFileDidFailedLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.httpFileDidLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FileDidLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(SendMessagesHelper.this, NotificationCenter.FileDidFailedLoad);
        });
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
    public void didReceivedNotification(int id, int account, final Object... args) {
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
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_editMessage) {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                        media = (TLRPC.InputMedia) message.extraHashMap.get(location);
                    }

                    if (file != null && media != null) {
                        if (message.type == 0) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, message, true);
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
                                media.flags |= 4;
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
                                media.flags |= 4;
                                performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                            }
                        } else if (message.type == 3) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath);
                        } else if (message.type == 4) {
                            if (media instanceof TLRPC.TL_inputMediaUploadedDocument) {
                                if (media.file == null) {
                                    media.file = file;
                                    MessageObject messageObject = (MessageObject) message.extraHashMap.get(location + "_i");
                                    int index = message.messageObjects.indexOf(messageObject);
                                    message.location = (TLRPC.FileLocation) message.extraHashMap.get(location + "_t");
                                    stopVideoService(message.messageObjects.get(index).messageOwner.attachPath);
                                    if (media.thumb == null && message.location != null) {
                                        performSendDelayedMessage(message, index);
                                    } else {
                                        uploadMultiMedia(message, media, null, location);
                                    }
                                } else {
                                    media.thumb = file;
                                    media.flags |= 4;
                                    uploadMultiMedia(message, media, null, (String) message.extraHashMap.get(location + "_o"));
                                }
                            } else {
                                media.file = file;
                                uploadMultiMedia(message, media, null, location);
                            }
                        }
                        arr.remove(a);
                        a--;
                    } else if (encryptedFile != null && message.sendEncryptedRequest != null) {
                        TLRPC.TL_decryptedMessage decryptedMessage = null;
                        if (message.type == 4) {
                            TLRPC.TL_messages_sendEncryptedMultiMedia req = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                            TLRPC.InputEncryptedFile inputEncryptedFile = (TLRPC.InputEncryptedFile) message.extraHashMap.get(location);
                            int index = req.files.indexOf(inputEncryptedFile);
                            if (index >= 0) {
                                req.files.set(index, encryptedFile);
                                if (inputEncryptedFile.id == 1) {
                                    MessageObject messageObject = (MessageObject) message.extraHashMap.get(location + "_i");
                                    message.location = (TLRPC.FileLocation) message.extraHashMap.get(location + "_t");
                                    stopVideoService(message.messageObjects.get(index).messageOwner.attachPath);
                                }
                                decryptedMessage = req.messages.get(index);
                            }
                        } else {
                            decryptedMessage = (TLRPC.TL_decryptedMessage) message.sendEncryptedRequest;
                        }
                        if (decryptedMessage != null) {
                            if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaVideo ||
                                    decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaPhoto ||
                                    decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                                long size = (Long) args[5];
                                decryptedMessage.media.size = (int) size;
                            }
                            decryptedMessage.media.key = (byte[]) args[3];
                            decryptedMessage.media.iv = (byte[]) args[4];
                            if (message.type == 4) {
                                uploadMultiMedia(message, null, encryptedFile, location);
                            } else {
                                SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(decryptedMessage, message.obj.messageOwner, message.encryptedChat, encryptedFile, message.originalPath, message.obj);
                            }
                        }
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
                        obj.markAsError();
                        arr.remove(a);
                        a--;
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(location);
                }
            }
        } else if (id == NotificationCenter.FilePreparingStarted) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.getId() == 0) {
                return;
            }
            String finalPath = (String) args[1];

            ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.type == 4) {
                        int index = message.messageObjects.indexOf(messageObject);
                        message.location = (TLRPC.FileLocation) message.extraHashMap.get(messageObject.messageOwner.attachPath + "_t");
                        performSendDelayedMessage(message, index);
                        arr.remove(a);
                        break;
                    } else if (message.obj == messageObject) {
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
            if (messageObject.getId() == 0) {
                return;
            }
            String finalPath = (String) args[1];
            long availableSize = (Long) args[2];
            long finalSize = (Long) args[3];
            boolean isEncrypted = ((int) messageObject.getDialogId()) == 0;
            FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(finalPath, isEncrypted, availableSize, finalSize);
            if (finalSize != 0) {
                ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
                if (arr != null) {
                    for (int a = 0; a < arr.size(); a++) {
                        DelayedMessage message = arr.get(a);
                        if (message.type == 4) {
                            for (int b = 0; b < message.messageObjects.size(); b++) {
                                MessageObject obj = message.messageObjects.get(b);
                                if (obj == messageObject) {
                                    obj.videoEditedInfo = null;
                                    obj.messageOwner.params.remove("ve");
                                    obj.messageOwner.media.document.size = (int) finalSize;

                                    ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                    messages.add(obj.messageOwner);
                                    MessagesStorage.getInstance(currentAccount).putMessages(messages, false, true, false, 0);
                                    break;
                                }
                            }
                        } else if (message.obj == messageObject) {
                            message.obj.videoEditedInfo = null;
                            message.obj.messageOwner.params.remove("ve");
                            message.obj.messageOwner.media.document.size = (int) finalSize;

                            ArrayList<TLRPC.Message> messages = new ArrayList<>();
                            messages.add(message.obj.messageOwner);
                            MessagesStorage.getInstance(currentAccount).putMessages(messages, false, true, false, 0);
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.FilePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.getId() == 0) {
                return;
            }
            String finalPath = (String) args[1];
            stopVideoService(messageObject.messageOwner.attachPath);

            ArrayList<DelayedMessage> arr = delayedMessages.get(finalPath);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    if (message.type == 4) {
                        for (int b = 0; b < message.messages.size(); b++) {
                            if (message.messageObjects.get(b) == messageObject) {
                                message.markAsError();
                                arr.remove(a);
                                a--;
                                break;
                            }
                        }
                    } else if (message.obj == messageObject) {
                        message.markAsError();
                        arr.remove(a);
                        a--;
                    }
                }
                if (arr.isEmpty()) {
                    delayedMessages.remove(finalPath);
                }
            }
        } else if (id == NotificationCenter.httpFileDidLoaded) {
            final String path = (String) args[0];
            ArrayList<DelayedMessage> arr = delayedMessages.get(path);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    final DelayedMessage message = arr.get(a);
                    final MessageObject messageObject;
                    int fileType = -1;
                    if (message.type == 0) {
                        fileType = 0;
                        messageObject = message.obj;
                    } else if (message.type == 2) {
                        fileType = 1;
                        messageObject = message.obj;
                    } else if (message.type == 4) {
                        messageObject = (MessageObject) message.extraHashMap.get(path);
                        if (messageObject.getDocument() != null) {
                            fileType = 1;
                        } else {
                            fileType = 0;
                        }
                    } else {
                        messageObject = null;
                    }
                    if (fileType == 0) {
                        String md5 = Utilities.MD5(path) + "." + ImageLoader.getHttpUrlExtension(path, "file");
                        final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        Utilities.globalQueue.postRunnable(() -> {
                            final TLRPC.TL_photo photo = generatePhotoSizes(cacheFile.toString(), null);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (photo != null) {
                                    messageObject.messageOwner.media.photo = photo;
                                    messageObject.messageOwner.attachPath = cacheFile.toString();
                                    ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                    messages.add(messageObject.messageOwner);
                                    MessagesStorage.getInstance(currentAccount).putMessages(messages, false, true, false, 0);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateMessageMedia, messageObject.messageOwner);
                                    message.location = photo.sizes.get(photo.sizes.size() - 1).location;
                                    message.httpLocation = null;
                                    if (message.type == 4) {
                                        performSendDelayedMessage(message, message.messageObjects.indexOf(messageObject));
                                    } else {
                                        performSendDelayedMessage(message);
                                    }
                                } else {
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.e("can't load image " + path + " to file " + cacheFile.toString());
                                    }
                                    message.markAsError();
                                }
                            });
                        });
                    } else if (fileType == 1) {
                        String md5 = Utilities.MD5(path) + ".gif";
                        final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        Utilities.globalQueue.postRunnable(() -> {
                            final TLRPC.Document document = message.obj.getDocument();
                            if (document.thumb.location instanceof TLRPC.TL_fileLocationUnavailable) {
                                try {
                                    Bitmap bitmap = ImageLoader.loadBitmap(cacheFile.getAbsolutePath(), null, 90, 90, true);
                                    if (bitmap != null) {
                                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, message.sendEncryptedRequest != null);
                                        bitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    document.thumb = null;
                                    FileLog.e(e);
                                }
                                if (document.thumb == null) {
                                    document.thumb = new TLRPC.TL_photoSizeEmpty();
                                    document.thumb.type = "s";
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                message.httpLocation = null;
                                message.obj.messageOwner.attachPath = cacheFile.toString();
                                message.location = document.thumb.location;
                                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                messages.add(messageObject.messageOwner);
                                MessagesStorage.getInstance(currentAccount).putMessages(messages, false, true, false, 0);
                                performSendDelayedMessage(message);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateMessageMedia, message.obj.messageOwner);
                            });
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
                for (int a = 0; a < arr.size(); a++) {
                    arr.get(a).markAsError();
                }
                delayedMessages.remove(path);
            }
        }
    }

    private void revertEditingMessageObject(MessageObject object) {
        object.cancelEditing = true;
        object.messageOwner.media = object.previousMedia;
        object.messageOwner.message = object.previousCaption;
        object.messageOwner.entities = object.previousCaptionEntities;
        object.messageOwner.attachPath = object.previousAttachPath;
        object.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

        object.previousMedia = null;
        object.previousCaption = null;
        object.previousCaptionEntities = null;
        object.previousAttachPath = null;
        object.videoEditedInfo = null;
        object.type = -1;
        object.setType();
        object.caption = null;
        object.generateCaption();

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(object.messageOwner);
        MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);

        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(object);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, object.getDialogId(), arrayList);
    }

    public void cancelSendingMessage(MessageObject object) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(object);
        cancelSendingMessage(arrayList);
    }

    public void cancelSendingMessage(ArrayList<MessageObject> objects) {
        ArrayList<String> keysToRemove = new ArrayList<>();
        ArrayList<Integer> messageIds = new ArrayList<>();
        boolean enc = false;
        int channelId = 0;
        for (int c = 0; c < objects.size(); c++) {
            MessageObject object = objects.get(c);
            messageIds.add(object.getId());
            channelId = object.messageOwner.to_id.channel_id;
            TLRPC.Message sendingMessage = removeFromSendingMessages(object.getId());
            if (sendingMessage != null) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(sendingMessage.reqId, true);
            }

            for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
                ArrayList<DelayedMessage> messages = entry.getValue();
                for (int a = 0; a < messages.size(); a++) {
                    DelayedMessage message = messages.get(a);
                    if (message.type == 4) {
                        int index = -1;
                        MessageObject messageObject = null;
                        for (int b = 0; b < message.messageObjects.size(); b++) {
                            messageObject = message.messageObjects.get(b);
                            if (messageObject.getId() == object.getId()) {
                                index = b;
                                break;
                            }
                        }
                        if (index >= 0) {
                            message.messageObjects.remove(index);
                            message.messages.remove(index);
                            message.originalPaths.remove(index);
                            if (message.sendRequest != null) {
                                TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                                request.multi_media.remove(index);
                            } else {
                                TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                                request.messages.remove(index);
                                request.files.remove(index);
                            }
                            MediaController.getInstance().cancelVideoConvert(object);

                            String keyToRemove = (String) message.extraHashMap.get(messageObject);
                            if (keyToRemove != null) {
                                keysToRemove.add(keyToRemove);
                            }
                            if (message.messageObjects.isEmpty()) {
                                message.sendDelayedRequests();
                            } else {
                                if (message.finalGroupMessage == object.getId()) {
                                    MessageObject prevMessage = message.messageObjects.get(message.messageObjects.size() - 1);
                                    message.finalGroupMessage = prevMessage.getId();
                                    prevMessage.messageOwner.params.put("final", "1");

                                    TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                                    messagesRes.messages.add(prevMessage.messageOwner);
                                    MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, message.peer, -2, 0, false);

                                }
                                sendReadyToSendGroup(message, false, true);
                            }
                        }
                        break;
                    } else if (message.obj.getId() == object.getId()) {
                        messages.remove(a);
                        message.sendDelayedRequests();
                        MediaController.getInstance().cancelVideoConvert(message.obj);
                        if (messages.size() == 0) {
                            keysToRemove.add(entry.getKey());
                            if (message.sendEncryptedRequest != null) {
                                enc = true;
                            }
                        }
                        break;
                    }
                }
            }
        }
        for (int a = 0; a < keysToRemove.size(); a++) {
            String key = keysToRemove.get(a);
            if (key.startsWith("http")) {
                ImageLoader.getInstance().cancelLoadHttpFile(key);
            } else {
                FileLoader.getInstance(currentAccount).cancelUploadFile(key, enc);
            }
            stopVideoService(key);
            delayedMessages.remove(key);
        }
        if (objects.size() == 1 && objects.get(0).isEditing() && objects.get(0).previousMedia != null) {
            revertEditingMessageObject(objects.get(0));
        } else {
            MessagesController.getInstance(currentAccount).deleteMessages(messageIds, null, null, channelId, false);
        }
    }

    public boolean retrySendMessage(MessageObject messageObject, boolean unsent) {
        if (messageObject.getId() >= 0) {
            if (messageObject.isEditing()) {
                editMessageMedia(messageObject, null, null, null, null, null, true);
            }
            return false;
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
            int enc_id = (int) (messageObject.getDialogId() >> 32);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(enc_id);
            if (encryptedChat == null) {
                MessagesStorage.getInstance(currentAccount).markMessageAsSendError(messageObject.messageOwner);
                messageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, messageObject.getId());
                processSentMessage(messageObject.getId());
                return false;
            }
            if (messageObject.messageOwner.random_id == 0) {
                messageObject.messageOwner.random_id = getNextRandomId();
            }
            if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                SecretChatHelper.getInstance(currentAccount).sendTTLMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                SecretChatHelper.getInstance(currentAccount).sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                SecretChatHelper.getInstance(currentAccount).sendClearHistoryMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                SecretChatHelper.getInstance(currentAccount).sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
                SecretChatHelper.getInstance(currentAccount).sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                SecretChatHelper.getInstance(currentAccount).sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionTyping) {

            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionResend) {

            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
                SecretChatHelper.getInstance(currentAccount).sendCommitKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
                SecretChatHelper.getInstance(currentAccount).sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                SecretChatHelper.getInstance(currentAccount).sendRequestKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
                SecretChatHelper.getInstance(currentAccount).sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNoop) {
                SecretChatHelper.getInstance(currentAccount).sendNoopMessage(encryptedChat, messageObject.messageOwner);
            }
            return true;
        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser((int) messageObject.getDialogId());
            sendScreenshotMessage(user, messageObject.messageOwner.reply_to_msg_id, messageObject.messageOwner);
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
        if (messageObject.messageOwner.media != null && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                sendMessage((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, null, messageObject.messageOwner.media.ttl_seconds);
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                sendMessage((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, null, messageObject.messageOwner.media.ttl_seconds);
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                sendMessage(messageObject.messageOwner.media, did, messageObject.replyMessageObject, null, null);
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact_old2();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                sendMessage(user, did, messageObject.replyMessageObject, null, null);
            } else if ((int) did != 0) {
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                sendMessage(arrayList, did);
            }
        } else if (messageObject.messageOwner.message != null) {
            TLRPC.WebPage webPage = null;
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                webPage = messageObject.messageOwner.media.webpage;
            }
            ArrayList<TLRPC.MessageEntity> entities;
            if (messageObject.messageOwner.entities != null && !messageObject.messageOwner.entities.isEmpty()) {
                entities = new ArrayList<>();
                for (int a = 0; a < messageObject.messageOwner.entities.size(); a++) {
                    TLRPC.MessageEntity entity = messageObject.messageOwner.entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityBold ||
                            entity instanceof TLRPC.TL_messageEntityItalic ||
                            entity instanceof TLRPC.TL_messageEntityPre ||
                            entity instanceof TLRPC.TL_messageEntityCode ||
                            entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        entities.add(entity);
                    }
                }
            } else {
                entities = null;
            }
            sendMessage(messageObject.messageOwner.message, did, messageObject.replyMessageObject, webPage, true, entities, null, null);
        } else if ((int) did != 0) {
            ArrayList<MessageObject> arrayList = new ArrayList<>();
            arrayList.add(messageObject);
            sendMessage(arrayList, did);
        }
    }

    public void sendScreenshotMessage(TLRPC.User user, int messageId, TLRPC.Message resendMessage) {
        if (user == null || messageId == 0 || user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return;
        }

        TLRPC.TL_messages_sendScreenshotNotification req = new TLRPC.TL_messages_sendScreenshotNotification();
        req.peer = new TLRPC.TL_inputPeerUser();
        req.peer.access_hash = user.access_hash;
        req.peer.user_id = user.id;
        TLRPC.Message message;
        if (resendMessage != null) {
            message = resendMessage;
            req.reply_to_msg_id = messageId;
            req.random_id = resendMessage.random_id;
        } else {
            message = new TLRPC.TL_messageService();
            message.random_id = getNextRandomId();
            message.dialog_id = user.id;
            message.unread = true;
            message.out = true;
            message.local_id = message.id = UserConfig.getInstance(currentAccount).getNewMessageId();
            message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.flags |= 256;
            message.flags |= 8;
            message.reply_to_msg_id = messageId;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = user.id;
            message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            message.action = new TLRPC.TL_messageActionScreenshotTaken();
            UserConfig.getInstance(currentAccount).saveConfig(false);
        }
        req.random_id = message.random_id;

        MessageObject newMsgObj = new MessageObject(currentAccount, message, false);
        newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        ArrayList<MessageObject> objArr = new ArrayList<>();
        objArr.add(newMsgObj);
        MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(message.dialog_id, objArr);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(message);
        MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);

        performSendMessageRequest(req, newMsgObj, null);
    }

    public void sendSticker(TLRPC.Document document, long peer, MessageObject replyingMessageObject) {
        if (document == null) {
            return;
        }
        if ((int) peer == 0) {
            int high_id = (int) (peer >> 32);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
            if (encryptedChat == null) {
                return;
            }
            TLRPC.TL_document newDocument = new TLRPC.TL_document();
            newDocument.id = document.id;
            newDocument.access_hash = document.access_hash;
            newDocument.date = document.date;
            newDocument.mime_type = document.mime_type;
            newDocument.size = document.size;
            newDocument.dc_id = document.dc_id;
            newDocument.attributes = new ArrayList<>(document.attributes);
            if (newDocument.mime_type == null) {
                newDocument.mime_type = "";
            }
            if (document.thumb instanceof TLRPC.TL_photoSize) {
                File file = FileLoader.getPathToAttach(document.thumb, true);
                if (file.exists()) {
                    try {
                        int len = (int) file.length();
                        byte[] arr = new byte[(int) file.length()];
                        RandomAccessFile reader = new RandomAccessFile(file, "r");
                        reader.readFully(arr);

                        newDocument.thumb = new TLRPC.TL_photoCachedSize();
                        newDocument.thumb.location = document.thumb.location;
                        newDocument.thumb.size = document.thumb.size;
                        newDocument.thumb.w = document.thumb.w;
                        newDocument.thumb.h = document.thumb.h;
                        newDocument.thumb.type = document.thumb.type;
                        newDocument.thumb.bytes = arr;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            if (newDocument.thumb == null) {
                newDocument.thumb = new TLRPC.TL_photoSizeEmpty();
                newDocument.thumb.type = "s";
            }
            document = newDocument;
        }
        if (document instanceof TLRPC.TL_document) {
            sendMessage((TLRPC.TL_document) document, null, null, peer, replyingMessageObject, null, null, null, null, 0);
        }
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int lower_id = (int) peer;
        int sendResult = 0;
        if (lower_id != 0) {
            final TLRPC.Peer to_id = MessagesController.getInstance(currentAccount).getPeer((int) peer);
            boolean isMegagroup = false;
            boolean isSignature = false;
            boolean canSendStickers = true;
            boolean canSendMedia = true;
            boolean canSendPreview = true;
            if (lower_id > 0) {
                TLRPC.User sendToUser = MessagesController.getInstance(currentAccount).getUser(lower_id);
                if (sendToUser == null) {
                    return 0;
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                if (ChatObject.isChannel(chat)) {
                    isMegagroup = chat.megagroup;
                    isSignature = chat.signatures;
                    if (chat.banned_rights != null) {
                        canSendStickers = !chat.banned_rights.send_stickers;
                        canSendMedia = !chat.banned_rights.send_media;
                        canSendPreview = !chat.banned_rights.embed_links;
                    }
                }
            }

            LongSparseArray<Long> groupsMap = new LongSparseArray<>();
            ArrayList<MessageObject> objArr = new ArrayList<>();
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            ArrayList<Long> randomIds = new ArrayList<>();
            ArrayList<Integer> ids = new ArrayList<>();
            LongSparseArray<TLRPC.Message> messagesByRandomIds = new LongSparseArray<>();
            TLRPC.InputPeer inputPeer = MessagesController.getInstance(currentAccount).getInputPeer(lower_id);
            long lastDialogId = 0;
            int myId = UserConfig.getInstance(currentAccount).getClientUserId();
            final boolean toMyself = peer == myId;
            long lastGroupedId;
            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview()) {
                    continue;
                }
                if (!canSendStickers && (msgObj.isSticker() || msgObj.isGif() || msgObj.isGame())) {
                    if (sendResult == 0) {
                        sendResult = 1;
                    }
                    continue;
                } else if (!canSendMedia && (msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaDocument)) {
                    if (sendResult == 0) {
                        sendResult = 2;
                    }
                    continue;
                }

                boolean groupedIdChanged = false;
                final TLRPC.Message newMsg = new TLRPC.TL_message();
                boolean forwardFromSaved = msgObj.getDialogId() == myId && msgObj.messageOwner.from_id == UserConfig.getInstance(currentAccount).getClientUserId();
                if (msgObj.isForwarded()) {
                    newMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
                    newMsg.fwd_from.flags = msgObj.messageOwner.fwd_from.flags;
                    newMsg.fwd_from.from_id = msgObj.messageOwner.fwd_from.from_id;
                    newMsg.fwd_from.date = msgObj.messageOwner.fwd_from.date;
                    newMsg.fwd_from.channel_id = msgObj.messageOwner.fwd_from.channel_id;
                    newMsg.fwd_from.channel_post = msgObj.messageOwner.fwd_from.channel_post;
                    newMsg.fwd_from.post_author = msgObj.messageOwner.fwd_from.post_author;
                    newMsg.flags = TLRPC.MESSAGE_FLAG_FWD;
                } else if (!forwardFromSaved) { //if (!toMyself || !msgObj.isOutOwner())
                    newMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
                    newMsg.fwd_from.channel_post = msgObj.getId();
                    newMsg.fwd_from.flags |= 4;
                    if (msgObj.isFromUser()) {
                        newMsg.fwd_from.from_id = msgObj.messageOwner.from_id;
                        newMsg.fwd_from.flags |= 1;
                    } else {
                        newMsg.fwd_from.channel_id = msgObj.messageOwner.to_id.channel_id;
                        newMsg.fwd_from.flags |= 2;
                        if (msgObj.messageOwner.post && msgObj.messageOwner.from_id > 0) {
                            newMsg.fwd_from.from_id = msgObj.messageOwner.from_id;
                            newMsg.fwd_from.flags |= 1;
                        }
                    }
                    if (msgObj.messageOwner.post_author != null) {
                        newMsg.fwd_from.post_author = msgObj.messageOwner.post_author;
                        newMsg.fwd_from.flags |= 8;
                    } else if (!msgObj.isOutOwner() && msgObj.messageOwner.from_id > 0 && msgObj.messageOwner.post) {
                        TLRPC.User signUser = MessagesController.getInstance(currentAccount).getUser(msgObj.messageOwner.from_id);
                        if (signUser != null) {
                            newMsg.fwd_from.post_author = ContactsController.formatName(signUser.first_name, signUser.last_name);
                            newMsg.fwd_from.flags |= 8;
                        }
                    }
                    newMsg.date = msgObj.messageOwner.date;
                    newMsg.flags = TLRPC.MESSAGE_FLAG_FWD;
                }
                if (peer == myId && newMsg.fwd_from != null) {
                    newMsg.fwd_from.flags |= 16;
                    newMsg.fwd_from.saved_from_msg_id = msgObj.getId();
                    newMsg.fwd_from.saved_from_peer = msgObj.messageOwner.to_id;
                }
                if (!canSendPreview && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    newMsg.media = new TLRPC.TL_messageMediaEmpty();
                } else {
                    newMsg.media = msgObj.messageOwner.media;
                }
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
                newMsg.local_id = newMsg.id = UserConfig.getInstance(currentAccount).getNewMessageId();
                newMsg.out = true;
                if ((lastGroupedId = msgObj.messageOwner.grouped_id) != 0) {
                    Long gId = groupsMap.get(msgObj.messageOwner.grouped_id);
                    if (gId == null) {
                        gId = Utilities.random.nextLong();
                        groupsMap.put(msgObj.messageOwner.grouped_id, gId);
                    }
                    newMsg.grouped_id = gId;
                    newMsg.flags |= 131072;
                }
                if (a != messages.size() - 1) {
                    MessageObject next = messages.get(a + 1);
                    if (next.messageOwner.grouped_id != msgObj.messageOwner.grouped_id) {
                        groupedIdChanged = true;
                    }
                }
                if (to_id.channel_id != 0 && !isMegagroup) {
                    newMsg.from_id = isSignature ? UserConfig.getInstance(currentAccount).getClientUserId() : -to_id.channel_id;
                    newMsg.post = true;
                } else {
                    newMsg.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                }
                if (newMsg.random_id == 0) {
                    newMsg.random_id = getNextRandomId();
                }
                randomIds.add(newMsg.random_id);
                messagesByRandomIds.put(newMsg.random_id, newMsg);
                ids.add(newMsg.fwd_msg_id);
                newMsg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                if (inputPeer instanceof TLRPC.TL_inputPeerChannel && !isMegagroup) {
                    newMsg.views = 1;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                } else {
                    if ((msgObj.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                        newMsg.views = msgObj.messageOwner.views;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                    }
                    newMsg.unread = true;
                }
                newMsg.dialog_id = peer;
                newMsg.to_id = to_id;
                if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
                    if (inputPeer instanceof TLRPC.TL_inputPeerChannel && msgObj.getChannelId() != 0) {
                        newMsg.media_unread = msgObj.isContentUnread();
                    } else {
                        newMsg.media_unread = true;
                    }
                }
                if (msgObj.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                    newMsg.ttl = -msgObj.messageOwner.to_id.channel_id;
                }
                MessageObject newMsgObj = new MessageObject(currentAccount, newMsg, true);
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
                objArr.add(newMsgObj);
                arr.add(newMsg);

                putToSendingMessages(newMsg);
                boolean differentDialog = false;

                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("forward message user_id = " + inputPeer.user_id + " chat_id = " + inputPeer.chat_id + " channel_id = " + inputPeer.channel_id + " access_hash = " + inputPeer.access_hash);
                }

                if (groupedIdChanged && arr.size() > 0 || arr.size() == 100 || a == messages.size() - 1 || a != messages.size() - 1 && messages.get(a + 1).getDialogId() != msgObj.getDialogId()) {
                    MessagesStorage.getInstance(currentAccount).putMessages(new ArrayList<>(arr), false, true, false, 0);
                    MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(peer, objArr);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                    UserConfig.getInstance(currentAccount).saveConfig(false);

                    final TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
                    req.to_peer = inputPeer;
                    req.grouped = lastGroupedId != 0;
                    if (req.to_peer instanceof TLRPC.TL_inputPeerChannel) {
                        req.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                    }
                    if (msgObj.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(msgObj.messageOwner.to_id.channel_id);
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
                    final LongSparseArray<TLRPC.Message> messagesByRandomIdsFinal = messagesByRandomIds;
                    final boolean isMegagroupFinal = isMegagroup;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                        if (error == null) {
                            SparseLongArray newMessagesByIds = new SparseLongArray();
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            for (int a1 = 0; a1 < updates.updates.size(); a1++) {
                                TLRPC.Update update = updates.updates.get(a1);
                                if (update instanceof TLRPC.TL_updateMessageID) {
                                    TLRPC.TL_updateMessageID updateMessageID = (TLRPC.TL_updateMessageID) update;
                                    newMessagesByIds.put(updateMessageID.id, updateMessageID.random_id);
                                    updates.updates.remove(a1);
                                    a1--;
                                }
                            }
                            Integer value = MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.get(peer);
                            if (value == null) {
                                value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, peer);
                                MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.put(peer, value);
                            }

                            int sentCount = 0;
                            for (int a1 = 0; a1 < updates.updates.size(); a1++) {
                                TLRPC.Update update = updates.updates.get(a1);
                                if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateNewChannelMessage) {
                                    updates.updates.remove(a1);
                                    a1--;
                                    final TLRPC.Message message;
                                    if (update instanceof TLRPC.TL_updateNewMessage) {
                                        TLRPC.TL_updateNewMessage updateNewMessage = (TLRPC.TL_updateNewMessage) update;
                                        message = updateNewMessage.message;
                                        MessagesController.getInstance(currentAccount).processNewDifferenceParams(-1, updateNewMessage.pts, -1, updateNewMessage.pts_count);
                                    } else {
                                        TLRPC.TL_updateNewChannelMessage updateNewChannelMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                        message = updateNewChannelMessage.message;
                                        MessagesController.getInstance(currentAccount).processNewChannelDifferenceParams(updateNewChannelMessage.pts, updateNewChannelMessage.pts_count, message.to_id.channel_id);
                                        if (isMegagroupFinal) {
                                            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                    ImageLoader.saveMessageThumbs(message);
                                    message.unread = value < message.id;
                                    if (toMyself) {
                                        message.out = true;
                                        message.unread = false;
                                        message.media_unread = false;
                                    }

                                    long random_id = newMessagesByIds.get(message.id);
                                    if (random_id != 0) {
                                        final TLRPC.Message newMsgObj1 = messagesByRandomIdsFinal.get(random_id);
                                        if (newMsgObj1 == null) {
                                            continue;
                                        }
                                        int index = newMsgObjArr.indexOf(newMsgObj1);
                                        if (index == -1) {
                                            continue;
                                        }
                                        MessageObject msgObj1 = newMsgArr.get(index);
                                        newMsgObjArr.remove(index);
                                        newMsgArr.remove(index);
                                        final int oldId = newMsgObj1.id;
                                        final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                                        sentMessages.add(message);
                                        newMsgObj1.id = message.id;
                                        sentCount++;
                                        updateMediaPaths(msgObj1, message, null, true);
                                        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                                            MessagesStorage.getInstance(currentAccount).updateMessageStateAndId(newMsgObj1.random_id, oldId, newMsgObj1.id, 0, false, to_id.channel_id);
                                            MessagesStorage.getInstance(currentAccount).putMessages(sentMessages, true, false, false, 0);
                                            AndroidUtilities.runOnUIThread(() -> {
                                                newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                DataQuery.getInstance(currentAccount).increasePeerRaiting(peer);
                                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, message.id, message, peer, 0L);
                                                processSentMessage(oldId);
                                                removeFromSendingMessages(oldId);
                                            });
                                        });
                                    }
                                }
                            }
                            if (!updates.updates.isEmpty()) {
                                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                            }
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, sentCount);
                        } else {
                            AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, null, req));
                        }
                        for (int a1 = 0; a1 < newMsgObjArr.size(); a1++) {
                            final TLRPC.Message newMsgObj1 = newMsgObjArr.get(a1);
                            MessagesStorage.getInstance(currentAccount).markMessageAsSendError(newMsgObj1);
                            AndroidUtilities.runOnUIThread(() -> {
                                newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, newMsgObj1.id);
                                processSentMessage(newMsgObj1.id);
                                removeFromSendingMessages(newMsgObj1.id);
                            });
                        }
                    }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);

                    if (a != messages.size() - 1) {
                        objArr = new ArrayList<>();
                        arr = new ArrayList<>();
                        randomIds = new ArrayList<>();
                        ids = new ArrayList<>();
                        messagesByRandomIds = new LongSparseArray<>();
                    }
                }
            }
        } else {
            for (int a = 0; a < messages.size(); a++) {
                processForwardFromMyName(messages.get(a), peer);
            }
        }
        return sendResult;
    }

    private void writePreviousMessageData(TLRPC.Message message, SerializedData data) {
        message.media.serializeToStream(data);
        data.writeString(message.message != null ? message.message : "");
        data.writeString(message.attachPath != null ? message.attachPath : "");
        int count;
        data.writeInt32(count = message.entities.size());
        for (int a = 0; a < count; a++) {
            message.entities.get(a).serializeToStream(data);
        }
    }

    private void editMessageMedia(MessageObject messageObject, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, HashMap<String, String> params, boolean retry) {
        if (messageObject == null) {
            return;
        }

        TLRPC.Message newMsg = messageObject.messageOwner;
        messageObject.cancelEditing = false;

        try {
            int type = -1;
            DelayedMessage delayedMessage = null;
            long peer = messageObject.getDialogId();

            if (retry) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    photo = (TLRPC.TL_photo) messageObject.messageOwner.media.photo;
                    type = 2;
                } else {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                    if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else {
                        type = 7;
                    }
                    videoEditedInfo = messageObject.videoEditedInfo;
                }
                params = newMsg.params;
                messageObject.editingMessage = newMsg.message;
                messageObject.editingMessageEntities = newMsg.entities;
                path = newMsg.attachPath;
            } else {
                messageObject.previousMedia = newMsg.media;
                messageObject.previousCaption = newMsg.message;
                messageObject.previousCaptionEntities = newMsg.entities;
                messageObject.previousAttachPath = newMsg.attachPath;

                SerializedData serializedDataCalc = new SerializedData(true);
                writePreviousMessageData(newMsg, serializedDataCalc);
                SerializedData prevMessageData = new SerializedData(serializedDataCalc.length());
                writePreviousMessageData(newMsg, prevMessageData);
                if (params == null) {
                    params = new HashMap<>();
                }
                params.put("prevMedia", Base64.encodeToString(prevMessageData.toByteArray(), Base64.DEFAULT));
                prevMessageData.cleanup();

                if (photo != null) {
                    newMsg.media = new TLRPC.TL_messageMediaPhoto();
                    newMsg.media.flags |= 3;
                    newMsg.media.photo = photo;
                    type = 2;
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        newMsg.attachPath = path;
                    } else {
                        TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                        newMsg.attachPath = FileLoader.getPathToAttach(location1, true).toString();
                    }
                } else if (document != null) {
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.flags |= 3;
                    newMsg.media.document = document;
                    if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else {
                        type = 7;
                    }
                    if (videoEditedInfo != null) {
                        String ve = videoEditedInfo.getString();
                        params.put("ve", ve);
                    }
                    newMsg.attachPath = path;
                }

                newMsg.params = params;
                newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_EDITING;
            }
            if (newMsg.attachPath == null) {
                newMsg.attachPath = "";
            }
            newMsg.local_id = 0;
            if ((messageObject.type == 3 || videoEditedInfo != null || messageObject.type == 2) && !TextUtils.isEmpty(newMsg.attachPath)) {
                messageObject.attachPathExists = true;
            }
            if (messageObject.videoEditedInfo != null && videoEditedInfo == null) {
                videoEditedInfo = messageObject.videoEditedInfo;
            }

            if (!retry) {
                if (messageObject.editingMessage != null) {
                    newMsg.message = messageObject.editingMessage.toString();
                    if (messageObject.editingMessageEntities != null) {
                        newMsg.entities = messageObject.editingMessageEntities;
                    } else {
                        CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                        ArrayList<TLRPC.MessageEntity> entities = DataQuery.getInstance(currentAccount).getEntities(message);
                        if (entities != null && !entities.isEmpty()) {
                            newMsg.entities = entities;
                        }
                    }
                    messageObject.caption = null;
                    messageObject.generateCaption();
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);

                messageObject.type = -1;
                messageObject.setType();
                messageObject.createMessageSendInfo();
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, peer, arrayList);
            }

            String originalPath = null;
            if (params != null && params.containsKey("originalPath")) {
                originalPath = params.get("originalPath");
            }

            if (type >= 1 && type <= 3 || type >= 5 && type <= 8) {
                TLRPC.InputMedia inputMedia = null;
                if (type == 2) {
                    if (photo.access_hash == 0) {
                        inputMedia = new TLRPC.TL_inputMediaUploadedPhoto();
                        if (params != null) {
                            String masks = params.get("masks");
                            if (masks != null) {
                                SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                                int count = serializedData.readInt32(false);
                                for (int a = 0; a < count; a++) {
                                    inputMedia.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                                }
                                inputMedia.flags |= 1;
                                serializedData.cleanup();
                            }
                        }
                        if (delayedMessage == null) {
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.type = 0;
                            delayedMessage.obj = messageObject;
                            delayedMessage.originalPath = originalPath;
                        }
                        if (path != null && path.length() > 0 && path.startsWith("http")) {
                            delayedMessage.httpLocation = path;
                        } else {
                            delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                        }
                    } else {
                        TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                        media.id = new TLRPC.TL_inputPhoto();
                        media.id.id = photo.id;
                        media.id.access_hash = photo.access_hash;
                        inputMedia = media;
                    }
                } else if (type == 3) {
                    if (document.access_hash == 0) {
                        inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                        inputMedia.mime_type = document.mime_type;
                        inputMedia.attributes = document.attributes;
                        if (!messageObject.isGif() && (videoEditedInfo == null || !videoEditedInfo.muted)) {
                            inputMedia.nosound_video = true;
                        }
                        if (delayedMessage == null) {
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.type = 1;
                            delayedMessage.obj = messageObject;
                            delayedMessage.originalPath = originalPath;
                        }
                        delayedMessage.location = document.thumb.location;
                        delayedMessage.videoEditedInfo = videoEditedInfo;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        inputMedia = media;
                    }
                } else if (type == 7) {
                    if (document.access_hash == 0) {
                        if (originalPath != null && originalPath.length() > 0 && originalPath.startsWith("http") && params != null) {
                            inputMedia = new TLRPC.TL_inputMediaGifExternal();
                            String args[] = params.get("url").split("\\|");
                            if (args.length == 2) {
                                ((TLRPC.TL_inputMediaGifExternal) inputMedia).url = args[0];
                                inputMedia.q = args[1];
                            }
                        } else {
                            inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.type = 2;
                            delayedMessage.obj = messageObject;
                            delayedMessage.location = document.thumb.location;
                        }
                        inputMedia.mime_type = document.mime_type;
                        inputMedia.attributes = document.attributes;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        inputMedia = media;
                    }
                }

                TLObject reqSend;

                TLRPC.TL_messages_editMessage request = new TLRPC.TL_messages_editMessage();
                request.id = messageObject.getId();
                request.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) peer);
                request.flags |= 16384;
                request.media = inputMedia;

                if (messageObject.editingMessage != null) {
                    request.message = messageObject.editingMessage.toString();
                    request.flags |= 2048;
                    if (messageObject.editingMessageEntities != null) {
                        request.entities = messageObject.editingMessageEntities;
                        request.flags |= 8;
                    } else {
                        CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                        ArrayList<TLRPC.MessageEntity> entities = DataQuery.getInstance(currentAccount).getEntities(message);
                        if (entities != null && !entities.isEmpty()) {
                            request.entities = entities;
                            request.flags |= 8;
                        }
                    }
                    messageObject.editingMessage = null;
                    messageObject.editingMessageEntities = null;
                }

                if (delayedMessage != null) {
                    delayedMessage.sendRequest = request;
                }
                reqSend = request;

                if (type == 1) {
                    performSendMessageRequest(reqSend, messageObject, null);
                } else if (type == 2) {
                    if (photo.access_hash == 0) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, null, null, true);
                    }
                } else if (type == 3) {
                    if (document.access_hash == 0) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, null);
                    }
                } else if (type == 6) {
                    performSendMessageRequest(reqSend, messageObject, null);
                } else if (type == 7) {
                    if (document.access_hash == 0 && delayedMessage != null) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath);
                    }
                } else if (type == 8) {
                    if (document.access_hash == 0) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, null);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            revertEditingMessageObject(messageObject);
        }
    }

    public int editMessage(MessageObject messageObject, String message, boolean searchLinks, final BaseFragment fragment, ArrayList<TLRPC.MessageEntity> entities, final Runnable callback) {
        if (fragment == null || fragment.getParentActivity() == null || callback == null) {
            return 0;
        }

        final TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) messageObject.getDialogId());
        req.message = message;
        req.flags |= 2048;
        req.id = messageObject.getId();
        req.no_webpage = !searchLinks;
        if (entities != null) {
            req.entities = entities;
            req.flags |= 8;
        }
        return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, req));
            }
            AndroidUtilities.runOnUIThread(callback);
        });
    }

    private void sendLocation(Location location) {
        TLRPC.TL_messageMediaGeo mediaGeo = new TLRPC.TL_messageMediaGeo();
        mediaGeo.geo = new TLRPC.TL_geoPoint();
        mediaGeo.geo.lat = AndroidUtilities.fixLocationCoord(location.getLatitude());
        mediaGeo.geo._long = AndroidUtilities.fixLocationCoord(location.getLongitude());
        for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
            MessageObject messageObject = entry.getValue();
            sendMessage(mediaGeo, messageObject.getDialogId(), messageObject, null, null);
        }
    }

    public void sendCurrentLocation(final MessageObject messageObject, final TLRPC.KeyboardButton button) {
        if (messageObject == null || button == null) {
            return;
        }
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "_" + Utilities.bytesToHex(button.data) + "_" + (button instanceof TLRPC.TL_keyboardButtonGame ? "1" : "0");
        waitingForLocation.put(key, messageObject);
        locationProvider.start();
    }

    public boolean isSendingCurrentLocation(MessageObject messageObject, TLRPC.KeyboardButton button) {
        if (messageObject == null || button == null) {
            return false;
        }
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "_" + Utilities.bytesToHex(button.data) + "_" + (button instanceof TLRPC.TL_keyboardButtonGame ? "1" : "0");
        return waitingForLocation.containsKey(key);
    }

    public void sendNotificationCallback(final long dialogId, final int msgId, final byte[] data) {
        AndroidUtilities.runOnUIThread(() -> {
            int lowerId = (int) dialogId;
            final String key = dialogId + "_" + msgId + "_" + Utilities.bytesToHex(data) + "_" + 0;
            waitingForCallback.put(key, true);

            if (lowerId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lowerId);
                if (user == null) {
                    user = MessagesStorage.getInstance(currentAccount).getUserSync(lowerId);
                    if (user != null) {
                        MessagesController.getInstance(currentAccount).putUser(user, true);
                    }
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
                if (chat == null) {
                    chat = MessagesStorage.getInstance(currentAccount).getChatSync(-lowerId);
                    if (chat != null) {
                        MessagesController.getInstance(currentAccount).putChat(chat, true);
                    }
                }
            }

            TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lowerId);
            req.msg_id = msgId;
            req.game = false;
            if (data != null) {
                req.flags |= 1;
                req.data = data;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> waitingForCallback.remove(key)), ConnectionsManager.RequestFlagFailOnServerErrors);
            MessagesController.getInstance(currentAccount).markDialogAsRead(dialogId, msgId, msgId, 0, false, 0, true);
        });
    }

    public void sendCallback(final boolean cache, final MessageObject messageObject, final TLRPC.KeyboardButton button, final ChatActivity parentFragment) {
        if (messageObject == null || button == null || parentFragment == null) {
            return;
        }
        final boolean cacheFinal;
        int type;
        if (button instanceof TLRPC.TL_keyboardButtonGame) {
            cacheFinal = false;
            type = 1;
        } else {
            cacheFinal = cache;
            if (button instanceof TLRPC.TL_keyboardButtonBuy) {
                type = 2;
            } else {
                type = 0;
            }
        }
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "_" + Utilities.bytesToHex(button.data) + "_" + type;
        waitingForCallback.put(key, true);

        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            waitingForCallback.remove(key);
            if (cacheFinal && response == null) {
                sendCallback(false, messageObject, button, parentFragment);
            } else if (response != null) {
                if (button instanceof TLRPC.TL_keyboardButtonBuy) {
                    if (response instanceof TLRPC.TL_payments_paymentForm) {
                        final TLRPC.TL_payments_paymentForm form = (TLRPC.TL_payments_paymentForm) response;
                        MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                        parentFragment.presentFragment(new PaymentFormActivity(form, messageObject));
                    } else if (response instanceof TLRPC.TL_payments_paymentReceipt) {
                        parentFragment.presentFragment(new PaymentFormActivity(messageObject, (TLRPC.TL_payments_paymentReceipt) response));
                    }
                } else {
                    TLRPC.TL_messages_botCallbackAnswer res = (TLRPC.TL_messages_botCallbackAnswer) response;
                    if (!cacheFinal && res.cache_time != 0) {
                        MessagesStorage.getInstance(currentAccount).saveBotCache(key, res);
                    }
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
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                                if (user != null) {
                                    name = ContactsController.formatName(user.first_name, user.last_name);
                                }
                            } else {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
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
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                        boolean verified = user != null && user.verified;
                        if (button instanceof TLRPC.TL_keyboardButtonGame) {
                            TLRPC.TL_game game = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame ? messageObject.messageOwner.media.game : null;
                            if (game == null) {
                                return;
                            }
                            parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && MessagesController.getNotificationsSettings(currentAccount).getBoolean("askgame_" + uid, true), uid);
                        } else {
                            parentFragment.showOpenUrlAlert(res.url, false);
                        }
                    }
                }
            }
        });
        if (cacheFinal) {
            MessagesStorage.getInstance(currentAccount).getBotCache(key, requestDelegate);
        } else {
            if (button instanceof TLRPC.TL_keyboardButtonBuy) {
                if ((messageObject.messageOwner.media.flags & 4) == 0) {
                    TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                    req.msg_id = messageObject.getId();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                } else {
                    TLRPC.TL_payments_getPaymentReceipt req = new TLRPC.TL_payments_getPaymentReceipt();
                    req.msg_id = messageObject.messageOwner.media.receipt_msg_id;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            } else {
                TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) messageObject.getDialogId());
                req.msg_id = messageObject.getId();
                req.game = button instanceof TLRPC.TL_keyboardButtonGame;
                if (button.data != null) {
                    req.flags |= 1;
                    req.data = button.data;
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        }
    }

    public boolean isSendingCallback(MessageObject messageObject, TLRPC.KeyboardButton button) {
        if (messageObject == null || button == null) {
            return false;
        }
        int type;
        if (button instanceof TLRPC.TL_keyboardButtonGame) {
            type = 1;
        } else if (button instanceof TLRPC.TL_keyboardButtonBuy) {
            type = 2;
        } else {
            type = 0;
        }
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "_" + Utilities.bytesToHex(button.data) + "_" + type;
        return waitingForCallback.containsKey(key);
    }

    public void sendGame(TLRPC.InputPeer peer, TLRPC.TL_inputMediaGame game, long random_id, final long taskId) {
        if (peer == null || game == null) {
            return;
        }
        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
        request.peer = peer;
        if (request.peer instanceof TLRPC.TL_inputPeerChannel) {
            request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer.channel_id, false);
        }
        request.random_id = random_id != 0 ? random_id : getNextRandomId();
        request.message = "";
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
                FileLog.e(e);
            }
            newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
            if (error == null) {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
            }
            if (newTaskId != 0) {
                MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
            }
        });
    }

    public void sendMessage(MessageObject retryMessageObject) {
        sendMessage(null, null, null, null, null, null, null, null, retryMessageObject.getDialogId(), retryMessageObject.messageOwner.attachPath, null, null, true, retryMessageObject, null, retryMessageObject.messageOwner.reply_markup, retryMessageObject.messageOwner.params, 0);
    }

    public void sendMessage(TLRPC.User user, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, null, null, null, user, null, null, peer, null, reply_to_msg, null, true, null, null, replyMarkup, params, 0);
    }

    public void sendMessage(TLRPC.TL_document document, VideoEditedInfo videoEditedInfo, String path, long peer, MessageObject reply_to_msg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, int ttl) {
        sendMessage(null, caption, null, null, videoEditedInfo, null, document, null, peer, path, reply_to_msg, null, true, null, entities, replyMarkup, params, ttl);
    }

    public void sendMessage(String message, long peer, MessageObject reply_to_msg, TLRPC.WebPage webPage, boolean searchLinks, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(message, null, null, null, null, null, null, null, peer, null, reply_to_msg, webPage, searchLinks, null, entities, replyMarkup, params, 0);
    }

    public void sendMessage(TLRPC.MessageMedia location, long peer, MessageObject reply_to_msg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, location, null, null, null, null, null, peer, null, reply_to_msg, null, true, null, null, replyMarkup, params, 0);
    }

    public void sendMessage(TLRPC.TL_game game, long peer, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params) {
        sendMessage(null, null, null, null, null, null, null, game, peer, null, null, null, true, null, null, replyMarkup, params, 0);
    }

    public void sendMessage(TLRPC.TL_photo photo, String path, long peer, MessageObject reply_to_msg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, int ttl) {
        sendMessage(null, caption, null, photo, null, null, null, null, peer, path, reply_to_msg, null, true, null, entities, replyMarkup, params, ttl);
    }

    private void sendMessage(String message, String caption, TLRPC.MessageMedia location, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_game game, long peer, String path, MessageObject reply_to_msg, TLRPC.WebPage webPage, boolean searchLinks, MessageObject retryMessageObject, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, int ttl) {
        if (user != null && user.phone == null) {
            return;
        }
        if (peer == 0) {
            return;
        }
        if (message == null && caption == null) {
            caption = "";
        }

        String originalPath = null;
        if (params != null && params.containsKey("originalPath")) {
            originalPath = params.get("originalPath");
        }

        TLRPC.Message newMsg = null;
        MessageObject newMsgObj = null;
        DelayedMessage delayedMessage = null;
        int type = -1;
        int lower_id = (int) peer;
        int high_id = (int) (peer >> 32);
        boolean isChannel = false;
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = lower_id != 0 ? MessagesController.getInstance(currentAccount).getInputPeer(lower_id) : null;
        ArrayList<TLRPC.InputUser> sendToPeers = null;
        if (lower_id == 0) {
            encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
            if (encryptedChat == null) {
                if (retryMessageObject != null) {
                    MessagesStorage.getInstance(currentAccount).markMessageAsSendError(retryMessageObject.messageOwner);
                    retryMessageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, retryMessageObject.getId());
                    processSentMessage(retryMessageObject.getId());
                }
                return;
            }
        } else if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(sendToPeer.channel_id);
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
                    } else if (retryMessageObject.type == 3 || retryMessageObject.type == 5 || videoEditedInfo != null) {
                        type = 3;
                        document = (TLRPC.TL_document) newMsg.media.document;
                    } else if (retryMessageObject.type == 12) {
                        user = new TLRPC.TL_userRequest_old2();
                        user.phone = newMsg.media.phone_number;
                        user.first_name = newMsg.media.first_name;
                        user.last_name = newMsg.media.last_name;
                        user.restriction_reason = newMsg.media.vcard;
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
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
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
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = location;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 1;
                    }
                } else if (photo != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = new TLRPC.TL_messageMediaPhoto();
                    newMsg.media.flags |= 3;
                    if (entities != null) {
                        newMsg.entities = entities;
                    }
                    if (ttl != 0) {
                        newMsg.ttl = newMsg.media.ttl_seconds = ttl;
                        newMsg.media.flags |= 4;
                    }
                    newMsg.media.photo = photo;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 2;
                    }
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        newMsg.attachPath = path;
                    } else {
                        TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                        newMsg.attachPath = FileLoader.getPathToAttach(location1, true).toString();
                    }
                } else if (game != null) {
                    newMsg = new TLRPC.TL_message();
                    newMsg.media = new TLRPC.TL_messageMediaGame();
                    newMsg.media.game = game;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    }
                } else if (user != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = new TLRPC.TL_messageMediaContact();
                    newMsg.media.phone_number = user.phone;
                    newMsg.media.first_name = user.first_name;
                    newMsg.media.last_name = user.last_name;
                    newMsg.media.user_id = user.id;
                    if (user.restriction_reason != null && user.restriction_reason.startsWith("BEGIN:VCARD")) {
                        newMsg.media.vcard = user.restriction_reason;
                    } else {
                        newMsg.media.vcard = "";
                    }
                    if (newMsg.media.first_name == null) {
                        user.first_name = newMsg.media.first_name = "";
                    }
                    if (newMsg.media.last_name == null) {
                        user.last_name = newMsg.media.last_name = "";
                    }
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else {
                        type = 6;
                    }
                } else if (document != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.flags |= 3;
                    if (ttl != 0) {
                        newMsg.ttl = newMsg.media.ttl_seconds = ttl;
                        newMsg.media.flags |= 4;
                    }
                    newMsg.media.document = document;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else if (MessageObject.isVideoDocument(document) || MessageObject.isRoundVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else if (MessageObject.isVoiceDocument(document)) {
                        type = 8;
                    } else {
                        type = 7;
                    }
                    if (videoEditedInfo != null) {
                        String ve = videoEditedInfo.getString();
                        if (params == null) {
                            params = new HashMap<>();
                        }
                        params.put("ve", ve);
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
                                document.attributes.remove(a);
                                TLRPC.TL_documentAttributeSticker_layer55 attributeSticker = new TLRPC.TL_documentAttributeSticker_layer55();
                                document.attributes.add(attributeSticker);
                                attributeSticker.alt = attribute.alt;
                                if (attribute.stickerset != null) {
                                    String name;
                                    if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetShortName) {
                                        name = attribute.stickerset.short_name;
                                    } else {
                                        name = DataQuery.getInstance(currentAccount).getStickerSetName(attribute.stickerset.id);
                                    }
                                    if (!TextUtils.isEmpty(name)) {
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetShortName();
                                        attributeSticker.stickerset.short_name = name;
                                    } else {
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                    }
                                } else {
                                    attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                }
                                break;
                            }
                        }
                    }
                }
                if (entities != null && !entities.isEmpty()) {
                    newMsg.entities = entities;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                }
                if (caption != null) {
                    newMsg.message = caption;
                } else if (newMsg.message == null) {
                    newMsg.message = "";
                }
                if (newMsg.attachPath == null) {
                    newMsg.attachPath = "";
                }
                newMsg.local_id = newMsg.id = UserConfig.getInstance(currentAccount).getNewMessageId();
                newMsg.out = true;
                if (isChannel && sendToPeer != null) {
                    newMsg.from_id = -sendToPeer.channel_id;
                } else {
                    newMsg.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                }
                UserConfig.getInstance(currentAccount).saveConfig(false);
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
                newMsg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
                    if (isChannel) {
                        newMsg.views = 1;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                    }
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(sendToPeer.channel_id);
                    if (chat != null) {
                        if (chat.megagroup) {
                            newMsg.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            newMsg.unread = true;
                        } else {
                            newMsg.post = true;
                            if (chat.signatures) {
                                newMsg.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
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
                        MessagesStorage.getInstance(currentAccount).markMessageAsSendError(newMsg);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, newMsg.id);
                        processSentMessage(newMsg.id);
                        return;
                    }
                    sendToPeers = new ArrayList<>();
                    for (TLRPC.ChatParticipant participant : currentChatInfo.participants.participants) {
                        TLRPC.User sendToUser = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                        TLRPC.InputUser peerUser = MessagesController.getInstance(currentAccount).getInputUser(sendToUser);
                        if (peerUser != null) {
                            sendToPeers.add(peerUser);
                        }
                    }
                    newMsg.to_id = new TLRPC.TL_peerChat();
                    newMsg.to_id.chat_id = lower_id;
                } else {
                    newMsg.to_id = MessagesController.getInstance(currentAccount).getPeer(lower_id);
                    if (lower_id > 0) {
                        TLRPC.User sendToUser = MessagesController.getInstance(currentAccount).getUser(lower_id);
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
                if (encryptedChat.participant_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    newMsg.to_id.user_id = encryptedChat.admin_id;
                } else {
                    newMsg.to_id.user_id = encryptedChat.participant_id;
                }
                if (ttl != 0) {
                    newMsg.ttl = ttl;
                } else {
                    newMsg.ttl = encryptedChat.ttl;
                    if (newMsg.ttl != 0 && newMsg.media != null) {
                        newMsg.media.ttl_seconds = newMsg.ttl;
                        newMsg.media.flags |= 4;
                    }
                }
                if (newMsg.ttl != 0 && newMsg.media.document != null) {
                    if (MessageObject.isVoiceMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(newMsg.ttl, duration + 1);
                    } else if (MessageObject.isVideoMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(newMsg.ttl, duration + 1);
                    }
                }
            }
            if (high_id != 1 && (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg))) {
                newMsg.media_unread = true;
            }

            newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj = new MessageObject(currentAccount, newMsg, true);
            newMsgObj.replyMessageObject = reply_to_msg;
            if (!newMsgObj.isForwarded() && (newMsgObj.type == 3 || videoEditedInfo != null || newMsgObj.type == 2) && !TextUtils.isEmpty(newMsg.attachPath)) {
                newMsgObj.attachPathExists = true;
            }
            if (newMsgObj.videoEditedInfo != null && videoEditedInfo == null) {
                videoEditedInfo = newMsgObj.videoEditedInfo;
            }

            long groupId = 0;
            boolean isFinalGroupMedia = false;
            if (params != null) {
                String groupIdStr = params.get("groupId");
                if (groupIdStr != null) {
                    groupId = Utilities.parseLong(groupIdStr);
                    newMsg.grouped_id = groupId;
                    newMsg.flags |= 131072;
                }
                isFinalGroupMedia = params.get("final") != null;
            }

            if (groupId == 0) {
                ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(newMsgObj);
                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);
                MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(peer, objArr);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
            } else {
                String key = "group_" + groupId;
                ArrayList<DelayedMessage> arrayList = delayedMessages.get(key);
                if (arrayList != null) {
                    delayedMessage = arrayList.get(0);
                }
                if (delayedMessage == null) {
                    delayedMessage = new DelayedMessage(peer);
                    delayedMessage.type = 4;
                    delayedMessage.groupId = groupId;
                    delayedMessage.messageObjects = new ArrayList<>();
                    delayedMessage.messages = new ArrayList<>();
                    delayedMessage.originalPaths = new ArrayList<>();
                    delayedMessage.extraHashMap = new HashMap<>();
                    delayedMessage.encryptedChat = encryptedChat;
                }
                if (isFinalGroupMedia) {
                    delayedMessage.finalGroupMessage = newMsg.id;
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                if (sendToPeer != null) {
                    FileLog.d("send message user_id = " + sendToPeer.user_id + " chat_id = " + sendToPeer.chat_id + " channel_id = " + sendToPeer.channel_id + " access_hash = " + sendToPeer.access_hash);
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
                            reqSend.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                        }
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
                        if (newMsg.reply_to_msg_id != 0) {
                            reqSend.flags |= 1;
                            reqSend.reply_to_msg_id = newMsg.reply_to_msg_id;
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
                            DataQuery.getInstance(currentAccount).cleanDraft(peer, false);
                        }
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 73) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_layer45();
                    }
                    reqSend.ttl = newMsg.ttl;
                    if (entities != null && !entities.isEmpty()) {
                        reqSend.entities = entities;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                    }
                    if (newMsg.reply_to_random_id != 0) {
                        reqSend.reply_to_random_id = newMsg.reply_to_random_id;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
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
                    SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    if (retryMessageObject == null) {
                        DataQuery.getInstance(currentAccount).cleanDraft(peer, false);
                    }
                }
            } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 9 && encryptedChat != null) {
                if (encryptedChat == null) {
                    TLRPC.InputMedia inputMedia = null;
                    if (type == 1) {
                        if (location instanceof TLRPC.TL_messageMediaVenue) {
                            inputMedia = new TLRPC.TL_inputMediaVenue();
                            inputMedia.address = location.address;
                            inputMedia.title = location.title;
                            inputMedia.provider = location.provider;
                            inputMedia.venue_id = location.venue_id;
                            inputMedia.venue_type = "";
                        } else if (location instanceof TLRPC.TL_messageMediaGeoLive) {
                            inputMedia = new TLRPC.TL_inputMediaGeoLive();
                            inputMedia.period = location.period;
                        } else {
                            inputMedia = new TLRPC.TL_inputMediaGeoPoint();
                        }
                        inputMedia.geo_point = new TLRPC.TL_inputGeoPoint();
                        inputMedia.geo_point.lat = location.geo.lat;
                        inputMedia.geo_point._long = location.geo._long;
                    } else if (type == 2 || type == 9 && photo != null) {
                        if (photo.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedPhoto();
                            if (ttl != 0) {
                                newMsg.ttl = inputMedia.ttl_seconds = ttl;
                                inputMedia.flags |= 2;
                            }
                            if (params != null) {
                                String masks = params.get("masks");
                                if (masks != null) {
                                    SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                                    int count = serializedData.readInt32(false);
                                    for (int a = 0; a < count; a++) {
                                        inputMedia.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                                    }
                                    inputMedia.flags |= 1;
                                    serializedData.cleanup();
                                }
                            }
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.type = 0;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.originalPath = originalPath;
                            }
                            if (path != null && path.length() > 0 && path.startsWith("http")) {
                                delayedMessage.httpLocation = path;
                            } else {
                                delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                            }
                        } else {
                            TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                            media.id = new TLRPC.TL_inputPhoto();
                            media.id.id = photo.id;
                            media.id.access_hash = photo.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 3) {
                        if (document.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                            if (!MessageObject.isRoundVideoDocument(document) && (videoEditedInfo == null || !videoEditedInfo.muted && !videoEditedInfo.roundVideo)) {
                                inputMedia.nosound_video = true;
                            }
                            if (ttl != 0) {
                                newMsg.ttl = inputMedia.ttl_seconds = ttl;
                                inputMedia.flags |= 2;
                            }
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.type = 1;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.originalPath = originalPath;
                            }
                            delayedMessage.location = document.thumb.location;
                            delayedMessage.videoEditedInfo = videoEditedInfo;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 6) {
                        inputMedia = new TLRPC.TL_inputMediaContact();
                        inputMedia.phone_number = user.phone;
                        inputMedia.first_name = user.first_name;
                        inputMedia.last_name = user.last_name;
                        if (user.restriction_reason != null && user.restriction_reason.startsWith("BEGIN:VCARD")) {
                            inputMedia.vcard = user.restriction_reason;
                        } else {
                            inputMedia.vcard = "";
                        }
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
                                inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                                if (ttl != 0) {
                                    newMsg.ttl = inputMedia.ttl_seconds = ttl;
                                    inputMedia.flags |= 2;
                                }
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.type = 2;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.location = document.thumb.location;
                            }
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            inputMedia = media;
                        }
                    } else if (type == 8) {
                        if (document.access_hash == 0) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            inputMedia.mime_type = document.mime_type;
                            inputMedia.attributes = document.attributes;
                            if (ttl != 0) {
                                newMsg.ttl = inputMedia.ttl_seconds = ttl;
                                inputMedia.flags |= 2;
                            }
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.type = 3;
                            delayedMessage.obj = newMsgObj;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
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
                            DataQuery.getInstance(currentAccount).cleanDraft(peer, false);
                        }
                    } else if (groupId != 0) {
                        TLRPC.TL_messages_sendMultiMedia request;
                        if (delayedMessage.sendRequest != null) {
                            request = (TLRPC.TL_messages_sendMultiMedia) delayedMessage.sendRequest;
                        } else {
                            request = new TLRPC.TL_messages_sendMultiMedia();
                            request.peer = sendToPeer;
                            if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                                request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                            }
                            if (newMsg.reply_to_msg_id != 0) {
                                request.flags |= 1;
                                request.reply_to_msg_id = newMsg.reply_to_msg_id;
                            }
                            delayedMessage.sendRequest = request;
                        }
                        delayedMessage.messageObjects.add(newMsgObj);
                        delayedMessage.messages.add(newMsg);
                        delayedMessage.originalPaths.add(originalPath);
                        TLRPC.TL_inputSingleMedia inputSingleMedia = new TLRPC.TL_inputSingleMedia();
                        inputSingleMedia.random_id = newMsg.random_id;
                        inputSingleMedia.media = inputMedia;
                        inputSingleMedia.message = caption;
                        if (entities != null && !entities.isEmpty()) {
                            inputSingleMedia.entities = entities;
                            inputSingleMedia.flags |= 1;
                        }
                        request.multi_media.add(inputSingleMedia);
                        reqSend = request;
                    } else {
                        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
                        request.peer = sendToPeer;
                        if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                            request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                        }
                        if (newMsg.reply_to_msg_id != 0) {
                            request.flags |= 1;
                            request.reply_to_msg_id = newMsg.reply_to_msg_id;
                        }
                        request.random_id = newMsg.random_id;
                        request.media = inputMedia;
                        request.message = caption;
                        if (entities != null && !entities.isEmpty()) {
                            request.entities = entities;
                            request.flags |= 8;
                        }

                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                    }
                    if (groupId != 0) {
                        performSendDelayedMessage(delayedMessage);
                    } else if (type == 1) {
                        performSendMessageRequest(reqSend, newMsgObj, null);
                    } else if (type == 2) {
                        if (photo.access_hash == 0) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, null, null, true);
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
                    if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 73) {
                        reqSend = new TLRPC.TL_decryptedMessage();
                        if (groupId != 0) {
                            reqSend.grouped_id = groupId;
                            reqSend.flags |= 131072;
                        }
                    } else {
                        reqSend = new TLRPC.TL_decryptedMessage_layer45();
                    }
                    reqSend.ttl = newMsg.ttl;
                    if (entities != null && !entities.isEmpty()) {
                        reqSend.entities = entities;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                    }
                    if (newMsg.reply_to_random_id != 0) {
                        reqSend.reply_to_random_id = newMsg.reply_to_random_id;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                    }
                    reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    if (params != null && params.get("bot_name") != null) {
                        reqSend.via_bot_name = params.get("bot_name");
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                    }
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = "";
                    if (type == 1) {
                        if (location instanceof TLRPC.TL_messageMediaVenue) {
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
                        SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    } else if (type == 2 || type == 9 && photo != null) {
                        TLRPC.PhotoSize small = photo.sizes.get(0);
                        TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
                        ImageLoader.fillPhotoSizeWithBytes(small);
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
                        reqSend.media.caption = caption;
                        if (small.bytes != null) {
                            ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = small.bytes;
                        } else {
                            ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = new byte[0];
                        }
                        reqSend.media.thumb_h = small.h;
                        reqSend.media.thumb_w = small.w;
                        reqSend.media.w = big.w;
                        reqSend.media.h = big.h;
                        reqSend.media.size = big.size;
                        if (big.location.key == null || groupId != 0) {
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.encryptedChat = encryptedChat;
                                delayedMessage.type = 0;
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.obj = newMsgObj;
                            }
                            if (!TextUtils.isEmpty(path) && path.startsWith("http")) {
                                delayedMessage.httpLocation = path;
                            } else {
                                delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                            }
                            if (groupId == 0) {
                                performSendDelayedMessage(delayedMessage);
                            }
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = big.location.volume_id;
                            encryptedFile.access_hash = big.location.secret;
                            reqSend.media.key = big.location.key;
                            reqSend.media.iv = big.location.iv;
                            SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 3) {
                        ImageLoader.fillPhotoSizeWithBytes(document.thumb);
                        if (MessageObject.isNewGifDocument(document) || MessageObject.isRoundVideoDocument(document)) {
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
                        reqSend.media.caption = caption;
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
                        if (document.key == null || groupId != 0) {
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.encryptedChat = encryptedChat;
                                delayedMessage.type = 1;
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.obj = newMsgObj;
                            }
                            delayedMessage.videoEditedInfo = videoEditedInfo;
                            if (groupId == 0) {
                                performSendDelayedMessage(delayedMessage);
                            }
                        } else {
                            TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                            encryptedFile.id = document.id;
                            encryptedFile.access_hash = document.access_hash;
                            reqSend.media.key = document.key;
                            reqSend.media.iv = document.iv;
                            SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 6) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                        reqSend.media.phone_number = user.phone;
                        reqSend.media.first_name = user.first_name;
                        reqSend.media.last_name = user.last_name;
                        reqSend.media.user_id = user.id;
                        SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
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
                            SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                        } else {
                            ImageLoader.fillPhotoSizeWithBytes(document.thumb);
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                            reqSend.media.attributes = document.attributes;
                            reqSend.media.caption = caption;
                            if (document.thumb != null && document.thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = document.thumb.bytes;
                                reqSend.media.thumb_h = document.thumb.h;
                                reqSend.media.thumb_w = document.thumb.w;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                                reqSend.media.thumb_h = 0;
                                reqSend.media.thumb_w = 0;
                            }
                            reqSend.media.size = document.size;
                            reqSend.media.mime_type = document.mime_type;

                            if (document.key == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.type = 2;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.encryptedChat = encryptedChat;
                                if (path != null && path.length() > 0 && path.startsWith("http")) {
                                    delayedMessage.httpLocation = path;
                                }
                                performSendDelayedMessage(delayedMessage);
                            } else {
                                TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                                encryptedFile.id = document.id;
                                encryptedFile.access_hash = document.access_hash;
                                reqSend.media.key = document.key;
                                reqSend.media.iv = document.iv;
                                SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                            }
                        }
                    } else if (type == 8) {
                        delayedMessage = new DelayedMessage(peer);
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.type = 3;

                        reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                        reqSend.media.attributes = document.attributes;
                        reqSend.media.caption = caption;
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
                        performSendDelayedMessage(delayedMessage);
                    }
                    if (groupId != 0) {
                        TLRPC.TL_messages_sendEncryptedMultiMedia request;
                        if (delayedMessage.sendEncryptedRequest != null) {
                            request = (TLRPC.TL_messages_sendEncryptedMultiMedia) delayedMessage.sendEncryptedRequest;
                        } else {
                            request = new TLRPC.TL_messages_sendEncryptedMultiMedia();
                            delayedMessage.sendEncryptedRequest = request;
                        }
                        delayedMessage.messageObjects.add(newMsgObj);
                        delayedMessage.messages.add(newMsg);
                        delayedMessage.originalPaths.add(originalPath);
                        delayedMessage.upload = true;
                        request.messages.add(reqSend);
                        TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                        encryptedFile.id = type == 3 ? 1 : 0;
                        request.files.add(encryptedFile);
                        performSendDelayedMessage(delayedMessage);
                    }
                    if (retryMessageObject == null) {
                        DataQuery.getInstance(currentAccount).cleanDraft(peer, false);
                    }
                }
            } else if (type == 4) {
                TLRPC.TL_messages_forwardMessages reqSend = new TLRPC.TL_messages_forwardMessages();
                reqSend.to_peer = sendToPeer;
                reqSend.with_my_score = retryMessageObject.messageOwner.with_my_score;
                if (retryMessageObject.messageOwner.ttl != 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-retryMessageObject.messageOwner.ttl);
                    reqSend.from_peer = new TLRPC.TL_inputPeerChannel();
                    reqSend.from_peer.channel_id = -retryMessageObject.messageOwner.ttl;
                    if (chat != null) {
                        reqSend.from_peer.access_hash = chat.access_hash;
                    }
                } else {
                    reqSend.from_peer = new TLRPC.TL_inputPeerEmpty();
                }
                if (retryMessageObject.messageOwner.to_id instanceof TLRPC.TL_peerChannel) {
                    reqSend.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                }
                reqSend.random_id.add(newMsg.random_id);
                if (retryMessageObject.getId() >= 0) {
                    reqSend.id.add(retryMessageObject.getId());
                } else {
                    if (retryMessageObject.messageOwner.fwd_msg_id != 0) {
                        reqSend.id.add(retryMessageObject.messageOwner.fwd_msg_id);
                    } else if (retryMessageObject.messageOwner.fwd_from != null) {
                        reqSend.id.add(retryMessageObject.messageOwner.fwd_from.channel_post);
                    }
                }
                performSendMessageRequest(reqSend, newMsgObj, null);
            } else if (type == 9) {
                TLRPC.TL_messages_sendInlineBotResult reqSend = new TLRPC.TL_messages_sendInlineBotResult();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (newMsg.reply_to_msg_id != 0) {
                    reqSend.flags |= 1;
                    reqSend.reply_to_msg_id = newMsg.reply_to_msg_id;
                }
                if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                    reqSend.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                }
                reqSend.query_id = Utilities.parseLong(params.get("query_id"));
                reqSend.id = params.get("id");
                if (retryMessageObject == null) {
                    reqSend.clear_draft = true;
                    DataQuery.getInstance(currentAccount).cleanDraft(peer, false);
                }
                performSendMessageRequest(reqSend, newMsgObj, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
            MessagesStorage.getInstance(currentAccount).markMessageAsSendError(newMsg);
            if (newMsgObj != null) {
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, newMsg.id);
            processSentMessage(newMsg.id);
        }
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        performSendDelayedMessage(message, -1);
    }

    private void performSendDelayedMessage(final DelayedMessage message, int index) {
        if (message.type == 0) {
            if (message.httpLocation != null) {
                putToDelayedMessages(message.httpLocation, message);
                ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file", currentAccount);
            } else {
                if (message.sendRequest != null) {
                    String location = FileLoader.getPathToAttach(message.location).toString();
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance(currentAccount).uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                } else {
                    String location = FileLoader.getPathToAttach(message.location).toString();
                    if (message.sendEncryptedRequest != null && message.location.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            location = FileLoader.getPathToAttach(message.location, true).toString();
                            file = new File(location);
                        }
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(message.location), message);
                            FileLoader.getInstance(currentAccount).loadFile(message.location, "jpg", 0, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance(currentAccount).uploadFile(location, true, true, ConnectionsManager.FileTypePhoto);
                }
            }
        } else if (message.type == 1) {
            if (message.videoEditedInfo != null && message.videoEditedInfo.needConvert()) {
                String location = message.obj.messageOwner.attachPath;
                TLRPC.Document document = message.obj.getDocument();
                if (location == null) {
                    location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                }
                putToDelayedMessages(location, message);
                MediaController.getInstance().scheduleVideoConvert(message.obj);
            } else {
                if (message.videoEditedInfo != null) {
                    if (message.videoEditedInfo.file != null) {
                        TLRPC.InputMedia media;
                        if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                        } else if (message.sendRequest instanceof TLRPC.TL_messages_editMessage) {
                            media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                        } else {
                            media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                        }
                        media.file = message.videoEditedInfo.file;
                        message.videoEditedInfo.file = null;
                    } else if (message.videoEditedInfo.encryptedFile != null) {
                        TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage) message.sendEncryptedRequest;
                        decryptedMessage.media.size = (int) message.videoEditedInfo.estimatedSize;
                        decryptedMessage.media.key = message.videoEditedInfo.key;
                        decryptedMessage.media.iv = message.videoEditedInfo.iv;
                        SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest(decryptedMessage, message.obj.messageOwner, message.encryptedChat, message.videoEditedInfo.encryptedFile, message.originalPath, message.obj);
                        message.videoEditedInfo.encryptedFile = null;
                        return;
                    }
                }
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_editMessage) {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    } else {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        TLRPC.Document document = message.obj.getDocument();
                        if (location == null) {
                            location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        if (message.obj.videoEditedInfo != null && message.obj.videoEditedInfo.needConvert()) {
                            FileLoader.getInstance(currentAccount).uploadFile(location, false, false, document.size, ConnectionsManager.FileTypeVideo);
                        } else {
                            FileLoader.getInstance(currentAccount).uploadFile(location, false, false, ConnectionsManager.FileTypeVideo);
                        }
                    } else {
                        String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                        putToDelayedMessages(location, message);
                        FileLoader.getInstance(currentAccount).uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                    }
                } else {
                    String location = message.obj.messageOwner.attachPath;
                    TLRPC.Document document = message.obj.getDocument();
                    if (location == null) {
                        location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                    }
                    if (message.sendEncryptedRequest != null && document.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(document), message);
                            FileLoader.getInstance(currentAccount).loadFile(document, true, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    if (message.obj.videoEditedInfo != null && message.obj.videoEditedInfo.needConvert()) {
                        FileLoader.getInstance(currentAccount).uploadFile(location, true, false, document.size, ConnectionsManager.FileTypeVideo);
                    } else {
                        FileLoader.getInstance(currentAccount).uploadFile(location, true, false, ConnectionsManager.FileTypeVideo);
                    }
                }
            }
        } else if (message.type == 2) {
            if (message.httpLocation != null) {
                putToDelayedMessages(message.httpLocation, message);
                ImageLoader.getInstance().loadHttpFile(message.httpLocation, "gif", currentAccount);
            } else {
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_editMessage) {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    } else {
                        media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        putToDelayedMessages(location, message);
                        FileLoader.getInstance(currentAccount).uploadFile(location, message.sendRequest == null, false, ConnectionsManager.FileTypeFile);
                    } else if (media.thumb == null && message.location != null) {
                        String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                        putToDelayedMessages(location, message);
                        FileLoader.getInstance(currentAccount).uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                    }
                } else {
                    String location = message.obj.messageOwner.attachPath;
                    TLRPC.Document document = message.obj.getDocument();
                    if (message.sendEncryptedRequest != null && document.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(document), message);
                            FileLoader.getInstance(currentAccount).loadFile(document, true, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    FileLoader.getInstance(currentAccount).uploadFile(location, true, false, ConnectionsManager.FileTypeFile);
                }
            }
        } else if (message.type == 3) {
            String location = message.obj.messageOwner.attachPath;
            putToDelayedMessages(location, message);
            FileLoader.getInstance(currentAccount).uploadFile(location, message.sendRequest == null, true, ConnectionsManager.FileTypeAudio);
        } else if (message.type == 4) {
            boolean add = index < 0;
            if (message.location != null || message.httpLocation != null || message.upload || index >= 0) {
                if (index < 0) {
                    index = message.messageObjects.size() - 1;
                }
                MessageObject messageObject = message.messageObjects.get(index);
                if (messageObject.getDocument() != null) {
                    if (message.videoEditedInfo != null) {
                        String location = messageObject.messageOwner.attachPath;
                        TLRPC.Document document = messageObject.getDocument();
                        if (location == null) {
                            location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        message.extraHashMap.put(messageObject, location);
                        message.extraHashMap.put(location + "_i", messageObject);
                        if (message.location != null) {
                            message.extraHashMap.put(location + "_t", message.location);
                        }
                        MediaController.getInstance().scheduleVideoConvert(messageObject);
                    } else {
                        TLRPC.Document document = messageObject.getDocument();
                        String documentLocation = messageObject.messageOwner.attachPath;
                        if (documentLocation == null) {
                            documentLocation = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        if (message.sendRequest != null) {
                            TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                            TLRPC.InputMedia media = request.multi_media.get(index).media;
                            if (media.file == null) {
                                putToDelayedMessages(documentLocation, message);
                                message.extraHashMap.put(messageObject, documentLocation);
                                message.extraHashMap.put(documentLocation, media);
                                message.extraHashMap.put(documentLocation + "_i", messageObject);
                                if (message.location != null) {
                                    message.extraHashMap.put(documentLocation + "_t", message.location);
                                }
                                if (messageObject.videoEditedInfo != null && messageObject.videoEditedInfo.needConvert()) {
                                    FileLoader.getInstance(currentAccount).uploadFile(documentLocation, false, false, document.size, ConnectionsManager.FileTypeVideo);
                                } else {
                                    FileLoader.getInstance(currentAccount).uploadFile(documentLocation, false, false, ConnectionsManager.FileTypeVideo);
                                }
                            } else {
                                String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                                putToDelayedMessages(location, message);
                                message.extraHashMap.put(location + "_o", documentLocation);
                                message.extraHashMap.put(messageObject, location);
                                message.extraHashMap.put(location, media);
                                FileLoader.getInstance(currentAccount).uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                            }
                        } else {
                            TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                            putToDelayedMessages(documentLocation, message);
                            message.extraHashMap.put(messageObject, documentLocation);
                            message.extraHashMap.put(documentLocation, request.files.get(index));
                            message.extraHashMap.put(documentLocation + "_i", messageObject);
                            if (message.location != null) {
                                message.extraHashMap.put(documentLocation + "_t", message.location);
                            }
                            if (messageObject.videoEditedInfo != null && messageObject.videoEditedInfo.needConvert()) {
                                FileLoader.getInstance(currentAccount).uploadFile(documentLocation, true, false, document.size, ConnectionsManager.FileTypeVideo);
                            } else {
                                FileLoader.getInstance(currentAccount).uploadFile(documentLocation, true, false, ConnectionsManager.FileTypeVideo);
                            }
                        }
                    }
                    message.videoEditedInfo = null;
                    message.location = null;
                } else {
                    if (message.httpLocation != null) {
                        putToDelayedMessages(message.httpLocation, message);
                        message.extraHashMap.put(messageObject, message.httpLocation);
                        message.extraHashMap.put(message.httpLocation, messageObject);
                        ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file", currentAccount);
                        message.httpLocation = null;
                    } else {
                        TLObject inputMedia;
                        if (message.sendRequest != null) {
                            TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                            inputMedia = request.multi_media.get(index).media;
                        } else {
                            TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                            inputMedia = request.files.get(index);
                        }
                        String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                        putToDelayedMessages(location, message);
                        message.extraHashMap.put(location, inputMedia);
                        message.extraHashMap.put(messageObject, location);
                        FileLoader.getInstance(currentAccount).uploadFile(location, message.sendEncryptedRequest != null, true, ConnectionsManager.FileTypePhoto);
                        message.location = null;
                    }
                }
                message.upload = false;
            } else {
                if (!message.messageObjects.isEmpty()) {
                    putToSendingMessages(message.messageObjects.get(message.messageObjects.size() - 1).messageOwner);
                }
            }
            sendReadyToSendGroup(message, add, true);
        }
    }

    private void uploadMultiMedia(final DelayedMessage message, final TLRPC.InputMedia inputMedia, final TLRPC.InputEncryptedFile inputEncryptedFile, String key) {
        if (inputMedia != null) {
            TLRPC.TL_messages_sendMultiMedia multiMedia = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
            for (int a = 0; a < multiMedia.multi_media.size(); a++) {
                if (multiMedia.multi_media.get(a).media == inputMedia) {
                    putToSendingMessages(message.messages.get(a));
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileUploadProgressChanged, key, 1.0f, false);
                    break;
                }
            }

            TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
            req.media = inputMedia;
            req.peer = ((TLRPC.TL_messages_sendMultiMedia) message.sendRequest).peer;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                TLRPC.InputMedia newInputMedia = null;
                if (response != null) {
                    TLRPC.MessageMedia messageMedia = (TLRPC.MessageMedia) response;
                    if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto && messageMedia instanceof TLRPC.TL_messageMediaPhoto) {
                        TLRPC.TL_inputMediaPhoto inputMediaPhoto = new TLRPC.TL_inputMediaPhoto();
                        inputMediaPhoto.id = new TLRPC.TL_inputPhoto();
                        inputMediaPhoto.id.id = messageMedia.photo.id;
                        inputMediaPhoto.id.access_hash = messageMedia.photo.access_hash;
                        newInputMedia = inputMediaPhoto;
                    } else if (inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument && messageMedia instanceof TLRPC.TL_messageMediaDocument) {
                        TLRPC.TL_inputMediaDocument inputMediaDocument = new TLRPC.TL_inputMediaDocument();
                        inputMediaDocument.id = new TLRPC.TL_inputDocument();
                        inputMediaDocument.id.id = messageMedia.document.id;
                        inputMediaDocument.id.access_hash = messageMedia.document.access_hash;
                        newInputMedia = inputMediaDocument;
                    }
                }
                if (newInputMedia != null) {
                    if (inputMedia.ttl_seconds != 0) {
                        newInputMedia.ttl_seconds = inputMedia.ttl_seconds;
                        newInputMedia.flags |= 1;
                    }
                    TLRPC.TL_messages_sendMultiMedia req1 = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                    for (int a = 0; a < req1.multi_media.size(); a++) {
                        if (req1.multi_media.get(a).media == inputMedia) {
                            req1.multi_media.get(a).media = newInputMedia;
                            break;
                        }
                    }
                    sendReadyToSendGroup(message, false, true);
                } else {
                    message.markAsError();
                }
            }));
        } else if (inputEncryptedFile != null) {
            TLRPC.TL_messages_sendEncryptedMultiMedia multiMedia = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
            for (int a = 0; a < multiMedia.files.size(); a++) {
                if (multiMedia.files.get(a) == inputEncryptedFile) {
                    putToSendingMessages(message.messages.get(a));
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileUploadProgressChanged, key, 1.0f, false);
                    break;
                }
            }
            sendReadyToSendGroup(message, false, true);
        }
    }

    private void sendReadyToSendGroup(DelayedMessage message, boolean add, boolean check) {
        if (message.messageObjects.isEmpty()) {
            message.markAsError();
            return;
        }
        String key = "group_" + message.groupId;
        if (message.finalGroupMessage != message.messageObjects.get(message.messageObjects.size() - 1).getId()) {
            if (add) {
                putToDelayedMessages(key, message);
            }
            return;
        } else if (add) {
            delayedMessages.remove(key);
            MessagesStorage.getInstance(currentAccount).putMessages(message.messages, false, true, false, 0);
            MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(message.peer, message.messageObjects);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
        if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
            TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
            for (int a = 0; a < request.multi_media.size(); a++) {
                TLRPC.InputMedia inputMedia = request.multi_media.get(a).media;
                if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
                    return;
                }
            }

            if (check) {
                DelayedMessage maxDelayedMessage = findMaxDelayedMessageForMessageId(message.finalGroupMessage, message.peer);
                if (maxDelayedMessage != null) {
                    maxDelayedMessage.addDelayedRequest(message.sendRequest, message.messageObjects, message.originalPaths);
                    if (message.requests != null) {
                        maxDelayedMessage.requests.addAll(message.requests);
                    }
                    return;
                }
            }
        } else {
            TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
            for (int a = 0; a < request.files.size(); a++) {
                TLRPC.InputEncryptedFile inputMedia = request.files.get(a);
                if (inputMedia instanceof TLRPC.TL_inputEncryptedFile) {
                    return;
                }
            }
        }

        if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
            performSendMessageRequestMulti((TLRPC.TL_messages_sendMultiMedia) message.sendRequest, message.messageObjects, message.originalPaths);
        } else {
            SecretChatHelper.getInstance(currentAccount).performSendEncryptedRequest((TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest, message);
        }

        message.sendDelayedRequests();
    }

    protected void stopVideoService(final String path) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopEncodingService, path, currentAccount)));
    }

    protected void putToSendingMessages(TLRPC.Message message) {
        sendingMessages.put(message.id, message);
    }

    protected TLRPC.Message removeFromSendingMessages(int mid) {
        TLRPC.Message message = sendingMessages.get(mid);
        if (message != null) {
            sendingMessages.remove(mid);
        }
        return message;
    }

    public boolean isSendingMessage(int mid) {
        return sendingMessages.indexOfKey(mid) >= 0;
    }

    private void performSendMessageRequestMulti(final TLRPC.TL_messages_sendMultiMedia req, final ArrayList<MessageObject> msgObjs, final ArrayList<String> originalPaths) {
        for (int a = 0; a < msgObjs.size(); a++) {
            putToSendingMessages(msgObjs.get(a).messageOwner);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            boolean isSentError = false;
            if (error == null) {
                SparseArray<TLRPC.Message> newMessages = new SparseArray<>();
                LongSparseArray<Integer> newIds = new LongSparseArray<>();
                final TLRPC.Updates updates = (TLRPC.Updates) response;
                ArrayList<TLRPC.Update> updatesArr = ((TLRPC.Updates) response).updates;
                for (int a = 0; a < updatesArr.size(); a++) {
                    TLRPC.Update update = updatesArr.get(a);
                    if (update instanceof TLRPC.TL_updateMessageID) {
                        TLRPC.TL_updateMessageID updateMessageID = (TLRPC.TL_updateMessageID) update;
                        newIds.put(updateMessageID.random_id, updateMessageID.id);
                        updatesArr.remove(a);
                        a--;
                    } else if (update instanceof TLRPC.TL_updateNewMessage) {
                        final TLRPC.TL_updateNewMessage newMessage = (TLRPC.TL_updateNewMessage) update;
                        newMessages.put(newMessage.message.id, newMessage.message);
                        Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count));
                        updatesArr.remove(a);
                        a--;
                    } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                        final TLRPC.TL_updateNewChannelMessage newMessage = (TLRPC.TL_updateNewChannelMessage) update;
                        newMessages.put(newMessage.message.id, newMessage.message);
                        Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processNewChannelDifferenceParams(newMessage.pts, newMessage.pts_count, newMessage.message.to_id.channel_id));
                        updatesArr.remove(a);
                        a--;
                    }
                }

                for (int i = 0; i < msgObjs.size(); i++) {
                    final MessageObject msgObj = msgObjs.get(i);
                    final String originalPath = originalPaths.get(i);
                    final TLRPC.Message newMsgObj = msgObj.messageOwner;
                    final int oldId = newMsgObj.id;
                    final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                    final String attachPath = newMsgObj.attachPath;
                    final long grouped_id;

                    Integer id = newIds.get(newMsgObj.random_id);
                    if (id != null) {
                        TLRPC.Message message = newMessages.get(id);
                        if (message != null) {
                            sentMessages.add(message);
                            newMsgObj.id = message.id;
                            if ((newMsgObj.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0) {
                                message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            }
                            grouped_id = message.grouped_id;

                            Integer value = MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.get(message.dialog_id);
                            if (value == null) {
                                value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                                MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.put(message.dialog_id, value);
                            }
                            message.unread = value < message.id;
                            updateMediaPaths(msgObj, message, originalPath, false);
                        } else {
                            isSentError = true;
                            break;
                        }
                    } else {
                        isSentError = true;
                        break;
                    }

                    if (!isSentError) {
                        StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, 1);
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id);
                        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                            MessagesStorage.getInstance(currentAccount).updateMessageStateAndId(newMsgObj.random_id, oldId, newMsgObj.id, 0, false, newMsgObj.to_id.channel_id);
                            MessagesStorage.getInstance(currentAccount).putMessages(sentMessages, true, false, false, 0);
                            AndroidUtilities.runOnUIThread(() -> {
                                DataQuery.getInstance(currentAccount).increasePeerRaiting(newMsgObj.dialog_id);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id);
                                processSentMessage(oldId);
                                removeFromSendingMessages(oldId);
                            });
                        });
                    }
                }
                Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processUpdates(updates, false));
            } else {
                AlertsCreator.processError(currentAccount, error, null, req);
                isSentError = true;
            }
            if (isSentError) {
                for (int i = 0; i < msgObjs.size(); i++) {
                    TLRPC.Message newMsgObj = msgObjs.get(i).messageOwner;
                    MessagesStorage.getInstance(currentAccount).markMessageAsSendError(newMsgObj);
                    newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                    processSentMessage(newMsgObj.id);
                    removeFromSendingMessages(newMsgObj.id);
                }
            }
        }), null, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);
    }

    private void performSendMessageRequest(final TLObject req, final MessageObject msgObj, final String originalPath) {
        performSendMessageRequest(req, msgObj, originalPath, null, false);
    }

    private DelayedMessage findMaxDelayedMessageForMessageId(int messageId, long dialogId) {
        DelayedMessage maxDelayedMessage = null;
        int maxDalyedMessageId = Integer.MIN_VALUE;
        for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
            ArrayList<DelayedMessage> messages = entry.getValue();
            int size = messages.size();
            for (int a = 0; a < size; a++) {
                DelayedMessage delayedMessage = messages.get(a);
                if ((delayedMessage.type == 4 || delayedMessage.type == 0) && delayedMessage.peer == dialogId) {
                    int mid = 0;
                    if (delayedMessage.obj != null) {
                        mid = delayedMessage.obj.getId();
                    } else if (delayedMessage.messageObjects != null && !delayedMessage.messageObjects.isEmpty()) {
                        mid = delayedMessage.messageObjects.get(delayedMessage.messageObjects.size() - 1).getId();
                    }
                    if (mid != 0 && mid > messageId) {
                        if (maxDelayedMessage == null && maxDalyedMessageId < mid) {
                            maxDelayedMessage = delayedMessage;
                            maxDalyedMessageId = mid;
                        }
                    }
                }
            }
        }
        return maxDelayedMessage;
    }

    private void performSendMessageRequest(final TLObject req, final MessageObject msgObj, final String originalPath, DelayedMessage parentMessage, boolean check) {
        if (!(req instanceof TLRPC.TL_messages_editMessage)) {
            if (check) {
                DelayedMessage maxDelayedMessage = findMaxDelayedMessageForMessageId(msgObj.getId(), msgObj.getDialogId());
                if (maxDelayedMessage != null) {
                    maxDelayedMessage.addDelayedRequest(req, msgObj, originalPath);
                    if (parentMessage != null && parentMessage.requests != null) {
                        maxDelayedMessage.requests.addAll(parentMessage.requests);
                    }
                    return;
                }
            }
        }
        final TLRPC.Message newMsgObj = msgObj.messageOwner;
        putToSendingMessages(newMsgObj);
        newMsgObj.reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (req instanceof TLRPC.TL_messages_editMessage) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        final String attachPath = newMsgObj.attachPath;

                        final TLRPC.Updates updates = (TLRPC.Updates) response;
                        ArrayList<TLRPC.Update> updatesArr = ((TLRPC.Updates) response).updates;
                        TLRPC.Message message = null;
                        for (int a = 0; a < updatesArr.size(); a++) {
                            TLRPC.Update update = updatesArr.get(a);
                            if (update instanceof TLRPC.TL_updateEditMessage) {
                                final TLRPC.TL_updateEditMessage newMessage = (TLRPC.TL_updateEditMessage) update;
                                message = newMessage.message;
                                break;
                            } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
                                final TLRPC.TL_updateEditChannelMessage newMessage = (TLRPC.TL_updateEditChannelMessage) update;
                                message = newMessage.message;
                                break;
                            }
                        }
                        if (message != null) {
                            ImageLoader.saveMessageThumbs(message);
                            updateMediaPaths(msgObj, message, originalPath, false);
                        }
                        Utilities.stageQueue.postRunnable(() -> {
                            MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                            AndroidUtilities.runOnUIThread(() -> {
                                processSentMessage(newMsgObj.id);
                                removeFromSendingMessages(newMsgObj.id);
                            });
                        });
                        if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                            stopVideoService(attachPath);
                        }
                    } else {
                        AlertsCreator.processError(currentAccount, error, null, req);
                        if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                            stopVideoService(newMsgObj.attachPath);
                        }
                        removeFromSendingMessages(newMsgObj.id);
                        revertEditingMessageObject(msgObj);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {

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
                                ImageLoader.saveMessageThumbs(newMsgObj);
                            }
                            if (res.media instanceof TLRPC.TL_messageMediaGame && !TextUtils.isEmpty(res.message)) {
                                newMsgObj.message = res.message;
                            }
                            if (!newMsgObj.entities.isEmpty()) {
                                newMsgObj.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                            }
                            Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processNewDifferenceParams(-1, res.pts, res.date, res.pts_count));
                            sentMessages.add(newMsgObj);
                        } else if (response instanceof TLRPC.Updates) {
                            final TLRPC.Updates updates = (TLRPC.Updates) response;
                            ArrayList<TLRPC.Update> updatesArr = ((TLRPC.Updates) response).updates;
                            TLRPC.Message message = null;
                            for (int a = 0; a < updatesArr.size(); a++) {
                                TLRPC.Update update = updatesArr.get(a);
                                if (update instanceof TLRPC.TL_updateNewMessage) {
                                    final TLRPC.TL_updateNewMessage newMessage = (TLRPC.TL_updateNewMessage) update;
                                    sentMessages.add(message = newMessage.message);
                                    Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count));
                                    updatesArr.remove(a);
                                    break;
                                } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                                    final TLRPC.TL_updateNewChannelMessage newMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                    sentMessages.add(message = newMessage.message);
                                    if ((newMsgObj.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0) {
                                        newMessage.message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                    }
                                    Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processNewChannelDifferenceParams(newMessage.pts, newMessage.pts_count, newMessage.message.to_id.channel_id));
                                    updatesArr.remove(a);
                                    break;
                                }
                            }
                            if (message != null) {
                                ImageLoader.saveMessageThumbs(message);
                                Integer value = MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.get(message.dialog_id);
                                if (value == null) {
                                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                                    MessagesController.getInstance(currentAccount).dialogs_read_outbox_max.put(message.dialog_id, value);
                                }
                                message.unread = value < message.id;

                                newMsgObj.id = message.id;
                                updateMediaPaths(msgObj, message, originalPath, false);
                            } else {
                                isSentError = true;
                            }
                            Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(currentAccount).processUpdates(updates, false));
                        }

                        if (MessageObject.isLiveLocationMessage(newMsgObj)) {
                            LocationController.getInstance(currentAccount).addSharingLocation(newMsgObj.dialog_id, newMsgObj.id, newMsgObj.media.period, newMsgObj);
                        }

                        if (!isSentError) {
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, 1);
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, (isBroadcast ? oldId : newMsgObj.id), newMsgObj, newMsgObj.dialog_id, 0L);
                            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                                MessagesStorage.getInstance(currentAccount).updateMessageStateAndId(newMsgObj.random_id, oldId, (isBroadcast ? oldId : newMsgObj.id), 0, false, newMsgObj.to_id.channel_id);
                                MessagesStorage.getInstance(currentAccount).putMessages(sentMessages, true, false, isBroadcast, 0);
                                if (isBroadcast) {
                                    ArrayList<TLRPC.Message> currentMessage = new ArrayList<>();
                                    currentMessage.add(newMsgObj);
                                    MessagesStorage.getInstance(currentAccount).putMessages(currentMessage, true, false, false, 0);
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (isBroadcast) {
                                        for (int a = 0; a < sentMessages.size(); a++) {
                                            TLRPC.Message message = sentMessages.get(a);
                                            ArrayList<MessageObject> arr = new ArrayList<>();
                                            MessageObject messageObject = new MessageObject(currentAccount, message, false);
                                            arr.add(messageObject);
                                            MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(messageObject.getDialogId(), arr, true);
                                        }
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                                    }
                                    DataQuery.getInstance(currentAccount).increasePeerRaiting(newMsgObj.dialog_id);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, (isBroadcast ? oldId : newMsgObj.id), newMsgObj, newMsgObj.dialog_id, 0L);
                                    processSentMessage(oldId);
                                    removeFromSendingMessages(oldId);
                                });
                                if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                                    stopVideoService(attachPath);
                                }
                            });
                        }
                    } else {
                        AlertsCreator.processError(currentAccount, error, null, req);
                        isSentError = true;
                    }
                    if (isSentError) {
                        MessagesStorage.getInstance(currentAccount).markMessageAsSendError(newMsgObj);
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                        processSentMessage(newMsgObj.id);
                        if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
                            stopVideoService(newMsgObj.attachPath);
                        }
                        removeFromSendingMessages(newMsgObj.id);
                    }
                });
            }
        }, () -> {
            final int msg_id = newMsgObj.id;
            AndroidUtilities.runOnUIThread(() -> {
                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByAck, msg_id);
            });
        }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter | (req instanceof TLRPC.TL_messages_sendMessage ? ConnectionsManager.RequestFlagNeedQuickAck : 0));

        if (parentMessage != null) {
            parentMessage.sendDelayedRequests();
        }
    }

    private void updateMediaPaths(MessageObject newMsgObj, TLRPC.Message sentMessage, String originalPath, boolean post) {
        TLRPC.Message newMsg = newMsgObj.messageOwner;
        if (sentMessage == null) {
            return;
        }
        if (sentMessage.media instanceof TLRPC.TL_messageMediaPhoto && sentMessage.media.photo != null && newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
            if (sentMessage.media.ttl_seconds == 0) {
                MessagesStorage.getInstance(currentAccount).putSentFile(originalPath, sentMessage.media.photo, 0);
            }

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
                            File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                            File cacheFile2;
                            if (sentMessage.media.ttl_seconds == 0 && (sentMessage.media.photo.sizes.size() == 1 || size.w > 90 || size.h > 90)) {
                                cacheFile2 = FileLoader.getPathToAttach(size);
                            } else {
                                cacheFile2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
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
                if (sentMessage.media.ttl_seconds == 0) {
                    MessagesStorage.getInstance(currentAccount).putSentFile(originalPath, sentMessage.media.document, 2);
                }
                sentMessage.attachPath = newMsg.attachPath;
            } else if (!MessageObject.isVoiceMessage(sentMessage) && !MessageObject.isRoundVideoMessage(sentMessage)) {
                if (sentMessage.media.ttl_seconds == 0) {
                    MessagesStorage.getInstance(currentAccount).putSentFile(originalPath, sentMessage.media.document, 1);
                }
            }

            TLRPC.PhotoSize size2 = newMsg.media.document.thumb;
            TLRPC.PhotoSize size = sentMessage.media.document.thumb;
            if (size2 != null && size2.location != null && size2.location.volume_id == Integer.MIN_VALUE && size != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                    File cacheFile2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
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
                    DataQuery.getInstance(currentAccount).addRecentGif(sentMessage.media.document, sentMessage.date);
                } else if (MessageObject.isStickerDocument(sentMessage.media.document)) {
                    DataQuery.getInstance(currentAccount).addRecentSticker(DataQuery.TYPE_IMAGE, sentMessage.media.document, sentMessage.date, false);
                }
            }

            if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                File cacheFile = new File(newMsg.attachPath);
                File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.document, sentMessage.media.ttl_seconds != 0);
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
                            MessagesStorage.getInstance(currentAccount).addRecentLocalFile(originalPath, cacheFile2.toString(), newMsg.media.document);
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
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaGeo) {
            sentMessage.media.geo.lat = newMsg.media.geo.lat;
            sentMessage.media.geo._long = newMsg.media.geo._long;
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
        MessagesStorage.getInstance(currentAccount).getUnsentMessages(1000);
    }

    protected void processUnsentMessages(final ArrayList<TLRPC.Message> messages, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(currentAccount).putUsers(users, true);
            MessagesController.getInstance(currentAccount).putChats(chats, true);
            MessagesController.getInstance(currentAccount).putEncryptedChats(encryptedChats, true);
            for (int a = 0; a < messages.size(); a++) {
                TLRPC.Message message = messages.get(a);
                MessageObject messageObject = new MessageObject(currentAccount, message, false);
                retrySendMessage(messageObject, true);
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
            UserConfig.getInstance(currentAccount).saveConfig(false);
            TLRPC.TL_photo photo = new TLRPC.TL_photo();
            photo.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            photo.sizes = sizes;
            return photo;
        }
    }

    private static boolean prepareSendingDocumentInternal(final int currentAccount, String path, String originalPath, Uri uri, String mime, final long dialog_id, final MessageObject reply_to_msg, CharSequence caption, final ArrayList<TLRPC.MessageEntity> entities, final MessageObject editingMessageObject) {
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
        String extension = null;
        if (uri != null) {
            boolean hasExt = false;
            if (mime != null) {
                extension = myMime.getExtensionFromMimeType(mime);
            }
            if (extension == null) {
                extension = "txt";
            } else {
                hasExt = true;
            }
            path = MediaController.copyFileToCache(uri, extension);
            if (path == null) {
                return false;
            }
            if (!hasExt) {
                extension = null;
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
        if (extension != null) {
            ext = extension;
        } else {
            int idx = path.lastIndexOf('.');
            if (idx != -1) {
                ext = path.substring(idx + 1);
            }
        }
        String extL = ext.toLowerCase();
        String permormer = null;
        String title = null;
        boolean isVoice = false;
        int duration = 0;
        if (extL.equals("mp3") || extL.equals("m4a")) {
            AudioInfo audioInfo = AudioInfo.getAudioInfo(f);
            if (audioInfo != null) {
                long d = audioInfo.getDuration();
                if (d != 0) {
                    permormer = audioInfo.getArtist();
                    title = audioInfo.getTitle();
                }
            }
        } else if (extL.equals("opus") || extL.equals("ogg") || extL.equals("flac")) {
            MediaMetadataRetriever mediaMetadataRetriever = null;
            try {
                mediaMetadataRetriever = new MediaMetadataRetriever();
                mediaMetadataRetriever.setDataSource(f.getAbsolutePath());
                String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) {
                    duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
                    title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    permormer = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                }
                if (editingMessageObject == null && extL.equals("ogg") && MediaController.isOpusFile(f.getAbsolutePath()) == 1) {
                    isVoice = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (mediaMetadataRetriever != null) {
                        mediaMetadataRetriever.release();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (duration != 0) {
            attributeAudio = new TLRPC.TL_documentAttributeAudio();
            attributeAudio.duration = duration;
            attributeAudio.title = title;
            attributeAudio.performer = permormer;
            if (attributeAudio.title == null) {
                attributeAudio.title = "";
            }
            attributeAudio.flags |= 1;
            if (attributeAudio.performer == null) {
                attributeAudio.performer = "";
            }
            attributeAudio.flags |= 2;
            if (isVoice) {
                attributeAudio.voice = true;
            }
        }
        boolean sendNew = false;
        if (originalPath != null) {
            if (originalPath.endsWith("attheme")) {
                sendNew = true;
            } else if (attributeAudio != null) {
                originalPath += "audio" + f.length();
            } else {
                originalPath += "" + f.length();
            }
        }

        TLRPC.TL_document document = null;
        if (!sendNew && !isEncrypted) {
            document = (TLRPC.TL_document) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 1 : 4);
            if (document == null && !path.equals(originalPath) && !isEncrypted) {
                document = (TLRPC.TL_document) MessagesStorage.getInstance(currentAccount).getSentFile(path + f.length(), !isEncrypted ? 1 : 4);
            }
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
            fileName.file_name = name;
            document.attributes.add(fileName);
            document.size = (int) f.length();
            document.dc_id = 0;
            if (attributeAudio != null) {
                document.attributes.add(attributeAudio);
            }
            if (ext.length() != 0) {
                switch (extL) {
                    case "webp":
                        document.mime_type = "image/webp";
                        break;
                    case "opus":
                        document.mime_type = "audio/opus";
                        break;
                    case "ogg":
                        document.mime_type = "audio/ogg";
                        break;
                    case "flac":
                        document.mime_type = "audio/flac";
                        break;
                    default:
                        String mimeType = myMime.getMimeTypeFromExtension(extL);
                        if (mimeType != null) {
                            document.mime_type = mimeType;
                        } else {
                            document.mime_type = "application/octet-stream";
                        }
                        break;
                }
            } else {
                document.mime_type = "application/octet-stream";
            }
            if (document.mime_type.equals("image/gif") && (editingMessageObject == null || editingMessageObject.getGroupIdForUse() == 0)) {
                try {
                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90, true);
                    if (bitmap != null) {
                        fileName.file_name = "animation.gif";
                        document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                        bitmap.recycle();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (document.mime_type.equals("image/webp") && allowSticker && editingMessageObject == null) {
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                try {
                    bmOptions.inJustDecodeBounds = true;
                    RandomAccessFile file = new RandomAccessFile(path, "r");
                    ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
                    Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                    file.close();
                } catch (Exception e) {
                    FileLog.e(e);
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
        final String captionFinal = caption != null ? caption.toString() : "";

        final HashMap<String, String> params = new HashMap<>();
        if (originalPath != null) {
            params.put("originalPath", originalPath);
        }
        final TLRPC.TL_document documentFinal = document;
        final String pathFinal = path;
        AndroidUtilities.runOnUIThread(() -> {
            if (editingMessageObject != null) {
                SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, null, null, documentFinal, pathFinal, params, false);
            } else {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(documentFinal, null, pathFinal, dialog_id, reply_to_msg, captionFinal, entities, null, params, 0);
            }
        });
        return true;
    }

    @UiThread
    public static void prepareSendingDocument(String path, String originalPath, Uri uri, String mine, long dialog_id, MessageObject reply_to_msg, InputContentInfoCompat inputContent, MessageObject editingMessageObject) {
        if ((path == null || originalPath == null) && uri == null) {
            return;
        }
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> originalPaths = new ArrayList<>();
        ArrayList<Uri> uris = null;
        if (uri != null) {
            uris = new ArrayList<>();
            uris.add(uri);
        }
        if (path != null) {
            paths.add(path);
            originalPaths.add(originalPath);
        }
        prepareSendingDocuments(paths, originalPaths, uris, mine, dialog_id, reply_to_msg, inputContent, editingMessageObject);
    }

    @UiThread
    public static void prepareSendingAudioDocuments(final ArrayList<MessageObject> messageObjects, final long dialog_id, final MessageObject reply_to_msg, final MessageObject editingMessageObject) {
        final int currentAccount = UserConfig.selectedAccount;
        new Thread(() -> {
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
                    document = (TLRPC.TL_document) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 1 : 4);
                }
                if (document == null) {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                }

                if (isEncrypted) {
                    int high_id = (int) (dialog_id >> 32);
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                    if (encryptedChat == null) {
                        return;
                    }
                }

                final HashMap<String, String> params = new HashMap<>();
                if (originalPath != null) {
                    params.put("originalPath", originalPath);
                }
                final TLRPC.TL_document documentFinal = document;
                AndroidUtilities.runOnUIThread(() -> {
                    if (editingMessageObject != null) {
                        SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, null, null, documentFinal, messageObject.messageOwner.attachPath, params, false);
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(documentFinal, null, messageObject.messageOwner.attachPath, dialog_id, reply_to_msg, null, null, null, params, 0);
                    }
                });
            }
        }).start();
    }

    @UiThread
    public static void prepareSendingDocuments(final ArrayList<String> paths, final ArrayList<String> originalPaths, final ArrayList<Uri> uris, final String mime, final long dialog_id, final MessageObject reply_to_msg, final InputContentInfoCompat inputContent, final MessageObject editingMessageObject) {
        if (paths == null && originalPaths == null && uris == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        new Thread(() -> {
            boolean error = false;
            if (paths != null) {
                for (int a = 0; a < paths.size(); a++) {
                    if (!prepareSendingDocumentInternal(currentAccount, paths.get(a), originalPaths.get(a), null, mime, dialog_id, reply_to_msg, null, null, editingMessageObject)) {
                        error = true;
                    }
                }
            }
            if (uris != null) {
                for (int a = 0; a < uris.size(); a++) {
                    if (!prepareSendingDocumentInternal(currentAccount, null, null, uris.get(a), mime, dialog_id, reply_to_msg, null, null, editingMessageObject)) {
                        error = true;
                    }
                }
            }
            if (inputContent != null) {
                inputContent.releasePermission();
            }
            if (error) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        Toast toast = Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
                        toast.show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            }
        }).start();
    }

    @UiThread
    public static void prepareSendingPhoto(String imageFilePath, Uri imageUri, long dialog_id, MessageObject reply_to_msg, CharSequence caption, ArrayList<TLRPC.MessageEntity> entities, ArrayList<TLRPC.InputDocument> stickers, InputContentInfoCompat inputContent, int ttl, MessageObject editingMessageObject) {
        SendingMediaInfo info = new SendingMediaInfo();
        info.path = imageFilePath;
        info.uri = imageUri;
        if (caption != null) {
            info.caption = caption.toString();
        }
        info.entities = entities;
        info.ttl = ttl;
        if (stickers != null && !stickers.isEmpty()) {
            info.masks = new ArrayList<>(stickers);
        }
        ArrayList<SendingMediaInfo> infos = new ArrayList<>();
        infos.add(info);
        prepareSendingMedia(infos, dialog_id, reply_to_msg, inputContent, false, false, editingMessageObject);
    }

    @UiThread
    public static void prepareSendingBotContextResult(final TLRPC.BotInlineResult result, final HashMap<String, String> params, final long dialog_id, final MessageObject reply_to_msg) {
        if (result == null) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
            new Thread(() -> {
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
                    if (result.content != null) {
                        File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content.url) + "." + ImageLoader.getHttpUrlExtension(result.content.url, "file"));
                        if (f.exists()) {
                            finalPath = f.getAbsolutePath();
                        } else {
                            finalPath = result.content.url;
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
                                document.mime_type = result.content.mime_type;
                                document.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
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
                                            FileLog.e(e);
                                        }
                                        break;
                                    }
                                    case "voice": {
                                        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                        audio.duration = MessageObject.getInlineResultDuration(result);
                                        audio.voice = true;
                                        fileName.file_name = "audio.ogg";
                                        document.attributes.add(audio);

                                        document.thumb = new TLRPC.TL_photoSizeEmpty();
                                        document.thumb.type = "s";

                                        break;
                                    }
                                    case "audio": {
                                        TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                                        audio.duration = MessageObject.getInlineResultDuration(result);
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
                                        int idx = result.content.mime_type.lastIndexOf('/');
                                        if (idx != -1) {
                                            fileName.file_name = "file." + result.content.mime_type.substring(idx + 1);
                                        } else {
                                            fileName.file_name = "file";
                                        }
                                        break;
                                    }
                                    case "video": {
                                        fileName.file_name = "video.mp4";
                                        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                        int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                        attributeVideo.w = wh[0];
                                        attributeVideo.h = wh[1];
                                        attributeVideo.duration = MessageObject.getInlineResultDuration(result);
                                        attributeVideo.supports_streaming = true;
                                        document.attributes.add(attributeVideo);
                                        try {
                                            if (result.thumb != null) {
                                                String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "jpg")).getAbsolutePath();
                                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                                if (bitmap != null) {
                                                    document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                    bitmap.recycle();
                                                }
                                            }
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                        break;
                                    }
                                    case "sticker": {
                                        TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
                                        attributeSticker.alt = "";
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                        document.attributes.add(attributeSticker);
                                        TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                                        int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                        attributeImageSize.w = wh[0];
                                        attributeImageSize.h = wh[1];
                                        document.attributes.add(attributeImageSize);
                                        fileName.file_name = "sticker.webp";
                                        try {
                                            if (result.thumb != null) {
                                                String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "webp")).getAbsolutePath();
                                                Bitmap bitmap = ImageLoader.loadBitmap(thumbPath, null, 90, 90, true);
                                                if (bitmap != null) {
                                                    document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                    bitmap.recycle();
                                                }
                                            }
                                        } catch (Throwable e) {
                                            FileLog.e(e);
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
                                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                    document.thumb.w = wh[0];
                                    document.thumb.h = wh[1];
                                    document.thumb.size = 0;
                                    document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                    document.thumb.type = "x";
                                }
                                break;
                            }
                            case "photo": {
                                if (f.exists()) {
                                    photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(finalPath, null);
                                }
                                if (photo == null) {
                                    photo = new TLRPC.TL_photo();
                                    photo.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                                    TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                    int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                    photoSize.w = wh[0];
                                    photoSize.h = wh[1];
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
                if (params != null && result.content != null) {
                    params.put("originalPath", result.content.url);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (finalDocument != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(finalDocument, null, finalPathFinal, dialog_id, reply_to_msg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, 0);
                    } else if (finalPhoto != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(finalPhoto, result.content != null ? result.content.url : null, dialog_id, reply_to_msg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, 0);
                    } else if (finalGame != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(finalGame, dialog_id, result.send_message.reply_markup, params);
                    }
                });
            }).run();
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageText) {
            TLRPC.WebPage webPage = null;
            if ((int) dialog_id == 0) {
                for (int a = 0; a < result.send_message.entities.size(); a++) {
                    TLRPC.MessageEntity entity = result.send_message.entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        webPage = new TLRPC.TL_webPagePending();
                        webPage.url = result.send_message.message.substring(entity.offset, entity.offset + entity.length);
                        break;
                    }
                }
            }
            SendMessagesHelper.getInstance(currentAccount).sendMessage(result.send_message.message, dialog_id, reply_to_msg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue) {
            TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
            venue.geo = result.send_message.geo;
            venue.address = result.send_message.address;
            venue.title = result.send_message.title;
            venue.provider = result.send_message.provider;
            venue.venue_id = result.send_message.venue_id;
            venue.venue_type = venue.venue_id = result.send_message.venue_type;
            if (venue.venue_type == null) {
                venue.venue_type = "";
            }
            SendMessagesHelper.getInstance(currentAccount).sendMessage(venue, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
            if (result.send_message.period != 0) {
                TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                location.period = result.send_message.period;
                location.geo = result.send_message.geo;
                SendMessagesHelper.getInstance(currentAccount).sendMessage(location, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
            } else {
                TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                location.geo = result.send_message.geo;
                SendMessagesHelper.getInstance(currentAccount).sendMessage(location, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
            }
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaContact) {
            TLRPC.User user = new TLRPC.TL_user();
            user.phone = result.send_message.phone_number;
            user.first_name = result.send_message.first_name;
            user.last_name = result.send_message.last_name;
            SendMessagesHelper.getInstance(currentAccount).sendMessage(user, dialog_id, reply_to_msg, result.send_message.reply_markup, params);
        }
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

    @UiThread
    public static void prepareSendingText(final String text, final long dialog_id) {
        final int currentAccount = UserConfig.selectedAccount;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> Utilities.stageQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            String textFinal = getTrimmedString(text);
            if (textFinal.length() != 0) {
                int count = (int) Math.ceil(textFinal.length() / 4096.0f);
                for (int a = 0; a < count; a++) {
                    String mess = textFinal.substring(a * 4096, Math.min((a + 1) * 4096, textFinal.length()));
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(mess, dialog_id, null, null, true, null, null, null);
                }
            }
        })));
    }

    @UiThread
    public static void prepareSendingMedia(final ArrayList<SendingMediaInfo> media, final long dialog_id, final MessageObject reply_to_msg, final InputContentInfoCompat inputContent, final boolean forceDocument, final boolean groupPhotos, final MessageObject editingMessageObject) {
        if (media.isEmpty()) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        mediaSendQueue.postRunnable(() -> {
            long beginTime = System.currentTimeMillis();
            HashMap<SendingMediaInfo, MediaSendPrepareWorker> workers;
            int count = media.size();
            boolean isEncrypted = (int) dialog_id == 0;
            int enryptedLayer = 0;
            if (isEncrypted) {
                int high_id = (int) (dialog_id >> 32);
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    enryptedLayer = AndroidUtilities.getPeerLayerVersion(encryptedChat.layer);
                }
            }
            if ((!isEncrypted || enryptedLayer >= 73) && !forceDocument && groupPhotos) {
                workers = new HashMap<>();
                for (int a = 0; a < count; a++) {
                    final SendingMediaInfo info = media.get(a);
                    if (info.searchImage == null && !info.isVideo) {
                        String originalPath = info.path;
                        String tempPath = info.path;
                        if (tempPath == null && info.uri != null) {
                            tempPath = AndroidUtilities.getPath(info.uri);
                            originalPath = info.uri.toString();
                        }

                        if (tempPath != null && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp"))) {
                            continue;
                        } else if (tempPath == null && info.uri != null) {
                            if (MediaController.isGif(info.uri) || MediaController.isWebp(info.uri)) {
                                continue;
                            }
                        }

                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        } else {
                            originalPath = null;
                        }
                        TLRPC.TL_photo photo = null;
                        if (!isEncrypted && info.ttl == 0) {
                            photo = (TLRPC.TL_photo) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 0 : 3);
                            if (photo == null && info.uri != null) {
                                photo = (TLRPC.TL_photo) MessagesStorage.getInstance(currentAccount).getSentFile(AndroidUtilities.getPath(info.uri), !isEncrypted ? 0 : 3);
                            }
                        }
                        final MediaSendPrepareWorker worker = new MediaSendPrepareWorker();
                        workers.put(info, worker);
                        if (photo != null) {
                            worker.photo = photo;
                        } else {
                            worker.sync = new CountDownLatch(1);
                            mediaSendThreadPool.execute(() -> {
                                worker.photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(info.path, info.uri);
                                worker.sync.countDown();
                            });
                        }
                    }
                }
            } else {
                workers = null;
            }

            long groupId = 0;
            long lastGroupId = 0;

            ArrayList<String> sendAsDocuments = null;
            ArrayList<String> sendAsDocumentsOriginal = null;
            ArrayList<String> sendAsDocumentsCaptions = null;
            ArrayList<ArrayList<TLRPC.MessageEntity>> sendAsDocumentsEntities = null;

            String extension = null;
            int photosCount = 0;
            for (int a = 0; a < count; a++) {
                final SendingMediaInfo info = media.get(a);
                if (groupPhotos && (!isEncrypted || enryptedLayer >= 73) && count > 1 && photosCount % 10 == 0) {
                    lastGroupId = groupId = Utilities.random.nextLong();
                    photosCount = 0;
                }
                if (info.searchImage != null) {
                    if (info.searchImage.type == 1) {
                        final HashMap<String, String> params = new HashMap<>();
                        TLRPC.TL_document document = null;
                        File cacheFile;
                        if (info.searchImage.document instanceof TLRPC.TL_document) {
                            document = (TLRPC.TL_document) info.searchImage.document;
                            cacheFile = FileLoader.getPathToAttach(document, true);
                        } else {
                            if (!isEncrypted) {
                                TLRPC.Document doc = (TLRPC.Document) MessagesStorage.getInstance(currentAccount).getSentFile(info.searchImage.imageUrl, !isEncrypted ? 1 : 4);
                                if (doc instanceof TLRPC.TL_document) {
                                    document = (TLRPC.TL_document) doc;
                                }
                            }
                            String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        }
                        if (document == null) {
                            if (info.searchImage.localUrl != null) {
                                params.put("url", info.searchImage.localUrl);
                            }
                            File thumbFile = null;
                            document = new TLRPC.TL_document();
                            document.id = 0;
                            document.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                            fileName.file_name = "animation.gif";
                            document.attributes.add(fileName);
                            document.size = info.searchImage.size;
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
                                String thumb = Utilities.MD5(info.searchImage.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.thumbUrl, "jpg");
                                thumbFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), thumb);
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
                                    FileLog.e(e);
                                }
                            }
                            if (document.thumb == null) {
                                document.thumb = new TLRPC.TL_photoSize();
                                document.thumb.w = info.searchImage.width;
                                document.thumb.h = info.searchImage.height;
                                document.thumb.size = 0;
                                document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                document.thumb.type = "x";
                            }
                        }
                        final TLRPC.TL_document documentFinal = document;
                        final String originalPathFinal = info.searchImage.imageUrl;
                        final String pathFinal = cacheFile == null ? info.searchImage.imageUrl : cacheFile.toString();
                        if (params != null && info.searchImage.imageUrl != null) {
                            params.put("originalPath", info.searchImage.imageUrl);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (editingMessageObject != null) {
                                SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, null, null, documentFinal, pathFinal, params, false);
                            } else {
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(documentFinal, null, pathFinal, dialog_id, reply_to_msg, info.caption, info.entities, null, params, 0);
                            }
                        });
                    } else {
                        boolean needDownloadHttp = true;
                        TLRPC.TL_photo photo = null;
                        if (info.searchImage.photo instanceof TLRPC.TL_photo) {
                            photo = (TLRPC.TL_photo) info.searchImage.photo;
                        } else {
                            if (!isEncrypted && info.ttl == 0) {
                                photo = (TLRPC.TL_photo) MessagesStorage.getInstance(currentAccount).getSentFile(info.searchImage.imageUrl, !isEncrypted ? 0 : 3);
                            }
                        }
                        if (photo == null) {
                            String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                            File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                            if (cacheFile.exists() && cacheFile.length() != 0) {
                                photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(cacheFile.toString(), null);
                                if (photo != null) {
                                    needDownloadHttp = false;
                                }
                            }
                            if (photo == null) {
                                md5 = Utilities.MD5(info.searchImage.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.thumbUrl, "jpg");
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                                if (cacheFile.exists()) {
                                    photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(cacheFile.toString(), null);
                                }
                                if (photo == null) {
                                    photo = new TLRPC.TL_photo();
                                    photo.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                                    TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                    photoSize.w = info.searchImage.width;
                                    photoSize.h = info.searchImage.height;
                                    photoSize.size = 0;
                                    photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                                    photoSize.type = "x";
                                    photo.sizes.add(photoSize);
                                }
                            }
                        }
                        if (photo != null) {
                            final TLRPC.TL_photo photoFinal = photo;
                            final boolean needDownloadHttpFinal = needDownloadHttp;
                            final HashMap<String, String> params = new HashMap<>();
                            if (info.searchImage.imageUrl != null) {
                                params.put("originalPath", info.searchImage.imageUrl);
                            }
                            if (groupPhotos) {
                                photosCount++;
                                params.put("groupId", "" + groupId);
                                if (photosCount == 10 || a == count -1) {
                                    params.put("final", "1");
                                    lastGroupId = 0;
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                if (editingMessageObject != null) {
                                    SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, photoFinal, null, null, needDownloadHttpFinal ? info.searchImage.imageUrl : null, params, false);
                                } else {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(photoFinal, needDownloadHttpFinal ? info.searchImage.imageUrl : null, dialog_id, reply_to_msg, info.caption, info.entities, null, params, info.ttl);
                                }
                            });
                        }
                    }
                } else {
                    if (info.isVideo) {
                        Bitmap thumb = null;
                        String thumbKey = null;

                        final VideoEditedInfo videoEditedInfo;
                        if (forceDocument) {
                            videoEditedInfo = null;
                        } else {
                            videoEditedInfo = info.videoEditedInfo != null ? info.videoEditedInfo : createCompressionSettings(info.path);
                        }

                        if (!forceDocument && (videoEditedInfo != null || info.path.endsWith("mp4"))) {
                            String path = info.path;
                            String originalPath = info.path;
                            File temp = new File(originalPath);
                            long startTime = 0;
                            boolean muted = false;

                            originalPath += temp.length() + "_" + temp.lastModified();
                            if (videoEditedInfo != null) {
                                muted = videoEditedInfo.muted;
                                originalPath += videoEditedInfo.estimatedDuration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime + (videoEditedInfo.muted ? "_m" : "");
                                if (videoEditedInfo.resultWidth != videoEditedInfo.originalWidth) {
                                    originalPath += "_" + videoEditedInfo.resultWidth;
                                }
                                startTime = videoEditedInfo.startTime >= 0 ? videoEditedInfo.startTime : 0;
                            }
                            TLRPC.TL_document document = null;
                            if (!isEncrypted && info.ttl == 0) {
                                document = (TLRPC.TL_document) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 2 : 5);
                            }
                            if (document == null) {
                                thumb = createVideoThumbnail(info.path, startTime);
                                if (thumb == null) {
                                    thumb = ThumbnailUtils.createVideoThumbnail(info.path, MediaStore.Video.Thumbnails.MINI_KIND);
                                }
                                TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, isEncrypted);
                                if (thumb != null && size != null) {
                                    thumb = null;
                                }
                                document = new TLRPC.TL_document();
                                document.thumb = size;
                                if (document.thumb == null) {
                                    document.thumb = new TLRPC.TL_photoSizeEmpty();
                                    document.thumb.type = "s";
                                } else {
                                    document.thumb.type = "s";
                                }
                                document.mime_type = "video/mp4";
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                                TLRPC.TL_documentAttributeVideo attributeVideo;
                                if (isEncrypted) {
                                    if (enryptedLayer >= 66) {
                                        attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                    } else {
                                        attributeVideo = new TLRPC.TL_documentAttributeVideo_layer65();
                                    }
                                } else {
                                    attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                    attributeVideo.supports_streaming = true;
                                }
                                document.attributes.add(attributeVideo);
                                if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
                                    if (videoEditedInfo.muted) {
                                        document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                        fillVideoAttribute(info.path, attributeVideo, videoEditedInfo);
                                        videoEditedInfo.originalWidth = attributeVideo.w;
                                        videoEditedInfo.originalHeight = attributeVideo.h;
                                        attributeVideo.w = videoEditedInfo.resultWidth;
                                        attributeVideo.h = videoEditedInfo.resultHeight;
                                    } else {
                                        attributeVideo.duration = (int) (videoEditedInfo.estimatedDuration / 1000);
                                        if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                                            attributeVideo.w = videoEditedInfo.resultHeight;
                                            attributeVideo.h = videoEditedInfo.resultWidth;
                                        } else {
                                            attributeVideo.w = videoEditedInfo.resultWidth;
                                            attributeVideo.h = videoEditedInfo.resultHeight;
                                        }
                                    }
                                    document.size = (int) videoEditedInfo.estimatedSize;
                                    String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".mp4";
                                    File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                                    SharedConfig.saveConfig();
                                    path = cacheFile.getAbsolutePath();
                                } else {
                                    if (temp.exists()) {
                                        document.size = (int) temp.length();
                                    }
                                    fillVideoAttribute(info.path, attributeVideo, null);
                                }
                            }
                            final TLRPC.TL_document videoFinal = document;
                            final String originalPathFinal = originalPath;
                            final String finalPath = path;
                            final HashMap<String, String> params = new HashMap<>();
                            final Bitmap thumbFinal = thumb;
                            final String thumbKeyFinal = thumbKey;
                            if (originalPath != null) {
                                params.put("originalPath", originalPath);
                            }
                            if (!muted && groupPhotos) {
                                photosCount++;
                                params.put("groupId", "" + groupId);
                                if (photosCount == 10 || a == count -1) {
                                    params.put("final", "1");
                                    lastGroupId = 0;
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                if (thumbFinal != null && thumbKeyFinal != null) {
                                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(thumbFinal), thumbKeyFinal);
                                }
                                if (editingMessageObject != null) {
                                    SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false);
                                } else {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(videoFinal, videoEditedInfo, finalPath, dialog_id, reply_to_msg, info.caption, info.entities, null, params, info.ttl);
                                }
                            });
                        } else {
                            prepareSendingDocumentInternal(currentAccount, info.path, info.path, null, null, dialog_id, reply_to_msg, info.caption, info.entities, editingMessageObject);
                        }
                    } else {
                        String originalPath = info.path;
                        String tempPath = info.path;
                        if (tempPath == null && info.uri != null) {
                            tempPath = AndroidUtilities.getPath(info.uri);
                            originalPath = info.uri.toString();
                        }

                        boolean isDocument = false;
                        if (forceDocument) {
                            isDocument = true;
                            extension = FileLoader.getFileExtension(new File(tempPath));
                        } else if (tempPath != null && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp"))) {
                            if (tempPath.endsWith(".gif")) {
                                extension = "gif";
                            } else {
                                extension = "webp";
                            }
                            isDocument = true;
                        } else if (tempPath == null && info.uri != null) {
                            if (MediaController.isGif(info.uri)) {
                                isDocument = true;
                                originalPath = info.uri.toString();
                                tempPath = MediaController.copyFileToCache(info.uri, "gif");
                                extension = "gif";
                            } else if (MediaController.isWebp(info.uri)) {
                                isDocument = true;
                                originalPath = info.uri.toString();
                                tempPath = MediaController.copyFileToCache(info.uri, "webp");
                                extension = "webp";
                            }
                        }

                        if (isDocument) {
                            if (sendAsDocuments == null) {
                                sendAsDocuments = new ArrayList<>();
                                sendAsDocumentsOriginal = new ArrayList<>();
                                sendAsDocumentsCaptions = new ArrayList<>();
                                sendAsDocumentsEntities = new ArrayList<>();
                            }
                            sendAsDocuments.add(tempPath);
                            sendAsDocumentsOriginal.add(originalPath);
                            sendAsDocumentsCaptions.add(info.caption);
                            sendAsDocumentsEntities.add(info.entities);
                        } else {
                            if (tempPath != null) {
                                File temp = new File(tempPath);
                                originalPath += temp.length() + "_" + temp.lastModified();
                            } else {
                                originalPath = null;
                            }
                            TLRPC.TL_photo photo = null;
                            if (workers != null) {
                                MediaSendPrepareWorker worker = workers.get(info);
                                photo = worker.photo;
                                if (photo == null) {
                                    try {
                                        worker.sync.await();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    photo = worker.photo;
                                }
                            } else {
                                if (!isEncrypted && info.ttl == 0) {
                                    photo = (TLRPC.TL_photo) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 0 : 3);
                                    if (photo == null && info.uri != null) {
                                        photo = (TLRPC.TL_photo) MessagesStorage.getInstance(currentAccount).getSentFile(AndroidUtilities.getPath(info.uri), !isEncrypted ? 0 : 3);
                                    }
                                }
                                if (photo == null) {
                                    photo = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(info.path, info.uri);
                                }
                            }
                            if (photo != null) {
                                final TLRPC.TL_photo photoFinal = photo;
                                final HashMap<String, String> params = new HashMap<>();
                                if (photo.has_stickers = info.masks != null && !info.masks.isEmpty()) {
                                    SerializedData serializedData = new SerializedData(4 + info.masks.size() * 20);
                                    serializedData.writeInt32(info.masks.size());
                                    for (int b = 0; b < info.masks.size(); b++) {
                                        info.masks.get(b).serializeToStream(serializedData);
                                    }
                                    params.put("masks", Utilities.bytesToHex(serializedData.toByteArray()));
                                    serializedData.cleanup();
                                }
                                if (originalPath != null) {
                                    params.put("originalPath", originalPath);
                                }
                                if (groupPhotos) {
                                    photosCount++;
                                    params.put("groupId", "" + groupId);
                                    if (photosCount == 10 || a == count - 1) {
                                        params.put("final", "1");
                                        lastGroupId = 0;
                                    }
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (editingMessageObject != null) {
                                        SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, photoFinal, null, null, null, params, false);
                                    } else {
                                        SendMessagesHelper.getInstance(currentAccount).sendMessage(photoFinal, null, dialog_id, reply_to_msg, info.caption, info.entities, null, params, info.ttl);
                                    }
                                });
                            } else {
                                if (sendAsDocuments == null) {
                                    sendAsDocuments = new ArrayList<>();
                                    sendAsDocumentsOriginal = new ArrayList<>();
                                    sendAsDocumentsCaptions = new ArrayList<>();
                                    sendAsDocumentsEntities = new ArrayList<>();
                                }
                                sendAsDocuments.add(tempPath);
                                sendAsDocumentsOriginal.add(originalPath);
                                sendAsDocumentsCaptions.add(info.caption);
                                sendAsDocumentsEntities.add(info.entities);
                            }
                        }
                    }
                }
            }
            if (lastGroupId != 0) {
                final long lastGroupIdFinal = lastGroupId;
                AndroidUtilities.runOnUIThread(() -> {
                    SendMessagesHelper instance = getInstance(currentAccount);
                    ArrayList<DelayedMessage> arrayList = instance.delayedMessages.get("group_" + lastGroupIdFinal);
                    if (arrayList != null && !arrayList.isEmpty()) {

                        DelayedMessage message = arrayList.get(0);

                        MessageObject prevMessage = message.messageObjects.get(message.messageObjects.size() - 1);
                        message.finalGroupMessage = prevMessage.getId();
                        prevMessage.messageOwner.params.put("final", "1");

                        TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                        messagesRes.messages.add(prevMessage.messageOwner);
                        MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, message.peer, -2, 0, false);
                        instance.sendReadyToSendGroup(message, true, true);
                    }
                });
            }
            if (inputContent != null) {
                inputContent.releasePermission();
            }
            if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                for (int a = 0; a < sendAsDocuments.size(); a++) {
                    prepareSendingDocumentInternal(currentAccount, sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a), null, extension, dialog_id, reply_to_msg, sendAsDocumentsCaptions.get(a), sendAsDocumentsEntities.get(a), editingMessageObject);
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("total send time = " + (System.currentTimeMillis() - beginTime));
            }
        });
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
            FileLog.e(e);
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
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
                FileLog.e(e);
            }
        }
    }

    private static Bitmap createVideoThumbnail(String filePath, long time) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_NEXT_SYNC);
        } catch (Exception ignore) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }

        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max > 90) {
            float scale = 90.0f / max;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            Bitmap scaled = Bitmaps.createScaledBitmap(bitmap, w, h, true);
            if (scaled != bitmap) {
                bitmap.recycle();
                bitmap = scaled;
            }
        }

        return bitmap;
    }

    private static VideoEditedInfo createCompressionSettings(String videoPath) {
        TrackHeaderBox trackHeaderBox = null;
        int originalBitrate = 0;
        int bitrate = 0;
        float videoDuration = 0.0f;
        long videoFramesSize = 0;
        long audioFramesSize = 0;
        int videoFramerate = 25;
        try {
            IsoFile isoFile = new IsoFile(videoPath);
            List<Box> boxes = Path.getPaths(isoFile, "/moov/trak/");

            Box boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/mp4a/");
            if (boxTest == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("video hasn't mp4a atom");
                }
            }

            boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/avc1/");
            if (boxTest == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("video hasn't avc1 atom");
                }
                return null;
            }

            for (int b = 0; b < boxes.size(); b++) {
                Box box = boxes.get(b);
                TrackBox trackBox = (TrackBox) box;
                long sampleSizes = 0;
                long trackBitrate = 0;
                MediaBox mediaBox = null;
                MediaHeaderBox mediaHeaderBox = null;
                try {
                    mediaBox = trackBox.getMediaBox();
                    mediaHeaderBox = mediaBox.getMediaHeaderBox();
                    SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                    long[] sizes = sampleSizeBox.getSampleSizes();
                    for (int a = 0; a < sizes.length; a++) {
                        sampleSizes += sizes[a];
                    }
                    videoDuration = (float) mediaHeaderBox.getDuration() / (float) mediaHeaderBox.getTimescale();
                    trackBitrate = (int) (sampleSizes * 8 / videoDuration);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                TrackHeaderBox headerBox = trackBox.getTrackHeaderBox();
                if (headerBox.getWidth() != 0 && headerBox.getHeight() != 0) {
                    if (trackHeaderBox == null || trackHeaderBox.getWidth() < headerBox.getWidth() || trackHeaderBox.getHeight() < headerBox.getHeight()) {
                        trackHeaderBox = headerBox;
                        originalBitrate = bitrate = (int) (trackBitrate / 100000 * 100000);
                        if (bitrate > 900000) {
                            bitrate = 900000;
                        }
                        videoFramesSize += sampleSizes;

                        if (mediaBox != null && mediaHeaderBox != null) {
                            TimeToSampleBox timeToSampleBox = mediaBox.getMediaInformationBox().getSampleTableBox().getTimeToSampleBox();
                            if (timeToSampleBox != null) {
                                List<TimeToSampleBox.Entry> entries = timeToSampleBox.getEntries();
                                long delta = 0;
                                int size = Math.min(entries.size(), 11);
                                for (int a = 1; a < size; a++) {
                                    delta += entries.get(a).getDelta();
                                }
                                if (delta != 0) {
                                    videoFramerate = (int) ((double) mediaHeaderBox.getTimescale() / (delta / (size - 1)));
                                }
                            }
                        }
                    }
                } else {
                    audioFramesSize += sampleSizes;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        if (trackHeaderBox == null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("video hasn't trackHeaderBox atom");
            }
            return null;
        }

        if (Build.VERSION.SDK_INT < 18) {
            try {
                MediaCodecInfo codecInfo = MediaController.selectCodec(MediaController.MIME_TYPE);
                if (codecInfo == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("no codec info for " + MediaController.MIME_TYPE);
                    }
                    return null;
                } else {
                    String name = codecInfo.getName();
                    if (name.equals("OMX.google.h264.encoder") ||
                            name.equals("OMX.ST.VFM.H264Enc") ||
                            name.equals("OMX.Exynos.avc.enc") ||
                            name.equals("OMX.MARVELL.VIDEO.HW.CODA7542ENCODER") ||
                            name.equals("OMX.MARVELL.VIDEO.H264ENCODER") ||
                            name.equals("OMX.k3.video.encoder.avc") ||
                            name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("unsupported encoder = " + name);
                        }
                        return null;
                    } else {
                        if (MediaController.selectColorFormat(codecInfo, MediaController.MIME_TYPE) == 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("no color format for " + MediaController.MIME_TYPE);
                            }
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        videoDuration *= 1000;

        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = -1;
        videoEditedInfo.endTime = -1;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.originalPath = videoPath;
        videoEditedInfo.framerate = videoFramerate;
        videoEditedInfo.estimatedDuration = (long) Math.ceil(videoDuration);
        videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = (int) trackHeaderBox.getWidth();
        videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = (int) trackHeaderBox.getHeight();

        Matrix matrix = trackHeaderBox.getMatrix();
        if (matrix.equals(Matrix.ROTATE_90)) {
            videoEditedInfo.rotationValue = 90;
        } else if (matrix.equals(Matrix.ROTATE_180)) {
            videoEditedInfo.rotationValue = 180;
        } else if (matrix.equals(Matrix.ROTATE_270)) {
            videoEditedInfo.rotationValue = 270;
        } else {
            videoEditedInfo.rotationValue = 0;
        }

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int selectedCompression = preferences.getInt("compress_video2", 1);
        int compressionsCount;
        if (videoEditedInfo.originalWidth > 1280 || videoEditedInfo.originalHeight > 1280) {
            compressionsCount = 5;
        } else if (videoEditedInfo.originalWidth > 848 || videoEditedInfo.originalHeight > 848) {
            compressionsCount = 4;
        } else if (videoEditedInfo.originalWidth > 640 || videoEditedInfo.originalHeight > 640) {
            compressionsCount = 3;
        } else if (videoEditedInfo.originalWidth > 480 || videoEditedInfo.originalHeight > 480) {
            compressionsCount = 2;
        } else {
            compressionsCount = 1;
        }

        if (selectedCompression >= compressionsCount) {
            selectedCompression = compressionsCount - 1;
        }
        if (selectedCompression != compressionsCount - 1) {
            float maxSize;
            int targetBitrate;
            switch (selectedCompression) {
                case 0:
                    maxSize = 432.0f;
                    targetBitrate = 400000;
                    break;
                case 1:
                    maxSize = 640.0f;
                    targetBitrate = 900000;
                    break;
                case 2:
                    maxSize = 848.0f;
                    targetBitrate = 1100000;
                    break;
                case 3:
                default:
                    targetBitrate = 2500000;
                    maxSize = 1280.0f;
                    break;
            }
            float scale = videoEditedInfo.originalWidth > videoEditedInfo.originalHeight ? maxSize / videoEditedInfo.originalWidth : maxSize / videoEditedInfo.originalHeight;
            videoEditedInfo.resultWidth = Math.round(videoEditedInfo.originalWidth * scale / 2) * 2;
            videoEditedInfo.resultHeight = Math.round(videoEditedInfo.originalHeight * scale / 2) * 2;
            if (bitrate != 0) {
                bitrate = Math.min(targetBitrate, (int) (originalBitrate / scale));
                videoFramesSize = (long) (bitrate / 8 * videoDuration / 1000);
            }
        }

        if (selectedCompression == compressionsCount - 1) {
            videoEditedInfo.resultWidth = videoEditedInfo.originalWidth;
            videoEditedInfo.resultHeight = videoEditedInfo.originalHeight;
            videoEditedInfo.bitrate = originalBitrate;
            videoEditedInfo.estimatedSize = (int) (new File(videoPath).length());
        } else {
            videoEditedInfo.bitrate = bitrate;
            videoEditedInfo.estimatedSize = (int) (audioFramesSize + videoFramesSize);
            videoEditedInfo.estimatedSize += videoEditedInfo.estimatedSize / (32 * 1024) * 16;
        }

        return videoEditedInfo;
    }

    @UiThread
    public static void prepareSendingVideo(final String videoPath, final long estimatedSize, final long duration, final int width, final int height, final VideoEditedInfo info, final long dialog_id, final MessageObject reply_to_msg, final CharSequence caption, final ArrayList<TLRPC.MessageEntity> entities, final int ttl, final MessageObject editingMessageObject) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        new Thread(() -> {

            final VideoEditedInfo videoEditedInfo = info != null ? info : createCompressionSettings(videoPath);

            boolean isEncrypted = (int) dialog_id == 0;

            boolean isRound = videoEditedInfo != null && videoEditedInfo.roundVideo;
            Bitmap thumb = null;
            String thumbKey = null;

            if (videoEditedInfo != null || videoPath.endsWith("mp4") || isRound) {
                String path = videoPath;
                String originalPath = videoPath;
                File temp = new File(originalPath);
                long startTime = 0;

                originalPath += temp.length() + "_" + temp.lastModified();
                if (videoEditedInfo != null) {
                    if (!isRound) {
                        originalPath += duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime + (videoEditedInfo.muted ? "_m" : "");
                        if (videoEditedInfo.resultWidth != videoEditedInfo.originalWidth) {
                            originalPath += "_" + videoEditedInfo.resultWidth;
                        }
                    }
                    startTime = videoEditedInfo.startTime >= 0 ? videoEditedInfo.startTime : 0;
                }
                TLRPC.TL_document document = null;
                if (!isEncrypted && ttl == 0) {
                    document = (TLRPC.TL_document) MessagesStorage.getInstance(currentAccount).getSentFile(originalPath, !isEncrypted ? 2 : 5);
                }
                if (document == null) {
                    thumb = createVideoThumbnail(videoPath, startTime);
                    if (thumb == null) {
                        thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    }
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, isEncrypted);
                    if (thumb != null && size !=null) {
                        if (isRound) {
                            if (isEncrypted) {
                                Utilities.blurBitmap(thumb, 7, Build.VERSION.SDK_INT < 21 ? 0 : 1, thumb.getWidth(), thumb.getHeight(), thumb.getRowBytes());
                                thumbKey = String.format(size.location.volume_id + "_" + size.location.local_id + "@%d_%d_b2", (int) (AndroidUtilities.roundMessageSize / AndroidUtilities.density), (int) (AndroidUtilities.roundMessageSize / AndroidUtilities.density));
                            } else {
                                Utilities.blurBitmap(thumb, 3, Build.VERSION.SDK_INT < 21 ? 0 : 1, thumb.getWidth(), thumb.getHeight(), thumb.getRowBytes());
                                thumbKey = String.format(size.location.volume_id + "_" + size.location.local_id + "@%d_%d_b", (int) (AndroidUtilities.roundMessageSize / AndroidUtilities.density), (int) (AndroidUtilities.roundMessageSize / AndroidUtilities.density));
                            }
                        } else {
                            thumb = null;
                        }
                    }
                    document = new TLRPC.TL_document();
                    document.thumb = size;
                    if (document.thumb == null) {
                        document.thumb = new TLRPC.TL_photoSizeEmpty();
                        document.thumb.type = "s";
                    } else {
                        document.thumb.type = "s";
                    }
                    document.mime_type = "video/mp4";
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    TLRPC.TL_documentAttributeVideo attributeVideo;
                    if (isEncrypted) {
                        int high_id = (int) (dialog_id >> 32);
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                        if (encryptedChat == null) {
                            return;
                        }
                        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 66) {
                            attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        } else {
                            attributeVideo = new TLRPC.TL_documentAttributeVideo_layer65();
                        }
                    } else {
                        attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        attributeVideo.supports_streaming = true;
                    }
                    attributeVideo.round_message = isRound;
                    document.attributes.add(attributeVideo);
                    if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
                        if (videoEditedInfo.muted) {
                            document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                            fillVideoAttribute(videoPath, attributeVideo, videoEditedInfo);
                            videoEditedInfo.originalWidth = attributeVideo.w;
                            videoEditedInfo.originalHeight = attributeVideo.h;
                            attributeVideo.w = videoEditedInfo.resultWidth;
                            attributeVideo.h = videoEditedInfo.resultHeight;
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
                        String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".mp4";
                        File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                        SharedConfig.saveConfig();
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
                final Bitmap thumbFinal = thumb;
                final String thumbKeyFinal = thumbKey;
                final String captionFinal = caption != null ? caption.toString() : "";
                if (originalPath != null) {
                    params.put("originalPath", originalPath);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (thumbFinal != null && thumbKeyFinal != null) {
                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(thumbFinal), thumbKeyFinal);
                    }
                    if (editingMessageObject != null) {
                        SendMessagesHelper.getInstance(currentAccount).editMessageMedia(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false);
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(videoFinal, videoEditedInfo, finalPath, dialog_id, reply_to_msg, captionFinal, entities, null, params, ttl);
                    }
                });
            } else {
                prepareSendingDocumentInternal(currentAccount, videoPath, videoPath, null, null, dialog_id, reply_to_msg, caption, entities, editingMessageObject);
            }
        }).start();
    }
}
