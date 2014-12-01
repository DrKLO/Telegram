/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

public class ImageLoader {

    private HashMap<String, Integer> bitmapUseCounts = new HashMap<String, Integer>();
    private LruCache memCache;
    private ConcurrentHashMap<String, CacheImage> imageLoadingByUrl = new ConcurrentHashMap<String, CacheImage>();
    private ConcurrentHashMap<String, CacheImage> imageLoadingByKeys = new ConcurrentHashMap<String, CacheImage>();
    private HashMap<Integer, CacheImage> imageLoadingByTag = new HashMap<Integer, CacheImage>();
    private LinkedList<HttpTask> httpTasks = new LinkedList<HttpTask>();
    private DispatchQueue cacheOutQueue = new DispatchQueue("cacheOutQueue");
    private int currentHttpTasksCount = 0;

    protected VMRuntimeHack runtimeHack = null;
    private String ignoreRemoval = null;

    private volatile long lastCacheOutTime = 0;
    private int lastImageNum = 0;
    private long lastProgressUpdateTime = 0;

    private File telegramPath = null;

    private class HttpTask extends AsyncTask<Void, Void, Boolean> {

        private CacheImage cacheImage = null;
        private RandomAccessFile fileOutputStream = null;

        public HttpTask(CacheImage cacheImage) {
            this.cacheImage = cacheImage;
        }

        protected Boolean doInBackground(Void... voids) {
            InputStream httpConnectionStream = null;
            boolean done = false;

            try {
                URL downloadUrl = new URL(cacheImage.httpUrl);
                URLConnection httpConnection = downloadUrl.openConnection();
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();

                fileOutputStream = new RandomAccessFile(cacheImage.tempFilePath, "rws");
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }

            try {
                byte[] data = new byte[1024 * 2];
                while (true) {
                    if (isCancelled()) {
                        break;
                    }
                    try {
                        int readed = httpConnectionStream.read(data);
                        if (readed > 0) {
                            fileOutputStream.write(data, 0, readed);
                        } else if (readed == -1) {
                            done = true;
                            break;
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        break;
                    }
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }

            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                    fileOutputStream = null;
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }

            try {
                if (httpConnectionStream != null) {
                    httpConnectionStream.close();
                }
                httpConnectionStream = null;
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }

            if (done) {
                if (cacheImage.tempFilePath != null) {
                    cacheImage.tempFilePath.renameTo(cacheImage.finalFilePath);
                }
            }

            return done;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            fileDidLoaded(cacheImage.url, cacheImage.finalFilePath, cacheImage.tempFilePath);
            runHttpTasks(true);
        }

        @Override
        protected void onCancelled() {
            runHttpTasks(true);
        }
    }

    private class CacheOutTask implements Runnable {
        private Thread runningThread = null;
        private final Object sync = new Object();

        private CacheImage cacheImage = null;
        private boolean isCancelled = false;

        public CacheOutTask(CacheImage cacheImage) {
            this.cacheImage = cacheImage;
        }

