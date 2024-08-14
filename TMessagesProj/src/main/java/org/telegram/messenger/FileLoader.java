/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.text.TextUtils;
import android.util.SparseArray;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileLoader extends BaseController {

    private static final int PRIORITY_STREAM = 4;
    public static final int PRIORITY_HIGH = 3;
    public static final int PRIORITY_NORMAL_UP = 2;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_LOW = 0;

    private int priorityIncreasePointer;

    private static Pattern sentPattern;

    public static FilePathDatabase.FileMeta getFileMetadataFromParent(int currentAccount, Object parentObject) {
        if (parentObject instanceof String) {
            String str = (String) parentObject;
            if (str.startsWith("sent_")) {
                if (sentPattern == null) {
                    sentPattern = Pattern.compile("sent_.*_([0-9]+)_([0-9]+)_([0-9]+)_([0-9]+)");
                }
                try {
                    Matcher matcher = sentPattern.matcher(str);
                    if (matcher.matches()) {
                        FilePathDatabase.FileMeta fileMeta = new FilePathDatabase.FileMeta();
                        fileMeta.messageId = Integer.parseInt(matcher.group(1));
                        fileMeta.dialogId = Long.parseLong(matcher.group(2));
                        fileMeta.messageType = Integer.parseInt(matcher.group(3));
                        fileMeta.messageSize = Long.parseLong(matcher.group(4));
                        return fileMeta;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            FilePathDatabase.FileMeta fileMeta = new FilePathDatabase.FileMeta();
            fileMeta.messageId = messageObject.getId();
            fileMeta.dialogId = messageObject.getDialogId();
            fileMeta.messageType = messageObject.type;
            fileMeta.messageSize = messageObject.getSize();
            return fileMeta;
        } else if (parentObject instanceof TL_stories.StoryItem) {
            TL_stories.StoryItem storyItem = (TL_stories.StoryItem) parentObject;
            FilePathDatabase.FileMeta fileMeta = new FilePathDatabase.FileMeta();
            fileMeta.dialogId = storyItem.dialogId;
            fileMeta.messageType = MessageObject.TYPE_STORY;
            return fileMeta;
        }
        return null;
    }

    public static TLRPC.VideoSize getVectorMarkupVideoSize(TLRPC.Photo photo) {
        if (photo == null || photo.video_sizes == null) {
            return null;
        }
        for (int i = 0; i < photo.video_sizes.size(); i++) {
            TLRPC.VideoSize videoSize = photo.video_sizes.get(i);
            if (videoSize instanceof TLRPC.TL_videoSizeEmojiMarkup || videoSize instanceof TLRPC.TL_videoSizeStickerMarkup) {
                return videoSize;
            }
        }
        return null;
    }

    public static TLRPC.VideoSize getEmojiMarkup(ArrayList<TLRPC.VideoSize> video_sizes) {
        for (int i = 0; i < video_sizes.size(); i++) {
            if (video_sizes.get(i) instanceof TLRPC.TL_videoSizeEmojiMarkup || video_sizes.get(i) instanceof TLRPC.TL_videoSizeStickerMarkup) {
                return video_sizes.get(i);
            }
        }
        return null;
    }

    private int getPriorityValue(int priorityType) {
        if (priorityType == PRIORITY_STREAM) {
            return Integer.MAX_VALUE;
        } else if (priorityType == PRIORITY_HIGH) {
            priorityIncreasePointer++;
            return FileLoaderPriorityQueue.PRIORITY_VALUE_MAX + priorityIncreasePointer;
        } else if (priorityType == PRIORITY_NORMAL_UP) {
            priorityIncreasePointer++;
            return FileLoaderPriorityQueue.PRIORITY_VALUE_NORMAL + priorityIncreasePointer;
        } else if (priorityType == PRIORITY_NORMAL) {
            return FileLoaderPriorityQueue.PRIORITY_VALUE_NORMAL;
        } else {
            return 0;
        }
    }

    public DispatchQueue getFileLoaderQueue() {
        return fileLoaderQueue;
    }

    public void setLocalPathTo(TLObject attach, String attachPath) {
        long documentId = 0;
        int dcId = 0;
        int type = 0;
        if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) attach;
            if (document.key != null) {
                type = MEDIA_DIR_CACHE;
            } else {
                if (MessageObject.isVoiceDocument(document)) {
                    type = MEDIA_DIR_AUDIO;
                } else if (MessageObject.isVideoDocument(document)) {
                    type = MEDIA_DIR_VIDEO;
                } else {
                    type = MEDIA_DIR_DOCUMENT;
                }
            }
            documentId = document.id;
            dcId = document.dc_id;
            filePathDatabase.putPath(documentId, dcId, type, FilePathDatabase.FLAG_LOCALLY_CREATED, attachPath);
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) attach;
            if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
                return;
            } else if (photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0 || photoSize.size < 0) {
                type = MEDIA_DIR_CACHE;
            } else {
                type = MEDIA_DIR_IMAGE;
            }
            documentId = photoSize.location.volume_id;
            dcId = photoSize.location.dc_id + (photoSize.location.local_id << 16);
            filePathDatabase.putPath(documentId, dcId, type, FilePathDatabase.FLAG_LOCALLY_CREATED, attachPath);
        }
    }


    public interface FileLoaderDelegate {
        void fileUploadProgressChanged(FileUploadOperation operation, String location, long uploadedSize, long totalSize, boolean isEncrypted);

        void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv, long totalFileSize);

        void fileDidFailedUpload(String location, boolean isEncrypted);

        void fileDidLoaded(String location, File finalFile, Object parentObject, int type);

        void fileDidFailedLoad(String location, int state);

        void fileLoadProgressChanged(FileLoadOperation operation, String location, long uploadedSize, long totalSize);
    }

    public static final int MEDIA_DIR_IMAGE = 0;
    public static final int MEDIA_DIR_AUDIO = 1;
    public static final int MEDIA_DIR_VIDEO = 2;
    public static final int MEDIA_DIR_DOCUMENT = 3;
    public static final int MEDIA_DIR_CACHE = 4;
    public static final int MEDIA_DIR_FILES = 5;
    public static final int MEDIA_DIR_STORIES = 6;

    public static final int MEDIA_DIR_IMAGE_PUBLIC = 100;
    public static final int MEDIA_DIR_VIDEO_PUBLIC = 101;

    public static final int IMAGE_TYPE_LOTTIE = 1;
    public static final int IMAGE_TYPE_ANIMATION = 2;
    public static final int IMAGE_TYPE_SVG = 3;
    public static final int IMAGE_TYPE_SVG_WHITE = 4;
    public static final int IMAGE_TYPE_THEME_PREVIEW = 5;

