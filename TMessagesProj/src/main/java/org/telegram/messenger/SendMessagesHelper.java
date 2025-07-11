/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaCodecInfo;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.json.JSONObject;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TdApi;
import org.drinkless.td.libcore.telegram.Client;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.Stories.MessageMediaStoryFull;
import org.telegram.ui.TwoStepVerificationActivity;
import org.telegram.ui.TwoStepVerificationSetupActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SendMessagesHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    public static final int MEDIA_TYPE_DICE = 11;
    public static final int MEDIA_TYPE_STORY = 12;
    private final HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<>();
    private final SparseArray<MessageObject> unsentMessages = new SparseArray<>();
    private final SparseArray<TLRPC.Message> sendingMessages = new SparseArray<>();
    private final SparseArray<TLRPC.Message> editingMessages = new SparseArray<>();
    private final SparseArray<TLRPC.Message> uploadMessages = new SparseArray<>();
    private final LongSparseArray<Integer> sendingMessagesIdDialogs = new LongSparseArray<>();
    private final LongSparseArray<Integer> uploadingMessagesIdDialogs = new LongSparseArray<>();
    private final HashMap<String, MessageObject> waitingForLocation = new HashMap<>();
    private final HashMap<String, Boolean> waitingForCallback = new HashMap<>();
    private final HashMap<String, List<String>> waitingForCallbackMap = new HashMap<>();
    private final HashMap<String, byte[]> waitingForVote = new HashMap<>();
    private final LongSparseArray<Long> voteSendTime = new LongSparseArray();
    private final HashMap<String, ImportingHistory> importingHistoryFiles = new HashMap<>();
    private final LongSparseArray<ImportingHistory> importingHistoryMap = new LongSparseArray<>();

    private final HashMap<String, ImportingStickers> importingStickersFiles = new HashMap<>();
    private final HashMap<String, ImportingStickers> importingStickersMap = new HashMap<>();

    public static boolean checkUpdateStickersOrder(CharSequence text) {
        if (text instanceof Spannable) {
            AnimatedEmojiSpan[] spans = ((Spannable)text).getSpans(0, text.length(), AnimatedEmojiSpan.class);
            for (int i = 0; i < spans.length; i++) {
                if (spans[i].fromEmojiKeyboard) {
                    return true;
                }
            }
        }
        return false;
    }

    public TLRPC.InputReplyTo createReplyInput(TL_stories.StoryItem storyItem) {
        TLRPC.TL_inputReplyToStory replyTo = new TLRPC.TL_inputReplyToStory();
        replyTo.story_id = storyItem.id;
        replyTo.peer = getMessagesController().getInputPeer(storyItem.dialogId);
        return replyTo;
    }

    public TLRPC.InputReplyTo createReplyInput(int replyToMsgId) {
        return createReplyInput(null, replyToMsgId, 0, null);
    }

    public TLRPC.InputReplyTo createReplyInput(TLRPC.InputPeer sendToPeer, int replyToMsgId, int topMessageId, ChatActivity.ReplyQuote replyQuote) {
        TLRPC.TL_inputReplyToMessage replyTo = new TLRPC.TL_inputReplyToMessage();
        replyTo.reply_to_msg_id = replyToMsgId;
        if (topMessageId != 0) {
            replyTo.flags |= 1;
            replyTo.top_msg_id = topMessageId;
        }
        if (replyQuote != null) {
            replyTo.quote_text = replyQuote.getText();
            if (!TextUtils.isEmpty(replyTo.quote_text)) {
                replyTo.flags |= 4;
                replyTo.quote_entities = replyQuote.getEntities();
                if (replyTo.quote_entities != null && !replyTo.quote_entities.isEmpty()) {
                    replyTo.quote_entities = new ArrayList<>(replyTo.quote_entities);
                    replyTo.flags |= 8;
                }
                replyTo.flags |= 16;
                replyTo.quote_offset = replyQuote.start;
            }
        }
        if (replyQuote != null && replyQuote.message != null) {
            long did = replyQuote.message.getDialogId();
            TLRPC.InputPeer peer = getMessagesController().getInputPeer(did);
            if (peer != null && !MessageObject.peersEqual(peer, sendToPeer)) {
                replyTo.flags |= 2;
                replyTo.reply_to_peer_id = peer;
            }
        }
        return replyTo;
    }

    public TLRPC.InputReplyTo createReplyInput(TLRPC.TL_messageReplyHeader replyHeader) {
        TLRPC.TL_inputReplyToMessage replyTo = new TLRPC.TL_inputReplyToMessage();
        replyTo.reply_to_msg_id = replyHeader.reply_to_msg_id;
        if ((replyHeader.flags & 2) != 0) {
            replyTo.flags |= 1;
            replyTo.top_msg_id = replyHeader.reply_to_top_id;
        }
        if ((replyHeader.flags & 1) != 0) {
            replyTo.flags |= 2;
            replyTo.reply_to_peer_id = MessagesController.getInstance(currentAccount).getInputPeer(replyHeader.reply_to_peer_id);
        }
        if (replyHeader.quote) {
            if ((replyHeader.flags & 64) != 0) {
                replyTo.flags |= 4;
                replyTo.quote_text = replyHeader.quote_text;
            }
            if ((replyHeader.flags & 128) != 0) {
                replyTo.flags |= 8;
                replyTo.quote_entities = replyHeader.quote_entities;
            }
            if ((replyHeader.flags & 1024) != 0) {
                replyTo.flags |= 16;
                replyTo.quote_offset = replyHeader.quote_offset;
            }
        }
        return replyTo;
    }

    public class ImportingHistory {
        public String historyPath;
        public ArrayList<Uri> mediaPaths = new ArrayList<>();
        public HashSet<String> uploadSet = new HashSet<>();
        public HashMap<String, Float> uploadProgresses = new HashMap<>();
        public HashMap<String, Long> uploadSize = new HashMap<>();
        public ArrayList<String> uploadMedia = new ArrayList<>();
        public TLRPC.InputPeer peer;
        public long totalSize;
        public long uploadedSize;
        public long dialogId;
        public long importId;
        public int uploadProgress;
        public double estimatedUploadSpeed;
        private long lastUploadTime;
        private long lastUploadSize;
        public int timeUntilFinish = Integer.MAX_VALUE;

        private void initImport(TLRPC.InputFile inputFile) {
            TLRPC.TL_messages_initHistoryImport req = new TLRPC.TL_messages_initHistoryImport();
            req.file = inputFile;
            req.media_count = mediaPaths.size();
            req.peer = peer;
            getConnectionsManager().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_messages_historyImport) {
                            importId = ((TLRPC.TL_messages_historyImport) response).id;
                            uploadSet.remove(historyPath);
                            getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
                            if (uploadSet.isEmpty()) {
                                startImport();
                            }
                            lastUploadTime = SystemClock.elapsedRealtime();
                            for (int a = 0, N = uploadMedia.size(); a < N; a++) {
                                getFileLoader().uploadFile(uploadMedia.get(a), false, true, ConnectionsManager.FileTypeFile);
                            }
                        } else {
                            importingHistoryMap.remove(dialogId);
                            getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, req, error);
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        public long getUploadedCount() {
            return uploadedSize;
        }

        public long getTotalCount() {
            return totalSize;
        }

        private void onFileFailedToUpload(String path) {
            if (path.equals(historyPath)) {
                importingHistoryMap.remove(dialogId);
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.code = 400;
                error.text = "IMPORT_UPLOAD_FAILED";
                getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, new TLRPC.TL_messages_initHistoryImport(), error);
            } else {
                uploadSet.remove(path);
            }
        }
        
        private void addUploadProgress(String path, long sz, float progress) {
            uploadProgresses.put(path, progress);
            uploadSize.put(path, sz);
            uploadedSize = 0;
            for (HashMap.Entry<String, Long> entry : uploadSize.entrySet()) {
                uploadedSize += entry.getValue();
            }
            long newTime = SystemClock.elapsedRealtime();
            if (!path.equals(historyPath) && uploadedSize != lastUploadSize && newTime != lastUploadTime) {
                double dt = (newTime - lastUploadTime) / 1000.0;
                double uploadSpeed = (uploadedSize - lastUploadSize) / dt;
                if (estimatedUploadSpeed == 0) {
                    estimatedUploadSpeed = uploadSpeed;
                } else {
                    double coef = 0.01;
                    estimatedUploadSpeed = coef * uploadSpeed + (1 - coef) * estimatedUploadSpeed;
                }
                timeUntilFinish = (int) ((totalSize - uploadedSize) * 1000 / (double) estimatedUploadSpeed);
                lastUploadSize = uploadedSize;
                lastUploadTime = newTime;
            }
            float pr = getUploadedCount() / (float) getTotalCount();
            int newProgress = (int) (pr * 100);
            if (uploadProgress != newProgress) {
                uploadProgress = newProgress;
                getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
            }
        }

        private void onMediaImport(String path, long size, TLRPC.InputFile inputFile) {
            addUploadProgress(path, size, 1.0f);
            TLRPC.TL_messages_uploadImportedMedia req = new TLRPC.TL_messages_uploadImportedMedia();
            req.peer = peer;
            req.import_id = importId;
            req.file_name = new File(path).getName();

            MimeTypeMap myMime = MimeTypeMap.getSingleton();
            String ext = "txt";
            int idx = req.file_name.lastIndexOf('.');
            if (idx != -1) {
                ext = req.file_name.substring(idx + 1).toLowerCase();
            }
            String mimeType = myMime.getMimeTypeFromExtension(ext);
            if (mimeType == null) {
                if ("opus".equals(ext)) {
                    mimeType = "audio/opus";
                } else if ("webp".equals(ext)) {
                    mimeType = "image/webp";
                } else {
                    mimeType = "text/plain";
                }
            }
            if (mimeType.equals("image/jpg") || mimeType.equals("image/jpeg")) {
                TLRPC.TL_inputMediaUploadedPhoto inputMediaUploadedPhoto = new TLRPC.TL_inputMediaUploadedPhoto();
                inputMediaUploadedPhoto.file = inputFile;
                req.media = inputMediaUploadedPhoto;
            } else {
                TLRPC.TL_inputMediaUploadedDocument inputMediaDocument = new TLRPC.TL_inputMediaUploadedDocument();
                inputMediaDocument.file = inputFile;
                inputMediaDocument.mime_type = mimeType;
                req.media = inputMediaDocument;
            }

            getConnectionsManager().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        uploadSet.remove(path);
                        getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
                        if (uploadSet.isEmpty()) {
                            startImport();
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        private void startImport() {
            TLRPC.TL_messages_startHistoryImport req = new TLRPC.TL_messages_startHistoryImport();
            req.peer = peer;
            req.import_id = importId;
            getConnectionsManager().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        importingHistoryMap.remove(dialogId);
                        if (error == null) {
                            getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
                        } else {
                            getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, req, error);
                        }
                    });
                }
            });
        }

        public void setImportProgress(int value) {
            if (value == 100) {
                importingHistoryMap.remove(dialogId);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
        }
    }

    public static class ImportingSticker {
        public String path;
        public String emoji;
        public boolean validated;
        public String mimeType;
        public boolean animated;
        public TLRPC.TL_inputStickerSetItem item;

        public VideoEditedInfo videoEditedInfo;
        public void uploadMedia(int account, TLRPC.InputFile inputFile, Runnable onFinish) {
            TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
            req.peer = new TLRPC.TL_inputPeerSelf();
            req.media = new TLRPC.TL_inputMediaUploadedDocument();
            req.media.file = inputFile;
            req.media.mime_type = mimeType;

            ConnectionsManager.getInstance(account).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_messageMediaDocument) {
                            TLRPC.TL_messageMediaDocument mediaDocument = (TLRPC.TL_messageMediaDocument) response;
                            item = new TLRPC.TL_inputStickerSetItem();
                            item.document = new TLRPC.TL_inputDocument();
                            item.document.id = mediaDocument.document.id;
                            item.document.access_hash = mediaDocument.document.access_hash;
                            item.document.file_reference = mediaDocument.document.file_reference;
                            item.emoji = emoji != null ? emoji : "";
                            mimeType = mediaDocument.document.mime_type;
                        } else if (animated) {
                            mimeType = "application/x-bad-tgsticker";
                        }
                        onFinish.run();
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    public class ImportingStickers {

        public HashMap<String, ImportingSticker> uploadSet = new HashMap<>();
        public HashMap<String, Float> uploadProgresses = new HashMap<>();
        public HashMap<String, Long> uploadSize = new HashMap<>();
        public ArrayList<ImportingSticker> uploadMedia = new ArrayList<>();

        public String shortName;
        public String title;
        public String software;

        public long totalSize;
        public long uploadedSize;

        public int uploadProgress;
        public double estimatedUploadSpeed;
        private long lastUploadTime;
        private long lastUploadSize;
        public int timeUntilFinish = Integer.MAX_VALUE;

        private void initImport() {
            getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName);
            lastUploadTime = SystemClock.elapsedRealtime();
            for (int a = 0, N = uploadMedia.size(); a < N; a++) {
                getFileLoader().uploadFile(uploadMedia.get(a).path, false, true, ConnectionsManager.FileTypeFile);
            }
        }

        public long getUploadedCount() {
            return uploadedSize;
        }

        public long getTotalCount() {
            return totalSize;
        }

        private void onFileFailedToUpload(String path) {
            ImportingSticker file = uploadSet.remove(path);
            if (file != null) {
                uploadMedia.remove(file);
            }
        }

        private void addUploadProgress(String path, long sz, float progress) {
            uploadProgresses.put(path, progress);
            uploadSize.put(path, sz);
            uploadedSize = 0;
            for (HashMap.Entry<String, Long> entry : uploadSize.entrySet()) {
                uploadedSize += entry.getValue();
            }
            long newTime = SystemClock.elapsedRealtime();
            if (uploadedSize != lastUploadSize && newTime != lastUploadTime) {
                double dt = (newTime - lastUploadTime) / 1000.0;
                double uploadSpeed = (uploadedSize - lastUploadSize) / dt;
                if (estimatedUploadSpeed == 0) {
                    estimatedUploadSpeed = uploadSpeed;
                } else {
                    double coef = 0.01;
                    estimatedUploadSpeed = coef * uploadSpeed + (1 - coef) * estimatedUploadSpeed;
                }
                timeUntilFinish = (int) ((totalSize - uploadedSize) * 1000 / (double) estimatedUploadSpeed);
                lastUploadSize = uploadedSize;
                lastUploadTime = newTime;
            }
            float pr = getUploadedCount() / (float) getTotalCount();
            int newProgress = (int) (pr * 100);
            if (uploadProgress != newProgress) {
                uploadProgress = newProgress;
                getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName);
            }
        }

        private void onMediaImport(String path, long size, TLRPC.InputFile inputFile) {
            addUploadProgress(path, size, 1.0f);

            ImportingSticker file = uploadSet.get(path);
            if (file == null) {
                return;
            }
            file.uploadMedia(currentAccount, inputFile, () -> {
                uploadSet.remove(path);
                getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName);
                if (uploadSet.isEmpty()) {
                    startImport();
                }
            });
        }

        private void startImport() {
            TLRPC.TL_stickers_createStickerSet req = new TLRPC.TL_stickers_createStickerSet();
            req.user_id = new TLRPC.TL_inputUserSelf();
            req.title = title;
            req.short_name = shortName;
            if (software != null) {
                req.software = software;
                req.flags |= 8;
            }
            for (int a = 0, N = uploadMedia.size(); a < N; a++) {
                ImportingSticker file = uploadMedia.get(a);
                if (file.item == null) {
                    continue;
                }
                req.stickers.add(file.item);
            }
            getConnectionsManager().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        importingStickersMap.remove(shortName);
                        if (error == null) {
                            getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName);
                        } else {
                            getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName, req, error);
                        }
                        if (response instanceof TLRPC.TL_messages_stickerSet) {
                            if (getNotificationCenter().hasObservers(NotificationCenter.stickersImportComplete)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.stickersImportComplete, response);
                            } else {
                                getMediaDataController().toggleStickerSet(null, response, 2, null, false, false);
                            }
                        }
                    });
                }
            });
        }

        public void setImportProgress(int value) {
            if (value == 100) {
                importingStickersMap.remove(shortName);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName);
        }
    }

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
        public volatile String parentObject;
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
            getNotificationCenter().postNotificationName(NotificationCenter.wasUnableToFindCurrentLocation, waitingForLocationCopy);
            waitingForLocation.clear();
        }
    });

    public static class SendingMediaInfo {
        public Uri uri;
        public String path;
        public String caption;
        public String thumbPath;
        public String coverPath;
        public TLRPC.Photo coverPhoto;
        public String paintPath;
        public int ttl;
        public ArrayList<TLRPC.MessageEntity> entities;
        public ArrayList<TLRPC.InputDocument> masks;
        public VideoEditedInfo videoEditedInfo;
        public MediaController.SearchImage searchImage;
        public TLRPC.BotInlineResult inlineResult;
        public HashMap<String, String> params;
        public boolean isVideo;
        public boolean canDeleteAfter;
        public boolean forceImage;
        public boolean updateStickersOrder;
        public boolean hasMediaSpoilers;
        public TLRPC.VideoSize emojiMarkup;
        public long stars;
        public boolean highQuality;
        public MediaController.PhotoEntry originalPhotoEntry;
    }

    @SuppressLint("MissingPermission")
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
            locationQueryCancelRunnable = () -> {
                if (delegate != null) {
                    if (lastKnownLocation != null) {
                        delegate.onLocationAcquired(lastKnownLocation);
                    } else {
                        delegate.onUnableLocationAcquire();
                    }
                }
                cleanup();
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
        public ArrayList<Object> parentObjects;
        public DelayedMessage delayedMessage;
        public Object parentObject;
        public boolean scheduled;
    }

    protected class DelayedMessage {

        public long peer;
        ArrayList<DelayedMessageSendAfterRequest> requests;

        public TLObject sendRequest;
        public TLObject sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.PhotoSize photoSize;
        public TLObject locationParent;
        public TLRPC.PhotoSize coverPhotoSize;
        public TLRPC.InputFile coverFile;
        public String httpLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
        public VideoEditedInfo videoEditedInfo;
        public boolean performMediaUpload;
        public boolean performCoverUpload;
        public boolean forceReupload;

        private boolean retriedToSend;
        public boolean[] retriedToSendArray;

        public boolean getRetriedToSend(int index) {
            if (index < 0 || retriedToSendArray == null || index >= retriedToSendArray.length) {
                return retriedToSend;
            } else {
                return retriedToSendArray[index];
            }
        }

        public void setRetriedToSend(int index, boolean value) {
            if (index < 0) {
                retriedToSend = value;
            } else {
                if (retriedToSendArray == null) retriedToSendArray = new boolean[messageObjects.size()];
                retriedToSendArray[index] = value;
            }
        }
        
        public int topMessageId;

        public TLRPC.InputMedia inputUploadMedia;

        public ArrayList<TLRPC.PhotoSize> locations;
        public ArrayList<String> httpLocations;
        public ArrayList<VideoEditedInfo> videoEditedInfos;
        public ArrayList<MessageObject> messageObjects;
        public ArrayList<Object> parentObjects;
        public ArrayList<TLRPC.Message> messages;
        public ArrayList<TLRPC.InputMedia> inputMedias;
        public ArrayList<String> originalPaths;
        public HashMap<Object, Object> extraHashMap;
        public long groupId;
        public int finalGroupMessage;
        public boolean scheduled;
        public boolean paidMedia;

        public Object parentObject;

        public DelayedMessage(long peer) {
            this.peer = peer;
        }

        public void initForGroup(long id) {
            type = 4;
            groupId = id;
            messageObjects = new ArrayList<>();
            messages = new ArrayList<>();
            inputMedias = new ArrayList<>();
            originalPaths = new ArrayList<>();
            parentObjects = new ArrayList<>();
            extraHashMap = new HashMap<>();
            locations = new ArrayList<>();
            httpLocations = new ArrayList<>();
            videoEditedInfos = new ArrayList<>();
        }

        public void addDelayedRequest(final TLObject req, final MessageObject msgObj, final String originalPath, Object parentObject, DelayedMessage delayedMessage, boolean scheduled) {
            DelayedMessageSendAfterRequest request = new DelayedMessageSendAfterRequest();
            request.request = req;
            request.msgObj = msgObj;
            request.originalPath = originalPath;
            request.delayedMessage = delayedMessage;
            request.parentObject = parentObject;
            request.scheduled = scheduled;
            if (requests == null) {
                requests = new ArrayList<>();
            }
            requests.add(request);
        }

        public void addDelayedRequest(final TLObject req, final ArrayList<MessageObject> msgObjs, final ArrayList<String> originalPaths, ArrayList<Object> parentObjects, DelayedMessage delayedMessage, boolean scheduled) {
            DelayedMessageSendAfterRequest request = new DelayedMessageSendAfterRequest();
            request.request = req;
            request.msgObjs = msgObjs;
            request.originalPaths = originalPaths;
            request.delayedMessage = delayedMessage;
            request.parentObjects = parentObjects;
            request.scheduled = scheduled;
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
                    getSecretChatHelper().performSendEncryptedRequest((TLRPC.TL_messages_sendEncryptedMultiMedia) request.request, this);
                } else if (request.request instanceof TLRPC.TL_messages_sendMultiMedia) {
                    performSendMessageRequestMulti((TLRPC.TL_messages_sendMultiMedia) request.request, request.msgObjs, request.originalPaths, request.parentObjects, request.delayedMessage, request.scheduled);
                } else if (request.request instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) request.request).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                    performSendMessageRequestMulti((TLRPC.TL_messages_sendMedia) request.request, request.msgObjs, request.originalPaths, request.parentObjects, request.delayedMessage, request.scheduled);
                } else {
                    performSendMessageRequest(request.request, request.msgObj, request.originalPath, request.delayedMessage, request.parentObject, null, request.scheduled);
                }
            }
            requests = null;
        }

        public void markAsError() {
            if (type == 4) {
                for (int a = 0; a < messageObjects.size(); a++) {
                    MessageObject obj = messageObjects.get(a);
                    getMessagesStorage().markMessageAsSendError(obj.messageOwner, obj.scheduled ? 1 : 0);
                    obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    obj.messageOwner.errorAllowedPriceStars = 0;
                    obj.messageOwner.errorNewPriceStars = 0;
                    getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, obj.getId());
                    processSentMessage(obj.getId());
                    removeFromUploadingMessages(obj.getId(), scheduled);
                }
                delayedMessages.remove( "group_" + groupId);
            } else {
                getMessagesStorage().markMessageAsSendError(obj.messageOwner, obj.scheduled ? 1 : 0);
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                obj.messageOwner.errorAllowedPriceStars = 0;
                obj.messageOwner.errorNewPriceStars = 0;
                getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, obj.getId());
                processSentMessage(obj.getId());
                removeFromUploadingMessages(obj.getId(), scheduled);
            }
            sendDelayedRequests();
        }
    }

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
        super(instance);

        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileUploaded);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileUploadProgressChanged);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileUploadFailed);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.filePreparingStarted);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileNewChunkAvailable);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.filePreparingFailed);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.httpFileDidFailedLoad);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.httpFileDidLoad);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileLoaded);
            getNotificationCenter().addObserver(SendMessagesHelper.this, NotificationCenter.fileLoadFailed);
        });
    }

    public void cleanup() {
        delayedMessages.clear();
        unsentMessages.clear();
        sendingMessages.clear();
        editingMessages.clear();
        sendingMessagesIdDialogs.clear();
        uploadMessages.clear();
        uploadingMessagesIdDialogs.clear();
        waitingForLocation.clear();
        waitingForCallback.clear();
        waitingForVote.clear();
        importingHistoryFiles.clear();
        importingHistoryMap.clear();
        importingStickersFiles.clear();
        importingStickersMap.clear();
        locationProvider.stop();
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.fileUploadProgressChanged) {
            String fileName = (String) args[0];
            ImportingHistory importingHistory = importingHistoryFiles.get(fileName);
            if (importingHistory != null) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                importingHistory.addUploadProgress(fileName, loadedSize, loadedSize / (float) totalSize);
            }

            ImportingStickers importingStickers = importingStickersFiles.get(fileName);
            if (importingStickers != null) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                importingStickers.addUploadProgress(fileName, loadedSize, loadedSize / (float) totalSize);
            }
        } else if (id == NotificationCenter.fileUploaded) {
            final String location = (String) args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile) args[1];
            final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile) args[2];

            ImportingHistory importingHistory = importingHistoryFiles.get(location);
            if (importingHistory != null) {
                if (location.equals(importingHistory.historyPath)) {
                    importingHistory.initImport(file);
                } else {
                    importingHistory.onMediaImport(location, (Long) args[5], file);
                }
            }

            ImportingStickers importingStickers = importingStickersFiles.get(location);
            if (importingStickers != null) {
                importingStickers.onMediaImport(location, (Long) args[5], file);
            }

            ArrayList<DelayedMessage> arr = delayedMessages.get(location);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    DelayedMessage message = arr.get(a);
                    TLRPC.InputMedia media = null;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                        if (media instanceof TLRPC.TL_inputMediaPaidMedia) {
                            if (message.extraHashMap == null) {
                                media = ((TLRPC.TL_inputMediaPaidMedia) media).extended_media.get(0);
                            } else {
                                media = (TLRPC.InputMedia) message.extraHashMap.get(location);
                            }
                        }
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_editMessage) {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                        media = (TLRPC.InputMedia) message.extraHashMap.get(location);
                    }

                    if (file != null && media != null) {
                        if (message.type == 0) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, message, true, null, message.parentObject, null, message.scheduled);
                        } else if (message.type == 1) {
                            if (media.file == null && (message.coverPhotoSize == null || message.performMediaUpload)) {
                                media.file = file;
                                if (message.coverFile == null && message.coverPhotoSize != null) {
                                    performSendDelayedMessage(message);
                                } else if (media.thumb == null && message.photoSize != null && message.photoSize.location != null && (message.obj == null || message.obj.videoEditedInfo == null || !message.obj.videoEditedInfo.isSticker)) {
                                    performSendDelayedMessage(message);
                                } else {
                                    performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                                }
                            } else if (message.coverFile == null && message.coverPhotoSize != null) {
                                message.coverFile = file;
                                message.performCoverUpload = true;
                                performSendDelayedMessage(message);
                            } else {
                                media.thumb = file;
                                media.flags |= 4;
                                performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                            }
                        } else if (message.type == 2) {
                            if (media.file == null) {
                                media.file = file;
                                if (media.thumb == null && message.photoSize != null && message.photoSize.location != null) {
                                    performSendDelayedMessage(message);
                                } else {
                                    performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                                }
                            } else {
                                media.thumb = file;
                                media.flags |= 4;
                                performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                            }
                        } else if (message.type == 3) {
                            media.file = file;
                            performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                        } else if (message.type == 4) {
                            if (media instanceof TLRPC.TL_inputMediaUploadedDocument) {
                                if (media.file == null) {
                                    media.file = file;
                                    MessageObject messageObject = (MessageObject) message.extraHashMap.get(location + "_i");
                                    int index = message.messageObjects.indexOf(messageObject);
                                    if (message.extraHashMap.containsKey(location + "_t")) {
                                        message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(location + "_t");
                                    }
                                    if (message.extraHashMap.containsKey(location + "_ct")) {
                                        message.coverPhotoSize = (TLRPC.PhotoSize) message.extraHashMap.get(location + "_ct");
                                    }
                                    if (media.video_cover == null && message.coverPhotoSize != null && message.coverPhotoSize.location != null) {
                                        message.performCoverUpload = true;
                                        performSendDelayedMessage(message, index);
                                    } else if (media.thumb == null && message.photoSize != null && message.photoSize.location != null) {
                                        message.performMediaUpload = true;
                                        performSendDelayedMessage(message, index);
                                    } else {
                                        uploadMultiMedia(message, media, null, location);
                                    }
                                } else {
                                    String documentLocation = (String) message.extraHashMap.get(location + "_doc");
                                    MessageObject messageObject = (MessageObject) message.extraHashMap.get(documentLocation + "_i");
                                    if (message.extraHashMap.containsKey(documentLocation + "_t")) {
                                        message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(documentLocation + "_t");
                                    }
                                    if (message.extraHashMap.containsKey(documentLocation + "_ct")) {
                                        message.coverPhotoSize = (TLRPC.PhotoSize) message.extraHashMap.get(documentLocation + "_ct");
                                    }
                                    int index = message.messageObjects.indexOf(messageObject);
                                    if (message.coverFile == null && message.coverPhotoSize != null) {
                                        message.coverFile = file;
                                        message.performCoverUpload = true;
                                        performSendDelayedMessage(message, index);
                                    } else {
                                        media.thumb = file;
                                        media.flags |= 4;
                                        uploadMultiMedia(message, media, null, (String) message.extraHashMap.get(location + "_o"));
                                    }
                                }
                            } else if (media instanceof TLRPC.TL_inputMediaDocument) {
                                final boolean isCover = message.extraHashMap != null && message.extraHashMap.containsKey(location + "_doc");
                                String documentLocation = isCover ? (String) message.extraHashMap.get(location + "_doc") : location;
                                MessageObject messageObject = (MessageObject) message.extraHashMap.get(documentLocation + "_i");
                                if (message.extraHashMap != null && message.extraHashMap.containsKey(documentLocation + "_t")) {
                                    message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(documentLocation + "_t");
                                }
                                if (message.extraHashMap != null && message.extraHashMap.containsKey(documentLocation + "_ct")) {
                                    message.coverPhotoSize = (TLRPC.PhotoSize) message.extraHashMap.get(documentLocation + "_ct");
                                }
                                int index = message.messageObjects.indexOf(messageObject);
                                if (isCover && message.coverFile == null && message.coverPhotoSize != null) {
                                    message.coverFile = file;
                                    message.performCoverUpload = true;
                                    performSendDelayedMessage(message, index);
                                } else if (message.photoSize != null && media.thumb == null) {
                                    media.thumb = file;
                                    media.flags |= 4;
                                    uploadMultiMedia(message, media, null, (String) message.extraHashMap.get(location + "_o"));
                                } else {
                                    media.file = file;
                                    uploadMultiMedia(message, media, null, location);
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
                                    message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(location + "_t");
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
                                decryptedMessage.media.size = size;
                            }
                            decryptedMessage.media.key = (byte[]) args[3];
                            decryptedMessage.media.iv = (byte[]) args[4];
                            if (message.type == 4) {
                                uploadMultiMedia(message, null, encryptedFile, location);
                            } else {
                                getSecretChatHelper().performSendEncryptedRequest(decryptedMessage, message.obj.messageOwner, message.encryptedChat, encryptedFile, message.originalPath, message.obj);
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
        } else if (id == NotificationCenter.fileUploadFailed) {
            final String location = (String) args[0];
            final boolean enc = (Boolean) args[1];

            ImportingHistory importingHistory = importingHistoryFiles.get(location);
            if (importingHistory != null) {
                importingHistory.onFileFailedToUpload(location);
            }

            ImportingStickers importingStickers = importingStickersFiles.get(location);
            if (importingStickers != null) {
                importingStickers.onFileFailedToUpload(location);
            }

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
        } else if (id == NotificationCenter.filePreparingStarted) {
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
                        message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(messageObject.messageOwner.attachPath + "_t");
                        if (message.extraHashMap.containsKey(messageObject.messageOwner.attachPath + "_ct")) {
                            message.coverPhotoSize = (TLRPC.PhotoSize) message.extraHashMap.get(messageObject.messageOwner.attachPath + "_ct");
                        }
                        message.performMediaUpload = true;
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
        } else if (id == NotificationCenter.fileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.getId() == 0) {
                return;
            }
            String finalPath = (String) args[1];
            long availableSize = (Long) args[2];
            long finalSize = (Long) args[3];
            Float progress = (Float) args[4];
            boolean isEncrypted = DialogObject.isEncryptedDialog(messageObject.getDialogId());
            getFileLoader().checkUploadNewDataAvailable(finalPath, isEncrypted, availableSize, finalSize, progress);
            if (finalSize != 0) {
                ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
                if (arr != null) {
                    for (int a = 0; a < arr.size(); a++) {
                        DelayedMessage message = arr.get(a);
                        if (message.type == 4) {
                            for (int b = 0; b < message.messageObjects.size(); b++) {
                                MessageObject obj = message.messageObjects.get(b);
                                if (obj == messageObject) {
                                    message.obj.shouldRemoveVideoEditedInfo = true;
                                    obj.messageOwner.params.remove("ve");
                                    TLRPC.Document document = message.obj.getDocument();
                                    if (document != null) {
                                        document.size = finalSize;
                                    }

                                    ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                    messages.add(obj.messageOwner);
                                    int threadMessageId = 0;
                                    int mode = 0;
                                    if (obj.isQuickReply()) {
                                        mode = ChatActivity.MODE_QUICK_REPLIES;
                                        threadMessageId = obj.getQuickReplyId();
                                    } else if (obj.scheduled) {
                                        mode = ChatActivity.MODE_SCHEDULED;
                                    }
                                    if (message.paidMedia && b != 0) break;
                                    getMessagesStorage().putMessages(messages, false, true, false, 0, mode, threadMessageId);
                                    break;
                                }
                            }
                        } else if (message.obj == messageObject) {
                            message.obj.shouldRemoveVideoEditedInfo = true;
                            message.obj.messageOwner.params.remove("ve");
                            TLRPC.Document document = message.obj.getDocument();
                            if (document != null) {
                                document.size = finalSize;
                            }

                            ArrayList<TLRPC.Message> messages = new ArrayList<>();
                            messages.add(message.obj.messageOwner);
                            int threadMessageId = 0;
                            int mode = 0;
                            if (message.obj.isQuickReply()) {
                                mode = ChatActivity.MODE_QUICK_REPLIES;
                                threadMessageId = message.obj.getQuickReplyId();
                            } else if (message.obj.scheduled) {
                                mode = ChatActivity.MODE_SCHEDULED;
                            }
                            getMessagesStorage().putMessages(messages, false, true, false, 0, mode, threadMessageId);
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.getId() == 0) {
                return;
            }
            String finalPath = (String) args[1];

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
        } else if (id == NotificationCenter.httpFileDidLoad) {
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
                                    getMessagesStorage().putMessages(messages, false, true, false, 0, messageObject.scheduled ? 1 : 0, 0);
                                    getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, messageObject.messageOwner);
                                    message.photoSize = photo.sizes.get(photo.sizes.size() - 1);
                                    message.locationParent = photo;
                                    message.httpLocation = null;
                                    if (message.type == 4) {
                                        message.performMediaUpload = true;
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
                            if (document.thumbs.isEmpty() || document.thumbs.get(0).location instanceof TLRPC.TL_fileLocationUnavailable) {
                                try {
                                    Bitmap bitmap = ImageLoader.loadBitmap(cacheFile.getAbsolutePath(), null, 90, 90, true);
                                    if (bitmap != null) {
                                        document.thumbs.clear();
                                        document.thumbs.add(ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, message.sendEncryptedRequest != null));
                                        bitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    document.thumbs.clear();
                                    FileLog.e(e);
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                message.httpLocation = null;
                                message.obj.messageOwner.attachPath = cacheFile.toString();
                                if (!document.thumbs.isEmpty()) {
                                    final TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                                    if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                        message.photoSize = photoSize;
                                        message.locationParent = document;
                                    }
                                }
                                final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                                messages.add(messageObject.messageOwner);
                                getMessagesStorage().putMessages(messages, false, true, false, 0, messageObject.scheduled ? 1 : 0, 0);
                                message.performMediaUpload = true;
                                performSendDelayedMessage(message);
                                getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, message.obj.messageOwner);
                            });
                        });
                    }
                }
                delayedMessages.remove(path);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String path = (String) args[0];
            ArrayList<DelayedMessage> arr = delayedMessages.get(path);
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    performSendDelayedMessage(arr.get(a));
                }
                delayedMessages.remove(path);
            }
        } else if (id == NotificationCenter.httpFileDidFailedLoad || id == NotificationCenter.fileLoadFailed) {
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
        object.messageOwner.message = object.previousMessage;
        object.messageOwner.entities = object.previousMessageEntities;
        object.messageOwner.attachPath = object.previousAttachPath;
        object.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

        if (object.messageOwner.entities != null) {
            object.messageOwner.flags |= 128;
        } else {
            object.messageOwner.flags &=~ 128;
        }

        object.previousMedia = null;
        object.previousMessage = null;
        object.previousMessageEntities = null;
        object.previousAttachPath = null;
        object.videoEditedInfo = null;
        object.type = -1;
        object.setType();
        object.caption = null;
        if (object.type != MessageObject.TYPE_TEXT) {
            object.generateCaption();
        } else {
            object.resetLayout();
//            object.checkLayout();
        }

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(object.messageOwner);
        getMessagesStorage().putMessages(arr, false, true, false, 0, object.scheduled ? 1 : 0, 0);

        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(object);
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, object.getDialogId(), arrayList);
    }

    public void cancelSendingMessage(MessageObject object) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(object);
        if (object != null && object.type == MessageObject.TYPE_PAID_MEDIA) {
            DelayedMessage msg = null;
            for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
                ArrayList<DelayedMessage> messages = entry.getValue();
                for (int a = 0; a < messages.size(); a++) {
                    DelayedMessage message = messages.get(a);
                    if (message.type == 4) {
                        for (int b = 0; b < message.messageObjects.size(); b++) {
                            MessageObject messageObject = message.messageObjects.get(b);
                            if (messageObject.getId() == object.getId()) {
                                msg = message;
                                break;
                            }
                        }
                    }
                    if (msg != null) {
                        break;
                    }
                }
            }
            if (msg != null) {
                arrayList.clear();
                arrayList.addAll(msg.messageObjects);
            }
        }
        cancelSendingMessage(arrayList);
    }

    public void cancelSendingMessage(ArrayList<MessageObject> objects) {
        ArrayList<String> keysToRemove = new ArrayList<>();
        ArrayList<DelayedMessage> checkReadyToSendGroups = new ArrayList<>();
        ArrayList<Integer> messageIds = new ArrayList<>();
        boolean enc = false;
        boolean scheduled = false;
        long dialogId = 0;
        int topicId = 0;
        for (int c = 0; c < objects.size(); c++) {
            MessageObject object = objects.get(c);
            if (object.scheduled) {
                scheduled = true;
            }
            dialogId = object.getDialogId();
            messageIds.add(object.getId());
            if (object.isQuickReply()) {
                topicId = object.getQuickReplyId();
            }
            TLRPC.Message sendingMessage = removeFromSendingMessages(object.getId(), object.scheduled);
            if (sendingMessage != null) {
                getConnectionsManager().cancelRequest(sendingMessage.reqId, true);
            }
            StarsController.getInstance(currentAccount).hidePaidMessageToast(object);

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
                                removeFromUploadingMessages(object.getId(), object.scheduled);
                                break;
                            }
                        }
                        if (index >= 0) {
                            message.messageObjects.remove(index);
                            message.messages.remove(index);
                            message.originalPaths.remove(index);
                            if (!message.parentObjects.isEmpty()) {
                                message.parentObjects.remove(index);
                            }
                            if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                                TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                                request.multi_media.remove(index);
                            } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) message.sendRequest).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                                TLRPC.TL_messages_sendMedia request = (TLRPC.TL_messages_sendMedia) message.sendRequest;
                                ((TLRPC.TL_inputMediaPaidMedia) request.media).extended_media.remove(index);
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
                                    getMessagesStorage().putMessages(messagesRes, message.peer, -2, 0, false, scheduled ? 1 : 0, 0);
                                }
                                if (!checkReadyToSendGroups.contains(message)) {
                                    checkReadyToSendGroups.add(message);
                                }
                            }
                        }
                        break;
                    } else if (message.obj.getId() == object.getId()) {
                        removeFromUploadingMessages(object.getId(), object.scheduled);
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
                getFileLoader().cancelFileUpload(key, enc);
            }
            delayedMessages.remove(key);
        }
        for (int a = 0, N = checkReadyToSendGroups.size(); a < N; a++) {
            sendReadyToSendGroup(checkReadyToSendGroups.get(a), false, true);
        }
        if (objects.size() == 1 && objects.get(0).isEditing() && objects.get(0).previousMedia != null) {
            revertEditingMessageObject(objects.get(0));
        } else {
            int mode = 0;
            if (!objects.isEmpty() && objects.get(0).isQuickReply()) {
                mode = ChatActivity.MODE_QUICK_REPLIES;
            } else if (scheduled) {
                mode = ChatActivity.MODE_SCHEDULED;
            }
            getMessagesController().deleteMessages(messageIds, null, null, dialogId, topicId, false, mode);
        }
    }

    public boolean retrySendMessage(MessageObject messageObject, boolean unsent, long payStars) {
        if (messageObject.getId() >= 0) {
            if (messageObject.isEditing()) {
                editMessage(messageObject, null, null, null, null, null, null, true, messageObject.hasMediaSpoilers(), messageObject);
            }
            return false;
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
            int enc_id = DialogObject.getEncryptedChatId(messageObject.getDialogId());
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(enc_id);
            if (encryptedChat == null) {
                getMessagesStorage().markMessageAsSendError(messageObject.messageOwner, messageObject.scheduled ? 1 : 0);
                messageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, messageObject.getId());
                processSentMessage(messageObject.getId());
                return false;
            }
            if (messageObject.messageOwner.random_id == 0) {
                messageObject.messageOwner.random_id = getNextRandomId();
            }
            if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                getSecretChatHelper().sendTTLMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                getSecretChatHelper().sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                getSecretChatHelper().sendClearHistoryMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                getSecretChatHelper().sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
                getSecretChatHelper().sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                getSecretChatHelper().sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionTyping) {

            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionResend) {
                getSecretChatHelper().sendResendMessage(encryptedChat, 0, 0, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
                getSecretChatHelper().sendCommitKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
                getSecretChatHelper().sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                getSecretChatHelper().sendRequestKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
                getSecretChatHelper().sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner);
            } else if (messageObject.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionNoop) {
                getSecretChatHelper().sendNoopMessage(encryptedChat, messageObject.messageOwner);
            }
            return true;
        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            TLRPC.User user = getMessagesController().getUser(messageObject.getDialogId());
            sendScreenshotMessage(user, messageObject.getReplyMsgId(), messageObject.messageOwner);
        }
        if (unsent) {
            unsentMessages.put(messageObject.getId(), messageObject);
        }
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(messageObject);
        params.payStars = payStars;
        sendMessage(params);
        return true;
    }

    protected void processSentMessage(int id) {
        int prevSize = unsentMessages.size();
        unsentMessages.remove(id);
        if (prevSize != 0 && unsentMessages.size() == 0) {
            checkUnsentMessages();
        }
    }

    public void processForwardFromMyName(MessageObject messageObject, long did, long payStars, long monoForumPeerId, MessageSuggestionParams suggestionParams) {
        if (messageObject == null) {
            return;
        }
        if (messageObject.messageOwner.media != null && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
            HashMap<String, String> params = null;
            if (DialogObject.isEncryptedDialog(did) && messageObject.messageOwner.peer_id != null && (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo || messageObject.messageOwner.media.document instanceof TLRPC.TL_document)) {
                params = new HashMap<>();
                params.put("parentObject", "sent_" + messageObject.messageOwner.peer_id.channel_id + "_" + messageObject.getId() + "_" + messageObject.getDialogId() + "_" + messageObject.type + "_" + messageObject.getSize());
            }
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, false);
                fparams.payStars = payStars;
                fparams.monoForumPeer = monoForumPeerId;
                fparams.suggestionParams = suggestionParams;
                sendMessage(fparams);
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, null, false);
                fparams.payStars = payStars;
                fparams.monoForumPeer = monoForumPeerId;
                fparams.suggestionParams = suggestionParams;
                sendMessage(fparams);
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of(messageObject.messageOwner.media, did, messageObject.replyMessageObject, null, null, null, true, 0);
                fparams.payStars = payStars;
                fparams.monoForumPeer = monoForumPeerId;
                fparams.suggestionParams = suggestionParams;
                sendMessage(fparams);
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact_old2();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of(user, did, messageObject.replyMessageObject, null, null, null, true, 0);
                fparams.monoForumPeer = monoForumPeerId;
                fparams.suggestionParams = suggestionParams;
                fparams.payStars = payStars;
                sendMessage(fparams);
            } else if (!DialogObject.isEncryptedDialog(did)) {
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                sendMessage(arrayList, did, true, false, true, 0, null, -1, payStars, monoForumPeerId, suggestionParams);
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
                            entity instanceof TLRPC.TL_messageEntityTextUrl ||
                            entity instanceof TLRPC.TL_messageEntitySpoiler ||
                            entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                        entities.add(entity);
                    }
                }
            } else {
                entities = null;
            }
            SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of(messageObject.messageOwner.message, did, messageObject.replyMessageObject, null, webPage, true, entities, null, null, true, 0, null, false);
            fparams.payStars = payStars;
            fparams.monoForumPeer = monoForumPeerId;
            fparams.suggestionParams = suggestionParams;
            sendMessage(fparams);
        } else if (DialogObject.isEncryptedDialog(did)) {
            ArrayList<MessageObject> arrayList = new ArrayList<>();
            arrayList.add(messageObject);
            sendMessage(arrayList, did, true, false, true, 0, null, -1, payStars, monoForumPeerId, suggestionParams);
        }
    }

    public void sendScreenshotMessage(TLRPC.User user, int messageId, TLRPC.Message resendMessage) {
        if (user == null || messageId == 0 || user.id == getUserConfig().getClientUserId()) {
            return;
        }

        TLRPC.TL_messages_sendScreenshotNotification req = new TLRPC.TL_messages_sendScreenshotNotification();
        req.peer = new TLRPC.TL_inputPeerUser();
        req.peer.access_hash = user.access_hash;
        req.peer.user_id = user.id;
        TLRPC.Message message;
        if (resendMessage != null) {
            message = resendMessage;
            req.reply_to = createReplyInput(messageId);
            req.random_id = resendMessage.random_id;
        } else {
            message = new TLRPC.TL_messageService();
            message.random_id = getNextRandomId();
            message.dialog_id = user.id;
            message.unread = true;
            message.out = true;
            message.local_id = message.id = getUserConfig().getNewMessageId();
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = getUserConfig().getClientUserId();
            message.flags |= 256;
            message.flags |= 8;
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags |= 16;
            message.reply_to.reply_to_msg_id = messageId;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = user.id;
            message.date = getConnectionsManager().getCurrentTime();
            message.action = new TLRPC.TL_messageActionScreenshotTaken();
            getUserConfig().saveConfig(false);
        }
        req.random_id = message.random_id;

        MessageObject newMsgObj = new MessageObject(currentAccount, message, false, true);
        newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        newMsgObj.wasJustSent = true;
        ArrayList<MessageObject> objArr = new ArrayList<>();
        objArr.add(newMsgObj);
        getMessagesController().updateInterfaceWithMessages(message.dialog_id, objArr, 0);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(message);
        getMessagesStorage().putMessages(arr, false, true, false, 0, false, 0, 0);

        performSendMessageRequest(req, newMsgObj, null, null, null, null, false);
    }

    public void sendSticker(TLRPC.Document document, String query, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, boolean updateStickersOrder, Object parentObject, String quick_reply_shortcut, int quick_reply_shortcut_id, long stars, long monoForumPeerId, MessageSuggestionParams suggestionParams) {
        if (document == null) {
            return;
        }
        if (DialogObject.isEncryptedDialog(peer)) {
            int encryptedId = DialogObject.getEncryptedChatId(peer);
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedId);
            if (encryptedChat == null) {
                return;
            }
            TLRPC.TL_document_layer82 newDocument = new TLRPC.TL_document_layer82();
            newDocument.id = document.id;
            newDocument.access_hash = document.access_hash;
            newDocument.date = document.date;
            newDocument.mime_type = document.mime_type;
            newDocument.file_reference = document.file_reference;
            if (newDocument.file_reference == null) {
                newDocument.file_reference = new byte[0];
            }
            newDocument.size = document.size;
            newDocument.dc_id = document.dc_id;
            newDocument.attributes = new ArrayList<>();
            for (int i = 0; i < document.attributes.size(); ++i) {
                TLRPC.DocumentAttribute attr = document.attributes.get(i);
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    TLRPC.TL_documentAttributeVideo_layer159 attr2 = new TLRPC.TL_documentAttributeVideo_layer159();
                    attr2.flags = attr.flags;
                    attr2.round_message = attr.round_message;
                    attr2.supports_streaming = attr.supports_streaming;
                    attr2.duration = attr.duration;
                    attr2.w = attr.w;
                    attr2.h = attr.h;
                    newDocument.attributes.add(attr2);
                } else {
                    newDocument.attributes.add(attr);
                }
            }
            if (newDocument.mime_type == null) {
                newDocument.mime_type = "";
            }

            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 10);
            if (thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive || thumb instanceof TLRPC.TL_photoStrippedSize) {
                File file = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, true);
                if (thumb instanceof TLRPC.TL_photoStrippedSize || file.exists()) {
                    try {
                        byte[] arr;
                        TLRPC.PhotoSize newThumb;
                        if (thumb instanceof TLRPC.TL_photoStrippedSize) {
                            newThumb = new TLRPC.TL_photoStrippedSize();
                            arr = thumb.bytes;
                        } else {
                            newThumb = new TLRPC.TL_photoCachedSize();
                            int len = (int) file.length();
                            arr = new byte[(int) file.length()];
                            RandomAccessFile reader = new RandomAccessFile(file, "r");
                            reader.readFully(arr);
                        }

                        TLRPC.TL_fileLocation_layer82 fileLocation = new TLRPC.TL_fileLocation_layer82();
                        fileLocation.dc_id = thumb.location.dc_id;
                        fileLocation.volume_id = thumb.location.volume_id;
                        fileLocation.local_id = thumb.location.local_id;
                        fileLocation.secret = thumb.location.secret;
                        newThumb.location = fileLocation;
                        newThumb.size = thumb.size;
                        newThumb.w = thumb.w;
                        newThumb.h = thumb.h;
                        newThumb.type = thumb.type;
                        newThumb.bytes = arr;
                        newDocument.thumbs.add(newThumb);
                        newDocument.flags |= 1;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            if (newDocument.thumbs.isEmpty()) {
                thumb = new TLRPC.TL_photoSizeEmpty();
                thumb.type = "s";
                newDocument.thumbs.add(thumb);
            }
            document = newDocument;
        }
        TLRPC.Document finalDocument = document;
        if (MessageObject.isGifDocument(document)) {
            mediaSendQueue.postRunnable(() -> {
                final Bitmap[] bitmapFinal = new Bitmap[1];
                final String[] keyFinal = new String[1];

                String docExt;
                String mediaLocationKey = ImageLocation.getForDocument(finalDocument).getKey(null, null, false);

                if ("video/mp4".equals(finalDocument.mime_type)) {
                    docExt = ".mp4";
                } else if ("video/x-matroska".equals(finalDocument.mime_type)) {
                    docExt = ".mkv";
                } else {
                    docExt = "";
                }

                File docFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), mediaLocationKey + docExt);
                if (!docFile.exists()) {
                    docFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO), mediaLocationKey + docExt);
                }

                ensureMediaThumbExists(getAccountInstance(), false, finalDocument, docFile.getAbsolutePath(), null, 0);
                keyFinal[0] = getKeyForPhotoSize(getAccountInstance(), FileLoader.getClosestPhotoSizeWithSize(finalDocument.thumbs, 320), bitmapFinal, true, true);

                AndroidUtilities.runOnUIThread(() -> {
                    if (bitmapFinal[0] != null && keyFinal[0] != null) {
                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal[0]), keyFinal[0], false);
                    }
                    SendMessageParams sendMessageParams = SendMessageParams.of((TLRPC.TL_document) finalDocument, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, null, notify, scheduleDate, 0, parentObject, sendAnimationData, false);
                    sendMessageParams.replyToStoryItem = storyItem;
                    sendMessageParams.replyQuote = quote;
                    sendMessageParams.quick_reply_shortcut = quick_reply_shortcut;
                    sendMessageParams.quick_reply_shortcut_id = quick_reply_shortcut_id;
                    sendMessageParams.payStars = stars;
                    sendMessageParams.monoForumPeer = monoForumPeerId;
                    sendMessageParams.suggestionParams = suggestionParams;
                    sendMessage(sendMessageParams);
                });
            });
        } else {
            HashMap<String, String> params;
            if (!TextUtils.isEmpty(query)) {
                params = new HashMap<>();
                params.put("query", query);
            } else {
                params = null;
            }
            SendMessageParams sendMessageParams = SendMessageParams.of((TLRPC.TL_document) finalDocument, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, params, notify, scheduleDate, 0, parentObject, sendAnimationData, updateStickersOrder);
            sendMessageParams.replyToStoryItem = storyItem;
            sendMessageParams.replyQuote = quote;
            sendMessageParams.quick_reply_shortcut = quick_reply_shortcut;
            sendMessageParams.quick_reply_shortcut_id = quick_reply_shortcut_id;
            sendMessageParams.payStars = stars;
            sendMessageParams.monoForumPeer = monoForumPeerId;
            sendMessageParams.suggestionParams = suggestionParams;
            sendMessage(sendMessageParams);
        }
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, long payStars) {
        return sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, null, -1, payStars);
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg, int video_timestamp, long payStars) {
        return sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, replyToTopMsg, video_timestamp, payStars, 0, null);
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg, int video_timestamp, long payStars, long monoForumPeerId, MessageSuggestionParams suggestionParams) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int sendResult = 0;
        long myId = getUserConfig().getClientUserId();
        boolean isChannel = false;
        if (!DialogObject.isEncryptedDialog(peer)) {
            final TLRPC.Peer peer_id = getMessagesController().getPeer(peer);
            boolean isSignature = false;
            boolean canSendStickers = true;
            boolean canSendPhoto = true;
            boolean canSendVideo = true;
            boolean canSendDocument = true;
            boolean canSendMusic = true;
            boolean canSendPolls = true;
            boolean canSendPreview = true;
            boolean canSendVoiceMessages = true;
            boolean canSendVoiceRound = true;
            String rank = null;
            long linkedToGroup = 0;
            TLRPC.Chat chat;
            long currentPayStars = getMessagesController().getSendPaidMessagesStars(peer);
            if (currentPayStars <= 0) {
                currentPayStars = DialogObject.getMessagesStarsPrice(getMessagesController().isUserContactBlocked(peer));
            }
            if (currentPayStars != payStars) {
                AlertsCreator.ensurePaidMessageConfirmation(currentAccount, peer, Math.max(1, messages.size()), newPayStars -> {
                    sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, replyToTopMsg, video_timestamp, newPayStars, monoForumPeerId, suggestionParams);
                });
                return 0;
            }
            if (DialogObject.isUserDialog(peer)) {
                TLRPC.User sendToUser = getMessagesController().getUser(peer);
                if (sendToUser == null) {
                    return 0;
                }
                chat = null;

                TLRPC.UserFull userFull = getMessagesController().getUserFull(peer);
                if (userFull != null) {
                    canSendVoiceRound = canSendVoiceMessages = !userFull.voice_messages_forbidden;
                }
            } else {
                chat = getMessagesController().getChat(-peer);
                if (ChatObject.isChannel(chat)) {
                    isSignature = chat.signatures;
                    isChannel = !chat.megagroup;

                    if (isChannel && chat.has_link) {
                        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chat.id);
                        if (chatFull != null) {
                            linkedToGroup = chatFull.linked_chat_id;
                        }
                    }
                }
                if (chat != null) {
                    rank = getMessagesController().getAdminRank(chat.id, myId);
                }
                canSendStickers = ChatObject.canSendStickers(chat);
                canSendPhoto = ChatObject.canSendPhoto(chat);
                canSendVideo = ChatObject.canSendVideo(chat);
                canSendDocument = ChatObject.canSendDocument(chat);
                canSendPreview = ChatObject.canSendEmbed(chat);
                canSendPolls = ChatObject.canSendPolls(chat);
                canSendVoiceRound = ChatObject.canSendRoundVideo(chat);
                canSendVoiceMessages = ChatObject.canSendVoice(chat);
                canSendMusic = ChatObject.canSendMusic(chat);
            }

            LongSparseArray<Long> groupsMap = new LongSparseArray<>();
            ArrayList<MessageObject> objArr = new ArrayList<>();
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            ArrayList<Long> randomIds = new ArrayList<>();
            ArrayList<Integer> ids = new ArrayList<>();
            LongSparseArray<TLRPC.Message> messagesByRandomIds = new LongSparseArray<>();
            TLRPC.InputPeer inputPeer = getMessagesController().getInputPeer(peer);
            long lastDialogId = 0;
            final boolean toMyself = peer == myId;
            long lastGroupedId;
            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview()) {
                    if (msgObj.type == MessageObject.TYPE_TEXT && !TextUtils.isEmpty(msgObj.messageText)) {
                        TLRPC.WebPage webPage = msgObj.messageOwner.media != null ? msgObj.messageOwner.media.webpage : null;
                        SendMessageParams params = SendMessageParams.of(msgObj.messageText.toString(), peer, null, replyToTopMsg, webPage, webPage != null, msgObj.messageOwner.entities, null, null, notify, scheduleDate, null, false);
                        params.suggestionParams = suggestionParams;
                        params.monoForumPeer = monoForumPeerId;
                        params.quick_reply_shortcut = msgObj.getQuickReplyName();
                        params.quick_reply_shortcut_id = msgObj.getQuickReplyId();
                        sendMessage(params);
                    }
                    continue;
                }
                boolean mediaIsSticker = (msgObj.isSticker() || msgObj.isAnimatedSticker() || msgObj.isGif() || msgObj.isGame());
                if (!canSendStickers && mediaIsSticker) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_STICKERS) ? 4 : 1;
                    }
                    continue;
                } else if (!canSendPhoto && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && !msgObj.isVideo() && !mediaIsSticker) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_PHOTO) ? 10 : 12;
                    }
                    continue;
                } else if (!canSendMusic && msgObj.isMusic()) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MUSIC) ? 19 : 20;
                    }
                    continue;
                } else if (!canSendVideo && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && msgObj.isVideo() && !mediaIsSticker) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_VIDEO) ? 9 : 11;
                    }
                    continue;
                } else if (!canSendPolls && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS) ? 6 : 3;
                    }
                    continue;
                } else if (!canSendPolls && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS) ? 21 : 22;
                    }
                    continue;
                } else if (!canSendVoiceMessages && MessageObject.isVoiceMessage(msgObj.messageOwner)) {
                    if (chat != null) {
                        if (sendResult == 0) {
                            sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_VOICE) ? 13 : 14;
                        }
                    } else {
                        if (sendResult == 0) {
                            sendResult = 7;
                        }
                    }
                    continue;
                } else if (!canSendVoiceRound && MessageObject.isRoundVideoMessage(msgObj.messageOwner)) {
                    if (chat != null) {
                        if (sendResult == 0) {
                            sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_ROUND) ? 15 : 16;
                        }
                    } else {
                        if (sendResult == 0) {
                            sendResult = 8;
                        }
                    }
                    continue;
                } else if (!canSendDocument && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && !mediaIsSticker) {
                    if (sendResult == 0) {
                        sendResult = ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_DOCUMENTS) ? 17 : 18;
                    }
                    continue;
                }

                final TLRPC.Message newMsg = new TLRPC.TL_message();
                if (!forwardFromMyName) {
                    boolean forwardFromSaved = msgObj.getDialogId() == myId && msgObj.isFromUser() && msgObj.messageOwner.from_id.user_id == myId;
                    if (msgObj.isForwarded()) {
                        newMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
                        if ((msgObj.messageOwner.fwd_from.flags & 1) != 0) {
                            newMsg.fwd_from.flags |= 1;
                            newMsg.fwd_from.from_id = msgObj.messageOwner.fwd_from.from_id;
                        }
                        if ((msgObj.messageOwner.fwd_from.flags & 32) != 0) {
                            newMsg.fwd_from.flags |= 32;
                            newMsg.fwd_from.from_name = msgObj.messageOwner.fwd_from.from_name;
                        }
                        if ((msgObj.messageOwner.fwd_from.flags & 4) != 0) {
                            newMsg.fwd_from.flags |= 4;
                            newMsg.fwd_from.channel_post = msgObj.messageOwner.fwd_from.channel_post;
                        }
                        if ((msgObj.messageOwner.fwd_from.flags & 8) != 0) {
                            newMsg.fwd_from.flags |= 8;
                            newMsg.fwd_from.post_author = msgObj.messageOwner.fwd_from.post_author;
                        }
                        if ((peer == myId || isChannel) && (msgObj.messageOwner.fwd_from.flags & 16) != 0 && !UserObject.isReplyUser(msgObj.getDialogId())) {
                            newMsg.fwd_from.flags |= 16;
                            newMsg.fwd_from.saved_from_peer = msgObj.messageOwner.fwd_from.saved_from_peer;
                            newMsg.fwd_from.saved_from_msg_id = msgObj.messageOwner.fwd_from.saved_from_msg_id;
                        }
                        newMsg.fwd_from.date = msgObj.messageOwner.fwd_from.date;
                        newMsg.flags = TLRPC.MESSAGE_FLAG_FWD;
                    } else if (!forwardFromSaved) { //if (!toMyself || !msgObj.isOutOwner())
                        long fromId = msgObj.getFromChatId();
                        newMsg.fwd_from = new TLRPC.TL_messageFwdHeader();
                        newMsg.fwd_from.channel_post = msgObj.getId();
                        newMsg.fwd_from.flags |= 4;
                        if (msgObj.isFromUser()) {
                            newMsg.fwd_from.from_id = msgObj.messageOwner.from_id;
                            newMsg.fwd_from.flags |= 1;
                        } else {
                            newMsg.fwd_from.from_id = new TLRPC.TL_peerChannel();
                            newMsg.fwd_from.from_id.channel_id = msgObj.messageOwner.peer_id.channel_id;
                            newMsg.fwd_from.flags |= 1;
                            if (msgObj.messageOwner.post && fromId > 0) {
                                newMsg.fwd_from.from_id = msgObj.messageOwner.from_id != null ? msgObj.messageOwner.from_id : msgObj.messageOwner.peer_id;
                            }
                        }
                        if (msgObj.messageOwner.post_author != null) {
                        /*newMsg.fwd_from.post_author = msgObj.messageOwner.post_author;
                        newMsg.fwd_from.flags |= 8;*/
                        } else if (!msgObj.isOutOwner() && fromId > 0 && msgObj.messageOwner.post) {
                            TLRPC.User signUser = getMessagesController().getUser(fromId);
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
                        newMsg.fwd_from.saved_from_peer = msgObj.messageOwner.peer_id;
                        if (newMsg.fwd_from.saved_from_peer.user_id == myId) {
                            newMsg.fwd_from.saved_from_peer.user_id = msgObj.getDialogId();
                        }
                    }
                }
                newMsg.params = new HashMap<>();
                newMsg.params.put("fwd_id", "" + msgObj.getId());
                newMsg.params.put("fwd_peer", "" + msgObj.getDialogId());
                if (!msgObj.messageOwner.restriction_reason.isEmpty()) {
                    newMsg.restriction_reason = msgObj.messageOwner.restriction_reason;
                    newMsg.flags |= 4194304;
                }
                if (!canSendPreview && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    newMsg.media = new TLRPC.TL_messageMediaEmpty();
                } else {
                    newMsg.media = msgObj.messageOwner.media;
                }
                newMsg.invert_media = msgObj.messageOwner.invert_media;
                if (newMsg.media != null) {
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                }
                if (msgObj.messageOwner.via_bot_id != 0) {
                    newMsg.via_bot_id = msgObj.messageOwner.via_bot_id;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                }
                if (linkedToGroup != 0) {
                    newMsg.replies = new TLRPC.TL_messageReplies();
                    newMsg.replies.comments = true;
                    newMsg.replies.channel_id = linkedToGroup;
                    newMsg.replies.flags |= 1;

                    newMsg.flags |= 8388608;
                }
                if (!hideCaption || newMsg.media == null) {
                    newMsg.message = msgObj.messageOwner.message;
                }
                if (newMsg.message == null) {
                    newMsg.message = "";
                }
                newMsg.fwd_msg_id = msgObj.getId();
                newMsg.attachPath = msgObj.messageOwner.attachPath;
                newMsg.entities = msgObj.messageOwner.entities;
                if (msgObj.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                    newMsg.reply_markup = new TLRPC.TL_replyInlineMarkup();
                    boolean dropMarkup = false;
                    for (int b = 0, N = msgObj.messageOwner.reply_markup.rows.size(); b < N; b++) {
                        TLRPC.TL_keyboardButtonRow oldRow = msgObj.messageOwner.reply_markup.rows.get(b);
                        TLRPC.TL_keyboardButtonRow newRow = null;
                        for (int c = 0, N2 = oldRow.buttons.size(); c < N2; c++) {
                            TLRPC.KeyboardButton button = oldRow.buttons.get(c);
                            if (button instanceof TLRPC.TL_keyboardButtonUrlAuth || button instanceof TLRPC.TL_keyboardButtonUrl || button instanceof TLRPC.TL_keyboardButtonSwitchInline || button instanceof TLRPC.TL_keyboardButtonBuy) {
                                if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
                                    TLRPC.TL_keyboardButtonUrlAuth auth = new TLRPC.TL_keyboardButtonUrlAuth();
                                    auth.flags = button.flags;
                                    if (button.fwd_text != null) {
                                        auth.text = auth.fwd_text = button.fwd_text;
                                    } else {
                                        auth.text = button.text;
                                    }
                                    auth.url = button.url;
                                    auth.button_id = button.button_id;
                                    button = auth;
                                }
                                if (newRow == null) {
                                    newRow = new TLRPC.TL_keyboardButtonRow();
                                    newMsg.reply_markup.rows.add(newRow);
                                }
                                newRow.buttons.add(button);
                            } else {
                                dropMarkup = true;
                                break;
                            }
                        }
                        if (dropMarkup) {
                            break;
                        }
                    }
                    if (!dropMarkup) {
                        newMsg.flags |= 64;
                    } else {
                        msgObj.messageOwner.reply_markup = null;
                        newMsg.flags &=~ 64;
                    }
                }
                if (!newMsg.entities.isEmpty()) {
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                }
                if (newMsg.attachPath == null) {
                    newMsg.attachPath = "";
                }
                newMsg.local_id = newMsg.id = getUserConfig().getNewMessageId();
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
                if (peer_id.channel_id != 0 && isChannel) {
                    if (isSignature) {
                        newMsg.from_id = new TLRPC.TL_peerUser();
                        newMsg.from_id.user_id = myId;
                    } else {
                        newMsg.from_id = peer_id;
                    }
                    newMsg.post = true;
                } else {
                    long fromPeerId = ChatObject.getSendAsPeerId(chat, getMessagesController().getChatFull(-peer), true);

                    if (fromPeerId == myId) {
                        newMsg.from_id = new TLRPC.TL_peerUser();
                        newMsg.from_id.user_id = myId;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    } else {
                        newMsg.from_id = getMessagesController().getPeer(fromPeerId);
                        if (rank != null) {
                            newMsg.post_author = rank;
                            newMsg.flags |= 65536;
                        }
                    }
                }
                if (newMsg.random_id == 0) {
                    newMsg.random_id = getNextRandomId();
                }
                randomIds.add(newMsg.random_id);
                messagesByRandomIds.put(newMsg.random_id, newMsg);
                ids.add(newMsg.fwd_msg_id);
                newMsg.date = scheduleDate != 0 ? scheduleDate : getConnectionsManager().getCurrentTime();
                if (inputPeer instanceof TLRPC.TL_inputPeerChannel && isChannel) {
                    if (scheduleDate == 0) {
                        newMsg.views = 1;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                    }
                } else {
                    if ((msgObj.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                        if (scheduleDate == 0) {
                            newMsg.views = msgObj.messageOwner.views;
                            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                        }
                    }
                    newMsg.unread = true;
                }
                newMsg.dialog_id = peer;
                newMsg.peer_id = peer_id;
                if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
                    if (inputPeer instanceof TLRPC.TL_inputPeerChannel && msgObj.getChannelId() != 0) {
                        newMsg.media_unread = msgObj.isContentUnread();
                    } else {
                        newMsg.media_unread = true;
                    }
                }
                if (replyToTopMsg == null && suggestionParams == null && msgObj.messageOwner.reply_to != null) {
                    if (
                        !(msgObj.messageOwner.reply_to.reply_to_peer_id == null || MessageObject.peersEqual(msgObj.messageOwner.reply_to.reply_to_peer_id, msgObj.messageOwner.peer_id)) ||
                        ids != null && (msgObj.messageOwner.reply_to.flags & 16) != 0 && ids.contains(msgObj.messageOwner.reply_to.reply_to_msg_id)
                    ) {
                        newMsg.flags |= 8;
                        newMsg.reply_to = msgObj.messageOwner.reply_to;
                    }
                }
                if (payStars > 0) {
                    newMsg.flags2 |= 64;
                    newMsg.paid_message_stars = payStars;
                }
                if (monoForumPeerId != 0) {
                    newMsg.saved_peer_id = getMessagesController().getPeer(monoForumPeerId);
                    newMsg.flags |= 268435456;
                }

                if (suggestionParams != null) {
                    newMsg.suggested_post = suggestionParams.toTl();
                }

                MessageObject newMsgObj = new MessageObject(currentAccount, newMsg, true, true);
                newMsgObj.scheduled = scheduleDate != 0;
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
                newMsgObj.wasJustSent = true;
                objArr.add(newMsgObj);
                arr.add(newMsg);
                StarsController.getInstance(currentAccount).beforeSendingMessage(newMsgObj);

                if (msgObj.replyMessageObject != null) {
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId() == msgObj.replyMessageObject.getId()) {
                            newMsgObj.messageOwner.replyMessage = msgObj.replyMessageObject.messageOwner;
                            newMsgObj.replyMessageObject = msgObj.replyMessageObject;
                            break;
                        }
                    }
                }

                putToSendingMessages(newMsg, scheduleDate != 0);
                boolean differentDialog = false;

                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("forward message user_id = " + inputPeer.user_id + " chat_id = " + inputPeer.chat_id + " channel_id = " + inputPeer.channel_id + " access_hash = " + inputPeer.access_hash);
                }

                if (replyToTopMsg != null && suggestionParams == null) {
                    newMsg.reply_to = new TLRPC.TL_messageReplyHeader();
                    newMsg.reply_to.flags |= 16;
                    newMsg.reply_to.reply_to_msg_id = replyToTopMsg.getId();
                    if (replyToTopMsg.isTopicMainMessage) {
                        newMsg.reply_to.forum_topic = true;
                        newMsg.reply_to.flags |= 8;
                    }
                }

                if (arr.size() == 100 || a == messages.size() - 1 || a != messages.size() - 1 && messages.get(a + 1).getDialogId() != msgObj.getDialogId()) {
                    getMessagesStorage().putMessages(new ArrayList<>(arr), false, true, false, 0, scheduleDate != 0 ? 1 : 0, 0);
                    getMessagesController().updateInterfaceWithMessages(peer, objArr, scheduleDate != 0 ? 1 : 0);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    getUserConfig().saveConfig(false);

                    final TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
                    req.to_peer = inputPeer;
                    req.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
                    if (replyToTopMsg != null) {
                        req.top_msg_id = replyToTopMsg.getId();
                        req.flags |= 512;
                    }
                    if (scheduleDate != 0) {
                        req.schedule_date = scheduleDate;
                        req.flags |= 1024;
                    }
                    if (msgObj.messageOwner.peer_id instanceof TLRPC.TL_peerChannel) {
                        TLRPC.Chat channel = getMessagesController().getChat(msgObj.messageOwner.peer_id.channel_id);
                        req.from_peer = new TLRPC.TL_inputPeerChannel();
                        req.from_peer.channel_id = msgObj.messageOwner.peer_id.channel_id;
                        if (channel != null) {
                            req.from_peer.access_hash = channel.access_hash;
                        }
                    } else {
                        req.from_peer = new TLRPC.TL_inputPeerEmpty();
                    }
                    req.random_id = randomIds;
                    req.id = ids;
                    req.drop_author = forwardFromMyName;
                    req.drop_media_captions = hideCaption;
                    req.with_my_score = messages.size() == 1 && messages.get(0).messageOwner.with_my_score;
                    if (video_timestamp >= 0) {
                        req.flags |= 1048576;
                        req.video_timestamp = video_timestamp;
                    }
                    if (payStars > 0) {
                        req.flags |= 2097152;
                        req.allow_paid_stars = req.id.size() * payStars;
                    }
                    if (suggestionParams != null) {
                        req.suggested_post = suggestionParams.toTl();
                        if (replyToTopMsg != null) {
                            req.reply_to = new TLRPC.TL_inputReplyToMessage();
                            req.reply_to.reply_to_msg_id = replyToTopMsg.getId();
                        }
                    }
                    applyMonoForumPeerId(req, monoForumPeerId);

                    final ArrayList<TLRPC.Message> newMsgObjArr = arr;
                    final ArrayList<MessageObject> newMsgArr = new ArrayList<>(objArr);
                    final LongSparseArray<TLRPC.Message> messagesByRandomIdsFinal = messagesByRandomIds;
                    boolean scheduledOnline = scheduleDate == 0x7FFFFFFE;
                    final Runnable send = () -> {
                        getConnectionsManager().sendRequest(req, (response, error) -> {
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
                                getNotificationCenter().postNotificationNameOnUIThread(NotificationCenter.savedMessagesForwarded, newMessagesByIds);
                                Integer value = getMessagesController().dialogs_read_outbox_max.get(peer);
                                if (value == null) {
                                    value = getMessagesStorage().getDialogReadMax(true, peer);
                                    getMessagesController().dialogs_read_outbox_max.put(peer, value);
                                }

                                int sentCount = 0;
                                for (int a1 = 0; a1 < updates.updates.size(); a1++) {
                                    TLRPC.Update update = updates.updates.get(a1);
                                    if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateNewChannelMessage || update instanceof TLRPC.TL_updateNewScheduledMessage || update instanceof TLRPC.TL_updateQuickReplyMessage) {
                                        boolean currentSchedule = false;
                                        boolean scheduled = scheduleDate != 0;

                                        updates.updates.remove(a1);
                                        a1--;
                                        final TLRPC.Message message;
                                        if (update instanceof TLRPC.TL_updateNewMessage) {
                                            TLRPC.TL_updateNewMessage updateNewMessage = (TLRPC.TL_updateNewMessage) update;
                                            message = updateNewMessage.message;
                                            getMessagesController().processNewDifferenceParams(-1, updateNewMessage.pts, -1, updateNewMessage.pts_count);
                                            currentSchedule = false;
                                        } else if (update instanceof TLRPC.TL_updateNewScheduledMessage) {
                                            TLRPC.TL_updateNewScheduledMessage updateNewMessage = (TLRPC.TL_updateNewScheduledMessage) update;
                                            message = updateNewMessage.message;
                                            currentSchedule = true;
                                        } else if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
                                            QuickRepliesController.getInstance(currentAccount).processUpdate(update, null, 0);
                                            TLRPC.TL_updateQuickReplyMessage updateQuickReplyMessage = (TLRPC.TL_updateQuickReplyMessage) update;
                                            message = updateQuickReplyMessage.message;
                                        } else {
                                            TLRPC.TL_updateNewChannelMessage updateNewChannelMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                            message = updateNewChannelMessage.message;
                                            getMessagesController().processNewChannelDifferenceParams(updateNewChannelMessage.pts, updateNewChannelMessage.pts_count, message.peer_id.channel_id);
                                            currentSchedule = false;
                                        }
                                        if (scheduledOnline && message.date != 0x7FFFFFFE) {
                                            currentSchedule = false;
                                        }
                                        ImageLoader.saveMessageThumbs(message);
                                        if (!currentSchedule) {
                                            message.unread = value < message.id;
                                        }
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
                                            msgObj1.messageOwner.post_author = message.post_author;
                                            if ((message.flags & 33554432) != 0) {
                                                msgObj1.messageOwner.ttl_period = message.ttl_period;
                                                msgObj1.messageOwner.flags |= 33554432;
                                            }
                                            updateMediaPaths(msgObj1, message, message.id, null, true);
                                            int existFlags = msgObj1.getMediaExistanceFlags();
                                            newMsgObj1.id = message.id;
                                            sentCount++;

                                            if (scheduled != currentSchedule) {
                                                final int fromMode = scheduled ? ChatActivity.MODE_SCHEDULED : 0;
                                                final int toMode = currentSchedule ? ChatActivity.MODE_SCHEDULED : 0;
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                                        getMessagesStorage().putMessages(sentMessages, true, false, false, 0, toMode, 0);
                                                        AndroidUtilities.runOnUIThread(() -> {
                                                            ArrayList<Integer> messageIds = new ArrayList<>();
                                                            messageIds.add(oldId);
                                                            getMessagesController().deleteMessages(messageIds, null, null, newMsgObj1.dialog_id, false, fromMode, false, 0, null, 0, toMode == ChatActivity.MODE_SCHEDULED, message.id);
                                                            ArrayList<MessageObject> messageObjects = new ArrayList<>();
                                                            messageObjects.add(new MessageObject(msgObj.currentAccount, msgObj.messageOwner, true, true));
                                                            getMessagesController().updateInterfaceWithMessages(newMsgObj1.dialog_id, messageObjects, toMode);
                                                            getMediaDataController().increasePeerRaiting(newMsgObj1.dialog_id);
                                                            processSentMessage(oldId);
                                                            removeFromSendingMessages(oldId, scheduleDate != 0);
                                                        });
                                                    });
                                                });
                                            } else {
                                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                                    int mode = scheduleDate != 0 ? ChatActivity.MODE_SCHEDULED : 0;
                                                    if (message.quick_reply_shortcut_id != 0 || message.quick_reply_shortcut != null) {
                                                        mode = ChatActivity.MODE_QUICK_REPLIES;
                                                    }
                                                    getMessagesStorage().updateMessageStateAndId(newMsgObj1.random_id, MessageObject.getPeerId(peer_id), oldId, newMsgObj1.id, 0, false, scheduleDate != 0 ? 1 : 0, message.quick_reply_shortcut_id);
                                                    getMessagesStorage().putMessages(sentMessages, true, false, false, 0, mode, message.quick_reply_shortcut_id);
                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                        getMediaDataController().increasePeerRaiting(peer);
                                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, message.id, message, peer, 0L, existFlags, scheduleDate != 0);
                                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, message.id, message, peer, 0L, existFlags, scheduleDate != 0);
                                                        processSentMessage(oldId);
                                                        removeFromSendingMessages(oldId, scheduleDate != 0);
                                                    });
                                                });
                                            }
                                        }
                                    }
                                }
                                if (!updates.updates.isEmpty()) {
                                    getMessagesController().processUpdates(updates, false);
                                }
                                getStatsController().incrementSentItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, sentCount);
                            } else {
                                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, null, req));
                            }
                            for (int a1 = 0; a1 < newMsgObjArr.size(); a1++) {
                                final TLRPC.Message newMsgObj1 = newMsgObjArr.get(a1);
                                getMessagesStorage().markMessageAsSendError(newMsgObj1, scheduleDate != 0 ? 1 : 0);
                                if (error != null && error.text != null && error.text.startsWith("ALLOW_PAYMENT_REQUIRED_")) {
                                    newMsgObj1.errorAllowedPriceStars = StarsController.getInstance(currentAccount).getAllowedPaidStars(req);
                                    newMsgObj1.errorNewPriceStars = Long.parseLong(error.text.substring("ALLOW_PAYMENT_REQUIRED_".length())) / req.id.size();
                                    getMessagesStorage().updateMessageCustomParams(MessageObject.getDialogId(newMsgObj1), newMsgObj1);
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                    getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj1.id);
                                    processSentMessage(newMsgObj1.id);
                                    removeFromSendingMessages(newMsgObj1.id, scheduleDate != 0);
                                });
                            }
                            if (error != null && error.text != null && error.text.startsWith("ALLOW_PAYMENT_REQUIRED_")) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    StarsController.getInstance(currentAccount).showPriceChangedToast(newMsgArr);
                                });
                            }
                        }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);
                    };
                    if (StarsController.getInstance(currentAccount).beforeSendingFinalRequest(req, newMsgArr, send)) {
                        send.run();
                    }

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
            boolean canSendVoiceMessages = true;
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat((int) peer);
            long userId = encryptedChat.user_id;
            if (DialogObject.isUserDialog(userId)) {
                TLRPC.User sendToUser = getMessagesController().getUser(userId);
                if (sendToUser != null) {
                    TLRPC.UserFull userFull = getMessagesController().getUserFull(userId);
                    if (userFull != null) {
                        canSendVoiceMessages = !userFull.voice_messages_forbidden;
                    }
                }
            }

            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                if (!canSendVoiceMessages && MessageObject.isVoiceMessage(msgObj.messageOwner)) {
                    if (sendResult == 0) {
                        sendResult = 7;
                    }
                } else if (!canSendVoiceMessages && MessageObject.isRoundVideoMessage(msgObj.messageOwner)) {
                    if (sendResult == 0) {
                        sendResult = 8;
                    }
                }
            }
            if (sendResult == 0) {
                for (int a = 0; a < messages.size(); a++) {
                    processForwardFromMyName(messages.get(a), peer, payStars, monoForumPeerId, suggestionParams);
                }
            }
        }
        return sendResult;
    }

    public static int canSendMessageToChat(TLRPC.Chat chat, MessageObject msgObj) {
        boolean canSendStickers = ChatObject.canSendStickers(chat);
        boolean canSendPhoto = ChatObject.canSendPhoto(chat);
        boolean canSendVideo = ChatObject.canSendVideo(chat);
        boolean canSendDocument = ChatObject.canSendDocument(chat);
        boolean canSendPreview = ChatObject.canSendEmbed(chat);
        boolean canSendPolls = ChatObject.canSendPolls(chat);
        boolean canSendVoiceRound = ChatObject.canSendRoundVideo(chat);
        boolean canSendVoiceMessages = ChatObject.canSendVoice(chat);
        boolean canSendMusic = ChatObject.canSendMusic(chat);

        boolean mediaIsSticker = (msgObj.isSticker() || msgObj.isAnimatedSticker() || msgObj.isGif() || msgObj.isGame());
        if (!canSendStickers && mediaIsSticker) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_STICKERS) ? 4 : 1;
        } else if (!canSendPhoto && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && !msgObj.isVideo() && !mediaIsSticker) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_PHOTO) ? 10 : 12;
        } else if (!canSendMusic && msgObj.isMusic()) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MUSIC) ? 19 : 20;
        } else if (!canSendVideo && msgObj.isVideo() && !mediaIsSticker) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_VIDEO) ? 9 : 11;
        } else if (!canSendPolls && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS) ? 6 : 3;
        } else if (!canSendPolls && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS) ? 21 : 22;
        } else if (!canSendVoiceMessages && MessageObject.isVoiceMessage(msgObj.messageOwner)) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_VOICE) ? 13 : 14;
        } else if (!canSendVoiceRound && MessageObject.isRoundVideoMessage(msgObj.messageOwner)) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_ROUND) ? 15 : 16;
        } else if (!canSendDocument && msgObj.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && !mediaIsSticker) {
            return ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_DOCUMENTS) ? 17 : 18;
        }
        return 0;
    }

    private void writePreviousMessageData(TLRPC.Message message, SerializedData data) {
        if (message.media == null) {
            TLRPC.TL_messageMediaEmpty media = new TLRPC.TL_messageMediaEmpty();
            media.serializeToStream(data);
        } else {
            message.media.serializeToStream(data);
        }
        data.writeString(message.message != null ? message.message : "");
        data.writeString(message.attachPath != null ? message.attachPath : "");
        int count;
        data.writeInt32(count = message.entities.size());
        for (int a = 0; a < count; a++) {
            message.entities.get(a).serializeToStream(data);
        }
    }

    public void editMessage(MessageObject messageObject, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, TLRPC.PhotoSize cover, HashMap<String, String> params, boolean retry, boolean hasMediaSpoilers, Object parentObject) {
        if (messageObject == null) {
            return;
        }
        if (params == null) {
            params = new HashMap<>();
        }

        TLRPC.Message newMsg = messageObject.messageOwner;
        messageObject.cancelEditing = false;

        try {
            int type = -1;
            DelayedMessage delayedMessage = null;
            long peer = messageObject.getDialogId();
            boolean supportsSendingNewEntities = true;
            if (DialogObject.isEncryptedDialog(peer)) {
                int encryptedId = DialogObject.getEncryptedChatId(peer);
                TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedId);
                if (encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 101) {
                    supportsSendingNewEntities = false;
                }
            }

            if (retry) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage || messageObject.messageOwner.media == null || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                    type = 1;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    photo = (TLRPC.TL_photo) messageObject.messageOwner.media.photo;
                    type = 2;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                    if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
                        type = 3;
                    } else {
                        type = 7;
                    }
                    videoEditedInfo = messageObject.videoEditedInfo;
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                    type = 10;
                }
                params = newMsg.params;
                if (parentObject == null && params != null && params.containsKey("parentObject")) {
                    parentObject = params.get("parentObject");
                }
                messageObject.editingMessage = newMsg.message;
                messageObject.editingMessageEntities = newMsg.entities;
                path = newMsg.attachPath;
            } else {
                messageObject.previousMedia = newMsg.media;
                messageObject.previousMessage = newMsg.message;
                messageObject.previousMessageEntities = newMsg.entities;
                messageObject.previousAttachPath = newMsg.attachPath;

                TLRPC.MessageMedia media = newMsg.media;
                if (media == null) {
                    media = new TLRPC.TL_messageMediaEmpty();
                }
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
                    newMsg.media.spoiler = hasMediaSpoilers;
                    type = 2;
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        newMsg.attachPath = path;
                    } else {
                        TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
                        newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(location1, true).toString();
                    }
                } else if (document != null) {
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.flags |= 3;
                    newMsg.media.document = document;
                    newMsg.media.spoiler = hasMediaSpoilers;
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
                    if (cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                        ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) cover;
                        newMsg.media.flags |= 512;
                        newMsg.media.video_cover = s.photo;
                    } else if (cover != null) {
                        TLRPC.TL_photo coverPhoto = new TLRPC.TL_photo();
                        coverPhoto.date = getConnectionsManager().getCurrentTime();
                        coverPhoto.sizes.add(cover);
                        coverPhoto.file_reference = new byte[0];
                        newMsg.media.video_cover = coverPhoto;
                        newMsg.media.flags |= 512;
                    }
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaToDo) {
                    type = 10;
                } else {
                    type = 1;
                }

                newMsg.params = params;
                newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_EDITING;
                newMsg.errorNewPriceStars = 0;
                newMsg.errorAllowedPriceStars = 0;
            }
            if (newMsg.attachPath == null) {
                newMsg.attachPath = "";
            }
            newMsg.local_id = 0;
            if ((messageObject.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || messageObject.type == MessageObject.TYPE_VOICE) && !TextUtils.isEmpty(newMsg.attachPath)) {
                messageObject.attachPathExists = true;
            }
            if (messageObject.videoEditedInfo != null && videoEditedInfo == null) {
                videoEditedInfo = messageObject.videoEditedInfo;
            }

            if (!retry) {
                if (messageObject.editingMessage != null) {
                    String oldMessge = newMsg.message;
                    newMsg.message = messageObject.editingMessage.toString();
                    messageObject.caption = null;
                    if (type == 1) {
                        if (messageObject.editingMessageEntities != null) {
                            newMsg.entities = messageObject.editingMessageEntities;
                            newMsg.flags |= 128;
                        } else if (!TextUtils.equals(oldMessge, newMsg.message)) {
                            newMsg.flags &=~ 128;
                        }
                        if (messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
                            messageObject.generateCaption();
                        }
                    } else {
                        if (messageObject.editingMessageEntities != null) {
                            newMsg.entities = messageObject.editingMessageEntities;
                            newMsg.flags |= 128;
                        } else {
                            CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                            ArrayList<TLRPC.MessageEntity> entities = getMediaDataController().getEntities(message, supportsSendingNewEntities);
                            if (entities != null && !entities.isEmpty()) {
                                newMsg.entities = entities;
                                newMsg.flags |= 128;
                            } else if (!TextUtils.equals(oldMessge, newMsg.message)) {
                                newMsg.flags &=~ 128;
                            }
                        }
                        messageObject.generateCaption();
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                getMessagesStorage().putMessages(arr, false, true, false, 0, messageObject.scheduled ? 1 : 0, 0);
                getMessagesController().getTopicsController().processEditedMessage(newMsg);

                messageObject.type = -1;
                messageObject.setType();
                if (type == 1) {
                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        messageObject.generateCaption();
                    } else {
                        messageObject.resetLayout();
                        messageObject.checkLayout();
                    }
                }
                messageObject.createMessageSendInfo();
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, peer, arrayList);
            }

            String originalPath = null;
            if (params != null && params.containsKey("originalPath")) {
                originalPath = params.get("originalPath");
            }

            boolean performMediaUpload = false, performCoverUpload = false;

            if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 10) {
                TLRPC.InputMedia inputMedia = null;
                if (type == 1) {
                    if ((newMsg.media == null || newMsg.media instanceof TLRPC.TL_messageMediaEmpty || newMsg.media != null && newMsg.media.webpage instanceof TLRPC.TL_webPageEmpty) && !messageObject.editingMessageSearchWebPage) {
                        inputMedia = new TLRPC.TL_inputMediaEmpty();
                    } else if (newMsg != null && newMsg.media != null && newMsg.media.webpage != null) {
                        TLRPC.TL_inputMediaWebPage inputWebpage = new TLRPC.TL_inputMediaWebPage();
                        inputWebpage.url = newMsg.media.webpage.url;
                        inputWebpage.force_small_media = newMsg.media.force_small_media;
                        inputWebpage.force_large_media = newMsg.media.force_large_media;
                        inputMedia = inputWebpage;
                    }
                } else if (type == 2) {
                    TLRPC.TL_inputMediaUploadedPhoto uploadedPhoto = new TLRPC.TL_inputMediaUploadedPhoto();
                    uploadedPhoto.spoiler = hasMediaSpoilers;
                    if (params != null) {
                        String masks = params.get("masks");
                        if (masks != null) {
                            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                            int count = serializedData.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                uploadedPhoto.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                            }
                            uploadedPhoto.flags |= 1;
                            serializedData.cleanup();
                        }
                    }

                    if (photo.access_hash == 0) {
                        inputMedia = uploadedPhoto;
                        performMediaUpload = true;
                    } else {
                        TLRPC.TL_inputMediaPhoto media = new TLRPC.TL_inputMediaPhoto();
                        media.id = new TLRPC.TL_inputPhoto();
                        media.id.id = photo.id;
                        media.id.access_hash = photo.access_hash;
                        media.id.file_reference = photo.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }

                    delayedMessage = new DelayedMessage(peer);
                    delayedMessage.type = 0;
                    delayedMessage.obj = messageObject;
                    delayedMessage.originalPath = originalPath;
                    delayedMessage.parentObject = parentObject;
                    delayedMessage.inputUploadMedia = uploadedPhoto;
                    delayedMessage.performMediaUpload = performMediaUpload;
                    if (path != null && path.length() > 0 && path.startsWith("http")) {
                        delayedMessage.httpLocation = path;
                    } else {
                        delayedMessage.photoSize = photo.sizes.get(photo.sizes.size() - 1);
                        delayedMessage.locationParent = photo;
                    }
                } else if (type == 3) {
                    TLRPC.TL_inputMediaUploadedDocument uploadedDocument = new TLRPC.TL_inputMediaUploadedDocument();
                    uploadedDocument.spoiler = hasMediaSpoilers;
                    if (params != null) {
                        String masks = params.get("masks");
                        if (masks != null) {
                            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(masks));
                            int count = serializedData.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                uploadedDocument.stickers.add(TLRPC.InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false));
                            }
                            uploadedDocument.flags |= 1;
                            serializedData.cleanup();
                        }
                    }
                    uploadedDocument.mime_type = document.mime_type;
                    uploadedDocument.attributes = document.attributes;
                    if (!messageObject.isGif() && (videoEditedInfo == null || !videoEditedInfo.muted)) {
                        uploadedDocument.nosound_video = true;
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("nosound_video = true");
                        }
                    }
                    if (document.access_hash == 0) {
                        inputMedia = uploadedDocument;
                        performMediaUpload = true;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        media.id.file_reference = document.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }

                    delayedMessage = new DelayedMessage(peer);
                    delayedMessage.type = 1;
                    delayedMessage.obj = messageObject;
                    delayedMessage.originalPath = originalPath;
                    delayedMessage.parentObject = parentObject;
                    delayedMessage.inputUploadMedia = uploadedDocument;
                    if (!document.thumbs.isEmpty()) {
                        TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                        if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                            delayedMessage.photoSize = photoSize;
                            delayedMessage.locationParent = document;
                        }
                    }
                    delayedMessage.videoEditedInfo = videoEditedInfo;

                    if (cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                        ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) cover;
                        uploadedDocument.video_cover = s.inputPhoto;
                        uploadedDocument.flags |= 64;
                    } else if (cover != null) {
                        TLRPC.PhotoSize photoSize = cover;
                        if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                            delayedMessage.coverPhotoSize = photoSize;
                            performCoverUpload = true;
                        }
                    }
                    delayedMessage.performMediaUpload = performMediaUpload;
                    delayedMessage.performCoverUpload = performCoverUpload;
                } else if (type == 7) {
                    boolean http = false;
                    TLRPC.InputMedia uploadedDocument = new TLRPC.TL_inputMediaUploadedDocument();
                    uploadedDocument.mime_type = document.mime_type;
                    uploadedDocument.attributes = document.attributes;
                    uploadedDocument.spoiler = hasMediaSpoilers;

                    if (document.access_hash == 0) {
                        inputMedia = uploadedDocument;
                        performMediaUpload = uploadedDocument instanceof TLRPC.TL_inputMediaUploadedDocument;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        media.id.file_reference = document.file_reference;
                        if (media.id.file_reference == null) {
                            media.id.file_reference = new byte[0];
                        }
                        media.spoiler = hasMediaSpoilers;
                        inputMedia = media;
                    }
                    if (!http) {
                        delayedMessage = new DelayedMessage(peer);
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.type = 2;
                        delayedMessage.obj = messageObject;
                        if (!document.thumbs.isEmpty() && (videoEditedInfo == null || !videoEditedInfo.isSticker)) {
                            TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                            if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                delayedMessage.photoSize = photoSize;
                                delayedMessage.locationParent = document;
                            }
                        }
                        delayedMessage.parentObject = parentObject;
                        delayedMessage.inputUploadMedia = uploadedDocument;
                        delayedMessage.performMediaUpload = performMediaUpload;
                    }
                } else if (type == 10) {
                    TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
                    if (media instanceof TLRPC.TL_messageMediaToDo) {
                        final TLRPC.TL_messageMediaToDo m = (TLRPC.TL_messageMediaToDo) media;
                        TLRPC.TL_inputMediaTodo inputTodo = new TLRPC.TL_inputMediaTodo();
                        inputTodo.todo = m.todo;
                        inputMedia = inputTodo;
                    }
                }
                if (inputMedia instanceof TLRPC.TL_inputMediaEmpty && (messageObject.type == MessageObject.TYPE_TEXT || messageObject.type == MessageObject.TYPE_EMOJIS)) {
                    inputMedia = null;
                }

                TLObject reqSend;

                TLRPC.TL_messages_editMessage request = new TLRPC.TL_messages_editMessage();
                request.id = messageObject.getId();
                request.peer = getMessagesController().getInputPeer(peer);
                request.invert_media = messageObject.messageOwner.invert_media;
                if (inputMedia != null) {
                    request.flags |= 16384;
                    request.media = inputMedia;
                } else if (!messageObject.editingMessageSearchWebPage) {
                    request.no_webpage = true;
                }
                if (messageObject.scheduled) {
                    request.schedule_date = messageObject.messageOwner.date;
                    request.flags |= 32768;
                }
                if ((messageObject.messageOwner.flags & 1073741824) != 0) {
                    request.quick_reply_shortcut_id = messageObject.messageOwner.quick_reply_shortcut_id;
                    request.flags |= 131072;
                }
                if (messageObject.editingMessage != null) {
                    request.message = messageObject.editingMessage.toString();
                    request.flags |= 2048;
                    request.no_webpage = !messageObject.editingMessageSearchWebPage;
                    if (messageObject.editingMessageEntities != null) {
                        request.entities = messageObject.editingMessageEntities;
                        request.flags |= 8;
                    } else {
                        CharSequence[] message = new CharSequence[]{messageObject.editingMessage};
                        ArrayList<TLRPC.MessageEntity> entities = getMediaDataController().getEntities(message, supportsSendingNewEntities);
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
                    performSendMessageRequest(reqSend, messageObject, null, delayedMessage, parentObject, params, messageObject.scheduled);
                } else if (type == 2) {
                    if (performMediaUpload || performCoverUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, null, true, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 3) {
                    if (performMediaUpload || performCoverUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 6) {
                    performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                } else if (type == 7) {
                    if (performMediaUpload || performCoverUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 8) {
                    if (performMediaUpload || performCoverUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 10) {
                    performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            revertEditingMessageObject(messageObject);
        }
    }

    public int editMessage(MessageObject messageObject, String message, boolean searchLinks, final BaseFragment fragment, ArrayList<TLRPC.MessageEntity> entities, int scheduleDate) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return 0;
        }

        final TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
        req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
        if (message != null) {
            req.message = message;
            req.flags |= 2048;
            req.no_webpage = !searchLinks;
        }
        req.id = messageObject.getId();
        if (messageObject.messageOwner != null && (messageObject.messageOwner.flags & 1073741824) != 0) {
            req.quick_reply_shortcut_id = messageObject.messageOwner.quick_reply_shortcut_id;
            req.flags |= 131072;
        }
        if (entities != null) {
            req.entities = entities;
            req.flags |= 8;
        }
        if (scheduleDate != 0) {
            req.schedule_date = scheduleDate;
            req.flags |= 32768;
        }
        return getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, req));
            }
        });
    }

    private void sendLocation(Location location) {
        TLRPC.TL_messageMediaGeo mediaGeo = new TLRPC.TL_messageMediaGeo();
        mediaGeo.geo = new TLRPC.TL_geoPoint();
        mediaGeo.geo.lat = AndroidUtilities.fixLocationCoord(location.getLatitude());
        mediaGeo.geo._long = AndroidUtilities.fixLocationCoord(location.getLongitude());
        for (HashMap.Entry<String, MessageObject> entry : waitingForLocation.entrySet()) {
            MessageObject messageObject = entry.getValue();
            sendMessage(SendMessagesHelper.SendMessageParams.of(mediaGeo, messageObject.getDialogId(), messageObject, null, null, null, true, 0));
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

    public void sendNotificationCallback(long dialogId, int msgId, byte[] data) {
        AndroidUtilities.runOnUIThread(() -> {
            final String key = dialogId + "_" + msgId + "_" + Utilities.bytesToHex(data) + "_" + 0;
            waitingForCallback.put(key, true);

            List<String> keys = waitingForCallbackMap.get(dialogId + "_" + msgId);
            if (keys == null) {
                waitingForCallbackMap.put(dialogId + "_" + msgId, keys = new ArrayList<>());
            }
            keys.add(key);

            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = getMessagesController().getUser(dialogId);
                if (user == null) {
                    user = getMessagesStorage().getUserSync(dialogId);
                    if (user != null) {
                        getMessagesController().putUser(user, true);
                    }
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                if (chat == null) {
                    chat = getMessagesStorage().getChatSync(-dialogId);
                    if (chat != null) {
                        getMessagesController().putChat(chat, true);
                    }
                }
            }

            TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
            req.peer = getMessagesController().getInputPeer(dialogId);
            req.msg_id = msgId;
            req.game = false;
            if (data != null) {
                req.flags |= 1;
                req.data = data;
            }
            List<String> finalKeys = keys;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                waitingForCallback.remove(key);
                finalKeys.remove(key);
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
            getMessagesController().markDialogAsRead(dialogId, msgId, msgId, 0, false, 0, 0, true, 0);
        });
    }

    public void onMessageEdited(TLRPC.Message message) {
        if (message != null && message.reply_markup != null) {
            List<String> keys = waitingForCallbackMap.remove(message.dialog_id + "_" + message.id);
            if (keys != null) {
                for (String key : keys) {
                    waitingForCallback.remove(key);
                }
            }
        }
    }

    public byte[] isSendingVote(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        final String key = "poll_" + messageObject.getPollId();
        return waitingForVote.get(key);
    }

    public int sendVote(final MessageObject messageObject, final ArrayList<TLRPC.PollAnswer> answers, final Runnable finishRunnable) {
        if (messageObject == null) {
            return 0;
        }
        final String key = "poll_" + messageObject.getPollId();
        if (waitingForCallback.containsKey(key)) {
            return 0;
        }
        TLRPC.TL_messages_sendVote req = new TLRPC.TL_messages_sendVote();
        req.msg_id = messageObject.getId();
        req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
        byte[] options;
        if (answers != null) {
            options = new byte[answers.size()];
            for (int a = 0; a < answers.size(); a++) {
                TLRPC.PollAnswer answer = answers.get(a);
                req.options.add(answer.option);
                options[a] = answer.option[0];
            }
        } else {
            options = new byte[0];
        }
        waitingForVote.put(key, options);
        return getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                voteSendTime.put(messageObject.getPollId(), 0L);
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
                voteSendTime.put(messageObject.getPollId(), SystemClock.elapsedRealtime());
            }
            AndroidUtilities.runOnUIThread(() -> {
                waitingForVote.remove(key);
                if (finishRunnable != null) {
                    finishRunnable.run();
                }
            });
        });
    }

    private final HashMap<Integer, Boolean> waitingForTodoUpdate = new HashMap<>();
    public Boolean getSendingTodoValue(final MessageObject messageObject, final TLRPC.TodoItem task) {
        return waitingForTodoUpdate.get(Objects.hash(messageObject.getDialogId(), messageObject.getId(), task.id));
    }
    public int toggleTodo(final MessageObject messageObject, final TLRPC.TodoItem task, final boolean enabled, final Runnable finishRunnable) {
        if (messageObject == null) {
            return 0;
        }
        int hash = Objects.hash(messageObject.getDialogId(), messageObject.getId(), task.id);
//        final String key = "todo_" + messageObject.getDialogId() + "_" + messageObject.getId() + "_" + task.id;
//        if (waitingForCallback.containsKey(key)) {
//            return 0;
//        }
        waitingForTodoUpdate.put(hash, enabled);
        TLRPC.TL_messages_toggleTodoCompleted req = new TLRPC.TL_messages_toggleTodoCompleted();
        req.msg_id = messageObject.getId();
        req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
        if (enabled) {
            req.completed.add(task.id);
        } else {
            req.incompleted.add(task.id);
        }
        return getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                getMessagesStorage().toggleTodo(messageObject.getDialogId(), messageObject.getId(), task.id, enabled);
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                Boolean value = waitingForTodoUpdate.get(hash);
                if (value != null && value == enabled) {
                    waitingForTodoUpdate.remove(hash);
                }
                if (finishRunnable != null) {
                    finishRunnable.run();
                }
            });
        });
    }

    protected long getVoteSendTime(long pollId) {
        return voteSendTime.get(pollId, 0L);
    }

    public void sendReaction(
        MessageObject messageObject,
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> visibleReactions,
        ReactionsLayoutInBubble.VisibleReaction addedReaction,
        boolean big,
        boolean addToRecent,
        BaseFragment parentFragment,
        Runnable callback
    ) {
        if (messageObject == null || parentFragment == null) {
            return;
        }
        TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
        if (messageObject.messageOwner.isThreadMessage && messageObject.messageOwner.fwd_from != null) {
            req.peer = getMessagesController().getInputPeer(messageObject.getFromChatId());
            req.msg_id = messageObject.messageOwner.fwd_from.saved_from_msg_id;
        } else {
            req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
            req.msg_id = messageObject.getId();
        }
        req.add_to_recent = addToRecent;
        if (addToRecent && addedReaction != null) {
            MediaDataController.getInstance(currentAccount).recentReactions.add(0, ReactionsUtils.toTLReaction(addedReaction));
        }
        if (visibleReactions != null && !visibleReactions.isEmpty()) {
            for (int i = 0; i < visibleReactions.size(); i++ ) {
                ReactionsLayoutInBubble.VisibleReaction visibleReaction = visibleReactions.get(i);
                if (visibleReaction.documentId != 0) {
                    TLRPC.TL_reactionCustomEmoji reactionCustomEmoji = new TLRPC.TL_reactionCustomEmoji();
                    reactionCustomEmoji.document_id = visibleReaction.documentId;
                    req.reaction.add(reactionCustomEmoji);
                    req.flags |= 1;
                } else if (visibleReaction.emojicon != null) {
                    TLRPC.TL_reactionEmoji defaultReaction = new TLRPC.TL_reactionEmoji();
                    defaultReaction.emoticon = visibleReaction.emojicon;
                    req.reaction.add(defaultReaction);
                    req.flags |= 1;
                }
            }
        }
        if (big) {
            req.flags |= 2;
            req.big = true;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
            }
        });
    }

    public void requestUrlAuth(String url, ChatActivity parentFragment, boolean ask) {
        TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
        req.url = url;
        req.flags |= 4;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_urlAuthResultRequest) {
                    TLRPC.TL_urlAuthResultRequest res = (TLRPC.TL_urlAuthResultRequest) response;
                    parentFragment.showRequestUrlAlert(res, (TLRPC.TL_messages_requestUrlAuth) req, url, ask);
                } else if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                    TLRPC.TL_urlAuthResultAccepted res = (TLRPC.TL_urlAuthResultAccepted) response;
                    AlertsCreator.showOpenUrlAlert(parentFragment, res.url, false, false);
                } else if (response instanceof TLRPC.TL_urlAuthResultDefault) {
                    AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask);
                }
            } else {
                AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public void sendCallback(final boolean cache, final MessageObject messageObject, final TLRPC.KeyboardButton button, final ChatActivity parentFragment) {
        sendCallback(cache, messageObject, button, null, null, parentFragment);
    }

    public void sendCallback(final boolean cache, final MessageObject messageObject, final TLRPC.KeyboardButton button, TLRPC.InputCheckPasswordSRP srp, TwoStepVerificationActivity passwordFragment, final ChatActivity parentFragment) {
        if (messageObject == null || button == null || parentFragment == null) {
            return;
        }
        final boolean cacheFinal;
        int type;
        if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
            cacheFinal = false;
            type = 3;
        } else if (button instanceof TLRPC.TL_keyboardButtonGame) {
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

        List<String> keys = waitingForCallbackMap.get(messageObject.getDialogId() + "_" + messageObject.getId());
        if (keys == null) {
            waitingForCallbackMap.put(messageObject.getDialogId() + "_" + messageObject.getId(), keys = new ArrayList<>());
        }
        keys.add(key);

        TLObject[] request = new TLObject[1];
        List<String> finalKeys = keys;
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            waitingForCallback.remove(key);
            finalKeys.remove(key);
            if (cacheFinal && response == null) {
                sendCallback(false, messageObject, button, parentFragment);
            } else if (response != null) {
                if (passwordFragment != null) {
                    passwordFragment.needHideProgress();
                    passwordFragment.finishFragment();
                }

                long uid = messageObject.getFromChatId();
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                String name = null;
                if (uid > 0) {
                    TLRPC.User user = getMessagesController().getUser(uid);
                    if (user != null) {
                        name = ContactsController.formatName(user.first_name, user.last_name);
                    }
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-uid);
                    if (chat != null) {
                        name = chat.title;
                    }
                }
                if (name == null) {
                    name = "bot";
                }

                if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
                    if (response instanceof TLRPC.TL_urlAuthResultRequest) {
                        TLRPC.TL_urlAuthResultRequest res = (TLRPC.TL_urlAuthResultRequest) response;
                        parentFragment.showRequestUrlAlert(res, (TLRPC.TL_messages_requestUrlAuth) request[0], button.url, false);
                    } else if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                        TLRPC.TL_urlAuthResultAccepted res = (TLRPC.TL_urlAuthResultAccepted) response;
                        AlertsCreator.showOpenUrlAlert(parentFragment, res.url, false, false);
                    } else if (response instanceof TLRPC.TL_urlAuthResultDefault) {
                        TLRPC.TL_urlAuthResultDefault res = (TLRPC.TL_urlAuthResultDefault) response;
                        AlertsCreator.showOpenUrlAlert(parentFragment, button.url, false, true);
                    }
                } else if (button instanceof TLRPC.TL_keyboardButtonBuy) {
                    if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                        TLRPC.InputInvoice inputInvoice = ((TLRPC.TL_payments_getPaymentForm) request[0]).invoice;
                        StarsController.getInstance(currentAccount).openPaymentForm(messageObject, inputInvoice, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                            waitingForCallback.remove(key);
                            finalKeys.remove(key);
                        }, status -> {});
                    } else if (response instanceof TLRPC.PaymentForm) {
                        final TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                        getMessagesController().putUsers(form.users, false);
                        parentFragment.presentFragment(new PaymentFormActivity(form, messageObject, parentFragment));
                    } else if (response instanceof TLRPC.TL_payments_paymentReceiptStars) {
                        StarsIntroActivity.showTransactionSheet(LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext, false, currentAccount, (TLRPC.TL_payments_paymentReceiptStars) response, null);
                    } else if (response instanceof TLRPC.PaymentReceipt) {
                        parentFragment.presentFragment(new PaymentFormActivity((TLRPC.PaymentReceipt) response));
                    }
                } else {
                    TLRPC.TL_messages_botCallbackAnswer res = (TLRPC.TL_messages_botCallbackAnswer) response;
                    if (!cacheFinal && res.cache_time != 0 && !button.requires_password) {
                        getMessagesStorage().saveBotCache(key, res);
                    }

                    if (res.message != null) {
                        if (res.alert) {
                            if (parentFragment.getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                            builder.setTitle(name);
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            builder.setMessage(res.message);
                            parentFragment.showDialog(builder.create());
                        } else {
                            parentFragment.showAlert(name, res.message);
                        }
                    } else if (res.url != null) {
                        if (parentFragment.getParentActivity() == null) {
                            return;
                        }
                        TLRPC.User user = getMessagesController().getUser(uid);
                        boolean verified = user != null && user.verified;
                        if (button instanceof TLRPC.TL_keyboardButtonGame) {
                            TLRPC.TL_game game = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame ? messageObject.messageOwner.media.game : null;
                            if (game == null) {
                                return;
                            }
                            parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && MessagesController.getNotificationsSettings(currentAccount).getBoolean("askgame_" + uid, true), uid);
                        } else {
                            AlertsCreator.showOpenUrlAlert(parentFragment, res.url, false, false);
                        }
                    }
                }
            } else if (error != null) {
                if (parentFragment.getParentActivity() == null) {
                    return;
                }
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    if (srp == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                        builder.setTitle(LocaleController.getString(R.string.BotOwnershipTransfer));
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferReadyAlertText", R.string.BotOwnershipTransferReadyAlertText)));
                        builder.setPositiveButton(LocaleController.getString(R.string.BotOwnershipTransferChangeOwner), (dialogInterface, i) -> {
                            TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                            fragment.setDelegate(0, password -> sendCallback(cache, messageObject, button, password, fragment, parentFragment));
                            parentFragment.presentFragment(fragment);
                        });
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                        parentFragment.showDialog(builder.create());
                    }
                } else if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(parentFragment.getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferAlertText", R.string.BotOwnershipTransferAlertText)));
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(parentFragment.getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(parentFragment.getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(parentFragment.getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(parentFragment.getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(parentFragment.getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> parentFragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(parentFragment.getParentActivity());
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString(R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                    }
                    parentFragment.showDialog(builder.create());
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            sendCallback(cache, messageObject, button, passwordFragment.getNewSrpPassword(), passwordFragment, parentFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                }
            }
        });
        if (cacheFinal) {
            getMessagesStorage().getBotCache(key, requestDelegate);
        } else {
            if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
                TLRPC.TL_messages_requestUrlAuth req = new TLRPC.TL_messages_requestUrlAuth();
                req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
                req.msg_id = messageObject.getId();
                req.button_id = button.button_id;
                req.flags |= 2;
                request[0] = req;
                getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else if (button instanceof TLRPC.TL_keyboardButtonBuy) {
                if ((messageObject.messageOwner.media.flags & 4) == 0) {
                    TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                    TLRPC.TL_inputInvoiceMessage inputInvoice = new TLRPC.TL_inputInvoiceMessage();
                    inputInvoice.msg_id = messageObject.getId();
                    inputInvoice.peer = getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
                    req.invoice = inputInvoice;
                    final JSONObject themeParams = BotWebViewSheet.makeThemeParams(null);
                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 1;
                    }
                    request[0] = req;
                    getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                } else {
                    TLRPC.TL_payments_getPaymentReceipt req = new TLRPC.TL_payments_getPaymentReceipt();
                    req.msg_id = messageObject.messageOwner.media.receipt_msg_id;
                    req.peer = getMessagesController().getInputPeer(messageObject.messageOwner.peer_id);
                    request[0] = req;
                    getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            } else {
                TLRPC.TL_messages_getBotCallbackAnswer req = new TLRPC.TL_messages_getBotCallbackAnswer();
                req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
                req.msg_id = messageObject.getId();
                req.game = button instanceof TLRPC.TL_keyboardButtonGame;
                if (button.requires_password) {
                    req.password = req.password = srp != null ? srp : new TLRPC.TL_inputCheckPasswordEmpty();;
                    req.flags |= 4;
                }
                if (button.data != null) {
                    req.flags |= 1;
                    req.data = button.data;
                }
                getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        }
    }

    public boolean isSendingCallback(MessageObject messageObject, TLRPC.KeyboardButton button) {
        if (messageObject == null || button == null) {
            return false;
        }
        int type;
        if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
            type = 3;
        } else if (button instanceof TLRPC.TL_keyboardButtonGame) {
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
            request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.channel_id, false);
        } else if (request.peer instanceof TLRPC.TL_inputPeerChat) {
            request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.chat_id, false);
        } else {
            request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer.user_id, false);
        }
        request.random_id = random_id != 0 ? random_id : getNextRandomId();
        request.message = "";
        request.media = game;
        long fromId = ChatObject.getSendAsPeerId(getMessagesController().getChat(peer.chat_id), getMessagesController().getChatFull(peer.chat_id));
        if (fromId != UserConfig.getInstance(currentAccount).getClientUserId()) {
            request.send_as = getMessagesController().getInputPeer(fromId);
        }
        long payStars = getMessagesController().getSendPaidMessagesStars(DialogObject.getPeerDialogId(peer));
        if (payStars <= 0) {
            payStars = DialogObject.getMessagesStarsPrice(getMessagesController().isUserContactBlocked(DialogObject.getPeerDialogId(peer)));
        }
        if (payStars > 0) {
            request.flags |= 2097152;
            request.allow_paid_stars = payStars;
        }
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
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
        });
    }

    public void sendMessage(SendMessageParams sendMessageParams) {
        String message = sendMessageParams.message;
        String caption = sendMessageParams.caption;
        TLRPC.MessageMedia location = sendMessageParams.location;
        TLRPC.TL_photo photo = sendMessageParams.photo;
        VideoEditedInfo videoEditedInfo = sendMessageParams.videoEditedInfo;
        TLRPC.User user = sendMessageParams.user;
        TLRPC.TL_document document = sendMessageParams.document;
        TLRPC.TL_game game = sendMessageParams.game;
        TLRPC.TL_messageMediaPoll poll = sendMessageParams.poll;
        TLRPC.TL_messageMediaToDo todo = sendMessageParams.todo;
        TLRPC.TL_messageMediaInvoice invoice = sendMessageParams.invoice;
        long peer = sendMessageParams.peer;
        String path = sendMessageParams.path;
        MessageObject replyToMsg = sendMessageParams.replyToMsg;
        MessageObject replyToTopMsg = sendMessageParams.replyToTopMsg;
        TLRPC.WebPage webPage = sendMessageParams.webPage;
        TLRPC.TL_messageMediaWebPage mediaWebPage = sendMessageParams.mediaWebPage;
        boolean searchLinks = sendMessageParams.searchLinks;
        MessageObject retryMessageObject = sendMessageParams.retryMessageObject;
        ArrayList<TLRPC.MessageEntity> entities = sendMessageParams.entities;
        TLRPC.ReplyMarkup replyMarkup = sendMessageParams.replyMarkup;
        HashMap<String, String> params = sendMessageParams.params;
        boolean notify = sendMessageParams.notify;
        int scheduleDate = sendMessageParams.scheduleDate;
        int ttl = sendMessageParams.ttl;
        Object parentObject = sendMessageParams.parentObject;
        MessageObject.SendAnimationData sendAnimationData = sendMessageParams.sendAnimationData;
        boolean updateStickersOrder = sendMessageParams.updateStickersOrder;
        boolean hasMediaSpoilers = sendMessageParams.hasMediaSpoilers;
        TL_stories.StoryItem replyToStoryItem = sendMessageParams.replyToStoryItem;
        TL_stories.StoryItem sendingStory = sendMessageParams.sendingStory;
        ChatActivity.ReplyQuote replyQuote = sendMessageParams.replyQuote;
        boolean invert_media = sendMessageParams.invert_media;
        String quick_reply_shortcut = sendMessageParams.quick_reply_shortcut;
        int quick_reply_shortcut_id = sendMessageParams.quick_reply_shortcut_id;
        long stars = sendMessageParams.stars;

        if (user != null && user.phone == null) {
            return;
        }
        if (peer == 0) {
            return;
        }
        if (message == null && caption == null) {
            caption = "";
        }

        long _payStars = getMessagesController().getSendPaidMessagesStars(peer);
        if (_payStars <= 0) {
            _payStars = DialogObject.getMessagesStarsPrice(getMessagesController().isUserContactBlocked(peer));
        }
        final long payStars = _payStars;
        final boolean isGroup = params != null && params.containsKey("groupId") && !"0".equalsIgnoreCase(params.get("groupId"));
        if (payStars != sendMessageParams.payStars && !isGroup) {
            AlertsCreator.ensurePaidMessageConfirmation(currentAccount, peer, 1, newPayStars -> {
                sendMessageParams.payStars = newPayStars;
                sendMessage(sendMessageParams);
            });
            return;
        }

        if (replyQuote != null && replyQuote.message != null && replyToMsg != null) {
            replyToMsg = replyQuote.message;
        }

        String originalPath = null;
        if (params != null && params.containsKey("originalPath")) {
            originalPath = params.get("originalPath");
        }

        TLRPC.Message newMsg = null;
        MessageObject newMsgObj = null;
        DelayedMessage delayedMessage = null;
        int type = -1;
        boolean isChannel = false;
        boolean forceNoSoundVideo = false;
        TLRPC.Peer fromPeer = null;
        String rank = null;
        long linkedToGroup = 0;
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = !DialogObject.isEncryptedDialog(peer) ? getMessagesController().getInputPeer(peer) : null;
        long myId = getUserConfig().getClientUserId();
        if (DialogObject.isEncryptedDialog(peer)) {
            encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(peer));
            if (encryptedChat == null) {
                if (retryMessageObject != null) {
                    getMessagesStorage().markMessageAsSendError(retryMessageObject.messageOwner, retryMessageObject.scheduled ? 1 : 0);
                    retryMessageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                    retryMessageObject.messageOwner.errorNewPriceStars = 0;
                    retryMessageObject.messageOwner.errorAllowedPriceStars = 0;
                    getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, retryMessageObject.getId());
                    processSentMessage(retryMessageObject.getId());
                }
                return;
            }
        } else if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.Chat chat = getMessagesController().getChat(sendToPeer.channel_id);
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chat.id);
            isChannel = chat != null && !chat.megagroup;
            if (isChannel && chat.has_link && chatFull != null) {
                linkedToGroup = chatFull.linked_chat_id;
            }
            fromPeer = getMessagesController().getPeer(ChatObject.getSendAsPeerId(chat, chatFull, true));
        }

        if (BuildConfig.DEBUG_VERSION) {
            final TLRPC.Chat chat = sendToPeer != null ? getMessagesController().getChat(sendToPeer.channel_id) : null;
            final boolean needMonoForumPeer = ChatObject.isMonoForum(chat) && ChatObject.canManageMonoForum(currentAccount, chat);
            if (needMonoForumPeer != (sendMessageParams.monoForumPeer != 0)) {
                Log.w("DEBUG", "Warning: monoForumPeer: " + sendMessageParams.monoForumPeer);
            }
        }

        try {
            if (retryMessageObject != null) {
                newMsg = retryMessageObject.messageOwner;
                if (parentObject == null && params != null && params.containsKey("parentObject")) {
                    parentObject = params.get("parentObject");
                }
                if (retryMessageObject.isForwarded() || params != null && params.containsKey("fwd_id")) {
                    type = 4;
                } else {
                    if (retryMessageObject.isDice()) {
                        type = MEDIA_TYPE_DICE;
                        message = retryMessageObject.getDiceEmoji();
                        caption = "";
                    } else if (retryMessageObject.type == MessageObject.TYPE_TEXT || retryMessageObject.isAnimatedEmoji()) {
                        if (retryMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            //game = retryMessageObject.messageOwner.media.game;
                        } else {
                            message = newMsg.message;
                        }
                        type = 0;
                    } else if (retryMessageObject.type == MessageObject.TYPE_GEO) {
                        location = newMsg.media;
                        type = 1;
                    } else if (retryMessageObject.type == MessageObject.TYPE_PHOTO) {
                        photo = (TLRPC.TL_photo) newMsg.media.photo;
                        if (retryMessageObject.messageOwner.message != null) {
                            caption = retryMessageObject.messageOwner.message;
                        }
                        type = 2;
                    } else if (
                        retryMessageObject.type == MessageObject.TYPE_VIDEO ||
                        retryMessageObject.type == MessageObject.TYPE_ROUND_VIDEO ||
                        retryMessageObject.videoEditedInfo != null
                    ) {
                        type = 3;
                        document = (TLRPC.TL_document) newMsg.media.document;
                        if (retryMessageObject.messageOwner.message != null) {
                            caption = retryMessageObject.messageOwner.message;
                        }
                    } else if (retryMessageObject.type == MessageObject.TYPE_CONTACT) {
                        user = new TLRPC.TL_userRequest_old2();
                        user.phone = newMsg.media.phone_number;
                        user.first_name = newMsg.media.first_name;
                        user.last_name = newMsg.media.last_name;
                        TLRPC.RestrictionReason reason = new TLRPC.RestrictionReason();
                        reason.platform = "";
                        reason.reason = "";
                        reason.text = newMsg.media.vcard;
                        user.restriction_reason.add(reason);
                        user.id = newMsg.media.user_id;
                        type = 6;
                    } else if (
                        retryMessageObject.type == MessageObject.TYPE_GIF ||
                        retryMessageObject.type == MessageObject.TYPE_FILE ||
                        retryMessageObject.type == MessageObject.TYPE_STICKER ||
                        retryMessageObject.type == MessageObject.TYPE_MUSIC ||
                        retryMessageObject.type == MessageObject.TYPE_ANIMATED_STICKER
                    ) {
                        document = (TLRPC.TL_document) newMsg.media.document;
                        type = 7;
                        if (retryMessageObject.messageOwner.message != null) {
                            caption = retryMessageObject.messageOwner.message;
                        }
                    } else if (retryMessageObject.type == MessageObject.TYPE_VOICE) {
                        document = (TLRPC.TL_document) newMsg.media.document;
                        type = 8;
                        if (retryMessageObject.messageOwner.message != null) {
                            caption = retryMessageObject.messageOwner.message;
                        }
                    } else if (retryMessageObject.type == MessageObject.TYPE_POLL) {
                        if (newMsg.media instanceof TLRPC.TL_messageMediaPoll) {
                            poll = (TLRPC.TL_messageMediaPoll) newMsg.media;
                        } else {
                            todo = (TLRPC.TL_messageMediaToDo) newMsg.media;
                        }
                        type = 10;
                    }
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    }
                    if (newMsg.media.ttl_seconds > 0) {
                        ttl = newMsg.media.ttl_seconds;
                    }
                }
            } else {
                boolean canSendStickers = true;
                if (DialogObject.isChatDialog(peer)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-peer);
                    canSendStickers = ChatObject.canSendStickers(chat);
                }
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
                    if (canSendStickers && message.length() < 30 && webPage == null && (entities == null || entities.isEmpty()) && getMessagesController().diceEmojies.contains(message.replace("\ufe0f", "")) && encryptedChat == null && scheduleDate == 0) {
                        TLRPC.TL_messageMediaDice mediaDice = new TLRPC.TL_messageMediaDice();
                        mediaDice.emoticon = message;
                        mediaDice.value = -1;
                        newMsg.media = mediaDice;
                        type = MEDIA_TYPE_DICE;
                        caption = "";
                    } else {
                        if (mediaWebPage != null) {
                            newMsg.media = mediaWebPage;
                        } else if (webPage == null) {
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
                    }
                } else if (poll != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = poll;
                    type = 10;
                } else if (todo != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    newMsg.media = todo;
                    type = 10;
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
                    newMsg.media.spoiler = hasMediaSpoilers;
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
                        newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(location1, true).toString();
                    }
                    if (caption != null) {
                        newMsg.message = caption;
                    } else if (newMsg.message == null) {
                        newMsg.message = "";
                    }
                } else if (game != null) {
                    newMsg = new TLRPC.TL_message();
                    newMsg.media = new TLRPC.TL_messageMediaGame();
                    newMsg.media.game = game;
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    }
                } else if (invoice != null) {
                    newMsg = new TLRPC.TL_message();
                    newMsg.media = invoice;
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
                    if (!user.restriction_reason.isEmpty() && user.restriction_reason.get(0).text.startsWith("BEGIN:VCARD")) {
                        newMsg.media.vcard = user.restriction_reason.get(0).text;
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
                    if (DialogObject.isChatDialog(peer)) {
                        if (!canSendStickers) {
                            for (int a = 0, N = document.attributes.size(); a < N; a++) {
                                if (document.attributes.get(a) instanceof TLRPC.TL_documentAttributeAnimated) {
                                    document.attributes.remove(a);
                                    forceNoSoundVideo = true;
                                    break;
                                }
                            }
                        }
                    }
                    newMsg.media = new TLRPC.TL_messageMediaDocument();
                    newMsg.media.flags |= 3;
                    newMsg.media.spoiler = hasMediaSpoilers;
                    if (ttl != 0) {
                        newMsg.ttl = newMsg.media.ttl_seconds = ttl;
                        newMsg.media.flags |= 4;
                    }
                    newMsg.media.document = document;
                    if (sendMessageParams.cover instanceof ImageLoader.PhotoSizeFromPhoto) {
                        ImageLoader.PhotoSizeFromPhoto s = (ImageLoader.PhotoSizeFromPhoto) sendMessageParams.cover;
                        newMsg.media.video_cover = s.photo;
                        newMsg.media.flags |= 512;
                    } else if (sendMessageParams.cover != null) {
                        TLRPC.TL_photo coverPhoto = new TLRPC.TL_photo();
                        coverPhoto.date = getConnectionsManager().getCurrentTime();
                        coverPhoto.sizes.add(sendMessageParams.cover);
                        coverPhoto.file_reference = new byte[0];
                        newMsg.media.video_cover = coverPhoto;
                        newMsg.media.flags |= 512;
                    }
                    if (params != null && params.containsKey("query_id")) {
                        type = 9;
                    } else if ((!MessageObject.isVideoSticker(document) || videoEditedInfo != null) && (MessageObject.isVideoDocument(document) || MessageObject.isRoundVideoDocument(document) || videoEditedInfo != null)) {
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
                    if (encryptedChat != null && document.dc_id > 0 && !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true) && !MessageObject.isGifDocument(document)) {
                        newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(document).toString();
                    } else {
                        newMsg.attachPath = path;
                    }
                    if (encryptedChat != null && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
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
                                        name = getMediaDataController().getStickerSetName(attribute.stickerset.id);
                                    }
                                    if (!TextUtils.isEmpty(name)) {
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetShortName();
                                        attributeSticker.stickerset.short_name = name;
                                    } else {
                                        if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetID) {
                                            delayedMessage = new DelayedMessage(peer);
                                            delayedMessage.encryptedChat = encryptedChat;
                                            delayedMessage.locationParent = attributeSticker;
                                            delayedMessage.type = 5;
                                            delayedMessage.parentObject = attribute.stickerset;
                                        }
                                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                    }
                                } else {
                                    attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                }
                                break;
                            }
                        }
                    }
                } else if (sendingStory != null) {
                    if (encryptedChat != null) {
                        newMsg = new TLRPC.TL_message_secret();
                    } else {
                        newMsg = new TLRPC.TL_message();
                    }
                    TLRPC.TL_messageMediaStory mediaStory = new MessageMediaStoryFull();
                    mediaStory.id = sendingStory.id;
                    mediaStory.user_id = sendingStory.dialogId;
                    mediaStory.peer = getMessagesController().getPeer(sendingStory.dialogId);
                    mediaStory.storyItem = sendingStory;
                    newMsg.media = mediaStory;
                    type = MEDIA_TYPE_STORY;
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
                newMsg.local_id = newMsg.id = getUserConfig().getNewMessageId();
                newMsg.out = true;
                TLRPC.Chat chat = sendToPeer != null ? getMessagesController().getChat(sendToPeer.channel_id) : null;
                if (isChannel && sendToPeer != null && (chat == null || !chat.signatures)) {
                    newMsg.from_id = new TLRPC.TL_peerChannel();
                    newMsg.from_id.channel_id = sendToPeer.channel_id;
                } else if (fromPeer != null) {
                    newMsg.from_id = fromPeer;
                    if (rank != null) {
                        newMsg.post_author = rank;
                        newMsg.flags |= 65536;
                    }
                } else {
                    newMsg.from_id = new TLRPC.TL_peerUser();
                    newMsg.from_id.user_id = myId;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                }
                getUserConfig().saveConfig(false);
            }
            newMsg.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false);
            if (newMsg.random_id == 0) {
                newMsg.random_id = getNextRandomId();
            }
            if (quick_reply_shortcut != null || quick_reply_shortcut_id != 0) {
                if (quick_reply_shortcut_id != 0) {
                    TLRPC.TL_inputQuickReplyShortcutId shortcut = new TLRPC.TL_inputQuickReplyShortcutId();
                    shortcut.shortcut_id = quick_reply_shortcut_id;
                    newMsg.quick_reply_shortcut = shortcut;
                } else {
                    TLRPC.TL_inputQuickReplyShortcut shortcut = new TLRPC.TL_inputQuickReplyShortcut();
                    shortcut.shortcut = quick_reply_shortcut;
                    newMsg.quick_reply_shortcut = shortcut;
                }
                newMsg.quick_reply_shortcut_id = quick_reply_shortcut_id;
                if (newMsg.quick_reply_shortcut_id != 0) {
                    newMsg.flags |= 1073741824;
                }
            }
            if (sendMessageParams.effect_id != 0) {
                newMsg.flags2 |= 4;
                newMsg.effect = sendMessageParams.effect_id;
            }
            if (params != null && params.containsKey("bot")) {
                if (encryptedChat != null) {
                    newMsg.via_bot_name = params.get("bot_name");
                    if (newMsg.via_bot_name == null) {
                        newMsg.via_bot_name = "";
                    }
                } else {
                    newMsg.via_bot_id = Utilities.parseLong(params.get("bot"));
                }
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
            }
            newMsg.params = params;
            if (retryMessageObject == null || !retryMessageObject.resendAsIs) {
                if (quick_reply_shortcut != null) {
                    newMsg.date = 0;
                } else if (scheduleDate != 0) {
                    newMsg.date = scheduleDate;
                } else {
                    newMsg.date = getConnectionsManager().getCurrentTime();
                }
                if (sendToPeer instanceof TLRPC.TL_inputPeerChannel) {
                    if (scheduleDate == 0 && isChannel) {
                        newMsg.views = 1;
                        newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
                    }
                    TLRPC.Chat chat = getMessagesController().getChat(sendToPeer.channel_id);
                    if (chat != null) {
                        if (chat.megagroup) {
                            newMsg.unread = true;
                        } else {
                            newMsg.post = true;
//                            if (chat.signatures && !chat.signature_profiles) {
//                                newMsg.from_id = new TLRPC.TL_peerUser();
//                                newMsg.from_id.user_id = myId;
//                            }
                        }
                    }
                } else {
                    newMsg.unread = true;
                }
            }
            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
            newMsg.dialog_id = peer;
            newMsg.invert_media = invert_media;
            if (replyToStoryItem != null) {
                newMsg.reply_to = new TLRPC.TL_messageReplyStoryHeader();
                newMsg.reply_to.story_id = replyToStoryItem.id;
                newMsg.reply_to.peer = getMessagesController().getPeer(replyToStoryItem.dialogId);
                newMsg.replyStory = replyToStoryItem;
                newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
            } else if (replyToMsg != null && (replyToTopMsg == null || replyToMsg != replyToTopMsg || replyToTopMsg.getId() != 1)) {
                newMsg.reply_to = new TLRPC.TL_messageReplyHeader();
                if (encryptedChat != null && replyToMsg.messageOwner.random_id != 0) {
                    newMsg.reply_to.reply_to_random_id = replyToMsg.messageOwner.random_id;
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                } else {
                    newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                }
                newMsg.reply_to.flags |= 16;
                newMsg.reply_to.reply_to_msg_id = replyToMsg.getId();
                if (replyToTopMsg != null && replyToTopMsg != replyToMsg && replyToTopMsg.getId() != 1) {
                    newMsg.reply_to.reply_to_top_id = replyToTopMsg.getId();
                    newMsg.reply_to.flags |= 2;
                    if (replyToTopMsg.isTopicMainMessage) {
                        newMsg.reply_to.forum_topic = true;
                        newMsg.reply_to.flags |= 8;
                    }
                } else {
                    if (replyToMsg.isTopicMainMessage) {
                        newMsg.reply_to.forum_topic = true;
                        newMsg.reply_to.flags |= 8;
                    }
                }
                if (replyQuote != null) {
                    newMsg.reply_to.quote_text = replyQuote.getText();
                    if (!TextUtils.isEmpty(newMsg.reply_to.quote_text)) {
                        newMsg.reply_to.quote = true;
                        newMsg.reply_to.flags |= 64;
                        newMsg.reply_to.flags |= 1024;
                        newMsg.reply_to.quote_offset = replyQuote.start;
                        newMsg.reply_to.quote_entities = replyQuote.getEntities();
                        if (newMsg.reply_to.quote_entities != null && !newMsg.reply_to.quote_entities.isEmpty()) {
                            newMsg.reply_to.quote_entities = new ArrayList<>(newMsg.reply_to.quote_entities);
                            newMsg.reply_to.flags |= 128;
                        }
                    }
                }
            }
            if (linkedToGroup != 0) {
                newMsg.replies = new TLRPC.TL_messageReplies();
                newMsg.replies.comments = true;
                newMsg.replies.channel_id = linkedToGroup;
                newMsg.replies.flags |= 1;

                newMsg.flags |= 8388608;
            }
            if (replyMarkup != null && encryptedChat == null) {
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MARKUP;
                newMsg.reply_markup = replyMarkup;
                String bot = params.get("bot");
                if (bot != null) {
                    newMsg.via_bot_id = Long.parseLong(bot);
                }
            }
            if (!DialogObject.isEncryptedDialog(peer)) {
                newMsg.peer_id = getMessagesController().getPeer(peer);
                if (DialogObject.isUserDialog(peer)) {
                    TLRPC.User sendToUser = getMessagesController().getUser(peer);
                    if (sendToUser == null) {
                        processSentMessage(newMsg.id);
                        return;
                    }
                    if (sendToUser.bot) {
                        newMsg.unread = false;
                    }
                }
            } else {
                newMsg.peer_id = new TLRPC.TL_peerUser();
                if (encryptedChat.participant_id == myId) {
                    newMsg.peer_id.user_id = encryptedChat.admin_id;
                } else {
                    newMsg.peer_id.user_id = encryptedChat.participant_id;
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
                                duration = (int) attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(newMsg.ttl, duration + 1);
                    } else if (MessageObject.isVideoMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                duration = (int) attribute.duration;
                                break;
                            }
                        }
                        newMsg.ttl = Math.max(newMsg.ttl, duration + 1);
                    }
                }
            }
            boolean destroyReply = false;
            if (replyToMsg != null && replyToStoryItem == null && newMsg.reply_to != null && !DialogObject.isEncryptedDialog(replyToMsg.getDialogId())) {
                boolean convertToQuote = false;
                TLRPC.Peer peer2 = getMessagesController().getPeer(replyToMsg.getDialogId() > 0 ? replyToMsg.getSenderId() : replyToMsg.getDialogId());
                boolean anotherChat = peer2 != null && !MessageObject.peersEqual(getMessagesController().getPeer(replyToMsg.getDialogId()), newMsg.peer_id);
                if (anotherChat && replyToMsg.isForwarded() && !replyToMsg.isImportedForward() && replyToMsg.messageOwner.fwd_from.saved_from_peer == null) {
                    if (replyToMsg.messageOwner.fwd_from.from_id != null && (replyToMsg.messageOwner.fwd_from.flags & 4) != 0) {
                        peer2 = replyToMsg.messageOwner.fwd_from.from_id;
                        newMsg.reply_to.reply_to_msg_id = replyToMsg.messageOwner.fwd_from.channel_post;
                        destroyReply = true;
                        anotherChat = true;
                    }
                }
                boolean anotherTopic = false;
                if (replyToMsg != null) {
                    boolean isForum = false;
                    if (!isForum) {
                        TLRPC.Chat chat = getMessagesController().getChat(-DialogObject.getPeerDialogId(newMsg.peer_id));
                        if (ChatObject.isForum(chat)) {
                            isForum = true;
                        }
                    }
                    if (isForum) {
                        anotherTopic = replyToTopMsg.getId() != replyToMsg.getId() && MessageObject.getTopicId(currentAccount, replyToMsg.messageOwner, true) != replyToTopMsg.getId();
                    }
                }
                if (anotherChat || anotherTopic) {
                    newMsg.reply_to.flags |= 1;
                    newMsg.reply_to.reply_to_peer_id = peer2;
                    convertToQuote = true;
                }
                if (convertToQuote) {
                    if (replyQuote == null) {
                        newMsg.reply_to.quote = false;
                        replyQuote = ChatActivity.ReplyQuote.from(replyToMsg);
                    }
                    if (replyQuote != null) {
                        if (replyToMsg.messageOwner != null && replyToMsg.messageOwner.media != null) {
                            newMsg.reply_to.flags |= 256;
                            newMsg.reply_to.reply_media = replyToMsg.messageOwner.media;
                        }
                        if (replyQuote.getText() != null) {
                            newMsg.reply_to.flags |= 64;
                            newMsg.reply_to.quote_text = replyQuote.getText();
                            newMsg.reply_to.flags |= 1024;
                            newMsg.reply_to.quote_offset = replyQuote.start;
                        }
                        if (replyQuote.getEntities() != null) {
                            newMsg.reply_to.flags |= 128;
                            newMsg.reply_to.quote_entities = replyQuote.getEntities();
                        }
                    }
                }
            }
            if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
                newMsg.media_unread = true;
            }

            if (newMsg.from_id == null) {
                newMsg.from_id = newMsg.peer_id;
            }
            newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsg.errorAllowedPriceStars = 0;
            newMsg.errorNewPriceStars = 0;
            if (payStars > 0) {
                newMsg.flags2 |= 64;
                newMsg.paid_message_stars = payStars;
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

            if (stars > 0) {
                TLRPC.MessageMedia media = newMsg.media;
                TLRPC.TL_messageMediaPaidMedia paidMedia = new TLRPC.TL_messageMediaPaidMedia();
                paidMedia.stars_amount = stars;
                TLRPC.TL_messageExtendedMedia extMedia = new TLRPC.TL_messageExtendedMedia();
                extMedia.attachPath = newMsg.attachPath;
                extMedia.media = media;
                paidMedia.extended_media.add(extMedia);
                newMsg.media = paidMedia;
            }
            if (sendMessageParams.monoForumPeer != 0) {
                newMsg.saved_peer_id = getMessagesController().getPeer(sendMessageParams.monoForumPeer);
                newMsg.flags |= 268435456;
            }
            if (sendMessageParams.suggestionParams != null) {
                newMsg.suggested_post = sendMessageParams.suggestionParams.toTl();
            } else if (retryMessageObject != null && retryMessageObject.messageOwner != null && retryMessageObject.messageOwner.suggested_post != null) {
                newMsg.suggested_post = retryMessageObject.messageOwner.suggested_post;
            }

            MessageObject reply = replyToMsg;
            if (replyToTopMsg != null && replyToTopMsg == reply && replyToTopMsg.getId() == 1 || destroyReply) {
                reply = null;
            }
            newMsgObj = new MessageObject(currentAccount, newMsg, reply, true, true);
            newMsgObj.sendAnimationData = sendAnimationData;
            newMsgObj.wasJustSent = true;
            newMsgObj.sentHighQuality = sendMessageParams.sendingHighQuality;
            newMsgObj.scheduled = scheduleDate != 0;
            if (!newMsgObj.isForwarded() && (newMsgObj.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || newMsgObj.type == MessageObject.TYPE_VOICE) && !TextUtils.isEmpty(newMsg.attachPath)) {
                newMsgObj.attachPathExists = true;
            }
            if (newMsgObj.videoEditedInfo != null && videoEditedInfo == null) {
                videoEditedInfo = newMsgObj.videoEditedInfo;
            } else if (videoEditedInfo != null && videoEditedInfo.notReadyYet) {
                newMsgObj.videoEditedInfo.notReadyYet = videoEditedInfo.notReadyYet;
            }

            if (groupId == 0) {
                final ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(newMsgObj);
                final ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                long threadMessageId = 0;
                final int mode;
                if (scheduleDate != 0) {
                    mode = ChatActivity.MODE_SCHEDULED;
                } else if (quick_reply_shortcut != null) {
                    mode = ChatActivity.MODE_QUICK_REPLIES;
                    threadMessageId = newMsg.quick_reply_shortcut_id;
                } else {
                    mode = 0;
                }
                MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0, mode, threadMessageId);
                MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(peer, objArr, mode);
                if (scheduleDate == 0) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            } else {
                String key = "group_" + groupId;
                ArrayList<DelayedMessage> arrayList = delayedMessages.get(key);
                if (arrayList != null) {
                    delayedMessage = arrayList.get(0);
                }
                if (delayedMessage == null) {
                    delayedMessage = new DelayedMessage(peer);
                    delayedMessage.initForGroup(groupId);
                    delayedMessage.encryptedChat = encryptedChat;
                    delayedMessage.scheduled = scheduleDate != 0;
                }
                delayedMessage.performMediaUpload = false;
                delayedMessage.photoSize = null;
                delayedMessage.videoEditedInfo = null;
                delayedMessage.httpLocation = null;
                if (isFinalGroupMedia) {
                    delayedMessage.finalGroupMessage = newMsg.id;
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                if (sendToPeer != null) {
                    FileLog.d("send message user_id = " + sendToPeer.user_id + " chat_id = " + sendToPeer.chat_id + " channel_id = " + sendToPeer.channel_id + " access_hash = " + sendToPeer.access_hash + " notify = " + notify + " silent = " + MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer, false));
                }
            }

            boolean performMediaUpload = false, performCoverUpload = false;

            if (type == 0 || type == 9 && message != null && encryptedChat != null) {
                if (encryptedChat == null) {
                    /* if (mediaWebPage != null) {
                        TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                        reqSend.message = message;
                        reqSend.clear_draft = retryMessageObject == null;
                        reqSend.silent = newMsg.silent;
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
                        if (payStars > 0) {
                            reqSend.flags |= 2097152;
                            reqSend.allow_paid_stars = payStars;
                        }
                        TLRPC.TL_inputMediaWebPage inputWebPage = new TLRPC.TL_inputMediaWebPage();
                        inputWebPage.url = mediaWebPage.webpage.url;
                        inputWebPage.force_large_media = mediaWebPage.force_large_media;
                        inputWebPage.force_small_media = mediaWebPage.force_small_media;
                        inputWebPage.optional = true;
                        reqSend.media = inputWebPage;
                        if (replyToStoryItem != null) {
                            reqSend.reply_to = createReplyInput(replyToStoryItem);
                            reqSend.flags |= 1;
                        } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                            reqSend.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                            reqSend.flags |= 1;
                        }
                        if (updateStickersOrder && SharedConfig.updateStickersOrderOnSend) {
                            reqSend.update_stickersets_order = true;
                        }
                        if (newMsg.from_id != null) {
                            reqSend.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                        }
                        if (entities != null && !entities.isEmpty()) {
                            reqSend.entities = entities;
                            reqSend.flags |= 8;
                        }
                        if (scheduleDate != 0) {
                            reqSend.schedule_date = scheduleDate;
                            reqSend.flags |= 1024;
                        }
                        if (newMsg.quick_reply_shortcut != null) {
                            reqSend.flags |= 131072;
                            reqSend.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                        }
                        if (sendMessageParams.effect_id != 0) {
                            reqSend.flags |= 262144;
                            reqSend.effect = sendMessageParams.effect_id;
                        }
                        reqSend.invert_media = newMsg.invert_media;
                        applyMonoForumPeerId(reqSend, sendMessageParams.monoForumPeer);
                        if (sendMessageParams.suggestionParams != null) {
                            reqSend.suggested_post = sendMessageParams.suggestionParams.toTl();
                        } else if (retryMessageObject != null && retryMessageObject.messageOwner != null && retryMessageObject.messageOwner.suggested_post != null) {
                            reqSend.suggested_post = retryMessageObject.messageOwner.suggested_post;
                        }
                        if (retryMessageObject == null) {
                            StarsController.getInstance(currentAccount).beforeSendingMessage(newMsgObj);
                        }
                        performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
                        if (retryMessageObject == null) {
                            getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                        }
                    } */

                    // --- START Refactored Text Message Sending using TdApiManager ---
                    final TdApiManager tdApiManager = TdApiManager.getInstance(currentAccount);
                    final MessageObject finalNewMsgObj = newMsgObj;
                    final TLRPC.Message finalNewMsgTLRPC = newMsg;
                    final String finalMessageString = message; // This is the actual text content for type 0
                    final ArrayList<TLRPC.MessageEntity> finalEntities = entities;
                    final TLRPC.WebPage finalWebPage = webPage;
                    final boolean finalSearchLinks = searchLinks;
                    final TLRPC.TL_messageMediaWebPage finalMediaWebPage = mediaWebPage;
                    final TLRPC.ReplyMarkup finalReplyMarkup = replyMarkup;
                    final HashMap<String, String> finalParams = params;
                    final int finalScheduleDate = scheduleDate;
                    final boolean finalNotify = notify;
                    final MessageObject finalRetryMessageObject = retryMessageObject;
                    final MessageObject finalReplyToMsg = replyToMsg;
                    final MessageObject finalReplyToTopMsg = replyToTopMsg;
                    final TL_stories.StoryItem finalReplyToStoryItem = replyToStoryItem;
                    final SendMessageParams finalSendMessageParams = sendMessageParams;

                    TdApi.InputMessageReplyTo tdReplyTo = null;
                    if (finalReplyToStoryItem != null) {
                        tdReplyTo = new TdApi.InputMessageReplyToStory(TdApiMessageConverter.getChatId(finalReplyToStoryItem.dialogId), finalReplyToStoryItem.id);
                    } else if (finalReplyToMsg != null) {
                        long replyToMsgId = finalReplyToMsg.getId();
                        long replyToDialogId = TdApiMessageConverter.getChatId(finalReplyToMsg.getDialogId());
                        if (finalNewMsgTLRPC.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                            TLRPC.TL_messageReplyHeader header = (TLRPC.TL_messageReplyHeader) finalNewMsgTLRPC.reply_to;
                            if ((header.flags & 1) != 0 && header.reply_to_peer_id != null) {
                                 replyToDialogId = TdApiMessageConverter.getChatId(DialogObject.getPeerDialogId(header.reply_to_peer_id));
                            }
                        }
                        TdApi.TextQuote quote = null;
                        if (finalSendMessageParams.replyQuote != null) {
                            quote = TdApiMessageConverter.fromTLRPC(finalSendMessageParams.replyQuote);
                        }
                        tdReplyTo = new TdApi.InputMessageReplyToMessage(replyToDialogId, replyToMsgId, quote);
                    }

                    ArrayList<TdApi.TextEntity> tdEntities = TdApiMessageConverter.fromTLRPC(finalEntities);
                    boolean disableLinkPreview = !finalSearchLinks;
                     if (finalMediaWebPage != null || finalWebPage != null) {
                       disableLinkPreview = false;
                    }
                    TdApi.LinkPreviewOptions linkPreviewOptions = TdApiMessageConverter.fromTLRPC(
                        finalWebPage,
                        disableLinkPreview,
                        finalMediaWebPage != null && finalMediaWebPage.force_small_media,
                        finalMediaWebPage != null && finalMediaWebPage.force_large_media,
                        finalNewMsgTLRPC.invert_media
                    );
                    if (linkPreviewOptions != null && finalWebPage != null && finalWebPage.url != null && TextUtils.isEmpty(linkPreviewOptions.url)) {
                        linkPreviewOptions.url = finalWebPage.url; // Ensure URL is set if webPage is present
                    }


                    TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
                    options.disableNotification = !finalNotify;
                    options.fromBackground = false;
                    if (finalScheduleDate != 0) {
                        if (finalScheduleDate == 0x7FFFFFFE) {
                            options.schedulingState = new TdApi.MessageSchedulingStateSendWhenOnline();
                        } else {
                            options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate(finalScheduleDate);
                        }
                    }
                    options.effectId = finalSendMessageParams.effect_id;

                    long messageThreadId = 0;
                    if (finalReplyToTopMsg != null && DialogObject.isChatDialog(peer)) {
                        TLRPC.Chat chat = getMessagesController().getChat(-peer);
                        if (ChatObject.isForum(chat)) {
                            messageThreadId = finalReplyToTopMsg.getId();
                        }
                    }

                    if (finalNewMsgTLRPC.quick_reply_shortcut != null) {
                        if (finalNewMsgTLRPC.quick_reply_shortcut instanceof TLRPC.TL_inputQuickReplyShortcut) {
                            options.quickReplyShortcut = new TdApi.InputQuickReplyShortcut(((TLRPC.TL_inputQuickReplyShortcut) finalNewMsgTLRPC.quick_reply_shortcut).shortcut);
                        } else if (finalNewMsgTLRPC.quick_reply_shortcut instanceof TLRPC.TL_inputQuickReplyShortcutId) {
                            options.quickReplyShortcut = new TdApi.InputQuickReplyShortcutId(((TLRPC.TL_inputQuickReplyShortcutId) finalNewMsgTLRPC.quick_reply_shortcut).shortcut_id);
                        }
                    }

                    TdApi.ReplyMarkup tdReplyMarkup = TdApiMessageConverter.fromTLRPC(finalReplyMarkup);
                    TdApi.FormattedText formattedText = new TdApi.FormattedText(finalMessageString, tdEntities); // Use finalMessageString here
                    TdApi.InputMessageText inputMessageText = new TdApi.InputMessageText(formattedText, linkPreviewOptions, finalRetryMessageObject == null);

                    if (finalRetryMessageObject == null) {
                        StarsController.getInstance(currentAccount).beforeSendingMessage(finalNewMsgObj);
                    }
                    putToSendingMessages(finalNewMsgTLRPC, finalScheduleDate != 0);

                    long sendAsChatId = 0;
                     if (finalNewMsgTLRPC.from_id != null) {
                        long fromPeerId = MessageObject.getPeerId(finalNewMsgTLRPC.from_id);
                        if (fromPeerId != getUserConfig().getClientUserId()) {
                            sendAsChatId = TdApiMessageConverter.getChatId(fromPeerId);
                        }
                    }

                    tdApiManager.sendMessageText(
                            TdApiMessageConverter.getChatId(peer),
                            messageThreadId,
                            sendAsChatId,
                            finalMessageString,
                            tdEntities,
                            linkPreviewOptions,
                            tdReplyTo,
                            options,
                            tdReplyMarkup,
                            new Client.ResultHandler() {
                                @Override
                                public void onResult(TdApi.Object object) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        if (object.getConstructor() == TdApi.Message.CONSTRUCTOR) {
                                            TdApi.Message sentTdMessage = (TdApi.Message) object;
                                            TLRPC.Message tlSentMessage = TdApiMessageConverter.toTLRPC(sentTdMessage);

                                            if (tlSentMessage != null) {
                                                final int oldId = finalNewMsgObj.messageOwner.id;
                                                final ArrayList<TLRPC.Message> sentMessagesList = new ArrayList<>();
                                                sentMessagesList.add(tlSentMessage);
                                                final int existFlags = finalNewMsgObj.getMediaExistanceFlags();

                                                finalNewMsgObj.messageOwner.id = tlSentMessage.id;
                                                finalNewMsgObj.messageOwner.date = tlSentMessage.date;
                                                finalNewMsgObj.messageOwner.params = tlSentMessage.params;
                                                finalNewMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                finalNewMsgObj.messageOwner.errorAllowedPriceStars = 0;
                                                finalNewMsgObj.messageOwner.errorNewPriceStars = 0;

                                                if (tlSentMessage.via_bot_id != 0) {
                                                    finalNewMsgObj.messageOwner.via_bot_id = tlSentMessage.via_bot_id;
                                                    finalNewMsgObj.messageOwner.flags |= 2048;
                                                }
                                                 if (tlSentMessage.media instanceof TLRPC.TL_messageMediaDice && finalNewMsgObj.messageOwner.media instanceof TLRPC.TL_messageMediaDice) {
                                                    ((TLRPC.TL_messageMediaDice)finalNewMsgObj.messageOwner.media).value = ((TLRPC.TL_messageMediaDice)tlSentMessage.media).value;
                                                }

                                                if (MessageObject.isLiveLocationMessage(finalNewMsgObj)) {
                                                    getLocationController().addSharingLocation(finalNewMsgObj);
                                                }

                                                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, tlSentMessage.id, tlSentMessage, peer, 0L, existFlags, finalScheduleDate != 0);
                                                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, tlSentMessage.id, tlSentMessage, peer, 0L, existFlags, finalScheduleDate != 0);

                                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                                    int currentMode = finalScheduleDate != 0 ? ChatActivity.MODE_SCHEDULED : 0;
                                                     if (finalNewMsgObj.messageOwner.quick_reply_shortcut_id != 0 || finalNewMsgObj.messageOwner.quick_reply_shortcut != null) {
                                                        currentMode = ChatActivity.MODE_QUICK_REPLIES;
                                                    }
                                                    getMessagesStorage().updateMessageStateAndId(finalNewMsgObj.messageOwner.random_id, MessageObject.getPeerId(finalNewMsgObj.messageOwner.peer_id), oldId, tlSentMessage.id, 0, false, finalScheduleDate != 0 ? 1 : 0, finalNewMsgObj.messageOwner.quick_reply_shortcut_id);
                                                    getMessagesStorage().putMessages(sentMessagesList, true, false, false, 0, currentMode, finalNewMsgObj.messageOwner.quick_reply_shortcut_id);
                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        getMediaDataController().increasePeerRaiting(peer);
                                                        processSentMessage(oldId);
                                                        removeFromSendingMessages(oldId, finalScheduleDate != 0);
                                                        if (finalRetryMessageObject == null) {
                                                            getMediaDataController().cleanDraft(peer, finalReplyToTopMsg != null ? finalReplyToTopMsg.getId() : 0, true);
                                                        }
                                                        if (finalNewMsgObj.type == MEDIA_TYPE_DICE && sentTdMessage.content instanceof TdApi.MessageDice) {
                                                            getMediaDataController().setDiceValue(finalNewMsgObj.getDialogId(), finalNewMsgObj.getId(), ((TdApi.MessageDice) sentTdMessage.content).value);
                                                        } else if (finalNewMsgObj.messageOwner.media instanceof TLRPC.TL_messageMediaGame || finalNewMsgObj.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                                                             if(finalParams != null && finalParams.containsKey("bot_id") && finalParams.containsKey("id")) {
                                                                getMessagesStorage().saveBotCache(finalParams.get("bot_id") + "_" + finalParams.get("id"), tlSentMessage);
                                                            }
                                                        }
                                                    });
                                                });
                                            } else {
                                                 handleSendError(finalNewMsgObj, finalScheduleDate != 0, "TDLib message conversion failed (toTLRPC returned null)", null);
                                            }
                                        } else if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                                            TdApi.Error tdError = (TdApi.Error) object;
                                            handleSendError(finalNewMsgObj, finalScheduleDate != 0, tdError.message, tdError);
                                        } else {
                                            handleSendError(finalNewMsgObj, finalScheduleDate != 0, "Unknown TDLib response: " + object.toString(), null);
                                        }
                                    });
                                }
                            }
                    );
                    // --- END Refactored Text Message Sending ---
                }
            } else if (type == MEDIA_TYPE_DICE) {
                 if (encryptedChat == null) {
                    // --- START Refactored Dice Message Sending using TdApiManager ---
                    final TdApiManager tdApiManager = TdApiManager.getInstance(currentAccount);
                    final MessageObject finalNewMsgObj = newMsgObj;
                    final TLRPC.Message finalNewMsgTLRPC = newMsg;
                    final String finalEmoji = message; // Dice emoji is in the 'message' field for dice type
                    final TLRPC.ReplyMarkup finalReplyMarkup = replyMarkup;
                    final int finalScheduleDate = scheduleDate;
                    final boolean finalNotify = notify;
                    final MessageObject finalReplyToMsg = replyToMsg;
                    final MessageObject finalReplyToTopMsg = replyToTopMsg;
                    final TL_stories.StoryItem finalReplyToStoryItem = replyToStoryItem;
                    final SendMessageParams finalSendMessageParams = sendMessageParams;

                    TdApi.InputMessageReplyTo tdReplyTo = null;
                    if (finalReplyToStoryItem != null) {
                        tdReplyTo = new TdApi.InputMessageReplyToStory(TdApiMessageConverter.getChatId(finalReplyToStoryItem.dialogId), finalReplyToStoryItem.id);
                    } else if (finalReplyToMsg != null) {
                        long replyToMsgId = finalReplyToMsg.getId();
                        long replyToDialogId = TdApiMessageConverter.getChatId(finalReplyToMsg.getDialogId());
                         if (finalNewMsgTLRPC.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                            TLRPC.TL_messageReplyHeader header = (TLRPC.TL_messageReplyHeader) finalNewMsgTLRPC.reply_to;
                            if ((header.flags & 1) != 0 && header.reply_to_peer_id != null) {
                                 replyToDialogId = TdApiMessageConverter.getChatId(DialogObject.getPeerDialogId(header.reply_to_peer_id));
                            }
                        }
                        TdApi.TextQuote quote = null;
                        if (finalSendMessageParams.replyQuote != null) {
                            quote = TdApiMessageConverter.fromTLRPC(finalSendMessageParams.replyQuote);
                        }
                        tdReplyTo = new TdApi.InputMessageReplyToMessage(replyToDialogId, replyToMsgId, quote);
                    }

                    TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
                    options.disableNotification = !finalNotify;
                    options.fromBackground = false;
                    if (finalScheduleDate != 0) {
                         if (finalScheduleDate == 0x7FFFFFFE) {
                            options.schedulingState = new TdApi.MessageSchedulingStateSendWhenOnline();
                        } else {
                            options.schedulingState = new TdApi.MessageSchedulingStateSendAtDate(finalScheduleDate);
                        }
                    }
                     options.effectId = finalSendMessageParams.effect_id;

                    long messageThreadId = 0;
                    if (finalReplyToTopMsg != null && DialogObject.isChatDialog(peer)) {
                        TLRPC.Chat chat = getMessagesController().getChat(-peer);
                        if (ChatObject.isForum(chat)) {
                            messageThreadId = finalReplyToTopMsg.getId();
                        }
                    }

                    if (finalNewMsgTLRPC.quick_reply_shortcut != null) {
                        if (finalNewMsgTLRPC.quick_reply_shortcut instanceof TLRPC.TL_inputQuickReplyShortcut) {
                            options.quickReplyShortcut = new TdApi.InputQuickReplyShortcut(((TLRPC.TL_inputQuickReplyShortcut) finalNewMsgTLRPC.quick_reply_shortcut).shortcut);
                        } else if (finalNewMsgTLRPC.quick_reply_shortcut instanceof TLRPC.TL_inputQuickReplyShortcutId) {
                            options.quickReplyShortcut = new TdApi.InputQuickReplyShortcutId(((TLRPC.TL_inputQuickReplyShortcutId) finalNewMsgTLRPC.quick_reply_shortcut).shortcut_id);
                        }
                    }

                    TdApi.ReplyMarkup tdReplyMarkup = TdApiMessageConverter.fromTLRPC(finalReplyMarkup);

                    putToSendingMessages(finalNewMsgTLRPC, finalScheduleDate != 0);

                    long sendAsChatId = 0;
                     if (finalNewMsgTLRPC.from_id != null) {
                        long fromPeerId = MessageObject.getPeerId(finalNewMsgTLRPC.from_id);
                        if (fromPeerId != getUserConfig().getClientUserId()) {
                            sendAsChatId = TdApiMessageConverter.getChatId(fromPeerId);
                        }
                    }

                    TdApi.InputMessageDice inputMessageDice = new TdApi.InputMessageDice(finalEmoji, true); // Clear draft for dice

                    tdApiManager.send(
                        new TdApi.SendMessage(
                            TdApiMessageConverter.getChatId(peer),
                            messageThreadId,
                            sendAsChatId,
                            tdReplyTo,
                            options,
                            tdReplyMarkup,
                            inputMessageDice
                        ),
                        new Client.ResultHandler() {
                             @Override
                            public void onResult(TdApi.Object object) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (object.getConstructor() == TdApi.Message.CONSTRUCTOR) {
                                        TdApi.Message sentTdMessage = (TdApi.Message) object;
                                        TLRPC.Message tlSentMessage = TdApiMessageConverter.toTLRPC(sentTdMessage);

                                        if (tlSentMessage != null) {
                                            final int oldId = finalNewMsgObj.messageOwner.id;
                                            final ArrayList<TLRPC.Message> sentMessagesList = new ArrayList<>();
                                            sentMessagesList.add(tlSentMessage);
                                            final int existFlags = finalNewMsgObj.getMediaExistanceFlags();

                                            finalNewMsgObj.messageOwner.id = tlSentMessage.id;
                                            finalNewMsgObj.messageOwner.date = tlSentMessage.date;
                                            finalNewMsgObj.messageOwner.params = tlSentMessage.params;
                                            finalNewMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                            finalNewMsgObj.messageOwner.errorAllowedPriceStars = 0;
                                            finalNewMsgObj.messageOwner.errorNewPriceStars = 0;

                                            if (tlSentMessage.media instanceof TLRPC.TL_messageMediaDice && finalNewMsgObj.messageOwner.media instanceof TLRPC.TL_messageMediaDice) {
                                                ((TLRPC.TL_messageMediaDice)finalNewMsgObj.messageOwner.media).value = ((TLRPC.TL_messageMediaDice)tlSentMessage.media).value;
                                            }

                                            getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, tlSentMessage.id, tlSentMessage, peer, 0L, existFlags, finalScheduleDate != 0);
                                            getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, tlSentMessage.id, tlSentMessage, peer, 0L, existFlags, finalScheduleDate != 0);

                                            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                                int currentMode = finalScheduleDate != 0 ? ChatActivity.MODE_SCHEDULED : 0;
                                                if (finalNewMsgObj.messageOwner.quick_reply_shortcut_id != 0 || finalNewMsgObj.messageOwner.quick_reply_shortcut != null) {
                                                    currentMode = ChatActivity.MODE_QUICK_REPLIES;
                                                }
                                                getMessagesStorage().updateMessageStateAndId(finalNewMsgObj.messageOwner.random_id, MessageObject.getPeerId(finalNewMsgObj.messageOwner.peer_id), oldId, tlSentMessage.id, 0, false, finalScheduleDate != 0 ? 1 : 0, finalNewMsgObj.messageOwner.quick_reply_shortcut_id);
                                                getMessagesStorage().putMessages(sentMessagesList, true, false, false, 0, currentMode, finalNewMsgObj.messageOwner.quick_reply_shortcut_id);
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    getMediaDataController().increasePeerRaiting(peer);
                                                    processSentMessage(oldId);
                                                    removeFromSendingMessages(oldId, finalScheduleDate != 0);
                                                    if (finalNewMsgObj.type == MEDIA_TYPE_DICE && sentTdMessage.content instanceof TdApi.MessageDice) {
                                                         getMediaDataController().setDiceValue(finalNewMsgObj.getDialogId(), finalNewMsgObj.getId(), ((TdApi.MessageDice) sentTdMessage.content).value);
                                                    }
                                                });
                                            });
                                        } else {
                                            handleSendError(finalNewMsgObj, finalScheduleDate != 0, "TDLib dice message conversion failed", null);
                                        }
                                    } else if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                                        TdApi.Error tdError = (TdApi.Error) object;
                                        handleSendError(finalNewMsgObj, finalScheduleDate != 0, tdError.message, tdError);
                                    } else {
                                        handleSendError(finalNewMsgObj, finalScheduleDate != 0, "Unknown TDLib response for dice: " + object.toString(), null);
                                    }
                                });
                            }
                        }
                    );
                    // --- END Refactored Dice Message Sending ---
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                }
            } else if (type == MEDIA_TYPE_STORY) {
                if (encryptedChat == null) {
                    TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                    reqSend.silent = newMsg.silent;
                    reqSend.peer = sendToPeer;
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = "";
                    if (payStars > 0) {
                        reqSend.flags |= 2097152;
                        reqSend.allow_paid_stars = payStars;
                    }
                    TLRPC.TL_inputMediaStory inputMediaStory = new TLRPC.TL_inputMediaStory();
                    inputMediaStory.peer = getMessagesController().getInputPeer(sendingStory.dialogId);
                    inputMediaStory.id = sendingStory.id;
                    reqSend.media = inputMediaStory;
                    if (replyToStoryItem != null) {
                        reqSend.reply_to = createReplyInput(replyToStoryItem);
                        reqSend.flags |= 1;
                    } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                        reqSend.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                        reqSend.flags |= 1;
                    }
                    if (newMsg.from_id != null) {
                        reqSend.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                    }
                    if (scheduleDate != 0) {
                        reqSend.schedule_date = scheduleDate;
                        reqSend.flags |= 1024;
                    }
                    if (newMsg.quick_reply_shortcut != null) {
                        reqSend.flags |= 131072;
                        reqSend.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                    }
                    if (sendMessageParams.effect_id != 0) {
                        reqSend.flags |= 262144;
                        reqSend.effect = sendMessageParams.effect_id;
                    }
                    applyMonoForumPeerId(reqSend, sendMessageParams.monoForumPeer);
                    if (sendMessageParams.suggestionParams != null) {
                        reqSend.suggested_post = sendMessageParams.suggestionParams.toTl();
                    } else if (retryMessageObject != null && retryMessageObject.messageOwner != null && retryMessageObject.messageOwner.suggested_post != null) {
                        reqSend.suggested_post = retryMessageObject.messageOwner.suggested_post;
                    }
                    performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                }
            } else if (type == 1) {
                if (encryptedChat == null) {
                    TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                    reqSend.silent = newMsg.silent;
                    reqSend.peer = sendToPeer;
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = "";
                    if (payStars > 0) {
                        reqSend.flags |= 2097152;
                        reqSend.allow_paid_stars = payStars;
                    }
                    if (params != null && params.containsKey("heading")) {
                        reqSend.media = new TLRPC.TL_inputMediaGeoPoint();
                        reqSend.media.geo_point = location.geo;
                        reqSend.media.heading = Utilities.parseInt(params.get("heading"));
                        reqSend.media.flags |= 1;
                        if (params.containsKey("period")) {
                            reqSend.media.period = Utilities.parseInt(params.get("period"));
                            reqSend.media.flags |= 2;
                        }
                        if (params.containsKey("proximity_notification_radius")) {
                            reqSend.media.proximity_notification_radius = Utilities.parseInt(params.get("proximity_notification_radius"));
                            reqSend.media.flags |= 4;
                        }
                    } else if (params != null && params.containsKey("pid")) {
                        reqSend.media = new TLRPC.TL_inputMediaVenue();
                        reqSend.media.geo_point = location.geo;
                        reqSend.media.title = location.title;
                        reqSend.media.address = location.address;
                        reqSend.media.provider = location.provider;
                        reqSend.media.venue_id = location.venue_id;
                        reqSend.media.venue_type = location.venue_type;
                    } else {
                        reqSend.media = new TLRPC.TL_inputMediaGeoPoint();
                        reqSend.media.geo_point = location.geo;
                    }
                    if (replyToStoryItem != null) {
                        reqSend.reply_to = createReplyInput(replyToStoryItem);
                        reqSend.flags |= 1;
                    } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                        reqSend.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                        reqSend.flags |= 1;
                    }
                    if (newMsg.from_id != null) {
                        reqSend.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                    }
                    if (scheduleDate != 0) {
                        reqSend.schedule_date = scheduleDate;
                        reqSend.flags |= 1024;
                    }
                    if (newMsg.quick_reply_shortcut != null) {
                        reqSend.flags |= 131072;
                        reqSend.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                    }
                    if (sendMessageParams.effect_id != 0) {
                        reqSend.flags |= 262144;
                        reqSend.effect = sendMessageParams.effect_id;
                    }
                    applyMonoForumPeerId(reqSend, sendMessageParams.monoForumPeer);
                    if (sendMessageParams.suggestionParams != null) {
                        reqSend.suggested_post = sendMessageParams.suggestionParams.toTl();
                    } else if (retryMessageObject != null && retryMessageObject.messageOwner != null && retryMessageObject.messageOwner.suggested_post != null) {
                        reqSend.suggested_post = retryMessageObject.messageOwner.suggested_post;
                    }
                    if (retryMessageObject == null) {
                        StarsController.getInstance(currentAccount).beforeSendingMessage(newMsgObj);
                    }
                    performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
                } else {
                    TLRPC.TL_decryptedMessage reqSend = new TLRPC.TL_decryptedMessage();
                    reqSend.random_id = newMsg.random_id;
                    reqSend.ttl = newMsg.ttl;
                    if (newMsg.message != null && newMsg.message.length() > 0) {
                        reqSend.message = newMsg.message;
                    } else {
                        reqSend.message = "";
                    }
                    if (newMsg.reply_to != null && newMsg.reply_to.reply_to_random_id != 0) {
                        reqSend.reply_to_random_id = newMsg.reply_to.reply_to_random_id;
                        reqSend.flags |= 1;
                    }
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
                    reqSend.media.lat = location.geo.lat;
                    reqSend.media._long = location.geo._long;
                    if (entities != null && !entities.isEmpty()) {
                        reqSend.entities = entities;
                        reqSend.flags |= 128;
                    }
                    if (newMsg.via_bot_name != null && newMsg.via_bot_name.length() > 0) {
                        reqSend.via_bot_name = newMsg.via_bot_name;
                        reqSend.flags |= 1 << 11;
                    }
                    getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, originalPath, newMsgObj);
                }
            } else if (type == 2 ||
