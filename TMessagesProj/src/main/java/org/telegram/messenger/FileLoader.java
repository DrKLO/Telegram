/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import org.telegram.android.AndroidUtilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class FileLoader {

    public static interface FileLoaderDelegate {
        public abstract void fileUploadProgressChanged(String location, float progress, boolean isEncrypted);
        public abstract void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile);
        public abstract void fileDidFailedUpload(String location, boolean isEncrypted);
        public abstract void fileDidLoaded(String location, File finalFile, File tempFile);
        public abstract void fileDidFailedLoad(String location, int state);
        public abstract void fileLoadProgressChanged(String location, float progress);
    }

    public static final int MEDIA_DIR_IMAGE = 0;
    public static final int MEDIA_DIR_AUDIO = 1;
    public static final int MEDIA_DIR_VIDEO = 2;
    public static final int MEDIA_DIR_DOCUMENT = 3;
    public static final int MEDIA_DIR_CACHE = 4;

    private HashMap<Integer, File> mediaDirs = null;
    private volatile DispatchQueue fileLoaderQueue = new DispatchQueue("fileUploadQueue");

    private LinkedList<FileUploadOperation> uploadOperationQueue = new LinkedList<FileUploadOperation>();
    private LinkedList<FileUploadOperation> uploadSmallOperationQueue = new LinkedList<FileUploadOperation>();
    private LinkedList<FileLoadOperation> loadOperationQueue = new LinkedList<FileLoadOperation>();
    private LinkedList<FileLoadOperation> audioLoadOperationQueue = new LinkedList<FileLoadOperation>();
    private LinkedList<FileLoadOperation> photoLoadOperationQueue = new LinkedList<FileLoadOperation>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPaths = new ConcurrentHashMap<String, FileUploadOperation>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPathsEnc = new ConcurrentHashMap<String, FileUploadOperation>();
    private ConcurrentHashMap<String, FileLoadOperation> loadOperationPaths = new ConcurrentHashMap<String, FileLoadOperation>();
    private ConcurrentHashMap<String, Float> fileProgresses = new ConcurrentHashMap<String, Float>();
    private HashMap<String, Long> uploadSizes = new HashMap<String, Long>();

    private FileLoaderDelegate delegate = null;

    private int currentLoadOperationsCount = 0;
    private int currentAudioLoadOperationsCount = 0;
    private int currentPhotoLoadOperationsCount = 0;
    private int currentUploadOperationsCount = 0;
    private int currentUploadSmallOperationsCount = 0;

    private static volatile FileLoader Instance = null;
    public static FileLoader getInstance() {
        FileLoader localInstance = Instance;
        if (localInstance == null) {
            synchronized (FileLoader.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new FileLoader();
                }
            }
        }
        return localInstance;
    }

    public void setMediaDirs(HashMap<Integer, File> dirs) {
        mediaDirs = dirs;
    }

    public File getDirectory(int type) {
        File dir = mediaDirs.get(type);
        if (dir == null && type != MEDIA_DIR_CACHE) {
            dir = mediaDirs.get(MEDIA_DIR_CACHE);
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
                FileUploadOperation operation = null;
                if (!enc) {
                    operation = uploadOperationPaths.get(location);
                } else {
                    operation = uploadOperationPathsEnc.get(location);
                }
                uploadSizes.remove(location);
                if (operation != null) {
                    uploadOperationQueue.remove(operation);
                    uploadSmallOperationQueue.remove(operation);
                    operation.cancel();
                }
            }
        });
    }

    public Float getFileProgress(String location) {
        return fileProgresses.get(location);
    }

    public void checkUploadNewDataAvailable(final String location, final boolean encrypted, final long finalSize) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileUploadOperation operation = null;
                if (encrypted) {
                    operation = uploadOperationPathsEnc.get(location);
                } else {
                    operation = uploadOperationPaths.get(location);
                }
                if (operation != null) {
                    operation.checkNewDataAvailable(finalSize);
                } else if (finalSize != 0) {
                    uploadSizes.put(location, finalSize);
                }
            }
        });
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small) {
        uploadFile(location, encrypted, small, 0);
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int estimatedSize) {
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
                FileUploadOperation operation = new FileUploadOperation(location, encrypted, esimated);
                if (encrypted) {
                    uploadOperationPathsEnc.put(location, operation);
                } else {
                    uploadOperationPaths.put(location, operation);
                }
                operation.delegate = new FileUploadOperation.FileUploadOperationDelegate() {
                    @Override
                    public void didFinishUploadingFile(FileUploadOperation operation, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile) {
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
                                    delegate.fileDidUploaded(location, inputFile, inputEncryptedFile);
                                }
                                Utilities.stageQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        fileProgresses.remove(location);
                                    }
                                });
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
                                Utilities.stageQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        fileProgresses.remove(location);
                                    }
                                });
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
                        if (operation.state != 2) {
                            fileProgresses.put(location, progress);
                        }
                        if (delegate != null) {
                            delegate.fileUploadProgressChanged(location, progress, encrypted);
                        }
                    }
                };
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

    public void cancelLoadFile(TLRPC.Video video) {
        cancelLoadFile(video, null, null, null);
    }

    public void cancelLoadFile(TLRPC.Document document) {
        cancelLoadFile(null, document, null, null);
    }

    public void cancelLoadFile(TLRPC.Audio audio) {
        cancelLoadFile(null, null, audio, null);
    }

    public void cancelLoadFile(TLRPC.PhotoSize photo) {
        cancelLoadFile(null, null, null, photo.location);
    }

    public void cancelLoadFile(TLRPC.FileLocation location) {
        cancelLoadFile(null, null, null, location);
    }

    private void cancelLoadFile(final TLRPC.Video video, final TLRPC.Document document, final TLRPC.Audio audio, final TLRPC.FileLocation location) {
        if (video == null && location == null && document == null && audio == null) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (video != null) {
                    fileName = getAttachFileName(video);
                } else if (location != null) {
                    fileName = getAttachFileName(location);
                } else if (document != null) {
                    fileName = getAttachFileName(document);
                } else if (audio != null) {
                    fileName = getAttachFileName(audio);
                }
                if (fileName == null) {
                    return;
                }
                FileLoadOperation operation = loadOperationPaths.get(fileName);
                if (operation != null) {
                    loadOperationPaths.remove(fileName);
                    if (audio != null) {
                        audioLoadOperationQueue.remove(operation);
                    } else if (location != null) {
                        photoLoadOperationQueue.remove(operation);
                    } else {
                        loadOperationQueue.remove(operation);
                    }
                    operation.cancel();
                }
            }
        });
    }

    public boolean isLoadingFile(final String fileName) {
        final Semaphore semaphore = new Semaphore(0);
        final Boolean[] result = new Boolean[1];
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                result[0] = loadOperationPaths.containsKey(fileName);
                semaphore.release();
            }
        });
        try {
            semaphore.acquire();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return result[0];
    }

    public void loadFile(TLRPC.Video video, boolean force) {
        loadFile(video, null, null, null, 0, force, video != null && video.key != null);
    }

    public void loadFile(TLRPC.PhotoSize photo, boolean cacheOnly) {
        loadFile(null, null, null, photo.location, photo.size, false, cacheOnly || (photo != null && photo.size == 0 || photo.location.key != null));
    }

    public void loadFile(TLRPC.Document document, boolean force) {
        loadFile(null, document, null, null, 0, force, document != null && document.key != null);
    }

    public void loadFile(TLRPC.Audio audio, boolean force) {
        loadFile(null, null, audio, null, 0, false, audio != null && audio.key != null);
    }

    public void loadFile(TLRPC.FileLocation location, int size, boolean cacheOnly) {
        loadFile(null, null, null, location, size, true, cacheOnly || size == 0 || (location != null && location.key != null));
    }

    private void loadFile(final TLRPC.Video video, final TLRPC.Document document, final TLRPC.Audio audio, final TLRPC.FileLocation location, final int locationSize, final boolean force, final boolean cacheOnly) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (video != null) {
                    fileName = getAttachFileName(video);
                } else if (location != null) {
                    fileName = getAttachFileName(location);
                } else if (document != null) {
                    fileName = getAttachFileName(document);
                } else if (audio != null) {
                    fileName = getAttachFileName(audio);
                }
                if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {
                    return;
                }

                FileLoadOperation operation = null;
                operation = loadOperationPaths.get(fileName);
                if (operation != null) {
                    if (force) {
                        LinkedList<FileLoadOperation> downloadQueue = null;
                        if (audio != null) {
                            downloadQueue = audioLoadOperationQueue;
                        } else if (location != null) {
                            downloadQueue = photoLoadOperationQueue;
                        } else {
                            downloadQueue = loadOperationQueue;
                        }
                        if (downloadQueue != null) {
                            int index = downloadQueue.indexOf(operation);
                            if (index != -1) {
                                downloadQueue.remove(index);
                                downloadQueue.add(0, operation);
                                operation.setForceRequest(true);
                            }
                        }
                    }
                    return;
                }

                File tempDir = getDirectory(MEDIA_DIR_CACHE);
                File storeDir = tempDir;

                if (video != null) {
                    operation = new FileLoadOperation(video);
                    if (!cacheOnly) {
                        storeDir = getDirectory(MEDIA_DIR_VIDEO);
                    }
                } else if (location != null) {
                    operation = new FileLoadOperation(location, locationSize);
                    if (!cacheOnly) {
                        storeDir = getDirectory(MEDIA_DIR_IMAGE);
                    }
                } else if (document != null) {
                    operation = new FileLoadOperation(document);
                    if (!cacheOnly) {
                        storeDir = getDirectory(MEDIA_DIR_DOCUMENT);
                    }
                } else if (audio != null) {
                    operation = new FileLoadOperation(audio);
                    if (!cacheOnly) {
                        storeDir = getDirectory(MEDIA_DIR_AUDIO);
                    }
                }
                operation.setPaths(storeDir, tempDir);

                final String arg1 = fileName;
                loadOperationPaths.put(fileName, operation);
                operation.setDelegate(new FileLoadOperation.FileLoadOperationDelegate() {
                    @Override
                    public void didFinishLoadingFile(FileLoadOperation operation, File finalFile, File tempFile) {
                        if (delegate != null) {
                            delegate.fileDidLoaded(arg1, finalFile, tempFile);
                        }
                        checkDownloadQueue(audio, location, arg1);
                    }

                    @Override
                    public void didFailedLoadingFile(FileLoadOperation operation, int canceled) {
                        checkDownloadQueue(audio, location, arg1);
                        if (delegate != null) {
                            delegate.fileDidFailedLoad(arg1, canceled);
                        }
                    }

                    @Override
                    public void didChangedLoadProgress(FileLoadOperation operation, float progress) {
                        fileProgresses.put(arg1, progress);
                        if (delegate != null) {
                            delegate.fileLoadProgressChanged(arg1, progress);
                        }
                    }
                });
                int maxCount = force ? 3 : 1;
                if (audio != null) {
                    if (currentAudioLoadOperationsCount < maxCount) {
                        currentAudioLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            audioLoadOperationQueue.add(0, operation);
                        } else {
                            audioLoadOperationQueue.add(operation);
                        }
                    }
                } else if (location != null) {
                    if (currentPhotoLoadOperationsCount < maxCount) {
                        currentPhotoLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            photoLoadOperationQueue.add(0, operation);
                        } else {
                            photoLoadOperationQueue.add(operation);
                        }
                    }
                } else {
                    if (currentLoadOperationsCount < maxCount) {
                        currentLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            loadOperationQueue.add(0, operation);
                        } else {
                            loadOperationQueue.add(operation);
                        }
                    }
                }
            }
        });
    }

    private void checkDownloadQueue(final TLRPC.Audio audio, final TLRPC.FileLocation location, final String arg1) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadOperationPaths.remove(arg1);
                FileLoadOperation operation = null;
                if (audio != null) {
                    currentAudioLoadOperationsCount--;
                    if (!audioLoadOperationQueue.isEmpty()) {
                        operation = audioLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentAudioLoadOperationsCount < maxCount) {
                            operation = audioLoadOperationQueue.poll();
                            if (operation != null) {
                                currentAudioLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                } else if (location != null) {
                    currentPhotoLoadOperationsCount--;
                    if (!photoLoadOperationQueue.isEmpty()) {
                        operation = photoLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentPhotoLoadOperationsCount < maxCount) {
                            operation = photoLoadOperationQueue.poll();
                            if (operation != null) {
                                currentPhotoLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                } else {
                    currentLoadOperationsCount--;
                    if (!loadOperationQueue.isEmpty()) {
                        operation = loadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentLoadOperationsCount < maxCount) {
                            operation = loadOperationQueue.poll();
                            if (operation != null) {
                                currentLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                }
            }
        });
        fileProgresses.remove(arg1);
    }

    public void setDelegate(FileLoaderDelegate delegate) {
        this.delegate = delegate;
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
            if (message.media instanceof TLRPC.TL_messageMediaVideo) {
                return getPathToAttach(message.media.video);
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                return getPathToAttach(message.media.document);
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                return getPathToAttach(message.media.audio);
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.media.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull);
                    }
                }
            }
        }
        return new File("");
    }

    public static File getExistPathToAttach(TLObject attach) {
        File path = getInstance().getDirectory(MEDIA_DIR_CACHE);
        String fileName = getAttachFileName(attach);
        File attachPath = new File(path, fileName);
        if (attachPath.exists()) {
            return attachPath;
        }
        return getPathToAttach(attach);
    }

    public static File getPathToAttach(TLObject attach) {
        return getPathToAttach(attach, false);
    }

    public static File getPathToAttach(TLObject attach, boolean forceCache) {
        File dir = null;
        if (attach instanceof TLRPC.Video) {
            TLRPC.Video video = (TLRPC.Video)attach;
            if (forceCache || video.key != null) {
                dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
            } else {
                dir = getInstance().getDirectory(MEDIA_DIR_VIDEO);
            }
        } else if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document)attach;
            if (forceCache || document.key != null) {
                dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
            } else {
                dir = getInstance().getDirectory(MEDIA_DIR_DOCUMENT);
            }
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize)attach;
            if (forceCache || photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0) {
                dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
            } else {
                dir = getInstance().getDirectory(MEDIA_DIR_IMAGE);
            }
        } else if (attach instanceof TLRPC.Audio) {
            TLRPC.Audio audio = (TLRPC.Audio)attach;
            if (forceCache || audio.key != null) {
                dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
            } else {
                dir = getInstance().getDirectory(MEDIA_DIR_AUDIO);
            }
        } else if (attach instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation fileLocation = (TLRPC.FileLocation)attach;
            if (forceCache || fileLocation.key != null || fileLocation.volume_id == Integer.MIN_VALUE && fileLocation.local_id < 0) {
                dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
            } else {
                dir = getInstance().getDirectory(MEDIA_DIR_IMAGE);
            }
        }
        if (dir == null) {
            return new File("");
        }
        return new File(dir, getAttachFileName(attach));
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side) {
        if (sizes == null) {
            return null;
        }
        int lastSide = 0;
        TLRPC.PhotoSize closestObject = null;
        for (TLRPC.PhotoSize obj : sizes) {
            if (obj == null) {
                continue;
            }
            int currentSide = obj.w >= obj.h ? obj.w : obj.h;
            if (closestObject == null || closestObject instanceof TLRPC.TL_photoCachedSize || currentSide <= side && lastSide < currentSide) {
                closestObject = obj;
                lastSide = currentSide;
            }
        }
        return closestObject;
    }

    public static String getAttachFileName(TLObject attach) {
        if (attach instanceof TLRPC.Video) {
            TLRPC.Video video = (TLRPC.Video)attach;
            return video.dc_id + "_" + video.id + ".mp4";
        } else if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document)attach;
            String ext = document.file_name;
            int idx = -1;
            if (ext == null || (idx = ext.lastIndexOf(".")) == -1) {
                ext = "";
            } else {
                ext = ext.substring(idx);
            }
            if (ext.length() > 1) {
                return document.dc_id + "_" + document.id + ext;
            } else {
                return document.dc_id + "_" + document.id;
            }
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize)attach;
            if (photo.location == null) {
                return "";
            }
            return photo.location.volume_id + "_" + photo.location.local_id + ".jpg";
        } else if (attach instanceof TLRPC.Audio) {
            TLRPC.Audio audio = (TLRPC.Audio)attach;
            return audio.dc_id + "_" + audio.id + ".ogg";
        } else if (attach instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation location = (TLRPC.FileLocation)attach;
            return location.volume_id + "_" + location.local_id + ".jpg";
        }
        return "";
    }

    public void deleteFiles(final ArrayList<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (File file : files) {
                    if (file.exists()) {
                        try {
                            if (!file.delete()) {
                                file.deleteOnExit();
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            }
        });
    }
}