        @Override
        public void run() {
            synchronized (sync) {
                runningThread = Thread.currentThread();
                Thread.interrupted();
                if (isCancelled) {
                    return;
                }
            }

            Long mediaId = null;
            Bitmap image = null;
            File cacheFileFinal = null;
            boolean canDeleteFile = true;

            if (cacheImage.finalFilePath != null && cacheImage.finalFilePath.exists()) {
                cacheFileFinal = cacheImage.finalFilePath;
            } else if (cacheImage.tempFilePath != null && cacheImage.tempFilePath.exists()) {
                cacheFileFinal = cacheImage.tempFilePath;
            } else if (cacheImage.finalFilePath != null) {
                cacheFileFinal = cacheImage.finalFilePath;
            }

            try {
                if (cacheImage.httpUrl != null) {
                    if (cacheImage.httpUrl.startsWith("thumb://")) {
                        int idx = cacheImage.httpUrl.indexOf(":", 8);
                        if (idx >= 0) {
                            mediaId = Long.parseLong(cacheImage.httpUrl.substring(8, idx));
                        }
                        canDeleteFile = false;
                    } else if (!cacheImage.httpUrl.startsWith("http")) {
                        canDeleteFile = false;
                    }
                }

                int delay = 20;
                if (runtimeHack != null) {
                    delay = 60;
                }
                if (mediaId != null) {
                    delay = 0;
                }
                if (delay != 0 && lastCacheOutTime != 0 && lastCacheOutTime > System.currentTimeMillis() - delay) {
                    Thread.sleep(delay);
                }
                lastCacheOutTime = System.currentTimeMillis();
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();

                float w_filter = 0;
                float h_filter = 0;
                boolean blur = false;
                if (cacheImage.filter != null) {
                    String args[] = cacheImage.filter.split("_");
                    w_filter = Float.parseFloat(args[0]) * AndroidUtilities.density;
                    h_filter = Float.parseFloat(args[1]) * AndroidUtilities.density;
                    if (args.length > 2) {
                        blur = true;
                    }
                    opts.inJustDecodeBounds = true;

                    if (mediaId != null) {
                        MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                    } else {
                        if (cacheImage.finalFilePath != null && cacheImage.finalFilePath.exists()) {
                            BitmapFactory.decodeFile(cacheImage.finalFilePath.getAbsolutePath(), opts);
                        } else if (cacheImage.tempFilePath != null && cacheImage.tempFilePath.exists()) {
                            BitmapFactory.decodeFile(cacheImage.tempFilePath.getAbsolutePath(), opts);
                        }
                    }

                    float photoW = opts.outWidth;
                    float photoH = opts.outHeight;
                    float scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                    if (scaleFactor < 1) {
                        scaleFactor = 1;
                    }
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = (int)scaleFactor;
                }
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }

                if (cacheImage.filter == null || blur) {
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                } else {
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                }
                opts.inDither = false;
                if (mediaId != null) {
                    image = MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                }
                if (image == null) {
                    FileInputStream is = new FileInputStream(cacheFileFinal);
                    image = BitmapFactory.decodeStream(is, null, opts);
                    is.close();
                }
                if (image == null) {
                    if (canDeleteFile && (cacheFileFinal.length() == 0 || cacheImage.filter == null)) {
                        cacheFileFinal.delete();
                    }
                } else {
                    if (cacheImage.filter != null) {
                        float bitmapW = image.getWidth();
                        float bitmapH = image.getHeight();
                        if (bitmapW != w_filter && bitmapW > w_filter) {
                            float scaleFactor = bitmapW / w_filter;
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, (int)w_filter, (int)(bitmapH / scaleFactor), true);
                            if (image != scaledBitmap) {
                                image.recycle();
                                image = scaledBitmap;
                            }
                        }
                        if (image != null && blur && bitmapH < 100 && bitmapW < 100) {
                            Utilities.blurBitmap(image, 3);
                        }
                    }
                    if (runtimeHack != null) {
                        runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                    }
                }
            } catch (Throwable e) {
                //don't promt
            }
            Thread.interrupted();
            onPostExecute(image != null ? new BitmapDrawable(image) : null);
        }

