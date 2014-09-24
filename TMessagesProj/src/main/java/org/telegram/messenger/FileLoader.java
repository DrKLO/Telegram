/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.io.File;
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
        public abstract void fileDidFailedLoad(String location, boolean canceled);
        public abstract void fileLoadProgressChanged(String location, float progress);
        public abstract File getCacheDir();
    }

    protected File destinationDir = null;
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
                                    if (currentUploadSmallOperationsCount < 2) {
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 2) {
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
                                    if (currentUploadSmallOperationsCount < 2) {
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 2) {
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
                    if (currentUploadSmallOperationsCount < 2) {
                        currentUploadSmallOperationsCount++;
                        operation.start();
                    } else {
                        uploadSmallOperationQueue.add(operation);
                    }
                } else {
                    if (currentUploadOperationsCount < 2) {
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

    public void loadFile(TLRPC.Video video) {
        loadFile(video, null, null, null, 0, false);
    }

    public void loadFile(TLRPC.PhotoSize photo) {
        loadFile(null, null, null, photo.location, photo.size, false);
    }

    public void loadFile(TLRPC.Document document) {
        loadFile(null, document, null, null, 0, false);
    }

    public void loadFile(TLRPC.Audio audio, boolean force) {
        loadFile(null, null, audio, null, 0, false);
    }

    public void loadFile(TLRPC.FileLocation location, int size) {
        loadFile(null, null, null, location, size, true);
    }

    private void loadFile(final TLRPC.Video video, final TLRPC.Document document, final TLRPC.Audio audio, final TLRPC.FileLocation location, final int locationSize, final boolean force) {
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
                            }
                        }
                    }
                    return;
                }

                if (video != null) {
                    operation = new FileLoadOperation(video);
                } else if (location != null) {
                    operation = new FileLoadOperation(location, locationSize);
                } else if (document != null) {
                    operation = new FileLoadOperation(document);
                } else if (audio != null) {
                    operation = new FileLoadOperation(audio);
                }

                final String arg1 = fileName;
                loadOperationPaths.put(fileName, operation);
                operation.setDelegate(new FileLoadOperation.FileLoadOperationDelegate() {
                    @Override
                    public void didFinishLoadingFile(FileLoadOperation operation, File finalFile, File tempFile) {
                        if (delegate != null) {
                            delegate.fileDidLoaded(arg1, finalFile, tempFile);
                        }
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                loadOperationPaths.remove(arg1);
                                if (audio != null) {
                                    currentAudioLoadOperationsCount--;
                                    if (currentAudioLoadOperationsCount < 2) {
                                        FileLoadOperation operation = audioLoadOperationQueue.poll();
                                        if (operation != null) {
                                            currentAudioLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else if (location != null) {
                                    currentPhotoLoadOperationsCount--;
                                    if (currentPhotoLoadOperationsCount < 2) {
                                        FileLoadOperation operation = photoLoadOperationQueue.poll();
                                        if (operation != null) {
                                            currentPhotoLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentLoadOperationsCount--;
                                    if (currentLoadOperationsCount < 2) {
                                        FileLoadOperation operation = loadOperationQueue.poll();
                                        if (operation != null) {
                                            currentLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                            }
                        });
                        fileProgresses.remove(arg1);
                    }

                    @Override
                    public void didFailedLoadingFile(FileLoadOperation operation, boolean canceled) {
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                loadOperationPaths.remove(arg1);
                                if (audio != null) {
                                    currentAudioLoadOperationsCount--;
                                    if (currentAudioLoadOperationsCount < 2) {
                                        FileLoadOperation operation = audioLoadOperationQueue.poll();
                                        if (operation != null) {
                                            currentAudioLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else if (location != null) {
                                    currentPhotoLoadOperationsCount--;
                                    if (currentPhotoLoadOperationsCount < 2) {
                                        FileLoadOperation operation = photoLoadOperationQueue.poll();
                                        if (operation != null) {
                                            currentPhotoLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentLoadOperationsCount--;
                                    if (currentLoadOperationsCount < 2) {
                                        FileLoadOperation operation = loadOperationQueue.poll();
                                        if (operation != null) {
                                            currentLoadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                            }
                        });
                        fileProgresses.remove(arg1);
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
                if (audio != null) {
                    if (currentAudioLoadOperationsCount < 2) {
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
                    if (currentPhotoLoadOperationsCount < 2) {
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
                    if (currentLoadOperationsCount < 2) {
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

    public void setDelegate(FileLoaderDelegate delegate) {
        this.delegate = delegate;
    }

    protected File getCacheDir() {
        return delegate == null ? null : delegate.getCacheDir();
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
            return audio.dc_id + "_" + audio.id + ".m4a";
        } else if (attach instanceof TLRPC.FileLocation) {
            TLRPC.FileLocation location = (TLRPC.FileLocation)attach;
            return location.volume_id + "_" + location.local_id + ".jpg";
        }
        return "";
    }
}
