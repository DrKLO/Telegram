/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import androidx.exifinterface.media.ExifInterface;

import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageLoader {

    private HashMap<String, Integer> bitmapUseCounts = new HashMap<>();
    private LruCache<BitmapDrawable> memCache;
    private LruCache<RLottieDrawable> lottieMemCache;
    private HashMap<String, CacheImage> imageLoadingByUrl = new HashMap<>();
    private HashMap<String, CacheImage> imageLoadingByKeys = new HashMap<>();
    private SparseArray<CacheImage> imageLoadingByTag = new SparseArray<>();
    private HashMap<String, ThumbGenerateInfo> waitingForQualityThumb = new HashMap<>();
    private SparseArray<String> waitingForQualityThumbByTag = new SparseArray<>();
    private LinkedList<HttpImageTask> httpTasks = new LinkedList<>();
    private LinkedList<ArtworkLoadTask> artworkTasks = new LinkedList<>();
    private DispatchQueue cacheOutQueue = new DispatchQueue("cacheOutQueue");
    private DispatchQueue cacheThumbOutQueue = new DispatchQueue("cacheThumbOutQueue");
    private DispatchQueue thumbGeneratingQueue = new DispatchQueue("thumbGeneratingQueue");
    private DispatchQueue imageLoadQueue = new DispatchQueue("imageLoadQueue");
    private HashMap<String, String> replacedBitmaps = new HashMap<>();
    private ConcurrentHashMap<String, Float> fileProgresses = new ConcurrentHashMap<>();
    private HashMap<String, ThumbGenerateTask> thumbGenerateTasks = new HashMap<>();
    private HashMap<String, Integer> forceLoadingImages = new HashMap<>();
    private static ThreadLocal<byte[]> bytesLocal = new ThreadLocal<>();
    private static ThreadLocal<byte[]> bytesThumbLocal = new ThreadLocal<>();
    private static byte[] header = new byte[12];
    private static byte[] headerThumb = new byte[12];
    private int currentHttpTasksCount = 0;
    private int currentArtworkTasksCount = 0;
    private boolean canForce8888;

    private ConcurrentHashMap<String, WebFile> testWebFile = new ConcurrentHashMap<>();

    private LinkedList<HttpFileTask> httpFileLoadTasks = new LinkedList<>();
    private HashMap<String, HttpFileTask> httpFileLoadTasksByKeys = new HashMap<>();
    private HashMap<String, Runnable> retryHttpsTasks = new HashMap<>();
    private int currentHttpFileLoadTasksCount = 0;

    private String ignoreRemoval = null;

    private volatile long lastCacheOutTime = 0;
    private int lastImageNum = 0;
    private long lastProgressUpdateTime = 0;

    private File telegramPath = null;

    public static final String AUTOPLAY_FILTER = "g";

    private class ThumbGenerateInfo {
        private TLRPC.Document parentDocument;
        private String filter;
        private ArrayList<ImageReceiver> imageReceiverArray = new ArrayList<>();
        private ArrayList<Integer> imageReceiverGuidsArray = new ArrayList<>();
        private boolean big;
    }

    private class HttpFileTask extends AsyncTask<Void, Void, Boolean> {

        private String url;
        private File tempFile;
        private String ext;
        private int fileSize;
        private RandomAccessFile fileOutputStream = null;
        private boolean canRetry = true;
        private long lastProgressTime;
        private int currentAccount;

        public HttpFileTask(String url, File tempFile, String ext, int currentAccount) {
            this.url = url;
            this.tempFile = tempFile;
            this.ext = ext;
            this.currentAccount = currentAccount;
        }

        private void reportProgress(final float progress) {
            long currentTime = System.currentTimeMillis();
            if (progress == 1 || lastProgressTime == 0 || lastProgressTime < currentTime - 500) {
                lastProgressTime = currentTime;
                Utilities.stageQueue.postRunnable(() -> {
                    fileProgresses.put(url, progress);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileLoadProgressChanged, url, progress));
                });
            }
        }

        protected Boolean doInBackground(Void... voids) {
            InputStream httpConnectionStream = null;
            boolean done = false;

            URLConnection httpConnection = null;
            try {
                URL downloadUrl = new URL(url);
                httpConnection = downloadUrl.openConnection();
                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                //httpConnection.addRequestProperty("Referer", "google.com");
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                if (httpConnection instanceof HttpURLConnection) {
                    HttpURLConnection httpURLConnection = (HttpURLConnection) httpConnection;
                    httpURLConnection.setInstanceFollowRedirects(true);
                    int status = httpURLConnection.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                        String newUrl = httpURLConnection.getHeaderField("Location");
                        String cookies = httpURLConnection.getHeaderField("Set-Cookie");
                        downloadUrl = new URL(newUrl);
                        httpConnection = downloadUrl.openConnection();
                        httpConnection.setRequestProperty("Cookie", cookies);
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                        //httpConnection.addRequestProperty("Referer", "google.com");
                    }
                }
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();

                fileOutputStream = new RandomAccessFile(tempFile, "rws");
            } catch (Throwable e) {
                if (e instanceof SocketTimeoutException) {
                    if (ApplicationLoader.isNetworkOnline()) {
                        canRetry = false;
                    }
                } else if (e instanceof UnknownHostException) {
                    canRetry = false;
                } else if (e instanceof SocketException) {
                    if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                        canRetry = false;
                    }
                } else if (e instanceof FileNotFoundException) {
                    canRetry = false;
                }
                FileLog.e(e);
            }

            if (canRetry) {
                try {
                    if (httpConnection instanceof HttpURLConnection) {
                        int code = ((HttpURLConnection) httpConnection).getResponseCode();
                        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                            canRetry = false;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (httpConnection != null) {
                    try {
                        Map<String, List<String>> headerFields = httpConnection.getHeaderFields();
                        if (headerFields != null) {
                            List values = headerFields.get("content-Length");
                            if (values != null && !values.isEmpty()) {
                                String length = (String) values.get(0);
                                if (length != null) {
                                    fileSize = Utilities.parseInt(length);
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                if (httpConnectionStream != null) {
                    try {
                        byte[] data = new byte[1024 * 32];
                        int totalLoaded = 0;
                        while (true) {
                            if (isCancelled()) {
                                break;
                            }
                            try {
                                int read = httpConnectionStream.read(data);
                                if (read > 0) {
                                    fileOutputStream.write(data, 0, read);
                                    totalLoaded += read;
                                    if (fileSize > 0) {
                                        reportProgress(totalLoaded / (float) fileSize);
                                    }
                                } else if (read == -1) {
                                    done = true;
                                    if (fileSize != 0) {
                                        reportProgress(1.0f);
                                    }
                                    break;
                                } else {
                                    break;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }

                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                        fileOutputStream = null;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }

                try {
                    if (httpConnectionStream != null) {
                        httpConnectionStream.close();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            return done;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            runHttpFileLoadTasks(this, result ? 2 : 1);
        }

        @Override
        protected void onCancelled() {
            runHttpFileLoadTasks(this, 2);
        }
    }

    private class ArtworkLoadTask extends AsyncTask<Void, Void, String> {

        private CacheImage cacheImage;
        private boolean canRetry = true;
        private HttpURLConnection httpConnection;

        private boolean small;

        public ArtworkLoadTask(CacheImage cacheImage) {
            this.cacheImage = cacheImage;
            Uri uri = Uri.parse(cacheImage.imageLocation.path);
            small = uri.getQueryParameter("s") != null;
        }

        protected String doInBackground(Void... voids) {
            ByteArrayOutputStream outbuf = null;
            InputStream httpConnectionStream = null;
            try {
                String location = cacheImage.imageLocation.path;
                URL downloadUrl = new URL(location.replace("athumb://", "https://"));
                httpConnection = (HttpURLConnection) downloadUrl.openConnection();
                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.connect();
                try {
                    if (httpConnection != null) {
                        int code = httpConnection.getResponseCode();
                        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                            canRetry = false;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                httpConnectionStream = httpConnection.getInputStream();

                outbuf = new ByteArrayOutputStream();

                byte[] data = new byte[1024 * 32];
                while (true) {
                    if (isCancelled()) {
                        break;
                    }
                    int read = httpConnectionStream.read(data);
                    if (read > 0) {
                        outbuf.write(data, 0, read);
                    } else if (read == -1) {
                        break;
                    } else {
                        break;
                    }
                }
                canRetry = false;
                JSONObject object = new JSONObject(new String(outbuf.toByteArray()));
                JSONArray array = object.getJSONArray("results");
                if (array.length() > 0) {
                    JSONObject media = array.getJSONObject(0);
                    String artworkUrl100 = media.getString("artworkUrl100");
                    if (small) {
                        return artworkUrl100;
                    } else {
                        return artworkUrl100.replace("100x100", "600x600");
                    }
                }
            } catch (Throwable e) {
                if (e instanceof SocketTimeoutException) {
                    if (ApplicationLoader.isNetworkOnline()) {
                        canRetry = false;
                    }
                } else if (e instanceof UnknownHostException) {
                    canRetry = false;
                } else if (e instanceof SocketException) {
                    if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                        canRetry = false;
                    }
                } else if (e instanceof FileNotFoundException) {
                    canRetry = false;
                }
                FileLog.e(e);
            } finally {
                try {
                    if (httpConnection != null) {
                        httpConnection.disconnect();
                    }
                } catch (Throwable ignore) {

                }
                try {
                    if (httpConnectionStream != null) {
                        httpConnectionStream.close();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (outbuf != null) {
                        outbuf.close();
                    }
                } catch (Exception ignore) {

                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final String result) {
            if (result != null) {
                cacheImage.httpTask = new HttpImageTask(cacheImage, 0, result);
                httpTasks.add(cacheImage.httpTask);
                runHttpTasks(false);
            } else if (canRetry) {
                artworkLoadError(cacheImage.url);
            }
            imageLoadQueue.postRunnable(() -> runArtworkTasks(true));
        }

        @Override
        protected void onCancelled() {
            imageLoadQueue.postRunnable(() -> runArtworkTasks(true));
        }
    }

    private class HttpImageTask extends AsyncTask<Void, Void, Boolean> {

        private CacheImage cacheImage;
        private RandomAccessFile fileOutputStream;
        private int imageSize;
        private long lastProgressTime;
        private boolean canRetry = true;
        private String overrideUrl;
        private HttpURLConnection httpConnection;

        public HttpImageTask(CacheImage cacheImage, int size) {
            this.cacheImage = cacheImage;
            imageSize = size;
        }

        public HttpImageTask(CacheImage cacheImage, int size, String url) {
            this.cacheImage = cacheImage;
            imageSize = size;
            overrideUrl = url;
        }

        private void reportProgress(final float progress) {
            long currentTime = System.currentTimeMillis();
            if (progress == 1 || lastProgressTime == 0 || lastProgressTime < currentTime - 500) {
                lastProgressTime = currentTime;
                Utilities.stageQueue.postRunnable(() -> {
                    fileProgresses.put(cacheImage.url, progress);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.FileLoadProgressChanged, cacheImage.url, progress));
                });
            }
        }

        protected Boolean doInBackground(Void... voids) {
            InputStream httpConnectionStream = null;
            boolean done = false;

            if (!isCancelled()) {
                try {
                    String location = cacheImage.imageLocation.path;
                    if (location.startsWith("https://static-maps") || location.startsWith("https://maps.googleapis")) {
                        int provider = MessagesController.getInstance(cacheImage.currentAccount).mapProvider;
                        if (provider == 3 || provider == 4) {
                            WebFile webFile = testWebFile.get(location);
                            if (webFile != null) {
                                TLRPC.TL_upload_getWebFile req = new TLRPC.TL_upload_getWebFile();
                                req.location = webFile.location;
                                req.offset = 0;
                                req.limit = 0;
                                ConnectionsManager.getInstance(cacheImage.currentAccount).sendRequest(req, (response, error) -> {

                                });
                            }
                        }
                    }

                    URL downloadUrl = new URL(overrideUrl != null ? overrideUrl : location);
                    httpConnection = (HttpURLConnection) downloadUrl.openConnection();
                    httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                    //httpConnection.addRequestProperty("Referer", "google.com");
                    httpConnection.setConnectTimeout(5000);
                    httpConnection.setReadTimeout(5000);
                    httpConnection.setInstanceFollowRedirects(true);
                    if (!isCancelled()) {
                        httpConnection.connect();
                        httpConnectionStream = httpConnection.getInputStream();
                        fileOutputStream = new RandomAccessFile(cacheImage.tempFilePath, "rws");
                    }
                } catch (Throwable e) {
                    if (e instanceof SocketTimeoutException) {
                        if (ApplicationLoader.isNetworkOnline()) {
                            canRetry = false;
                        }
                    } else if (e instanceof UnknownHostException) {
                        canRetry = false;
                    } else if (e instanceof SocketException) {
                        if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                            canRetry = false;
                        }
                    } else if (e instanceof FileNotFoundException) {
                        canRetry = false;
                    }
                    FileLog.e(e);
                }
            }

            if (!isCancelled()) {
                try {
                    if (httpConnection != null) {
                        int code = httpConnection.getResponseCode();
                        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                            canRetry = false;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (imageSize == 0 && httpConnection != null) {
                    try {
                        Map<String, List<String>> headerFields = httpConnection.getHeaderFields();
                        if (headerFields != null) {
                            List values = headerFields.get("content-Length");
                            if (values != null && !values.isEmpty()) {
                                String length = (String) values.get(0);
                                if (length != null) {
                                    imageSize = Utilities.parseInt(length);
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                if (httpConnectionStream != null) {
                    try {
                        byte[] data = new byte[1024 * 8];
                        int totalLoaded = 0;
                        while (true) {
                            if (isCancelled()) {
                                break;
                            }
                            try {
                                int read = httpConnectionStream.read(data);
                                if (read > 0) {
                                    totalLoaded += read;
                                    fileOutputStream.write(data, 0, read);
                                    if (imageSize != 0) {
                                        reportProgress(totalLoaded / (float) imageSize);
                                    }
                                } else if (read == -1) {
                                    done = true;
                                    if (imageSize != 0) {
                                        reportProgress(1.0f);
                                    }
                                    break;
                                } else {
                                    break;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }

            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                    fileOutputStream = null;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                if (httpConnection != null) {
                    httpConnection.disconnect();
                }
            } catch (Throwable ignore) {

            }
            try {
                if (httpConnectionStream != null) {
                    httpConnectionStream.close();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (done) {
                if (cacheImage.tempFilePath != null) {
                    if (!cacheImage.tempFilePath.renameTo(cacheImage.finalFilePath)) {
                        cacheImage.finalFilePath = cacheImage.tempFilePath;
                    }
                }
            }

            return done;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (result || !canRetry) {
                fileDidLoaded(cacheImage.url, cacheImage.finalFilePath, FileLoader.MEDIA_DIR_IMAGE);
            } else {
                httpFileLoadError(cacheImage.url);
            }
            Utilities.stageQueue.postRunnable(() -> {
                fileProgresses.remove(cacheImage.url);
                AndroidUtilities.runOnUIThread(() -> {
                    if (result) {
                        NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileDidLoad, cacheImage.url, cacheImage.finalFilePath);
                    } else {
                        NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileDidFailToLoad, cacheImage.url, 2);
                    }
                });
            });
            imageLoadQueue.postRunnable(() -> runHttpTasks(true));
        }

        @Override
        protected void onCancelled() {
            imageLoadQueue.postRunnable(() -> runHttpTasks(true));
            Utilities.stageQueue.postRunnable(() -> {
                fileProgresses.remove(cacheImage.url);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileDidFailToLoad, cacheImage.url, 1));
            });
        }
    }

    private class ThumbGenerateTask implements Runnable {

        private File originalPath;
        private int mediaType;
        private ThumbGenerateInfo info;

        public ThumbGenerateTask(int type, File path, ThumbGenerateInfo i) {
            mediaType = type;
            originalPath = path;
            info = i;
        }

        private void removeTask() {
            if (info == null) {
                return;
            }
            final String name = FileLoader.getAttachFileName(info.parentDocument);
            imageLoadQueue.postRunnable(() -> thumbGenerateTasks.remove(name));
        }

        @Override
        public void run() {
            try {
                if (info == null) {
                    removeTask();
                    return;
                }
                final String key = "q_" + info.parentDocument.dc_id + "_" + info.parentDocument.id;
                File thumbFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), key + ".jpg");
                if (thumbFile.exists() || !originalPath.exists()) {
                    removeTask();
                    return;
                }
                int size = info.big ? Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) : Math.min(180, Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 4);
                Bitmap originalBitmap = null;
                if (mediaType == FileLoader.MEDIA_DIR_IMAGE) {
                    originalBitmap = ImageLoader.loadBitmap(originalPath.toString(), null, size, size, false);
                } else if (mediaType == FileLoader.MEDIA_DIR_VIDEO) {
                    originalBitmap = ThumbnailUtils.createVideoThumbnail(originalPath.toString(), info.big ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND);
                } else if (mediaType == FileLoader.MEDIA_DIR_DOCUMENT) {
                    String path = originalPath.toString().toLowerCase();
                    if (path.endsWith("mp4")) {
                        originalBitmap = ThumbnailUtils.createVideoThumbnail(originalPath.toString(), info.big ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND);
                    } else if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".gif")) {
                        originalBitmap = ImageLoader.loadBitmap(path, null, size, size, false);
                    }
                }
                if (originalBitmap == null) {
                    removeTask();
                    return;
                }

                int w = originalBitmap.getWidth();
                int h = originalBitmap.getHeight();
                if (w == 0 || h == 0) {
                    removeTask();
                    return;
                }
                float scaleFactor = Math.min((float) w / size, (float) h / size);
                if (scaleFactor > 1) {
                    Bitmap scaledBitmap = Bitmaps.createScaledBitmap(originalBitmap, (int) (w / scaleFactor), (int) (h / scaleFactor), true);
                    if (scaledBitmap != originalBitmap) {
                        originalBitmap.recycle();
                        originalBitmap = scaledBitmap;
                    }
                }
                FileOutputStream stream = new FileOutputStream(thumbFile);
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, info.big ? 83 : 60, stream);
                try {
                    stream.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final BitmapDrawable bitmapDrawable = new BitmapDrawable(originalBitmap);
                final ArrayList<ImageReceiver> finalImageReceiverArray = new ArrayList<>(info.imageReceiverArray);
                final ArrayList<Integer> finalImageReceiverGuidsArray = new ArrayList<>(info.imageReceiverGuidsArray);
                AndroidUtilities.runOnUIThread(() -> {
                    removeTask();

                    String kf = key;
                    if (info.filter != null) {
                        kf += "@" + info.filter;
                    }

                    for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                        ImageReceiver imgView = finalImageReceiverArray.get(a);
                        imgView.setImageBitmapByKey(bitmapDrawable, kf, ImageReceiver.TYPE_IMAGE, false, finalImageReceiverGuidsArray.get(a));
                    }

                    memCache.put(kf, bitmapDrawable);
                });
            } catch (Throwable e) {
                FileLog.e(e);
                removeTask();
            }
        }
    }

    private class CacheOutTask implements Runnable {
        private Thread runningThread;
        private final Object sync = new Object();

        private CacheImage cacheImage;
        private boolean isCancelled;

        public CacheOutTask(CacheImage image) {
            cacheImage = image;
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

            if (cacheImage.imageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize) {
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }
                TLRPC.TL_photoStrippedSize photoSize = (TLRPC.TL_photoStrippedSize) cacheImage.imageLocation.photoSize;
                int len = photoSize.bytes.length - 3 + Bitmaps.header.length + Bitmaps.footer.length;
                byte[] bytes = bytesLocal.get();
                byte[] data = bytes != null && bytes.length >= len ? bytes : null;
                if (data == null) {
                    bytes = data = new byte[len];
                    bytesLocal.set(bytes);
                }
                System.arraycopy(Bitmaps.header, 0, data, 0, Bitmaps.header.length);
                System.arraycopy(photoSize.bytes, 3, data, Bitmaps.header.length, photoSize.bytes.length - 3);
                System.arraycopy(Bitmaps.footer, 0, data, Bitmaps.header.length + photoSize.bytes.length - 3, Bitmaps.footer.length);

                data[164] = photoSize.bytes[1];
                data[166] = photoSize.bytes[2];

                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, len);
                if (bitmap != null && !TextUtils.isEmpty(cacheImage.filter) && cacheImage.filter.contains("b")) {
                    Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
                }
                onPostExecute(bitmap != null ? new BitmapDrawable(bitmap) : null);
            } else if (cacheImage.lottieFile) {
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }
                int w = Math.min(512, AndroidUtilities.dp(170.6f));
                int h = Math.min(512, AndroidUtilities.dp(170.6f));
                boolean precache = false;
                boolean limitFps = false;
                int autoRepeat = 1;
                int[] colors = null;
                if (cacheImage.filter != null) {
                    String[] args = cacheImage.filter.split("_");
                    if (args.length >= 2) {
                        float w_filter = Float.parseFloat(args[0]);
                        float h_filter = Float.parseFloat(args[1]);
                        w = Math.min(512, (int) (w_filter * AndroidUtilities.density));
                        h = Math.min(512, (int) (h_filter * AndroidUtilities.density));
                        if (w_filter <= 90 && h_filter <= 90) {
                            w = Math.min(w, 160);
                            h = Math.min(h, 160);
                            limitFps = true;
                            precache = SharedConfig.getDevicePerfomanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH;
                        }
                    }
                    if (args.length >= 3) {
                        if ("nr".equals(args[2])) {
                            autoRepeat = 2;
                        } else if ("nrs".equals(args[2])) {
                            autoRepeat = 3;
                        }
                    }
                    if (args.length >= 5) {
                        if ("c1".equals(args[4])) {
                            colors = new int[]{0xf77e41, 0xca907a, 0xffb139, 0xedc5a5, 0xffd140, 0xf7e3c3, 0xffdf79, 0xfbefd6};
                        } else if ("c2".equals(args[4])) {
                            colors = new int[]{0xf77e41, 0xaa7c60, 0xffb139, 0xc8a987, 0xffd140, 0xddc89f, 0xffdf79, 0xe6d6b2};
                        } else if ("c3".equals(args[4])) {
                            colors = new int[]{0xf77e41, 0x8c6148, 0xffb139, 0xad8562, 0xffd140, 0xc49e76, 0xffdf79, 0xd4b188};
                        } else if ("c4".equals(args[4])) {
                            colors = new int[]{0xf77e41, 0x6e3c2c, 0xffb139, 0x925a34, 0xffd140, 0xa16e46, 0xffdf79, 0xac7a52};
                        } else if ("c5".equals(args[4])) {
                            colors = new int[]{0xf77e41, 0x291c12, 0xffb139, 0x472a22, 0xffd140, 0x573b30, 0xffdf79, 0x68493c};
                        }
                    }
                }
                RLottieDrawable lottieDrawable = new RLottieDrawable(cacheImage.finalFilePath, w, h, precache, limitFps, colors);
                lottieDrawable.setAutoRepeat(autoRepeat);
                onPostExecute(lottieDrawable);
            } else if (cacheImage.animatedFile) {
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }
                AnimatedFileDrawable fileDrawable;
                if (AUTOPLAY_FILTER.equals(cacheImage.filter) && !(cacheImage.imageLocation.document instanceof TLRPC.TL_documentEncrypted)) {
                    fileDrawable = new AnimatedFileDrawable(cacheImage.finalFilePath, false, cacheImage.size, cacheImage.imageLocation.document instanceof TLRPC.Document ? cacheImage.imageLocation.document : null, cacheImage.parentObject, cacheImage.currentAccount, false);
                } else {
                    fileDrawable = new AnimatedFileDrawable(cacheImage.finalFilePath, "d".equals(cacheImage.filter), 0, null, null, cacheImage.currentAccount, false);
                }
                Thread.interrupted();
                onPostExecute(fileDrawable);
            } else {
                Long mediaId = null;
                boolean mediaIsVideo = false;
                Bitmap image = null;
                boolean needInvert = false;
                int orientation = 0;
                File cacheFileFinal = cacheImage.finalFilePath;
                boolean inEncryptedFile = cacheImage.secureDocument != null || cacheImage.encryptionKeyPath != null && cacheFileFinal != null && cacheFileFinal.getAbsolutePath().endsWith(".enc");
                SecureDocumentKey secureDocumentKey;
                byte[] secureDocumentHash;
                if (cacheImage.secureDocument != null) {
                    secureDocumentKey = cacheImage.secureDocument.secureDocumentKey;
                    if (cacheImage.secureDocument.secureFile != null && cacheImage.secureDocument.secureFile.file_hash != null) {
                        secureDocumentHash = cacheImage.secureDocument.secureFile.file_hash;
                    } else {
                        secureDocumentHash = cacheImage.secureDocument.fileHash;
                    }
                } else {
                    secureDocumentKey = null;
                    secureDocumentHash = null;
                }
                boolean canDeleteFile = true;
                boolean useNativeWebpLoader = false;

                if (Build.VERSION.SDK_INT < 19) {
                    RandomAccessFile randomAccessFile = null;
                    try {
                        randomAccessFile = new RandomAccessFile(cacheFileFinal, "r");
                        byte[] bytes;
                        if (cacheImage.imageType == ImageReceiver.TYPE_THUMB) {
                            bytes = headerThumb;
                        } else {
                            bytes = header;
                        }
                        randomAccessFile.readFully(bytes, 0, bytes.length);
                        String str = new String(bytes).toLowerCase();
                        str = str.toLowerCase();
                        if (str.startsWith("riff") && str.endsWith("webp")) {
                            useNativeWebpLoader = true;
                        }
                        randomAccessFile.close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }

                String mediaThumbPath = null;
                if (cacheImage.imageLocation.path != null) {
                    String location = cacheImage.imageLocation.path;
                    if (location.startsWith("thumb://")) {
                        int idx = location.indexOf(":", 8);
                        if (idx >= 0) {
                            mediaId = Long.parseLong(location.substring(8, idx));
                            mediaIsVideo = false;
                            mediaThumbPath = location.substring(idx + 1);
                        }
                        canDeleteFile = false;
                    } else if (location.startsWith("vthumb://")) {
                        int idx = location.indexOf(":", 9);
                        if (idx >= 0) {
                            mediaId = Long.parseLong(location.substring(9, idx));
                            mediaIsVideo = true;
                        }
                        canDeleteFile = false;
                    } else if (!location.startsWith("http")) {
                        canDeleteFile = false;
                    }
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;

                if (Build.VERSION.SDK_INT < 21) {
                    opts.inPurgeable = true;
                }

                float w_filter = 0;
                float h_filter = 0;
                int blurType = 0;
                boolean checkInversion = false;
                boolean force8888 = canForce8888;
                try {
                    if (cacheImage.filter != null) {
                        String[] args = cacheImage.filter.split("_");
                        if (args.length >= 2) {
                            w_filter = Float.parseFloat(args[0]) * AndroidUtilities.density;
                            h_filter = Float.parseFloat(args[1]) * AndroidUtilities.density;
                        }
                        if (cacheImage.filter.contains("b2")) {
                            blurType = 3;
                        } else if (cacheImage.filter.contains("b1")) {
                            blurType = 2;
                        } else if (cacheImage.filter.contains("b")) {
                            blurType = 1;
                        }
                        if (cacheImage.filter.contains("i")) {
                            checkInversion = true;
                        }
                        if (cacheImage.filter.contains("f")) {
                            force8888 = true;
                        }
                        if (!useNativeWebpLoader && w_filter != 0 && h_filter != 0) {
                            opts.inJustDecodeBounds = true;

                            if (mediaId != null && mediaThumbPath == null) {
                                if (mediaIsVideo) {
                                    MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                                } else {
                                    MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                                }
                            } else {
                                if (secureDocumentKey != null) {
                                    RandomAccessFile f = new RandomAccessFile(cacheFileFinal, "r");
                                    int len = (int) f.length();
                                    byte[] bytes = bytesLocal.get();
                                    byte[] data = bytes != null && bytes.length >= len ? bytes : null;
                                    if (data == null) {
                                        bytes = data = new byte[len];
                                        bytesLocal.set(bytes);
                                    }
                                    f.readFully(data, 0, len);
                                    f.close();
                                    EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey);
                                    byte[] hash = Utilities.computeSHA256(data, 0, len);
                                    boolean error = false;
                                    if (secureDocumentHash == null || !Arrays.equals(hash, secureDocumentHash)) {
                                        error = true;
                                    }
                                    int offset = (data[0] & 0xff);
                                    len -= offset;
                                    if (!error) {
                                        BitmapFactory.decodeByteArray(data, offset, len, opts);
                                    }
                                } else {
                                    FileInputStream is;
                                    if (inEncryptedFile) {
                                        is = new EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath);
                                    } else {
                                        is = new FileInputStream(cacheFileFinal);
                                    }
                                    BitmapFactory.decodeStream(is, null, opts);
                                    is.close();
                                }
                            }

                            float photoW = opts.outWidth;
                            float photoH = opts.outHeight;
                            float scaleFactor;
                            if (w_filter >= h_filter && photoW > photoH) {
                                scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                            } else {
                                scaleFactor = Math.min(photoW / w_filter, photoH / h_filter);
                            }
                            if (scaleFactor < 1.2f) {
                                scaleFactor = 1;
                            }
                            opts.inJustDecodeBounds = false;
                            if (scaleFactor > 1.0f && (photoW > w_filter || photoH > h_filter)) {
                                int sample = 1;
                                do {
                                    sample *= 2;
                                } while (sample * 2 < scaleFactor);
                                opts.inSampleSize = sample;
                            } else {
                                opts.inSampleSize = (int) scaleFactor;
                            }
                        }
                    } else if (mediaThumbPath != null) {
                        opts.inJustDecodeBounds = true;
                        opts.inPreferredConfig = force8888 ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
                        FileInputStream is = new FileInputStream(cacheFileFinal);
                        image = BitmapFactory.decodeStream(is, null, opts);
                        is.close();
                        int photoW2 = opts.outWidth;
                        int photoH2 = opts.outHeight;
                        opts.inJustDecodeBounds = false;
                        float scaleFactor = Math.max(photoW2 / 200, photoH2 / 200);
                        if (scaleFactor < 1) {
                            scaleFactor = 1;
                        }
                        if (scaleFactor > 1.0f) {
                            int sample = 1;
                            do {
                                sample *= 2;
                            } while (sample * 2 < scaleFactor);
                            opts.inSampleSize = sample;
                        } else {
                            opts.inSampleSize = (int) scaleFactor;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }

                if (cacheImage.imageType == ImageReceiver.TYPE_THUMB) {
                    try {
                        lastCacheOutTime = System.currentTimeMillis();
                        synchronized (sync) {
                            if (isCancelled) {
                                return;
                            }
                        }

                        if (useNativeWebpLoader) {
                            RandomAccessFile file = new RandomAccessFile(cacheFileFinal, "r");
                            ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal.length());

                            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                            bmOptions.inJustDecodeBounds = true;
                            Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                            image = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);

                            Utilities.loadWebpImage(image, buffer, buffer.limit(), null, !opts.inPurgeable);
                            file.close();
                        } else {
                            if (opts.inPurgeable || secureDocumentKey != null) {
                                RandomAccessFile f = new RandomAccessFile(cacheFileFinal, "r");
                                int len = (int) f.length();
                                int offset = 0;
                                byte[] bytesThumb = bytesThumbLocal.get();
                                byte[] data = bytesThumb != null && bytesThumb.length >= len ? bytesThumb : null;
                                if (data == null) {
                                    bytesThumb = data = new byte[len];
                                    bytesThumbLocal.set(bytesThumb);
                                }
                                f.readFully(data, 0, len);
                                f.close();
                                boolean error = false;
                                if (secureDocumentKey != null) {
                                    EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey);
                                    byte[] hash = Utilities.computeSHA256(data, 0, len);
                                    if (secureDocumentHash == null || !Arrays.equals(hash, secureDocumentHash)) {
                                        error = true;
                                    }
                                    offset = (data[0] & 0xff);
                                    len -= offset;
                                } else if (inEncryptedFile) {
                                    EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, cacheImage.encryptionKeyPath);
                                }
                                if (!error) {
                                    image = BitmapFactory.decodeByteArray(data, offset, len, opts);
                                }
                            } else {
                                FileInputStream is;
                                if (inEncryptedFile) {
                                    is = new EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath);
                                } else {
                                    is = new FileInputStream(cacheFileFinal);
                                }
                                image = BitmapFactory.decodeStream(is, null, opts);
                                is.close();
                            }
                        }

                        if (image == null) {
                            if (cacheFileFinal.length() == 0 || cacheImage.filter == null) {
                                cacheFileFinal.delete();
                            }
                        } else {
                            if (cacheImage.filter != null) {
                                float bitmapW = image.getWidth();
                                float bitmapH = image.getHeight();
                                if (!opts.inPurgeable && w_filter != 0 && bitmapW != w_filter && bitmapW > w_filter + 20) {
                                    float scaleFactor = bitmapW / w_filter;
                                    Bitmap scaledBitmap = Bitmaps.createScaledBitmap(image, (int) w_filter, (int) (bitmapH / scaleFactor), true);
                                    if (image != scaledBitmap) {
                                        image.recycle();
                                        image = scaledBitmap;
                                    }
                                }
                            }
                            if (checkInversion) {
                                needInvert = Utilities.needInvert(image, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes()) != 0;
                            }
                            if (blurType == 1) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 3, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 2) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 1, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 3) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 0 && opts.inPurgeable) {
                                Utilities.pinBitmap(image);
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    try {
                        int delay = 20;
                        if (mediaId != null) {
                            delay = 0;
                        }
                        if (delay != 0 && lastCacheOutTime != 0 && lastCacheOutTime > System.currentTimeMillis() - delay && Build.VERSION.SDK_INT < 21) {
                            Thread.sleep(delay);
                        }
                        lastCacheOutTime = System.currentTimeMillis();
                        synchronized (sync) {
                            if (isCancelled) {
                                return;
                            }
                        }

                        if (force8888 || cacheImage.filter == null || blurType != 0 || cacheImage.imageLocation.path != null) {
                            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        } else {
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        }

                        opts.inDither = false;
                        if (mediaId != null && mediaThumbPath == null) {
                            if (mediaIsVideo) {
                                image = MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                            } else {
                                image = MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                            }
                        }
                        if (image == null) {
                            if (useNativeWebpLoader) {
                                RandomAccessFile file = new RandomAccessFile(cacheFileFinal, "r");
                                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal.length());

                                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                bmOptions.inJustDecodeBounds = true;
                                Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                                image = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);

                                Utilities.loadWebpImage(image, buffer, buffer.limit(), null, !opts.inPurgeable);
                                file.close();
                            } else {
                                if (opts.inPurgeable || secureDocumentKey != null) {
                                    RandomAccessFile f = new RandomAccessFile(cacheFileFinal, "r");
                                    int len = (int) f.length();
                                    int offset = 0;
                                    byte[] bytes = bytesLocal.get();
                                    byte[] data = bytes != null && bytes.length >= len ? bytes : null;
                                    if (data == null) {
                                        bytes = data = new byte[len];
                                        bytesLocal.set(bytes);
                                    }
                                    f.readFully(data, 0, len);
                                    f.close();
                                    boolean error = false;
                                    if (secureDocumentKey != null) {
                                        EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey);
                                        byte[] hash = Utilities.computeSHA256(data, 0, len);
                                        if (secureDocumentHash == null || !Arrays.equals(hash, secureDocumentHash)) {
                                            error = true;
                                        }
                                        offset = (data[0] & 0xff);
                                        len -= offset;
                                    } else if (inEncryptedFile) {
                                        EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, cacheImage.encryptionKeyPath);
                                    }
                                    if (!error) {
                                        image = BitmapFactory.decodeByteArray(data, offset, len, opts);
                                    }
                                } else {
                                    FileInputStream is;
                                    if (inEncryptedFile) {
                                        is = new EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath);
                                    } else {
                                        is = new FileInputStream(cacheFileFinal);
                                    }
                                    if (cacheImage.imageLocation.document instanceof TLRPC.TL_document) {
                                        try {
                                            ExifInterface exif = new ExifInterface(is);
                                            int attribute = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                                            switch (attribute) {
                                                case ExifInterface.ORIENTATION_ROTATE_90:
                                                    orientation = 90;
                                                    break;
                                                case ExifInterface.ORIENTATION_ROTATE_180:
                                                    orientation = 180;
                                                    break;
                                                case ExifInterface.ORIENTATION_ROTATE_270:
                                                    orientation = 270;
                                                    break;
                                            }
                                        } catch (Throwable ignore) {

                                        }
                                        is.getChannel().position(0);
                                    }
                                    image = BitmapFactory.decodeStream(is, null, opts);
                                    is.close();
                                }
                            }
                        }
                        if (image == null) {
                            if (canDeleteFile && (cacheFileFinal.length() == 0 || cacheImage.filter == null)) {
                                cacheFileFinal.delete();
                            }
                        } else {
                            boolean blured = false;
                            if (cacheImage.filter != null) {
                                float bitmapW = image.getWidth();
                                float bitmapH = image.getHeight();
                                if (!opts.inPurgeable && w_filter != 0 && bitmapW != w_filter && bitmapW > w_filter + 20) {
                                    Bitmap scaledBitmap;
                                    if (bitmapW > bitmapH && w_filter > h_filter) {
                                        float scaleFactor = bitmapW / w_filter;
                                        scaledBitmap = Bitmaps.createScaledBitmap(image, (int) w_filter, (int) (bitmapH / scaleFactor), true);
                                    } else {
                                        float scaleFactor = bitmapH / h_filter;
                                        scaledBitmap = Bitmaps.createScaledBitmap(image, (int) (bitmapW / scaleFactor), (int) h_filter, true);
                                    }
                                    if (image != scaledBitmap) {
                                        image.recycle();
                                        image = scaledBitmap;
                                    }
                                }
                                if (image != null) {
                                    if (checkInversion) {
                                        Bitmap b = image;
                                        int w = image.getWidth();
                                        int h = image.getHeight();
                                        if (w * h > 150 * 150) {
                                            b = Bitmaps.createScaledBitmap(image, 100, 100, false);
                                        }
                                        needInvert = Utilities.needInvert(b, opts.inPurgeable ? 0 : 1, b.getWidth(), b.getHeight(), b.getRowBytes()) != 0;
                                        if (b != image) {
                                            b.recycle();
                                        }
                                    }
                                    if (blurType != 0 && bitmapH < 100 && bitmapW < 100) {
                                        if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                            Utilities.blurBitmap(image, 3, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                        }
                                        blured = true;
                                    }
                                }
                            }
                            if (!blured && opts.inPurgeable) {
                                Utilities.pinBitmap(image);
                            }
                        }
                    } catch (Throwable ignore) {

                    }
                }
                Thread.interrupted();
                if (needInvert || orientation != 0) {
                    onPostExecute(image != null ? new ExtendedBitmapDrawable(image, needInvert, orientation) : null);
                } else {
                    onPostExecute(image != null ? new BitmapDrawable(image) : null);
                }
            }
        }

        private void onPostExecute(final Drawable drawable) {
            AndroidUtilities.runOnUIThread(() -> {
                Drawable toSet = null;
                String decrementKey = null;
                if (drawable instanceof RLottieDrawable) {
                    RLottieDrawable lottieDrawable = (RLottieDrawable) drawable;
                    toSet = lottieMemCache.get(cacheImage.key);
                    if (toSet == null) {
                        lottieMemCache.put(cacheImage.key, lottieDrawable);
                        toSet = lottieDrawable;
                    } else {
                        lottieDrawable.recycle();
                    }
                    if (toSet != null) {
                        incrementUseCount(cacheImage.key);
                        decrementKey = cacheImage.key;
                    }
                } else if (drawable instanceof AnimatedFileDrawable) {
                    toSet = drawable;
                } else if (drawable instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                    toSet = memCache.get(cacheImage.key);
                    if (toSet == null) {
                        memCache.put(cacheImage.key, bitmapDrawable);
                        toSet = bitmapDrawable;
                    } else {
                        Bitmap image = bitmapDrawable.getBitmap();
                        image.recycle();
                    }
                    if (toSet != null) {
                        incrementUseCount(cacheImage.key);
                        decrementKey = cacheImage.key;
                    }
                }
                final Drawable toSetFinal = toSet;
                final String decrementKetFinal = decrementKey;
                imageLoadQueue.postRunnable(() -> cacheImage.setImageAndClear(toSetFinal, decrementKetFinal));
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

    private class CacheImage {

        protected String key;
        protected String url;
        protected String filter;
        protected String ext;
        protected SecureDocument secureDocument;
        protected ImageLocation imageLocation;
        protected Object parentObject;
        protected int size;
        protected boolean animatedFile;
        protected boolean lottieFile;
        protected int imageType;

        protected int currentAccount;

        protected File finalFilePath;
        protected File tempFilePath;
        protected File encryptionKeyPath;

        protected ArtworkLoadTask artworkTask;
        protected HttpImageTask httpTask;
        protected CacheOutTask cacheTask;

        protected ArrayList<ImageReceiver> imageReceiverArray = new ArrayList<>();
        protected ArrayList<Integer> imageReceiverGuidsArray = new ArrayList<>();
        protected ArrayList<String> keys = new ArrayList<>();
        protected ArrayList<String> filters = new ArrayList<>();
        protected ArrayList<Integer> imageTypes = new ArrayList<>();

        public void addImageReceiver(ImageReceiver imageReceiver, String key, String filter, int type, int guid) {
            int index = imageReceiverArray.indexOf(imageReceiver);
            if (index >= 0) {
                imageReceiverGuidsArray.set(index, guid);
                return;
            }
            imageReceiverArray.add(imageReceiver);
            imageReceiverGuidsArray.add(guid);
            keys.add(key);
            filters.add(filter);
            imageTypes.add(type);
            imageLoadingByTag.put(imageReceiver.getTag(type), this);
        }

        public void replaceImageReceiver(ImageReceiver imageReceiver, String key, String filter, int type, int guid) {
            int index = imageReceiverArray.indexOf(imageReceiver);
            if (index == -1) {
                return;
            }
            if (imageTypes.get(index) != type) {
                index = imageReceiverArray.subList(index + 1, imageReceiverArray.size()).indexOf(imageReceiver);
                if (index == -1) {
                    return;
                }
            }
            imageReceiverGuidsArray.set(index, guid);
            keys.set(index, key);
            filters.set(index, filter);
        }

        public void removeImageReceiver(ImageReceiver imageReceiver) {
            int currentImageType = imageType;
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver obj = imageReceiverArray.get(a);
                if (obj == null || obj == imageReceiver) {
                    imageReceiverArray.remove(a);
                    imageReceiverGuidsArray.remove(a);
                    keys.remove(a);
                    filters.remove(a);
                    currentImageType = imageTypes.remove(a);
                    if (obj != null) {
                        imageLoadingByTag.remove(obj.getTag(currentImageType));
                    }
                    a--;
                }
            }
            if (imageReceiverArray.isEmpty()) {
                if (imageLocation != null) {
                    if (!forceLoadingImages.containsKey(key)) {
                        if (imageLocation.location != null) {
                            FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation.location, ext);
                        } else if (imageLocation.document != null) {
                            FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation.document);
                        } else if (imageLocation.secureDocument != null) {
                            FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation.secureDocument);
                        } else if (imageLocation.webFile != null) {
                            FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation.webFile);
                        }
                    }
                }
                if (cacheTask != null) {
                    if (currentImageType == ImageReceiver.TYPE_THUMB) {
                        cacheThumbOutQueue.cancelRunnable(cacheTask);
                    } else {
                        cacheOutQueue.cancelRunnable(cacheTask);
                    }
                    cacheTask.cancel();
                    cacheTask = null;
                }
                if (httpTask != null) {
                    httpTasks.remove(httpTask);
                    httpTask.cancel(true);
                    httpTask = null;
                }
                if (artworkTask != null) {
                    artworkTasks.remove(artworkTask);
                    artworkTask.cancel(true);
                    artworkTask = null;
                }
                if (url != null) {
                    imageLoadingByUrl.remove(url);
                }
                if (key != null) {
                    imageLoadingByKeys.remove(key);
                }
            }
        }

        public void setImageAndClear(final Drawable image, String decrementKey) {
            if (image != null) {
                final ArrayList<ImageReceiver> finalImageReceiverArray = new ArrayList<>(imageReceiverArray);
                final ArrayList<Integer> finalImageReceiverGuidsArray = new ArrayList<>(imageReceiverGuidsArray);
                AndroidUtilities.runOnUIThread(() -> {
                    if (image instanceof AnimatedFileDrawable) {
                        boolean imageSet = false;
                        AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                        for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                            ImageReceiver imgView = finalImageReceiverArray.get(a);
                            AnimatedFileDrawable toSet = (a == 0 ? fileDrawable : fileDrawable.makeCopy());
                            if (imgView.setImageBitmapByKey(toSet, key, imageType, false, finalImageReceiverGuidsArray.get(a))) {
                                if (toSet == fileDrawable) {
                                    imageSet = true;
                                }
                            } else {
                                if (toSet != fileDrawable) {
                                    toSet.recycle();
                                }
                            }
                        }
                        if (!imageSet) {
                            fileDrawable.recycle();
                        }
                    } else {
                        for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                            ImageReceiver imgView = finalImageReceiverArray.get(a);
                            imgView.setImageBitmapByKey(image, key, imageTypes.get(a), false, finalImageReceiverGuidsArray.get(a));
                        }
                    }
                    if (decrementKey != null) {
                        decrementUseCount(decrementKey);
                    }
                });
            }
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver imageReceiver = imageReceiverArray.get(a);
                imageLoadingByTag.remove(imageReceiver.getTag(imageType));
            }
            imageReceiverArray.clear();
            imageReceiverGuidsArray.clear();
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
        thumbGeneratingQueue.setPriority(Thread.MIN_PRIORITY);

        int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        int maxSize;
        if (canForce8888 = memoryClass >= 192) {
            maxSize = 30;
        } else {
            maxSize = 15;
        }
        int cacheSize = Math.min(maxSize, memoryClass / 7) * 1024 * 1024;

        memCache = new LruCache<BitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                return value.getBitmap().getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, final BitmapDrawable oldValue, BitmapDrawable newValue) {
                if (ignoreRemoval != null && ignoreRemoval.equals(key)) {
                    return;
                }
                final Integer count = bitmapUseCounts.get(key);
                if (count == null || count == 0) {
                    Bitmap b = oldValue.getBitmap();
                    if (!b.isRecycled()) {
                        b.recycle();
                    }
                }
            }
        };

        lottieMemCache = new LruCache<RLottieDrawable>(512 * 512 * 2 * 4 * 5) {
            @Override
            protected int sizeOf(String key, RLottieDrawable value) {
                return value.getIntrinsicWidth() * value.getIntrinsicHeight() * 4 * 2;
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, final RLottieDrawable oldValue, RLottieDrawable newValue) {
                final Integer count = bitmapUseCounts.get(key);
                if (count == null || count == 0) {
                    oldValue.recycle();
                }
            }
        };

        SparseArray<File> mediaDirs = new SparseArray<>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            new File(cachePath, ".nomedia").createNewFile();
        } catch (Exception e) {
            FileLog.e(e);
        }
        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int currentAccount = a;
            FileLoader.getInstance(a).setDelegate(new FileLoader.FileLoaderDelegate() {
                @Override
                public void fileUploadProgressChanged(final String location, final float progress, final boolean isEncrypted) {
                    fileProgresses.put(location, progress);
                    long currentTime = System.currentTimeMillis();
                    if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                        lastProgressUpdateTime = currentTime;

                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileUploadProgressChanged, location, progress, isEncrypted));
                    }
                }

                @Override
                public void fileDidUploaded(final String location, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv, final long totalFileSize) {
                    Utilities.stageQueue.postRunnable(() -> {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileDidUpload, location, inputFile, inputEncryptedFile, key, iv, totalFileSize));
                        fileProgresses.remove(location);
                    });
                }

                @Override
                public void fileDidFailedUpload(final String location, final boolean isEncrypted) {
                    Utilities.stageQueue.postRunnable(() -> {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileDidFailUpload, location, isEncrypted));
                        fileProgresses.remove(location);
                    });
                }

                @Override
                public void fileDidLoaded(final String location, final File finalFile, final int type) {
                    fileProgresses.remove(location);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (SharedConfig.saveToGallery && telegramPath != null && finalFile != null && (location.endsWith(".mp4") || location.endsWith(".jpg"))) {
                            if (finalFile.toString().startsWith(telegramPath.toString())) {
                                AndroidUtilities.addMediaToGallery(finalFile.toString());
                            }
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileDidLoad, location, finalFile);
                        ImageLoader.this.fileDidLoaded(location, finalFile, type);
                    });
                }

                @Override
                public void fileDidFailedLoad(final String location, final int canceled) {
                    fileProgresses.remove(location);
                    AndroidUtilities.runOnUIThread(() -> {
                        ImageLoader.this.fileDidFailedLoad(location, canceled);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileDidFailToLoad, location, canceled);
                    });
                }

                @Override
                public void fileLoadProgressChanged(final String location, final float progress) {
                    fileProgresses.put(location, progress);
                    long currentTime = System.currentTimeMillis();
                    if (lastProgressUpdateTime == 0 || lastProgressUpdateTime < currentTime - 500) {
                        lastProgressUpdateTime = currentTime;
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.FileLoadProgressChanged, location, progress));
                    }
                }
            });
        }
        FileLoader.setMediaDirs(mediaDirs);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("file system changed");
                }
                Runnable r = () -> checkMediaPaths();
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
        try {
            ApplicationLoader.applicationContext.registerReceiver(receiver, filter);
        } catch (Throwable ignore) {

        }

        checkMediaPaths();
    }

    public void checkMediaPaths() {
        cacheOutQueue.postRunnable(() -> {
            final SparseArray<File> paths = createMediaPaths();
            AndroidUtilities.runOnUIThread(() -> FileLoader.setMediaDirs(paths));
        });
    }

    public void addTestWebFile(String url, WebFile webFile) {
        if (url == null || webFile == null) {
            return;
        }
        testWebFile.put(url, webFile);
    }

    public void removeTestWebFile(String url) {
        if (url == null) {
            return;
        }
        testWebFile.remove(url);
    }

    public SparseArray<File> createMediaPaths() {
        SparseArray<File> mediaDirs = new SparseArray<>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            new File(cachePath, ".nomedia").createNewFile();
        } catch (Exception e) {
            FileLog.e(e);
        }

        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("cache path = " + cachePath);
        }

        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                telegramPath.mkdirs();

                if (telegramPath.isDirectory()) {
                    try {
                        File imagePath = new File(telegramPath, "Telegram Images");
                        imagePath.mkdir();
                        if (imagePath.isDirectory() && canMoveFiles(cachePath, imagePath, FileLoader.MEDIA_DIR_IMAGE)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE, imagePath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("image path = " + imagePath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    try {
                        File videoPath = new File(telegramPath, "Telegram Video");
                        videoPath.mkdir();
                        if (videoPath.isDirectory() && canMoveFiles(cachePath, videoPath, FileLoader.MEDIA_DIR_VIDEO)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO, videoPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("video path = " + videoPath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    try {
                        File audioPath = new File(telegramPath, "Telegram Audio");
                        audioPath.mkdir();
                        if (audioPath.isDirectory() && canMoveFiles(cachePath, audioPath, FileLoader.MEDIA_DIR_AUDIO)) {
                            new File(audioPath, ".nomedia").createNewFile();
                            mediaDirs.put(FileLoader.MEDIA_DIR_AUDIO, audioPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("audio path = " + audioPath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    try {
                        File documentPath = new File(telegramPath, "Telegram Documents");
                        documentPath.mkdir();
                        if (documentPath.isDirectory() && canMoveFiles(cachePath, documentPath, FileLoader.MEDIA_DIR_DOCUMENT)) {
                            new File(documentPath, ".nomedia").createNewFile();
                            mediaDirs.put(FileLoader.MEDIA_DIR_DOCUMENT, documentPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("documents path = " + documentPath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("this Android can't rename files");
                }
            }
            SharedConfig.checkSaveToGalleryFiles();
        } catch (Exception e) {
            FileLog.e(e);
        }

        return mediaDirs;
    }

    private boolean canMoveFiles(File from, File to, int type) {
        RandomAccessFile file = null;
        try {
            File srcFile = null;
            File dstFile = null;
            if (type == FileLoader.MEDIA_DIR_IMAGE) {
                srcFile = new File(from, "000000000_999999_temp.jpg");
                dstFile = new File(to, "000000000_999999.jpg");
            } else if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                srcFile = new File(from, "000000000_999999_temp.doc");
                dstFile = new File(to, "000000000_999999.doc");
            } else if (type == FileLoader.MEDIA_DIR_AUDIO) {
                srcFile = new File(from, "000000000_999999_temp.ogg");
                dstFile = new File(to, "000000000_999999.ogg");
            } else if (type == FileLoader.MEDIA_DIR_VIDEO) {
                srcFile = new File(from, "000000000_999999_temp.mp4");
                dstFile = new File(to, "000000000_999999.mp4");
            }
            byte[] buffer = new byte[1024];
            srcFile.createNewFile();
            file = new RandomAccessFile(srcFile, "rws");
            file.write(buffer);
            file.close();
            file = null;
            boolean canRename = srcFile.renameTo(dstFile);
            srcFile.delete();
            dstFile.delete();
            if (canRename) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public Float getFileProgress(String location) {
        if (location == null) {
            return null;
        }
        return fileProgresses.get(location);
    }

    public String getReplacedKey(String oldKey) {
        if (oldKey == null) {
            return null;
        }
        return replacedBitmaps.get(oldKey);
    }

    private void performReplace(String oldKey, String newKey) {
        BitmapDrawable b = memCache.get(oldKey);
        replacedBitmaps.put(oldKey, newKey);
        if (b != null) {
            BitmapDrawable oldBitmap = memCache.get(newKey);
            boolean dontChange = false;
            if (oldBitmap != null && oldBitmap.getBitmap() != null && b.getBitmap() != null) {
                Bitmap oldBitmapObject = oldBitmap.getBitmap();
                Bitmap newBitmapObject = b.getBitmap();
                if (oldBitmapObject.getWidth() > newBitmapObject.getWidth() || oldBitmapObject.getHeight() > newBitmapObject.getHeight()) {
                    dontChange = true;
                }
            }
            if (!dontChange) {
                ignoreRemoval = oldKey;
                memCache.remove(oldKey);
                memCache.put(newKey, b);
                ignoreRemoval = null;
            } else {
                memCache.remove(oldKey);
            }
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

    public boolean isInMemCache(String key, boolean animated) {
        if (animated) {
            return lottieMemCache.get(key) != null;
        } else {
            return memCache.get(key) != null;
        }
    }

    public void clearMemory() {
        memCache.evictAll();
        lottieMemCache.evictAll();
    }

    private void removeFromWaitingForThumb(int TAG, ImageReceiver imageReceiver) {
        String location = waitingForQualityThumbByTag.get(TAG);
        if (location != null) {
            ThumbGenerateInfo info = waitingForQualityThumb.get(location);
            if (info != null) {
                int index = info.imageReceiverArray.indexOf(imageReceiver);
                if (index >= 0) {
                    info.imageReceiverArray.remove(index);
                    info.imageReceiverGuidsArray.remove(index);
                }
                if (info.imageReceiverArray.isEmpty()) {
                    waitingForQualityThumb.remove(location);
                }
            }
            waitingForQualityThumbByTag.remove(TAG);
        }
    }

    public void cancelLoadingForImageReceiver(final ImageReceiver imageReceiver, final boolean cancelAll) {
        if (imageReceiver == null) {
            return;
        }
        imageLoadQueue.postRunnable(() -> {
            for (int a = 0; a < 3; a++) {
                int imageType;
                if (a > 0 && !cancelAll) {
                    return;
                }
                if (a == 0) {
                    imageType = ImageReceiver.TYPE_THUMB;
                } else if (a == 1) {
                    imageType = ImageReceiver.TYPE_IMAGE;
                } else {
                    imageType = ImageReceiver.TYPE_MEDIA;
                }
                int TAG = imageReceiver.getTag(imageType);
                if (TAG != 0) {
                    if (a == 0) {
                        removeFromWaitingForThumb(TAG, imageReceiver);
                    }
                    CacheImage ei = imageLoadingByTag.get(TAG);
                    if (ei != null) {
                        ei.removeImageReceiver(imageReceiver);
                    }
                }
            }
        });
    }

    public BitmapDrawable getAnyImageFromMemory(String key) {
        BitmapDrawable drawable = memCache.get(key);
        if (drawable == null) {
            ArrayList<String> filters = memCache.getFilterKeys(key);
            if (filters != null && !filters.isEmpty()) {
                return memCache.get(key + "@" + filters.get(0));
            }
        }
        return drawable;
    }

    public BitmapDrawable getImageFromMemory(TLObject fileLocation, String httpUrl, String filter) {
        if (fileLocation == null && httpUrl == null) {
            return null;
        }
        String key = null;
        if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
        } else {
            if (fileLocation instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation location = (TLRPC.FileLocation) fileLocation;
                key = location.volume_id + "_" + location.local_id;
            } else if (fileLocation instanceof TLRPC.Document) {
                TLRPC.Document location = (TLRPC.Document) fileLocation;
                key = location.dc_id + "_" + location.id;
            } else if (fileLocation instanceof SecureDocument) {
                SecureDocument location = (SecureDocument) fileLocation;
                key = location.secureFile.dc_id + "_" + location.secureFile.id;
            } else if (fileLocation instanceof WebFile) {
                WebFile location = (WebFile) fileLocation;
                key = Utilities.MD5(location.url);
            }
        }
        if (filter != null) {
            key += "@" + filter;
        }
        return memCache.get(key);
    }

    private void replaceImageInCacheInternal(final String oldKey, final String newKey, final ImageLocation newLocation) {
        ArrayList<String> arr = memCache.getFilterKeys(oldKey);
        if (arr != null) {
            for (int a = 0; a < arr.size(); a++) {
                String filter = arr.get(a);
                String oldK = oldKey + "@" + filter;
                String newK = newKey + "@" + filter;
                performReplace(oldK, newK);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldK, newK, newLocation);
            }
        } else {
            performReplace(oldKey, newKey);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldKey, newKey, newLocation);
        }
    }

    public void replaceImageInCache(final String oldKey, final String newKey, final ImageLocation newLocation, boolean post) {
        if (post) {
            AndroidUtilities.runOnUIThread(() -> replaceImageInCacheInternal(oldKey, newKey, newLocation));
        } else {
            replaceImageInCacheInternal(oldKey, newKey, newLocation);
        }
    }

    public void putImageToCache(BitmapDrawable bitmap, String key) {
        memCache.put(key, bitmap);
    }

    private void generateThumb(int mediaType, File originalPath, ThumbGenerateInfo info) {
        if (mediaType != FileLoader.MEDIA_DIR_IMAGE && mediaType != FileLoader.MEDIA_DIR_VIDEO && mediaType != FileLoader.MEDIA_DIR_DOCUMENT || originalPath == null || info == null) {
            return;
        }
        String name = FileLoader.getAttachFileName(info.parentDocument);
        ThumbGenerateTask task = thumbGenerateTasks.get(name);
        if (task == null) {
            task = new ThumbGenerateTask(mediaType, originalPath, info);
            thumbGeneratingQueue.postRunnable(task);
        }
    }

    public void cancelForceLoadingForImageReceiver(final ImageReceiver imageReceiver) {
        if (imageReceiver == null) {
            return;
        }
        final String key = imageReceiver.getImageKey();
        if (key == null) {
            return;
        }
        imageLoadQueue.postRunnable(() -> forceLoadingImages.remove(key));
    }

    private void createLoadOperationForImageReceiver(final ImageReceiver imageReceiver, final String key, final String url, final String ext, final ImageLocation imageLocation, final String filter, final int size, final int cacheType, final int imageType, final int thumb, int guid) {
        if (imageReceiver == null || url == null || key == null || imageLocation == null) {
            return;
        }
        int TAG = imageReceiver.getTag(imageType);
        if (TAG == 0) {
            imageReceiver.setTag(TAG = lastImageNum, imageType);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE) {
                lastImageNum = 0;
            }
        }

        final int finalTag = TAG;
        final boolean finalIsNeedsQualityThumb = imageReceiver.isNeedsQualityThumb();
        final Object parentObject = imageReceiver.getParentObject();
        final TLRPC.Document qualityDocument = imageReceiver.getQulityThumbDocument();
        final boolean shouldGenerateQualityThumb = imageReceiver.isShouldGenerateQualityThumb();
        final int currentAccount = imageReceiver.getCurrentAccount();
        final boolean currentKeyQuality = imageType == ImageReceiver.TYPE_IMAGE && imageReceiver.isCurrentKeyQuality();
        imageLoadQueue.postRunnable(() -> {
            boolean added = false;
            if (thumb != 2) {
                CacheImage alreadyLoadingUrl = imageLoadingByUrl.get(url);
                CacheImage alreadyLoadingCache = imageLoadingByKeys.get(key);
                CacheImage alreadyLoadingImage = imageLoadingByTag.get(finalTag);
                if (alreadyLoadingImage != null) {
                    if (alreadyLoadingImage == alreadyLoadingCache) {
                        added = true;
                    } else if (alreadyLoadingImage == alreadyLoadingUrl) {
                        if (alreadyLoadingCache == null) {
                            alreadyLoadingImage.replaceImageReceiver(imageReceiver, key, filter, imageType, guid);
                        }
                        added = true;
                    } else {
                        alreadyLoadingImage.removeImageReceiver(imageReceiver);
                    }
                }

                if (!added && alreadyLoadingCache != null) {
                    alreadyLoadingCache.addImageReceiver(imageReceiver, key, filter, imageType, guid);
                    added = true;
                }
                if (!added && alreadyLoadingUrl != null) {
                    alreadyLoadingUrl.addImageReceiver(imageReceiver, key, filter, imageType, guid);
                    added = true;
                }
            }

            if (!added) {
                boolean onlyCache = false;
                boolean isQuality = false;
                File cacheFile = null;
                boolean cacheFileExists = false;

                if (imageLocation.path != null) {
                    String location = imageLocation.path;
                    if (!location.startsWith("http") && !location.startsWith("athumb")) {
                        onlyCache = true;
                        if (location.startsWith("thumb://")) {
                            int idx = location.indexOf(":", 8);
                            if (idx >= 0) {
                                cacheFile = new File(location.substring(idx + 1));
                            }
                        } else if (location.startsWith("vthumb://")) {
                            int idx = location.indexOf(":", 9);
                            if (idx >= 0) {
                                cacheFile = new File(location.substring(idx + 1));
                            }
                        } else {
                            cacheFile = new File(location);
                        }
                    }
                } else if (thumb == 0 && currentKeyQuality) {
                    onlyCache = true;

                    TLRPC.Document parentDocument;
                    String localPath;
                    File cachePath;
                    boolean forceCache;
                    String fileName;
                    int fileType;
                    boolean bigThumb;
                    if (parentObject instanceof MessageObject) {
                        MessageObject parentMessageObject = (MessageObject) parentObject;
                        parentDocument = parentMessageObject.getDocument();
                        localPath = parentMessageObject.messageOwner.attachPath;
                        cachePath = FileLoader.getPathToMessage(parentMessageObject.messageOwner);
                        fileType = parentMessageObject.getFileType();
                        bigThumb = false;
                    } else if (qualityDocument != null) {
                        parentDocument = qualityDocument;
                        cachePath = FileLoader.getPathToAttach(parentDocument, true);
                        if (MessageObject.isVideoDocument(parentDocument)) {
                            fileType = FileLoader.MEDIA_DIR_VIDEO;
                        } else {
                            fileType = FileLoader.MEDIA_DIR_DOCUMENT;
                        }
                        localPath = null;
                        bigThumb = true;
                    } else {
                        parentDocument = null;
                        localPath = null;
                        cachePath = null;
                        fileType = 0;
                        bigThumb = false;
                    }
                    if (parentDocument != null) {
                        if (finalIsNeedsQualityThumb) {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "q_" + parentDocument.dc_id + "_" + parentDocument.id + ".jpg");
                            if (!cacheFile.exists()) {
                                cacheFile = null;
                            } else {
                                cacheFileExists = true;
                            }
                        }

                        File attachPath = null;
                        if (!TextUtils.isEmpty(localPath)) {
                            attachPath = new File(localPath);
                            if (!attachPath.exists()) {
                                attachPath = null;
                            }
                        }
                        if (attachPath == null) {
                            attachPath = cachePath;
                        }

                        if (cacheFile == null) {
                            String location = FileLoader.getAttachFileName(parentDocument);
                            ThumbGenerateInfo info = waitingForQualityThumb.get(location);
                            if (info == null) {
                                info = new ThumbGenerateInfo();
                                info.parentDocument = parentDocument;
                                info.filter = filter;
                                info.big = bigThumb;
                                waitingForQualityThumb.put(location, info);
                            }
                            if (!info.imageReceiverArray.contains(imageReceiver)) {
                                info.imageReceiverArray.add(imageReceiver);
                                info.imageReceiverGuidsArray.add(guid);
                            }
                            waitingForQualityThumbByTag.put(finalTag, location);
                            if (attachPath.exists() && shouldGenerateQualityThumb) {
                                generateThumb(fileType, attachPath, info);
                            }
                            return;
                        }
                    }
                }

                if (thumb != 2) {
                    boolean isEncrypted = imageLocation.isEncrypted();
                    CacheImage img = new CacheImage();
                    if (!currentKeyQuality) {
                        if (MessageObject.isGifDocument(imageLocation.webFile) || MessageObject.isGifDocument(imageLocation.document) || MessageObject.isRoundVideoDocument(imageLocation.document)) {
                            img.animatedFile = true;
                        } else if (imageLocation.path != null) {
                            String location = imageLocation.path;
                            if (!location.startsWith("vthumb") && !location.startsWith("thumb")) {
                                String trueExt = getHttpUrlExtension(location, "jpg");
                                if (trueExt.equals("mp4") || trueExt.equals("gif")) {
                                    img.animatedFile = true;
                                }
                            }
                        }
                    }

                    if (cacheFile == null) {
                        int fileSize = 0;
                        if (imageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize) {
                            onlyCache = true;
                        } else if (imageLocation.secureDocument != null) {
                            img.secureDocument = imageLocation.secureDocument;
                            onlyCache = img.secureDocument.secureFile.dc_id == Integer.MIN_VALUE;
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                        } else if (!AUTOPLAY_FILTER.equals(filter) && (cacheType != 0 || size <= 0 || imageLocation.path != null || isEncrypted)) {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                            if (cacheFile.exists()) {
                                cacheFileExists = true;
                            } else if (cacheType == 2) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url + ".enc");
                            }
                            if (imageLocation.document != null) {
                                img.lottieFile = "application/x-tgsticker".equals(imageLocation.document.mime_type);
                            }
                        } else if (imageLocation.document != null) {
                            TLRPC.Document document = imageLocation.document;
                            if (document instanceof TLRPC.TL_documentEncrypted) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                            } else if (MessageObject.isVideoDocument(document)) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO), url);
                            } else {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url);
                            }
                            if (AUTOPLAY_FILTER.equals(filter) && !cacheFile.exists()) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document.dc_id + "_" + document.id + ".temp");
                            }
                            img.lottieFile = "application/x-tgsticker".equals(document.mime_type);
                            fileSize = document.size;
                        } else if (imageLocation.webFile != null) {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url);
                        } else {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE), url);
                        }
                        if (AUTOPLAY_FILTER.equals(filter)) {
                            img.animatedFile = true;
                            img.size = fileSize;
                            onlyCache = true;
                        }
                    }

                    img.imageType = imageType;
                    img.key = key;
                    img.filter = filter;
                    img.imageLocation = imageLocation;
                    img.ext = ext;
                    img.currentAccount = currentAccount;
                    img.parentObject = parentObject;
                    if (imageLocation.lottieAnimation) {
                        img.lottieFile = true;
                    }
                    if (cacheType == 2) {
                        img.encryptionKeyPath = new File(FileLoader.getInternalCacheDir(), url + ".enc.key");
                    }
                    img.addImageReceiver(imageReceiver, key, filter, imageType, guid);
                    if (onlyCache || cacheFileExists || cacheFile.exists()) {
                        img.finalFilePath = cacheFile;
                        img.imageLocation = imageLocation;
                        img.cacheTask = new CacheOutTask(img);
                        imageLoadingByKeys.put(key, img);
                        if (thumb != 0) {
                            cacheThumbOutQueue.postRunnable(img.cacheTask);
                        } else {
                            cacheOutQueue.postRunnable(img.cacheTask);
                        }
                    } else {
                        img.url = url;

                        imageLoadingByUrl.put(url, img);
                        if (imageLocation.path != null) {
                            String file = Utilities.MD5(imageLocation.path);
                            File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                            img.tempFilePath = new File(cacheDir, file + "_temp.jpg");
                            img.finalFilePath = cacheFile;
                            if (imageLocation.path.startsWith("athumb")) {
                                img.artworkTask = new ArtworkLoadTask(img);
                                artworkTasks.add(img.artworkTask);
                                runArtworkTasks(false);
                            } else {
                                img.httpTask = new HttpImageTask(img, size);
                                httpTasks.add(img.httpTask);
                                runHttpTasks(false);
                            }
                        } else {
                            if (imageLocation.location != null) {
                                int localCacheType = cacheType;
                                if (localCacheType == 0 && (size <= 0 || imageLocation.key != null)) {
                                    localCacheType = 1;
                                }
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation, parentObject, ext, thumb != 0 ? 2 : 1, localCacheType);
                            } else if (imageLocation.document != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.document, parentObject, thumb != 0 ? 2 : 1, cacheType);
                            } else if (imageLocation.secureDocument != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.secureDocument, thumb != 0 ? 2 : 1);
                            } else if (imageLocation.webFile != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.webFile, thumb != 0 ? 2 : 1, cacheType);
                            }
                            if (imageReceiver.isForceLoding()) {
                                forceLoadingImages.put(img.key, 0);
                            }
                        }
                    }
                }
            }
        });
    }

    public void loadImageForImageReceiver(ImageReceiver imageReceiver) {
        if (imageReceiver == null) {
            return;
        }

        boolean imageSet = false;
        String mediaKey = imageReceiver.getMediaKey();
        int guid = imageReceiver.getNewGuid();
        if (mediaKey != null) {
            ImageLocation mediaLocation = imageReceiver.getMediaLocation();
            Drawable drawable;
            if (mediaLocation != null && (MessageObject.isAnimatedStickerDocument(mediaLocation.document) || mediaLocation.lottieAnimation)) {
                drawable = lottieMemCache.get(mediaKey);
            } else {
                drawable = memCache.get(mediaKey);
                if (drawable != null) {
                    memCache.moveToFront(mediaKey);
                }
            }
            if (drawable != null) {
                cancelLoadingForImageReceiver(imageReceiver, true);
                imageReceiver.setImageBitmapByKey(drawable, mediaKey, ImageReceiver.TYPE_MEDIA, true, guid);
                imageSet = true;
                if (!imageReceiver.isForcePreview()) {
                    return;
                }
            }
        }
        String imageKey = imageReceiver.getImageKey();
        if (!imageSet && imageKey != null) {
            ImageLocation imageLocation = imageReceiver.getImageLocation();
            Drawable drawable;
            if (imageLocation != null && (MessageObject.isAnimatedStickerDocument(imageLocation.document) || imageLocation.lottieAnimation)) {
                drawable = lottieMemCache.get(imageKey);
            } else {
                drawable = memCache.get(imageKey);
                if (drawable != null) {
                    memCache.moveToFront(imageKey);
                }
            }
            if (drawable != null) {
                cancelLoadingForImageReceiver(imageReceiver, true);
                imageReceiver.setImageBitmapByKey(drawable, imageKey, ImageReceiver.TYPE_IMAGE, true, guid);
                imageSet = true;
                if (!imageReceiver.isForcePreview() && mediaKey == null) {
                    return;
                }
            }
        }
        boolean thumbSet = false;
        String thumbKey = imageReceiver.getThumbKey();
        if (thumbKey != null) {
            ImageLocation thumbLocation = imageReceiver.getThumbLocation();
            Drawable drawable;
            if (thumbLocation != null && (MessageObject.isAnimatedStickerDocument(thumbLocation.document) || thumbLocation.lottieAnimation)) {
                drawable = lottieMemCache.get(imageKey);
            } else {
                drawable = memCache.get(thumbKey);
                if (drawable != null) {
                    memCache.moveToFront(thumbKey);
                }
            }
            if (drawable != null) {
                imageReceiver.setImageBitmapByKey(drawable, thumbKey, ImageReceiver.TYPE_THUMB, true, guid);
                cancelLoadingForImageReceiver(imageReceiver, false);
                if (imageSet && imageReceiver.isForcePreview()) {
                    return;
                }
                thumbSet = true;
            }
        }

        boolean qualityThumb = false;
        Object parentObject = imageReceiver.getParentObject();
        TLRPC.Document qualityDocument = imageReceiver.getQulityThumbDocument();
        ImageLocation thumbLocation = imageReceiver.getThumbLocation();
        String thumbFilter = imageReceiver.getThumbFilter();
        ImageLocation mediaLocation = imageReceiver.getMediaLocation();
        String mediaFilter = imageReceiver.getMediaFilter();
        ImageLocation imageLocation = imageReceiver.getImageLocation();
        String imageFilter = imageReceiver.getImageFilter();
        if (imageLocation == null && imageReceiver.isNeedsQualityThumb() && imageReceiver.isCurrentKeyQuality()) {
            if (parentObject instanceof MessageObject) {
                MessageObject messageObject = (MessageObject) parentObject;
                imageLocation = ImageLocation.getForDocument(messageObject.getDocument());
                qualityThumb = true;
            } else if (qualityDocument != null) {
                imageLocation = ImageLocation.getForDocument(qualityDocument);
                qualityThumb = true;
            }
        }
        boolean saveImageToCache = false;

        String imageUrl = null;
        String thumbUrl = null;
        String mediaUrl = null;
        imageKey = null;
        thumbKey = null;
        mediaKey = null;
        String ext = imageReceiver.getExt();
        if (ext == null) {
            ext = "jpg";
        }

        for (int a = 0; a < 2; a++) {
            ImageLocation object;
            if (a == 0) {
                object = imageLocation;
            } else {
                object = mediaLocation;
            }
            if (object == null) {
                continue;
            }
            String key = object.getKey(parentObject, mediaLocation != null ? mediaLocation : imageLocation);
            if (key == null) {
                continue;
            }
            String url = null;
            if (object.path != null) {
                url = key + "." + getHttpUrlExtension(object.path, "jpg");
            } else if (object.photoSize instanceof TLRPC.TL_photoStrippedSize) {
                url = key + "." + ext;
            } else if (object.location != null) {
                url = key + "." + ext;
                if (imageReceiver.getExt() != null || object.location.key != null || object.location.volume_id == Integer.MIN_VALUE && object.location.local_id < 0) {
                    saveImageToCache = true;
                }
            } else if (object.webFile != null) {
                String defaultExt = FileLoader.getMimeTypePart(object.webFile.mime_type);
                url = key + "." + getHttpUrlExtension(object.webFile.url, defaultExt);
            } else if (object.secureDocument != null) {
                url = key + "." + ext;
            } else if (object.document != null) {
                if (a == 0 && qualityThumb) {
                    key = "q_" + key;
                }
                String docExt = FileLoader.getDocumentFileName(object.document);
                int idx;
                if (docExt == null || (idx = docExt.lastIndexOf('.')) == -1) {
                    docExt = "";
                } else {
                    docExt = docExt.substring(idx);
                }
                if (docExt.length() <= 1) {
                    if ("video/mp4".equals(object.document.mime_type)) {
                        docExt = ".mp4";
                    } else if ("video/x-matroska".equals(object.document.mime_type)) {
                        docExt = ".mkv";
                    } else {
                        docExt = "";
                    }
                }
                url = key + docExt;
                saveImageToCache = !MessageObject.isVideoDocument(object.document) && !MessageObject.isGifDocument(object.document) && !MessageObject.isRoundVideoDocument(object.document) && !MessageObject.canPreviewDocument(object.document);
            }
            if (a == 0) {
                imageKey = key;
                imageUrl = url;
            } else {
                mediaKey = key;
                mediaUrl = url;
            }
            if (object == thumbLocation) {
                if (a == 0) {
                    imageLocation = null;
                    imageKey = null;
                    imageUrl = null;
                } else {
                    mediaLocation = null;
                    mediaKey = null;
                    mediaUrl = null;
                }
            }
        }

        if (thumbLocation != null) {
            ImageLocation strippedLoc = imageReceiver.getStrippedLocation();
            if (strippedLoc == null) {
                strippedLoc = mediaLocation != null ? mediaLocation : imageLocation;
            }
            thumbKey = thumbLocation.getKey(parentObject, strippedLoc);
            if (thumbLocation.path != null) {
                thumbUrl = thumbKey + "." + getHttpUrlExtension(thumbLocation.path, "jpg");
            } else if (thumbLocation.photoSize instanceof TLRPC.TL_photoStrippedSize) {
                thumbUrl = thumbKey + "." + ext;
            } else if (thumbLocation.location != null) {
                thumbUrl = thumbKey + "." + ext;
            }
        }

        if (mediaKey != null && mediaFilter != null) {
            mediaKey += "@" + mediaFilter;
        }
        if (imageKey != null && imageFilter != null) {
            imageKey += "@" + imageFilter;
        }
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (imageLocation != null && imageLocation.path != null) {
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, ext, thumbLocation, thumbFilter, 0, 1, ImageReceiver.TYPE_THUMB, thumbSet ? 2 : 1, guid);
            createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, ext, imageLocation, imageFilter, imageReceiver.getSize(), 1, ImageReceiver.TYPE_IMAGE, 0, guid);
        } else if (mediaLocation != null) {
            int mediaCacheType = imageReceiver.getCacheType();
            int imageCacheType = 1;
            if (mediaCacheType == 0 && saveImageToCache) {
                mediaCacheType = 1;
            }
            int thumbCacheType = mediaCacheType == 0 ? 1 : mediaCacheType;
            if (!thumbSet) {
                createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, ext, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, thumbSet ? 2 : 1, guid);
            }
            if (!imageSet) {
                createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, ext, imageLocation, imageFilter, 0, imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid);
            }
            createLoadOperationForImageReceiver(imageReceiver, mediaKey, mediaUrl, ext, mediaLocation, mediaFilter, imageReceiver.getSize(), mediaCacheType, ImageReceiver.TYPE_MEDIA, 0, guid);
        } else {
            int imageCacheType = imageReceiver.getCacheType();
            if (imageCacheType == 0 && saveImageToCache) {
                imageCacheType = 1;
            }
            int thumbCacheType = imageCacheType == 0 ? 1 : imageCacheType;
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, ext, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, thumbSet ? 2 : 1, guid);
            createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, ext, imageLocation, imageFilter, imageReceiver.getSize(), imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid);
        }
    }

    private void httpFileLoadError(final String location) {
        imageLoadQueue.postRunnable(() -> {
            CacheImage img = imageLoadingByUrl.get(location);
            if (img == null) {
                return;
            }
            HttpImageTask oldTask = img.httpTask;
            img.httpTask = new HttpImageTask(oldTask.cacheImage, oldTask.imageSize);
            httpTasks.add(img.httpTask);
            runHttpTasks(false);
        });
    }

    private void artworkLoadError(final String location) {
        imageLoadQueue.postRunnable(() -> {
            CacheImage img = imageLoadingByUrl.get(location);
            if (img == null) {
                return;
            }
            ArtworkLoadTask oldTask = img.artworkTask;
            img.artworkTask = new ArtworkLoadTask(oldTask.cacheImage);
            artworkTasks.add(img.artworkTask);
            runArtworkTasks(false);
        });
    }

    private void fileDidLoaded(final String location, final File finalFile, final int type) {
        imageLoadQueue.postRunnable(() -> {
            ThumbGenerateInfo info = waitingForQualityThumb.get(location);
            if (info != null && info.parentDocument != null) {
                generateThumb(type, finalFile, info);
                waitingForQualityThumb.remove(location);
            }
            CacheImage img = imageLoadingByUrl.get(location);
            if (img == null) {
                return;
            }
            imageLoadingByUrl.remove(location);
            ArrayList<CacheOutTask> tasks = new ArrayList<>();
            for (int a = 0; a < img.imageReceiverArray.size(); a++) {
                String key = img.keys.get(a);
                String filter = img.filters.get(a);
                int imageType = img.imageTypes.get(a);
                ImageReceiver imageReceiver = img.imageReceiverArray.get(a);
                int guid = img.imageReceiverGuidsArray.get(a);
                CacheImage cacheImage = imageLoadingByKeys.get(key);
                if (cacheImage == null) {
                    cacheImage = new CacheImage();
                    cacheImage.secureDocument = img.secureDocument;
                    cacheImage.currentAccount = img.currentAccount;
                    cacheImage.finalFilePath = finalFile;
                    cacheImage.key = key;
                    cacheImage.imageLocation = img.imageLocation;
                    cacheImage.imageType = imageType;
                    cacheImage.ext = img.ext;
                    cacheImage.encryptionKeyPath = img.encryptionKeyPath;
                    cacheImage.cacheTask = new CacheOutTask(cacheImage);
                    cacheImage.filter = filter;
                    cacheImage.animatedFile = img.animatedFile;
                    cacheImage.lottieFile = img.lottieFile;
                    imageLoadingByKeys.put(key, cacheImage);
                    tasks.add(cacheImage.cacheTask);
                }
                cacheImage.addImageReceiver(imageReceiver, key, filter, imageType, guid);
            }
            for (int a = 0; a < tasks.size(); a++) {
                CacheOutTask task = tasks.get(a);
                if (task.cacheImage.imageType == ImageReceiver.TYPE_THUMB) {
                    cacheThumbOutQueue.postRunnable(task);
                } else {
                    cacheOutQueue.postRunnable(task);
                }
            }
        });
    }

    private void fileDidFailedLoad(final String location, int canceled) {
        if (canceled == 1) {
            return;
        }
        imageLoadQueue.postRunnable(() -> {
            CacheImage img = imageLoadingByUrl.get(location);
            if (img != null) {
                img.setImageAndClear(null, null);
            }
        });
    }

    private void runHttpTasks(boolean complete) {
        if (complete) {
            currentHttpTasksCount--;
        }
        while (currentHttpTasksCount < 4 && !httpTasks.isEmpty()) {
            HttpImageTask task = httpTasks.poll();
            if (task != null) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentHttpTasksCount++;
            }
        }
    }

    private void runArtworkTasks(boolean complete) {
        if (complete) {
            currentArtworkTasksCount--;
        }
        while (currentArtworkTasksCount < 4 && !artworkTasks.isEmpty()) {
            try {
                ArtworkLoadTask task = artworkTasks.poll();
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentArtworkTasksCount++;
            } catch (Throwable ignore) {
                runArtworkTasks(false);
            }
        }
    }

    public boolean isLoadingHttpFile(String url) {
        return httpFileLoadTasksByKeys.containsKey(url);
    }

    public static String getHttpFileName(String url) {
        return Utilities.MD5(url);
    }

    public static File getHttpFilePath(String url, String defaultExt) {
        String ext = getHttpUrlExtension(url, defaultExt);
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(url) + "." + ext);
    }

    public void loadHttpFile(String url, String defaultExt, int currentAccount) {
        if (url == null || url.length() == 0 || httpFileLoadTasksByKeys.containsKey(url)) {
            return;
        }
        String ext = getHttpUrlExtension(url, defaultExt);
        File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(url) + "_temp." + ext);
        file.delete();

        HttpFileTask task = new HttpFileTask(url, file, ext, currentAccount);
        httpFileLoadTasks.add(task);
        httpFileLoadTasksByKeys.put(url, task);
        runHttpFileLoadTasks(null, 0);
    }

    public void cancelLoadHttpFile(String url) {
        HttpFileTask task = httpFileLoadTasksByKeys.get(url);
        if (task != null) {
            task.cancel(true);
            httpFileLoadTasksByKeys.remove(url);
            httpFileLoadTasks.remove(task);
        }
        Runnable runnable = retryHttpsTasks.get(url);
        if (runnable != null) {
            AndroidUtilities.cancelRunOnUIThread(runnable);
        }
        runHttpFileLoadTasks(null, 0);
    }

    private void runHttpFileLoadTasks(final HttpFileTask oldTask, final int reason) {
        AndroidUtilities.runOnUIThread(() -> {
            if (oldTask != null) {
                currentHttpFileLoadTasksCount--;
            }
            if (oldTask != null) {
                if (reason == 1) {
                    if (oldTask.canRetry) {
                        final HttpFileTask newTask = new HttpFileTask(oldTask.url, oldTask.tempFile, oldTask.ext, oldTask.currentAccount);
                        Runnable runnable = () -> {
                            httpFileLoadTasks.add(newTask);
                            runHttpFileLoadTasks(null, 0);
                        };
                        retryHttpsTasks.put(oldTask.url, runnable);
                        AndroidUtilities.runOnUIThread(runnable, 1000);
                    } else {
                        httpFileLoadTasksByKeys.remove(oldTask.url);
                        NotificationCenter.getInstance(oldTask.currentAccount).postNotificationName(NotificationCenter.httpFileDidFailedLoad, oldTask.url, 0);
                    }
                } else if (reason == 2) {
                    httpFileLoadTasksByKeys.remove(oldTask.url);
                    File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(oldTask.url) + "." + oldTask.ext);
                    String result = oldTask.tempFile.renameTo(file) ? file.toString() : oldTask.tempFile.toString();
                    NotificationCenter.getInstance(oldTask.currentAccount).postNotificationName(NotificationCenter.httpFileDidLoad, oldTask.url, result);
                }
            }
            while (currentHttpFileLoadTasksCount < 2 && !httpFileLoadTasks.isEmpty()) {
                HttpFileTask task = httpFileLoadTasks.poll();
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                currentHttpFileLoadTasksCount++;
            }
        });
    }

    public static boolean shouldSendImageAsDocument(String path, Uri uri) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        if (path == null && uri != null && uri.getScheme() != null) {
            String imageFilePath = null;
            if (uri.getScheme().contains("file")) {
                path = uri.getPath();
            } else {
                try {
                    path = AndroidUtilities.getPath(uri);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }

        if (path != null) {
            BitmapFactory.decodeFile(path, bmOptions);
        } else if (uri != null) {
            boolean error = false;
            try {
                InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(inputStream, null, bmOptions);
                inputStream.close();
            } catch (Throwable e) {
                FileLog.e(e);
                return false;
            }
        }
        float photoW = bmOptions.outWidth;
        float photoH = bmOptions.outHeight;
        return photoW / photoH > 10.0f || photoH / photoW > 10.0f;
    }

    public static Bitmap loadBitmap(String path, Uri uri, float maxWidth, float maxHeight, boolean useMaxScale) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        InputStream inputStream = null;

        if (path == null && uri != null && uri.getScheme() != null) {
            String imageFilePath = null;
            if (uri.getScheme().contains("file")) {
                path = uri.getPath();
            } else {
                try {
                    path = AndroidUtilities.getPath(uri);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }

        if (path != null) {
            BitmapFactory.decodeFile(path, bmOptions);
        } else if (uri != null) {
            boolean error = false;
            try {
                inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(inputStream, null, bmOptions);
                inputStream.close();
                inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            } catch (Throwable e) {
                FileLog.e(e);
                return null;
            }
        }
        float photoW = bmOptions.outWidth;
        float photoH = bmOptions.outHeight;
        float scaleFactor = useMaxScale ? Math.max(photoW / maxWidth, photoH / maxHeight) : Math.min(photoW / maxWidth, photoH / maxHeight);
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = (int) scaleFactor;
        if (bmOptions.inSampleSize % 2 != 0) {
            int sample = 1;
            while (sample * 2 < bmOptions.inSampleSize) {
                sample *= 2;
            }
            bmOptions.inSampleSize = sample;
        }
        bmOptions.inPurgeable = Build.VERSION.SDK_INT < 21;

        String exifPath = null;
        if (path != null) {
            exifPath = path;
        } else if (uri != null) {
            exifPath = AndroidUtilities.getPath(uri);
        }

        Matrix matrix = null;

        if (exifPath != null) {
            try {
                ExifInterface exif = new ExifInterface(exifPath);
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
            } catch (Throwable ignore) {

            }
        }

        Bitmap b = null;
        if (path != null) {
            try {
                b = BitmapFactory.decodeFile(path, bmOptions);
                if (b != null) {
                    if (bmOptions.inPurgeable) {
                        Utilities.pinBitmap(b);
                    }
                    Bitmap newBitmap = Bitmaps.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                    if (newBitmap != b) {
                        b.recycle();
                        b = newBitmap;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                ImageLoader.getInstance().clearMemory();
                try {
                    if (b == null) {
                        b = BitmapFactory.decodeFile(path, bmOptions);
                        if (b != null && bmOptions.inPurgeable) {
                            Utilities.pinBitmap(b);
                        }
                    }
                    if (b != null) {
                        Bitmap newBitmap = Bitmaps.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                        if (newBitmap != b) {
                            b.recycle();
                            b = newBitmap;
                        }
                    }
                } catch (Throwable e2) {
                    FileLog.e(e2);
                }
            }
        } else if (uri != null) {
            try {
                b = BitmapFactory.decodeStream(inputStream, null, bmOptions);
                if (b != null) {
                    if (bmOptions.inPurgeable) {
                        Utilities.pinBitmap(b);
                    }
                    Bitmap newBitmap = Bitmaps.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                    if (newBitmap != b) {
                        b.recycle();
                        b = newBitmap;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    inputStream.close();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }

        return b;
    }

    public static void fillPhotoSizeWithBytes(TLRPC.PhotoSize photoSize) {
        if (photoSize == null || photoSize.bytes != null && photoSize.bytes.length != 0) {
            return;
        }
        File file = FileLoader.getPathToAttach(photoSize, true);
        try {
            RandomAccessFile f = new RandomAccessFile(file, "r");
            int len = (int) f.length();
            if (len < 20000) {
                photoSize.bytes = new byte[(int) f.length()];
                f.readFully(photoSize.bytes, 0, photoSize.bytes.length);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static TLRPC.PhotoSize scaleAndSaveImageInternal(TLRPC.PhotoSize photoSize, Bitmap bitmap, int w, int h, float photoW, float photoH, float scaleFactor, int quality, boolean cache, boolean scaleAnyway) throws Exception {
        Bitmap scaledBitmap;
        if (scaleFactor > 1 || scaleAnyway) {
            scaledBitmap = Bitmaps.createScaledBitmap(bitmap, w, h, true);
        } else {
            scaledBitmap = bitmap;
        }

        boolean check = photoSize != null;
        TLRPC.TL_fileLocationToBeDeprecated location;
        if (photoSize == null || !(photoSize.location instanceof TLRPC.TL_fileLocationToBeDeprecated)) {
            location = new TLRPC.TL_fileLocationToBeDeprecated();
            location.volume_id = Integer.MIN_VALUE;
            location.dc_id = Integer.MIN_VALUE;
            location.local_id = SharedConfig.getLastLocalId();
            location.file_reference = new byte[0];

            photoSize = new TLRPC.TL_photoSize();
            photoSize.location = location;
            photoSize.w = scaledBitmap.getWidth();
            photoSize.h = scaledBitmap.getHeight();
            if (photoSize.w <= 100 && photoSize.h <= 100) {
                photoSize.type = "s";
            } else if (photoSize.w <= 320 && photoSize.h <= 320) {
                photoSize.type = "m";
            } else if (photoSize.w <= 800 && photoSize.h <= 800) {
                photoSize.type = "x";
            } else if (photoSize.w <= 1280 && photoSize.h <= 1280) {
                photoSize.type = "y";
            } else {
                photoSize.type = "w";
            }
        } else {
            location = (TLRPC.TL_fileLocationToBeDeprecated) photoSize.location;
        }

        String fileName = location.volume_id + "_" + location.local_id + ".jpg";
        final File cacheFile = new File(location.volume_id != Integer.MIN_VALUE ? FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE) : FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
        FileOutputStream stream = new FileOutputStream(cacheFile);
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        if (cache) {
            ByteArrayOutputStream stream2 = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream2);
            photoSize.bytes = stream2.toByteArray();
            photoSize.size = photoSize.bytes.length;
            stream2.close();
        } else {
            photoSize.size = (int) stream.getChannel().size();
        }
        stream.close();
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        return photoSize;
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache) {
        return scaleAndSaveImage(null, bitmap, maxWidth, maxHeight, quality, cache, 0, 0);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(TLRPC.PhotoSize photoSize, Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache) {
        return scaleAndSaveImage(photoSize, bitmap, maxWidth, maxHeight, quality, cache, 0, 0);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight) {
        return scaleAndSaveImage(null, bitmap, maxWidth, maxHeight, quality, cache, minWidth, minHeight);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(TLRPC.PhotoSize photoSize, Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight) {
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
        if (minWidth != 0 && minHeight != 0 && (photoW < minWidth || photoH < minHeight)) {
            if (photoW < minWidth && photoH > minHeight) {
                scaleFactor = photoW / minWidth;
            } else if (photoW > minWidth && photoH < minHeight) {
                scaleFactor = photoH / minHeight;
            } else {
                scaleFactor = Math.max(photoW / minWidth, photoH / minHeight);
            }
            scaleAnyway = true;
        }
        int w = (int) (photoW / scaleFactor);
        int h = (int) (photoH / scaleFactor);
        if (h == 0 || w == 0) {
            return null;
        }

        try {
            return scaleAndSaveImageInternal(photoSize, bitmap, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway);
        } catch (Throwable e) {
            FileLog.e(e);
            ImageLoader.getInstance().clearMemory();
            System.gc();
            try {
                return scaleAndSaveImageInternal(photoSize, bitmap, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway);
            } catch (Throwable e2) {
                FileLog.e(e2);
                return null;
            }
        }
    }

    public static String getHttpUrlExtension(String url, String defaultExt) {
        String ext = null;
        String last = Uri.parse(url).getLastPathSegment();
        if (!TextUtils.isEmpty(last) && last.length() > 1) {
            url = last;
        }
        int idx = url.lastIndexOf('.');
        if (idx != -1) {
            ext = url.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0 || ext.length() > 4) {
            ext = defaultExt;
        }
        return ext;
    }

    public static void saveMessageThumbs(TLRPC.Message message) {
        TLRPC.PhotoSize photoSize = null;
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            for (int a = 0, count = message.media.photo.sizes.size(); a < count; a++) {
                TLRPC.PhotoSize size = message.media.photo.sizes.get(a);
                if (size instanceof TLRPC.TL_photoCachedSize) {
                    photoSize = size;
                    break;
                }
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            for (int a = 0, count = message.media.document.thumbs.size(); a < count; a++) {
                TLRPC.PhotoSize size = message.media.document.thumbs.get(a);
                if (size instanceof TLRPC.TL_photoCachedSize) {
                    photoSize = size;
                    break;
                }
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            if (message.media.webpage.photo != null) {
                for (int a = 0, count = message.media.webpage.photo.sizes.size(); a < count; a++) {
                    TLRPC.PhotoSize size = message.media.webpage.photo.sizes.get(a);
                    if (size instanceof TLRPC.TL_photoCachedSize) {
                        photoSize = size;
                        break;
                    }
                }
            }
        }
        if (photoSize != null && photoSize.bytes != null && photoSize.bytes.length != 0) {
            if (photoSize.location == null || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable) {
                photoSize.location = new TLRPC.TL_fileLocationToBeDeprecated();
                photoSize.location.volume_id = Integer.MIN_VALUE;
                photoSize.location.local_id = SharedConfig.getLastLocalId();
            }
            File file = FileLoader.getPathToAttach(photoSize, true);
            boolean isEncrypted = false;
            if (MessageObject.shouldEncryptPhotoOrVideo(message)) {
                file = new File(file.getAbsolutePath() + ".enc");
                isEncrypted = true;
            }
            if (!file.exists()) {
                try {
                    if (isEncrypted) {
                        File keyPath = new File(FileLoader.getInternalCacheDir(), file.getName() + ".key");
                        RandomAccessFile keyFile = new RandomAccessFile(keyPath, "rws");
                        long len = keyFile.length();
                        byte[] encryptKey = new byte[32];
                        byte[] encryptIv = new byte[16];
                        if (len > 0 && len % 48 == 0) {
                            keyFile.read(encryptKey, 0, 32);
                            keyFile.read(encryptIv, 0, 16);
                        } else {
                            Utilities.random.nextBytes(encryptKey);
                            Utilities.random.nextBytes(encryptIv);
                            keyFile.write(encryptKey);
                            keyFile.write(encryptIv);
                        }
                        keyFile.close();
                        Utilities.aesCtrDecryptionByteArray(photoSize.bytes, encryptKey, encryptIv, 0, photoSize.bytes.length, 0);
                    }
                    RandomAccessFile writeFile = new RandomAccessFile(file, "rws");
                    writeFile.write(photoSize.bytes);
                    writeFile.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            TLRPC.TL_photoSize newPhotoSize = new TLRPC.TL_photoSize();
            newPhotoSize.w = photoSize.w;
            newPhotoSize.h = photoSize.h;
            newPhotoSize.location = photoSize.location;
            newPhotoSize.size = photoSize.size;
            newPhotoSize.type = photoSize.type;

            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                for (int a = 0, count = message.media.photo.sizes.size(); a < count; a++) {
                    TLRPC.PhotoSize size = message.media.photo.sizes.get(a);
                    if (size instanceof TLRPC.TL_photoCachedSize) {
                        message.media.photo.sizes.set(a, newPhotoSize);
                        break;
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                for (int a = 0, count = message.media.document.thumbs.size(); a < count; a++) {
                    TLRPC.PhotoSize size = message.media.document.thumbs.get(a);
                    if (size instanceof TLRPC.TL_photoCachedSize) {
                        message.media.document.thumbs.set(a, newPhotoSize);
                        break;
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                for (int a = 0, count = message.media.webpage.photo.sizes.size(); a < count; a++) {
                    TLRPC.PhotoSize size = message.media.webpage.photo.sizes.get(a);
                    if (size instanceof TLRPC.TL_photoCachedSize) {
                        message.media.webpage.photo.sizes.set(a, newPhotoSize);
                        break;
                    }
                }
            }
        }
    }

    public static void saveMessagesThumbs(ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            saveMessageThumbs(message);
        }
    }
}