        private void onPostExecute(final BitmapDrawable bitmapDrawable) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (bitmapDrawable != null && memCache.get(cacheImage.key) == null) {
                        memCache.put(cacheImage.key, bitmapDrawable);
                    }
                    cacheImage.setImageAndClear(bitmapDrawable);
                }
            });
        }

        public void cancel() {
            synchronized (sync) {
                try {
                    isCancelled = true;
                    if (runningThread != null) {
                        runningThread.interrupt();
                    }
                } catch (Exception e) {
                    //don't promt
                }
            }
        }
    }

    public class VMRuntimeHack {
        private Object runtime = null;
        private Method trackAllocation = null;
        private Method trackFree = null;

        public boolean trackAlloc(long size) {
            if (runtime == null) {
                return false;
            }
            try {
                Object res = trackAllocation.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean)res : true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean trackFree(long size) {
            if (runtime == null) {
                return false;
            }
            try {
                Object res = trackFree.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean)res : true;
            } catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        public VMRuntimeHack() {
            try {
                Class cl = Class.forName("dalvik.system.VMRuntime");
                Method getRt = cl.getMethod("getRuntime", new Class[0]);
                Object[] objects = new Object[0];
                runtime = getRt.invoke(null, objects);
                trackAllocation = cl.getMethod("trackExternalAllocation", new Class[] {long.class});
                trackFree = cl.getMethod("trackExternalFree", new Class[] {long.class});
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                runtime = null;
                trackAllocation = null;
                trackFree = null;
            }
        }
    }

    private class CacheImage {
        protected String key = null;
        protected String url = null;
        protected String filter = null;
        protected TLRPC.FileLocation fileLocation = null;
        protected String httpUrl = null;
        protected File finalFilePath = null;
        protected File tempFilePath = null;
        protected CacheOutTask cacheTask;
        protected HttpTask httpTask;
        protected ArrayList<ImageReceiver> imageViewArray = new ArrayList<ImageReceiver>();

        public void addImageView(ImageReceiver imageView) {
            boolean exist = false;
            for (ImageReceiver v : imageViewArray) {
                if (v == imageView) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                imageViewArray.add(imageView);
                imageLoadingByTag.put(imageView.getTag(), this);
            }
        }

        public void removeImageView(ImageReceiver imageView) {
            for (int a = 0; a < imageViewArray.size(); a++) {
                ImageReceiver obj = imageViewArray.get(a);
                if (obj == null || obj == imageView) {
                    imageViewArray.remove(a);
                    if (obj != null) {
                        imageLoadingByTag.remove(obj.getTag());
                    }
                    a--;
                }
            }

            if (imageViewArray.size() == 0) {
                cancelAndClear();
            }
        }

        public void setImageAndClear(BitmapDrawable image) {
            if (image != null) {
                for (ImageReceiver imgView : imageViewArray) {
                    imgView.setImageBitmap(image, key);
                }
            }
            clear();
        }

        public void cancelAndClear() {
            if (fileLocation != null) {
                FileLoader.getInstance().cancelLoadFile(fileLocation);
            }
            if (cacheTask != null) {
                cacheOutQueue.cancelRunnable(cacheTask);
                cacheTask.cancel();
                cacheTask = null;
            }
            if (httpTask != null) {
                httpTasks.remove(httpTask);
                httpTask.cancel(true);
                httpTask = null;
            }
            clear();
        }

        private void clear() {
            for (ImageReceiver imageReceiver : imageViewArray) {
                imageLoadingByTag.remove(imageReceiver.getTag());
            }
            imageViewArray.clear();
            if (url != null) {
                imageLoadingByUrl.remove(url);
            }
            if (key != null) {
                imageLoadingByKeys.remove(key);
            }
        }
    }

    private static volatile ImageLoader Instance = null;
    public static ImageLoader getInstance() {
        ImageLoader localInstance = Instance;
        if (localInstance == null) {
            synchronized (ImageLoader.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ImageLoader();
                }
            }
        }
        return localInstance;
    }

    public ImageLoader() {
        int cacheSize = Math.min(15, ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() / 7) * 1024 * 1024;

        if (Build.VERSION.SDK_INT < 11) {
            runtimeHack = new VMRuntimeHack();
            cacheSize = 1024 * 1024 * 3;
        }
        memCache = new LruCache(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable bitmap) {
                Bitmap b = bitmap.getBitmap();
                if(Build.VERSION.SDK_INT < 12) {
                    return b.getRowBytes() * b.getHeight();
                } else {
                    return b.getByteCount();
                }
            }
            @Override
            protected void entryRemoved(boolean evicted, String key, BitmapDrawable oldBitmap, BitmapDrawable newBitmap) {
                if (ignoreRemoval != null && key != null && ignoreRemoval.equals(key)) {
                    return;
                }
                Integer count = bitmapUseCounts.get(key);
                if (count == null || count == 0) {
                    Bitmap b = oldBitmap.getBitmap();
                    if (runtimeHack != null) {
                        runtimeHack.trackAlloc(b.getRowBytes() * b.getHeight());
                    }
                    if (!b.isRecycled()) {
                        b.recycle();
                    }
                }
            }
        };

        FileLoader.getInstance().setDelegate(new FileLoader.FileLoaderDelegate() {
            @Override
            public void fileUploadProgressChanged(final String location, final float progress, final boolean isEncrypted) {
                long currentTime = System.currentTimeMillis();
                if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                    lastProgressUpdateTime = currentTime;

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileUploadProgressChanged, location, progress, isEncrypted);
                        }
                    });
                }
            }

            @Override
            public void fileDidUploaded(final String location, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile) {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileDidUpload, location, inputFile, inputEncryptedFile);
                    }
                });
            }

            @Override
            public void fileDidFailedUpload(final String location, final boolean isEncrypted) {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileDidFailUpload, location, isEncrypted);
                    }
                });
            }

            @Override
            public void fileDidLoaded(final String location, final File finalFile, final File tempFile) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (location != null) {
                            if (MediaController.getInstance().canSaveToGallery() && telegramPath != null && finalFile != null && finalFile.exists() && (location.endsWith(".mp4") || location.endsWith(".jpg"))) {
                                if (finalFile.toString().startsWith(telegramPath.toString())) {
                                    Utilities.addMediaToGallery(finalFile.toString());
                                }
                            }
                        }
                        ImageLoader.this.fileDidLoaded(location, finalFile, tempFile);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileDidLoaded, location);
                    }
                });
            }

            @Override
            public void fileDidFailedLoad(final String location, final int state) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageLoader.this.fileDidFailedLoad(location);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileDidFailedLoad, location, state);
                    }
                });
            }

            @Override
            public void fileLoadProgressChanged(final String location, final float progress) {
                long currentTime = System.currentTimeMillis();
                if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                    lastProgressUpdateTime = currentTime;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileLoadProgressChanged, location, progress);
                        }
                    });
                }
            }
        });

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                FileLog.e("tmessages", "file system changed");
                Runnable r = new Runnable() {
                    public void run() {
                        FileLoader.getInstance().setMediaDirs(createMediaPaths());
                    }
                };
                if (Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                    AndroidUtilities.runOnUIThread(r, 1000);
                } else {
                    r.run();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addAction(Intent.ACTION_MEDIA_CHECKING);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_NOFS);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        ApplicationLoader.applicationContext.registerReceiver(receiver, filter);

        FileLoader.getInstance().setMediaDirs(createMediaPaths());
    }

    private HashMap<Integer, File> createMediaPaths() {
        HashMap<Integer, File> mediaDirs = new HashMap<Integer, File>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        FileLog.e("tmessages", "cache path = " + cachePath);

        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                telegramPath.mkdirs();

                boolean canRename = false;

                try {
                    for (int a = 0; a < 5; a++) {
                        File srcFile = new File(cachePath, "temp.file");
                        srcFile.createNewFile();
                        File dstFile = new File(telegramPath, "temp.file");
                        canRename = srcFile.renameTo(dstFile);
                        srcFile.delete();
                        dstFile.delete();
                        if (canRename) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }

                if (canRename) {
                    if (telegramPath.isDirectory()) {
                        try {
                            File imagePath = new File(telegramPath, "Telegram Images");
                            imagePath.mkdir();
                            if (imagePath.isDirectory()) {
                                mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE, imagePath);
                                FileLog.e("tmessages", "image path = " + imagePath);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }

                        try {
                            File videoPath = new File(telegramPath, "Telegram Video");
                            videoPath.mkdir();
                            if (videoPath.isDirectory()) {
                                mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO, videoPath);
                                FileLog.e("tmessages", "video path = " + videoPath);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }

                        try {
                            File audioPath = new File(telegramPath, "Telegram Audio");
                            audioPath.mkdir();
                            if (audioPath.isDirectory()) {
                                new File(audioPath, ".nomedia").createNewFile();
                                mediaDirs.put(FileLoader.MEDIA_DIR_AUDIO, audioPath);
                                FileLog.e("tmessages", "audio path = " + audioPath);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }

                        try {
                            File documentPath = new File(telegramPath, "Telegram Documents");
                            documentPath.mkdir();
                            if (documentPath.isDirectory()) {
                                new File(documentPath, ".nomedia").createNewFile();
                                mediaDirs.put(FileLoader.MEDIA_DIR_DOCUMENT, documentPath);
                                FileLog.e("tmessages", "documents path = " + documentPath);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                } else {
                    FileLog.e("tmessages", "this Android can't rename files");
                }
            }
            MediaController.getInstance().checkSaveToGalleryFiles();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        return mediaDirs;
    }

    private void performReplace(String oldKey, String newKey) {
        BitmapDrawable b = memCache.get(oldKey);
        if (b != null) {
            ignoreRemoval = oldKey;
            memCache.remove(oldKey);
            memCache.put(newKey, b);
            ignoreRemoval = null;
        }
        Integer val = bitmapUseCounts.get(oldKey);
        if (val != null) {
            bitmapUseCounts.put(newKey, val);
            bitmapUseCounts.remove(oldKey);
        }
    }

    public void incrementUseCount(String key) {
        Integer count = bitmapUseCounts.get(key);
        if (count == null) {
            bitmapUseCounts.put(key, 1);
        } else {
            bitmapUseCounts.put(key, count + 1);
        }
    }

    public boolean decrementUseCount(String key) {
        Integer count = bitmapUseCounts.get(key);
        if (count == null) {
            return true;
        }
        if (count == 1) {
            bitmapUseCounts.remove(key);
            return true;
        } else {
            bitmapUseCounts.put(key, count - 1);
        }
        return false;
    }

    public void removeImage(String key) {
        bitmapUseCounts.remove(key);
        memCache.remove(key);
    }

    public boolean isInCache(String key) {
        return memCache.get(key) != null;
    }

    public void clearMemory() {
        memCache.evictAll();
    }

    public void cancelLoadingForImageView(ImageReceiver imageView) {
        if (imageView == null) {
            return;
        }
        Integer TAG = imageView.getTag();
        if (TAG == null) {
            imageView.setTag(TAG = lastImageNum);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE) {
                lastImageNum = 0;
            }
        }
        CacheImage ei = imageLoadingByTag.get(TAG);
        if (ei != null) {
            ei.removeImageView(imageView);
        }
    }

    public BitmapDrawable getImageFromMemory(TLRPC.FileLocation url, String httpUrl, String filter, ImageReceiver imageReceiver) {
        if (url == null && httpUrl == null) {
            return null;
        }
        String key;
        if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
        } else {
            key = url.volume_id + "_" + url.local_id;
        }
        if (filter != null) {
            key += "@" + filter;
        }
        BitmapDrawable bitmapDrawable = memCache.get(key);
        if (bitmapDrawable != null && imageReceiver != null) {
            Integer TAG = imageReceiver.getTag();
            if (TAG != null) {
                CacheImage alreadyLoadingImage = imageLoadingByTag.get(TAG);
                if (alreadyLoadingImage != null) {
                    alreadyLoadingImage.removeImageView(imageReceiver);
                }
            }
        }
        return bitmapDrawable;
    }

    public void replaceImageInCache(final String oldKey, final String newKey) {
        AndroidUtilities.runOnUIThread(new Runnable() {
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

    public void putImageToCache(BitmapDrawable bitmap, String key) {
        memCache.put(key, bitmap);
    }

    public void loadImage(final TLRPC.FileLocation fileLocation, final String httpUrl, final ImageReceiver imageView, final int size, final boolean cacheOnly) {
        if ((fileLocation == null && httpUrl == null) || imageView == null || (fileLocation != null && !(fileLocation instanceof TLRPC.TL_fileLocation) && !(fileLocation instanceof TLRPC.TL_fileEncryptedLocation))) {
            return;
        }

        String url;
        String key;
        if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
            url = key + ".jpg";
        } else {
            key = fileLocation.volume_id + "_" + fileLocation.local_id;
            url = key + ".jpg";
        }
        String filter = imageView.getFilter();
        if (filter != null) {
            key += "@" + filter;
        }

        Integer TAG = imageView.getTag();
        if (TAG == null) {
            imageView.setTag(TAG = lastImageNum);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE) {
                lastImageNum = 0;
            }
        }

        boolean added = false;
        CacheImage alreadyLoadingUrl = imageLoadingByUrl.get(url);
        CacheImage alreadyLoadingCache = imageLoadingByKeys.get(key);
        CacheImage alreadyLoadingImage = imageLoadingByTag.get(TAG);
        if (alreadyLoadingImage != null) {
            if (alreadyLoadingImage == alreadyLoadingUrl || alreadyLoadingImage == alreadyLoadingCache) {
                added = true;
            } else {
                alreadyLoadingImage.removeImageView(imageView);
            }
        }

        if (!added && alreadyLoadingCache != null) {
            alreadyLoadingCache.addImageView(imageView);
            added = true;
        }
        if (!added && alreadyLoadingUrl != null) {
            alreadyLoadingUrl.addImageView(imageView);
            added = true;
        }

        if (!added) {
            boolean onlyCache = false;
            File cacheFile = null;
            if (cacheOnly || size == 0 || httpUrl != null || fileLocation != null && (fileLocation.key != null || fileLocation.volume_id == Integer.MIN_VALUE && fileLocation.local_id < 0)) {
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
            } else {
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_IMAGE), url);
            }
            if (httpUrl != null) {
                if (!httpUrl.startsWith("http")) {
                    onlyCache = true;
                    if (httpUrl.startsWith("thumb://")) {
                        int idx = httpUrl.indexOf(":", 8);
                        if (idx >= 0) {
                            cacheFile = new File(httpUrl.substring(idx + 1));
                        }
                    } else {
                        cacheFile = new File(httpUrl);
                    }
                }
            }
            CacheImage img = new CacheImage();
            if (onlyCache || cacheFile.exists()) {
                img.finalFilePath = cacheFile;
                img.key = key;
                img.httpUrl = httpUrl;
                if (imageView.getFilter() != null) {
                    img.filter = imageView.getFilter();
                }
                img.addImageView(imageView);
                imageLoadingByKeys.put(key, img);
                img.cacheTask = new CacheOutTask(img);
                cacheOutQueue.postRunnable(img.cacheTask);
            } else {
                img.url = url;
                img.fileLocation = fileLocation;
                img.httpUrl = httpUrl;
                img.addImageView(imageView);
                imageLoadingByUrl.put(url, img);
                if (httpUrl == null) {
                    FileLoader.getInstance().loadFile(fileLocation, size, size == 0 || fileLocation.key != null || cacheOnly);
                } else {
                    String file = Utilities.MD5(httpUrl);
                    File cacheDir = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE);
                    img.tempFilePath = new File(cacheDir, file + "_temp.jpg");
                    img.finalFilePath = cacheFile;
                    img.httpTask = new HttpTask(img);
                    httpTasks.add(img.httpTask);
                    runHttpTasks(false);
                }
            }
        }
    }

    private void fileDidLoaded(String location, File finalFile, File tempFile) {
        if (!location.endsWith(".jpg") && !location.startsWith("http")) {
            return;
        }
        CacheImage img = imageLoadingByUrl.get(location);
        if (img == null) {
            return;
        }
        imageLoadingByUrl.remove(location);
        for (ImageReceiver imageReceiver : img.imageViewArray) {
            String key = imageReceiver.getKey();
            if (key == null) {
                continue;
            }
            CacheImage cacheImage = imageLoadingByKeys.get(key);
            if (cacheImage == null) {
                cacheImage = new CacheImage();
                cacheImage.finalFilePath = finalFile;
                cacheImage.tempFilePath = tempFile;
                cacheImage.key = key;
                cacheImage.httpUrl = img.httpUrl;
                cacheImage.cacheTask = new CacheOutTask(cacheImage);
                if (imageReceiver.getFilter() != null) {
                    cacheImage.filter = imageReceiver.getFilter();
                }
                imageLoadingByKeys.put(cacheImage.key, cacheImage);
                cacheOutQueue.postRunnable(cacheImage.cacheTask);
            }
            cacheImage.addImageView(imageReceiver);
        }
    }

    private void fileDidFailedLoad(String location) {
        if (!location.endsWith(".jpg") && !location.startsWith("http")) {
            return;
        }
        CacheImage img = imageLoadingByUrl.get(location);
        if (img != null) {
            img.setImageAndClear(null);
        }
    }

    private void runHttpTasks(boolean complete) {
        if (complete) {
            currentHttpTasksCount--;
        }
        while (currentHttpTasksCount < 1 && !httpTasks.isEmpty()) {
            HttpTask task = httpTasks.poll();
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
            } else {
                task.execute(null, null, null);
            }
            currentHttpTasksCount++;
        }
    }

    public static Bitmap loadBitmap(String path, Uri uri, float maxWidth, float maxHeight) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        FileDescriptor fileDescriptor = null;
        ParcelFileDescriptor parcelFD = null;

        if (path == null && uri != null && uri.getScheme() != null) {
            String imageFilePath = null;
            if (uri.getScheme().contains("file")) {
                path = uri.getPath();
            } else {
                try {
                    path = Utilities.getPath(uri);
                } catch (Throwable e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        if (path != null) {
            BitmapFactory.decodeFile(path, bmOptions);
        } else if (uri != null) {
            boolean error = false;
            try {
                parcelFD = ApplicationLoader.applicationContext.getContentResolver().openFileDescriptor(uri, "r");
                fileDescriptor = parcelFD.getFileDescriptor();
                BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bmOptions);
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
                try {
                    if (parcelFD != null) {
                        parcelFD.close();
                    }
                } catch (Throwable e2) {
                    FileLog.e("tmessages", e2);
                }
                return null;
            }
        }
        float photoW = bmOptions.outWidth;
        float photoH = bmOptions.outHeight;
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = (int)scaleFactor;

        String exifPath = null;
        if (path != null) {
            exifPath = path;
        } else if (uri != null) {
            exifPath = Utilities.getPath(uri);
        }

        Matrix matrix = null;

        if (exifPath != null) {
            ExifInterface exif;
            try {
                exif = new ExifInterface(exifPath);
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
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
        }

        Bitmap b = null;
        if (path != null) {
            try {
                b = BitmapFactory.decodeFile(path, bmOptions);
                if (b != null) {
                    b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
                ImageLoader.getInstance().clearMemory();
                try {
                    if (b == null) {
                        b = BitmapFactory.decodeFile(path, bmOptions);
                    }
                    if (b != null) {
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                    }
                } catch (Throwable e2) {
                    FileLog.e("tmessages", e2);
                }
            }
        } else if (uri != null) {
            try {
                b = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bmOptions);
                if (b != null) {
                    b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            } finally {
                try {
                    if (parcelFD != null) {
                        parcelFD.close();
                    }
                } catch (Throwable e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        return b;
    }

    private static TLRPC.PhotoSize scaleAndSaveImageInternal(Bitmap bitmap, int w, int h, float photoW, float photoH, float scaleFactor, int quality, boolean cache, boolean scaleAnyway) throws Exception {
        Bitmap scaledBitmap = null;
        if (scaleFactor > 1 || scaleAnyway) {
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        } else {
            scaledBitmap = bitmap;
        }

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
        size.w = scaledBitmap.getWidth();
        size.h = scaledBitmap.getHeight();
        if (size.w <= 100 && size.h <= 100) {
            size.type = "s";
        } else if (size.w <= 320 && size.h <= 320) {
            size.type = "m";
        } else if (size.w <= 800 && size.h <= 800) {
            size.type = "x";
        } else if (size.w <= 1280 && size.h <= 1280) {
            size.type = "y";
        } else {
            size.type = "w";
        }

        if (!cache) {
            String fileName = location.volume_id + "_" + location.local_id + ".jpg";
            final File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
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
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache) {
        return scaleAndSaveImage(bitmap, maxWidth, maxHeight, quality, cache, 0, 0);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight) {
        if (bitmap == null) {
            return null;
        }
        float photoW = bitmap.getWidth();
        float photoH = bitmap.getHeight();
        if (photoW == 0 || photoH == 0) {
            return null;
        }
        boolean scaleAnyway = false;
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);
        if (scaleFactor < 1 && minWidth != 0 && minHeight != 0) {
            scaleFactor = Math.max(photoW / minWidth, photoH / minHeight);
            scaleAnyway = true;
        }
        int w = (int)(photoW / scaleFactor);
        int h = (int)(photoH / scaleFactor);
        if (h == 0 || w == 0) {
            return null;
        }

        try {
            return scaleAndSaveImageInternal(bitmap, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
            ImageLoader.getInstance().clearMemory();
            System.gc();
            try {
                return scaleAndSaveImageInternal(bitmap, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway);
            } catch (Throwable e2) {
                FileLog.e("tmessages", e2);
                return null;
            }
        }
    }
}
