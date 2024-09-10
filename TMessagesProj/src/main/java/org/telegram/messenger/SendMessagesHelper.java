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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SendMessagesHelper extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    public static final int MEDIA_TYPE_DICE = 11;
    public static final int MEDIA_TYPE_STORY = 12;
    private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<>();
    private SparseArray<MessageObject> unsentMessages = new SparseArray<>();
    private SparseArray<TLRPC.Message> sendingMessages = new SparseArray<>();
    private SparseArray<TLRPC.Message> editingMessages = new SparseArray<>();
    private SparseArray<TLRPC.Message> uploadMessages = new SparseArray<>();
    private LongSparseArray<Integer> sendingMessagesIdDialogs = new LongSparseArray<>();
    private LongSparseArray<Integer> uploadingMessagesIdDialogs = new LongSparseArray<>();
    private HashMap<String, MessageObject> waitingForLocation = new HashMap<>();
    private HashMap<String, Boolean> waitingForCallback = new HashMap<>();
    private HashMap<String, List<String>> waitingForCallbackMap = new HashMap<>();
    private HashMap<String, byte[]> waitingForVote = new HashMap<>();
    private LongSparseArray<Long> voteSendTime = new LongSparseArray();
    private HashMap<String, ImportingHistory> importingHistoryFiles = new HashMap<>();
    private LongSparseArray<ImportingHistory> importingHistoryMap = new LongSparseArray<>();

    private HashMap<String, ImportingStickers> importingStickersFiles = new HashMap<>();
    private HashMap<String, ImportingStickers> importingStickersMap = new HashMap<>();

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
        public String httpLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
        public VideoEditedInfo videoEditedInfo;
        public boolean performMediaUpload;

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
                    getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, obj.getId());
                    processSentMessage(obj.getId());
                    removeFromUploadingMessages(obj.getId(), scheduled);
                }
                delayedMessages.remove( "group_" + groupId);
            } else {
                getMessagesStorage().markMessageAsSendError(obj.messageOwner, obj.scheduled ? 1 : 0);
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
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
                            if (media.file == null) {
                                media.file = file;
                                if (media.thumb == null && message.photoSize != null && message.photoSize.location != null && (message.obj == null || message.obj.videoEditedInfo == null || !message.obj.videoEditedInfo.isSticker)) {
                                    performSendDelayedMessage(message);
                                } else {
                                    performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, null, message.scheduled);
                                }
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
                                    message.photoSize = (TLRPC.PhotoSize) message.extraHashMap.get(location + "_t");
                                    if (media.thumb == null && message.photoSize != null && message.photoSize.location != null) {
                                        message.performMediaUpload = true;
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
                                    TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                                    if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                        message.photoSize = photoSize;
                                        message.locationParent = document;
                                    }
                                }
                                ArrayList<TLRPC.Message> messages = new ArrayList<>();
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

    public boolean retrySendMessage(MessageObject messageObject, boolean unsent) {
        if (messageObject.getId() >= 0) {
            if (messageObject.isEditing()) {
                editMessage(messageObject, null, null, null, null, null, true, messageObject.hasMediaSpoilers(), messageObject);
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
        sendMessage(SendMessagesHelper.SendMessageParams.of(messageObject));
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
            HashMap<String, String> params = null;
            if (DialogObject.isEncryptedDialog(did) && messageObject.messageOwner.peer_id != null && (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo || messageObject.messageOwner.media.document instanceof TLRPC.TL_document)) {
                params = new HashMap<>();
                params.put("parentObject", "sent_" + messageObject.messageOwner.peer_id.channel_id + "_" + messageObject.getId() + "_" + messageObject.getDialogId() + "_" + messageObject.type + "_" + messageObject.getSize());
            }
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                sendMessage(SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, false));
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                sendMessage(SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, null, false));
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                sendMessage(SendMessagesHelper.SendMessageParams.of(messageObject.messageOwner.media, did, messageObject.replyMessageObject, null, null, null, true, 0));
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact_old2();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                sendMessage(SendMessagesHelper.SendMessageParams.of(user, did, messageObject.replyMessageObject, null, null, null, true, 0));
            } else if (!DialogObject.isEncryptedDialog(did)) {
                ArrayList<MessageObject> arrayList = new ArrayList<>();
                arrayList.add(messageObject);
                sendMessage(arrayList, did, true, false, true, 0);
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
            sendMessage(SendMessagesHelper.SendMessageParams.of(messageObject.messageOwner.message, did, messageObject.replyMessageObject, null, webPage, true, entities, null, null, true, 0, null, false));
        } else if (DialogObject.isEncryptedDialog(did)) {
            ArrayList<MessageObject> arrayList = new ArrayList<>();
            arrayList.add(messageObject);
            sendMessage(arrayList, did, true, false, true, 0);
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

    public void sendSticker(TLRPC.Document document, String query, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, boolean updateStickersOrder, Object parentObject, String quick_reply_shortcut, int quick_reply_shortcut_id) {
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
            sendMessage(sendMessageParams);
        }
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate) {
        return sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, null);
    }

    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
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
                if (replyToTopMsg == null && msgObj.messageOwner.reply_to != null) {
                    if (
                        !(msgObj.messageOwner.reply_to.reply_to_peer_id == null || MessageObject.peersEqual(msgObj.messageOwner.reply_to.reply_to_peer_id, msgObj.messageOwner.peer_id)) ||
                        ids != null && (msgObj.messageOwner.reply_to.flags & 16) != 0 && ids.contains(msgObj.messageOwner.reply_to.reply_to_msg_id)
                    ) {
                        newMsg.flags |= 8;
                        newMsg.reply_to = msgObj.messageOwner.reply_to;
                    }
                }
                MessageObject newMsgObj = new MessageObject(currentAccount, newMsg, true, true);
                newMsgObj.scheduled = scheduleDate != 0;
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
                newMsgObj.wasJustSent = true;
                objArr.add(newMsgObj);
                arr.add(newMsg);

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

                if (replyToTopMsg != null) {
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

                    final ArrayList<TLRPC.Message> newMsgObjArr = arr;
                    final ArrayList<MessageObject> newMsgArr = new ArrayList<>(objArr);
                    final LongSparseArray<TLRPC.Message> messagesByRandomIdsFinal = messagesByRandomIds;
                    boolean scheduledOnline = scheduleDate == 0x7FFFFFFE;
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

                                    boolean currentSchedule = scheduleDate != 0;

                                    updates.updates.remove(a1);
                                    a1--;
                                    final TLRPC.Message message;
                                    if (update instanceof TLRPC.TL_updateNewMessage) {
                                        TLRPC.TL_updateNewMessage updateNewMessage = (TLRPC.TL_updateNewMessage) update;
                                        message = updateNewMessage.message;
                                        getMessagesController().processNewDifferenceParams(-1, updateNewMessage.pts, -1, updateNewMessage.pts_count);
                                    } else if (update instanceof TLRPC.TL_updateNewScheduledMessage) {
                                        TLRPC.TL_updateNewScheduledMessage updateNewMessage = (TLRPC.TL_updateNewScheduledMessage) update;
                                        message = updateNewMessage.message;
                                    } else if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
                                        QuickRepliesController.getInstance(currentAccount).processUpdate(update, null, 0);
                                        TLRPC.TL_updateQuickReplyMessage updateQuickReplyMessage = (TLRPC.TL_updateQuickReplyMessage) update;
                                        message = updateQuickReplyMessage.message;
                                    } else {
                                        TLRPC.TL_updateNewChannelMessage updateNewChannelMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                        message = updateNewChannelMessage.message;
                                        getMessagesController().processNewChannelDifferenceParams(updateNewChannelMessage.pts, updateNewChannelMessage.pts_count, message.peer_id.channel_id);
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

                                        if (scheduleDate != 0 && !currentSchedule) {
                                            AndroidUtilities.runOnUIThread(() -> {
                                                ArrayList<Integer> messageIds = new ArrayList<>();
                                                messageIds.add(oldId);
                                                getMessagesController().deleteMessages(messageIds, null, null, newMsgObj1.dialog_id, newMsgObj1.quick_reply_shortcut_id, false, ChatActivity.MODE_SCHEDULED);
                                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                                    getMessagesStorage().putMessages(sentMessages, true, false, false, 0, 0, 0);
                                                    AndroidUtilities.runOnUIThread(() -> {
                                                        ArrayList<MessageObject> messageObjects = new ArrayList<>();
                                                        messageObjects.add(new MessageObject(msgObj.currentAccount, msgObj.messageOwner, true, true));
                                                        getMessagesController().updateInterfaceWithMessages(newMsgObj1.dialog_id, messageObjects, 0);
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
                            AndroidUtilities.runOnUIThread(() -> {
                                newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj1.id);
                                processSentMessage(newMsgObj1.id);
                                removeFromSendingMessages(newMsgObj1.id, scheduleDate != 0);
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
                    processForwardFromMyName(messages.get(a), peer);
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

    public void editMessage(MessageObject messageObject, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, HashMap<String, String> params, boolean retry, boolean hasMediaSpoilers, Object parentObject) {
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
                } else {
                    type = 1;
                }

                newMsg.params = params;
                newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_EDITING;
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

            boolean performMediaUpload = false;

            if (type >= 1 && type <= 3 || type >= 5 && type <= 8) {
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
                    delayedMessage.performMediaUpload = performMediaUpload;
                    if (!document.thumbs.isEmpty()) {
                        TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                        if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                            delayedMessage.photoSize = photoSize;
                            delayedMessage.locationParent = document;
                        }
                    }
                    delayedMessage.videoEditedInfo = videoEditedInfo;
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
                }

                TLObject reqSend;

                TLRPC.TL_messages_editMessage request = new TLRPC.TL_messages_editMessage();
                request.id = messageObject.getId();
                request.peer = getMessagesController().getInputPeer(peer);
                request.invert_media = messageObject.messageOwner.invert_media;
                if (inputMedia != null) {
                    request.flags |= 16384;
                    request.media = inputMedia;
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
                    if (performMediaUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, null, true, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 3) {
                    if (performMediaUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 6) {
                    performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                } else if (type == 7) {
                    if (performMediaUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
                } else if (type == 8) {
                    if (performMediaUpload) {
                        performSendDelayedMessage(delayedMessage);
                    } else {
                        performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, params, messageObject.scheduled);
                    }
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
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TLRPC.account_Password currentPassword = (TLRPC.account_Password) response2;
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
                        poll = (TLRPC.TL_messageMediaPoll) newMsg.media;
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

            MessageObject reply = replyToMsg;
            if (replyToTopMsg != null && replyToTopMsg == reply && replyToTopMsg.getId() == 1 || destroyReply) {
                reply = null;
            }
            newMsgObj = new MessageObject(currentAccount, newMsg, reply, true, true);
            newMsgObj.sendAnimationData = sendAnimationData;
            newMsgObj.wasJustSent = true;
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
                ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(newMsgObj);
                ArrayList<TLRPC.Message> arr = new ArrayList<>();
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

            boolean performMediaUpload = false;

            if (type == 0 || type == 9 && message != null && encryptedChat != null) {
                if (encryptedChat == null) {
                    if (mediaWebPage != null) {
                        TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                        reqSend.message = message;
                        reqSend.clear_draft = retryMessageObject == null;
                        reqSend.silent = newMsg.silent;
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
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
                        performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
                        if (retryMessageObject == null) {
                            getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                        }
                    } else {
                        TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                        reqSend.message = message;
                        reqSend.clear_draft = retryMessageObject == null;
                        reqSend.silent = newMsg.silent;
                        reqSend.peer = sendToPeer;
                        reqSend.random_id = newMsg.random_id;
                        if (replyToStoryItem != null) {
                            reqSend.reply_to = createReplyInput(replyToStoryItem);
                            reqSend.flags |= 1;
                        } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                            reqSend.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                            reqSend.flags |= 1;
                        }
                        if (newMsg.quick_reply_shortcut != null) {
                            reqSend.flags |= 131072;
                            reqSend.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                        }
                        if (updateStickersOrder && SharedConfig.updateStickersOrderOnSend) {
                            reqSend.update_stickersets_order = true;
                        }
                        if (newMsg.from_id != null) {
                            reqSend.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                        }
                        if (!searchLinks) {
                            reqSend.no_webpage = true;
                        }
                        if (entities != null && !entities.isEmpty()) {
                            reqSend.entities = entities;
                            reqSend.flags |= 8;
                        }
                        if (scheduleDate != 0) {
                            reqSend.schedule_date = scheduleDate;
                            reqSend.flags |= 1024;
                        }
                        if (sendMessageParams.effect_id != 0) {
                            reqSend.flags |= 262144;
                            reqSend.effect = sendMessageParams.effect_id;
                        }
                        reqSend.invert_media = newMsg.invert_media;
                        performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
                        if (retryMessageObject == null) {
                            getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                        }
                    }
                } else {
                    TLRPC.TL_decryptedMessage reqSend;
                    reqSend = new TLRPC.TL_decryptedMessage();
                    reqSend.ttl = newMsg.ttl;
                    if (entities != null && !entities.isEmpty()) {
                        reqSend.entities = entities;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                    }
                    if (newMsg.reply_to != null && newMsg.reply_to.reply_to_random_id != 0) {
                        reqSend.reply_to_random_id = newMsg.reply_to.reply_to_random_id;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                    }
                    if (params != null && params.get("bot_name") != null) {
                        reqSend.via_bot_name = params.get("bot_name");
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                    }
                    reqSend.silent = newMsg.silent;
                    reqSend.random_id = newMsg.random_id;
                    reqSend.message = message;
                    if (webPage != null && webPage.url != null) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaWebPage();
                        reqSend.media.url = webPage.url;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    } else {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
                    }
                    getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    if (retryMessageObject == null) {
                        getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                    }
                }
            } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 9 && encryptedChat != null || type == 10 || type == MEDIA_TYPE_DICE || type == MEDIA_TYPE_STORY) {
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
                            inputMedia.flags |= 2;
                            if (location.heading != 0) {
                                inputMedia.heading = location.heading;
                                inputMedia.flags |= 4;
                            }
                            if (location.proximity_notification_radius != 0) {
                                inputMedia.proximity_notification_radius = location.proximity_notification_radius;
                                inputMedia.flags |= 8;
                            }
                        } else {
                            inputMedia = new TLRPC.TL_inputMediaGeoPoint();
                        }
                        inputMedia.geo_point = new TLRPC.TL_inputGeoPoint();
                        inputMedia.geo_point.lat = location.geo.lat;
                        inputMedia.geo_point._long = location.geo._long;
                    } else if (type == 2 || type == 9 && photo != null) {
                        TLRPC.TL_inputMediaUploadedPhoto uploadedPhoto = new TLRPC.TL_inputMediaUploadedPhoto();
                        uploadedPhoto.spoiler = hasMediaSpoilers;
                        if (ttl != 0) {
                            newMsg.ttl = uploadedPhoto.ttl_seconds = ttl;
                            uploadedPhoto.flags |= 2;
                        }
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
                        if (delayedMessage == null) {
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.type = 0;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.scheduled = scheduleDate != 0;
                        }
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
                        uploadedDocument.mime_type = document.mime_type;
                        uploadedDocument.attributes = document.attributes;
                        uploadedDocument.spoiler = hasMediaSpoilers;
                        if (forceNoSoundVideo || !MessageObject.isRoundVideoDocument(document) && (videoEditedInfo == null || !videoEditedInfo.muted && !videoEditedInfo.roundVideo)) {
                            uploadedDocument.nosound_video = true;
                            if (BuildVars.DEBUG_VERSION) {
                                FileLog.d("nosound_video = true");
                            }
                        }
                        if (ttl != 0) {
                            newMsg.ttl = uploadedDocument.ttl_seconds = ttl;
                            uploadedDocument.flags |= 2;
                        }
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
                            if (params != null && params.containsKey("query")) {
                                media.query = params.get("query");
                                media.flags |= 2;
                            }
                            media.spoiler = hasMediaSpoilers;
                            inputMedia = media;
                        }
                        if (delayedMessage == null) {
                            delayedMessage = new DelayedMessage(peer);
                            delayedMessage.type = 1;
                            delayedMessage.obj = newMsgObj;
                            delayedMessage.originalPath = originalPath;
                            delayedMessage.parentObject = parentObject;
                            delayedMessage.scheduled = scheduleDate != 0;
                        }
                        delayedMessage.inputUploadMedia = uploadedDocument;
                        delayedMessage.performMediaUpload = performMediaUpload;
                        if (!document.thumbs.isEmpty()) {
                            TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                            if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                delayedMessage.photoSize = photoSize;
                                delayedMessage.locationParent = document;
                            }
                        }
                        delayedMessage.videoEditedInfo = videoEditedInfo;
                    } else if (type == 6) {
                        inputMedia = new TLRPC.TL_inputMediaContact();
                        inputMedia.phone_number = user.phone;
                        inputMedia.first_name = user.first_name;
                        inputMedia.last_name = user.last_name;
                        if (!user.restriction_reason.isEmpty() && user.restriction_reason.get(0).text.startsWith("BEGIN:VCARD")) {
                            inputMedia.vcard = user.restriction_reason.get(0).text;
                        } else {
                            inputMedia.vcard = "";
                        }
                    } else if (type == 7 || type == 9) {
                        boolean http = false;
                        TLRPC.InputMedia uploadedMedia;
                        if (originalPath != null || path != null || document.access_hash == 0) {
                            uploadedMedia = new TLRPC.TL_inputMediaUploadedDocument();
                            uploadedMedia.spoiler = hasMediaSpoilers;
                            if (ttl != 0) {
                                newMsg.ttl = uploadedMedia.ttl_seconds = ttl;
                                uploadedMedia.flags |= 2;
                            }
                            if (forceNoSoundVideo || !TextUtils.isEmpty(path) && path.toLowerCase().endsWith("mp4") && (params == null || params.containsKey("forceDocument"))) {
                                uploadedMedia.nosound_video = true;
                            }
                            uploadedMedia.force_file = params != null && params.containsKey("forceDocument");
                            uploadedMedia.mime_type = document.mime_type;
                            uploadedMedia.attributes = document.attributes;
                        } else {
                            uploadedMedia = null;
                        }

                        if (document.access_hash == 0) {
                            inputMedia = uploadedMedia;
                            performMediaUpload = uploadedMedia instanceof TLRPC.TL_inputMediaUploadedDocument;
                        } else {
                            TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                            media.id = new TLRPC.TL_inputDocument();
                            media.id.id = document.id;
                            media.id.access_hash = document.access_hash;
                            media.id.file_reference = document.file_reference;
                            if (media.id.file_reference == null) {
                                media.id.file_reference = new byte[0];
                            }
                            if (params != null && params.containsKey("query")) {
                                media.query = params.get("query");
                                media.flags |= 2;
                            }
                            media.spoiler = hasMediaSpoilers;
                            inputMedia = media;
                        }
                        if (!http && uploadedMedia != null) {
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.type = 2;
                                delayedMessage.obj = newMsgObj;
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.parentObject = parentObject;
                                delayedMessage.scheduled = scheduleDate != 0;
                            }
                            delayedMessage.inputUploadMedia = uploadedMedia;
                            delayedMessage.performMediaUpload = performMediaUpload;
                            if (!document.thumbs.isEmpty()) {
                                TLRPC.PhotoSize photoSize = document.thumbs.get(0);
                                if (!(photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                                    delayedMessage.photoSize = photoSize;
                                    delayedMessage.locationParent = document;
                                }
                            }
                        }
                    } else if (type == 8) {
                        TLRPC.TL_inputMediaUploadedDocument uploadedDocument = new TLRPC.TL_inputMediaUploadedDocument();
                        uploadedDocument.mime_type = document.mime_type;
                        uploadedDocument.attributes = document.attributes;
                        uploadedDocument.spoiler = hasMediaSpoilers;
                        if (ttl != 0) {
                            newMsg.ttl = uploadedDocument.ttl_seconds = ttl;
                            uploadedDocument.flags |= 2;
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
                            if (params != null && params.containsKey("query")) {
                                media.query = params.get("query");
                                media.flags |= 2;
                            }
                            media.spoiler = hasMediaSpoilers;
                            inputMedia = media;
                        }
                        delayedMessage = new DelayedMessage(peer);
                        delayedMessage.type = 3;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.parentObject = parentObject;
                        delayedMessage.inputUploadMedia = uploadedDocument;
                        delayedMessage.performMediaUpload = performMediaUpload;
                        delayedMessage.scheduled = scheduleDate != 0;
                    } else if (type == 10) {
                        TLRPC.TL_inputMediaPoll inputMediaPoll = new TLRPC.TL_inputMediaPoll();
                        inputMediaPoll.poll = poll.poll;
                        if (params != null && params.containsKey("answers")) {
                            byte[] answers = Utilities.hexToBytes(params.get("answers"));
                            if (answers.length > 0) {
                                for (int a = 0; a < answers.length; a++) {
                                    inputMediaPoll.correct_answers.add(new byte[]{answers[a]});
                                }
                                inputMediaPoll.flags |= 1;
                            }
                        }
                        if (poll.results != null && !TextUtils.isEmpty(poll.results.solution)) {
                            inputMediaPoll.solution = poll.results.solution;
                            inputMediaPoll.solution_entities = poll.results.solution_entities;
                            inputMediaPoll.flags |= 2;
                        }
                        inputMedia = inputMediaPoll;
                    } else if (type == MEDIA_TYPE_DICE) {
                        TLRPC.TL_inputMediaDice inputMediaDice = new TLRPC.TL_inputMediaDice();
                        inputMediaDice.emoticon = message;
                        inputMedia = inputMediaDice;
                    } else if (type == MEDIA_TYPE_STORY) {
                        TLRPC.TL_inputMediaStory inputMediaStory = new TLRPC.TL_inputMediaStory();
                        inputMediaStory.id = sendingStory.id;
                        inputMediaStory.peer = MessagesController.getInstance(currentAccount).getInputPeer(sendingStory.dialogId);
                        inputMedia = inputMediaStory;
                    }
                    if (groupId == 0 && stars > 0 && inputMedia != null) {
                        TLRPC.TL_inputMediaPaidMedia inputPaidMedia = new TLRPC.TL_inputMediaPaidMedia();
                        inputPaidMedia.stars_amount = stars;
                        inputPaidMedia.extended_media.add(inputMedia);
                        inputMedia = inputPaidMedia;
                    }

                    TLObject reqSend;

                    if (groupId != 0) {
                        TLObject request;
                        if (delayedMessage.sendRequest != null) {
                            request = delayedMessage.sendRequest;
                        } else if (stars > 0) {
                            TLRPC.TL_messages_sendMedia req = new TLRPC.TL_messages_sendMedia();
                            req.peer = sendToPeer;
                            req.silent = newMsg.silent;
                            req.message = caption;
                            if (entities != null && !entities.isEmpty()) {
                                req.entities = entities;
                                req.flags |= 8;
                            }

                            if (newMsg.replyStory != null) {
                                req.flags |= 1;
                                req.reply_to = createReplyInput(replyToStoryItem);
                            } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                                req.flags |= 1;
                                req.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                            }
                            if (scheduleDate != 0) {
                                req.schedule_date = scheduleDate;
                                req.flags |= 1024;
                            }
                            if (newMsg.quick_reply_shortcut != null) {
                                req.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                                req.flags |= 131072;
                            }
                            if (newMsg.effect != 0) {
                                req.flags |= 262144;
                                req.effect = newMsg.effect;
                            }
                            req.invert_media = sendMessageParams.invert_media;

                            TLRPC.TL_inputMediaPaidMedia media = new TLRPC.TL_inputMediaPaidMedia();
                            media.stars_amount = stars;
                            req.random_id = newMsg.random_id;
                            req.media = media;

                            request = req;
                            delayedMessage.paidMedia = true;
                            delayedMessage.sendRequest = request;
                        } else {
                            TLRPC.TL_messages_sendMultiMedia req = new TLRPC.TL_messages_sendMultiMedia();
                            req.peer = sendToPeer;
                            req.silent = newMsg.silent;

                            if (newMsg.replyStory != null) {
                                req.flags |= 1;
                                req.reply_to = createReplyInput(replyToStoryItem);
                            } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                                req.flags |= 1;
                                req.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                            }
                            if (scheduleDate != 0) {
                                req.schedule_date = scheduleDate;
                                req.flags |= 1024;
                            }
                            if (newMsg.quick_reply_shortcut != null) {
                                req.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                                req.flags |= 131072;
                            }
                            if (newMsg.effect != 0) {
                                req.flags |= 262144;
                                req.effect = newMsg.effect;
                            }
                            req.invert_media = sendMessageParams.invert_media;
                            request = req;
                            delayedMessage.sendRequest = request;
                        }
                        if (delayedMessage.paidMedia && !delayedMessage.messages.isEmpty()) {
                            TLRPC.Message firstMessage = delayedMessage.messages.get(0);
                            TLRPC.MessageMedia media = newMsg.media;
                            if (media instanceof TLRPC.TL_messageMediaPaidMedia && !media.extended_media.isEmpty() && media.extended_media.get(0) instanceof TLRPC.TL_messageExtendedMedia) {
                                media = ((TLRPC.TL_messageExtendedMedia) media.extended_media.get(0)).media;
                            }
                            if (media != null && firstMessage.media instanceof TLRPC.TL_messageMediaPaidMedia) {
                                TLRPC.TL_messageExtendedMedia extMedia = new TLRPC.TL_messageExtendedMedia();
                                extMedia.attachPath = newMsg.attachPath;
                                extMedia.media = media;
                                firstMessage.media.extended_media.add(extMedia);
                            }
                        }
                        delayedMessage.messageObjects.add(newMsgObj);
                        delayedMessage.messages.add(newMsg);
                        delayedMessage.parentObjects.add(parentObject);
                        delayedMessage.locations.add(delayedMessage.photoSize);
                        delayedMessage.videoEditedInfos.add(delayedMessage.videoEditedInfo);
                        delayedMessage.httpLocations.add(delayedMessage.httpLocation);
                        delayedMessage.inputMedias.add(delayedMessage.inputUploadMedia);
                        delayedMessage.originalPaths.add(originalPath);
                        if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
                            TLRPC.TL_inputSingleMedia inputSingleMedia = new TLRPC.TL_inputSingleMedia();
                            inputSingleMedia.random_id = newMsg.random_id;
                            inputSingleMedia.media = inputMedia;
                            inputSingleMedia.message = caption;
                            if (entities != null && !entities.isEmpty()) {
                                inputSingleMedia.entities = entities;
                                inputSingleMedia.flags |= 1;
                            }
                            ((TLRPC.TL_messages_sendMultiMedia) request).multi_media.add(inputSingleMedia);
                        } else if (request instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) request).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                            ((TLRPC.TL_inputMediaPaidMedia) ((TLRPC.TL_messages_sendMedia) request).media).extended_media.add(inputMedia);
                        }
                        reqSend = request;
                    } else {
                        TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
                        request.peer = sendToPeer;
                        request.silent = newMsg.silent;
                        int replyToTopMsgInt = 0;
                        if (replyToTopMsg != null) {
                            replyToTopMsgInt = replyToTopMsg.getId();
                        }
                        if (replyToStoryItem != null) {
                            request.reply_to = createReplyInput(replyToStoryItem);
                            request.flags |= 1;
                        } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                            request.flags |= 1;
                            request.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                        }
                        request.random_id = newMsg.random_id;
                        if (newMsg.from_id != null) {
                            request.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                        }
                        request.media = inputMedia;
                        request.message = caption;
                        if (entities != null && !entities.isEmpty()) {
                            request.entities = entities;
                            request.flags |= 8;
                        }
                        if (scheduleDate != 0) {
                            request.schedule_date = scheduleDate;
                            request.flags |= 1024;
                        }
                        if (updateStickersOrder && SharedConfig.updateStickersOrderOnSend) {
                            request.update_stickersets_order = true;
                        }
                        if (newMsg.quick_reply_shortcut != null) {
                            request.flags |= 131072;
                            request.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                        }
                        if (sendMessageParams.effect_id != 0) {
                            request.flags |= 262144;
                            request.effect = sendMessageParams.effect_id;
                        }
                        request.invert_media = newMsg.invert_media;

                        if (delayedMessage != null) {
                            delayedMessage.sendRequest = request;
                        }
                        reqSend = request;
                    }
                    if (groupId != 0) {
                        performSendDelayedMessage(delayedMessage);
                    } else if (type == 1) {
                        performSendMessageRequest(reqSend, newMsgObj, null, delayedMessage, parentObject, params, scheduleDate != 0);
                    } else if (type == 2) {
                        if (performMediaUpload) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, originalPath, null, true, delayedMessage, parentObject, params, scheduleDate != 0);
                        }
                    } else if (type == 3) {
                        if (performMediaUpload) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, params, scheduleDate != 0);
                        }
                    } else if (type == 6) {
                        performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, params, scheduleDate != 0);
                    } else if (type == 7) {
                        if (performMediaUpload && delayedMessage != null) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, params, scheduleDate != 0);
                        }
                    } else if (type == 8) {
                        if (performMediaUpload) {
                            performSendDelayedMessage(delayedMessage);
                        } else {
                            performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, params, scheduleDate != 0);
                        }
                    } else if (type == 10 || type == MEDIA_TYPE_DICE || type == MEDIA_TYPE_STORY) {
                        performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, params, scheduleDate != 0);
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
                    if (newMsg.reply_to != null && newMsg.reply_to.reply_to_random_id != 0) {
                        reqSend.reply_to_random_id = newMsg.reply_to.reply_to_random_id;
                        reqSend.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                    }
                    reqSend.silent = newMsg.silent;
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
                        getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
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
                                if (params != null && params.containsKey("parentObject")) {
                                    delayedMessage.parentObject = params.get("parentObject");
                                } else {
                                    delayedMessage.parentObject = parentObject;
                                }
                                delayedMessage.performMediaUpload = true;
                                delayedMessage.scheduled = scheduleDate != 0;
                            }
                            if (!TextUtils.isEmpty(path) && path.startsWith("http")) {
                                delayedMessage.httpLocation = path;
                            } else {
                                delayedMessage.photoSize = photo.sizes.get(photo.sizes.size() - 1);
                                delayedMessage.locationParent = photo;
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
                            getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 3) {
                        TLRPC.PhotoSize thumb = getThumbForSecretChat(document.thumbs);
                        ImageLoader.fillPhotoSizeWithBytes(thumb);
                        if (MessageObject.isNewGifDocument(document) || MessageObject.isRoundVideoDocument(document)) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                            reqSend.media.attributes = document.attributes;
                            if (thumb != null && thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = thumb.bytes;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                            }
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo();
                            if (thumb != null && thumb.bytes != null) {
                                ((TLRPC.TL_decryptedMessageMediaVideo) reqSend.media).thumb = thumb.bytes;
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
                                reqSend.media.duration = (int) attribute.duration;
                                break;
                            }
                        }
                        reqSend.media.thumb_h = thumb.h;
                        reqSend.media.thumb_w = thumb.w;
                        if (document.key == null || groupId != 0) {
                            if (delayedMessage == null) {
                                delayedMessage = new DelayedMessage(peer);
                                delayedMessage.encryptedChat = encryptedChat;
                                delayedMessage.type = 1;
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.originalPath = originalPath;
                                delayedMessage.obj = newMsgObj;
                                if (params != null && params.containsKey("parentObject")) {
                                    delayedMessage.parentObject = params.get("parentObject");
                                } else {
                                    delayedMessage.parentObject = parentObject;
                                }
                                delayedMessage.performMediaUpload = true;
                                delayedMessage.scheduled = scheduleDate != 0;
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
                            getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                        }
                    } else if (type == 6) {
                        reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                        reqSend.media.phone_number = user.phone;
                        reqSend.media.first_name = user.first_name;
                        reqSend.media.last_name = user.last_name;
                        reqSend.media.user_id = user.id;
                        getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                    } else if (type == 7 || type == 9 && document != null) {
                        if (document.access_hash != 0 && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaExternalDocument();
                            reqSend.media.id = document.id;
                            reqSend.media.date = document.date;
                            reqSend.media.access_hash = document.access_hash;
                            reqSend.media.mime_type = document.mime_type;
                            reqSend.media.size = document.size;
                            reqSend.media.dc_id = document.dc_id;
                            reqSend.media.attributes = document.attributes;
                            TLRPC.PhotoSize thumb = getThumbForSecretChat(document.thumbs);
                            if (thumb != null) {
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb = thumb;
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb = new TLRPC.TL_photoSizeEmpty();
                                ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb.type = "s";
                            }
                            if (delayedMessage != null && delayedMessage.type == 5) {
                                delayedMessage.sendEncryptedRequest = reqSend;
                                delayedMessage.obj = newMsgObj;
                                performSendDelayedMessage(delayedMessage);
                            } else {
                                getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj);
                            }
                        } else {
                            reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                            reqSend.media.attributes = document.attributes;
                            reqSend.media.caption = caption;
                            TLRPC.PhotoSize thumb = getThumbForSecretChat(document.thumbs);
                            if (thumb != null) {
                                if (thumb instanceof TLRPC.TL_photoStrippedSize) {
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = thumb.bytes;
                                    reqSend.media.thumb_h = thumb.h;
                                    reqSend.media.thumb_w = thumb.w;
                                } else {
                                    ImageLoader.fillPhotoSizeWithBytes(thumb);
                                    ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = thumb.bytes;
                                    reqSend.media.thumb_h = thumb.h;
                                    reqSend.media.thumb_w = thumb.w;
                                }
                            } else {
                                ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                                reqSend.media.thumb_h = 0;
                                reqSend.media.thumb_w = 0;
                            }
                            reqSend.media.size = document.size;
                            reqSend.media.mime_type = document.mime_type;

                            if (document.key == null || groupId != 0) {
                                if (delayedMessage == null) {
                                    delayedMessage = new DelayedMessage(peer);
                                    delayedMessage.encryptedChat = encryptedChat;
                                    delayedMessage.type = 2;
                                    delayedMessage.sendEncryptedRequest = reqSend;
                                    delayedMessage.originalPath = originalPath;
                                    delayedMessage.obj = newMsgObj;
                                    if (params != null && params.containsKey("parentObject")) {
                                        delayedMessage.parentObject = params.get("parentObject");
                                    } else {
                                        delayedMessage.parentObject = parentObject;
                                    }
                                    delayedMessage.performMediaUpload = true;
                                    delayedMessage.scheduled = scheduleDate != 0;
                                }
                                if (path != null && path.length() > 0 && path.startsWith("http")) {
                                    delayedMessage.httpLocation = path;
                                }
                                if (groupId == 0) {
                                    performSendDelayedMessage(delayedMessage);
                                }
                            } else {
                                TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                                encryptedFile.id = document.id;
                                encryptedFile.access_hash = document.access_hash;
                                reqSend.media.key = document.key;
                                reqSend.media.iv = document.iv;
                                getSecretChatHelper().performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj);
                            }
                        }
                    } else if (type == 8) {
                        delayedMessage = new DelayedMessage(peer);
                        delayedMessage.encryptedChat = encryptedChat;
                        delayedMessage.sendEncryptedRequest = reqSend;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.type = 3;
                        delayedMessage.parentObject = parentObject;
                        delayedMessage.performMediaUpload = true;
                        delayedMessage.scheduled = scheduleDate != 0;

                        reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                        reqSend.media.attributes = document.attributes;
                        reqSend.media.caption = caption;
                        TLRPC.PhotoSize thumb = getThumbForSecretChat(document.thumbs);
                        if (thumb != null) {
                            ImageLoader.fillPhotoSizeWithBytes(thumb);
                            ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = thumb.bytes;
                            reqSend.media.thumb_h = thumb.h;
                            reqSend.media.thumb_w = thumb.w;
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
                        delayedMessage.performMediaUpload = true;
                        request.messages.add(reqSend);
                        TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                        encryptedFile.id = type == 3 || type == 7 ? 1 : 0;
                        request.files.add(encryptedFile);
                        performSendDelayedMessage(delayedMessage);
                    }
                    if (retryMessageObject == null) {
                        getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                    }
                }
            } else if (type == 4) {
                TLRPC.TL_messages_forwardMessages reqSend = new TLRPC.TL_messages_forwardMessages();
                reqSend.to_peer = sendToPeer;
                reqSend.with_my_score = retryMessageObject.messageOwner.with_my_score;
                if (replyToTopMsg != null) {
                    reqSend.top_msg_id = replyToTopMsg.getId();
                    reqSend.flags |= 512;
                }
                if (params != null && params.containsKey("fwd_id")) {
                    int fwdId = Utilities.parseInt(params.get("fwd_id"));
                    reqSend.drop_author = true;
                    long peerId = Utilities.parseLong(params.get("fwd_peer"));
                    if (peerId < 0) {
                        TLRPC.Chat chat = getMessagesController().getChat(-peerId);
                        if (ChatObject.isChannel(chat)) {
                            reqSend.from_peer = new TLRPC.TL_inputPeerChannel();
                            reqSend.from_peer.channel_id = chat.id;
                            reqSend.from_peer.access_hash = chat.access_hash;
                        } else {
                            reqSend.from_peer = new TLRPC.TL_inputPeerEmpty();
                        }
                    } else {
                        reqSend.from_peer = new TLRPC.TL_inputPeerEmpty();
                    }
                    reqSend.id.add(fwdId);
                } else {
                    reqSend.from_peer = new TLRPC.TL_inputPeerEmpty();
                }
                reqSend.silent = newMsg.silent;
                if (scheduleDate != 0) {
                    reqSend.schedule_date = scheduleDate;
                    reqSend.flags |= 1024;
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
                performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
            } else if (type == 9) {
                TLRPC.TL_messages_sendInlineBotResult reqSend = new TLRPC.TL_messages_sendInlineBotResult();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (newMsg.quick_reply_shortcut != null) {
                    reqSend.flags |= 131072;
                    reqSend.quick_reply_shortcut = newMsg.quick_reply_shortcut;
                }

                if (newMsg.from_id != null) {
                    reqSend.send_as = getMessagesController().getInputPeer(newMsg.from_id);
                }
                reqSend.hide_via = !params.containsKey("bot");
                if (replyToStoryItem != null) {
                    reqSend.reply_to = createReplyInput(replyToStoryItem);
                    reqSend.flags |= 1;
                } else if (newMsg.reply_to instanceof TLRPC.TL_messageReplyHeader) {
                    reqSend.flags |= 1;
                    reqSend.reply_to = createReplyInput((TLRPC.TL_messageReplyHeader) newMsg.reply_to);
                }
                reqSend.silent = newMsg.silent;
                if (scheduleDate != 0) {
                    reqSend.schedule_date = scheduleDate;
                    reqSend.flags |= 1024;
                }
                reqSend.query_id = Utilities.parseLong(params.get("query_id"));
                reqSend.id = params.get("id");
                if (retryMessageObject == null) {
                    reqSend.clear_draft = true;
                    getMediaDataController().cleanDraft(peer, replyToTopMsg != null ? replyToTopMsg.getId() : 0, false);
                }
                performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, params, scheduleDate != 0);
            }
        } catch (Exception e) {
            FileLog.e(e);
            getMessagesStorage().markMessageAsSendError(newMsg, scheduleDate != 0 ? 1 : 0);
            if (newMsgObj != null) {
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            }
            getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsg.id);
            processSentMessage(newMsg.id);
        }
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        performSendDelayedMessage(message, -1);
    }

    private TLRPC.PhotoSize getThumbForSecretChat(ArrayList<TLRPC.PhotoSize> arrayList) {
        if (arrayList == null || arrayList.isEmpty()) {
            return null;
        }
        for (int a = 0, N = arrayList.size(); a < N; a++) {
            TLRPC.PhotoSize size = arrayList.get(a);
            if (size == null || size instanceof TLRPC.TL_photoPathSize || size instanceof TLRPC.TL_photoSizeEmpty || size.location == null) {
                continue;
            }
            if (size instanceof TLRPC.TL_photoStrippedSize) {
                return size;
            }
            TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize_layer127();
            photoSize.type = size.type;
            photoSize.w = size.w;
            photoSize.h = size.h;
            photoSize.size = size.size;
            photoSize.bytes = size.bytes;
            if (photoSize.bytes == null) {
                photoSize.bytes = new byte[0];
            }
            photoSize.location = new TLRPC.TL_fileLocation_layer82();
            photoSize.location.dc_id = size.location.dc_id;
            photoSize.location.volume_id = size.location.volume_id;
            photoSize.location.local_id = size.location.local_id;
            photoSize.location.secret = size.location.secret;
            return photoSize;
        }
        return null;
    }

    private void performSendDelayedMessage(final DelayedMessage message, int index) {
        if (message.type == 0) {
            if (message.httpLocation != null) {
                putToDelayedMessages(message.httpLocation, message);
                ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file", currentAccount);
            } else {
                if (message.sendRequest != null) {
                    String location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString();
                    putToDelayedMessages(location, message);
                    getFileLoader().uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                    putToUploadingMessages(message.obj);
                } else {
                    String location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString();
                    if (message.sendEncryptedRequest != null && message.photoSize.location.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize, true).toString();
                            file = new File(location);
                        }
                        if (!file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(message.photoSize), message);
                            getFileLoader().loadFile(ImageLocation.getForObject(message.photoSize, message.locationParent), message.parentObject, "jpg", FileLoader.PRIORITY_HIGH, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    getFileLoader().uploadFile(location, true, true, ConnectionsManager.FileTypePhoto);
                    putToUploadingMessages(message.obj);
                }
            }
        } else if (message.type == 1) {
            if (message.videoEditedInfo != null && message.videoEditedInfo.needConvert()) {
                String location = message.obj.messageOwner.attachPath;
                TLRPC.Document document = message.obj.getDocument(); // TODO: paid media
                if (location == null) {
                    location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + "." + (message.videoEditedInfo.isSticker ? "webm" : "mp4");
                }
                putToDelayedMessages(location, message);
                if (!message.videoEditedInfo.alreadyScheduledConverting) {
                    MediaController.getInstance().scheduleVideoConvert(message.obj);
                }
                putToUploadingMessages(message.obj);
            } else {
                if (message.videoEditedInfo != null) {
                    if (message.videoEditedInfo.file != null) {
                        TLRPC.InputMedia media;
                        if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                        } else {
                            media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                        }
                        media.file = message.videoEditedInfo.file;
                        message.videoEditedInfo.file = null;
                    } else if (message.videoEditedInfo.encryptedFile != null) {
                        TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage) message.sendEncryptedRequest;
                        decryptedMessage.media.size = message.videoEditedInfo.estimatedSize;
                        decryptedMessage.media.key = message.videoEditedInfo.key;
                        decryptedMessage.media.iv = message.videoEditedInfo.iv;
                        getSecretChatHelper().performSendEncryptedRequest(decryptedMessage, message.obj.messageOwner, message.encryptedChat, message.videoEditedInfo.encryptedFile, message.originalPath, message.obj);
                        message.videoEditedInfo.encryptedFile = null;
                        return;
                    }
                }
                if (message.sendRequest != null) {
                    TLRPC.InputMedia media;
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                        media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                    } else {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    }
                    if (media instanceof TLRPC.TL_inputMediaPaidMedia && !((TLRPC.TL_inputMediaPaidMedia) media).extended_media.isEmpty()) {
                        media = ((TLRPC.TL_inputMediaPaidMedia) media).extended_media.get(0);
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        TLRPC.Document document = message.obj.getDocument();
                        if (location == null) {
                            location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        if (message.obj.videoEditedInfo == null || !message.obj.videoEditedInfo.notReadyYet) {
                            if (message.obj.videoEditedInfo != null && message.obj.videoEditedInfo.needConvert()) {
                                getFileLoader().uploadFile(location, false, false, document.size, ConnectionsManager.FileTypeVideo, false);
                            } else {
                                getFileLoader().uploadFile(location, false, false, ConnectionsManager.FileTypeVideo);
                            }
                        }
                        putToUploadingMessages(message.obj);
                    } else {
                        String ext = "jpg";
                        if (message.obj != null && message.obj.videoEditedInfo != null && message.obj.videoEditedInfo.isSticker) {
                            ext = "webp";
                        }
                        String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.photoSize.location.volume_id + "_" + message.photoSize.location.local_id + "." + ext;
                        putToDelayedMessages(location, message);
                        getFileLoader().uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                        putToUploadingMessages(message.obj);
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
                            file = getFileLoader().getPathToMessage(message.obj.messageOwner);
                            if (file != null && file.exists()) {
                                message.obj.messageOwner.attachPath = location = file.getAbsolutePath();
                                message.obj.attachPathExists = true;
                            }
                        }
                        if (file == null || !file.exists() && message.obj.getDocument() != null) {
                            file = getFileLoader().getPathToAttach(message.obj.getDocument(), false);
                            if (file != null && file.exists()) {
                                message.obj.messageOwner.attachPath = location = file.getAbsolutePath();
                                message.obj.attachPathExists = true;
                            }
                        }
                        if (file == null || !file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(document), message);
                            getFileLoader().loadFile(document, message.parentObject, FileLoader.PRIORITY_HIGH, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    if (message.obj.videoEditedInfo == null || !message.obj.videoEditedInfo.notReadyYet) {
                        if (message.obj.videoEditedInfo != null && message.obj.videoEditedInfo.needConvert()) {
                            getFileLoader().uploadFile(location, true, false, document.size, ConnectionsManager.FileTypeVideo, false);
                        } else {
                            getFileLoader().uploadFile(location, true, false, ConnectionsManager.FileTypeVideo);
                        }
                    }
                    putToUploadingMessages(message.obj);
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
                    } else {
                        media = ((TLRPC.TL_messages_editMessage) message.sendRequest).media;
                    }
                    if (media.file == null) {
                        String location = message.obj.messageOwner.attachPath;
                        putToDelayedMessages(location, message);
                        getFileLoader().uploadFile(location, message.sendRequest == null, false, ConnectionsManager.FileTypeFile);
                        putToUploadingMessages(message.obj);
                    } else if (media.thumb == null && message.photoSize != null && !(message.photoSize instanceof TLRPC.TL_photoStrippedSize)) {
                        String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.photoSize.location.volume_id + "_" + message.photoSize.location.local_id + ".jpg";
                        putToDelayedMessages(location, message);
                        getFileLoader().uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                        putToUploadingMessages(message.obj);
                    }
                } else {
                    String location = message.obj.messageOwner.attachPath;
                    TLRPC.Document document = message.obj.getDocument();

                    if (message.sendEncryptedRequest != null && document.dc_id != 0) {
                        File file = new File(location);
                        if (!file.exists()) {
                            file = getFileLoader().getPathToMessage(message.obj.messageOwner);
                            if (file != null && file.exists()) {
                                message.obj.messageOwner.attachPath = location = file.getAbsolutePath();
                                message.obj.attachPathExists = true;
                            }
                        }
                        if (file == null || !file.exists() && message.obj.getDocument() != null) {
                            file = getFileLoader().getPathToAttach(message.obj.getDocument(), false);
                            if (file != null && file.exists()) {
                                message.obj.messageOwner.attachPath = location = file.getAbsolutePath();
                                message.obj.attachPathExists = true;
                            }
                        }

                        if (file == null || !file.exists()) {
                            putToDelayedMessages(FileLoader.getAttachFileName(document), message);
                            getFileLoader().loadFile(document, message.parentObject, FileLoader.PRIORITY_HIGH, 0);
                            return;
                        }
                    }
                    putToDelayedMessages(location, message);
                    getFileLoader().uploadFile(location, true, false, ConnectionsManager.FileTypeFile);
                    putToUploadingMessages(message.obj);
                }
            }
        } else if (message.type == 3) {
            String location = message.obj.messageOwner.attachPath;
            putToDelayedMessages(location, message);
            getFileLoader().uploadFile(location, message.sendRequest == null, true, ConnectionsManager.FileTypeAudio);
            putToUploadingMessages(message.obj);
        } else if (message.type == 4) {
            boolean add = index < 0;
            if (message.performMediaUpload) {
                if (index < 0) {
                    index = message.messageObjects.size() - 1;
                }
                MessageObject messageObject = message.messageObjects.get(index);
                TLRPC.Document document = messageObject.getDocument();
                if (document == null && MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPaidMedia) {
                    TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) MessageObject.getMedia(messageObject);
                    TLRPC.MessageExtendedMedia emedia = index >= paidMedia.extended_media.size() ? null : paidMedia.extended_media.get(index);
                    TLRPC.MessageMedia media = emedia instanceof TLRPC.TL_messageExtendedMedia ? ((TLRPC.TL_messageExtendedMedia) emedia).media : null;
                    document = media == null ? null : media.document;
                }
                if (document != null) {
                    if (message.videoEditedInfo != null) {
                        String location = messageObject.messageOwner.attachPath;
                        if (location == null) {
                            location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        putToDelayedMessages(location, message);
                        message.extraHashMap.put(messageObject, location);
                        message.extraHashMap.put(location + "_i", messageObject);
                        if (message.photoSize != null && message.photoSize.location != null) {
                            message.extraHashMap.put(location + "_t", message.photoSize);
                        }
                        if (!message.videoEditedInfo.alreadyScheduledConverting) {
                            MediaController.getInstance().scheduleVideoConvert(messageObject);
                        }
                        message.obj = messageObject;
                        putToUploadingMessages(messageObject);
                    } else {
                        String documentLocation = messageObject.messageOwner.attachPath;
                        if (documentLocation == null) {
                            documentLocation = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + document.id + ".mp4";
                        }
                        if (message.sendRequest != null) {
                            TLRPC.InputMedia media = null;
                            if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                                TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                                media = request.multi_media.get(index).media;
                            } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                                TLRPC.TL_messages_sendMedia request = (TLRPC.TL_messages_sendMedia) message.sendRequest;
                                if (request.media instanceof TLRPC.TL_inputMediaPaidMedia) {
                                    media = ((TLRPC.TL_inputMediaPaidMedia) request.media).extended_media.get(index);
                                }
                            }
                            if (media != null && media.file == null) {
                                putToDelayedMessages(documentLocation, message);
                                message.extraHashMap.put(messageObject, documentLocation);
                                message.extraHashMap.put(documentLocation, media);
                                message.extraHashMap.put(documentLocation + "_i", messageObject);
                                if (message.photoSize != null && message.photoSize.location != null) {
                                    message.extraHashMap.put(documentLocation + "_t", message.photoSize);
                                }
                                if (messageObject.videoEditedInfo != null && messageObject.videoEditedInfo.needConvert()) {
                                    getFileLoader().uploadFile(documentLocation, false, false, document.size, ConnectionsManager.FileTypeVideo, false);
                                } else {
                                    getFileLoader().uploadFile(documentLocation, false, false, ConnectionsManager.FileTypeVideo);
                                }
                                putToUploadingMessages(messageObject);
                            } else if (message.photoSize != null) {
                                String location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.photoSize.location.volume_id + "_" + message.photoSize.location.local_id + ".jpg";
                                putToDelayedMessages(location, message);
                                message.extraHashMap.put(location + "_o", documentLocation);
                                message.extraHashMap.put(messageObject, location);
                                message.extraHashMap.put(location, media);
                                getFileLoader().uploadFile(location, false, true, ConnectionsManager.FileTypePhoto);
                                putToUploadingMessages(messageObject);
                            }
                        } else {
                            TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                            putToDelayedMessages(documentLocation, message);
                            message.extraHashMap.put(messageObject, documentLocation);
                            message.extraHashMap.put(documentLocation, request.files.get(index));
                            message.extraHashMap.put(documentLocation + "_i", messageObject);
                            if (message.photoSize != null && message.photoSize.location != null) {
                                message.extraHashMap.put(documentLocation + "_t", message.photoSize);
                            }
                            if (messageObject.videoEditedInfo != null && messageObject.videoEditedInfo.needConvert()) {
                                getFileLoader().uploadFile(documentLocation, true, false, document.size, ConnectionsManager.FileTypeVideo, false);
                            } else {
                                getFileLoader().uploadFile(documentLocation, true, false, ConnectionsManager.FileTypeVideo);
                            }
                            putToUploadingMessages(messageObject);
                        }
                    }
                    message.videoEditedInfo = null;
                    message.photoSize = null;
                } else {
                    if (message.httpLocation != null) {
                        putToDelayedMessages(message.httpLocation, message);
                        message.extraHashMap.put(messageObject, message.httpLocation);
                        message.extraHashMap.put(message.httpLocation, messageObject);
                        ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file", currentAccount);
                        message.httpLocation = null;
                    } else {
                        TLObject inputMedia;
                        if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                            TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                            inputMedia = request.multi_media.get(index).media;
                        } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) message.sendRequest).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                            inputMedia = ((TLRPC.TL_inputMediaPaidMedia) ((TLRPC.TL_messages_sendMedia) message.sendRequest).media).extended_media.get(index);
                        } else {
                            TLRPC.TL_messages_sendEncryptedMultiMedia request = (TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest;
                            inputMedia = request.files.get(index);
                        }
                        String location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString();
                        putToDelayedMessages(location, message);
                        message.extraHashMap.put(location, inputMedia);
                        message.extraHashMap.put(messageObject, location);
                        getFileLoader().uploadFile(location, message.sendEncryptedRequest != null, true, ConnectionsManager.FileTypePhoto);
                        putToUploadingMessages(messageObject);
                        message.photoSize = null;
                    }
                }
                message.performMediaUpload = false;
            } else if (!message.messageObjects.isEmpty()) {
                putToSendingMessages(message.messageObjects.get(message.messageObjects.size() - 1).messageOwner, message.finalGroupMessage != 0);
            }
            sendReadyToSendGroup(message, add, true);
        } else if (message.type == 5) {
            String key = "stickerset_" + message.obj.getId();
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = (TLRPC.InputStickerSet) message.parentObject;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    boolean found = false;
                    if (response != null) {
                        TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                        getMediaDataController().storeTempStickerSet(set);
                        TLRPC.TL_documentAttributeSticker_layer55 attributeSticker = (TLRPC.TL_documentAttributeSticker_layer55) message.locationParent;
                        attributeSticker.stickerset = new TLRPC.TL_inputStickerSetShortName();
                        attributeSticker.stickerset.short_name = set.set.short_name;
                        found = true;
                    }
                    ArrayList<DelayedMessage> arrayList = delayedMessages.remove(key);
                    if (arrayList != null && !arrayList.isEmpty()) {
                        if (found) {
                            getMessagesStorage().replaceMessageIfExists(arrayList.get(0).obj.messageOwner, null, null, false);
                        }
                        getSecretChatHelper().performSendEncryptedRequest((TLRPC.DecryptedMessage) message.sendEncryptedRequest, message.obj.messageOwner, message.encryptedChat, null, null, message.obj);
                    }
                });
            });
            putToDelayedMessages(key, message);
        }
    }

    private void uploadMultiMedia(final DelayedMessage message, final TLRPC.InputMedia inputMedia, final TLRPC.InputEncryptedFile inputEncryptedFile, String key) {
        if (inputMedia != null) {
            TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
            req.media = inputMedia;
            if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                TLRPC.TL_messages_sendMultiMedia multiMedia = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                req.peer = multiMedia.peer;
                for (int a = 0; a < multiMedia.multi_media.size(); a++) {
                    if (multiMedia.multi_media.get(a).media == inputMedia) {
                        putToSendingMessages(message.messages.get(a), message.scheduled);
                        getNotificationCenter().postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false);
                        break;
                    }
                }
            } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) message.sendRequest).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                TLRPC.TL_messages_sendMedia sendMedia = (TLRPC.TL_messages_sendMedia) message.sendRequest;
                req.peer = sendMedia.peer;
                TLRPC.TL_inputMediaPaidMedia multiMedia = (TLRPC.TL_inputMediaPaidMedia) sendMedia.media;
                for (int a = 0; a < multiMedia.extended_media.size(); a++) {
                    if (multiMedia.extended_media.get(a) == inputMedia) {
                        putToSendingMessages(message.messages.get(a), message.scheduled);
                        getNotificationCenter().postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false);
                        break;
                    }
                }
            }
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                TLRPC.InputMedia newInputMedia = null;
                if (response != null) {
                    TLRPC.MessageMedia messageMedia = (TLRPC.MessageMedia) response;
                    if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto && messageMedia instanceof TLRPC.TL_messageMediaPhoto) {
                        TLRPC.TL_inputMediaPhoto inputMediaPhoto = new TLRPC.TL_inputMediaPhoto();
                        inputMediaPhoto.id = new TLRPC.TL_inputPhoto();
                        inputMediaPhoto.id.id = messageMedia.photo.id;
                        inputMediaPhoto.id.access_hash = messageMedia.photo.access_hash;
                        inputMediaPhoto.id.file_reference = messageMedia.photo.file_reference;
                        inputMediaPhoto.spoiler = inputMedia.spoiler;
                        newInputMedia = inputMediaPhoto;
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("set uploaded photo");
                        }
                    } else if (inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument && messageMedia instanceof TLRPC.TL_messageMediaDocument) {
                        TLRPC.TL_inputMediaDocument inputMediaDocument = new TLRPC.TL_inputMediaDocument();
                        inputMediaDocument.id = new TLRPC.TL_inputDocument();
                        inputMediaDocument.id.id = messageMedia.document.id;
                        inputMediaDocument.id.access_hash = messageMedia.document.access_hash;
                        inputMediaDocument.id.file_reference = messageMedia.document.file_reference;
                        inputMediaDocument.spoiler = inputMedia.spoiler;
                        newInputMedia = inputMediaDocument;
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("set uploaded document");
                        }
                    }
                }
                if (newInputMedia != null) {
                    if (inputMedia.ttl_seconds != 0) {
                        newInputMedia.ttl_seconds = inputMedia.ttl_seconds;
                        newInputMedia.flags |= 1;
                    }
                    if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
                        TLRPC.TL_messages_sendMultiMedia req1 = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
                        for (int a = 0; a < req1.multi_media.size(); a++) {
                            if (req1.multi_media.get(a).media == inputMedia) {
                                req1.multi_media.get(a).media = newInputMedia;
                                break;
                            }
                        }
                    } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) message.sendRequest).media instanceof TLRPC.TL_inputMediaPaidMedia) {
                        TLRPC.TL_messages_sendMedia req1 = (TLRPC.TL_messages_sendMedia) message.sendRequest;
                        TLRPC.TL_inputMediaPaidMedia media = (TLRPC.TL_inputMediaPaidMedia) req1.media;
                        for (int a = 0; a < media.extended_media.size(); a++) {
                            if (media.extended_media.get(a) == inputMedia) {
                                media.extended_media.set(a, newInputMedia);
                                break;
                            }
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
                    putToSendingMessages(message.messages.get(a), message.scheduled);
                    getNotificationCenter().postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false);
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
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("final message not added, add");
                }
                putToDelayedMessages(key, message);
            } else {
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("final message not added");
                }
            }
            return;
        } else if (add) {
            delayedMessages.remove(key);
            final int mode;
            if (message.scheduled) {
                mode = ChatActivity.MODE_SCHEDULED;
            } else if (
                message.obj != null && message.obj.isQuickReply() ||
                message.messageObjects != null && !message.messageObjects.isEmpty() && message.messageObjects.get(0).isQuickReply()
            ) {
                mode = ChatActivity.MODE_QUICK_REPLIES;
            } else {
                mode = ChatActivity.MODE_DEFAULT;
            }
            if (message.paidMedia) {
                ArrayList<MessageObject> firstMessageObject = new ArrayList<>();
                firstMessageObject.add(message.messageObjects.get(0));
                ArrayList<TLRPC.Message> firstMessage = new ArrayList<>();
                firstMessage.add(message.messages.get(0));

                getMessagesStorage().putMessages(firstMessage, false, true, false, 0, mode, 0);
                getMessagesController().updateInterfaceWithMessages(message.peer, firstMessageObject, mode);
            } else {
                getMessagesStorage().putMessages(message.messages, false, true, false, 0, mode, 0);
                getMessagesController().updateInterfaceWithMessages(message.peer, message.messageObjects, mode);
            }
            if (!message.scheduled) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("add message");
            }
        }
        if (message.sendRequest instanceof TLRPC.TL_messages_sendMultiMedia) {
            TLRPC.TL_messages_sendMultiMedia request = (TLRPC.TL_messages_sendMultiMedia) message.sendRequest;
            for (int a = 0; a < request.multi_media.size(); a++) {
                TLRPC.InputMedia inputMedia = request.multi_media.get(a).media;
                if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("multi media not ready");
                    }
                    return;
                }
            }

            if (check) {
                DelayedMessage maxDelayedMessage = findMaxDelayedMessageForMessageId(message.finalGroupMessage, message.peer);
                if (maxDelayedMessage != null) {
                    maxDelayedMessage.addDelayedRequest(message.sendRequest, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled);
                    if (message.requests != null) {
                        maxDelayedMessage.requests.addAll(message.requests);
                    }
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("has maxDelayedMessage, delay");
                    }
                    return;
                }
            }
        } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia && ((TLRPC.TL_messages_sendMedia) message.sendRequest).media instanceof TLRPC.TL_inputMediaPaidMedia) {
            TLRPC.TL_messages_sendMedia request = (TLRPC.TL_messages_sendMedia) message.sendRequest;
            TLRPC.TL_inputMediaPaidMedia media = (TLRPC.TL_inputMediaPaidMedia) request.media;
            for (int a = 0; a < media.extended_media.size(); a++) {
                TLRPC.InputMedia inputMedia = media.extended_media.get(a);
                if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("multi media not ready");
                    }
                    return;
                }
            }

            if (check) {
                DelayedMessage maxDelayedMessage = findMaxDelayedMessageForMessageId(message.finalGroupMessage, message.peer);
                if (maxDelayedMessage != null) {
                    maxDelayedMessage.addDelayedRequest(message.sendRequest, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled);
                    if (message.requests != null) {
                        maxDelayedMessage.requests.addAll(message.requests);
                    }
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("has maxDelayedMessage, delay");
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
            performSendMessageRequestMulti((TLRPC.TL_messages_sendMultiMedia) message.sendRequest, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled);
        } else if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
            performSendMessageRequestMulti((TLRPC.TL_messages_sendMedia) message.sendRequest, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled);
        } else {
            getSecretChatHelper().performSendEncryptedRequest((TLRPC.TL_messages_sendEncryptedMultiMedia) message.sendEncryptedRequest, message);
        }

        message.sendDelayedRequests();
    }

    protected void putToSendingMessages(TLRPC.Message message, boolean scheduled) {
        if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
            AndroidUtilities.runOnUIThread(() -> putToSendingMessages(message, scheduled, true));
        } else {
            putToSendingMessages(message, scheduled, true);
        }
    }

    protected void putToSendingMessages(TLRPC.Message message, boolean scheduled, boolean notify) {
        if (message == null) {
            return;
        }
        if (message.id > 0) {
            editingMessages.put(message.id, message);
        } else {
            boolean contains = sendingMessages.indexOfKey(message.id) >= 0;
            removeFromUploadingMessages(message.id, scheduled);
            sendingMessages.put(message.id, message);
            if (!scheduled && !contains) {
                long did = MessageObject.getDialogId(message);
                sendingMessagesIdDialogs.put(did, sendingMessagesIdDialogs.get(did, 0) + 1);
                if (notify) {
                    getNotificationCenter().postNotificationName(NotificationCenter.sendingMessagesChanged);
                }
            }
        }
    }

    protected TLRPC.Message removeFromSendingMessages(int mid, boolean scheduled) {
        TLRPC.Message message;
        if (mid > 0) {
            message = editingMessages.get(mid);
            if (message != null) {
                editingMessages.remove(mid);
            }
        } else {
            message = sendingMessages.get(mid);
            if (message != null) {
                sendingMessages.remove(mid);
                if (!scheduled) {
                    long did = MessageObject.getDialogId(message);
                    Integer currentCount = sendingMessagesIdDialogs.get(did);
                    if (currentCount != null) {
                        int count = currentCount - 1;
                        if (count <= 0) {
                            sendingMessagesIdDialogs.remove(did);
                        } else {
                            sendingMessagesIdDialogs.put(did, count);
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.sendingMessagesChanged);
                    }
                }
            }
        }
        return message;
    }

    public int getSendingMessageId(long did) {
        for (int a = 0; a < sendingMessages.size(); a++) {
            TLRPC.Message message = sendingMessages.valueAt(a);
            if (message.dialog_id == did) {
                return message.id;
            }
        }
        for (int a = 0; a < uploadMessages.size(); a++) {
            TLRPC.Message message = uploadMessages.valueAt(a);
            if (message.dialog_id == did) {
                return message.id;
            }
        }
        return 0;
    }

    protected void putToUploadingMessages(MessageObject obj) {
        if (obj == null || obj.getId() > 0 || obj.scheduled) {
            return;
        }
        TLRPC.Message message = obj.messageOwner;
        boolean contains = uploadMessages.indexOfKey(message.id) >= 0;
        uploadMessages.put(message.id, message);
        if (!contains) {
            long did = MessageObject.getDialogId(message);
            uploadingMessagesIdDialogs.put(did, uploadingMessagesIdDialogs.get(did, 0) + 1);
            getNotificationCenter().postNotificationName(NotificationCenter.sendingMessagesChanged);
        }
    }

    protected void removeFromUploadingMessages(int mid, boolean scheduled) {
        if (mid > 0 || scheduled) {
            return;
        }
        TLRPC.Message message = uploadMessages.get(mid);
        if (message != null) {
            uploadMessages.remove(mid);
            long did = MessageObject.getDialogId(message);
            Integer currentCount = uploadingMessagesIdDialogs.get(did);
            if (currentCount != null) {
                int count = currentCount - 1;
                if (count <= 0) {
                    uploadingMessagesIdDialogs.remove(did);
                } else {
                    uploadingMessagesIdDialogs.put(did, count);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.sendingMessagesChanged);
            }
        }
    }

    public boolean isSendingMessage(int mid) {
        return sendingMessages.indexOfKey(mid) >= 0 || editingMessages.indexOfKey(mid) >= 0;
    }


    public boolean isSendingPaidMessage(int mid, int index) {
        DelayedMessage delayedMessage = null;
        if (delayedMessages != null) {
            for (ArrayList<DelayedMessage> arr : delayedMessages.values()) {
                if (arr == null) continue;
                for (DelayedMessage m : arr) {
                    if (m.messages == null) continue;
                    for (TLRPC.Message msg : m.messages) {
                        if (msg != null && msg.id == mid) {
                            delayedMessage = m;
                            break;
                        }
                    }
                    if (delayedMessage != null) break;
                }
                if (delayedMessage != null) break;
            }
        }
        if (delayedMessage != null && index >= 0 && index < delayedMessage.messages.size()) {
            mid = delayedMessage.messages.get(index).id;
        }
        return sendingMessages.indexOfKey(mid) >= 0 || editingMessages.indexOfKey(mid) >= 0;
    }

    public boolean isSendingMessageIdDialog(long did) {
        return sendingMessagesIdDialogs.get(did, 0) > 0;
    }

    public boolean isUploadingMessageIdDialog(long did) {
        return uploadingMessagesIdDialogs.get(did, 0) > 0;
    }

    protected void performSendMessageRequestMulti(final TLObject request, final ArrayList<MessageObject> msgObjs, final ArrayList<String> originalPaths, final ArrayList<Object> parentObjects, DelayedMessage delayedMessage, boolean scheduled) {
        for (int a = 0, size = msgObjs.size(); a < size; a++) {
            putToSendingMessages(msgObjs.get(a).messageOwner, scheduled);
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text)) {
                final int fileRefIndex = FileRefController.getFileRefErrorIndex(error.text);
                if (parentObjects != null) {
                    ArrayList<Object> arrayList = new ArrayList<>(parentObjects);
                    if (fileRefIndex >= 0) {
                        for (int i = 0; i < arrayList.size(); ++i) {
                            arrayList.set(i, fileRefIndex == i ? arrayList.get(i) : null);
                        }
                    }
                    getFileRefController().requestReference(arrayList, request, msgObjs, originalPaths, arrayList, delayedMessage, scheduled);
                    return;
                } else if (delayedMessage != null && !delayedMessage.getRetriedToSend(fileRefIndex)) {
                    delayedMessage.setRetriedToSend(fileRefIndex, true);
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean hasEmptyFile = false;
                        if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
                            TLRPC.TL_messages_sendMultiMedia req = (TLRPC.TL_messages_sendMultiMedia) request;
                            for (int a = 0, size = req.multi_media.size(); a < size; a++) {
                                if (fileRefIndex >= 0 ? fileRefIndex != a : delayedMessage.parentObjects.get(a) == null) {
                                    continue;
                                }
                                removeFromSendingMessages(msgObjs.get(a).getId(), scheduled);
                                TLRPC.TL_inputSingleMedia requestMedia = req.multi_media.get(a);
                                if (requestMedia.media instanceof TLRPC.TL_inputMediaPhoto) {
                                    requestMedia.media = delayedMessage.inputMedias.get(a);
                                } else if (requestMedia.media instanceof TLRPC.TL_inputMediaDocument) {
                                    requestMedia.media = delayedMessage.inputMedias.get(a);
                                }
                                delayedMessage.videoEditedInfo = delayedMessage.videoEditedInfos.get(a);
                                delayedMessage.httpLocation = delayedMessage.httpLocations.get(a);
                                delayedMessage.photoSize = delayedMessage.locations.get(a);
                                delayedMessage.performMediaUpload = true;
                                if (requestMedia.media.file == null || delayedMessage.photoSize != null) {
                                    hasEmptyFile = true;
                                }
                                performSendDelayedMessage(delayedMessage, a);
                            }
                        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
                            TLRPC.TL_messages_sendMedia req = (TLRPC.TL_messages_sendMedia) request;
                            TLRPC.TL_inputMediaPaidMedia paidMedia = (TLRPC.TL_inputMediaPaidMedia) req.media;
                            for (int a = 0, size = paidMedia.extended_media.size(); a < size; a++) {
                                if (fileRefIndex >= 0 ? fileRefIndex != a : delayedMessage.parentObjects.get(a) == null) {
                                    continue;
                                }
                                removeFromSendingMessages(msgObjs.get(a).getId(), scheduled);
                                TLRPC.InputMedia requestMedia = paidMedia.extended_media.get(a);
                                if (requestMedia instanceof TLRPC.TL_inputMediaPhoto) {
                                    paidMedia.extended_media.set(a, requestMedia = delayedMessage.inputMedias.get(a));
                                } else if (requestMedia instanceof TLRPC.TL_inputMediaDocument) {
                                    paidMedia.extended_media.set(a, requestMedia = delayedMessage.inputMedias.get(a));
                                }
                                delayedMessage.videoEditedInfo = delayedMessage.videoEditedInfos.get(a);
                                delayedMessage.httpLocation = delayedMessage.httpLocations.get(a);
                                delayedMessage.photoSize = delayedMessage.locations.get(a);
                                delayedMessage.performMediaUpload = true;
                                if (requestMedia.file == null || delayedMessage.photoSize != null) {
                                    hasEmptyFile = true;
                                }
                                performSendDelayedMessage(delayedMessage, a);
                            }
                        }
                        if (!hasEmptyFile) {
                            for (int i = 0; i < msgObjs.size(); i++) {
                                TLRPC.Message newMsgObj = msgObjs.get(i).messageOwner;
                                getMessagesStorage().markMessageAsSendError(newMsgObj, scheduled ? 1 : 0);
                                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                                processSentMessage(newMsgObj.id);
                                removeFromSendingMessages(newMsgObj.id, scheduled);
                            }
                        }
                    });
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                boolean isSentError = false;
                if (error == null) {
                    SparseArray<TLRPC.Message> newMessages = new SparseArray<>();
                    LongSparseArray<Integer> newIds = new LongSparseArray<>();
                    final TLRPC.Updates updates = (TLRPC.Updates) response;
                    ArrayList<TLRPC.Update> updatesArr = ((TLRPC.Updates) response).updates;
                    LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies = null;
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
                            Utilities.stageQueue.postRunnable(() -> getMessagesController().processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count));
                            updatesArr.remove(a);
                            a--;
                        } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                            final TLRPC.TL_updateNewChannelMessage newMessage = (TLRPC.TL_updateNewChannelMessage) update;
                            long channelId = MessagesController.getUpdateChannelId(newMessage);
                            TLRPC.Chat chat = getMessagesController().getChat(channelId);
                            if ((chat == null || chat.megagroup) && newMessage.message.reply_to != null && (newMessage.message.reply_to.reply_to_top_id != 0 || newMessage.message.reply_to.reply_to_msg_id != 0)) {
                                if (channelReplies == null) {
                                    channelReplies = new LongSparseArray<>();
                                }
                                long did = MessageObject.getDialogId(newMessage.message);
                                SparseArray<TLRPC.MessageReplies> replies = channelReplies.get(did);
                                if (replies == null) {
                                    replies = new SparseArray<>();
                                    channelReplies.put(did, replies);
                                }
                                int id = newMessage.message.reply_to.reply_to_top_id != 0 ? newMessage.message.reply_to.reply_to_top_id : newMessage.message.reply_to.reply_to_msg_id;
                                TLRPC.MessageReplies messageReplies = replies.get(id);
                                if (messageReplies == null) {
                                    messageReplies = new TLRPC.TL_messageReplies();
                                    replies.put(id, messageReplies);
                                }
                                if (newMessage.message.from_id != null) {
                                    messageReplies.recent_repliers.add(0, newMessage.message.from_id);
                                }
                                messageReplies.replies++;
                            }

                            newMessages.put(newMessage.message.id, newMessage.message);
                            Utilities.stageQueue.postRunnable(() -> getMessagesController().processNewChannelDifferenceParams(newMessage.pts, newMessage.pts_count, newMessage.message.peer_id.channel_id));
                            updatesArr.remove(a);
                            a--;
                            if (newMessage.message.pinned) {
                                Utilities.stageQueue.postRunnable(() -> {
                                    ArrayList<Integer> mids = new ArrayList<>();
                                    mids.add(newMessage.message.id);
                                    getMessagesStorage().updatePinnedMessages(-channelId, mids, true, -1, 0, false, null);
                                });
                            }
                        } else if (update instanceof TLRPC.TL_updateNewScheduledMessage) {
                            final TLRPC.TL_updateNewScheduledMessage newMessage = (TLRPC.TL_updateNewScheduledMessage) update;
                            newMessages.put(newMessage.message.id, newMessage.message);
                            updatesArr.remove(a);
                            a--;
                        } else if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
                            QuickRepliesController.getInstance(currentAccount).processUpdate(update, msgObjs.isEmpty() ? null : msgObjs.get(0).getQuickReplyName(), msgObjs.isEmpty() ? null : msgObjs.get(0).getQuickReplyId());
                            final TLRPC.TL_updateQuickReplyMessage newMessage = (TLRPC.TL_updateQuickReplyMessage) update;
                            newMessages.put(newMessage.message.id, newMessage.message);
                            updatesArr.remove(a);
                            a--;
                        }
                    }
                    if (channelReplies != null) {
                        getMessagesStorage().putChannelViews(null, null, channelReplies, true);
                        getNotificationCenter().postNotificationName(NotificationCenter.didUpdateMessagesViews, null, null, channelReplies, true);
                    }

                    for (int i = 0; i < msgObjs.size(); i++) {
                        final MessageObject msgObj = msgObjs.get(i);
                        final String originalPath = originalPaths.get(i);
                        final TLRPC.Message newMsgObj = msgObj.messageOwner;
                        final int oldId = newMsgObj.id;
                        final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                        final String attachPath = newMsgObj.attachPath;
                        final long grouped_id;
                        final int existFlags;

                        Integer id = newIds.get(newMsgObj.random_id);
                        if (id != null) {
                            TLRPC.Message message = newMessages.get(id);
                            if (message != null) {
                                MessageObject.getDialogId(message);
                                sentMessages.add(message);
                                if ((message.flags & 33554432) != 0) {
                                    msgObj.messageOwner.ttl_period = message.ttl_period;
                                    msgObj.messageOwner.flags |= 33554432;
                                }
                                if (request instanceof TLRPC.TL_messages_sendMedia) { // paid media
                                    updateMediaPaths(msgObjs.get(0), message, message.id, originalPaths, false, -1);
                                } else {
                                    updateMediaPaths(msgObj, message, message.id, originalPath, false);
                                }
                                existFlags = msgObj.getMediaExistanceFlags();
                                newMsgObj.id = message.id;
                                newMsgObj.quick_reply_shortcut_id = message.quick_reply_shortcut_id;
                                if (newMsgObj.quick_reply_shortcut_id != 0) {
                                    newMsgObj.flags |= 1073741824;
                                }
                                grouped_id = message.grouped_id;

                                if (!scheduled) {
                                    Integer value = getMessagesController().dialogs_read_outbox_max.get(message.dialog_id);
                                    if (value == null) {
                                        value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                                        getMessagesController().dialogs_read_outbox_max.put(message.dialog_id, value);
                                    }
                                    message.unread = value < message.id;
                                }
                            } else {
                                isSentError = true;
                                break;
                            }
                        } else {
                            isSentError = true;
                            break;
                        }

                        if (!isSentError) {
                            getStatsController().incrementSentItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, 1);
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                            getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id, existFlags, scheduled);
                            getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id, existFlags, scheduled);
                            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                int mode = scheduled ? ChatActivity.MODE_SCHEDULED : 0;
                                if (newMsgObj.quick_reply_shortcut_id != 0 || newMsgObj.quick_reply_shortcut != null) {
                                    mode = ChatActivity.MODE_QUICK_REPLIES;
                                }
                                getMessagesStorage().updateMessageStateAndId(newMsgObj.random_id, MessageObject.getPeerId(newMsgObj.peer_id), oldId, newMsgObj.id, 0, false, scheduled ? 1 : 0, newMsgObj.quick_reply_shortcut_id);
                                getMessagesStorage().putMessages(sentMessages, true, false, false, 0, mode, newMsgObj.quick_reply_shortcut_id);
                                AndroidUtilities.runOnUIThread(() -> {
                                    getMediaDataController().increasePeerRaiting(newMsgObj.dialog_id);
                                    getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id, existFlags, scheduled);
                                    getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, grouped_id, existFlags, scheduled);
                                    processSentMessage(oldId);
                                    removeFromSendingMessages(oldId, scheduled);
                                });
                            });
                        }
                    }
                    Utilities.stageQueue.postRunnable(() -> getMessagesController().processUpdates(updates, false));
                } else {
                    AlertsCreator.processError(currentAccount, error, null, request);
                    isSentError = true;
                }
                if (isSentError) {
                    for (int i = 0; i < msgObjs.size(); i++) {
                        TLRPC.Message newMsgObj = msgObjs.get(i).messageOwner;
                        getMessagesStorage().markMessageAsSendError(newMsgObj, scheduled ? 1 : 0);
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                        processSentMessage(newMsgObj.id);
                        removeFromSendingMessages(newMsgObj.id, scheduled);
                    }
                }
            });
        }, null, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter);
    }

    private void performSendMessageRequest(final TLObject req, final MessageObject msgObj, final String originalPath, DelayedMessage delayedMessage, Object parentObject, HashMap<String, String> params, boolean scheduled) {
        performSendMessageRequest(req, msgObj, originalPath, null, false, delayedMessage, parentObject, params, scheduled);
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

    protected void performSendMessageRequest(final TLObject req, final MessageObject msgObj, final String originalPath, DelayedMessage parentMessage, boolean check, DelayedMessage delayedMessage, Object parentObject, HashMap<String, String> params, boolean scheduled) {
        if (!(req instanceof TLRPC.TL_messages_editMessage)) {
            if (check) {
                DelayedMessage maxDelayedMessage = findMaxDelayedMessageForMessageId(msgObj.getId(), msgObj.getDialogId());
                if (maxDelayedMessage != null) {
                    maxDelayedMessage.addDelayedRequest(req, msgObj, originalPath, parentObject, delayedMessage, parentMessage != null ? parentMessage.scheduled : false);
                    if (parentMessage != null && parentMessage.requests != null) {
                        maxDelayedMessage.requests.addAll(parentMessage.requests);
                    }
                    return;
                }
            }
        }
        final TLRPC.Message newMsgObj = msgObj.messageOwner;
        putToSendingMessages(newMsgObj, scheduled);
        newMsgObj.reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null && (req instanceof TLRPC.TL_messages_sendMedia || req instanceof TLRPC.TL_messages_editMessage) && FileRefController.isFileRefError(error.text)) {
                if (parentObject != null) {
                    getFileRefController().requestReference(parentObject, req, msgObj, originalPath, parentMessage, check, delayedMessage, scheduled);
                    return;
                } else if (delayedMessage != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        removeFromSendingMessages(newMsgObj.id, scheduled);
                        if (req instanceof TLRPC.TL_messages_sendMedia) {
                            TLRPC.TL_messages_sendMedia request = (TLRPC.TL_messages_sendMedia) req;
                            if (request.media instanceof TLRPC.TL_inputMediaPhoto) {
                                request.media = delayedMessage.inputUploadMedia;
                            } else if (request.media instanceof TLRPC.TL_inputMediaDocument) {
                                request.media = delayedMessage.inputUploadMedia;
                            }
                        } else if (req instanceof TLRPC.TL_messages_editMessage) {
                            TLRPC.TL_messages_editMessage request = (TLRPC.TL_messages_editMessage) req;
                            if (request.media instanceof TLRPC.TL_inputMediaPhoto) {
                                request.media = delayedMessage.inputUploadMedia;
                            } else if (request.media instanceof TLRPC.TL_inputMediaDocument) {
                                request.media = delayedMessage.inputUploadMedia;
                            }
                        }
                        delayedMessage.performMediaUpload = true;
                        performSendDelayedMessage(delayedMessage);
                    });
                    return;
                }
            }
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
                            } else if (update instanceof TLRPC.TL_updateNewScheduledMessage) {
                                final TLRPC.TL_updateNewScheduledMessage newMessage = (TLRPC.TL_updateNewScheduledMessage) update;
                                message = newMessage.message;
                                break;
                            } else if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
                                QuickRepliesController.getInstance(currentAccount).processUpdate(update, MessageObject.getQuickReplyName(newMsgObj), MessageObject.getQuickReplyId(newMsgObj));
                                final TLRPC.TL_updateQuickReplyMessage newMessage = (TLRPC.TL_updateQuickReplyMessage) update;
                                message = newMessage.message;
                                break;
                            }
                        }
                        if (message != null) {
                            ImageLoader.saveMessageThumbs(message);
                            updateMediaPaths(msgObj, message, message.id, originalPath, false);
                        }
                        Utilities.stageQueue.postRunnable(() -> {
                            getMessagesController().processUpdates(updates, false);
                            AndroidUtilities.runOnUIThread(() -> {
                                processSentMessage(newMsgObj.id);
                                removeFromSendingMessages(newMsgObj.id, scheduled);
                            });
                        });

                    } else {
                        AlertsCreator.processError(currentAccount, error, null, req);
                        removeFromSendingMessages(newMsgObj.id, scheduled);
                        revertEditingMessageObject(msgObj);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {

                    boolean currentSchedule = scheduled;
                    boolean isSentError = false;
                    if (error == null) {
                        final int oldId = newMsgObj.id;
                        final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                        final String attachPath = newMsgObj.attachPath;
                        final int existFlags;
                        boolean scheduledOnline = newMsgObj.date == 0x7FFFFFFE;
                        if (response instanceof TLRPC.TL_updateShortSentMessage) {
                            final TLRPC.TL_updateShortSentMessage res = (TLRPC.TL_updateShortSentMessage) response;
                            updateMediaPaths(msgObj, null, res.id, null, false);
                            existFlags = msgObj.getMediaExistanceFlags();
                            newMsgObj.local_id = newMsgObj.id = res.id;
                            newMsgObj.date = res.date;
                            newMsgObj.entities = res.entities;
                            newMsgObj.out = res.out;
                            if ((res.flags & 33554432) != 0) {
                                newMsgObj.ttl_period = res.ttl_period;
                                newMsgObj.flags |= 33554432;
                            }
                            if (res.media != null) {
                                newMsgObj.media = res.media;
                                newMsgObj.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                                ImageLoader.saveMessageThumbs(newMsgObj);
                            }
                            if ((res.media instanceof TLRPC.TL_messageMediaGame || res.media instanceof TLRPC.TL_messageMediaInvoice) && !TextUtils.isEmpty(res.message)) {
                                newMsgObj.message = res.message;
                            }
                            if (!newMsgObj.entities.isEmpty()) {
                                newMsgObj.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                            }
                            currentSchedule = false;
                            if (!currentSchedule) {
                                Integer value = getMessagesController().dialogs_read_outbox_max.get(newMsgObj.dialog_id);
                                if (value == null) {
                                    value = getMessagesStorage().getDialogReadMax(newMsgObj.out, newMsgObj.dialog_id);
                                    getMessagesController().dialogs_read_outbox_max.put(newMsgObj.dialog_id, value);
                                }
                                newMsgObj.unread = value < newMsgObj.id;
                            }
                            Utilities.stageQueue.postRunnable(() -> getMessagesController().processNewDifferenceParams(-1, res.pts, res.date, res.pts_count));
                            sentMessages.add(newMsgObj);
                        } else if (response instanceof TLRPC.Updates) {
                            final TLRPC.Updates updates = (TLRPC.Updates) response;
                            ArrayList<TLRPC.Update> updatesArr = ((TLRPC.Updates) response).updates;
                            TLRPC.Message message = null;
                            LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies = null;
                            for (int a = 0; a < updatesArr.size(); a++) {
                                TLRPC.Update update = updatesArr.get(a);
                                if (update instanceof TLRPC.TL_updateNewMessage && ((TLRPC.TL_updateNewMessage) update).message.action == null) {
                                    final TLRPC.TL_updateNewMessage newMessage = (TLRPC.TL_updateNewMessage) update;
                                    sentMessages.add(message = newMessage.message);
                                    Utilities.stageQueue.postRunnable(() -> getMessagesController().processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count));
                                    updatesArr.remove(a);
                                    break;
                                } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                                    final TLRPC.TL_updateNewChannelMessage newMessage = (TLRPC.TL_updateNewChannelMessage) update;
                                    long channelId = MessagesController.getUpdateChannelId(newMessage);
                                    TLRPC.Chat chat = getMessagesController().getChat(channelId);
                                    if ((chat == null || chat.megagroup) && newMessage.message.reply_to != null && (newMessage.message.reply_to.reply_to_top_id != 0 || newMessage.message.reply_to.reply_to_msg_id != 0)) {
                                        if (channelReplies == null) {
                                            channelReplies = new LongSparseArray<>();
                                        }
                                        long did = MessageObject.getDialogId(newMessage.message);
                                        SparseArray<TLRPC.MessageReplies> replies = channelReplies.get(did);
                                        if (replies == null) {
                                            replies = new SparseArray<>();
                                            channelReplies.put(did, replies);
                                        }
                                        int id = newMessage.message.reply_to.reply_to_top_id != 0 ? newMessage.message.reply_to.reply_to_top_id : newMessage.message.reply_to.reply_to_msg_id;
                                        TLRPC.MessageReplies messageReplies = replies.get(id);
                                        if (messageReplies == null) {
                                            messageReplies = new TLRPC.TL_messageReplies();
                                            replies.put(id, messageReplies);
                                        }
                                        if (newMessage.message.from_id != null) {
                                            messageReplies.recent_repliers.add(0, newMessage.message.from_id);
                                        }
                                        messageReplies.replies++;
                                    }

                                    sentMessages.add(message = newMessage.message);
                                    Utilities.stageQueue.postRunnable(() -> getMessagesController().processNewChannelDifferenceParams(newMessage.pts, newMessage.pts_count, newMessage.message.peer_id.channel_id));
                                    updatesArr.remove(a);
                                    a--;
                                    if (newMessage.message.pinned) {
                                        Utilities.stageQueue.postRunnable(() -> {
                                            ArrayList<Integer> mids = new ArrayList<>();
                                            mids.add(newMessage.message.id);
                                            getMessagesStorage().updatePinnedMessages(-channelId, mids, true, -1, 0, false, null);
                                        });
                                    }
                                    break;
                                } else if (update instanceof TLRPC.TL_updateNewScheduledMessage) {
                                    final TLRPC.TL_updateNewScheduledMessage newMessage = (TLRPC.TL_updateNewScheduledMessage) update;
                                    sentMessages.add(message = newMessage.message);
                                    updatesArr.remove(a);
                                    break;
                                } else if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
                                    QuickRepliesController.getInstance(currentAccount).processUpdate(update, msgObj.getQuickReplyName(), msgObj.getQuickReplyId());
                                    final TLRPC.TL_updateQuickReplyMessage newMessage = (TLRPC.TL_updateQuickReplyMessage) update;
                                    sentMessages.add(message = newMessage.message);
                                    updatesArr.remove(a);
                                    break;
                                }
                            }
                            if (channelReplies != null) {
                                getMessagesStorage().putChannelViews(null, null, channelReplies, true);
                                getNotificationCenter().postNotificationName(NotificationCenter.didUpdateMessagesViews, null, null, channelReplies, true);
                            }
                            if (message != null) {
                                MessageObject.getDialogId(message);
                                if (scheduledOnline && message.date != 0x7FFFFFFE) {
                                    currentSchedule = false;
                                }
                                ImageLoader.saveMessageThumbs(message);
                                if (!currentSchedule) {
                                    Integer value = getMessagesController().dialogs_read_outbox_max.get(message.dialog_id);
                                    if (value == null) {
                                        value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                                        getMessagesController().dialogs_read_outbox_max.put(message.dialog_id, value);
                                    }
                                    message.unread = value < message.id;
                                }
                                msgObj.messageOwner.post_author = message.post_author;
                                if ((message.flags & 33554432) != 0) {
                                    msgObj.messageOwner.ttl_period = message.ttl_period;
                                    msgObj.messageOwner.flags |= 33554432;
                                }
                                msgObj.messageOwner.entities = message.entities;
                                msgObj.messageOwner.quick_reply_shortcut_id = message.quick_reply_shortcut_id;
                                if (msgObj.messageOwner.quick_reply_shortcut_id != 0) {
                                    msgObj.messageOwner.flags |= 1073741824;
                                }
                                updateMediaPaths(msgObj, message, message.id, originalPath, false);
                                existFlags = msgObj.getMediaExistanceFlags();
                                newMsgObj.id = message.id;
                            } else {
                                isSentError = true;
                                existFlags = 0;
                                if (BuildVars.LOGS_ENABLED) {
                                    StringBuilder builder = new StringBuilder();
                                    for (int i = 0; i < updatesArr.size(); i++) {
                                        builder.append(updatesArr.get(i).getClass().getSimpleName()).append(", ");
                                    }
                                    FileLog.d("can't find message in updates " + builder);
                                }
                            }
                            Utilities.stageQueue.postRunnable(() -> getMessagesController().processUpdates(updates, false));
                        } else {
                            existFlags = 0;
                        }

                        if (MessageObject.isLiveLocationMessage(newMsgObj) && newMsgObj.via_bot_id == 0 && TextUtils.isEmpty(newMsgObj.via_bot_name)) {
                            getLocationController().addSharingLocation(newMsgObj);
                        }

                        if (!isSentError) {
                            getStatsController().incrementSentItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, 1);
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                            if (scheduled && !currentSchedule) {
                                ArrayList<Integer> messageIds = new ArrayList<>();
                                messageIds.add(oldId);
                                getMessagesController().deleteMessages(messageIds, null, null, newMsgObj.dialog_id, 0, false, ChatActivity.MODE_SCHEDULED);
                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                    getMessagesStorage().putMessages(sentMessages, true, false, false, 0, false, 0, 0);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        ArrayList<MessageObject> messageObjects = new ArrayList<>();
                                        messageObjects.add(new MessageObject(msgObj.currentAccount, msgObj.messageOwner, true, true));
                                        getMessagesController().updateInterfaceWithMessages(newMsgObj.dialog_id, messageObjects, 0);
                                        getMediaDataController().increasePeerRaiting(newMsgObj.dialog_id);
                                        processSentMessage(oldId);
                                        removeFromSendingMessages(oldId, scheduled);
                                    });
                                });
                            } else {
                                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled);
                                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled);
                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                    int mode = scheduled ? ChatActivity.MODE_SCHEDULED : 0;
                                    if (newMsgObj.quick_reply_shortcut_id != 0 || newMsgObj.quick_reply_shortcut != null) {
                                        mode = ChatActivity.MODE_QUICK_REPLIES;
                                    }
                                    getMessagesStorage().updateMessageStateAndId(newMsgObj.random_id, MessageObject.getPeerId(newMsgObj.peer_id), oldId, newMsgObj.id, 0, false, scheduled ? 1 : 0, newMsgObj.quick_reply_shortcut_id);
                                    getMessagesStorage().putMessages(sentMessages, true, false, false, 0, mode, newMsgObj.quick_reply_shortcut_id);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        getMediaDataController().increasePeerRaiting(newMsgObj.dialog_id);
                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled);
                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer2, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled);
                                        processSentMessage(oldId);
                                        removeFromSendingMessages(oldId, scheduled);
                                    });
                                });
                            }
                        }
                    } else {
                        AlertsCreator.processError(currentAccount, error, null, req);
                        isSentError = true;
                    }
                    if (isSentError) {
                        getMessagesStorage().markMessageAsSendError(newMsgObj, scheduled ? 1 : 0);
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                        getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                        processSentMessage(newMsgObj.id);
                        removeFromSendingMessages(newMsgObj.id, scheduled);
                    }
                });
            }
        }, () -> {
            final int msg_id = newMsgObj.id;
            AndroidUtilities.runOnUIThread(() -> {
                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByAck, msg_id);
            });
        }, ConnectionsManager.RequestFlagCanCompress | ConnectionsManager.RequestFlagInvokeAfter | (req instanceof TLRPC.TL_messages_sendMessage ? ConnectionsManager.RequestFlagNeedQuickAck : 0));
        if (parentMessage != null) {
            parentMessage.sendDelayedRequests();
        }
    }

    private void updateMediaPaths(MessageObject newMsgObj, TLRPC.Message sentMessage, int newMsgId, String originalPath, boolean post) {
        updateMediaPaths(newMsgObj, sentMessage, newMsgId, Collections.singletonList(originalPath), post, -1);
    }

    private void updateMediaPaths(MessageObject newMsgObj, TLRPC.Message sentMessage, int newMsgId, List<String> originalPaths, boolean post, int index) {
        TLRPC.Message newMsg = newMsgObj.messageOwner;

        final String originalPath = originalPaths.isEmpty() || Math.max(0, index) >= originalPaths.size() ? null : originalPaths.get(Math.max(0, index));
        boolean singleAlbum = false;
        TLRPC.MessageExtendedMedia sentEMedia = null;
        TLRPC.MessageMedia sentMedia = sentMessage == null ? null : sentMessage.media;
        TLRPC.MessageExtendedMedia newEMedia = null;
        TLRPC.MessageMedia newMedia = newMsg == null ? null : newMsg.media;

        TLRPC.PhotoSize strippedNew = null;
        if (newMsg.media != null) {
            TLRPC.PhotoSize strippedOld = null;
            TLObject photoObject = null;
            if (newMsg.media.storyItem != null) {
                sentMessage.media = newMsg.media;
            } else if (newMsgObj.isLiveLocation() && sentMessage.media instanceof TLRPC.TL_messageMediaGeoLive) {
                newMsg.media.period = sentMessage.media.period;
            } else if (newMsgObj.isDice()) {
                TLRPC.TL_messageMediaDice mediaDice = (TLRPC.TL_messageMediaDice) newMsg.media;
                TLRPC.TL_messageMediaDice mediaDiceNew = (TLRPC.TL_messageMediaDice) sentMessage.media;
                mediaDice.value = mediaDiceNew.value;
            } else if (newMsg.media.photo != null) {
                strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media.photo.sizes, 40);
                if (sentMessage != null && sentMessage.media != null && sentMessage.media.photo != null) {
                    strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMessage.media.photo.sizes, 40);
                } else {
                    strippedNew = strippedOld;
                }
                photoObject = newMsg.media.photo;
            } else if (newMsg.media.document != null) {
                strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media.document.thumbs, 40);
                if (sentMessage != null && sentMessage.media != null && sentMessage.media.document != null) {
                    strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMessage.media.document.thumbs, 40);
                } else {
                    strippedNew = strippedOld;
                }
                photoObject = newMsg.media.document;
            } else if (newMsg.media.webpage != null) {
                if (newMsg.media.webpage.photo != null) {
                    strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media.webpage.photo.sizes, 40);
                    if (sentMessage != null && sentMessage.media != null && sentMessage.media.webpage != null && sentMessage.media.webpage.photo != null) {
                        strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMessage.media.webpage.photo.sizes, 40);
                    } else {
                        strippedNew = strippedOld;
                    }
                    photoObject = newMsg.media.webpage.photo;
                } else if (newMsg.media.webpage.document != null) {
                    strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media.webpage.document.thumbs, 40);
                    if (sentMessage != null && sentMessage.media != null && sentMessage.media.webpage != null && sentMessage.media.webpage.document != null) {
                        strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMessage.media.webpage.document.thumbs, 40);
                    } else {
                        strippedNew = strippedOld;
                    }
                    photoObject = newMsg.media.webpage.document;
                }
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaPaidMedia && sentMedia instanceof TLRPC.TL_messageMediaPaidMedia) {
                TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) newMsg.media;
                TLRPC.TL_messageMediaPaidMedia sentPaidMedia = (TLRPC.TL_messageMediaPaidMedia) sentMedia;
                if (paidMedia.extended_media.isEmpty() || sentPaidMedia.extended_media.isEmpty()) {
                    return;
                }
                if (index == -1) {
                    for (int i = 0; i < paidMedia.extended_media.size(); ++i) {
                        updateMediaPaths(newMsgObj, sentMessage, newMsgId, originalPaths, post, i);
                    };
                    return;
                }
                singleAlbum = sentPaidMedia.extended_media.size() > 1;
                if (index < 0 || index >= sentPaidMedia.extended_media.size()) {
                    return;
                }
                TLRPC.MessageExtendedMedia emedia2 = sentPaidMedia.extended_media.get(index);
                if (emedia2 instanceof TLRPC.TL_messageExtendedMedia) {
                    TLRPC.TL_messageExtendedMedia eemedia2 = (TLRPC.TL_messageExtendedMedia) emedia2;
                    sentEMedia = eemedia2;
                    sentMedia = eemedia2.media;
                }

                TLRPC.MessageExtendedMedia emedia = paidMedia.extended_media.get(index);
                if (emedia instanceof TLRPC.TL_messageExtendedMedia) {
                    TLRPC.TL_messageExtendedMedia eemedia = (TLRPC.TL_messageExtendedMedia) emedia;
                    TLRPC.MessageMedia media = eemedia.media;
                    if (media.photo != null) {
                        strippedOld = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, 40);
                        if (sentMedia != null && sentMedia.photo != null) {
                            strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMedia.photo.sizes, 40);
                        } else {
                            strippedNew = strippedOld;
                        }
                        photoObject = media.photo;
                    } else if (media.document != null) {
                        strippedOld = FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, 40);
                        if (sentMedia != null && sentMedia.document != null) {
                            strippedNew = FileLoader.getClosestPhotoSizeWithSize(sentMedia.document.thumbs, 40);
                        } else {
                            strippedNew = strippedOld;
                        }
                        photoObject = media.document;
                    }

                    newEMedia = eemedia;
                    newMedia = media;
                }
            }
            if (strippedNew instanceof TLRPC.TL_photoStrippedSize && strippedOld instanceof TLRPC.TL_photoStrippedSize) {
                String oldKey = "stripped" + FileRefController.getKeyForParentObject(newMsgObj);
                String newKey = null;
                if (sentMessage != null) {
                    newKey = "stripped" + FileRefController.getKeyForParentObject(sentMessage);
                } else {
                    newKey = "stripped" + "message" + newMsgId + "_" + newMsgObj.getChannelId() + "_" + newMsgObj.scheduled;
                }
                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForObject(strippedNew, photoObject), post);
            }
        }
        if (sentMessage == null) {
            return;
        }
        if (sentMedia instanceof TLRPC.TL_messageMediaPhoto && sentMedia.photo != null && newMedia instanceof TLRPC.TL_messageMediaPhoto && newMedia.photo != null) {
            if (sentMedia.ttl_seconds == 0 && !newMsgObj.scheduled) {
                getMessagesStorage().putSentFile(originalPath, sentMedia.photo, 0, "sent_" + sentMessage.peer_id.channel_id + "_" + sentMessage.id + "_" + DialogObject.getPeerDialogId(sentMessage.peer_id) + "_" + MessageObject.TYPE_PHOTO + "_" + MessageObject.getMediaSize(newMedia));
            }

            if (newMedia.photo.sizes.size() == 1 && newMedia.photo.sizes.get(0).location instanceof TLRPC.TL_fileLocationUnavailable) {
                newMedia.photo.sizes = sentMedia.photo.sizes;
            } else {
                for (int b = 0; b < newMedia.photo.sizes.size(); b++) {
                    TLRPC.PhotoSize size2 = newMedia.photo.sizes.get(b);
                    if (size2 == null || size2.location == null || size2.type == null) {
                        continue;
                    }
                    boolean found = false;
                    for (int a = 0; a < sentMedia.photo.sizes.size(); a++) {
                        TLRPC.PhotoSize size = sentMedia.photo.sizes.get(a);
                        if (size == null || size.location == null || size instanceof TLRPC.TL_photoSizeEmpty || size.type == null) {
                            continue;
                        }
                        if (size2.location.volume_id == Integer.MIN_VALUE && size.type.equals(size2.type) || size.w == size2.w && size.h == size2.h) {
                            found = true;
                            String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                            String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                            if (fileName.equals(fileName2)) {
                                break;
                            }
                            File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                            File cacheFile2;
                            if (sentMedia.ttl_seconds == 0 && (sentMedia.photo.sizes.size() == 1 || size.w > 90 || size.h > 90) && !singleAlbum) {
                                cacheFile2 = FileLoader.getInstance(currentAccount).getPathToAttach(size);
                            } else {
                                cacheFile2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                            }
                            cacheFile.renameTo(cacheFile2);
                            ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, ImageLocation.getForPhoto(size, sentMedia.photo), post);
                            size2.location = size.location;
                            size2.size = size.size;
                            break;
                        }
                    }
                    if (!found) {
                        String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                        File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                        cacheFile.delete();
                        if ("s".equals(size2.type) && strippedNew != null) {
                            newMedia.photo.sizes.set(b, strippedNew);
                            ImageLocation location = ImageLocation.getForPhoto(strippedNew, sentMedia.photo);
                            ImageLoader.getInstance().replaceImageInCache(fileName, location.getKey(sentMessage, null, false), location, post);
                        }
                    }
                }
            }
            if (!singleAlbum) {
                newMsg.message = sentMessage.message;
                sentMessage.attachPath = newMsg.attachPath;
            } else {
                if (newEMedia != null && sentEMedia != null) {
                    newEMedia.attachPath = sentEMedia.attachPath;
                }
            }
            newMedia.photo.id = sentMedia.photo.id;
            newMedia.photo.dc_id = sentMedia.photo.dc_id;
            newMedia.photo.access_hash = sentMedia.photo.access_hash;
        } else if (sentMedia instanceof TLRPC.TL_messageMediaDocument && sentMedia.document != null && newMedia instanceof TLRPC.TL_messageMediaDocument && newMedia.document != null) {
            if (sentMedia.ttl_seconds == 0 && (newMsgObj.videoEditedInfo == null || newMsgObj.videoEditedInfo.mediaEntities == null && TextUtils.isEmpty(newMsgObj.videoEditedInfo.paintPath) && newMsgObj.videoEditedInfo.cropState == null)) {
                boolean isVideo = MessageObject.isVideoMessage(sentMessage);
                if ((isVideo || MessageObject.isGifMessage(sentMessage)) && MessageObject.isGifDocument(sentMedia.document) == MessageObject.isGifDocument(newMedia.document)) {
                    if (!newMsgObj.scheduled) {
                        MessageObject messageObject = new MessageObject(currentAccount, sentMessage, false, false);
                        getMessagesStorage().putSentFile(originalPath, sentMedia.document, 2, "sent_" + sentMessage.peer_id.channel_id + "_" + sentMessage.id + "_" + DialogObject.getPeerDialogId(sentMessage.peer_id) + "_" + messageObject.type + "_" + messageObject.getSize());
                    }
                    if (isVideo) {
                        sentMessage.attachPath = newMsg.attachPath;
                    }
                } else if (!MessageObject.isVoiceMessage(sentMessage) && !MessageObject.isRoundVideoMessage(sentMessage) && !newMsgObj.scheduled) {
                    MessageObject messageObject = new MessageObject(currentAccount, sentMessage, false, false);
                    getMessagesStorage().putSentFile(originalPath, sentMedia.document, 1, "sent_" + sentMessage.peer_id.channel_id + "_" + sentMessage.id + "_" + DialogObject.getPeerDialogId(sentMessage.peer_id) + "_" + messageObject.type + "_" + messageObject.getSize());
                }
            }

            TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(newMedia.document.thumbs, 320);
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(sentMedia.document.thumbs, 320);
            if (size2 != null && size2.location != null && size2.location.volume_id == Integer.MIN_VALUE && size != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                    File cacheFile2 = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName2 + ".jpg");
                    cacheFile.renameTo(cacheFile2);
                    ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, ImageLocation.getForDocument(size, sentMedia.document), post);
                    size2.location = size.location;
                    size2.size = size.size;
                }
            } else if (size != null && size2 != null && MessageObject.isStickerMessage(sentMessage) && size2.location != null) {
                size.location = size2.location;
            } else if (size2 == null || size2 != null && size2.location instanceof TLRPC.TL_fileLocationUnavailable || size2 instanceof TLRPC.TL_photoSizeEmpty) {
                newMedia.document.thumbs = sentMedia.document.thumbs;
            }

            newMedia.document.dc_id = sentMedia.document.dc_id;
            newMedia.document.id = sentMedia.document.id;
            newMedia.document.access_hash = sentMedia.document.access_hash;
            byte[] oldWaveform = null;
            for (int a = 0; a < newMedia.document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = newMedia.document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    oldWaveform = attribute.waveform;
                    break;
                }
            }
            newMedia.document.attributes = sentMedia.document.attributes;
            if (oldWaveform != null) {
                for (int a = 0; a < newMedia.document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = newMedia.document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        attribute.waveform = oldWaveform;
                        attribute.flags |= 4;
                    }
                }
            }
            newMedia.document.size = sentMedia.document.size;
            newMedia.document.mime_type = sentMedia.document.mime_type;

            if ((sentMessage.flags & TLRPC.MESSAGE_FLAG_FWD) == 0 && (MessageObject.isOut(sentMessage) || sentMessage.dialog_id == getUserConfig().getClientUserId()) && !MessageObject.isQuickReply(sentMessage)) {
                if (MessageObject.isNewGifDocument(sentMedia.document)) {
                    boolean save;
                    if (MessageObject.isDocumentHasAttachedStickers(sentMedia.document)) {
                        save = getMessagesController().saveGifsWithStickers;
                    } else {
                        save = true;
                    }
                    if (save) {
                        getMediaDataController().addRecentGif(sentMedia.document, sentMessage.date, true);
                    }
                } else if (MessageObject.isStickerDocument(sentMedia.document) || MessageObject.isAnimatedStickerDocument(sentMedia.document, true)) {
                    getMediaDataController().addRecentSticker(MediaDataController.TYPE_IMAGE, sentMessage, sentMedia.document, sentMessage.date, false);
                }
            }

            final String newAttachPath = newEMedia != null ? newEMedia.attachPath : newMsg.attachPath;

            if (newAttachPath != null && newAttachPath.startsWith(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath()) && !MessageObject.isGifDocument(sentMedia.document)) {
                File cacheFile = new File(newAttachPath);
                File cacheFile2 = FileLoader.getInstance(currentAccount).getPathToAttach(sentMedia.document, sentMedia.ttl_seconds != 0);
                if (!cacheFile.renameTo(cacheFile2)) {
                    if (cacheFile.exists()) {
                        if (newEMedia != null) {
                            newEMedia.attachPath = newAttachPath;
                        } else {
                            sentMessage.attachPath = newAttachPath;
                        }
                    } else {
                        if (newEMedia != null) {

                        } else {
                            newMsgObj.attachPathExists = false;
                        }
                    }
                    if (newEMedia != null) {

                    } else {
                        newMsgObj.mediaExists = cacheFile2.exists();
                    }
                    sentMessage.message = newMsg.message;
                } else {
                    if (MessageObject.isVideoMessage(sentMessage)) {
                        newMsgObj.attachPathExists = true;
                    } else {
                        newMsgObj.mediaExists = newMsgObj.attachPathExists;
                        newMsgObj.attachPathExists = false;
                        if (newEMedia != null) {
                            newEMedia.attachPath = "";
                        } else {
                            newMsg.attachPath = "";
                        }
                        if (originalPath != null && originalPath.startsWith("http")) {
                            getMessagesStorage().addRecentLocalFile(originalPath, cacheFile2.toString(), newMedia.document);
                        }
                    }
                }
            } else {
                if (newEMedia != null) {
                    newEMedia.attachPath = newAttachPath;
                } else {
                    sentMessage.attachPath = newAttachPath;
                    sentMessage.message = newMsg.message;
                }
            }
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaContact && newMsg.media instanceof TLRPC.TL_messageMediaContact) {
            newMsg.media = sentMessage.media;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaWebPage) {
            newMsg.media = sentMessage.media;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaGeo) {
            sentMessage.media.geo.lat = newMsg.media.geo.lat;
            sentMessage.media.geo._long = newMsg.media.geo._long;
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaGame || sentMessage.media instanceof TLRPC.TL_messageMediaInvoice) {
            newMsg.media = sentMessage.media;
            if (!TextUtils.isEmpty(sentMessage.message)) {
                newMsg.entities = sentMessage.entities;
                newMsg.message = sentMessage.message;
            }
            if (sentMessage.reply_markup != null) {
                newMsg.reply_markup = sentMessage.reply_markup;
                newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MARKUP;
            }
        } else if (sentMessage.media instanceof TLRPC.TL_messageMediaPoll) {
            newMsg.media = sentMessage.media;
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

    public long getNextRandomId() {
        long val = 0;
        while (val == 0) {
            val = Utilities.random.nextLong();
        }
        return val;
    }

    public void checkUnsentMessages() {
        getMessagesStorage().getUnsentMessages(1000);
    }

    protected void processUnsentMessages(final ArrayList<TLRPC.Message> messages, final ArrayList<TLRPC.Message> scheduledMessages, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesController().putUsers(users, true);
            getMessagesController().putChats(chats, true);
            getMessagesController().putEncryptedChats(encryptedChats, true);
            for (int a = 0, N = messages.size(); a < N; a++) {
                MessageObject messageObject = new MessageObject(currentAccount, messages.get(a), false, true);
                long groupId = messageObject.getGroupId();
                if (groupId != 0 && messageObject.messageOwner.params != null && !messageObject.messageOwner.params.containsKey("final")) {
                    if (a == N - 1 || messages.get(a + 1).grouped_id != groupId) {
                        messageObject.messageOwner.params.put("final", "1");
                    }
                }
                retrySendMessage(messageObject, true);
            }
            if (scheduledMessages != null) {
                for (int a = 0; a < scheduledMessages.size(); a++) {
                    MessageObject messageObject = new MessageObject(currentAccount, scheduledMessages.get(a), false, true);
                    messageObject.scheduled = true;
                    retrySendMessage(messageObject, true);
                }
            }
        });
    }

    public ImportingStickers getImportingStickers(String shortName) {
        return importingStickersMap.get(shortName);
    }

    public ImportingHistory getImportingHistory(long dialogId) {
        return importingHistoryMap.get(dialogId);
    }

    public boolean isImportingStickers() {
        return importingStickersMap.size() != 0;
    }

    public boolean isImportingHistory() {
        return importingHistoryMap.size() != 0;
    }

    public void prepareImportHistory(long dialogId, Uri uri, ArrayList<Uri> mediaUris, MessagesStorage.LongCallback onStartImport) {
        if (importingHistoryMap.get(dialogId) != null) {
            onStartImport.run(0);
            return;
        }
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null && !chat.megagroup) {
                getMessagesController().convertToMegaGroup(null, -dialogId, null, (chatId) -> {
                    if (chatId != 0) {
                        prepareImportHistory(-chatId, uri, mediaUris, onStartImport);
                    } else {
                        onStartImport.run(0);
                    }
                });
                return;
            }
        }
        new Thread(() -> {
            ArrayList<Uri> uris = mediaUris != null ? mediaUris : new ArrayList<>();
            ImportingHistory importingHistory = new ImportingHistory();
            importingHistory.mediaPaths = uris;
            importingHistory.dialogId = dialogId;
            importingHistory.peer = getMessagesController().getInputPeer(dialogId);
            HashMap<String, ImportingHistory> files = new HashMap<>();
            for (int a = 0, N = uris.size(); a < N + 1; a++) {
                Uri mediaUri;
                if (a == 0) {
                    mediaUri = uri;
                } else {
                    mediaUri = uris.get(a - 1);
                }
                if (mediaUri == null || AndroidUtilities.isInternalUri(mediaUri)) {
                    if (a == 0) {
                        AndroidUtilities.runOnUIThread(() -> {
                            onStartImport.run(0);
                        });
                        return;
                    }
                    continue;
                }

                String ext = "txt";
                String filename = FileLoader.fixFileName(MediaController.getFileName(uri));
                if (filename != null && filename.endsWith(".zip")) {
                    ext = "zip";
                }

                String path = MediaController.copyFileToCache(mediaUri, ext);
                if ("zip".equals(ext)) {
                    File zipfile = new File(path);
                    try {
                        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipfile))) {
                            ZipEntry zipEntry = zis.getNextEntry();
                            while (zipEntry != null) {
                                if (zipEntry.getName().endsWith(".txt")) {
                                    File newFile = MediaController.createFileInCache(zipEntry.getName(), "txt");
                                    path = newFile.getAbsolutePath();
                                    FileOutputStream fos = new FileOutputStream(newFile);
                                    byte[] buffer = new byte[1024];
                                    int len;
                                    while ((len = zis.read(buffer)) > 0) {
                                        fos.write(buffer, 0, len);
                                    }
                                    fos.close();
                                    break;
                                }
                                zipEntry = zis.getNextEntry();
                            }
                            zis.closeEntry();
                        } catch (IOException e) {
                            FileLog.e(e);
                        }
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                    try {
                        zipfile.delete();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (path == null) {
                    continue;
                }
                final File f = new File(path);
                long size;
                if (!f.exists() || (size = f.length()) == 0) {
                    if (a == 0) {
                        AndroidUtilities.runOnUIThread(() -> {
                            onStartImport.run(0);
                        });
                        return;
                    }
                    continue;
                }
                importingHistory.totalSize += size;
                if (a == 0) {
                    if (size > 32 * 1024 * 1024) {
                        f.delete();
                        AndroidUtilities.runOnUIThread(() -> {
                            Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString(R.string.ImportFileTooLarge), Toast.LENGTH_SHORT).show();
                            onStartImport.run(0);
                        });
                        return;
                    }
                    importingHistory.historyPath = path;
                } else {
                    importingHistory.uploadMedia.add(path);
                }
                importingHistory.uploadSet.add(path);
                files.put(path, importingHistory);
            }
            AndroidUtilities.runOnUIThread(() -> {
                importingHistoryFiles.putAll(files);
                importingHistoryMap.put(dialogId, importingHistory);
                getFileLoader().uploadFile(importingHistory.historyPath, false, true, 0, ConnectionsManager.FileTypeFile, true);
                getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId);
                onStartImport.run(dialogId);

                Intent intent = new Intent(ApplicationLoader.applicationContext, ImportingService.class);
                try {
                    ApplicationLoader.applicationContext.startService(intent);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        }).start();
    }

    public void prepareImportStickers(String title, String shortName, String sofrware, ArrayList<ImportingSticker> paths, MessagesStorage.StringCallback onStartImport) {
        if (importingStickersMap.get(shortName) != null) {
            onStartImport.run(null);
            return;
        }
        new Thread(() -> {
            ImportingStickers importingStickers = new ImportingStickers();
            importingStickers.title = title;
            importingStickers.shortName = shortName;
            importingStickers.software = sofrware;
            HashMap<String, ImportingStickers> files = new HashMap<>();
            for (int a = 0, N = paths.size(); a < N; a++) {
                ImportingSticker sticker = paths.get(a);
                final File f = new File(sticker.path);
                long size;
                if (!f.exists() || (size = f.length()) == 0) {
                    if (a == 0) {
                        AndroidUtilities.runOnUIThread(() -> {
                            onStartImport.run(null);
                        });
                        return;
                    }
                    continue;
                }
                importingStickers.totalSize += size;
                importingStickers.uploadMedia.add(sticker);
                importingStickers.uploadSet.put(sticker.path, sticker);
                files.put(sticker.path, importingStickers);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (importingStickers.uploadMedia.get(0).item != null) {
                    importingStickers.startImport();
                } else {
                    importingStickersFiles.putAll(files);
                    importingStickersMap.put(shortName, importingStickers);
                    importingStickers.initImport();
                    getNotificationCenter().postNotificationName(NotificationCenter.historyImportProgressChanged, shortName);
                    onStartImport.run(shortName);
                }

                Intent intent = new Intent(ApplicationLoader.applicationContext, ImportingService.class);
                try {
                    ApplicationLoader.applicationContext.startService(intent);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        }).start();
    }

    public TLRPC.TL_photo generatePhotoSizes(String path, Uri imageUri) {
        return generatePhotoSizes(null, path, imageUri);
    }

    public TLRPC.TL_photo generatePhotoSizes(TLRPC.TL_photo photo, String path, Uri imageUri) {
        Bitmap bitmap = ImageLoader.loadBitmap(path, imageUri, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), true);
        if (bitmap == null) {
            bitmap = ImageLoader.loadBitmap(path, imageUri, 800, 800, true);
        }
        ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<>();
        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
        if (size != null) {
            sizes.add(size);
        }
        size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), true, 80, false, 101, 101);
        if (size != null) {
            sizes.add(size);
        }
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (sizes.isEmpty()) {
            return null;
        } else {
            getUserConfig().saveConfig(false);
            if (photo == null) {
                photo = new TLRPC.TL_photo();
            }
            photo.date = getConnectionsManager().getCurrentTime();
            photo.sizes = sizes;
            photo.file_reference = new byte[0];
            return photo;
        }
    }

    private final static int ERROR_TYPE_UNSUPPORTED = 1;
    private final static int ERROR_TYPE_FILE_TOO_LARGE = 2;

    private static int prepareSendingDocumentInternal(AccountInstance accountInstance, String path, String originalPath, Uri uri, String mime, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, final ArrayList<TLRPC.MessageEntity> entities, final MessageObject editingMessageObject, long[] groupId, boolean isGroupFinal, CharSequence caption, boolean notify, int scheduleDate, Integer[] docType, boolean forceDocument, String quickReplyShortcut, int quickReplyShortcutId, long effectId, boolean invertMedia) {
        if ((path == null || path.length() == 0) && uri == null) {
            return ERROR_TYPE_UNSUPPORTED;
        }
        if (uri != null && AndroidUtilities.isInternalUri(uri)) {
            return ERROR_TYPE_UNSUPPORTED;
        }
        if (path != null && AndroidUtilities.isInternalUri(Uri.fromFile(new File(path)))) {
            return ERROR_TYPE_UNSUPPORTED;
        }
        MimeTypeMap myMime = MimeTypeMap.getSingleton();
        TLRPC.TL_documentAttributeAudio attributeAudio = null;
        String extension = null;
        if (uri != null && path == null) {
            if (checkFileSize(accountInstance, uri)) {
                return ERROR_TYPE_FILE_TOO_LARGE;
            }
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
                return ERROR_TYPE_UNSUPPORTED;
            }
            if (!hasExt) {
                extension = null;
            }
        }
        final File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            return ERROR_TYPE_UNSUPPORTED;
        }

        if (!FileLoader.checkUploadFileSize(accountInstance.getCurrentAccount(), f.length())) {
            return ERROR_TYPE_FILE_TOO_LARGE;
        }

        boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);

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
        Bitmap cover = null;
        if (extL.equals("mp3") || extL.equals("m4a")) {
            AudioInfo audioInfo = AudioInfo.getAudioInfo(f);
            if (audioInfo != null) {
                long d = audioInfo.getDuration();
                if (d != 0) {
                    permormer = audioInfo.getArtist();
                    title = audioInfo.getTitle();
                    duration = (int) (d / 1000);
                }
                cover = audioInfo.getCover();
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
                byte[] art = mediaMetadataRetriever.getEmbeddedPicture();
                if (art != null) {
                    cover = BitmapFactory.decodeByteArray(art, 0, art.length);
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
        String parentObject = null;
        if (!sendNew && !isEncrypted) {
            Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 1 : 4);
            if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                document = (TLRPC.TL_document) sentData[0];
                parentObject = (String) sentData[1];
            }
            if (document == null && !path.equals(originalPath) && !isEncrypted) {
                sentData = accountInstance.getMessagesStorage().getSentFile(path + f.length(), !isEncrypted ? 1 : 4);
                if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                    document = (TLRPC.TL_document) sentData[0];
                    parentObject = (String) sentData[1];
                }
            }
            ensureMediaThumbExists(accountInstance, isEncrypted, document, path, null, 0);
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.date = accountInstance.getConnectionsManager().getCurrentTime();
            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
            fileName.file_name = name;
            document.file_reference = new byte[0];
            document.attributes.add(fileName);
            document.size = f.length();
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
                    case "mp3":
                        document.mime_type = "audio/mpeg";
                        break;
                    case "m4a":
                        document.mime_type = "audio/m4a";
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
            if (!forceDocument && document.mime_type.equals("image/gif") && (editingMessageObject == null || editingMessageObject.getGroupIdForUse() == 0)) {
                try {
                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90, true);
                    if (bitmap != null) {
                        fileName.file_name = "animation.gif";
                        document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                        TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                        if (thumb != null) {
                            document.thumbs.add(thumb);
                            document.flags |= 1;
                        }
                        bitmap.recycle();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (cover != null) {
                TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(cover, 132, 132, 55, isEncrypted);
                if (thumb != null) {
                    document.thumbs.add(thumb);
                    document.flags |= 1;
                }
                cover.recycle();
            }
            if (document.mime_type.equals("image/webp") && editingMessageObject == null) {
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                try {
                    bmOptions.inJustDecodeBounds = true;
//                    RandomAccessFile file = new RandomAccessFile(path, "r");
//                    ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
//                    Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                    BitmapFactory.decodeFile(path, bmOptions);
//                    file.close();
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

                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 400, 400, true);
                    TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(null, bitmap, Bitmap.CompressFormat.PNG, false, 400, 400, 100, isEncrypted, 0, 0, false);
                    if (thumb != null) {
                        document.thumbs.add(thumb);
                        document.flags |= 1;
                    }
                }
            }
        }
        final String captionFinal = caption != null ? caption.toString() : "";

        final TLRPC.TL_document documentFinal = document;
        final String pathFinal = path;
        final String parentFinal = parentObject;
        final HashMap<String, String> params = new HashMap<>();
        if (originalPath != null) {
            params.put("originalPath", originalPath);
        }
        if (forceDocument && attributeAudio == null) {
            params.put("forceDocument", "1");
        }
        if (parentFinal != null) {
            params.put("parentObject", parentFinal);
        }
        Integer prevType = 0;
        boolean isSticker = false;
        if (docType != null) {
            prevType = docType[0];
            if (document.mime_type != null && document.mime_type.toLowerCase().startsWith("image/webp")) {
                docType[0] = -1;
                isSticker = true;
            } else if (document.mime_type != null && (document.mime_type.toLowerCase().startsWith("image/") || document.mime_type.toLowerCase().startsWith("video/mp4")) || MessageObject.canPreviewDocument(document)) {
                docType[0] = 1;
            } else if (attributeAudio != null) {
                docType[0] = 2;
            } else {
                docType[0] = 0;
            }
        }
        if (!isEncrypted && groupId != null) {
            if (docType != null && prevType != null && prevType != docType[0]) {
                finishGroup(accountInstance, groupId[0], scheduleDate);
                groupId[0] = Utilities.random.nextLong();
            }
            if (!isSticker) {
                params.put("groupId", "" + groupId[0]);
                if (isGroupFinal) {
                    params.put("final", "1");
                }
            }
        }

        AndroidUtilities.runOnUIThread(() -> {
            if (editingMessageObject != null) {
                accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, null, null, documentFinal, pathFinal, params, false, false, parentFinal);
            } else {
                SendMessageParams sendMessageParams = SendMessagesHelper.SendMessageParams.of(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false);
                sendMessageParams.replyToStoryItem = storyItem;
                sendMessageParams.replyQuote = quote;
                sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                sendMessageParams.effect_id = effectId;
                sendMessageParams.invert_media = invertMedia;
                accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
            }
        });
        return 0;
    }

    private static boolean checkFileSize(AccountInstance accountInstance, Uri uri) {
        long len = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {

                AssetFileDescriptor assetFileDescriptor = ApplicationLoader.applicationContext.getContentResolver().openAssetFileDescriptor(uri, "r", null);
                if (assetFileDescriptor != null ) {
                    len = assetFileDescriptor.getLength();
                }
                Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                cursor.moveToFirst();
                len = cursor.getLong(sizeIndex);
                cursor.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (!FileLoader.checkUploadFileSize(accountInstance.getCurrentAccount(), len)) {
            return true;
        }
        return false;
    }

    @UiThread
    public static void prepareSendingDocument(AccountInstance accountInstance, String path, String originalPath, Uri uri, String caption, String mine, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, MessageObject editingMessageObject, boolean notify, int scheduleDate, InputContentInfoCompat inputContent, String quickReplyShortcut, int quickReplyShortcutId, boolean invertMedia) {
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
        prepareSendingDocuments(accountInstance, paths, originalPaths, uris, caption, mine, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, editingMessageObject, notify, scheduleDate, inputContent, quickReplyShortcut, quickReplyShortcutId, 0, invertMedia);
    }

    @UiThread
    public static void prepareSendingAudioDocuments(AccountInstance accountInstance, ArrayList<MessageObject> messageObjects, CharSequence caption, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, boolean notify, int scheduleDate, MessageObject editingMessageObject, String quickReplyShortcut, int quickReplyShortcutId, long effectId, boolean invertMedia) {
        new Thread(() -> {
            int count = messageObjects.size();
            long groupId = 0;
            int mediaCount = 0;
            for (int a = 0; a < count; a++) {
                final MessageObject messageObject = messageObjects.get(a);
                String originalPath = messageObject.messageOwner.attachPath;
                final File f = new File(originalPath);

                boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
                if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
                    groupId = Utilities.random.nextLong();
                    mediaCount = 0;
                }

                if (originalPath != null) {
                    originalPath += "audio" + f.length();
                }

                TLRPC.TL_document document = null;
                String parentObject = null;
                if (!isEncrypted) {
                    Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 1 : 4);
                    if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                        document = (TLRPC.TL_document) sentData[0];
                        parentObject = (String) sentData[1];
                        ensureMediaThumbExists(accountInstance, isEncrypted, document, originalPath, null, 0);
                    }
                }
                if (document == null) {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                }

                if (isEncrypted) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                    TLRPC.EncryptedChat encryptedChat = accountInstance.getMessagesController().getEncryptedChat(encryptedChatId);
                    if (encryptedChat == null) {
                        return;
                    }
                }

                final TLRPC.TL_document documentFinal = document;
                final String parentFinal = parentObject;
                final CharSequence[] text = new CharSequence[] { caption };
                final ArrayList<TLRPC.MessageEntity> entities = a == 0 ? accountInstance.getMediaDataController().getEntities(text, true) : null;
                final String captionFinal = a == 0 ? text[0].toString() : null;
                final HashMap<String, String> params = new HashMap<>();
                if (originalPath != null) {
                    params.put("originalPath", originalPath);
                }
                if (parentFinal != null) {
                    params.put("parentObject", parentFinal);
                }
                mediaCount++;
                params.put("groupId", "" + groupId);
                if (mediaCount == 10 || a == count - 1) {
                    params.put("final", "1");
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (editingMessageObject != null) {
                        accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, null, null, documentFinal, messageObject.messageOwner.attachPath, params, false, false, parentFinal);
                    } else {
                        SendMessageParams sendMessageParams = SendMessageParams.of(documentFinal, null, messageObject.messageOwner.attachPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false, false);
                        sendMessageParams.replyToStoryItem = storyItem;
                        sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                        sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                        sendMessageParams.effect_id = effectId;
                        sendMessageParams.invert_media = invertMedia;
                        accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                    }
                });
            }
        }).start();
    }

    private static void finishGroup(AccountInstance accountInstance, long groupId, int scheduleDate) {
        AndroidUtilities.runOnUIThread(() -> {
            SendMessagesHelper instance = accountInstance.getSendMessagesHelper();
            ArrayList<DelayedMessage> arrayList = instance.delayedMessages.get("group_" + groupId);
            if (arrayList != null && !arrayList.isEmpty()) {

                DelayedMessage message = arrayList.get(0);

                MessageObject prevMessage = message.messageObjects.get(message.messageObjects.size() - 1);
                message.finalGroupMessage = prevMessage.getId();
                prevMessage.messageOwner.params.put("final", "1");

                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                messagesRes.messages.add(prevMessage.messageOwner);
                if (!message.paidMedia) {
                    accountInstance.getMessagesStorage().putMessages(messagesRes, message.peer, -2, 0, false, scheduleDate != 0 ? 1 : 0, 0);
                }
                instance.sendReadyToSendGroup(message, true, true);
            }
        });
    }

    @UiThread
    public static void prepareSendingDocuments(AccountInstance accountInstance, ArrayList<String> paths, ArrayList<String> originalPaths, ArrayList<Uri> uris, String caption, String mime, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, MessageObject editingMessageObject, boolean notify, int scheduleDate, InputContentInfoCompat inputContent, String quickReplyShortcut, int quickReplyShortcutId, long effectId, boolean invertMedia) {
        if (paths == null && originalPaths == null && uris == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            int error = 0;
            long[] groupId = new long[1];
            int mediaCount = 0;
            Integer[] docType = new Integer[1];

            boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
            boolean first = true;
            if (paths != null) {
                int count = paths.size();
                for (int a = 0; a < count; a++) {
                    final String captionFinal = a == 0 ? caption : null;
                    if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
                        if (groupId[0] != 0) {
                            finishGroup(accountInstance, groupId[0], scheduleDate);
                        }
                        groupId[0] = Utilities.random.nextLong();
                        mediaCount = 0;
                    }
                    mediaCount++;
                    long prevGroupId = groupId[0];
                    error = prepareSendingDocumentInternal(accountInstance, paths.get(a), originalPaths.get(a), null, mime, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, captionFinal, notify, scheduleDate, docType, inputContent == null, quickReplyShortcut, quickReplyShortcutId, first ? effectId : 0, invertMedia);
                    first = false;
                    if (prevGroupId != groupId[0] || groupId[0] == -1) {
                        mediaCount = 1;
                    }
                }
            }
            if (uris != null) {
                groupId[0] = 0;
                mediaCount = 0;
                int count = uris.size();
                for (int a = 0; a < uris.size(); a++) {
                    final String captionFinal = a == 0 && (paths == null || paths.size() == 0) ? caption : null;
                    if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
                        if (groupId[0] != 0) {
                            finishGroup(accountInstance, groupId[0], scheduleDate);
                        }
                        groupId[0] = Utilities.random.nextLong();
                        mediaCount = 0;
                    }
                    mediaCount++;
                    long prevGroupId = groupId[0];
                    error = prepareSendingDocumentInternal(accountInstance, null, null, uris.get(a), mime, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, captionFinal, notify, scheduleDate, docType, inputContent == null, quickReplyShortcut, quickReplyShortcutId, first ? effectId : 0, invertMedia);
                    first = false;
                    if (prevGroupId != groupId[0] || groupId[0] == -1) {
                        mediaCount = 1;
                    }
                }
            }
            if (inputContent != null) {
                inputContent.releasePermission();
            }
            handleError(error, accountInstance);
        });
    }

    private static void handleError(int error, AccountInstance accountInstance) {
        if (error != 0) {
            int finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (finalError == ERROR_TYPE_UNSUPPORTED) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.UnsupportedAttachment));
                    } else if (finalError == ERROR_TYPE_FILE_TOO_LARGE) {
                        NotificationCenter.getInstance(accountInstance.getCurrentAccount()).postNotificationName(NotificationCenter.currentUserShowLimitReachedDialog, LimitReachedBottomSheet.TYPE_LARGE_FILE);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
    }

    @UiThread
    public static void prepareSendingPhoto(AccountInstance accountInstance, String imageFilePath, Uri imageUri, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, ChatActivity.ReplyQuote quote, CharSequence caption, ArrayList<TLRPC.MessageEntity> entities, ArrayList<TLRPC.InputDocument> stickers, InputContentInfoCompat inputContent, int ttl, MessageObject editingMessageObject, boolean notify, int scheduleDate, int mode, String quickReplyShortcut, int quickReplyShortcutId) {
        prepareSendingPhoto(accountInstance, imageFilePath, null, imageUri, dialogId, replyToMsg, replyToTopMsg, null, null, entities, stickers, inputContent, ttl, editingMessageObject, null, notify, scheduleDate, mode, false, caption, quickReplyShortcut, quickReplyShortcutId, 0);
    }

    @UiThread
    public static void prepareSendingPhoto(AccountInstance accountInstance, String imageFilePath, String thumbFilePath, Uri imageUri, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, ArrayList<TLRPC.MessageEntity> entities, ArrayList<TLRPC.InputDocument> stickers, InputContentInfoCompat inputContent, int ttl, MessageObject editingMessageObject, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int mode, boolean forceDocument, CharSequence caption, String quickReplyShortcut, int quickReplyShortcutId, long effectId) {
        SendingMediaInfo info = new SendingMediaInfo();
        info.path = imageFilePath;
        info.thumbPath = thumbFilePath;
        info.uri = imageUri;
        if (caption != null) {
            info.caption = caption.toString();
        }
        info.entities = entities;
        info.ttl = ttl;
        if (stickers != null) {
            info.masks = new ArrayList<>(stickers);
        }
        info.videoEditedInfo = videoEditedInfo;
        ArrayList<SendingMediaInfo> infos = new ArrayList<>();
        infos.add(info);
        prepareSendingMedia(accountInstance, infos, dialogId, replyToMsg, replyToTopMsg, null, quote, forceDocument, false, editingMessageObject, notify, scheduleDate, mode, false, inputContent, quickReplyShortcut, quickReplyShortcutId, effectId, false);
    }

    @UiThread
    public static void prepareSendingBotContextResult(BaseFragment fragment, AccountInstance accountInstance, TLRPC.BotInlineResult result, HashMap<String, String> params, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, boolean notify, int scheduleDate, String quick_reply_shortcut, int quick_reply_shortcut_id) {
        if (result == null) {
            return;
        }
        if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
            new Thread(() -> {
                boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
                String finalPath = null;
                TLRPC.TL_document document = null;
                TLRPC.TL_photo photo = null;
                TLRPC.TL_game game = null;
                if ("game".equals(result.type)) {
                    if (isEncrypted) {
                        return; //doesn't work in secret chats for now
                    }
                    game = new TLRPC.TL_game();
                    game.title = result.title;
                    game.description = result.description;
                    game.short_name = result.id;
                    game.photo = result.photo;
                    if (game.photo == null) {
                        game.photo = new TLRPC.TL_photoEmpty();
                    }
                    if (result.document instanceof TLRPC.TL_document) {
                        game.document = result.document;
                        game.flags |= 1;
                    }
                } else if (result instanceof TLRPC.TL_botInlineMediaResult) {
                    if (result.document != null) {
                        if (result.document instanceof TLRPC.TL_document) {
                            document = (TLRPC.TL_document) result.document;
                        }
                    } else if (result.photo != null) {
                        if (result.photo instanceof TLRPC.TL_photo) {
                            photo = (TLRPC.TL_photo) result.photo;
                        }
                    }
                } else if (result.content != null) {
                    String ext = ImageLoader.getHttpUrlExtension(result.content.url, null);
                    if (TextUtils.isEmpty(ext)) {
                        ext = FileLoader.getExtensionByMimeType(result.content.mime_type);
                    } else {
                        ext = "." + ext;
                    }
                    File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content.url) + ext);
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
                            document.file_reference = new byte[0];
                            document.date = accountInstance.getConnectionsManager().getCurrentTime();
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
                                        int side = isEncrypted ? 90 : 320;
                                        Bitmap bitmap;
                                        if (finalPath.endsWith("mp4")) {
                                            bitmap = createVideoThumbnail(finalPath, MediaStore.Video.Thumbnails.MINI_KIND);
                                            if (bitmap == null && result.thumb instanceof TLRPC.TL_webDocument && "video/mp4".equals(result.thumb.mime_type)) {
                                                ext = ImageLoader.getHttpUrlExtension(result.thumb.url, null);
                                                if (TextUtils.isEmpty(ext)) {
                                                    ext = FileLoader.getExtensionByMimeType(result.thumb.mime_type);
                                                } else {
                                                    ext = "." + ext;
                                                }
                                                f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + ext);
                                                bitmap = createVideoThumbnail(f.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
                                            }
                                        } else {
                                            bitmap = ImageLoader.loadBitmap(finalPath, null, side, side, true);
                                        }
                                        if (bitmap != null) {
                                            TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, side, side, side > 90 ? 80 : 55, false);
                                            if (thumb != null) {
                                                document.thumbs.add(thumb);
                                                document.flags |= 1;
                                            }
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
                                                TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                if (thumb != null) {
                                                    document.thumbs.add(thumb);
                                                    document.flags |= 1;
                                                }
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
                                                TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, false);
                                                if (thumb != null) {
                                                    document.thumbs.add(thumb);
                                                    document.flags |= 1;
                                                }
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
                            if (document.thumbs.isEmpty()) {
                                TLRPC.PhotoSize thumb = new TLRPC.TL_photoSize();
                                int wh[] = MessageObject.getInlineResultWidthAndHeight(result);
                                thumb.w = wh[0];
                                thumb.h = wh[1];
                                thumb.size = 0;
                                thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                thumb.type = "x";

                                document.thumbs.add(thumb);
                                document.flags |= 1;
                            }
                            break;
                        }
                        case "photo": {
                            if (f.exists()) {
                                photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(finalPath, null);
                            }
                            if (photo == null) {
                                photo = new TLRPC.TL_photo();
                                photo.date = accountInstance.getConnectionsManager().getCurrentTime();
                                photo.file_reference = new byte[0];
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
                final String finalPathFinal = finalPath;
                final TLRPC.TL_document finalDocument = document;
                final TLRPC.TL_photo finalPhoto = photo;
                final TLRPC.TL_game finalGame = game;
                if (params != null && result.content != null) {
                    params.put("originalPath", result.content.url);
                }
                final Bitmap[] precahcedThumb = new Bitmap[1];
                final String[] precachedKey = new String[1];

                if (isEncrypted && document != null) {
                    for (int i = 0; i < document.attributes.size(); ++i) {
                        if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeVideo) {
                            TLRPC.TL_documentAttributeVideo attr = (TLRPC.TL_documentAttributeVideo) document.attributes.get(i);
                            TLRPC.TL_documentAttributeVideo_layer159 attr2 = new TLRPC.TL_documentAttributeVideo_layer159();
                            attr2.flags = attr.flags;
                            attr2.round_message = attr.round_message;
                            attr2.supports_streaming = attr.supports_streaming;
                            attr2.duration = attr.duration;
                            attr2.w = attr.w;
                            attr2.h = attr.h;
                            document.attributes.set(i, attr2);
                        }
                    }
                }

                if (MessageObject.isGifDocument(document)) {
                    TLRPC.PhotoSize photoSizeThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                    File gifFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(document);
                    if (!gifFile.exists()) {
                        gifFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(document, true);
                    }
                    ensureMediaThumbExists(accountInstance, isEncrypted, document, gifFile.getAbsolutePath(), null, 0);
                    precachedKey[0] = getKeyForPhotoSize(accountInstance, photoSizeThumb, precahcedThumb, true, true);
                }
                TLRPC.InputPeer sendToPeer = !DialogObject.isEncryptedDialog(dialogId) ? accountInstance.getMessagesController().getInputPeer(dialogId) : null;
                if (sendToPeer != null && sendToPeer.user_id != 0 && accountInstance.getMessagesController().getUserFull(sendToPeer.user_id) != null &&
                        accountInstance.getMessagesController().getUserFull(sendToPeer.user_id).voice_messages_forbidden && document != null) {

                    if (MessageObject.isVoiceDocument(finalDocument)) {
                        AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSendMediaAlert(7, fragment, null));
                    } else if (MessageObject.isRoundVideoDocument(finalDocument)) {
                        AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSendMediaAlert(8, fragment, null));
                    }
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    SendMessageParams params2 = null;
                    if (finalDocument != null) {
                        if (precahcedThumb[0] != null && precachedKey[0] != null) {
                            ImageLoader.getInstance().putImageToCache(new BitmapDrawable(precahcedThumb[0]), precachedKey[0], false);
                        }
                        params2 = SendMessageParams.of(finalDocument, null, finalPathFinal, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, 0, result, null, false);
                    } else if (finalPhoto != null) {
                        params2 = SendMessageParams.of(finalPhoto, result.content != null ? result.content.url : null, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, 0, result, false);
                    } else if (finalGame != null) {
                        params2 = SendMessageParams.of(finalGame, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
                    }
                    if (params2 != null) {
                        params2.quick_reply_shortcut = quick_reply_shortcut;
                        params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
                        params2.replyToStoryItem = storyItem;
                        params2.replyQuote = quote;
                        accountInstance.getSendMessagesHelper().sendMessage(params2);
                    }
                });
            }).run();
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageText) {
            TLRPC.WebPage webPage = null;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                for (int a = 0; a < result.send_message.entities.size(); a++) {
                    TLRPC.MessageEntity entity = result.send_message.entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        webPage = new TLRPC.TL_webPagePending();
                        webPage.url = result.send_message.message.substring(entity.offset, entity.offset + entity.length);
                        break;
                    }
                }
            }
            SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(result.send_message.message, dialogId, replyToMsg, replyToTopMsg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, null, false);
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
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
            SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(venue, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
            SendMessagesHelper.SendMessageParams params2;
            if (result.send_message.period != 0 || result.send_message.proximity_notification_radius != 0) {
                TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                location.period = result.send_message.period != 0 ? result.send_message.period : 900;
                location.geo = result.send_message.geo;
                location.heading = result.send_message.heading;
                location.proximity_notification_radius = result.send_message.proximity_notification_radius;
                params2 = SendMessagesHelper.SendMessageParams.of(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
            } else {
                TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                location.geo = result.send_message.geo;
                location.heading = result.send_message.heading;
                params2 = SendMessagesHelper.SendMessageParams.of(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
            }
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaContact) {
            TLRPC.User user = new TLRPC.TL_user();
            user.phone = result.send_message.phone_number;
            user.first_name = result.send_message.first_name;
            user.last_name = result.send_message.last_name;
            TLRPC.RestrictionReason reason = new TLRPC.RestrictionReason();
            reason.text = result.send_message.vcard;
            reason.platform = "";
            reason.reason = "";
            user.restriction_reason.add(reason);
            SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(user, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaInvoice) {
            if (DialogObject.isEncryptedDialog(dialogId)) {
                return; //doesn't work in secret chats for now
            }
            TLRPC.TL_botInlineMessageMediaInvoice invoice = (TLRPC.TL_botInlineMessageMediaInvoice) result.send_message;
            TLRPC.TL_messageMediaInvoice messageMediaInvoice = new TLRPC.TL_messageMediaInvoice();
            messageMediaInvoice.shipping_address_requested = invoice.shipping_address_requested;
            messageMediaInvoice.test = invoice.test;
            messageMediaInvoice.title = invoice.title;
            messageMediaInvoice.description = invoice.description;
            if (invoice.photo != null) {
                messageMediaInvoice.webPhoto = invoice.photo;
                messageMediaInvoice.flags |= 1;
            }
            messageMediaInvoice.currency = invoice.currency;
            messageMediaInvoice.total_amount = invoice.total_amount;
            messageMediaInvoice.start_param = "";
            SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(messageMediaInvoice, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate);
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
        } else if (result.send_message instanceof TLRPC.TL_botInlineMessageMediaWebPage) {
            TLRPC.TL_botInlineMessageMediaWebPage request = (TLRPC.TL_botInlineMessageMediaWebPage) result.send_message;
            TLRPC.WebPage webPage = new TLRPC.TL_webPagePending();
            webPage.url = request.url;
            SendMessagesHelper.SendMessageParams params2 = SendMessagesHelper.SendMessageParams.of(result.send_message.message, dialogId, replyToMsg, replyToTopMsg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, null, false);
            params2.quick_reply_shortcut = quick_reply_shortcut;
            params2.quick_reply_shortcut_id = quick_reply_shortcut_id;
            accountInstance.getSendMessagesHelper().sendMessage(params2);
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
    public static void prepareSendingText(AccountInstance accountInstance, String text, long dialogId, boolean notify, int scheduleDate, long effectId) {
        prepareSendingText(accountInstance, text, dialogId, 0, notify, scheduleDate, effectId);
    }

    @UiThread
    public static void prepareSendingText(AccountInstance accountInstance, String text, long dialogId, long topicId, boolean notify, int scheduleDate, long effectId) {
        accountInstance.getMessagesStorage().getStorageQueue().postRunnable(() -> Utilities.stageQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            String textFinal = getTrimmedString(text);
            if (textFinal.length() != 0) {
                int count = (int) Math.ceil(textFinal.length() / 4096.0f);
                MessageObject replyToMsg = null;
                if (topicId != 0) {
                    TLRPC.TL_forumTopic topic = accountInstance.getMessagesController().getTopicsController().findTopic(-dialogId, topicId);
                    if (topic != null && topic.topicStartMessage != null) {
                        replyToMsg = new MessageObject(accountInstance.getCurrentAccount(), topic.topicStartMessage, false, false);
                        replyToMsg.isTopicMainMessage = true;
                    }
                }
                for (int a = 0; a < count; a++) {
                    String mess = textFinal.substring(a * 4096, Math.min((a + 1) * 4096, textFinal.length()));
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(mess, dialogId, replyToMsg, replyToMsg, null, true, null, null, null, notify, scheduleDate, null, false);
                    if (a == 0) {
                        params.effect_id = effectId;
                    }
                    accountInstance.getSendMessagesHelper().sendMessage(params);
                }
            }
        })));
    }

    public static void ensureMediaThumbExists(AccountInstance accountInstance, boolean isEncrypted, TLObject object, String path, Uri uri, long startTime) {
        if (object instanceof TLRPC.TL_photo) {
            TLRPC.TL_photo photo = (TLRPC.TL_photo) object;
            boolean smallExists;
            TLRPC.PhotoSize smallSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90);
            if (smallSize instanceof TLRPC.TL_photoStrippedSize || smallSize instanceof TLRPC.TL_photoPathSize) {
                smallExists = true;
            } else {
                File smallFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(smallSize, true);
                smallExists = smallFile.exists();
            }
            TLRPC.PhotoSize bigSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            File bigFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(bigSize, false);
            boolean bigExists = bigFile.exists();
            if (!smallExists || !bigExists) {
                Bitmap bitmap = ImageLoader.loadBitmap(path, uri, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), true);
                if (bitmap == null) {
                    bitmap = ImageLoader.loadBitmap(path, uri, 800, 800, true);
                }
                if (!bigExists) {
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bigSize, bitmap, Bitmap.CompressFormat.JPEG, true, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), 80, false, 101, 101,false);
                    if (size != bigSize) {
                        photo.sizes.add(0, size);
                    }
                }
                if (!smallExists) {
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(smallSize, bitmap, 90, 90, 55, true, false);
                    if (size != smallSize) {
                        photo.sizes.add(0, size);
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        } else if (object instanceof TLRPC.TL_document) {
            TLRPC.TL_document document = (TLRPC.TL_document) object;
            if ((MessageObject.isVideoDocument(document) || MessageObject.isNewGifDocument(document)) && MessageObject.isDocumentHasThumb(document)) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
                    return;
                }
                File smallFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(photoSize, true);
                if (!smallFile.exists()) {
                    Bitmap thumb = createVideoThumbnailAtTime(path, startTime);
                    if (thumb == null) {
                        thumb = SendMessagesHelper.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                    }
                    int side = isEncrypted ? 90 : 320;
                    document.thumbs.set(0, ImageLoader.scaleAndSaveImage(photoSize, thumb, side, side, side > 90 ? 80 : 55, false, true));
                }
            }
        }
    }

    public static String getKeyForPhotoSize(AccountInstance accountInstance, TLRPC.PhotoSize photoSize, Bitmap[] bitmap, boolean blur, boolean forceCache) {
        if (photoSize == null || photoSize.location == null) {
            return null;
        }
        Point point = ChatMessageCell.getMessageSize(photoSize.w, photoSize.h);

        if (bitmap != null) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                File file = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(photoSize, forceCache);

                FileInputStream is = new FileInputStream(file);
                BitmapFactory.decodeStream(is, null, opts);
                is.close();

                float photoW = opts.outWidth;
                float photoH = opts.outHeight;
                float scaleFactor = Math.max(photoW / point.x, photoH / point.y);
                if (scaleFactor < 1) {
                    scaleFactor = 1;
                }
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = (int) scaleFactor;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                if (Build.VERSION.SDK_INT >= 21) {
                    is = new FileInputStream(file);
                    bitmap[0] = BitmapFactory.decodeStream(is, null, opts);
                    is.close();
                } else {
                    /*opts.inPurgeable = true;
                    RandomAccessFile f = new RandomAccessFile(file, "r");
                    int len = (int) f.length();
                    int offset = 0;
                    byte[] data = bytes != null && bytes.length >= len ? bytes : null;
                    if (data == null) {
                        bytes = data = new byte[len];
                    }
                    f.readFully(data, 0, len);
                    f.close();
                    bitmapFinal[0] = BitmapFactory.decodeByteArray(data, offset, len, opts);*/
                }
            } catch (Throwable ignore) {

            }
        }
        return String.format(Locale.US, blur ? "%d_%d@%d_%d_b" : "%d_%d@%d_%d", photoSize.location.volume_id, photoSize.location.local_id, (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density));
    }

    public static boolean shouldSendWebPAsSticker(String path, Uri uri) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        try {
            if (path != null) {
//                RandomAccessFile file = new RandomAccessFile(path, "r");
//                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
//                Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                BitmapFactory.decodeFile(path, bmOptions);
//                file.close();
            } else {
                try (InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(inputStream, null, bmOptions);
                } catch (Exception e) {

                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return bmOptions.outWidth < 800 && bmOptions.outHeight < 800;
    }

    @UiThread
    public static void prepareSendingMedia(AccountInstance accountInstance, ArrayList<SendingMediaInfo> media, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, boolean forceDocument, boolean groupMedia, MessageObject editingMessageObject, boolean notify, int scheduleDate, int mode, boolean updateStikcersOrder, InputContentInfoCompat inputContent, String quickReplyShortcut, int quickReplyShortcutId, long effectId, boolean invertMedia) {
        if (media.isEmpty()) {
            return;
        }
        for (int a = 0, N = media.size(); a < N; a++) {
            if (media.get(a).ttl > 0) {
                groupMedia = false;
                break;
            }
        }
        final boolean groupMediaFinal = groupMedia;
        mediaSendQueue.postRunnable(() -> {
            long beginTime = System.currentTimeMillis();
            HashMap<SendingMediaInfo, MediaSendPrepareWorker> workers;
            int count = media.size();

            boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);
            if (!forceDocument && groupMediaFinal) {
                workers = new HashMap<>();
                for (int a = 0; a < count; a++) {
                    final SendingMediaInfo info = media.get(a);
                    if (info.searchImage == null && !info.isVideo && info.videoEditedInfo == null) {
                        String originalPath = info.path;
                        String tempPath = info.path;
                        if (tempPath == null && info.uri != null) {
                            tempPath = AndroidUtilities.getPath(info.uri);
                            originalPath = info.uri.toString();
                        }

                        boolean isWebP = false;
                        if (tempPath != null && info.ttl <= 0 && (tempPath.endsWith(".gif") || (isWebP = tempPath.endsWith(".webp")))) {
                            if (media.size() <= 1 && (!isWebP || shouldSendWebPAsSticker(tempPath, null)) && TextUtils.isEmpty(info.caption)) {
                                continue;
                            } else {
                                info.forceImage = true;
                            }
                        } else if (ImageLoader.shouldSendImageAsDocument(info.path, info.uri)) {
                            continue;
                        } else if (tempPath == null && info.uri != null) {
                            if (MediaController.isGif(info.uri) || (isWebP = MediaController.isWebp(info.uri))) {
                                if (media.size() <= 1 && (!isWebP || shouldSendWebPAsSticker(null, info.uri)) && TextUtils.isEmpty(info.caption)) {
                                    continue;
                                } else {
                                    info.forceImage = true;
                                }
                            }
                        }

                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        } else {
                            originalPath = null;
                        }
                        TLRPC.TL_photo photo = null;
                        String parentObject = null;
                        if (!isEncrypted && info.ttl == 0) {
                            Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 0 : 3);
                            if (sentData != null && sentData[0] instanceof TLRPC.TL_photo) {
                                photo = (TLRPC.TL_photo) sentData[0];
                                parentObject = (String) sentData[1];
                            }
                            if (photo == null && info.uri != null) {
                                sentData = accountInstance.getMessagesStorage().getSentFile(AndroidUtilities.getPath(info.uri), !isEncrypted ? 0 : 3);
                                if (sentData != null && sentData[0] instanceof TLRPC.TL_photo) {
                                    photo = (TLRPC.TL_photo) sentData[0];
                                    parentObject = (String) sentData[1];
                                }
                            }
                            ensureMediaThumbExists(accountInstance, isEncrypted, photo, info.path, info.uri, 0);
                        }
                        final MediaSendPrepareWorker worker = new MediaSendPrepareWorker();
                        workers.put(info, worker);
                        if (photo != null) {
                            worker.parentObject = parentObject;
                            worker.photo = photo;
                        } else {
                            worker.sync = new CountDownLatch(1);
                            mediaSendThreadPool.execute(() -> {
                                worker.photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(info.path, info.uri);
                                if (isEncrypted && info.canDeleteAfter) {
                                    new File(info.path).delete();
                                }
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
            ArrayList<Uri> sendAsDocumentsUri = null;
            ArrayList<String> sendAsDocumentsCaptions = null;
            ArrayList<ArrayList<TLRPC.MessageEntity>> sendAsDocumentsEntities = null;

            String extension = null;
            int mediaCount = 0;
            for (int a = 0; a < count; a++) {
                final SendingMediaInfo info = media.get(a);
                final boolean last = mediaCount == 0;
                if (groupMediaFinal && count > 1 && mediaCount % 10 == 0) {
                    lastGroupId = groupId = Utilities.random.nextLong();
                    mediaCount = 0;
                }
                if (info.searchImage != null && info.videoEditedInfo == null) {
                    if (info.searchImage.type == 1) {
                        final HashMap<String, String> params = new HashMap<>();
                        TLRPC.TL_document document = null;
                        String parentObject = null;
                        File cacheFile;
                        if (info.searchImage.document instanceof TLRPC.TL_document) {
                            document = (TLRPC.TL_document) info.searchImage.document;
                            cacheFile = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(document, true);
                        } else {
                            /*if (!isEncrypted) {
                                Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 1 : 4);
                                if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                                    document = (TLRPC.TL_document) sentData[0];
                                    parentObject = (String) sentData[1];
                                }
                            }*/
                            String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                        }
                        if (document == null) {
                            File thumbFile = null;
                            document = new TLRPC.TL_document();
                            document.id = 0;
                            document.file_reference = new byte[0];
                            document.date = accountInstance.getConnectionsManager().getCurrentTime();
                            TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                            fileName.file_name = "animation.gif";
                            document.attributes.add(fileName);
                            document.size = info.searchImage.size;
                            document.dc_id = 0;
                            if (!forceDocument && cacheFile.toString().endsWith("mp4")) {
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
                                    int side = isEncrypted || info.ttl != 0 ? 90 : 320;
                                    Bitmap bitmap;
                                    if (thumbFile.getAbsolutePath().endsWith("mp4")) {
                                        bitmap = SendMessagesHelper.createVideoThumbnail(thumbFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
                                    } else {
                                        bitmap = ImageLoader.loadBitmap(thumbFile.getAbsolutePath(), null, side, side, true);
                                    }
                                    if (bitmap != null) {
                                        TLRPC.PhotoSize thumb = ImageLoader.scaleAndSaveImage(bitmap, side, side, side > 90 ? 80 : 55, isEncrypted);
                                        if (thumb != null) {
                                            document.thumbs.add(thumb);
                                            document.flags |= 1;
                                        }
                                        bitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                            if (document.thumbs.isEmpty()) {
                                TLRPC.TL_photoSize thumb = new TLRPC.TL_photoSize();
                                thumb.w = info.searchImage.width;
                                thumb.h = info.searchImage.height;
                                thumb.size = 0;
                                thumb.location = new TLRPC.TL_fileLocationUnavailable();
                                thumb.type = "x";
                                document.thumbs.add(thumb);
                                document.flags |= 1;
                            }
                        }
                        final TLRPC.TL_document documentFinal = document;
                        final String parentFinal = parentObject;
                        final String originalPathFinal = info.searchImage.imageUrl;
                        final String pathFinal = cacheFile == null ? info.searchImage.imageUrl : cacheFile.toString();
                        if (params != null && info.searchImage.imageUrl != null) {
                            params.put("originalPath", info.searchImage.imageUrl);
                        }
                        if (parentFinal != null) {
                            params.put("parentObject", parentFinal);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (editingMessageObject != null) {
                                accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, null, null, documentFinal, pathFinal, params, false, info.hasMediaSpoilers, parentFinal);
                            } else {
                                SendMessageParams sendMessageParams = SendMessageParams.of(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, 0, parentFinal, null, false, info.hasMediaSpoilers);
                                sendMessageParams.replyToStoryItem = storyItem;
                                sendMessageParams.replyQuote = quote;
                                sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                                sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                                if (last) {
                                    sendMessageParams.effect_id = effectId;
                                }
                                sendMessageParams.invert_media = invertMedia;
                                accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                            }
                        });
                    } else {
                        boolean needDownloadHttp = true;
                        TLRPC.TL_photo photo = null;
                        String parentObject = null;
                        if (info.searchImage.photo instanceof TLRPC.TL_photo) {
                            photo = (TLRPC.TL_photo) info.searchImage.photo;
                        } else {
                            if (!isEncrypted && info.ttl == 0) {
                                /*Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 0 : 3);
                                if (sentData != null) {
                                    photo = (TLRPC.TL_photo) sentData[0];
                                    parentObject = (String) sentData[1];
                                    ensureMediaThumbExists(currentAccount, photo, );
                                }*/
                            }
                        }
                        if (photo == null) {
                            String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                            File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                            if (cacheFile.exists() && cacheFile.length() != 0) {
                                photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(cacheFile.toString(), null);
                                if (photo != null) {
                                    needDownloadHttp = false;
                                }
                            }
                            if (photo == null) {
                                md5 = Utilities.MD5(info.searchImage.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.thumbUrl, "jpg");
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                                if (cacheFile.exists()) {
                                    photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(cacheFile.toString(), null);
                                }
                                if (photo == null) {
                                    photo = new TLRPC.TL_photo();
                                    photo.date = accountInstance.getConnectionsManager().getCurrentTime();
                                    photo.file_reference = new byte[0];
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
                            final String parentFinal = parentObject;
                            final boolean needDownloadHttpFinal = needDownloadHttp;
                            final HashMap<String, String> params = new HashMap<>();
                            if (info.searchImage.imageUrl != null) {
                                params.put("originalPath", info.searchImage.imageUrl);
                            }
                            if (parentFinal != null) {
                                params.put("parentObject", parentFinal);
                            }
                            if (groupMediaFinal) {
                                mediaCount++;
                                params.put("groupId", "" + groupId);
                                if (mediaCount == 10 || a == count -1) {
                                    params.put("final", "1");
                                    lastGroupId = 0;
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                if (editingMessageObject != null) {
                                    accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, photoFinal, null, null, needDownloadHttpFinal ? info.searchImage.imageUrl : null, params, false, info.hasMediaSpoilers, parentFinal);
                                } else {
                                    SendMessageParams sendMessageParams = SendMessageParams.of(photoFinal, needDownloadHttpFinal ? info.searchImage.imageUrl : null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, false, info.hasMediaSpoilers);
                                    sendMessageParams.replyToStoryItem = storyItem;
                                    sendMessageParams.replyQuote = quote;
                                    sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                                    sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                                    sendMessageParams.effect_id = effectId;
                                    sendMessageParams.invert_media = invertMedia;
                                    accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                                }
                            });
                        }
                    }
                } else {
                    if (info.isVideo || info.videoEditedInfo != null) {
                        Bitmap thumb = null;
                        String thumbKey = null;

                        final VideoEditedInfo videoEditedInfo;
                        if (forceDocument) {
                            videoEditedInfo = null;
                        } else {
                            videoEditedInfo = info.videoEditedInfo != null ? info.videoEditedInfo : createCompressionSettings(info.path);
                        }

                        if (!forceDocument && (videoEditedInfo != null || info.path.endsWith("mp4"))) {
                            if (info.path == null && info.searchImage != null) {
                                if (info.searchImage.photo instanceof TLRPC.TL_photo) {
                                    info.path = FileLoader.getInstance(accountInstance.getCurrentAccount()).getPathToAttach(info.searchImage.photo, true).getAbsolutePath();
                                } else {
                                    String md5 = Utilities.MD5(info.searchImage.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage.imageUrl, "jpg");
                                    info.path = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5).getAbsolutePath();
                                }
                            }
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
                            String parentObject = null;
                            if (!isEncrypted && info.ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
                                Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 2 : 5);
                                if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                                    document = (TLRPC.TL_document) sentData[0];
                                    parentObject = (String) sentData[1];
                                    ensureMediaThumbExists(accountInstance, isEncrypted, document, info.path, null, startTime);
                                }
                            }
                            if (document == null) {
                                if (info.thumbPath != null) {
                                    thumb = BitmapFactory.decodeFile(info.thumbPath);
                                }
                                if (thumb == null) {
                                    thumb = createVideoThumbnailAtTime(info.path, startTime);
                                    if (thumb == null) {
                                        thumb = createVideoThumbnail(info.path, MediaStore.Video.Thumbnails.MINI_KIND);
                                    }
                                }

                                TLRPC.PhotoSize size = null;
                                if (thumb != null) {
                                    int side = isEncrypted || info.ttl != 0 ? 90 : Math.max(thumb.getWidth(), thumb.getHeight());
                                    size = ImageLoader.scaleAndSaveImage(null, thumb, videoEditedInfo != null && videoEditedInfo.isSticker ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG, false, side, side, side > 90 ? 80 : 55, isEncrypted, 0, 0, false);
                                    if (size != null && size.location != null) {
                                        thumbKey = getKeyForPhotoSize(accountInstance, size, null, true, false);
                                    }
                                }
                                document = new TLRPC.TL_document();
                                document.file_reference = new byte[0];
                                if (size != null) {
                                    document.thumbs.add(size);
                                    document.flags |= 1;
                                }
                                if (info.videoEditedInfo != null && info.videoEditedInfo.isSticker) {
                                    document.mime_type = "video/webm";
                                } else {
                                    document.mime_type = "video/mp4";
                                }
                                accountInstance.getUserConfig().saveConfig(false);
                                if (info.videoEditedInfo != null && info.videoEditedInfo.isSticker) {
                                    document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                    TLRPC.TL_documentAttributeSticker sticker = new TLRPC.TL_documentAttributeSticker();
                                    sticker.alt = "👍";
                                    sticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
                                    document.attributes.add(sticker);
//                                    document.size = videoEditedInfo.estimatedSize;
                                    if (size != null && size.location != null) {
                                        thumbKey = String.format(Locale.US, "%d_%d@b1", size.location.volume_id, size.location.local_id);
                                    }
                                }
                                TLRPC.TL_documentAttributeVideo attributeVideo;
                                if (isEncrypted) {
                                    attributeVideo = new TLRPC.TL_documentAttributeVideo_layer159();
                                } else {
                                    attributeVideo = new TLRPC.TL_documentAttributeVideo();
                                    attributeVideo.supports_streaming = true;
                                }
                                document.attributes.add(attributeVideo);
                                if (videoEditedInfo != null && (videoEditedInfo.needConvert() || !info.isVideo)) {
                                    if (info.isVideo && videoEditedInfo.muted) {
                                        fillVideoAttribute(info.path, attributeVideo, videoEditedInfo);
                                        videoEditedInfo.originalWidth = attributeVideo.w;
                                        videoEditedInfo.originalHeight = attributeVideo.h;
                                    } else {
                                        attributeVideo.duration = (int) (videoEditedInfo.estimatedDuration / 1000);
                                    }
                                    int w, h;
                                    int rotation = videoEditedInfo.rotationValue;
                                    if (videoEditedInfo.cropState != null) {
                                        w = videoEditedInfo.cropState.transformWidth;
                                        h = videoEditedInfo.cropState.transformHeight;
                                    } else {
                                        w = videoEditedInfo.resultWidth;
                                        h = videoEditedInfo.resultHeight;
                                    }
                                    if (rotation == 90 || rotation == 270) {
                                        attributeVideo.w = h;
                                        attributeVideo.h = w;
                                    } else {
                                        attributeVideo.w = w;
                                        attributeVideo.h = h;
                                    }
                                    document.size = videoEditedInfo.estimatedSize;
                                } else {
                                    if (temp.exists()) {
                                        document.size = (int) temp.length();
                                    }
                                    fillVideoAttribute(info.path, attributeVideo, null);
                                }
                            }
                            if (videoEditedInfo != null && videoEditedInfo.muted) {
                                boolean found = false;
                                for (int b = 0, N = document.attributes.size(); b < N; b++) {
                                    if (document.attributes.get(b) instanceof TLRPC.TL_documentAttributeAnimated) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                                }
                            }
                            if (videoEditedInfo != null && (videoEditedInfo.needConvert() || !info.isVideo)) {
                                String ext = videoEditedInfo.isSticker ? "webm" : "mp4";
                                String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + "." + ext;
                                File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                                SharedConfig.saveConfig();
                                path = cacheFile.getAbsolutePath();
                            }
                            final TLRPC.TL_document videoFinal = document;
                            final String parentFinal = parentObject;
                            final String originalPathFinal = originalPath;
                            final String finalPath = path;
                            final HashMap<String, String> params = new HashMap<>();
                            final Bitmap thumbFinal = thumb;
                            final String thumbKeyFinal = thumbKey;
                            if (originalPath != null) {
                                params.put("originalPath", originalPath);
                            }
                            if (parentFinal != null) {
                                params.put("parentObject", parentFinal);
                            }
                            if (!muted && groupMediaFinal) {
                                mediaCount++;
                                params.put("groupId", "" + groupId);
                                if (mediaCount == 10 || a == count -1) {
                                    params.put("final", "1");
                                    lastGroupId = 0;
                                }
                            }
                            if (!isEncrypted && (videoEditedInfo == null || !videoEditedInfo.isSticker) && info.masks != null && !info.masks.isEmpty()) {
                                document.attributes.add(new TLRPC.TL_documentAttributeHasStickers());
                                SerializedData serializedData = new SerializedData(4 + info.masks.size() * 20);
                                serializedData.writeInt32(info.masks.size());
                                for (int b = 0; b < info.masks.size(); b++) {
                                    info.masks.get(b).serializeToStream(serializedData);
                                }
                                params.put("masks", Utilities.bytesToHex(serializedData.toByteArray()));
                                serializedData.cleanup();
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                if (thumbFinal != null && thumbKeyFinal != null) {
                                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(thumbFinal), thumbKeyFinal, false);
                                }
                                if (editingMessageObject != null) {
                                    accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, info.hasMediaSpoilers, parentFinal);
                                } else {
                                    SendMessageParams sendMessageParams = SendMessageParams.of(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, null, false, info.hasMediaSpoilers);
                                    sendMessageParams.replyToStoryItem = storyItem;
                                    sendMessageParams.replyQuote = quote;
                                    sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                                    sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                                    sendMessageParams.effect_id = effectId;
                                    sendMessageParams.invert_media = invertMedia;
                                    sendMessageParams.stars = info.stars;
                                    accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                                }
                            });
                        } else {
                            if (sendAsDocuments == null) {
                                sendAsDocuments = new ArrayList<>();
                                sendAsDocumentsOriginal = new ArrayList<>();
                                sendAsDocumentsCaptions = new ArrayList<>();
                                sendAsDocumentsEntities = new ArrayList<>();
                                sendAsDocumentsUri = new ArrayList<>();
                            }
                            sendAsDocuments.add(info.path);
                            sendAsDocumentsOriginal.add(info.path);
                            sendAsDocumentsUri.add(info.uri);
                            sendAsDocumentsCaptions.add(info.caption);
                            sendAsDocumentsEntities.add(info.entities);
                            //prepareSendingDocumentInternal(accountInstance, info.path, info.path, null, null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, editingMessageObject, null, false, forceDocument, notify, scheduleDate, null);
                        }
                    } else {
                        String originalPath = info.path;
                        String tempPath = info.path;
                        if (tempPath == null && info.uri != null) {
                            if (Build.VERSION.SDK_INT >= 30 && "content".equals(info.uri.getScheme())) {
                                tempPath = null;
                            } else {
                                tempPath = AndroidUtilities.getPath(info.uri);
                            }
                            originalPath = info.uri.toString();
                        }

                        boolean isDocument = false;
                        if (inputContent != null && info.uri != null) {
                            ClipDescription description = inputContent.getDescription();
                            if (description.hasMimeType("image/png")) {
                                InputStream inputStream = null;
                                FileOutputStream stream = null;
                                try {
                                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                    inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(info.uri);
                                    Bitmap b = BitmapFactory.decodeStream(inputStream, null, bmOptions);
                                    String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".webp";
                                    File fileDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                                    final File cacheFile = new File(fileDir, fileName);
                                    stream = new FileOutputStream(cacheFile);
                                    b.compress(Bitmap.CompressFormat.WEBP, 100, stream);
                                    SharedConfig.saveConfig();
                                    info.uri = Uri.fromFile(cacheFile);
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                } finally {
                                    try {
                                        if (inputStream != null) {
                                            inputStream.close();
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    try {
                                        if (stream != null) {
                                            stream.close();
                                        }
                                    } catch (Exception ignore) {

                                    }
                                }
                            }
                        }
                        if (forceDocument || ImageLoader.shouldSendImageAsDocument(info.path, info.uri)) {
                            isDocument = true;
                            extension = tempPath != null ? FileLoader.getFileExtension(new File(tempPath)) : "";
                        } else if (!info.forceImage && tempPath != null && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp")) && info.ttl <= 0) {
                            if (tempPath.endsWith(".gif")) {
                                extension = "gif";
                            } else {
                                extension = "webp";
                            }
                            isDocument = true;
                        } else if (!info.forceImage && tempPath == null && info.uri != null) {
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
                                sendAsDocumentsUri = new ArrayList<>();
                            }
                            sendAsDocuments.add(tempPath);
                            sendAsDocumentsOriginal.add(originalPath);
                            sendAsDocumentsUri.add(info.uri);
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
                            String parentObject = null;
                            if (workers != null) {
                                MediaSendPrepareWorker worker = workers.get(info);
                                photo = worker.photo;
                                parentObject = worker.parentObject;
                                if (photo == null) {
                                    try {
                                        worker.sync.await();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    photo = worker.photo;
                                    parentObject = worker.parentObject;
                                }
                            } else {
                                if (!isEncrypted && info.ttl == 0) {
                                    Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 0 : 3);
                                    if (sentData != null && sentData[0] instanceof TLRPC.TL_photo) {
                                        photo = (TLRPC.TL_photo) sentData[0];
                                        parentObject = (String) sentData[1];
                                    }
                                    if (photo == null && info.uri != null) {
                                        sentData = accountInstance.getMessagesStorage().getSentFile(AndroidUtilities.getPath(info.uri), !isEncrypted ? 0 : 3);
                                        if (sentData != null && sentData[0] instanceof TLRPC.TL_photo) {
                                            photo = (TLRPC.TL_photo) sentData[0];
                                            parentObject = (String) sentData[1];
                                        }
                                    }
                                    ensureMediaThumbExists(accountInstance, isEncrypted, photo, info.path, info.uri, 0);
                                }
                                if (photo == null) {
                                    photo = accountInstance.getSendMessagesHelper().generatePhotoSizes(info.path, info.uri);
                                    if (isEncrypted && info.canDeleteAfter) {
                                        new File(info.path).delete();
                                    }
                                }
                            }
                            if (photo != null) {
                                final TLRPC.TL_photo photoFinal = photo;
                                final String parentFinal = parentObject;
                                final HashMap<String, String> params = new HashMap<>();
                                final Bitmap[] bitmapFinal = new Bitmap[1];
                                final String[] keyFinal = new String[1];
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
                                if (parentFinal != null) {
                                    params.put("parentObject", parentFinal);
                                }

                                try {
                                    if (!groupMediaFinal || media.size() == 1) {
                                        TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoFinal.sizes, AndroidUtilities.getPhotoSize());
                                        if (currentPhotoObject != null) {
                                            keyFinal[0] = getKeyForPhotoSize(accountInstance, currentPhotoObject, bitmapFinal, false, false);
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }

                                if (groupMediaFinal) {
                                    mediaCount++;
                                    params.put("groupId", "" + groupId);
                                    if (mediaCount == 10 || a == count - 1) {
                                        params.put("final", "1");
                                        lastGroupId = 0;
                                    }
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (bitmapFinal[0] != null && keyFinal[0] != null) {
                                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal[0]), keyFinal[0], false);
                                    }
                                    if (editingMessageObject != null) {
                                        accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, photoFinal, null, null, null, params, false, info.hasMediaSpoilers, parentFinal);
                                    } else {
                                        SendMessageParams sendMessageParams = SendMessageParams.of(photoFinal, null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, updateStikcersOrder, info.hasMediaSpoilers);
                                        sendMessageParams.replyToStoryItem = storyItem;
                                        sendMessageParams.replyQuote = quote;
                                        sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                                        sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                                        sendMessageParams.effect_id = effectId;
                                        sendMessageParams.invert_media = invertMedia;
                                        sendMessageParams.stars = info.stars;
                                        accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                                    }
                                });
                            } else {
                                if (sendAsDocuments == null) {
                                    sendAsDocuments = new ArrayList<>();
                                    sendAsDocumentsOriginal = new ArrayList<>();
                                    sendAsDocumentsCaptions = new ArrayList<>();
                                    sendAsDocumentsEntities = new ArrayList<>();
                                    sendAsDocumentsUri = new ArrayList<>();
                                }
                                sendAsDocuments.add(tempPath);
                                sendAsDocumentsOriginal.add(originalPath);
                                sendAsDocumentsUri.add(info.uri);
                                sendAsDocumentsCaptions.add(info.caption);
                                sendAsDocumentsEntities.add(info.entities);
                            }
                        }
                    }
                }
            }
            if (lastGroupId != 0) {
                finishGroup(accountInstance, lastGroupId, scheduleDate);
            }
            if (inputContent != null) {
                inputContent.releasePermission();
            }
            if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                long[] groupId2 = new long[1];
                int documentsCount = sendAsDocuments.size();
                for (int a = 0; a < documentsCount; a++) {
                    if (forceDocument && !isEncrypted && count > 1 && mediaCount % 10 == 0) {
                        groupId2[0] = Utilities.random.nextLong();
                        mediaCount = 0;
                    }
                    mediaCount++;
                    int error = prepareSendingDocumentInternal(accountInstance, sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a), sendAsDocumentsUri.get(a), extension, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, sendAsDocumentsEntities.get(a), editingMessageObject, groupId2, mediaCount == 10 || a == documentsCount - 1, sendAsDocumentsCaptions.get(a), notify, scheduleDate, null, forceDocument, quickReplyShortcut, quickReplyShortcutId, effectId, invertMedia);
                    handleError(error, accountInstance);
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("total send time = " + (System.currentTimeMillis() - beginTime));
            }
        });
    }

    public static void fillVideoAttribute(String videoPath, TLRPC.TL_documentAttributeVideo attributeVideo, VideoEditedInfo videoEditedInfo) {
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
                attributeVideo.duration = Long.parseLong(duration) / 1000.0;
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
                    attributeVideo.duration = mp.getDuration() / 1000.0;
                    attributeVideo.w = mp.getVideoWidth();
                    attributeVideo.h = mp.getVideoHeight();
                    mp.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static Bitmap createVideoThumbnail(String filePath, int kind) {
        float size;
        if (kind == MediaStore.Video.Thumbnails.FULL_SCREEN_KIND)  {
            size = 1920;
        } else if (kind == MediaStore.Video.Thumbnails.MICRO_KIND) {
            size = 96;
        } else {
            size = 512;
        }
        Bitmap bitmap = createVideoThumbnailAtTime(filePath, 0);
        if (bitmap != null) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > size || h > size) {
                float scale = Math.max(w, h) / size;
                w /= scale;
                h /= scale;
                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
            }
        }
        return bitmap;
    }

    public static Bitmap createVideoThumbnailAtTime(String filePath, long time) {
        return createVideoThumbnailAtTime(filePath, time, null, false);
    }

    public static Bitmap createVideoThumbnailAtTime(String filePath, long time, int[] orientation, boolean precise) {
        Bitmap bitmap = null;
        if (precise) {
            AnimatedFileDrawable fileDrawable = new AnimatedFileDrawable(new File(filePath), true, 0, 0, null, null, null, 0, 0, true, null);
            bitmap = fileDrawable.getFrameAtTime(time, precise);
            if (orientation != null) {
                orientation[0] = fileDrawable.getOrientation();
            }
            fileDrawable.recycle();
            if (bitmap == null) {
                return createVideoThumbnailAtTime(filePath, time, orientation, false);
            }
        } else {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(filePath);
                bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_NEXT_SYNC);
                if (bitmap == null) {
                    bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST);
                }
            } catch (Exception ignore) {
                // Assume this is a corrupt video file.
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ex) {
                    // Ignore failures while cleaning up.
                }
            }
        }
        return bitmap;
    }

    private static VideoEditedInfo createCompressionSettings(String videoPath) {
        int[] params = new int[AnimatedFileDrawable.PARAM_NUM_COUNT];
        AnimatedFileDrawable.getVideoInfo(videoPath, params);

        if (params[AnimatedFileDrawable.PARAM_NUM_SUPPORTED_VIDEO_CODEC] == 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("video hasn't avc1 atom");
            }
            return null;
        }

        long originalSize = new File(videoPath).length();
        int originalBitrate = MediaController.getVideoBitrate(videoPath);
        if (originalBitrate == -1) {
            originalBitrate = params[AnimatedFileDrawable.PARAM_NUM_BITRATE];
        }
        int bitrate = originalBitrate;
        float videoDuration = params[AnimatedFileDrawable.PARAM_NUM_DURATION];
        long videoFramesSize = params[AnimatedFileDrawable.PARAM_NUM_VIDEO_FRAME_SIZE];
        long audioFramesSize = params[AnimatedFileDrawable.PARAM_NUM_AUDIO_FRAME_SIZE];
        int videoFramerate = params[AnimatedFileDrawable.PARAM_NUM_FRAMERATE];


        if (Build.VERSION.SDK_INT < 18) {
            try {
                MediaCodecInfo codecInfo = MediaController.selectCodec(MediaController.VIDEO_MIME_TYPE);
                if (codecInfo == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("no codec info for " + MediaController.VIDEO_MIME_TYPE);
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
                        if (MediaController.selectColorFormat(codecInfo, MediaController.VIDEO_MIME_TYPE) == 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("no color format for " + MediaController.VIDEO_MIME_TYPE);
                            }
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }

        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = -1;
        videoEditedInfo.endTime = -1;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.originalPath = videoPath;
        videoEditedInfo.framerate = videoFramerate;
        videoEditedInfo.estimatedDuration = (long) Math.ceil(videoDuration);
        videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = params[AnimatedFileDrawable.PARAM_NUM_WIDTH];
        videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = params[AnimatedFileDrawable.PARAM_NUM_HEIGHT];
        videoEditedInfo.rotationValue = params[AnimatedFileDrawable.PARAM_NUM_ROTATION];
        videoEditedInfo.originalDuration = (long) (videoDuration * 1000);

        int compressionsCount;

        float maxSize = Math.max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight);
        if (maxSize > 1280) {
            compressionsCount = 4;
        } else if (maxSize > 854) {
            compressionsCount = 3;
        } else if (maxSize > 640) {
            compressionsCount = 2;
        } else {
            compressionsCount = 1;
        }

        int selectedCompression = Math.round(DownloadController.getInstance(UserConfig.selectedAccount).getMaxVideoBitrate() / (100f / compressionsCount));

        if (selectedCompression > compressionsCount) {
            selectedCompression = compressionsCount;
        }
        boolean needCompress = false;
        if (new File(videoPath).length() < 1024L * 1024L * 1000L) {
            if (selectedCompression != compressionsCount || Math.max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight) > 1280) {
                needCompress = true;
                switch (selectedCompression) {
                    case 1:
                        maxSize = 432.0f;
                        break;
                    case 2:
                        maxSize = 640.0f;
                        break;
                    case 3:
                        maxSize = 848.0f;
                        break;
                    default:
                        maxSize = 1280.0f;
                        break;
                }
                float scale = videoEditedInfo.originalWidth > videoEditedInfo.originalHeight ? maxSize / videoEditedInfo.originalWidth : maxSize / videoEditedInfo.originalHeight;
                videoEditedInfo.resultWidth = Math.round(videoEditedInfo.originalWidth * scale / 2) * 2;
                videoEditedInfo.resultHeight = Math.round(videoEditedInfo.originalHeight * scale / 2) * 2;
            }
            bitrate = MediaController.makeVideoBitrate(
                    videoEditedInfo.originalHeight, videoEditedInfo.originalWidth,
                    originalBitrate,
                    videoEditedInfo.resultHeight, videoEditedInfo.resultWidth
            );
        }

        if (!needCompress) {
            videoEditedInfo.resultWidth = videoEditedInfo.originalWidth;
            videoEditedInfo.resultHeight = videoEditedInfo.originalHeight;
            videoEditedInfo.bitrate = bitrate;
            videoEditedInfo.estimatedSize = originalSize;
        } else {
            videoEditedInfo.bitrate = bitrate;
            int encoderBitrate = MediaController.extractRealEncoderBitrate(videoEditedInfo.resultWidth, videoEditedInfo.resultHeight, bitrate, false);
            videoEditedInfo.estimatedSize = (long) (audioFramesSize + videoDuration / 1000.0f * encoderBitrate / 8);
        }

        if (videoEditedInfo.estimatedSize == 0) {
            videoEditedInfo.estimatedSize = 1;
        }

        return videoEditedInfo;
    }

    @UiThread
    public static void prepareSendingVideo(AccountInstance accountInstance, String videoPath, VideoEditedInfo info, long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem, ChatActivity.ReplyQuote quote, ArrayList<TLRPC.MessageEntity> entities, int ttl, MessageObject editingMessageObject, boolean notify, int scheduleDate, boolean forceDocument, boolean hasMediaSpoilers, CharSequence caption, String quickReplyShortcut, int quickReplyShortcutId, long effectId) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(() -> {
            final VideoEditedInfo videoEditedInfo = info != null ? info : createCompressionSettings(videoPath);

            boolean isEncrypted = DialogObject.isEncryptedDialog(dialogId);

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
                        originalPath += videoEditedInfo.estimatedDuration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime + (videoEditedInfo.muted ? "_m" : "");
                        if (videoEditedInfo.resultWidth != videoEditedInfo.originalWidth) {
                            originalPath += "_" + videoEditedInfo.resultWidth;
                        }
                    }
                    startTime = videoEditedInfo.startTime >= 0 ? videoEditedInfo.startTime : 0;
                }
                TLRPC.TL_document document = null;
                String parentObject = null;
                if (!isEncrypted && ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
                    Object[] sentData = accountInstance.getMessagesStorage().getSentFile(originalPath, !isEncrypted ? 2 : 5);
                    if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                        document = (TLRPC.TL_document) sentData[0];
                        parentObject = (String) sentData[1];
                        ensureMediaThumbExists(accountInstance, isEncrypted, document, videoPath, null, startTime);
                    }
                }
                if (document == null) {
                    if (videoEditedInfo != null && videoEditedInfo.notReadyYet) {
                        thumb = videoEditedInfo.thumb;
                    }
                    if (thumb == null) {
                        thumb = createVideoThumbnailAtTime(videoPath, startTime);
                    }
                    if (thumb == null) {
                        thumb = createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    }
                    int side = isEncrypted || ttl != 0 ? 90 : 320;
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, side, side, side > 90 ? 80 : 55, isEncrypted);
                    if (thumb != null && size != null) {
                        if (isRound) {
                            if (isEncrypted) {
                                thumb = Bitmap.createScaledBitmap(thumb, 90, 90, true);
                                Utilities.blurBitmap(thumb, 7, Build.VERSION.SDK_INT < 21 ? 0 : 1, thumb.getWidth(), thumb.getHeight(), thumb.getRowBytes());
                                Utilities.blurBitmap(thumb, 7, Build.VERSION.SDK_INT < 21 ? 0 : 1, thumb.getWidth(), thumb.getHeight(), thumb.getRowBytes());
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
                    if (size != null) {
                        document.thumbs.add(size);
                        document.flags |= 1;
                    }
                    document.file_reference = new byte[0];
                    document.mime_type = "video/mp4";
                    accountInstance.getUserConfig().saveConfig(false);
                    TLRPC.TL_documentAttributeVideo attributeVideo;
                    if (isEncrypted) {
                        int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                        TLRPC.EncryptedChat encryptedChat = accountInstance.getMessagesController().getEncryptedChat(encryptedChatId);
                        if (encryptedChat == null) {
                            return;
                        }
                        attributeVideo = new TLRPC.TL_documentAttributeVideo_layer159();
                    } else {
                        attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        attributeVideo.supports_streaming = true;
                    }
                    attributeVideo.round_message = isRound;
                    document.attributes.add(attributeVideo);
                    if (videoEditedInfo != null && videoEditedInfo.notReadyYet) {
                        attributeVideo.w = videoEditedInfo.resultWidth;
                        attributeVideo.h = videoEditedInfo.resultHeight;
                        attributeVideo.duration = videoEditedInfo.estimatedDuration / 1000.0;
                        document.size = videoEditedInfo.estimatedSize;
                    } else if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
                        if (videoEditedInfo.muted) {
                            document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                            fillVideoAttribute(videoPath, attributeVideo, videoEditedInfo);
                            videoEditedInfo.originalWidth = attributeVideo.w;
                            videoEditedInfo.originalHeight = attributeVideo.h;
                        } else {
                            attributeVideo.duration = videoEditedInfo.estimatedDuration / 1000.0;
                        }

                        int w, h;
                        int rotation = videoEditedInfo.rotationValue;
                        if (videoEditedInfo.cropState != null) {
                            w = videoEditedInfo.cropState.transformWidth;
                            h = videoEditedInfo.cropState.transformHeight;
                            rotation += videoEditedInfo.cropState.transformRotation;
                        } else {
                            w = videoEditedInfo.resultWidth;
                            h = videoEditedInfo.resultHeight;
                        }
                        if (rotation == 90 || rotation == 270) {
                            attributeVideo.w = h;
                            attributeVideo.h = w;
                        } else {
                            attributeVideo.w = w;
                            attributeVideo.h = h;
                        }
                        document.size = videoEditedInfo.estimatedSize;
                    } else {
                        if (temp.exists()) {
                            document.size = (int) temp.length();
                        }
                        fillVideoAttribute(videoPath, attributeVideo, null);
                    }
                }
                if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
                    String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".mp4";
                    File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                    SharedConfig.saveConfig();
                    path = cacheFile.getAbsolutePath();
                }

                final TLRPC.TL_document videoFinal = document;
                final String parentFinal = parentObject;
                final String originalPathFinal = originalPath;
                final String finalPath = path;
                final HashMap<String, String> params = new HashMap<>();
                final Bitmap thumbFinal = thumb;
                final String thumbKeyFinal = thumbKey;
                final String captionFinal = caption != null ? caption.toString() : "";
                if (originalPath != null) {
                    params.put("originalPath", originalPath);
                }
                if (parentFinal != null) {
                    params.put("parentObject", parentFinal);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (thumbFinal != null && thumbKeyFinal != null) {
                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(thumbFinal), thumbKeyFinal, false);
                    }
                    if (editingMessageObject != null) {
                        accountInstance.getSendMessagesHelper().editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, hasMediaSpoilers, parentFinal);
                    } else {
                        SendMessageParams sendMessageParams = SendMessageParams.of(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, ttl, parentFinal, null, false, hasMediaSpoilers);
                        sendMessageParams.replyToStoryItem = storyItem;
                        sendMessageParams.replyQuote = quote;
                        sendMessageParams.quick_reply_shortcut_id = quickReplyShortcutId;
                        sendMessageParams.quick_reply_shortcut = quickReplyShortcut;
                        sendMessageParams.effect_id = effectId;
                        accountInstance.getSendMessagesHelper().sendMessage(sendMessageParams);
                    }
                });
            } else {
                prepareSendingDocumentInternal(accountInstance, videoPath, videoPath, null, null, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, entities, editingMessageObject, null, false, caption, notify, scheduleDate, null, forceDocument, quickReplyShortcut, quickReplyShortcutId, 0, false);
            }
        }).start();
    }

    public static class SendMessageParams {
        public String message;
        public String caption;
        public TLRPC.MessageMedia location;
        public TLRPC.TL_photo photo;
        public VideoEditedInfo videoEditedInfo;
        public TLRPC.User user;
        public TLRPC.TL_document document;
        public TLRPC.TL_game game;
        public TLRPC.TL_messageMediaPoll poll;
        public TLRPC.TL_messageMediaInvoice invoice;
        public TLRPC.TL_messageMediaWebPage mediaWebPage;
        public long peer;
        public String path;
        public MessageObject replyToMsg;
        public MessageObject replyToTopMsg;
        public TLRPC.WebPage webPage;
        public boolean searchLinks = true;
        public MessageObject retryMessageObject;
        public ArrayList<TLRPC.MessageEntity> entities;
        public TLRPC.ReplyMarkup replyMarkup;
        public HashMap<String, String> params;
        public boolean notify;
        public int scheduleDate;
        public int ttl;
        public Object parentObject;
        public MessageObject.SendAnimationData sendAnimationData;
        public boolean updateStickersOrder;
        public boolean hasMediaSpoilers;
        public TL_stories.StoryItem replyToStoryItem;
        public TL_stories.StoryItem sendingStory;
        public ChatActivity.ReplyQuote replyQuote;
        public boolean invert_media;
        public String quick_reply_shortcut;
        public int quick_reply_shortcut_id;
        public long effect_id;
        public long stars;

        public static SendMessageParams of(String string, long dialogId) {
            return of(string, null, null, null, null, null, null, null, null, null, dialogId, null, null, null, null, true, null, null, null, null, false, 0, 0, null, null, false);
        }

        public static SendMessageParams of(MessageObject retryMessageObject) {
            SendMessageParams params = of(null, null, null, null, null, null, null, null, null, null, retryMessageObject.getDialogId(), retryMessageObject.messageOwner.attachPath, null, null, null, true, retryMessageObject, null, retryMessageObject.messageOwner.reply_markup, retryMessageObject.messageOwner.params, !retryMessageObject.messageOwner.silent, retryMessageObject.scheduled ? retryMessageObject.messageOwner.date : 0, 0, null, null, false);
            if (retryMessageObject.messageOwner != null) {
                if (retryMessageObject.messageOwner.quick_reply_shortcut instanceof TLRPC.TL_inputQuickReplyShortcut) {
                    params.quick_reply_shortcut = ((TLRPC.TL_inputQuickReplyShortcut) retryMessageObject.messageOwner.quick_reply_shortcut).shortcut;
                }
                params.quick_reply_shortcut_id = retryMessageObject.getQuickReplyId();
            }
            return params;
        }

        public static SendMessageParams of(TLRPC.User user, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate) {
            return of(null, null, null, null, null, user, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false);
        }

        public static SendMessageParams of(TLRPC.TL_messageMediaInvoice invoice, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate) {
            return of(null, null, null, null, null, null, null, null, null, invoice, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false);
        }

        public static SendMessageParams of(TLRPC.TL_document document, VideoEditedInfo videoEditedInfo, String path, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, int ttl, Object parentObject, MessageObject.SendAnimationData sendAnimationData, boolean updateStickersOrder) {
            return of(null, caption, null, null, videoEditedInfo, null, document, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, sendAnimationData, updateStickersOrder);
        }

        public static SendMessageParams of(TLRPC.TL_document document, VideoEditedInfo videoEditedInfo, String path, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, int ttl, Object parentObject, MessageObject.SendAnimationData sendAnimationData, boolean updateStickersOrder, boolean hasMediaSpoilers) {
            return of(null, caption, null, null, videoEditedInfo, null, document, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, sendAnimationData, updateStickersOrder, hasMediaSpoilers);
        }

        public static SendMessageParams of(String message, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.WebPage webPage, boolean searchLinks, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, MessageObject.SendAnimationData sendAnimationData, boolean updateStickersOrder) {
            return of(message, null, null, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, webPage, searchLinks, null, entities, replyMarkup, params, notify, scheduleDate, 0, null, sendAnimationData, updateStickersOrder);
        }

        public static SendMessageParams of(TLRPC.MessageMedia location, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate) {
            return of(null, null, location, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false);
        }

        public static SendMessageParams of(TLRPC.TL_messageMediaPoll poll, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate) {
            return of(null, null, null, null, null, null, null, null, poll, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false);
        }

        public static SendMessageParams of(TLRPC.TL_game game, long peer,  MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate) {
            return of(null, null, null, null, null, null, null, game, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false);
        }

        public static SendMessageParams of(TLRPC.TL_photo photo, String path, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, int ttl, Object parentObject, boolean updateStickersOrder, boolean hasMediaSpoilers) {
            return of(null, caption, null, photo, null, null, null, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, null, updateStickersOrder, hasMediaSpoilers);
        }

        public static SendMessageParams of(TLRPC.TL_photo photo, String path, long peer, MessageObject replyToMsg, MessageObject replyToTopMsg, String caption, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, int ttl, Object parentObject, boolean updateStickersOrder) {
            return of(null, caption, null, photo, null, null, null, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, null, updateStickersOrder);
        }

        private static SendMessageParams of(String message, String caption, TLRPC.MessageMedia location, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_game game, TLRPC.TL_messageMediaPoll poll, TLRPC.TL_messageMediaInvoice invoice, long peer, String path, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.WebPage webPage, boolean searchLinks, MessageObject retryMessageObject, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> params, boolean notify, int scheduleDate, int ttl, Object parentObject, MessageObject.SendAnimationData sendAnimationData, boolean updateStickersOrder) {
            return of(message, caption, location, photo, videoEditedInfo, user, document, game, poll, invoice, peer, path, replyToMsg, replyToTopMsg, webPage, searchLinks, retryMessageObject, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, sendAnimationData, updateStickersOrder, false);
        }

        public static SendMessageParams of(String message, String caption, TLRPC.MessageMedia location, TLRPC.TL_photo photo, VideoEditedInfo videoEditedInfo, TLRPC.User user, TLRPC.TL_document document, TLRPC.TL_game game, TLRPC.TL_messageMediaPoll poll, TLRPC.TL_messageMediaInvoice invoice, long peer, String path, MessageObject replyToMsg, MessageObject replyToTopMsg, TLRPC.WebPage webPage, boolean searchLinks, MessageObject retryMessageObject, ArrayList<TLRPC.MessageEntity> entities, TLRPC.ReplyMarkup replyMarkup, HashMap<String, String> messageParams, boolean notify, int scheduleDate, int ttl, Object parentObject, MessageObject.SendAnimationData sendAnimationData, boolean updateStickersOrder, boolean hasMediaSpoilers) {
            SendMessageParams params = new SendMessageParams();
            params.message = message;
            params.caption = caption;
            params.location = location;
            params.photo = photo;
            params.videoEditedInfo = videoEditedInfo;
            params.user = user;
            params.document = document;
            params.game = game;
            params.poll = poll;
            params.invoice = invoice;
            params.peer = peer;
            params.path = path;
            params.replyToMsg = replyToMsg;
            params.replyToTopMsg = replyToTopMsg;
            params.webPage = webPage;
            params.searchLinks = searchLinks;
            params.retryMessageObject = retryMessageObject;
            params.entities = entities;
            params.replyMarkup = replyMarkup;
            params.params = messageParams;
            params.notify = notify;
            params.scheduleDate = scheduleDate;
            params.ttl = ttl;
            params.parentObject = parentObject;
            params.sendAnimationData = sendAnimationData;
            params.updateStickersOrder = updateStickersOrder;
            params.hasMediaSpoilers = hasMediaSpoilers;
            return params;
        }
    }
}
