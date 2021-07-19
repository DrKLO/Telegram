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
import android.util.SparseIntArray;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class FileLoader extends BaseController {

    public interface FileLoaderDelegate {
        void fileUploadProgressChanged(FileUploadOperation operation, String location, long uploadedSize, long totalSize, boolean isEncrypted);
        void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv, long totalFileSize);
        void fileDidFailedUpload(String location, boolean isEncrypted);
        void fileDidLoaded(String location, File finalFile, int type);
        void fileDidFailedLoad(String location, int state);
        void fileLoadProgressChanged(FileLoadOperation operation, String location, long uploadedSize, long totalSize);
    }

    public static final int MEDIA_DIR_IMAGE = 0;
    public static final int MEDIA_DIR_AUDIO = 1;
    public static final int MEDIA_DIR_VIDEO = 2;
    public static final int MEDIA_DIR_DOCUMENT = 3;
    public static final int MEDIA_DIR_CACHE = 4;

    public static final int IMAGE_TYPE_LOTTIE = 1;
    public static final int IMAGE_TYPE_ANIMATION = 2;
    public static final int IMAGE_TYPE_SVG = 3;
    public static final int IMAGE_TYPE_SVG_WHITE = 4;
    public static final int IMAGE_TYPE_THEME_PREVIEW = 5;

    public static final int QUEUE_TYPE_FILE = 0;
    public static final int QUEUE_TYPE_IMAGE = 1;
    public static final int QUEUE_TYPE_AUDIO = 2;

    public final static long MAX_FILE_SIZE = 1024L * 1024L * 2000L;

    private volatile static DispatchQueue fileLoaderQueue = new DispatchQueue("fileUploadQueue");

    private LinkedList<FileUploadOperation> uploadOperationQueue = new LinkedList<>();
    private LinkedList<FileUploadOperation> uploadSmallOperationQueue = new LinkedList<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPaths = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPathsEnc = new ConcurrentHashMap<>();
    private int currentUploadOperationsCount = 0;
    private int currentUploadSmallOperationsCount = 0;

    private SparseArray<LinkedList<FileLoadOperation>> fileLoadOperationQueues = new SparseArray<>();
    private SparseArray<LinkedList<FileLoadOperation>> audioLoadOperationQueues = new SparseArray<>();
    private SparseArray<LinkedList<FileLoadOperation>> imageLoadOperationQueues = new SparseArray<>();
    private SparseIntArray fileLoadOperationsCount = new SparseIntArray();
    private SparseIntArray audioLoadOperationsCount = new SparseIntArray();
    private SparseIntArray imageLoadOperationsCount = new SparseIntArray();

    private ConcurrentHashMap<String, FileLoadOperation> loadOperationPaths = new ConcurrentHashMap<>();
    private ArrayList<FileLoadOperation> activeFileLoadOperation = new ArrayList<>();
    private ConcurrentHashMap<String, Boolean> loadOperationPathsUI = new ConcurrentHashMap<>(10, 1, 2);
    private HashMap<String, Long> uploadSizes = new HashMap<>();

    private HashMap<String, Boolean> loadingVideos = new HashMap<>();

    private String forceLoadingFile;

    private static SparseArray<File> mediaDirs = null;
    private FileLoaderDelegate delegate = null;

    private int lastReferenceId;
    private ConcurrentHashMap<Integer, Object> parentObjectReferences = new ConcurrentHashMap<>();

    private static volatile FileLoader[] Instance = new FileLoader[UserConfig.MAX_ACCOUNT_COUNT];
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
        try {
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            //don't promt
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
        fileLoaderQueue.postRunnable(() -> {
            FileUploadOperation operation;
            if (encrypted) {
                operation = uploadOperationPathsEnc.get(location);
            } else {
                operation = uploadOperationPaths.get(location);
            }
            if (operation != null) {
                operation.checkNewDataAvailable(newAvailableSize, finalSize);
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

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int estimatedSize, final int type, boolean forceSmallFile) {
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
            int esimated = estimatedSize;
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

    private LinkedList<FileLoadOperation> getLoadOperationQueue(int datacenterId, int type) {
        SparseArray<LinkedList<FileLoadOperation>> queues;
        if (type == QUEUE_TYPE_AUDIO) {
            queues = audioLoadOperationQueues;
        } else if (type == QUEUE_TYPE_IMAGE) {
            queues = imageLoadOperationQueues;
        } else {
            queues = fileLoadOperationQueues;
        }
        LinkedList<FileLoadOperation> queue = queues.get(datacenterId);
        if (queue == null) {
            queue = new LinkedList<>();
            queues.put(datacenterId, queue);
        }
        return queue;
    }

    private SparseIntArray getLoadOperationCount(int type) {
        SparseArray<LinkedList<FileLoadOperation>> queues;
        if (type == QUEUE_TYPE_AUDIO) {
            return audioLoadOperationsCount;
        } else if (type == QUEUE_TYPE_IMAGE) {
            return imageLoadOperationsCount;
        } else {
            return fileLoadOperationsCount;
        }
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
                int datacenterId = operation.getDatacenterId();
                int queueType = operation.getQueueType();
                LinkedList<FileLoadOperation> downloadQueue = getLoadOperationQueue(datacenterId, queueType);
                SparseIntArray count = getLoadOperationCount(queueType);
                int index = downloadQueue.indexOf(operation);
                if (index >= 0) {
                    downloadQueue.remove(index);
                        if (operation.start()) {
                            count.put(datacenterId, count.get(datacenterId) + 1);
                        }
                        if (queueType == QUEUE_TYPE_FILE) {
                            if (operation.wasStarted() && !activeFileLoadOperation.contains(operation)) {
                                pauseCurrentFileLoadOperations(operation);
                                activeFileLoadOperation.add(operation);
                            }
                        }
                } else {
                    pauseCurrentFileLoadOperations(operation);
                    operation.start();
                    if (queueType == QUEUE_TYPE_FILE && !activeFileLoadOperation.contains(operation)) {
                        activeFileLoadOperation.add(operation);
                    }
                }
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
        loadOperationPathsUI.remove(fileName);
        fileLoaderQueue.postRunnable(() -> {
            FileLoadOperation operation = loadOperationPaths.remove(fileName);
            if (operation != null) {
                int queueType = operation.getQueueType();
                int datacenterId = operation.getDatacenterId();
                LinkedList<FileLoadOperation> queue = getLoadOperationQueue(datacenterId, queueType);
                if (!queue.remove(operation)) {
                    SparseIntArray count = getLoadOperationCount(queueType);
                    count.put(datacenterId, count.get(datacenterId) - 1);
                }
                if (queueType == QUEUE_TYPE_FILE) {
                    activeFileLoadOperation.remove(operation);
                }
                operation.cancel(deleteFile);
            }
        });
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

    private void pauseCurrentFileLoadOperations(FileLoadOperation newOperation) {
        for (int a = 0; a < activeFileLoadOperation.size(); a++) {
            FileLoadOperation operation = activeFileLoadOperation.get(a);
            if (operation == newOperation || operation.getDatacenterId() != newOperation.getDatacenterId() || operation.getFileName().equals(forceLoadingFile)) {
                continue;
            }
            activeFileLoadOperation.remove(operation);
            a--;
            int datacenterId = operation.getDatacenterId();
            int queueType = operation.getQueueType();
            LinkedList<FileLoadOperation> downloadQueue = getLoadOperationQueue(datacenterId, queueType);
            SparseIntArray count = getLoadOperationCount(queueType);
            downloadQueue.add(0, operation);
            if (operation.wasStarted()) {
                count.put(datacenterId, count.get(datacenterId) - 1);
            }
            operation.pause();
        }
    }

    private FileLoadOperation loadFileInternal(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, TLRPC.TL_fileLocationToBeDeprecated location, final ImageLocation imageLocation, Object parentObject, final String locationExt, final int locationSize, final int priority, final FileLoadOperationStream stream, final int streamOffset, boolean streamPriority, final int cacheType) {
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
        if (cacheType != 10 && !TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            loadOperationPathsUI.put(fileName, true);
        }

        FileLoadOperation operation = loadOperationPaths.get(fileName);
        if (operation != null) {
            if (cacheType != 10 && operation.isPreloadVideoOperation()) {
                operation.setIsPreloadVideoOperation(false);
            }
            if (stream != null || priority > 0) {
                int datacenterId = operation.getDatacenterId();
                operation.setForceRequest(true);

                int queueType = operation.getQueueType();
                LinkedList<FileLoadOperation> downloadQueue = getLoadOperationQueue(datacenterId, queueType);
                SparseIntArray count = getLoadOperationCount(queueType);
                int index = downloadQueue.indexOf(operation);
                if (index >= 0) {
                    downloadQueue.remove(index);
                    if (stream != null) {
                        if (operation.start(stream, streamOffset, streamPriority)) {
                            count.put(datacenterId, count.get(datacenterId) + 1);
                        }
                        if (queueType == QUEUE_TYPE_FILE) {
                            if (operation.wasStarted() && !activeFileLoadOperation.contains(operation)) {
                                pauseCurrentFileLoadOperations(operation);
                                activeFileLoadOperation.add(operation);
                            }
                        }
                    } else {
                        downloadQueue.add(0, operation);
                    }
                } else {
                    if (stream != null) {
                        pauseCurrentFileLoadOperations(operation);
                    }
                    operation.start(stream, streamOffset, streamPriority);
                    if (queueType == QUEUE_TYPE_FILE && !activeFileLoadOperation.contains(operation)) {
                        activeFileLoadOperation.add(operation);
                    }
                }
            }
            operation.updateProgress();
            return operation;
        }

        File tempDir = getDirectory(MEDIA_DIR_CACHE);
        File storeDir = tempDir;
        int type = MEDIA_DIR_CACHE;

        if (secureDocument != null) {
            operation = new FileLoadOperation(secureDocument);
            type = MEDIA_DIR_DOCUMENT;
        } else if (location != null) {
            operation = new FileLoadOperation(imageLocation, parentObject, locationExt, locationSize);
            type = MEDIA_DIR_IMAGE;
        } else if (document != null) {
            operation = new FileLoadOperation(document, parentObject);
            if (MessageObject.isVoiceDocument(document)) {
                type = MEDIA_DIR_AUDIO;
            } else if (MessageObject.isVideoDocument(document)) {
                type = MEDIA_DIR_VIDEO;
            } else {
                type = MEDIA_DIR_DOCUMENT;
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
        int queueType;
        if (type == MEDIA_DIR_AUDIO) {
            queueType = QUEUE_TYPE_AUDIO;
        } else if (secureDocument != null || location != null && (imageLocation == null || imageLocation.imageType != IMAGE_TYPE_ANIMATION) || MessageObject.isImageWebDocument(webDocument)) {
            queueType = QUEUE_TYPE_IMAGE;
        } else {
            queueType = QUEUE_TYPE_FILE;
        }
        if (cacheType == 0 || cacheType == 10) {
            storeDir = getDirectory(type);
        } else if (cacheType == 2) {
            operation.setEncryptFile(true);
        }
        operation.setPaths(currentAccount, fileName, queueType, storeDir, tempDir);
        if (cacheType == 10) {
            operation.setIsPreloadVideoOperation(true);
        }

        final int finalType = type;
        FileLoadOperation.FileLoadOperationDelegate fileLoadOperationDelegate = new FileLoadOperation.FileLoadOperationDelegate() {
            @Override
            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {
                if (!operation.isPreloadVideoOperation() && operation.isPreloadFinished()) {
                    return;
                }
                if (!operation.isPreloadVideoOperation()) {
                    loadOperationPathsUI.remove(fileName);
                    if (delegate != null) {
                        delegate.fileDidLoaded(fileName, finalFile, finalType);
                    }
                }
                checkDownloadQueue(operation.getDatacenterId(), queueType, fileName);
            }

            @Override
            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {
                loadOperationPathsUI.remove(fileName);
                checkDownloadQueue(operation.getDatacenterId(), queueType, fileName);
                if (delegate != null) {
                    delegate.fileDidFailedLoad(fileName, reason);
                }
            }

            @Override
            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {
                if (delegate != null) {
                    delegate.fileLoadProgressChanged(operation, fileName, uploadedSize, totalSize);
                }
            }
        };
        operation.setDelegate(fileLoadOperationDelegate);

        int datacenterId = operation.getDatacenterId();

        loadOperationPaths.put(fileName, operation);
        operation.setPriority(priority);

        boolean started;
        if (queueType == QUEUE_TYPE_AUDIO) {
            int maxCount = priority > 0 ? 3 : 1;
            int count = audioLoadOperationsCount.get(datacenterId);
            if (started = (stream != null || count < maxCount)) {
                if (operation.start(stream, streamOffset, streamPriority)) {
                    audioLoadOperationsCount.put(datacenterId, count + 1);
                }
            }
        } else if (queueType == QUEUE_TYPE_IMAGE) {
            int maxCount = priority > 0 ? 6 : 2;
            int count = imageLoadOperationsCount.get(datacenterId);
            if (started = (stream != null || count < maxCount)) {
                if (operation.start(stream, streamOffset, streamPriority)) {
                    imageLoadOperationsCount.put(datacenterId, count + 1);
                }
            }
        } else {
            int maxCount = priority > 0 ? 4 : 1;
            int count = fileLoadOperationsCount.get(datacenterId);
            if (started = (stream != null || count < maxCount)) {
                if (operation.start(stream, streamOffset, streamPriority)) {
                    fileLoadOperationsCount.put(datacenterId, count + 1);
                    activeFileLoadOperation.add(operation);
                }
                if (operation.wasStarted() && stream != null) {
                    pauseCurrentFileLoadOperations(operation);
                }
            }
        }
        if (!started) {
            addOperationToQueue(operation, getLoadOperationQueue(datacenterId, queueType));
        }
        return operation;
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

    private void loadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, TLRPC.TL_fileLocationToBeDeprecated location, final ImageLocation imageLocation, final Object parentObject, final String locationExt, final int locationSize, final int priority, final int cacheType) {
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
        if (cacheType != 10 && !TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            loadOperationPathsUI.put(fileName, true);
        }
        fileLoaderQueue.postRunnable(() -> loadFileInternal(document, secureDocument, webDocument, location, imageLocation, parentObject, locationExt, locationSize, priority, null, 0, false, cacheType));
    }

    protected FileLoadOperation loadStreamFile(final FileLoadOperationStream stream, final TLRPC.Document document, final ImageLocation location, final Object parentObject, final int offset, final boolean priority) {
        final CountDownLatch semaphore = new CountDownLatch(1);
        final FileLoadOperation[] result = new FileLoadOperation[1];
        fileLoaderQueue.postRunnable(() -> {
            result[0] = loadFileInternal(document, null, null, document == null && location != null ? location.location : null, location, parentObject, document == null && location != null ? "mp4" : null, document == null && location != null ? location.currentSize : 0, 1, stream, offset, priority,  document == null ? 1 : 0);
            semaphore.countDown();
        });
        try {
            semaphore.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    private void checkDownloadQueue(int datacenterId, int queueType, String fileName) {
        fileLoaderQueue.postRunnable(() -> {
            FileLoadOperation operation = loadOperationPaths.remove(fileName);
            LinkedList<FileLoadOperation> queue = getLoadOperationQueue(datacenterId, queueType);
            SparseIntArray operationCount = getLoadOperationCount(queueType);
            int count = operationCount.get(datacenterId);
            if (operation != null) {
                if (operation.wasStarted()) {
                    count--;
                    operationCount.put(datacenterId, count);
                } else {
                    queue.remove(operation);
                }
                if (queueType == QUEUE_TYPE_FILE) {
                    activeFileLoadOperation.remove(operation);
                }
            }
            while (!queue.isEmpty()) {
                operation = queue.get(0);
                int maxCount;
                if (queueType == QUEUE_TYPE_AUDIO) {
                    maxCount = operation.getPriority() != 0 ? 3 : 1;
                } else if (queueType == QUEUE_TYPE_IMAGE) {
                    maxCount = operation.getPriority() != 0 ? 6 : 2;
                } else {
                    maxCount = operation.isForceRequest() ? 3 : 1;
                }
                if (count < maxCount) {
                    operation = queue.poll();
                    if (operation != null && operation.start()) {
                        count++;
                        operationCount.put(datacenterId, count);
                        if (queueType == QUEUE_TYPE_FILE) {
                            if (!activeFileLoadOperation.contains(operation)) {
                                activeFileLoadOperation.add(operation);
                            }
                        }
                    }
                } else {
                    break;
                }
            }
        });
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
            if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                return getAttachFileName(message.media.document);
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.media.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true);
                    if (sizeFull != null) {
                        return getAttachFileName(sizeFull);
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (message.media.webpage.document != null) {
                    return getAttachFileName(message.media.webpage.document);
                } else if (message.media.webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.media.webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getAttachFileName(sizeFull);
                        }
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                TLRPC.WebDocument document = ((TLRPC.TL_messageMediaInvoice) message.media).photo;
                if (document != null) {
                    return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getMimeTypePart(document.mime_type));
                }
            }
        }
        return "";
    }

    public static File getPathToMessage(TLRPC.Message message) {
        if (message == null) {
            return new File("");
        }
        if (message instanceof TLRPC.TL_messageService) {
            if (message.action.photo != null) {
                ArrayList<TLRPC.PhotoSize> sizes = message.action.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull);
                    }
                }
            }
        } else {
            if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                return getPathToAttach(message.media.document, message.media.ttl_seconds != 0);
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.media.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true);
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull, message.media.ttl_seconds != 0);
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (message.media.webpage.document != null) {
                    return getPathToAttach(message.media.webpage.document);
                } else if (message.media.webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.media.webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getPathToAttach(sizeFull);
                        }
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                return getPathToAttach(((TLRPC.TL_messageMediaInvoice) message.media).photo, true);
            }
        }
        return new File("");
    }

    public static File getPathToAttach(TLObject attach) {
        return getPathToAttach(attach, null, false);
    }

    public static File getPathToAttach(TLObject attach, boolean forceCache) {
        return getPathToAttach(attach, null, forceCache);
    }

    public static File getPathToAttach(TLObject attach, String ext, boolean forceCache) {
        return getPathToAttach(attach, null, ext, forceCache);
    }

    public static File getPathToAttach(TLObject attach, String size, String ext, boolean forceCache) {
        File dir = null;
        if (forceCache) {
            dir = getDirectory(MEDIA_DIR_CACHE);
        } else {
            if (attach instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) attach;
                if (document.key != null) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    if (MessageObject.isVoiceDocument(document)) {
                        dir = getDirectory(MEDIA_DIR_AUDIO);
                    } else if (MessageObject.isVideoDocument(document)) {
                        dir = getDirectory(MEDIA_DIR_VIDEO);
                    } else {
                        dir = getDirectory(MEDIA_DIR_DOCUMENT);
                    }
                }
            } else if (attach instanceof TLRPC.Photo) {
                TLRPC.PhotoSize photoSize = getClosestPhotoSizeWithSize(((TLRPC.Photo) attach).sizes, AndroidUtilities.getPhotoSize());
                return getPathToAttach(photoSize, ext, false);
            } else if (attach instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) attach;
                if (photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize) {
                    dir = null;
                } else if (photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0 || photoSize.size < 0) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(MEDIA_DIR_IMAGE);
                }
            } else if (attach instanceof TLRPC.TL_videoSize) {
                TLRPC.TL_videoSize videoSize = (TLRPC.TL_videoSize) attach;
                if (videoSize.location == null || videoSize.location.key != null || videoSize.location.volume_id == Integer.MIN_VALUE && videoSize.location.local_id < 0 || videoSize.size < 0) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(MEDIA_DIR_IMAGE);
                }
            } else if (attach instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation fileLocation = (TLRPC.FileLocation) attach;
                if (fileLocation.key != null || fileLocation.volume_id == Integer.MIN_VALUE && fileLocation.local_id < 0) {
                    dir = getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getDirectory(MEDIA_DIR_IMAGE);
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
        return new File(dir, getAttachFileName(attach, ext));
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
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || side > lastSide && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            } else {
                int currentSide = Math.max(obj.w, obj.h);
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || currentSide <= side && lastSide < currentSide) {
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
}
