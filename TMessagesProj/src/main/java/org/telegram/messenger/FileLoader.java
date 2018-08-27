/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class FileLoader {

    public interface FileLoaderDelegate {
        void fileUploadProgressChanged(String location, float progress, boolean isEncrypted);
        void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv, long totalFileSize);
        void fileDidFailedUpload(String location, boolean isEncrypted);
        void fileDidLoaded(String location, File finalFile, int type);
        void fileDidFailedLoad(String location, int state);
        void fileLoadProgressChanged(String location, float progress);
    }

    public static final int MEDIA_DIR_IMAGE = 0;
    public static final int MEDIA_DIR_AUDIO = 1;
    public static final int MEDIA_DIR_VIDEO = 2;
    public static final int MEDIA_DIR_DOCUMENT = 3;
    public static final int MEDIA_DIR_CACHE = 4;

    private volatile static DispatchQueue fileLoaderQueue = new DispatchQueue("fileUploadQueue");

    private LinkedList<FileUploadOperation> uploadOperationQueue = new LinkedList<>();
    private LinkedList<FileUploadOperation> uploadSmallOperationQueue = new LinkedList<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPaths = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPathsEnc = new ConcurrentHashMap<>();
    private int currentUploadOperationsCount = 0;
    private int currentUploadSmallOperationsCount = 0;

    private SparseArray<LinkedList<FileLoadOperation>> loadOperationQueues = new SparseArray<>();
    private SparseArray<LinkedList<FileLoadOperation>> audioLoadOperationQueues = new SparseArray<>();
    private SparseArray<LinkedList<FileLoadOperation>> photoLoadOperationQueues = new SparseArray<>();
    private SparseIntArray currentLoadOperationsCount = new SparseIntArray();
    private SparseIntArray currentAudioLoadOperationsCount = new SparseIntArray();
    private SparseIntArray currentPhotoLoadOperationsCount = new SparseIntArray();

    private ConcurrentHashMap<String, FileLoadOperation> loadOperationPaths = new ConcurrentHashMap<>();
    private ArrayList<FileLoadOperation> activeFileLoadOperation = new ArrayList<>();
    private ConcurrentHashMap<String, Boolean> loadOperationPathsUI = new ConcurrentHashMap<>(10, 1, 2);
    private HashMap<String, Long> uploadSizes = new HashMap<>();

    private static SparseArray<File> mediaDirs = null;
    private FileLoaderDelegate delegate = null;

    private int currentAccount;
    private static volatile FileLoader Instance[] = new FileLoader[UserConfig.MAX_ACCOUNT_COUNT];
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
        currentAccount = instance;
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
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            //don't promt
        }
        return dir;
    }

    public void cancelUploadFile(final String location, final boolean enc) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }

    public void checkUploadNewDataAvailable(final String location, final boolean encrypted, final long newAvailableSize, final long finalSize) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }

    public void onNetworkChanged(final boolean slow) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (ConcurrentHashMap.Entry<String, FileUploadOperation> entry : uploadOperationPaths.entrySet()) {
                    entry.getValue().onNetworkChanged(slow);
                }
                for (ConcurrentHashMap.Entry<String, FileUploadOperation> entry : uploadOperationPathsEnc.entrySet()) {
                    entry.getValue().onNetworkChanged(slow);
                }
            }
        });
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int type) {
        uploadFile(location, encrypted, small, 0, type);
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int estimatedSize, final int type) {
        if (location == null) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
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
                if (encrypted) {
                    uploadOperationPathsEnc.put(location, operation);
                } else {
                    uploadOperationPaths.put(location, operation);
                }
                operation.setDelegate(new FileUploadOperation.FileUploadOperationDelegate() {
                    @Override
                    public void didFinishUploadingFile(final FileUploadOperation operation, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv) {
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                if (encrypted) {
                                    uploadOperationPathsEnc.remove(location);
                                } else {
                                    uploadOperationPaths.remove(location);
                                }
                                if (small) {
                                    currentUploadSmallOperationsCount--;
                                    if (currentUploadSmallOperationsCount < 1) {
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 1) {
                                        FileUploadOperation operation = uploadOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                                if (delegate != null) {
                                    delegate.fileDidUploaded(location, inputFile, inputEncryptedFile, key, iv, operation.getTotalFileSize());
                                }
                            }
                        });
                    }

                    @Override
                    public void didFailedUploadingFile(final FileUploadOperation operation) {
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
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
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 1) {
                                        FileUploadOperation operation = uploadOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void didChangedUploadProgress(FileUploadOperation operation, final float progress) {
                        if (delegate != null) {
                            delegate.fileUploadProgressChanged(location, progress, encrypted);
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
            }
        });
    }

    private LinkedList<FileLoadOperation> getAudioLoadOperationQueue(int datacenterId) {
        LinkedList<FileLoadOperation> audioLoadOperationQueue = audioLoadOperationQueues.get(datacenterId);
        if (audioLoadOperationQueue == null) {
            audioLoadOperationQueue = new LinkedList<>();
            audioLoadOperationQueues.put(datacenterId, audioLoadOperationQueue);
        }
        return audioLoadOperationQueue;
    }

    private LinkedList<FileLoadOperation> getPhotoLoadOperationQueue(int datacenterId) {
        LinkedList<FileLoadOperation> photoLoadOperationQueue = photoLoadOperationQueues.get(datacenterId);
        if (photoLoadOperationQueue == null) {
            photoLoadOperationQueue = new LinkedList<>();
            photoLoadOperationQueues.put(datacenterId, photoLoadOperationQueue);
        }
        return photoLoadOperationQueue;
    }

    private LinkedList<FileLoadOperation> getLoadOperationQueue(int datacenterId) {
        LinkedList<FileLoadOperation> loadOperationQueue = loadOperationQueues.get(datacenterId);
        if (loadOperationQueue == null) {
            loadOperationQueue = new LinkedList<>();
            loadOperationQueues.put(datacenterId, loadOperationQueue);
        }
        return loadOperationQueue;
    }

    public void cancelLoadFile(TLRPC.Document document) {
        cancelLoadFile(document, null, null, null, null);
    }

    public void cancelLoadFile(SecureDocument document) {
        cancelLoadFile(null, document, null, null, null);
    }

    public void cancelLoadFile(WebFile document) {
        cancelLoadFile(null, null, document, null, null);
    }

    public void cancelLoadFile(TLRPC.PhotoSize photo) {
        cancelLoadFile(null, null, null, photo.location, null);
    }

    public void cancelLoadFile(TLRPC.FileLocation location, String ext) {
        cancelLoadFile(null, null, null, location, ext);
    }

    private void cancelLoadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt) {
        if (location == null && document == null && webDocument == null && secureDocument == null) {
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
            fileName = null;
        }
        if (fileName == null) {
            return;
        }
        loadOperationPathsUI.remove(fileName);
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileLoadOperation operation = loadOperationPaths.remove(fileName);
                if (operation != null) {
                    int datacenterId = operation.getDatacenterId();
                    if (MessageObject.isVoiceDocument(document) || MessageObject.isVoiceWebDocument(webDocument)) {
                        LinkedList<FileLoadOperation> audioLoadOperationQueue = getAudioLoadOperationQueue(datacenterId);
                        if (!audioLoadOperationQueue.remove(operation)) {
                            currentAudioLoadOperationsCount.put(datacenterId, currentAudioLoadOperationsCount.get(datacenterId) - 1);
                        }
                    } else if (secureDocument != null || location != null || MessageObject.isImageWebDocument(webDocument)) {
                        LinkedList<FileLoadOperation> photoLoadOperationQueue = getPhotoLoadOperationQueue(datacenterId);
                        if (!photoLoadOperationQueue.remove(operation)) {
                            currentPhotoLoadOperationsCount.put(datacenterId, currentPhotoLoadOperationsCount.get(datacenterId) - 1);
                        }
                    } else {
                        LinkedList<FileLoadOperation> loadOperationQueue = getLoadOperationQueue(datacenterId);
                        if (!loadOperationQueue.remove(operation)) {
                            currentLoadOperationsCount.put(datacenterId, currentLoadOperationsCount.get(datacenterId) - 1);
                        }
                        activeFileLoadOperation.remove(operation);
                    }
                    operation.cancel();
                }
            }
        });
    }

    public boolean isLoadingFile(final String fileName) {
        return loadOperationPathsUI.containsKey(fileName);
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

    public void loadFile(TLRPC.PhotoSize photo, String ext, int cacheType) {
        if (photo == null) {
            return;
        }
        if (cacheType == 0 && photo != null && (photo.size == 0 || photo.location.key != null)) {
            cacheType = 1;
        }
        loadFile(null, null, null, photo.location, ext, photo.size, false, cacheType);
    }

    public void loadFile(SecureDocument secureDocument, boolean force) {
        if (secureDocument == null) {
            return;
        }
        loadFile(null, secureDocument, null, null, null, 0, force, 1);
    }

    public void loadFile(TLRPC.Document document, boolean force, int cacheType) {
        if (document == null) {
            return;
        }
        if (cacheType == 0 && document != null && document.key != null) {
            cacheType = 1;
        }
        loadFile(document, null, null, null, null, 0, force, cacheType);
    }

    public void loadFile(WebFile document, boolean force, int cacheType) {
        loadFile(null, null, document, null, null, 0, force, cacheType);
    }

    public void loadFile(TLRPC.FileLocation location, String ext, int size, int cacheType) {
        if (location == null) {
            return;
        }
        if (cacheType == 0 && (size == 0 || location != null && location.key != null)) {
            cacheType = 1;
        }
        loadFile(null, null, null, location, ext, size, true, cacheType);
    }

    private void pauseCurrentFileLoadOperations(FileLoadOperation newOperation) {
        for (int a = 0; a < activeFileLoadOperation.size(); a++) {
            FileLoadOperation operation = activeFileLoadOperation.get(a);
            if (operation == newOperation) {
                continue;
            }
            activeFileLoadOperation.remove(operation);
            a--;
            operation.pause();
            int datacenterId = operation.getDatacenterId();
            LinkedList<FileLoadOperation> loadOperationQueue = getLoadOperationQueue(datacenterId);
            loadOperationQueue.add(0, operation);
            if (operation.wasStarted()) {
                currentLoadOperationsCount.put(datacenterId, currentLoadOperationsCount.get(datacenterId) - 1);
            }
        }
    }

    private FileLoadOperation loadFileInternal(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt, final int locationSize, final boolean force, final FileStreamLoadOperation stream, final int streamOffset, final int cacheType) {
        String fileName = null;
        if (location != null) {
            fileName = getAttachFileName(location, locationExt);
        } else if (secureDocument != null) {
            fileName = getAttachFileName(secureDocument);
        } else if (document != null) {
            fileName = getAttachFileName(document);
        } else if (webDocument != null) {
            fileName = getAttachFileName(webDocument);
        }
        if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {
            return null;
        }
        if (!TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            loadOperationPathsUI.put(fileName, true);
        }

        FileLoadOperation operation;
        operation = loadOperationPaths.get(fileName);
        if (operation != null) {
            if (streamOffset != 0 || force) {
                int datacenterId = operation.getDatacenterId();

                LinkedList<FileLoadOperation> audioLoadOperationQueue = getAudioLoadOperationQueue(datacenterId);
                LinkedList<FileLoadOperation> photoLoadOperationQueue = getPhotoLoadOperationQueue(datacenterId);
                LinkedList<FileLoadOperation> loadOperationQueue = getLoadOperationQueue(datacenterId);

                operation.setForceRequest(true);
                LinkedList<FileLoadOperation> downloadQueue;
                if (MessageObject.isVoiceDocument(document) || MessageObject.isVoiceWebDocument(webDocument)) {
                    downloadQueue = audioLoadOperationQueue;
                } else if (secureDocument != null || location != null || MessageObject.isImageWebDocument(webDocument)) {
                    downloadQueue = photoLoadOperationQueue;
                } else {
                    downloadQueue = loadOperationQueue;
                }
                if (downloadQueue != null) {
                    int index = downloadQueue.indexOf(operation);
                    if (index > 0) {
                        downloadQueue.remove(index);
                        if (streamOffset != 0) {
                            if (downloadQueue == audioLoadOperationQueue) {
                                if (operation.start(stream, streamOffset)) {
                                    currentAudioLoadOperationsCount.put(datacenterId, currentAudioLoadOperationsCount.get(datacenterId) + 1);
                                }
                            } else if (downloadQueue == photoLoadOperationQueue) {
                                if (operation.start(stream, streamOffset)) {
                                    currentPhotoLoadOperationsCount.put(datacenterId, currentPhotoLoadOperationsCount.get(datacenterId) + 1);
                                }
                            } else {
                                if (operation.start(stream, streamOffset)) {
                                    currentLoadOperationsCount.put(datacenterId, currentLoadOperationsCount.get(datacenterId) + 1);
                                }
                                if (operation.wasStarted() && !activeFileLoadOperation.contains(operation)) {
                                    if (stream != null) {
                                        pauseCurrentFileLoadOperations(operation);
                                    }
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
                        operation.start(stream, streamOffset);
                        if (downloadQueue == loadOperationQueue && !activeFileLoadOperation.contains(operation)) {
                            activeFileLoadOperation.add(operation);
                        }
                    }
                }
            }
            return operation;
        }

        File tempDir = getDirectory(MEDIA_DIR_CACHE);
        File storeDir = tempDir;
        int type = MEDIA_DIR_CACHE;

        if (secureDocument != null) {
            operation = new FileLoadOperation(secureDocument);
            type = MEDIA_DIR_DOCUMENT;
        } else if (location != null) {
            operation = new FileLoadOperation(location, locationExt, locationSize);
            type = MEDIA_DIR_IMAGE;
        } else if (document != null) {
            operation = new FileLoadOperation(document);
            if (MessageObject.isVoiceDocument(document)) {
                type = MEDIA_DIR_AUDIO;
            } else if (MessageObject.isVideoDocument(document)) {
                type = MEDIA_DIR_VIDEO;
            } else {
                type = MEDIA_DIR_DOCUMENT;
            }
        } else if (webDocument != null) {
            operation = new FileLoadOperation(currentAccount, webDocument);
            if (MessageObject.isVoiceWebDocument(webDocument)) {
                type = MEDIA_DIR_AUDIO;
            } else if (MessageObject.isVideoWebDocument(webDocument)) {
                type = MEDIA_DIR_VIDEO;
            } else if (MessageObject.isImageWebDocument(webDocument)) {
                type = MEDIA_DIR_IMAGE;
            } else {
                type = MEDIA_DIR_DOCUMENT;
            }
        }
        if (cacheType == 0) {
            storeDir = getDirectory(type);
        } else if (cacheType == 2) {
            operation.setEncryptFile(true);
        }
        operation.setPaths(currentAccount, storeDir, tempDir);

        final String finalFileName = fileName;
        final int finalType = type;
        FileLoadOperation.FileLoadOperationDelegate fileLoadOperationDelegate = new FileLoadOperation.FileLoadOperationDelegate() {
            @Override
            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {
                loadOperationPathsUI.remove(finalFileName);
                if (delegate != null) {
                    delegate.fileDidLoaded(finalFileName, finalFile, finalType);
                }
                checkDownloadQueue(operation.getDatacenterId(), document, webDocument, location, finalFileName);
            }

            @Override
            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {
                loadOperationPathsUI.remove(finalFileName);
                checkDownloadQueue(operation.getDatacenterId(), document, webDocument, location, finalFileName);
                if (delegate != null) {
                    delegate.fileDidFailedLoad(finalFileName, reason);
                }
            }

            @Override
            public void didChangedLoadProgress(FileLoadOperation operation, float progress) {
                if (delegate != null) {
                    delegate.fileLoadProgressChanged(finalFileName, progress);
                }
            }
        };
        operation.setDelegate(fileLoadOperationDelegate);

        int datacenterId = operation.getDatacenterId();

        LinkedList<FileLoadOperation> audioLoadOperationQueue = getAudioLoadOperationQueue(datacenterId);
        LinkedList<FileLoadOperation> photoLoadOperationQueue = getPhotoLoadOperationQueue(datacenterId);
        LinkedList<FileLoadOperation> loadOperationQueue = getLoadOperationQueue(datacenterId);

        loadOperationPaths.put(fileName, operation);
        int maxCount = force ? 3 : 1;
        if (type == MEDIA_DIR_AUDIO) {
            int count = currentAudioLoadOperationsCount.get(datacenterId);
            if (streamOffset != 0 || count < maxCount) {
                if (operation.start(stream, streamOffset)) {
                    currentAudioLoadOperationsCount.put(datacenterId, count + 1);
                }
            } else {
                if (force) {
                    audioLoadOperationQueue.add(0, operation);
                } else {
                    audioLoadOperationQueue.add(operation);
                }
            }
        } else if (location != null || MessageObject.isImageWebDocument(webDocument)) {
            int count = currentPhotoLoadOperationsCount.get(datacenterId);
            if (streamOffset != 0 || count < maxCount) {
                if (operation.start(stream, streamOffset)) {
                    currentPhotoLoadOperationsCount.put(datacenterId, count + 1);
                }
            } else {
                if (force) {
                    photoLoadOperationQueue.add(0, operation);
                } else {
                    photoLoadOperationQueue.add(operation);
                }
            }
        } else {
            int count = currentLoadOperationsCount.get(datacenterId);
            if (streamOffset != 0 || count < maxCount) {
                if (operation.start(stream, streamOffset)) {
                    currentLoadOperationsCount.put(datacenterId, count + 1);
                    activeFileLoadOperation.add(operation);
                }
                if (operation.wasStarted() && stream != null) {
                    pauseCurrentFileLoadOperations(operation);
                }
            } else {
                if (force) {
                    loadOperationQueue.add(0, operation);
                } else {
                    loadOperationQueue.add(operation);
                }
            }
        }
        return operation;
    }

    private void loadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt, final int locationSize, final boolean force, final int cacheType) {
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
        if (!TextUtils.isEmpty(fileName) && !fileName.contains("" + Integer.MIN_VALUE)) {
            loadOperationPathsUI.put(fileName, true);
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadFileInternal(document, secureDocument, webDocument, location, locationExt, locationSize, force, null, 0, cacheType);
            }
        });
    }

    protected FileLoadOperation loadStreamFile(final FileStreamLoadOperation stream, final TLRPC.Document document, final int offset) {
        final CountDownLatch semaphore = new CountDownLatch(1);
        final FileLoadOperation[] result = new FileLoadOperation[1];
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                result[0] = loadFileInternal(document, null, null, null, null, 0, true, stream, offset, 0);
                semaphore.countDown();
            }
        });
        try {
            semaphore.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    private void checkDownloadQueue(final int datacenterId, final TLRPC.Document document, final WebFile webDocument, final TLRPC.FileLocation location, final String arg1) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                LinkedList<FileLoadOperation> audioLoadOperationQueue = getAudioLoadOperationQueue(datacenterId);
                LinkedList<FileLoadOperation> photoLoadOperationQueue = getPhotoLoadOperationQueue(datacenterId);
                LinkedList<FileLoadOperation> loadOperationQueue = getLoadOperationQueue(datacenterId);

                FileLoadOperation operation = loadOperationPaths.remove(arg1);
                if (MessageObject.isVoiceDocument(document) || MessageObject.isVoiceWebDocument(webDocument)) {
                    int count = currentAudioLoadOperationsCount.get(datacenterId);
                    if (operation != null) {
                        if (operation.wasStarted()) {
                            count--;
                            currentAudioLoadOperationsCount.put(datacenterId, count);
                        } else {
                            audioLoadOperationQueue.remove(operation);
                        }
                    }
                    while (!audioLoadOperationQueue.isEmpty()) {
                        operation = audioLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (count < maxCount) {
                            operation = audioLoadOperationQueue.poll();
                            if (operation != null && operation.start()) {
                                count++;
                                currentAudioLoadOperationsCount.put(datacenterId, count);
                            }
                        } else {
                            break;
                        }
                    }
                } else if (location != null || MessageObject.isImageWebDocument(webDocument)) {
                    int count = currentPhotoLoadOperationsCount.get(datacenterId);
                    if (operation != null) {
                        if (operation.wasStarted()) {
                            count--;
                            currentPhotoLoadOperationsCount.put(datacenterId, count);
                        } else {
                            photoLoadOperationQueue.remove(operation);
                        }
                    }
                    while (!photoLoadOperationQueue.isEmpty()) {
                        operation = photoLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (count < maxCount) {
                            operation = photoLoadOperationQueue.poll();
                            if (operation != null && operation.start()) {
                                count++;
                                currentPhotoLoadOperationsCount.put(datacenterId, count);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    int count = currentLoadOperationsCount.get(datacenterId);
                    if (operation != null) {
                        if (operation.wasStarted()) {
                            count--;
                            currentLoadOperationsCount.put(datacenterId, count);
                        } else {
                            loadOperationQueue.remove(operation);
                        }
                        activeFileLoadOperation.remove(operation);
                    }
                    while (!loadOperationQueue.isEmpty()) {
                        operation = loadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (count < maxCount) {
                            operation = loadOperationQueue.poll();
                            if (operation != null && operation.start()) {
                                count++;
                                currentLoadOperationsCount.put(datacenterId, count);
                                if (!activeFileLoadOperation.contains(operation)) {
                                    activeFileLoadOperation.add(operation);
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        });
    }

    public void setDelegate(FileLoaderDelegate delegate) {
        this.delegate = delegate;
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
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
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
                } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                    return getAttachFileName(((TLRPC.TL_messageMediaInvoice) message.media).photo);
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                TLRPC.WebDocument document = ((TLRPC.TL_messageMediaInvoice) message.media).photo;
                if (document != null) {
                    return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getExtensionByMime(document.mime_type));
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
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
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
            } else if (attach instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) attach;
                if (photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0 || photoSize.size < 0) {
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

    public static FileStreamLoadOperation getStreamLoadOperation(TransferListener listener) {
        return new FileStreamLoadOperation(listener);
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side) {
        return getClosestPhotoSizeWithSize(sizes, side, false);
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side, boolean byMinSide) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        int lastSide = 0;
        TLRPC.PhotoSize closestObject = null;
        for (int a = 0; a < sizes.size(); a++) {
            TLRPC.PhotoSize obj = sizes.get(a);
            if (obj == null) {
                continue;
            }
            if (byMinSide) {
                int currentSide = obj.h >= obj.w ? obj.w : obj.h;
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || side > lastSide && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            } else {
                int currentSide = obj.w >= obj.h ? obj.w : obj.h;
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || currentSide <= side && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            }
        }
        return closestObject;
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
            fileName = fileName.replaceAll("[\u0001-\u001f<>:\"/\\\\|?*\u007f]+", "").trim();
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

    public static String getExtensionByMime(String mime) {
        int index;
        if ((index = mime.lastIndexOf('/')) != -1) {
            return mime.substring(index + 1);
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
        if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) attach;
            String docExt = null;
            if (docExt == null) {
                docExt = getDocumentFileName(document);
                int idx;
                if (docExt == null || (idx = docExt.lastIndexOf('.')) == -1) {
                    docExt = "";
                } else {
                    docExt = docExt.substring(idx);
                }
            }
            if (docExt.length() <= 1) {
                if (document.mime_type != null) {
                    switch (document.mime_type) {
                        case "video/mp4":
                            docExt = ".mp4";
                            break;
                        case "audio/ogg":
                            docExt = ".ogg";
                            break;
                        default:
                            docExt = "";
                            break;
                    }
                } else {
                    docExt = "";
                }
            }
            if (document.version == 0) {
                if (docExt.length() > 1) {
                    return document.dc_id + "_" + document.id + docExt;
                } else {
                    return document.dc_id + "_" + document.id;
                }
            } else {
                if (docExt.length() > 1) {
                    return document.dc_id + "_" + document.id + "_" + document.version + docExt;
                } else {
                    return document.dc_id + "_" + document.id + "_" + document.version;
                }
            }
        } else if (attach instanceof SecureDocument) {
            SecureDocument secureDocument = (SecureDocument) attach;
            return secureDocument.secureFile.dc_id + "_" + secureDocument.secureFile.id + ".jpg";
        } else if (attach instanceof TLRPC.TL_secureFile) {
            TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) attach;
            return secureFile.dc_id + "_" + secureFile.id + ".jpg";
        } else if (attach instanceof WebFile) {
            WebFile document = (WebFile) attach;
            return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getExtensionByMime(document.mime_type));
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize) attach;
            if (photo.location == null || photo.location instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            return photo.location.volume_id + "_" + photo.location.local_id + "." + (ext != null ? ext : "jpg");
        } else if (attach instanceof TLRPC.FileLocation) {
            if (attach instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            TLRPC.FileLocation location = (TLRPC.FileLocation) attach;
            return location.volume_id + "_" + location.local_id + "." + (ext != null ? ext : "jpg");
        } else if (attach instanceof TLRPC.Photo) {
            TLRPC.FileLocation location = (TLRPC.FileLocation) attach;
            return location.volume_id + "_" + location.local_id + "." + (ext != null ? ext : "jpg");
        }
        return "";
    }

    public void deleteFiles(final ArrayList<File> files, final int type) {
        if (files == null || files.isEmpty()) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }
}