//    private final FileLoaderPriorityQueue largeFilesQueue = new FileLoaderPriorityQueue("large files queue", 2);
//    private final FileLoaderPriorityQueue filesQueue = new FileLoaderPriorityQueue("files queue", 3);
//    private final FileLoaderPriorityQueue imagesQueue = new FileLoaderPriorityQueue("imagesQueue queue", 6);
//    private final FileLoaderPriorityQueue audioQueue = new FileLoaderPriorityQueue("audioQueue queue", 3);

    private final FileLoaderPriorityQueue[] smallFilesQueue = new FileLoaderPriorityQueue[5];
    private final FileLoaderPriorityQueue[] largeFilesQueue = new FileLoaderPriorityQueue[5];

    public final static long DEFAULT_MAX_FILE_SIZE = 1024L * 1024L * 2000L;
    public final static long DEFAULT_MAX_FILE_SIZE_PREMIUM = DEFAULT_MAX_FILE_SIZE * 2L;

    public final static int PRELOAD_CACHE_TYPE = 11;

    private volatile static DispatchQueue fileLoaderQueue = new DispatchQueue("fileUploadQueue");
    private final FilePathDatabase filePathDatabase;

    private final LinkedList<FileUploadOperation> uploadOperationQueue = new LinkedList<>();
    private final LinkedList<FileUploadOperation> uploadSmallOperationQueue = new LinkedList<>();
    private final ConcurrentHashMap<String, FileUploadOperation> uploadOperationPaths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FileUploadOperation> uploadOperationPathsEnc = new ConcurrentHashMap<>();
    private int currentUploadOperationsCount = 0;
    private int currentUploadSmallOperationsCount = 0;


    private final ConcurrentHashMap<String, FileLoadOperation> loadOperationPaths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoadOperationUIObject> loadOperationPathsUI = new ConcurrentHashMap<>(10, 1, 2);
    private HashMap<String, Long> uploadSizes = new HashMap<>();

    private HashMap<String, Boolean> loadingVideos = new HashMap<>();

    private String forceLoadingFile;

    private static SparseArray<File> mediaDirs = null;
    private FileLoaderDelegate delegate = null;

    private int lastReferenceId;
    private ConcurrentHashMap<Integer, Object> parentObjectReferences = new ConcurrentHashMap<>();

    private static final FileLoader[] Instance = new FileLoader[UserConfig.MAX_ACCOUNT_COUNT];

    public static FileLoader getInstance(int num) {
        FileLoader localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (FileLoader.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new FileLoader(num);
                }
            }
        }
        return localInstance;
    }

    public FileLoader(int instance) {
        super(instance);
        filePathDatabase = new FilePathDatabase(instance);
        for (int i = 0; i < smallFilesQueue.length; i++)  {
            smallFilesQueue[i] = new FileLoaderPriorityQueue(instance, "smallFilesQueue dc" + (i + 1), FileLoaderPriorityQueue.TYPE_SMALL, fileLoaderQueue);
            largeFilesQueue[i] = new FileLoaderPriorityQueue(instance, "largeFilesQueue dc" + (i + 1), FileLoaderPriorityQueue.TYPE_LARGE, fileLoaderQueue);
        }
        dumpFilesQueue();
    }

    public static void setMediaDirs(SparseArray<File> dirs) {
        mediaDirs = dirs;
    }

    public static File checkDirectory(int type) {
        return mediaDirs.get(type);
    }

    public static File getDirectory(int type) {
        File dir = mediaDirs.get(type);
        if (dir == null && type != FileLoader.MEDIA_DIR_CACHE) {
            dir = mediaDirs.get(FileLoader.MEDIA_DIR_CACHE);
        }
        if (BuildVars.NO_SCOPED_STORAGE) {
            try {
                if (dir != null && !dir.isDirectory()) {
                    dir.mkdirs();
                }
            } catch (Exception e) {
                //don't prompt
            }
        }
        return dir;
    }

    public int getFileReference(Object parentObject) {
        int reference = lastReferenceId++;
        parentObjectReferences.put(reference, parentObject);
        return reference;
    }

    public Object getParentObject(int reference) {
        return parentObjectReferences.get(reference);
    }

    public void setLoadingVideoInternal(TLRPC.Document document, boolean player) {
        String key = getAttachFileName(document);
        String dKey = key + (player ? "p" : "");
        loadingVideos.put(dKey, true);
        getNotificationCenter().postNotificationName(NotificationCenter.videoLoadingStateChanged, key);
    }

    public void setLoadingVideo(TLRPC.Document document, boolean player, boolean schedule) {
        if (document == null) {
            return;
        }
        if (schedule) {
            AndroidUtilities.runOnUIThread(() -> setLoadingVideoInternal(document, player));
        } else {
            setLoadingVideoInternal(document, player);
        }
    }

    public void setLoadingVideoForPlayer(TLRPC.Document document, boolean player) {
        if (document == null) {
            return;
        }
        String key = getAttachFileName(document);
        if (loadingVideos.containsKey(key + (player ? "" : "p"))) {
            loadingVideos.put(key + (player ? "p" : ""), true);
            getNotificationCenter().postNotificationName(NotificationCenter.videoLoadingStateChanged, key);
        }
    }

    private void removeLoadingVideoInternal(TLRPC.Document document, boolean player) {
        String key = getAttachFileName(document);
        String dKey = key + (player ? "p" : "");
        if (loadingVideos.remove(dKey) != null) {
            getNotificationCenter().postNotificationName(NotificationCenter.videoLoadingStateChanged, key);
        }
    }

    public void removeLoadingVideo(TLRPC.Document document, boolean player, boolean schedule) {
        if (document == null) {
            return;
        }
        if (schedule) {
            AndroidUtilities.runOnUIThread(() -> removeLoadingVideoInternal(document, player));
        } else {
            removeLoadingVideoInternal(document, player);
        }
    }

    public boolean isLoadingVideo(TLRPC.Document document, boolean player) {
        return document != null && loadingVideos.containsKey(getAttachFileName(document) + (player ? "p" : ""));
    }

    public boolean isLoadingVideoAny(TLRPC.Document document) {
        return isLoadingVideo(document, false) || isLoadingVideo(document, true);
    }

    public void cancelFileUpload(final String location, final boolean enc) {
        if (location == null) {
            return;
        }
        fileLoaderQueue.postRunnable(() -> {
            FileUploadOperation operation;
            if (!enc) {
                operation = uploadOperationPaths.get(location);
            } else {
                operation = uploadOperationPathsEnc.get(location);
            }
            uploadSizes.remove(location);
            if (operation != null) {
                uploadOperationPathsEnc.remove(location);
                uploadOperationQueue.remove(operation);
                uploadSmallOperationQueue.remove(operation);
                operation.cancel();
            }
        });
    }

    public void checkUploadNewDataAvailable(final String location, final boolean encrypted, final long newAvailableSize, final long finalSize) {
        checkUploadNewDataAvailable(location, encrypted, newAvailableSize, finalSize, null);
    }

    public void checkUploadNewDataAvailable(final String location, final boolean encrypted, final long newAvailableSize, final long finalSize, final Float progress) {
        fileLoaderQueue.postRunnable(() -> {
            FileUploadOperation operation;
            if (encrypted) {
                operation = uploadOperationPathsEnc.get(location);
            } else {
                operation = uploadOperationPaths.get(location);
            }
            if (operation != null) {
                operation.checkNewDataAvailable(newAvailableSize, finalSize, progress);
            } else if (finalSize != 0) {
                uploadSizes.put(location, finalSize);
            }
        });
    }

    public void onNetworkChanged(final boolean slow) {
        fileLoaderQueue.postRunnable(() -> {
            for (ConcurrentHashMap.Entry<String, FileUploadOperation> entry : uploadOperationPaths.entrySet()) {
                entry.getValue().onNetworkChanged(slow);
            }
            for (ConcurrentHashMap.Entry<String, FileUploadOperation> entry : uploadOperationPathsEnc.entrySet()) {
                entry.getValue().onNetworkChanged(slow);
            }
        });
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int type) {
        uploadFile(location, encrypted, small, 0, type, false);
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final long estimatedSize, final int type, boolean forceSmallFile) {
        if (location == null) {
            return;
        }
        fileLoaderQueue.postRunnable(() -> {
            if (encrypted) {
                if (uploadOperationPathsEnc.containsKey(location)) {
                    return;
                }
            } else {
                if (uploadOperationPaths.containsKey(location)) {
                    return;
                }
            }
            long esimated = estimatedSize;
            if (esimated != 0) {
                Long finalSize = uploadSizes.get(location);
                if (finalSize != null) {
                    esimated = 0;
                    uploadSizes.remove(location);
                }
            }
            FileUploadOperation operation = new FileUploadOperation(currentAccount, location, encrypted, esimated, type);
            if (delegate != null && estimatedSize != 0) {
                delegate.fileUploadProgressChanged(operation, location, 0, estimatedSize, encrypted);
            }
            if (encrypted) {
                uploadOperationPathsEnc.put(location, operation);
            } else {
                uploadOperationPaths.put(location, operation);
            }
            if (forceSmallFile) {
                operation.setForceSmallFile();
            }
            operation.setDelegate(new FileUploadOperation.FileUploadOperationDelegate() {
                @Override
                public void didFinishUploadingFile(final FileUploadOperation operation, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv) {
                    fileLoaderQueue.postRunnable(() -> {
                        if (encrypted) {
                            uploadOperationPathsEnc.remove(location);
                        } else {
                            uploadOperationPaths.remove(location);
                        }
                        if (small) {
                            currentUploadSmallOperationsCount--;
                            if (currentUploadSmallOperationsCount < 1) {
                                FileUploadOperation operation12 = uploadSmallOperationQueue.poll();
                                if (operation12 != null) {
                                    currentUploadSmallOperationsCount++;
                                    operation12.start();
                                }
                            }
                        } else {
                            currentUploadOperationsCount--;
                            if (currentUploadOperationsCount < 1) {
                                FileUploadOperation operation12 = uploadOperationQueue.poll();
                                if (operation12 != null) {
                                    currentUploadOperationsCount++;
                                    operation12.start();
                                }
                            }
                        }
                        if (delegate != null) {
                            delegate.fileDidUploaded(location, inputFile, inputEncryptedFile, key, iv, operation.getTotalFileSize());
                        }
                    });
                }

                @Override
                public void didFailedUploadingFile(final FileUploadOperation operation) {
                    fileLoaderQueue.postRunnable(() -> {
                        if (encrypted) {
                            uploadOperationPathsEnc.remove(location);
                        } else {
                            uploadOperationPaths.remove(location);
                        }
                        if (delegate != null) {
                            delegate.fileDidFailedUpload(location, encrypted);
                        }
                        if (small) {
                            currentUploadSmallOperationsCount--;
                            if (currentUploadSmallOperationsCount < 1) {
                                FileUploadOperation operation1 = uploadSmallOperationQueue.poll();
                                if (operation1 != null) {
                                    currentUploadSmallOperationsCount++;
                                    operation1.start();
                                }
                            }
                        } else {
                            currentUploadOperationsCount--;
                            if (currentUploadOperationsCount < 1) {
                                FileUploadOperation operation1 = uploadOperationQueue.poll();
                                if (operation1 != null) {
                                    currentUploadOperationsCount++;
                                    operation1.start();
                                }
                            }
                        }
                    });
                }

                @Override
                public void didChangedUploadProgress(FileUploadOperation operation, long uploadedSize, long totalSize) {
                    if (delegate != null) {
                        delegate.fileUploadProgressChanged(operation, location, uploadedSize, totalSize, encrypted);
                    }
                }
            });
            if (small) {
                if (currentUploadSmallOperationsCount < 1) {
                    currentUploadSmallOperationsCount++;
                    operation.start();
                } else {
                    uploadSmallOperationQueue.add(operation);
                }
            } else {
                if (currentUploadOperationsCount < 1) {
                    currentUploadOperationsCount++;
                    operation.start();
                } else {
                    uploadOperationQueue.add(operation);
                }
            }
        });
    }

    public void setForceStreamLoadingFile(TLRPC.FileLocation location, String ext) {
        if (location == null) {
            return;
        }
        fileLoaderQueue.postRunnable(() -> {
            forceLoadingFile = getAttachFileName(location, ext);
            FileLoadOperation operation = loadOperationPaths.get(forceLoadingFile);
            if (operation != null) {
                if (operation.isPreloadVideoOperation()) {
                    operation.setIsPreloadVideoOperation(false);
                }
                operation.setForceRequest(true);
                operation.setPriority(getPriorityValue(PRIORITY_STREAM));
                operation.getQueue().remove(operation);
                operation.getQueue().add(operation);
                operation.getQueue().checkLoadingOperations();
            }
        });
    }

    public void cancelLoadFile(TLRPC.Document document) {
        cancelLoadFile(document, false);
    }

    public void cancelLoadFile(TLRPC.Document document, boolean deleteFile) {
        cancelLoadFile(document, null, null, null, null, null, deleteFile);
    }

    public void cancelLoadFile(SecureDocument document) {
        cancelLoadFile(null, document, null, null, null, null, false);
    }

    public void cancelLoadFile(WebFile document) {
        cancelLoadFile(null, null, document, null, null, null, false);
    }

    public void cancelLoadFile(TLRPC.PhotoSize photo) {
        cancelLoadFile(photo, false);
    }

    public void cancelLoadFile(TLRPC.PhotoSize photo, boolean deleteFile) {
        cancelLoadFile(null, null, null, photo.location, null, null, deleteFile);
    }

    public void cancelLoadFile(TLRPC.FileLocation location, String ext) {
        cancelLoadFile(location, ext, false);
    }

    public void cancelLoadFile(TLRPC.FileLocation location, String ext, boolean deleteFile) {
        cancelLoadFile(null, null, null, location, ext, null, deleteFile);
    }

    public void cancelLoadFile(String fileName) {
        cancelLoadFile(null, null, null, null, null, fileName, true);
    }

    public void cancelLoadFiles(ArrayList<String> fileNames) {
        for (int a = 0, N = fileNames.size(); a < N; a++) {
            cancelLoadFile(null, null, null, null, null, fileNames.get(a), true);
        }
    }

    private void cancelLoadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt, String name, boolean deleteFile) {
        if (location == null && document == null && webDocument == null && secureDocument == null && TextUtils.isEmpty(name)) {
            return;
        }
        final String fileName;
        if (location != null) {
            fileName = getAttachFileName(location, locationExt);
        } else if (document != null) {
            fileName = getAttachFileName(document);
        } else if (secureDocument != null) {
            fileName = getAttachFileName(secureDocument);
        } else if (webDocument != null) {
            fileName = getAttachFileName(webDocument);
        } else {
            fileName = name;
        }
        LoadOperationUIObject uiObject = loadOperationPathsUI.remove(fileName);
        Runnable runnable = uiObject != null ? uiObject.loadInternalRunnable : null;
        boolean removed = uiObject != null;
        if (runnable != null) {
            fileLoaderQueue.cancelRunnable(runnable);
        }
        fileLoaderQueue.postRunnable(() -> {
            FileLoadOperation operation = loadOperationPaths.remove(fileName);
            if (operation != null) {
                FileLoaderPriorityQueue queue = operation.getQueue();
                queue.cancel(operation);
            }
        });
        if (removed && document != null) {
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.onDownloadingFilesChanged);
            });
        }
    }

    public void changePriority(int priority, final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt, String name) {
        if (location == null && document == null && webDocument == null && secureDocument == null && TextUtils.isEmpty(name)) {
            return;
        }
        final String fileName;
        if (location != null) {
            fileName = getAttachFileName(location, locationExt);
        } else if (document != null) {
            fileName = getAttachFileName(document);
        } else if (secureDocument != null) {
            fileName = getAttachFileName(secureDocument);
        } else if (webDocument != null) {
            fileName = getAttachFileName(webDocument);
        } else {
            fileName = name;
        }
        fileLoaderQueue.postRunnable(() -> {
            FileLoadOperation operation = loadOperationPaths.get(fileName);
            if (operation != null) {
                int newPriority = getPriorityValue(priority);
                if (operation.getPriority() == newPriority) {
                    return;
                }
                operation.setPriority(newPriority);
                FileLoaderPriorityQueue queue = operation.getQueue();
                queue.remove(operation);
                queue.add(operation);
                queue.checkLoadingOperations();
                FileLog.d("update priority " + fileName + " position in queue " + operation.getPositionInQueue() + " account=" + currentAccount);
            }
        });
    }


    public void cancelLoadAllFiles() {
        for (String fileName : loadOperationPathsUI.keySet()) {
            LoadOperationUIObject uiObject = loadOperationPathsUI.get(fileName);
            Runnable runnable = uiObject != null ? uiObject.loadInternalRunnable : null;
            boolean removed = uiObject != null;
            if (runnable != null) {
                fileLoaderQueue.cancelRunnable(runnable);
            }
            fileLoaderQueue.postRunnable(() -> {
                FileLoadOperation operation = loadOperationPaths.remove(fileName);
                if (operation != null) {
                    FileLoaderPriorityQueue queue = operation.getQueue();
                    queue.cancel(operation);
                }
            });
//            if (removed && document != null) {
//                AndroidUtilities.runOnUIThread(() -> {
//                    getNotificationCenter().postNotificationName(NotificationCenter.onDownloadingFilesChanged);
//                });
//            }
        }
    }

    public FileUploadOperation findUploadOperationByRequestToken(final int requestToken) {
        for (FileUploadOperation operation : uploadOperationPaths.values()) {
            if (operation == null) continue;
            for (int i = 0; i < operation.requestTokens.size(); ++i) {
                if (operation.requestTokens.valueAt(i) == requestToken) {
                    return operation;
                }
            }
        }
        return null;
    }

    public boolean checkUploadCaughtPremiumFloodWait(final String filename) {
        if (filename == null) return false;
        FileUploadOperation operation = uploadOperationPaths.get(filename);
        if (operation != null && operation.caughtPremiumFloodWait) {
            operation.caughtPremiumFloodWait = false;
            return true;
        }
        return false;
    }

    public FileLoadOperation findLoadOperationByRequestToken(final int requestToken) {
        for (FileLoadOperation operation : loadOperationPaths.values()) {
            if (operation == null || operation.requestInfos == null) continue;
            for (FileLoadOperation.RequestInfo requestInfo : operation.requestInfos) {
                if (requestInfo != null && requestInfo.requestToken == requestToken) {
                    return operation;
                }
            }
        }
        return null;
    }

    public boolean checkLoadCaughtPremiumFloodWait(final String filename) {
        if (filename == null) return false;
        FileLoadOperation operation = loadOperationPaths.get(filename);
        if (operation != null && operation.caughtPremiumFloodWait) {
            operation.caughtPremiumFloodWait = false;
            return true;
        }
        return false;
    }

    public boolean isLoadingFile(final String fileName) {
        return fileName != null && loadOperationPathsUI.containsKey(fileName);
    }

    public float getBufferedProgressFromPosition(final float position, final String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return 0;
        }
        FileLoadOperation loadOperation = loadOperationPaths.get(fileName);
        if (loadOperation != null) {
            return loadOperation.getDownloadedLengthFromOffset(position);
        } else {
            return 0.0f;
        }
    }

    public void loadFile(ImageLocation imageLocation, Object parentObject, String ext, int priority, int cacheType) {
        if (imageLocation == null) {
            return;
        }
        if (cacheType == 0 && (imageLocation.isEncrypted() || imageLocation.photoSize != null && imageLocation.getSize() == 0)) {
            cacheType = 1;
        }
        loadFile(imageLocation.document, imageLocation.secureDocument, imageLocation.webFile, imageLocation.location, imageLocation, parentObject, ext, imageLocation.getSize(), priority, cacheType);
    }

    public void loadFile(SecureDocument secureDocument, int priority) {
        if (secureDocument == null) {
            return;
        }
        loadFile(null, secureDocument, null, null, null, null, null, 0, priority, 1);
    }

    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {
        if (document == null) {
            return;
        }
        if (cacheType == 0 && document.key != null) {
            cacheType = 1;
        }
        loadFile(document, null, null, null, null, parentObject, null, 0, priority, cacheType);
    }

    public void loadFile(WebFile document, int priority, int cacheType) {
        loadFile(null, null, document, null, null, null, null, 0, priority, cacheType);
    }


    private FileLoadOperation loadFileInternal(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, TLRPC.TL_fileLocationToBeDeprecated location, final ImageLocation imageLocation, Object parentObject, final String locationExt, final long locationSize, int priority, FileLoadOperationStream stream, final long streamOffset, boolean streamPriority, final int cacheType) {
        String fileName;
        if (location != null) {
            fileName = getAttachFileName(location, locationExt);
        } else if (secureDocument != null) {
            fileName = getAttachFileName(secureDocument);
        } else if (document != null) {
            fileName = getAttachFileName(document);
        } else if (webDocument != null) {
            fileName = getAttachFileName(webDocument);
        } else {
            fileName = null;
        }
        if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {
            return null;
        }
        if (fileName.startsWith("0_0")) {
            FileLog.e(new RuntimeException("cant get hash from " + document));
            return null;
        }
        if (cacheType != 10 && !TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            loadOperationPathsUI.put(fileName, new LoadOperationUIObject());
        }

        if (document != null && parentObject instanceof MessageObject && ((MessageObject) parentObject).putInDownloadsStore && !((MessageObject) parentObject).isAnyKindOfSticker()) {
            getDownloadController().startDownloadFile(document, (MessageObject) parentObject);
        }

        final String finalFileName = fileName;
        FileLoadOperation operation = loadOperationPaths.get(finalFileName);

        priority = getPriorityValue(priority);

        if (operation != null) {
            if (cacheType != 10 && operation.isPreloadVideoOperation()) {
                operation.setIsPreloadVideoOperation(false);
            }
            operation.setForceRequest(priority > 0);
            operation.setStream(stream, streamPriority, streamOffset);
            boolean priorityChanged = false;
            if (operation.getPriority() != priority) {
                priorityChanged = true;
                operation.setPriority(priority);
            }
            operation.getQueue().add(operation);
            operation.updateProgress();
            if (priorityChanged) {
                operation.getQueue().checkLoadingOperations();
            }
            FileLog.d("load operation update position fileName=" + finalFileName + " position in queue " + operation.getPositionInQueue() + " preloadFinish " + operation.isPreloadFinished() + " priority=" + operation.getPriority());
            return operation;
        }

        File tempDir = getDirectory(MEDIA_DIR_CACHE);
        File storeDir = tempDir;
        int type = MEDIA_DIR_CACHE;
        long documentId = 0;
        int dcId = 0;

        if (secureDocument != null) {
            operation = new FileLoadOperation(secureDocument);
            type = MEDIA_DIR_DOCUMENT;
        } else if (location != null) {
            documentId = location.volume_id;
            dcId = location.dc_id + (location.local_id << 16);
            operation = new FileLoadOperation(imageLocation, parentObject, locationExt, locationSize);
            type = MEDIA_DIR_IMAGE;
        } else if (document != null) {
            operation = new FileLoadOperation(document, parentObject);
            if (MessageObject.isVoiceDocument(document)) {
                type = MEDIA_DIR_AUDIO;
            } else if (MessageObject.isVideoDocument(document)) {
                type = MEDIA_DIR_VIDEO;
                documentId = document.id;
                dcId = document.dc_id;
            } else {
                type = MEDIA_DIR_DOCUMENT;
                documentId = document.id;
                dcId = document.dc_id;
            }
            if (MessageObject.isRoundVideoDocument(document)) {
                documentId = 0;
                dcId = 0;
            }
        } else if (webDocument != null) {
            operation = new FileLoadOperation(currentAccount, webDocument);
            if (webDocument.location != null) {
                type = MEDIA_DIR_CACHE;
            } else if (MessageObject.isVoiceWebDocument(webDocument)) {
                type = MEDIA_DIR_AUDIO;
            } else if (MessageObject.isVideoWebDocument(webDocument)) {
                type = MEDIA_DIR_VIDEO;
            } else if (MessageObject.isImageWebDocument(webDocument)) {
                type = MEDIA_DIR_IMAGE;
            } else {
                type = MEDIA_DIR_DOCUMENT;
            }
        }

        FileLoaderPriorityQueue loaderQueue;
        int index = Utilities.clamp(operation.getDatacenterId() - 1, 4, 0);
        boolean isStory = parentObject instanceof TL_stories.StoryItem;
        if (operation.totalBytesCount > 20 * 1024 * 1024 || isStory) {
            loaderQueue = largeFilesQueue[index];
        } else {
            loaderQueue = smallFilesQueue[index];
        }

        String storeFileName = fileName;

        if (cacheType == 0 || cacheType == 10 || isStory) {
            if (documentId != 0) {
                String path = getFileDatabase().getPath(documentId, dcId, type, true);
                boolean customPath = false;
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) {
                        customPath = true;
                        storeFileName = file.getName();
                        storeDir = file.getParentFile();
                    }
                }
                if (!customPath) {
                    storeFileName = fileName;
                    storeDir = getDirectory(type);
                    boolean saveCustomPath = false;

                    if (isStory) {
                        File newDir = getDirectory(MEDIA_DIR_STORIES);
                        if (newDir != null) {
                            storeDir = newDir;
                            saveCustomPath = true;
                        }
                    } else if ((type == MEDIA_DIR_IMAGE || type == MEDIA_DIR_VIDEO) && canSaveToPublicStorage(parentObject)) {
                        File newDir;
                        if (type == MEDIA_DIR_IMAGE) {
                            newDir = getDirectory(MEDIA_DIR_IMAGE_PUBLIC);
                        } else {
                            newDir = getDirectory(MEDIA_DIR_VIDEO_PUBLIC);
                        }
                        if (newDir != null) {
                            storeDir = newDir;
                            saveCustomPath = true;
                        }
                    } else if (!TextUtils.isEmpty(getDocumentFileName(document)) && canSaveAsFile(parentObject)) {
                        storeFileName = getDocumentFileName(document);
                        File newDir = getDirectory(MEDIA_DIR_FILES);
                        if (newDir != null) {
                            storeDir = newDir;
                            saveCustomPath = true;
                        }
                    }

                    if (saveCustomPath) {
                        operation.pathSaveData = new FilePathDatabase.PathData(documentId, dcId, type);
                    }
                }
            } else {
                storeDir = getDirectory(type);
            }
        } else if (cacheType == ImageLoader.CACHE_TYPE_ENCRYPTED) {
            operation.setEncryptFile(true);
        }
        operation.setPaths(currentAccount, fileName, loaderQueue, storeDir, tempDir, storeFileName);
        if (cacheType == 10) {
            operation.setIsPreloadVideoOperation(true);
        }

        final int finalType = type;

        FileLoadOperation.FileLoadOperationDelegate fileLoadOperationDelegate = new FileLoadOperation.FileLoadOperationDelegate() {

            @Override
            public void didPreFinishLoading(FileLoadOperation operation, File finalFile) {
                FileLoaderPriorityQueue queue = operation.getQueue();
                fileLoaderQueue.postRunnable(() -> {
                    operation.preFinished = true;
                    queue.checkLoadingOperations();
                });
            }

            @Override
            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {
                if (!operation.isPreloadVideoOperation() && operation.isPreloadFinished()) {
                    checkDownloadQueue(operation, operation.getQueue(), 0);
                    return;
                }
                FilePathDatabase.FileMeta fileMeta = getFileMetadataFromParent(currentAccount, parentObject);
                if (fileMeta != null) {
                    getFileLoader().getFileDatabase().saveFileDialogId(finalFile, fileMeta);
                }
                if (parentObject instanceof MessageObject) {
                    MessageObject messageObject = (MessageObject) parentObject;
                    if (document != null && messageObject.putInDownloadsStore) {
                        getDownloadController().onDownloadComplete(messageObject);
                    }
                }

                if (!operation.isPreloadVideoOperation()) {
                    loadOperationPathsUI.remove(fileName);
                    if (delegate != null) {
                        delegate.fileDidLoaded(fileName, finalFile, parentObject, finalType);
                    }
                }

                checkDownloadQueue(operation, operation.getQueue(), 0);
            }

            @Override
            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {
                loadOperationPathsUI.remove(fileName);
                checkDownloadQueue(operation, operation.getQueue());
                if (delegate != null) {
                    delegate.fileDidFailedLoad(fileName, reason);
                }

                if (document != null && parentObject instanceof MessageObject && reason == 0) {
                    getDownloadController().onDownloadFail((MessageObject) parentObject, reason);
                } else if (reason == -1) {
                    LaunchActivity.checkFreeDiscSpaceStatic(2);
                }
            }

            @Override
            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {
                if (delegate != null) {
                    delegate.fileLoadProgressChanged(operation, fileName, uploadedSize, totalSize);
                }
            }

            @Override
            public void saveFilePath(FilePathDatabase.PathData pathSaveData, File cacheFileFinal) {
                getFileDatabase().putPath(pathSaveData.id, pathSaveData.dc, pathSaveData.type, 0, cacheFileFinal != null ? cacheFileFinal.toString() : null);
            }

            @Override
            public boolean hasAnotherRefOnFile(String path) {
                return getFileDatabase().hasAnotherRefOnFile(path);
            }

            @Override
            public boolean isLocallyCreatedFile(String path) {
                return getFileDatabase().isLocallyCreated(path);
            }
        };
        operation.setDelegate(fileLoadOperationDelegate);

        loadOperationPaths.put(finalFileName, operation);
        operation.setPriority(priority);
        if (stream == null) {
            stream = FileStreamLoadOperation.allStreams.get(documentId);
        }
        if (stream != null) {
            operation.setStream(stream, streamPriority, streamOffset);
        }

        loaderQueue.add(operation);
        loaderQueue.checkLoadingOperations(operation.isStory && priority >= FileLoaderPriorityQueue.PRIORITY_VALUE_MAX);

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("create load operation fileName=" + finalFileName + " documentName=" + getDocumentFileName(document) + " size=" + AndroidUtilities.formatFileSize(operation.totalBytesCount) + " position in queue " + operation.getPositionInQueue() + " account=" + currentAccount + " cacheType=" + cacheType + " priority=" + operation.getPriority() + " stream=" + stream);
        }
        return operation;
    }

    public static boolean canSaveAsFile(Object parentObject) {
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            if (!messageObject.isDocument() || messageObject.isRoundVideo() || messageObject.isVoice()) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean canSaveToPublicStorage(Object parentObject) {
        if (BuildVars.NO_SCOPED_STORAGE) {
            return false;
        }
        FilePathDatabase.FileMeta metadata = getFileMetadataFromParent(currentAccount, parentObject);
        MessageObject messageObject = null;
        if (metadata != null) {
            int flag;
            long dialogId = metadata.dialogId;
            if (getMessagesController().isChatNoForwards(getMessagesController().getChat(-dialogId)) || DialogObject.isEncryptedDialog(dialogId)) {
                return false;
            }
            if (parentObject instanceof MessageObject) {
                messageObject = (MessageObject) parentObject;
                if (messageObject.isRoundVideo() || messageObject.isVoice() || messageObject.isAnyKindOfSticker() || messageObject.messageOwner.noforwards) {
                    return false;
                }
            } else {
                if (metadata.messageType == MessageObject.TYPE_ROUND_VIDEO || metadata.messageType == MessageObject.TYPE_STICKER || metadata.messageType == MessageObject.TYPE_VOICE) {
                    return false;
                }
            }
            if (dialogId >= 0) {
                flag = SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;
            } else {
                if (ChatObject.isChannelAndNotMegaGroup(getMessagesController().getChat(-dialogId))) {
                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
                } else {
                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
                }
            }

            if (SaveToGallerySettingsHelper.needSave(flag, metadata, messageObject, currentAccount)) {
                return true;
            }
        }
        return false;
    }

    private void addOperationToQueue(FileLoadOperation operation, LinkedList<FileLoadOperation> queue) {
        int priority = operation.getPriority();
        if (priority > 0) {
            int index = queue.size();
            for (int a = 0, size = queue.size(); a < size; a++) {
                FileLoadOperation queuedOperation = queue.get(a);
                if (queuedOperation.getPriority() < priority) {
                    index = a;
                    break;
                }
            }
            queue.add(index, operation);
        } else {
            queue.add(operation);
        }
    }

    private void loadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, TLRPC.TL_fileLocationToBeDeprecated location, final ImageLocation imageLocation, final Object parentObject, final String locationExt, final long locationSize, final int priority, final int cacheType) {
        String fileName;
        if (location != null) {
            fileName = getAttachFileName(location, locationExt);
        } else if (document != null) {
            fileName = getAttachFileName(document);
        } else if (webDocument != null) {
            fileName = getAttachFileName(webDocument);
        } else {
            fileName = null;
        }
        Runnable runnable = () -> loadFileInternal(document, secureDocument, webDocument, location, imageLocation, parentObject, locationExt, locationSize, priority, null, 0, false, cacheType);
        if (cacheType != 10 && !TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            LoadOperationUIObject uiObject = new FileLoader.LoadOperationUIObject();
            uiObject.loadInternalRunnable = runnable;
            loadOperationPathsUI.put(fileName, uiObject);
        }
        fileLoaderQueue.postRunnable(runnable);
    }

    protected FileLoadOperation loadStreamFile(final FileLoadOperationStream stream, final TLRPC.Document document, final ImageLocation location, final Object parentObject, final long offset, final boolean priority, int loadingPriority) {
        return loadStreamFile(stream, document, location, parentObject, offset, priority, loadingPriority, document == null ? 1 : 0);
    }

    protected FileLoadOperation loadStreamFile(final FileLoadOperationStream stream, final TLRPC.Document document, final ImageLocation location, final Object parentObject, final long offset, final boolean priority, int loadingPriority, int cacheType) {
        final CountDownLatch semaphore = new CountDownLatch(1);
        final FileLoadOperation[] result = new FileLoadOperation[1];
        fileLoaderQueue.postRunnable(() -> {
            result[0] = loadFileInternal(document, null, null, document == null && location != null ? location.location : null, location, parentObject, document == null && location != null ? "mp4" : null, document == null && location != null ? location.currentSize : 0, loadingPriority, stream, offset, priority, cacheType);
            semaphore.countDown();
        });
        awaitFileLoadOperation(semaphore, true);
        return result[0];
    }

    /**
     * Necessary to wait of the FileLoadOperation object, despite the interruption of the thread.
     * Thread can be interrupted by {@link ImageLoader.CacheOutTask#cancel}.
     * For cases when two {@link ImageReceiver} require loading of the same file and the first {@link ImageReceiver} decides to cancel the operation.
     * For example, to autoplay a video after sending a message.
     */
    private void awaitFileLoadOperation(CountDownLatch latch, boolean ignoreInterruption) {
        try {
            latch.await();
        } catch (Exception e) {
            FileLog.e(e, false);
            if (ignoreInterruption) awaitFileLoadOperation(latch, false);
        }
    }

    private void checkDownloadQueue(FileLoadOperation operation, FileLoaderPriorityQueue queue) {
        checkDownloadQueue(operation, queue, 0);
    }

    private void checkDownloadQueue(FileLoadOperation operation, FileLoaderPriorityQueue queue, long delay) {
        fileLoaderQueue.postRunnable(() -> {
            if (queue.remove(operation)) {
                loadOperationPaths.remove(operation.getFileName());
                queue.checkLoadingOperations(operation.isStory);
            }
        }, delay);
    }

    public void setDelegate(FileLoaderDelegate fileLoaderDelegate) {
        delegate = fileLoaderDelegate;
    }

    public static String getMessageFileName(TLRPC.Message message) {
        if (message == null) {
            return "";
        }
        if (message instanceof TLRPC.TL_messageService) {
            if (message.action.photo != null) {
                ArrayList<TLRPC.PhotoSize> sizes = message.action.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getAttachFileName(sizeFull);
                    }
                }
            }
        } else {
            if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument) {
                return getAttachFileName(MessageObject.getMedia(message).document);
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = MessageObject.getMedia(message).photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true);
                    if (sizeFull != null) {
                        return getAttachFileName(sizeFull);
                    }
                }
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaWebPage) {
                if (MessageObject.getMedia(message).webpage.document != null) {
                    return getAttachFileName(MessageObject.getMedia(message).webpage.document);
                } else if (MessageObject.getMedia(message).webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = MessageObject.getMedia(message).webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getAttachFileName(sizeFull);
                        }
                    }
                }
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaInvoice) {
                TLRPC.WebDocument document = ((TLRPC.TL_messageMediaInvoice) MessageObject.getMedia(message)).webPhoto;
                if (document != null) {
                    return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getMimeTypePart(document.mime_type));
                }
            }
        }
        return "";
    }

    public File getPathToMessage(TLRPC.Message message) {
        return getPathToMessage(message, true);
    }

    public File getPathToMessage(TLRPC.Message message, boolean useFileDatabaseQueue) {
        if (message == null) {
            return new File("");
        }
        if (message instanceof TLRPC.TL_messageService) {
            if (message.action.photo != null) {
                ArrayList<TLRPC.PhotoSize> sizes = message.action.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull, null, false, useFileDatabaseQueue);
                    }
                }
            }
        } else {
            if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument) {
                return getPathToAttach(MessageObject.getMedia(message).document, null, MessageObject.getMedia(message).ttl_seconds != 0, useFileDatabaseQueue);
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = MessageObject.getMedia(message).photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true);
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull, null, MessageObject.getMedia(message).ttl_seconds != 0, useFileDatabaseQueue);
                    }
                }
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaWebPage) {
                if (MessageObject.getMedia(message).webpage.document != null) {
                    return getPathToAttach(MessageObject.getMedia(message).webpage.document, null, false, useFileDatabaseQueue);
                } else if (MessageObject.getMedia(message).webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = MessageObject.getMedia(message).webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getPathToAttach(sizeFull, null, false, useFileDatabaseQueue);
                        }
                    }
                }
            } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaInvoice) {
                return getPathToAttach(((TLRPC.TL_messageMediaInvoice) MessageObject.getMedia(message)).photo, null, true, useFileDatabaseQueue);
            }
        }
        return new File("");
    }

    public File getPathToAttach(TLObject attach) {
        return getPathToAttach(attach, null, false);
    }

    public File getPathToAttach(TLObject attach, boolean forceCache) {
        return getPathToAttach(attach, null, forceCache);
    }

    public File getPathToAttach(TLObject attach, String ext, boolean forceCache) {
        return getPathToAttach(attach, null, ext, forceCache, true);
    }

    public File getPathToAttach(TLObject attach, String ext, boolean forceCache, boolean useFileDatabaseQueue) {
        return getPathToAttach(attach, null, ext, forceCache, useFileDatabaseQueue);
    }

    /**
     * Return real file name. Used before file.exist()
     */
    public File getPathToAttach(TLObject attach, String size, String ext, boolean forceCache, boolean useFileDatabaseQueue) {
        File dir = null;
        long documentId = 0;
        int dcId = 0;
        int type = 0;
        if (forceCache) {
            dir = getDirectory(MEDIA_DIR_CACHE);
        } else {
            if (attach instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) attach;
                if (!TextUtils.isEmpty(document.localPath)) {
                    return new File(document.localPath);
                }
                if (document.key != null) {
                    type = MEDIA_DIR_CACHE;
                } else {
                    if (MessageObject.isVoiceDocument(document)) {
                        type = MEDIA_DIR_AUDIO;
                    } else if (MessageObject.isVideoDocument(document)) {
                        type = MEDIA_DIR_VIDEO;
                    } else {
                        type = MEDIA_DIR_DOCUMENT;
                    }
                }
                documentId = document.id;
                dcId = document.dc_id;
                dir = getDirectory(type);
            } else if (attach instanceof TLRPC.Photo) {
                TLRPC.PhotoSize photoSize = getClosestPhotoSizeWithSize(((TLRPC.Photo) attach).sizes, AndroidUtilities.getPhotoSize());
                return getPathToAttach(photoSize, ext, false, useFileDatabaseQueue);
            } else if (attach instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) attach;
                if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
                    dir = null;
                } else if (photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0 || photoSize.size < 0) {
                    dir = getDirectory(type = MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(type = MEDIA_DIR_IMAGE);
                }
                documentId = photoSize.location.volume_id;
                dcId = photoSize.location.dc_id + (photoSize.location.local_id << 16);
            } else if (attach instanceof TLRPC.TL_videoSize) {
                TLRPC.TL_videoSize videoSize = (TLRPC.TL_videoSize) attach;
                if (videoSize.location == null || videoSize.location.key != null || videoSize.location.volume_id == Integer.MIN_VALUE && videoSize.location.local_id < 0 || videoSize.size < 0) {
                    dir = getDirectory(type = MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(type = MEDIA_DIR_IMAGE);
                }
                documentId = videoSize.location.volume_id;
                dcId = videoSize.location.dc_id + (videoSize.location.local_id << 16);
            } else if (attach instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation fileLocation = (TLRPC.FileLocation) attach;
                if (fileLocation.key != null || fileLocation.volume_id == Integer.MIN_VALUE && fileLocation.local_id < 0) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    documentId = fileLocation.volume_id;
                    dcId = fileLocation.dc_id + (fileLocation.local_id << 16);
                    dir = getDirectory(type = MEDIA_DIR_IMAGE);
                }
            } else if (attach instanceof TLRPC.UserProfilePhoto || attach instanceof TLRPC.ChatPhoto) {
                if (size == null) {
                    size = "s";
                }
                if ("s".equals(size)) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(MEDIA_DIR_IMAGE);
                }
            } else if (attach instanceof WebFile) {
                WebFile document = (WebFile) attach;
                if (document.mime_type.startsWith("image/")) {
                    dir = getDirectory(MEDIA_DIR_IMAGE);
                } else if (document.mime_type.startsWith("audio/")) {
                    dir = getDirectory(MEDIA_DIR_AUDIO);
                } else if (document.mime_type.startsWith("video/")) {
                    dir = getDirectory(MEDIA_DIR_VIDEO);
                } else {
                    dir = getDirectory(MEDIA_DIR_DOCUMENT);
                }
            } else if (attach instanceof TLRPC.TL_secureFile || attach instanceof SecureDocument) {
                dir = getDirectory(MEDIA_DIR_CACHE);
            }
        }
        if (dir == null) {
            return new File("");
        }
        if (documentId != 0) {
            String path = getInstance(UserConfig.selectedAccount).getFileDatabase().getPath(documentId, dcId, type, useFileDatabaseQueue);
            if (path != null) {
                return new File(path);
            }
        }
        return new File(dir, getAttachFileName(attach, ext));
    }

    public FilePathDatabase getFileDatabase() {
        return filePathDatabase;
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side) {
        return getClosestPhotoSizeWithSize(sizes, side, false);
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side, boolean byMinSide) {
        return getClosestPhotoSizeWithSize(sizes, side, byMinSide, null, false);
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side, boolean byMinSide, TLRPC.PhotoSize toIgnore, boolean ignoreStripped) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        int lastSide = 0;
        TLRPC.PhotoSize closestObject = null;
        for (int a = 0; a < sizes.size(); a++) {
            TLRPC.PhotoSize obj = sizes.get(a);
            if (obj == null || obj == toIgnore || obj instanceof TLRPC.TL_photoSizeEmpty || obj instanceof TLRPC.TL_photoPathSize || ignoreStripped && obj instanceof TLRPC.TL_photoStrippedSize) {
                continue;
            }
            if (byMinSide) {
                int currentSide = Math.min(obj.h, obj.w);
                if (
                    closestObject == null ||
                    side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE ||
                    obj instanceof TLRPC.TL_photoCachedSize || side > lastSide && lastSide < currentSide
                ) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            } else {
                int currentSide = Math.max(obj.w, obj.h);
                if (
                    closestObject == null ||
                    side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE ||
                    obj instanceof TLRPC.TL_photoCachedSize ||
                    currentSide <= side && lastSide < currentSide
                ) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            }
        }
        return closestObject;
    }

    public static TLRPC.VideoSize getClosestVideoSizeWithSize(ArrayList<TLRPC.VideoSize> sizes, int side) {
        return getClosestVideoSizeWithSize(sizes, side, false);
    }

    public static TLRPC.VideoSize getClosestVideoSizeWithSize(ArrayList<TLRPC.VideoSize> sizes, int side, boolean byMinSide) {
        return getClosestVideoSizeWithSize(sizes, side, byMinSide, false);
    }

    public static TLRPC.VideoSize getClosestVideoSizeWithSize(ArrayList<TLRPC.VideoSize> sizes, int side, boolean byMinSide, boolean ignoreStripped) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        int lastSide = 0;
        TLRPC.VideoSize closestObject = null;
        for (int a = 0; a < sizes.size(); a++) {
            TLRPC.VideoSize obj = sizes.get(a);
            if (obj == null || obj instanceof TLRPC.TL_videoSizeEmojiMarkup || obj instanceof TLRPC.TL_videoSizeStickerMarkup) {
                continue;
            }
            if (byMinSide) {
                int currentSide = Math.min(obj.h, obj.w);
                if (closestObject == null ||
                                side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE ||
                                side > lastSide && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            } else {
                int currentSide = Math.max(obj.w, obj.h);
                if (
                        closestObject == null ||
                                side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE ||
                                currentSide <= side && lastSide < currentSide
                ) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            }
        }
        return closestObject;
    }

    public static TLRPC.TL_photoPathSize getPathPhotoSize(ArrayList<TLRPC.PhotoSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        for (int a = 0; a < sizes.size(); a++) {
            TLRPC.PhotoSize obj = sizes.get(a);
            if (obj instanceof TLRPC.TL_photoPathSize) {
                continue;
            }
            return (TLRPC.TL_photoPathSize) obj;
        }
        return null;
    }

    public static String getFileExtension(File file) {
        String name = file.getName();
        try {
            return name.substring(name.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return "";
        }
    }

    public static String fixFileName(String fileName) {
        if (fileName != null) {
            fileName = fileName.replaceAll("[\u0001-\u001f<>\u202E:\"/\\\\|?*\u007f]+", "").trim();
        }
        return fileName;
    }

    public static String getDocumentFileName(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        if (document.file_name_fixed != null) {
            return document.file_name_fixed;
        }
        String fileName = null;
        if (document != null) {
            if (document.file_name != null) {
                fileName = document.file_name;
            } else {
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute documentAttribute = document.attributes.get(a);
                    if (documentAttribute instanceof TLRPC.TL_documentAttributeFilename) {
                        fileName = documentAttribute.file_name;
                    }
                }
            }
        }
        fileName = fixFileName(fileName);
        return fileName != null ? fileName : "";
    }

    public static String getMimeTypePart(String mime) {
        int index;
        if ((index = mime.lastIndexOf('/')) != -1) {
            return mime.substring(index + 1);
        }
        return "";
    }

    public static String getExtensionByMimeType(String mime) {
        if (mime != null) {
            switch (mime) {
                case "video/mp4":
                    return ".mp4";
                case "video/x-matroska":
                    return ".mkv";
                case "audio/ogg":
                    return ".ogg";
            }
        }
        return "";
    }

    public static File getInternalCacheDir() {
        return ApplicationLoader.applicationContext.getCacheDir();
    }

    public static String getDocumentExtension(TLRPC.Document document) {
        String fileName = getDocumentFileName(document);
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0) {
            ext = document.mime_type;
        }
        if (ext == null) {
            ext = "";
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public static String getAttachFileName(TLObject attach) {
        return getAttachFileName(attach, null);
    }

    public static String getAttachFileName(TLObject attach, String ext) {
        return getAttachFileName(attach, null, ext);
    }

    /**
     * file hash. contains docId, dcId, ext.
     */
    public static String getAttachFileName(TLObject attach, String size, String ext) {
        if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) attach;
            String docExt;
            docExt = getDocumentFileName(document);
            int idx;
            if ((idx = docExt.lastIndexOf('.')) == -1) {
                docExt = "";
            } else {
                docExt = docExt.substring(idx);
            }
            if (docExt.length() <= 1) {
                docExt = getExtensionByMimeType(document.mime_type);
            }
            if (docExt.length() > 1) {
                return document.dc_id + "_" + document.id + docExt;
            } else {
                return document.dc_id + "_" + document.id;
            }
        } else if (attach instanceof SecureDocument) {
            SecureDocument secureDocument = (SecureDocument) attach;
            return secureDocument.secureFile.dc_id + "_" + secureDocument.secureFile.id + ".jpg";
        } else if (attach instanceof TLRPC.TL_secureFile) {
            TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) attach;
            return secureFile.dc_id + "_" + secureFile.id + ".jpg";
        } else if (attach instanceof WebFile) {
            WebFile document = (WebFile) attach;
            return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getMimeTypePart(document.mime_type));
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize) attach;
            if (photo.location == null || photo.location instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            return photo.location.volume_id + "_" + photo.location.local_id + "." + (ext != null ? ext : "jpg");
        } else if (attach instanceof TLRPC.TL_videoSize) {
            TLRPC.TL_videoSize video = (TLRPC.TL_videoSize) attach;
            if (video.location == null || video.location instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            return video.location.volume_id + "_" + video.location.local_id + "." + (ext != null ? ext : "mp4");
        } else if (attach instanceof TLRPC.FileLocation) {
            if (attach instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            TLRPC.FileLocation location = (TLRPC.FileLocation) attach;
            return location.volume_id + "_" + location.local_id + "." + (ext != null ? ext : "jpg");
        } else if (attach instanceof TLRPC.UserProfilePhoto) {
            if (size == null) {
                size = "s";
            }
            TLRPC.UserProfilePhoto location = (TLRPC.UserProfilePhoto) attach;
            if (location.photo_small != null) {
                if ("s".equals(size)) {
                    return getAttachFileName(location.photo_small, ext);
                } else {
                    return getAttachFileName(location.photo_big, ext);
                }
            } else {
                return location.photo_id + "_" + size + "." + (ext != null ? ext : "jpg");
            }
        } else if (attach instanceof TLRPC.ChatPhoto) {
            TLRPC.ChatPhoto location = (TLRPC.ChatPhoto) attach;
            if (location.photo_small != null) {
                if ("s".equals(size)) {
                    return getAttachFileName(location.photo_small, ext);
                } else {
                    return getAttachFileName(location.photo_big, ext);
                }
            } else {
                return location.photo_id + "_" + size + "." + (ext != null ? ext : "jpg");
            }
        }
        return "";
    }

    public void deleteFiles(final ArrayList<File> files, final int type) {
        if (files == null || files.isEmpty()) {
            return;
        }
        fileLoaderQueue.postRunnable(() -> {
            for (int a = 0; a < files.size(); a++) {
                File file = files.get(a);
                File encrypted = new File(file.getAbsolutePath() + ".enc");
                if (encrypted.exists()) {
                    try {
                        if (!encrypted.delete()) {
                            encrypted.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        File key = new File(FileLoader.getInternalCacheDir(), file.getName() + ".enc.key");
                        if (!key.delete()) {
                            key.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (file.exists()) {
                    try {
                        if (!file.delete()) {
                            file.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                try {
                    File qFile = new File(file.getParentFile(), "q_" + file.getName());
                    if (qFile.exists()) {
                        if (!qFile.delete()) {
                            qFile.deleteOnExit();
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (type == 2) {
                ImageLoader.getInstance().clearMemory();
            }
        });
    }

    public static boolean isVideoMimeType(String mime) {
        return "video/mp4".equals(mime) || SharedConfig.streamMkv && "video/x-matroska".equals(mime);
    }

    public static boolean copyFile(InputStream sourceFile, File destFile) throws IOException {
        return copyFile(sourceFile, destFile, -1);
    }

    public static boolean copyFile(InputStream sourceFile, File destFile, int maxSize) throws IOException {
        FileOutputStream out = new FileOutputStream(destFile);
        byte[] buf = new byte[4096];
        int len;
        int totalLen = 0;
        while ((len = sourceFile.read(buf)) > 0) {
            Thread.yield();
            out.write(buf, 0, len);
            totalLen += len;
            if (maxSize > 0 && totalLen >= maxSize) {
                break;
            }
        }
        out.getFD().sync();
        out.close();
        return true;
    }

    public static boolean isSamePhoto(TLObject photo1, TLObject photo2) {
        if (photo1 == null && photo2 != null || photo1 != null && photo2 == null) {
            return false;
        }
        if (photo1 == null && photo2 == null) {
            return true;
        }
        if (photo1.getClass() != photo2.getClass()) {
            return false;
        }
        if (photo1 instanceof TLRPC.UserProfilePhoto) {
            TLRPC.UserProfilePhoto p1 = (TLRPC.UserProfilePhoto) photo1;
            TLRPC.UserProfilePhoto p2 = (TLRPC.UserProfilePhoto) photo2;
            return p1.photo_id == p2.photo_id;
        } else if (photo1 instanceof TLRPC.ChatPhoto) {
            TLRPC.ChatPhoto p1 = (TLRPC.ChatPhoto) photo1;
            TLRPC.ChatPhoto p2 = (TLRPC.ChatPhoto) photo2;
            return p1.photo_id == p2.photo_id;
        }
        return false;
    }

    public static boolean isSamePhoto(TLRPC.FileLocation location, TLRPC.Photo photo) {
        if (location == null || !(photo instanceof TLRPC.TL_photo)) {
            return false;
        }
        for (int b = 0, N = photo.sizes.size(); b < N; b++) {
            TLRPC.PhotoSize size = photo.sizes.get(b);
            if (size.location != null && size.location.local_id == location.local_id && size.location.volume_id == location.volume_id) {
                return true;
            }
        }
        if (-location.volume_id == photo.id) {
            return true;
        }
        return false;
    }

    public static long getPhotoId(TLObject object) {
        if (object instanceof TLRPC.Photo) {
            return ((TLRPC.Photo) object).id;
        } else if (object instanceof TLRPC.ChatPhoto) {
            return ((TLRPC.ChatPhoto) object).photo_id;
        } else if (object instanceof TLRPC.UserProfilePhoto) {
            return ((TLRPC.UserProfilePhoto) object).photo_id;
        }
        return 0;
    }

    public void getCurrentLoadingFiles(ArrayList<MessageObject> currentLoadingFiles) {
        currentLoadingFiles.clear();
        currentLoadingFiles.addAll(getDownloadController().downloadingFiles);
        for (int i = 0; i < currentLoadingFiles.size(); i++) {
            currentLoadingFiles.get(i).isDownloadingFile = true;
        }
    }

    public void getRecentLoadingFiles(ArrayList<MessageObject> recentLoadingFiles) {
        recentLoadingFiles.clear();
        recentLoadingFiles.addAll(getDownloadController().recentDownloadingFiles);
        for (int i = 0; i < recentLoadingFiles.size(); i++) {
            recentLoadingFiles.get(i).isDownloadingFile = true;
        }
    }

    public void checkCurrentDownloadsFiles() {
        ArrayList<MessageObject> messagesToRemove = new ArrayList<>();
        ArrayList<MessageObject> messageObjects = new ArrayList<>(getDownloadController().recentDownloadingFiles);
        for (int i = 0; i < messageObjects.size(); i++) {
            messageObjects.get(i).checkMediaExistance();
            if (messageObjects.get(i).mediaExists) {
                messagesToRemove.add(messageObjects.get(i));
            }
        }
        if (!messagesToRemove.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                getDownloadController().recentDownloadingFiles.removeAll(messagesToRemove);
                getNotificationCenter().postNotificationName(NotificationCenter.onDownloadingFilesChanged);
            });
        }
    }

    /**
     * optimezed for bulk messages
     */
    public void checkMediaExistance(ArrayList<MessageObject> messageObjects) {
        getFileDatabase().checkMediaExistance(messageObjects);
    }

    public interface FileResolver {
        File getFile();
    }

    public void clearRecentDownloadedFiles() {
        getDownloadController().clearRecentDownloadedFiles();
    }

    public void clearFilePaths() {
        filePathDatabase.clear();
    }

    public static boolean checkUploadFileSize(int currentAccount, long length) {
        boolean premium = AccountInstance.getInstance(currentAccount).getUserConfig().isPremium();
        if (length < DEFAULT_MAX_FILE_SIZE || (length < DEFAULT_MAX_FILE_SIZE_PREMIUM && premium)) {
            return true;
        }
        return false;
    }

    private static class LoadOperationUIObject {
        Runnable loadInternalRunnable;
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l <<= 8;
            l ^= bytes[i] & 0xFF;
        }
        return l;
    }

    Runnable dumpFilesQueueRunnable = () -> {
        for (int i = 0; i < smallFilesQueue.length; i++) {
            if (smallFilesQueue[i].getCount() > 0 || largeFilesQueue[i].getCount() > 0) {
                FileLog.d("download queue: dc" + (i + 1) + " account=" + currentAccount + " small_operations=" + smallFilesQueue[i].getCount() + " large_operations=" + largeFilesQueue[i].getCount());
//                if (!largeFilesQueue[i].allOperations.isEmpty()) {
//                    largeFilesQueue[i].allOperations.get(0).dump();
//                }
            }
        }
        dumpFilesQueue();
    };

    public void dumpFilesQueue() {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        fileLoaderQueue.cancelRunnable(dumpFilesQueueRunnable);
        fileLoaderQueue.postRunnable(dumpFilesQueueRunnable, 10000);
    }
}
