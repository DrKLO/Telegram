/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.telegram.TL.TLRPC;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.Views.BackupImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class FileLoader {
    public static FileLoader Instance = new FileLoader();
    public LruCache memCache;

    private String ignoreRemoval = null;
    private HashMap<String, CacheImage> imageLoading;
    private HashMap<Integer, CacheImage> imageLoadingByKeys;
    private Queue<FileLoadOperation> operationsQueue;
    private Queue<FileLoadOperation> runningOperation;
    private final int maxConcurentLoadingOpertaionsCount = 2;
    private Queue<FileUploadOperation> uploadOperationQueue;
    private HashMap<String, FileUploadOperation> uploadOperationPaths;
    private int currentUploadOperationsCount = 0;
    private Queue<FileLoadOperation> loadOperationQueue;
    private HashMap<String, FileLoadOperation> loadOperationPaths;
    private int currentLoadOperationsCount = 0;
    public static long lastCacheOutTime = 0;
    public ConcurrentHashMap<String, Float> fileProgresses = new ConcurrentHashMap<String, Float>();
    private long lastProgressUpdateTime = 0;
    public float density = 1;
    private HashMap<String, Integer> BitmapUseCounts = new HashMap<String, Integer>();

    int lastImageNum;

    public static final int FileDidUpload = 10000;
    public static final int FileDidFailUpload = 10001;
    public static final int FileUploadProgressChanged = 10002;
    public static final int FileLoadProgressChanged = 10003;
    public static final int FileDidLoaded = 10004;
    public static final int FileDidFailedLoad = 10005;

    public class VMRuntimeHack {
        private Object runtime = null;
        private Method trackAllocation = null;
        private Method trackFree = null;

        public boolean trackAlloc(long size) {
            if (runtime == null)
                return false;
            try {
                Object res = trackAllocation.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean)res : true;
            } catch (IllegalArgumentException e) {
                return false;
            } catch (IllegalAccessException e) {
                return false;
            } catch (InvocationTargetException e) {
                return false;
            }
        }

        public boolean trackFree(long size) {
            if (runtime == null)
                return false;
            try {
                Object res = trackFree.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean)res : true;
            } catch (IllegalArgumentException e) {
                return false;
            } catch (IllegalAccessException e) {
                return false;
            } catch (InvocationTargetException e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        public VMRuntimeHack() {
            boolean success = false;
            try {
                Class cl = Class.forName("dalvik.system.VMRuntime");
                Method getRt = cl.getMethod("getRuntime", new Class[0]);
                runtime = getRt.invoke(null, new Object[0]);
                trackAllocation = cl.getMethod("trackExternalAllocation", new Class[] {long.class});
                trackFree = cl.getMethod("trackExternalFree", new Class[] {long.class});
                success = true;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (!success) {
                Log.i("tmessages", "VMRuntime hack does not work!");
                runtime = null;
                trackAllocation = null;
                trackFree = null;
            }
        }
    }

    public VMRuntimeHack runtimeHack = null;

    private class CacheImage {
        public String key;
        public ArrayList<View> imageViewArray;
        public FileLoadOperation loadOperation;

        public void addImageView(View imageView) {
            if (imageViewArray == null) {
                imageViewArray = new ArrayList<View>();
            }
            boolean exist = false;
            for (View v : imageViewArray) {
                if (v == imageView) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                imageViewArray.add(imageView);
            }
        }

        public void removeImageView(View imageView) {
            if (imageViewArray != null) {
                for (int a = 0; a < imageViewArray.size(); a++) {
                    View obj = imageViewArray.get(a);
                    if (obj == null || obj == imageView) {
                        imageViewArray.remove(a);
                        a--;
                    }
                }
            }
        }

        public void callAndClear(Bitmap image) {
            if (image != null) {
                for (View imgView : imageViewArray) {
                    if (imgView instanceof BackupImageView) {
                        ((BackupImageView)imgView).setImageBitmap(image, key);
                    } else if (imgView instanceof ImageView) {
                        ((ImageView)imgView).setImageBitmap(image);
                    }
                }
            }
            Utilities.imageLoadQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    imageViewArray.clear();
                    loadOperation = null;
                }
            });
        }

        public void cancelAndClear() {
            if (loadOperation != null) {
                loadOperation.cancel();
                loadOperation = null;
            }
            imageViewArray.clear();
        }
    }

    public void incrementUseCount(String key) {
        Integer count = BitmapUseCounts.get(key);
        if (count == null) {
            BitmapUseCounts.put(key, 1);
        } else {
            BitmapUseCounts.put(key, count + 1);
        }
    }

    public boolean decrementUseCount(String key) {
        Integer count = BitmapUseCounts.get(key);
        if (count == null) {
            return true;
        }
        if (count == 1) {
            BitmapUseCounts.remove(key);
            return true;
        } else {
            BitmapUseCounts.put(key, count - 1);
        }
        return false;
    }

    /*class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
        private CacheImage cacheImage;
        private Bitmap bitmap;
        private int data = 0;

        public BitmapWorkerTask(ArrayList<WeakReference<View>> arr) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            data = params[0];
            return decodeSampledBitmapFromResource(getResources(), data, 100, 100));
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }*/

    public FileLoader() {
        int maxMemory = (int)Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 10;
        density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT < 11) {
            runtimeHack = new VMRuntimeHack();
            cacheSize = 1024 * 1024 * 3;
        }
        memCache = new LruCache(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                if(Build.VERSION.SDK_INT < 12) {
                    return bitmap.getRowBytes() * bitmap.getHeight();
                } else {
                    return bitmap.getByteCount();
                }
            }
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldBitmap, Bitmap newBitmap) {
                if (ignoreRemoval != null && key != null && ignoreRemoval.equals(key)) {
                    return;
                }
                Integer count = BitmapUseCounts.get(key);
                if (count == null || count == 0) {
                    if (runtimeHack != null) {
                        runtimeHack.trackAlloc(oldBitmap.getRowBytes() * oldBitmap.getHeight());
                    }
                    oldBitmap.recycle();
                }
            }
        };
        imageLoading = new HashMap<String, CacheImage>();
        imageLoadingByKeys = new HashMap<Integer, CacheImage>();
        operationsQueue = new LinkedList<FileLoadOperation>();
        runningOperation = new LinkedList<FileLoadOperation>();
        uploadOperationQueue = new LinkedList<FileUploadOperation>();
        uploadOperationPaths = new HashMap<String, FileUploadOperation>();
        loadOperationPaths = new HashMap<String, FileLoadOperation>();
        loadOperationQueue = new LinkedList<FileLoadOperation>();
    }

    public void cancelUploadFile(final String location) {
        Utilities.fileUploadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileUploadOperation operation = uploadOperationPaths.get(location);
                if (operation != null) {
                    uploadOperationQueue.remove(operation);
                    operation.cancel();
                }
            }
        });
    }

    public boolean isInCache(String key) {
        return memCache.get(key) != null;
    }

    public void uploadFile(final String location, final byte[] key, final byte[] iv) {
        Utilities.fileUploadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileUploadOperation operation = new FileUploadOperation(location, key, iv);
                uploadOperationPaths.put(location, operation);
                operation.delegate = new FileUploadOperation.FileUploadOperationDelegate() {
                    @Override
                    public void didFinishUploadingFile(FileUploadOperation operation, final TLRPC.TL_inputFile inputFile, final TLRPC.TL_inputEncryptedFileUploaded inputEncryptedFile) {
                        NotificationCenter.Instance.postNotificationName(FileDidUpload, location, inputFile, inputEncryptedFile);
                        Utilities.fileUploadQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                uploadOperationPaths.remove(location);
                                currentUploadOperationsCount--;
                                if (currentUploadOperationsCount < 2) {
                                    FileUploadOperation operation = uploadOperationQueue.poll();
                                    if (operation != null) {
                                        currentUploadOperationsCount++;
                                        operation.start();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void didFailedUploadingFile(FileUploadOperation operation) {
                        fileProgresses.remove(location);
                        if (operation.state != 2) {
                            NotificationCenter.Instance.postNotificationName(FileDidFailUpload, location);
                        }
                        Utilities.fileUploadQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                uploadOperationPaths.remove(location);
                                currentUploadOperationsCount--;
                                if (currentUploadOperationsCount < 2) {
                                    FileUploadOperation operation = uploadOperationQueue.poll();
                                    if (operation != null) {
                                        currentUploadOperationsCount++;
                                        operation.start();
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
                        long currentTime = System.currentTimeMillis();
                        if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                            lastProgressUpdateTime = currentTime;
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.Instance.postNotificationName(FileUploadProgressChanged, location, progress);
                                }
                            });
                        }
                    }
                };
                if (currentUploadOperationsCount < 2) {
                    currentUploadOperationsCount++;
                    operation.start();
                } else {
                    uploadOperationQueue.add(operation);
                }
            }
        });
    }

    public void cancelLoadFile(final TLRPC.Video video, final TLRPC.PhotoSize photo) {
        if (video == null && photo == null) {
            return;
        }
        Utilities.fileUploadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (video != null) {
                    fileName = video.dc_id + "_" + video.id + ".mp4";
                } else if (photo != null) {
                    fileName = photo.location.volume_id + "_" + photo.location.local_id + ".jpg";
                }
                FileLoadOperation operation = loadOperationPaths.get(fileName);
                if (operation != null) {
                    loadOperationQueue.remove(operation);
                    operation.cancel();
                }
            }
        });
    }

    public void loadFile(final TLRPC.Video video, final TLRPC.PhotoSize photo) {
        Utilities.fileUploadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (video != null) {
                    fileName = video.dc_id + "_" + video.id + ".mp4";
                } else if (photo != null) {
                    fileName = photo.location.volume_id + "_" + photo.location.local_id + ".jpg";
                }
                if (loadOperationPaths.containsKey(fileName)) {
                    return;
                }
                FileLoadOperation operation = null;
                if (video != null) {
                    operation = new FileLoadOperation(video);
                    operation.totalBytesCount = video.size;
                } else if (photo != null) {
                    operation = new FileLoadOperation(photo.location);
                    operation.totalBytesCount = photo.size;
                    operation.needBitmapCreate = false;
                }

                final String arg1 = fileName;
                loadOperationPaths.put(fileName, operation);
                operation.delegate = new FileLoadOperation.FileLoadOperationDelegate() {
                    @Override
                    public void didFinishLoadingFile(FileLoadOperation operation) {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.Instance.postNotificationName(FileLoadProgressChanged, arg1, 1.0f);
                            }
                        });
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.Instance.postNotificationName(FileDidLoaded, arg1);
                            }
                        });
                        Utilities.fileUploadQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                loadOperationPaths.remove(arg1);
                                currentLoadOperationsCount--;
                                if (currentLoadOperationsCount < 2) {
                                    FileLoadOperation operation = loadOperationQueue.poll();
                                    if (operation != null) {
                                        currentLoadOperationsCount++;
                                        operation.start();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void didFailedLoadingFile(FileLoadOperation operation) {
                        fileProgresses.remove(arg1);
                        if (operation.state != 2) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.Instance.postNotificationName(FileDidFailedLoad, arg1);
                                }
                            });
                        }
                        Utilities.fileUploadQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                loadOperationPaths.remove(arg1);
                                currentLoadOperationsCount--;
                                if (currentLoadOperationsCount < 2) {
                                    FileLoadOperation operation = loadOperationQueue.poll();
                                    if (operation != null) {
                                        currentLoadOperationsCount++;
                                        operation.start();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void didChangedLoadProgress(FileLoadOperation operation, final float progress) {
                        if (operation.state != 2) {
                            fileProgresses.put(arg1, progress);
                        }
                        long currentTime = System.currentTimeMillis();
                        if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                            lastProgressUpdateTime = currentTime;
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.Instance.postNotificationName(FileLoadProgressChanged, arg1, progress);
                                }
                            });
                        }
                    }
                };
                if (currentLoadOperationsCount < 2) {
                    currentLoadOperationsCount++;
                    operation.start();
                } else {
                    loadOperationQueue.add(operation);
                }
            }
        });
    }

    Bitmap imageFromKey(String key) {
        if (key == null) {
            return null;
        }
        return memCache.get(key);
    }

    public void clearMemory() {
        memCache.evictAll();
    }

    public void cleanDisk(boolean all) {
        /*if (all) {
            [cacheInQueue cancelAllOperations];
            [[NSFileManager defaultManager] removeItemAtPath:diskCachePath error:nil];
            [[NSFileManager defaultManager] createDirectoryAtPath:diskCachePath withIntermediateDirectories:YES attributes:nil error:NULL];
        } else {
            NSDate *expirationDate = [NSDate dateWithTimeIntervalSinceNow:-cacheMaxCacheAge];
            NSDirectoryEnumerator *fileEnumerator = [[NSFileManager defaultManager] enumeratorAtPath:diskCachePath];
            for (NSString *fileName in fileEnumerator) {
                NSString *filePath = [diskCachePath stringByAppendingPathComponent:fileName];
                NSDictionary *attrs = [[NSFileManager defaultManager] attributesOfItemAtPath:filePath error:nil];
                if ([[[attrs fileModificationDate] laterDate:expirationDate] isEqualToDate:expirationDate]) {
                    [[NSFileManager defaultManager] removeItemAtPath:filePath error:nil];
                }
            }
        }*/
    }

    public void cancelLoadingForImageView(final View imageView) {
        if (imageView == null) {
            return;
        }
        Utilities.imageLoadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                Integer num = (Integer)imageView.getTag(R.string.CacheTag);
                if (num == null) {
                    num = lastImageNum;
                    imageView.setTag(R.string.CacheTag, num);
                    lastImageNum++;
                    if (lastImageNum == Integer.MAX_VALUE) {
                        lastImageNum = 0;
                    }
                }
                CacheImage ei = imageLoadingByKeys.get(num);
                if (ei != null) {
                    imageLoadingByKeys.remove(num);
                    ei.removeImageView(imageView);
                    if (ei.imageViewArray.size() == 0) {
                        checkOperationsAndClear(ei.loadOperation);
                        ei.cancelAndClear();
                        imageLoading.remove(ei.key);
                    }
                }
            }
        });
    }

    public Bitmap getImageFromMemory(TLRPC.FileLocation url, View imageView, String filter, boolean cancel) {
        return getImageFromMemory(url, null, imageView, filter, cancel);
    }

    public Bitmap getImageFromMemory(String url, View imageView, String filter, boolean cancel) {
        return getImageFromMemory(null, url, imageView, filter, cancel);
    }

    public Bitmap getImageFromMemory(TLRPC.FileLocation url, String httpUrl, View imageView, String filter, boolean cancel) {
        if ((url == null && httpUrl == null) || imageView == null)
            return null;
        String key;
        if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
        } else if (filter == null) {
            key = url.volume_id + "_" + url.local_id;
        } else {
            key = url.volume_id + "_" + url.local_id + "@" + filter;
        }

        Bitmap img = imageFromKey(key);
        if (img != null && cancel) {
            cancelLoadingForImageView(imageView);
        }
        return img;
    }

    private void performReplace(String oldKey, String newKey) {
        Bitmap b = memCache.get(oldKey);
        if (b != null) {
            ignoreRemoval = oldKey;
            memCache.remove(oldKey);
            memCache.put(newKey, b);
            ignoreRemoval = null;
        }
        Integer val = BitmapUseCounts.get(oldKey);
        if (val != null) {
            BitmapUseCounts.put(newKey, val);
            BitmapUseCounts.remove(oldKey);
        }
    }

    public void replaceImageInCache(final String oldKey, final String newKey) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> arr = memCache.getFilterKeys(oldKey);
                if (arr != null) {
                    for (String filter : arr) {
                        performReplace(oldKey + "@" + filter, newKey + "@" + filter);
                    }
                } else {
                    performReplace(oldKey, newKey);
                }
            }
        });
    }

    public void loadImage(final String url, final View imageView, final String filter, final boolean cancel) {
        loadImage(null, url, imageView, filter, cancel);
    }

    public void loadImage(final TLRPC.FileLocation url, final View imageView, final String filter, final boolean cancel) {
        loadImage(url, null, imageView, filter, cancel);
    }

    public void loadImage(final TLRPC.FileLocation url, final String httpUrl, final View imageView, final String filter, final boolean cancel) {
        if ((url == null && httpUrl == null) || imageView == null || (url != null && !(url instanceof TLRPC.TL_fileLocation) && !(url instanceof TLRPC.TL_fileEncryptedLocation))) {
            return;
        }
        Utilities.imageLoadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String key;
                if (httpUrl != null) {
                    key = Utilities.MD5(httpUrl);
                } else if (filter == null) {
                    key = url.volume_id + "_" + url.local_id;
                } else {
                    key = url.volume_id + "_" + url.local_id + "@" + filter;
                }

                Integer num = (Integer)imageView.getTag(R.string.CacheTag);
                if (num == null) {
                    num = lastImageNum;
                    imageView.setTag(R.string.CacheTag, num);
                    lastImageNum++;
                    if (lastImageNum == Integer.MAX_VALUE)
                        lastImageNum = 0;
                }

                boolean added = false;
                boolean addToByKeys = true;
                CacheImage alreadyLoadingImage = imageLoading.get(key);
                if (cancel) {
                    CacheImage ei = imageLoadingByKeys.get(num);
                    if (ei != null) {
                        if (ei != alreadyLoadingImage) {
                            ei.removeImageView(imageView);
                            if (ei.imageViewArray.size() == 0) {
                                checkOperationsAndClear(ei.loadOperation);
                                ei.cancelAndClear();
                                imageLoading.remove(ei.key);
                            }
                        } else {
                            addToByKeys = false;
                            added = true;
                        }
                    }
                }

                if (alreadyLoadingImage != null && addToByKeys) {
                    alreadyLoadingImage.addImageView(imageView);
                    imageLoadingByKeys.put(num, alreadyLoadingImage);
                    added = true;
                }

                if (!added) {
                    final CacheImage img = new CacheImage();
                    img.key = key;
                    img.addImageView(imageView);
                    imageLoadingByKeys.put(num, img);
                    imageLoading.put(key, img);

                    final String arg2 = key;
                    FileLoadOperation loadOperation;
                    if (httpUrl != null) {
                        loadOperation = new FileLoadOperation(httpUrl);
                    } else {
                        loadOperation = new FileLoadOperation(url);
                    }
                    loadOperation.filter = filter;
                    loadOperation.delegate = new FileLoadOperation.FileLoadOperationDelegate() {
                        @Override
                        public void didFinishLoadingFile(final FileLoadOperation operation) {
                            enqueueImageProcessingOperationWithImage(operation.image, filter, arg2, img);
                        }

                        @Override
                        public void didFailedLoadingFile(final FileLoadOperation operation) {
                            if (url != null) {
                                if (url.volume_id != 0 && url.local_id != 0) {
                                    fileProgresses.remove(url.volume_id + "_" + url.local_id + ".jpg");
                                }
                            }
                            Utilities.imageLoadQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    for (View view : img.imageViewArray) {
                                        Integer num = (Integer)view.getTag(R.string.CacheTag);
                                        imageLoadingByKeys.remove(num);
                                        imageLoading.remove(arg2);
                                        checkOperationsAndClear(operation);
                                    }
                                }
                            });
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    img.callAndClear(null);
                                }
                            });
                        }

                        @Override
                        public void didChangedLoadProgress(FileLoadOperation operation, float progress) {
                            if (url != null) {
                                if (url.volume_id != 0 && url.local_id != 0) {
                                    fileProgresses.put(url.volume_id + "_" + url.local_id + ".jpg", progress);
                                }
                            }
                        }
                    };
                    img.loadOperation = loadOperation;
                    if (runningOperation.size() < maxConcurentLoadingOpertaionsCount) {
                        loadOperation.start();
                        runningOperation.add(loadOperation);
                    } else {
                        operationsQueue.add(loadOperation);
                    }
                }
            }
        });
    }

    private void checkOperationsAndClear(FileLoadOperation operation) {
        operationsQueue.remove(operation);
        runningOperation.remove(operation);
        while (runningOperation.size() < maxConcurentLoadingOpertaionsCount && operationsQueue.size() != 0) {
            FileLoadOperation loadOperation = operationsQueue.poll();
            runningOperation.add(loadOperation);
            loadOperation.start();
        }
    }

    public void processImage(Bitmap image, View imageView, String filter, boolean cancel) {
        if (filter == null || imageView == null) {
            return;
        }

        Integer num = (Integer)imageView.getTag(R.string.CacheTag);
        if (num == null) {
            num = lastImageNum;
            imageView.setTag(R.string.CacheTag, num);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE)
                lastImageNum = 0;
        }

        boolean added = false;
        boolean addToByKeys = true;
        CacheImage alreadyLoadingImage = imageLoading.get(filter);
        if (cancel) {
            CacheImage ei = imageLoadingByKeys.get(num);
            if (ei != null) {
                if (ei != alreadyLoadingImage) {
                    ei.removeImageView(imageView);
                    if (ei.imageViewArray.size() == 0) {
                        checkOperationsAndClear(ei.loadOperation);
                        ei.cancelAndClear();
                        imageLoading.remove(ei.key);
                    }
                } else {
                    addToByKeys = false;
                    added = true;
                }
            }
        }

        if (alreadyLoadingImage != null && addToByKeys) {
            alreadyLoadingImage.addImageView(imageView);
            imageLoadingByKeys.put(num, alreadyLoadingImage);
            added = true;
        }

        if (!added) {
            CacheImage img = new CacheImage();
            img.key = filter;
            img.addImageView(imageView);
            imageLoadingByKeys.put(num, img);
            imageLoading.put(filter, img);

            enqueueImageProcessingOperationWithImage(image, filter, filter, img);
        }
    }

    void enqueueImageProcessingOperationWithImage(final Bitmap image, final String filter, final String key, final CacheImage img) {
        if (image == null || key == null) {
            return;
        }

        Utilities.imageLoadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (View v : img.imageViewArray) {
                    Integer num = (Integer)v.getTag(R.string.CacheTag);
                    imageLoadingByKeys.remove(num);
                }
                checkOperationsAndClear(img.loadOperation);
                imageLoading.remove(key);
            }
        });

        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                img.callAndClear(image);
                if (memCache.get(key) == null) {
                    memCache.put(key, image);
                }
            }
        });
    }
        /*AsyncTask
        NSBlockOperation *processingOperation = [NSBlockOperation blockOperationWithBlock:^(void) {
        if (countCores() == 1)
        [NSThread sleepForTimeInterval:0.1];
        UIImage *filterImage = [UIImage decodedImageWithImage:image andFilter:filter];
        if (!filterImage)
            filterImage = image;
        dispatch_async(dispatch_get_main_queue(), ^{
        if (filterImage) {
            if (![memCache objectForKey:key]) {
                [memCache setObject:filterImage forKey:key];
                [memArray addObject:key];
            }
        }
        for (UIView *view in img.imageViewArray) {
            NSNumber *num = objc_getAssociatedObject(view, &VIEW_CACHE_KEY);
            [imageLoadingByKeys removeObjectForKey:num];
        }
        [img callAndClear:filterImage];
        [imageLoading removeObjectForKey:key];
        });
        }];
        [imageProcessingQueue addOperation:processingOperation];

*/
    public static Bitmap loadBitmap(String path, float maxWidth, float maxHeight) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bmOptions);
        float photoW = bmOptions.outWidth;
        float photoH = bmOptions.outHeight;
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = (int)scaleFactor;

        ExifInterface exif;
        Matrix matrix = null;
        try {
            exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bitmap b;
        try {
            b = BitmapFactory.decodeFile(path, bmOptions);
            if (b != null && matrix != null) {
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            FileLoader.Instance.memCache.evictAll();
            b = BitmapFactory.decodeFile(path, bmOptions);
            if (b != null && matrix != null) {
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
            }
        }

        return b;
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache) {
        if (bitmap == null) {
            return null;
        }
        float photoW = bitmap.getWidth();
        float photoH = bitmap.getHeight();
        if (photoW == 0 || photoH == 0) {
            return null;
        }
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int)(photoW / scaleFactor), (int)(photoH / scaleFactor), true);

        TLRPC.TL_fileLocation location = new TLRPC.TL_fileLocation();
        location.volume_id = Integer.MIN_VALUE;
        location.dc_id = Integer.MIN_VALUE;
        location.local_id = UserConfig.lastLocalId;
        UserConfig.lastLocalId--;
        TLRPC.PhotoSize size;
        if (!cache) {
            size = new TLRPC.TL_photoSize();
        } else {
            size = new TLRPC.TL_photoCachedSize();
        }
        size.location = location;
        size.w = (int)(photoW / scaleFactor);
        size.h = (int)(photoH / scaleFactor);
        try {
            if (!cache) {
                String fileName = location.volume_id + "_" + location.local_id + ".jpg";
                final File cacheFile = new File(Utilities.getCacheDir(), fileName);
                FileOutputStream stream = new FileOutputStream(cacheFile);
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
                size.size = (int)stream.getChannel().size();
            } else {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
                size.bytes = stream.toByteArray();
                size.size = size.bytes.length;
            }
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
            return size;
        } catch (Exception e) {
            return null;
        }
    }
}
