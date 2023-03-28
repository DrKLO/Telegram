/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.DispatchQueuePriority;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.messenger.utils.BitmapsCache;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.SlotsDrawable;
import org.telegram.ui.Components.ThemePreviewDrawable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * image filter types
 * suffixes:
 * f - image is wallpaper
 * isc - ignore cache for small images
 * b - need blur image
 * g - autoplay
 * lastframe - return lastframe for Lottie animation
 * lastreactframe - return lastframe for Lottie animation + some scale ReactionLastFrame magic
 * firstframe - return firstframe for Lottie animation
 */
public class ImageLoader {

    private HashMap<String, Integer> bitmapUseCounts = new HashMap<>();
    private LruCache<BitmapDrawable> smallImagesMemCache;
    private LruCache<BitmapDrawable> memCache;
    private LruCache<BitmapDrawable> wallpaperMemCache;
    private LruCache<BitmapDrawable> lottieMemCache;
    ArrayList<AnimatedFileDrawable> cachedAnimatedFileDrawables = new ArrayList<>();
    private HashMap<String, CacheImage> imageLoadingByUrl = new HashMap<>();
    private HashMap<String, CacheImage> imageLoadingByKeys = new HashMap<>();
    private SparseArray<CacheImage> imageLoadingByTag = new SparseArray<>();
    private HashMap<String, ThumbGenerateInfo> waitingForQualityThumb = new HashMap<>();
    private SparseArray<String> waitingForQualityThumbByTag = new SparseArray<>();
    private LinkedList<HttpImageTask> httpTasks = new LinkedList<>();
    private LinkedList<ArtworkLoadTask> artworkTasks = new LinkedList<>();
    private DispatchQueuePriority cacheOutQueue = new DispatchQueuePriority("cacheOutQueue");
    private DispatchQueue cacheThumbOutQueue = new DispatchQueue("cacheThumbOutQueue");
    private DispatchQueue thumbGeneratingQueue = new DispatchQueue("thumbGeneratingQueue");
    private DispatchQueue imageLoadQueue = new DispatchQueue("imageLoadQueue");
    private HashMap<String, String> replacedBitmaps = new HashMap<>();
    private ConcurrentHashMap<String, long[]> fileProgresses = new ConcurrentHashMap<>();
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

    private File telegramPath = null;

    public static final String AUTOPLAY_FILTER = "g";

    public static boolean hasAutoplayFilter(String s) {
        if (s == null) {
            return false;
        }
        String[] words = s.split("_");
        for (int i = 0; i < words.length; ++i) {
            if (AUTOPLAY_FILTER.equals(words[i])) {
                return true;
            }
        }
        return false;
    }

    public void moveToFront(String key) {
        if (key == null) {
            return;
        }
        BitmapDrawable drawable = lottieMemCache.get(key);
        if (drawable != null) {
            lottieMemCache.moveToFront(key);
        }
        drawable = memCache.get(key);
        if (drawable != null) {
            memCache.moveToFront(key);
        }
        drawable = smallImagesMemCache.get(key);
        if (drawable != null) {
            smallImagesMemCache.moveToFront(key);
        }
    }

    public void putThumbsToCache(ArrayList<MessageThumb> updateMessageThumbs) {
        for (int i = 0; i < updateMessageThumbs.size(); i++) {
            putImageToCache(updateMessageThumbs.get(i).drawable, updateMessageThumbs.get(i).key, true);
        }
    }

    public LruCache<BitmapDrawable> getLottieMemCahce() {
        return lottieMemCache;
    }

    private static class ThumbGenerateInfo {
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

        private void reportProgress(long uploadedSize, long totalSize) {
            long currentTime = SystemClock.elapsedRealtime();
            if (uploadedSize == totalSize || lastProgressTime == 0 || lastProgressTime < currentTime - 100) {
                lastProgressTime = currentTime;
                Utilities.stageQueue.postRunnable(() -> {
                    fileProgresses.put(url, new long[]{uploadedSize, totalSize});
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileLoadProgressChanged, url, uploadedSize, totalSize));
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
                                        reportProgress(totalLoaded, fileSize);
                                    }
                                } else if (read == -1) {
                                    done = true;
                                    if (fileSize != 0) {
                                        reportProgress(fileSize, fileSize);
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
                //httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
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
                    FileLog.e(e, false);
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
                FileLog.e(e, false);
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
                imageLoadQueue.postRunnable(() -> {
                    cacheImage.httpTask = new HttpImageTask(cacheImage, 0, result);
                    httpTasks.add(cacheImage.httpTask);
                    runHttpTasks(false);
                });
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
        private long imageSize;
        private long lastProgressTime;
        private boolean canRetry = true;
        private String overrideUrl;
        private HttpURLConnection httpConnection;

        public HttpImageTask(CacheImage cacheImage, long size) {
            this.cacheImage = cacheImage;
            imageSize = size;
        }

        public HttpImageTask(CacheImage cacheImage, int size, String url) {
            this.cacheImage = cacheImage;
            imageSize = size;
            overrideUrl = url;
        }

        private void reportProgress(long uploadedSize, long totalSize) {
            long currentTime = SystemClock.elapsedRealtime();
            if (uploadedSize == totalSize || lastProgressTime == 0 || lastProgressTime < currentTime - 100) {
                lastProgressTime = currentTime;
                Utilities.stageQueue.postRunnable(() -> {
                    fileProgresses.put(cacheImage.url, new long[]{uploadedSize, totalSize});
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadProgressChanged, cacheImage.url, uploadedSize, totalSize));
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
                    httpConnection.setConnectTimeout(5000);
                    httpConnection.setReadTimeout(5000);
                    httpConnection.setInstanceFollowRedirects(true);
                    if (!isCancelled()) {
                        httpConnection.connect();
                        httpConnectionStream = httpConnection.getInputStream();
                        fileOutputStream = new RandomAccessFile(cacheImage.tempFilePath, "rws");
                    }
                } catch (Throwable e) {
                    boolean sentLogs = true;
                    if (e instanceof SocketTimeoutException) {
                        if (ApplicationLoader.isNetworkOnline()) {
                            canRetry = false;
                        }
                        sentLogs = false;
                    } else if (e instanceof UnknownHostException) {
                        canRetry = false;
                        sentLogs = false;
                    } else if (e instanceof SocketException) {
                        if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                            canRetry = false;
                        }
                        sentLogs = false;
                    } else if (e instanceof FileNotFoundException) {
                        canRetry = false;
                        sentLogs = false;
                    } else if (e instanceof InterruptedIOException) {
                        sentLogs = false;
                    }
                    FileLog.e(e, sentLogs);
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
                                        reportProgress(totalLoaded, imageSize);
                                    }
                                } else if (read == -1) {
                                    done = true;
                                    if (imageSize != 0) {
                                        reportProgress(imageSize, imageSize);
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
                        NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoaded, cacheImage.url, cacheImage.finalFilePath);
                    } else {
                        NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadFailed, cacheImage.url, 2);
                    }
                });
            });
            imageLoadQueue.postRunnable(() -> runHttpTasks(true), cacheImage.priority);
        }

        @Override
        protected void onCancelled() {
            imageLoadQueue.postRunnable(() -> runHttpTasks(true), cacheImage.priority);
            Utilities.stageQueue.postRunnable(() -> {
                fileProgresses.remove(cacheImage.url);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadFailed, cacheImage.url, 1));
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
                    originalBitmap = SendMessagesHelper.createVideoThumbnail(originalPath.toString(), info.big ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND);
                } else if (mediaType == FileLoader.MEDIA_DIR_DOCUMENT) {
                    String path = originalPath.toString().toLowerCase();
                    if (path.endsWith("mp4")) {
                        originalBitmap = SendMessagesHelper.createVideoThumbnail(originalPath.toString(), info.big ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND);
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

    public static String decompressGzip(File file) {
        final StringBuilder outStr = new StringBuilder();
        if (file == null) {
            return "";
        }
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file)); BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, "UTF-8"))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                outStr.append(line);
            }
            return outStr.toString();
        } catch (Exception ignore) {
            return "";
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
                TLRPC.TL_photoStrippedSize photoSize = (TLRPC.TL_photoStrippedSize) cacheImage.imageLocation.photoSize;
                Bitmap bitmap = getStrippedPhotoBitmap(photoSize.bytes, "b");
                onPostExecute(bitmap != null ? new BitmapDrawable(bitmap) : null);
            } else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_THEME_PREVIEW) {
                BitmapDrawable bitmapDrawable = null;
                try {
                    bitmapDrawable = new ThemePreviewDrawable(cacheImage.finalFilePath, (DocumentObject.ThemeDocument) cacheImage.imageLocation.document);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                onPostExecute(bitmapDrawable);
            } else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG || cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG_WHITE) {
                int w = AndroidUtilities.displaySize.x;
                int h = AndroidUtilities.displaySize.y;
                if (cacheImage.filter != null) {
                    String[] args = cacheImage.filter.split("_");
                    if (args.length >= 2) {
                        float w_filter = Float.parseFloat(args[0]);
                        float h_filter = Float.parseFloat(args[1]);
                        w = (int) (w_filter * AndroidUtilities.density);
                        h = (int) (h_filter * AndroidUtilities.density);
                    }
                }
                Bitmap bitmap = null;
                try {
                    bitmap = SvgHelper.getBitmap(cacheImage.finalFilePath, w, h, cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG_WHITE);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                onPostExecute(bitmap != null ? new BitmapDrawable(bitmap) : null);
            } else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                int w = Math.min(512, AndroidUtilities.dp(170.6f));
                int h = Math.min(512, AndroidUtilities.dp(170.6f));
                boolean precache = false;
                boolean limitFps = false;
                boolean lastFrameBitmap = false;
                boolean lastFrameReactionScaleBitmap = false;
                boolean firstFrameBitmap = false;
                int autoRepeat = 1;
                int[] colors = null;
                String diceEmoji = null;
                int fitzModifier = 0;
                if (cacheImage.filter != null) {
                    String[] args = cacheImage.filter.split("_");
                    if (args.length >= 2) {
                        float w_filter = Float.parseFloat(args[0]);
                        float h_filter = Float.parseFloat(args[1]);
                        w = Math.min(512, (int) (w_filter * AndroidUtilities.density));
                        h = Math.min(512, (int) (h_filter * AndroidUtilities.density));
                        if (w_filter <= 90 && h_filter <= 90 && !cacheImage.filter.contains("nolimit")) {
                            w = Math.min(w, 160);
                            h = Math.min(h, 160);
                            limitFps = true;
                        }
                        if (args.length >= 3 && "pcache".equals(args[2])) {
                            precache = true;
                        } else {
                            precache = cacheImage.filter.contains("pcache") || !cacheImage.filter.contains("nolimit") && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH;
                        }

                        if (cacheImage.filter.contains("lastframe")) {
                            lastFrameBitmap = true;
                        }
                        if (cacheImage.filter.contains("lastreactframe")) {
                            lastFrameBitmap = true;
                            lastFrameReactionScaleBitmap = true;
                        }
                        if (cacheImage.filter.contains("firstframe")) {
                            firstFrameBitmap = true;
                        }

                    }

                    if (args.length >= 3) {
                        if ("nr".equals(args[2])) {
                            autoRepeat = 2;
                        } else if ("nrs".equals(args[2])) {
                            autoRepeat = 3;
                        } else if ("dice".equals(args[2])) {
                            diceEmoji = args[3];
                            autoRepeat = 2;
                        }
                    }
                    if (args.length >= 5) {
                        if ("c1".equals(args[4])) {
                            fitzModifier = 12;
                        } else if ("c2".equals(args[4])) {
                            fitzModifier = 3;
                        } else if ("c3".equals(args[4])) {
                            fitzModifier = 4;
                        } else if ("c4".equals(args[4])) {
                            fitzModifier = 5;
                        } else if ("c5".equals(args[4])) {
                            fitzModifier = 6;
                        }
                    }
                }
                RLottieDrawable lottieDrawable;
                if (diceEmoji != null) {
                    if ("\uD83C\uDFB0".equals(diceEmoji)) {
                        lottieDrawable = new SlotsDrawable(diceEmoji, w, h);
                    } else {
                        lottieDrawable = new RLottieDrawable(diceEmoji, w, h);
                    }
                } else {
                    File f = cacheImage.finalFilePath;
                    RandomAccessFile randomAccessFile = null;
                    boolean compressed = false;
                    try {
                        randomAccessFile = new RandomAccessFile(cacheImage.finalFilePath, "r");
                        byte[] bytes;
                        if (cacheImage.type == ImageReceiver.TYPE_THUMB) {
                            bytes = headerThumb;
                        } else {
                            bytes = header;
                        }
                        randomAccessFile.readFully(bytes, 0, 2);
                        if (bytes[0] == 0x1f && bytes[1] == (byte) 0x8b) {
                            compressed = true;
                        }
                    } catch (Exception e) {
                        FileLog.e(e, false);
                    } finally {
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                    if (lastFrameBitmap || firstFrameBitmap) {
                        precache = false;
                    }
                    BitmapsCache.CacheOptions cacheOptions = null;
                    if (precache || lastFrameBitmap || firstFrameBitmap) {
                        cacheOptions = new BitmapsCache.CacheOptions();
                        if (!lastFrameBitmap && !firstFrameBitmap) {
                            if (cacheImage.filter != null && cacheImage.filter.contains("compress")) {
                                cacheOptions.compressQuality = BitmapsCache.COMPRESS_QUALITY_DEFAULT;
                            }
                            if (cacheImage.filter != null && cacheImage.filter.contains("flbk")) {
                                cacheOptions.fallback = true;
                            }
                        } else {
                            cacheOptions.firstFrame = true;
                        }
                    }
                    if (compressed) {
                        lottieDrawable = new RLottieDrawable(cacheImage.finalFilePath, decompressGzip(cacheImage.finalFilePath), w, h, cacheOptions, limitFps, null, fitzModifier);
                    } else {
                        lottieDrawable = new RLottieDrawable(cacheImage.finalFilePath, w, h, cacheOptions, limitFps, null, fitzModifier);
                    }
                }
                if (lastFrameBitmap || firstFrameBitmap) {
                    loadLastFrame(lottieDrawable, h, w, lastFrameBitmap, lastFrameReactionScaleBitmap);
                } else {
                    lottieDrawable.setAutoRepeat(autoRepeat);
                    onPostExecute(lottieDrawable);
                }
            } else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
                AnimatedFileDrawable fileDrawable;
                long seekTo;
                if (cacheImage.imageLocation != null) {
                    seekTo = cacheImage.imageLocation.videoSeekTo;
                } else {
                    seekTo = 0;
                }
                boolean limitFps = false;
                boolean precache = false;
                boolean fistFrame = false;
                boolean notCreateStream = false;
                if (cacheImage.filter != null) {
                    String[] args = cacheImage.filter.split("_");
                    if (args.length >= 2) {
                        float w_filter = Float.parseFloat(args[0]);
                        float h_filter = Float.parseFloat(args[1]);
                        if (w_filter <= 90 && h_filter <= 90 && !cacheImage.filter.contains("nolimit")) {
                            limitFps = true;
                        }
                    }
                    for (int i = 0; i < args.length; i++) {
                        if ("pcache".equals(args[i])) {
                            precache = true;
                        }
                        if ("firstframe".equals(args[i])) {
                            fistFrame = true;
                        }
                        if ("nostream".equals(args[i])) {
                            notCreateStream = true;
                        }
                    }
                    if (fistFrame) {
                        notCreateStream = true;
                    }
                }
                BitmapsCache.CacheOptions cacheOptions = null;
                if (precache && !fistFrame) {
                    cacheOptions = new BitmapsCache.CacheOptions();
                    if (cacheImage.filter != null && cacheImage.filter.contains("compress")) {
                        cacheOptions.compressQuality = BitmapsCache.COMPRESS_QUALITY_DEFAULT;
                    }
                }
                if ((isAnimatedAvatar(cacheImage.filter) || AUTOPLAY_FILTER.equals(cacheImage.filter)) && !(cacheImage.imageLocation.document instanceof TLRPC.TL_documentEncrypted) && !precache) {
                    TLRPC.Document document = cacheImage.imageLocation.document instanceof TLRPC.Document ? cacheImage.imageLocation.document : null;
                    long size = document != null ? cacheImage.size : cacheImage.imageLocation.currentSize;
                    fileDrawable = new AnimatedFileDrawable(cacheImage.finalFilePath, fistFrame, notCreateStream ? 0 : size, cacheImage.priority, notCreateStream ? null : document, document == null && !notCreateStream ? cacheImage.imageLocation : null, cacheImage.parentObject, seekTo, cacheImage.currentAccount, false, cacheOptions);
                    fileDrawable.setIsWebmSticker(MessageObject.isWebM(document) || MessageObject.isVideoSticker(document) || isAnimatedAvatar(cacheImage.filter));
                } else {

                    int w = 0;
                    int h = 0;
                    if (cacheImage.filter != null) {
                        String[] args = cacheImage.filter.split("_");
                        if (args.length >= 2) {
                            float w_filter = Float.parseFloat(args[0]);
                            float h_filter = Float.parseFloat(args[1]);
                            w = (int) (w_filter * AndroidUtilities.density);
                            h = (int) (h_filter * AndroidUtilities.density);
                        }
                    }
                    boolean createDecoder = fistFrame || (cacheImage.filter != null && ("d".equals(cacheImage.filter) || cacheImage.filter.contains("_d")));
                    fileDrawable = new AnimatedFileDrawable(cacheImage.finalFilePath, createDecoder, 0, cacheImage.priority, notCreateStream ? null : cacheImage.imageLocation.document, null, null, seekTo, cacheImage.currentAccount, false, w, h, cacheOptions);
                    fileDrawable.setIsWebmSticker(MessageObject.isWebM(cacheImage.imageLocation.document) || MessageObject.isVideoSticker(cacheImage.imageLocation.document) || isAnimatedAvatar(cacheImage.filter));
                }
                if (fistFrame) {
                    Bitmap bitmap = fileDrawable.getFrameAtTime(0, false);

                    fileDrawable.recycle();
                    Thread.interrupted();
                    if (bitmap == null) {
                        onPostExecute(null);
                    } else {
                        onPostExecute(new BitmapDrawable(bitmap));
                    }
                } else {
                    fileDrawable.setLimitFps(limitFps);
                    Thread.interrupted();
                    onPostExecute(fileDrawable);
                }
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
                        if (cacheImage.type == ImageReceiver.TYPE_THUMB) {
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
                        int screenSize = Math.max(66, Math.min(AndroidUtilities.getRealScreenSize().x, AndroidUtilities.getRealScreenSize().y));
                        float scaleFactor = (Math.min(photoH2, photoW2) / (float) screenSize) * 6f;
                        if (scaleFactor < 1) {
                            scaleFactor = 1;
                        }
                        if (scaleFactor > 1.0f) {
                            int sample = 1;
                            do {
                                sample *= 2;
                            } while (sample * 2 <= scaleFactor);
                            opts.inSampleSize = sample;
                        } else {
                            opts.inSampleSize = (int) scaleFactor;
                        }
                    }
                } catch (Throwable e) {
                    boolean sentLog = true;
                    if (e instanceof FileNotFoundException) {
                        sentLog = false;
                    }
                    FileLog.e(e, sentLog);
                }

                if (cacheImage.type == ImageReceiver.TYPE_THUMB) {
                    try {
                        lastCacheOutTime = SystemClock.elapsedRealtime();
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
                        FileLog.e(e, !(e instanceof FileNotFoundException));
                    }
                } else {
                    try {
                        int delay = 20;
                        if (mediaId != null) {
                            delay = 0;
                        }
                        if (delay != 0 && lastCacheOutTime != 0 && lastCacheOutTime > SystemClock.elapsedRealtime() - delay && Build.VERSION.SDK_INT < 21) {
                            Thread.sleep(delay);
                        }
                        lastCacheOutTime = SystemClock.elapsedRealtime();
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
                                if (mediaId == 0) {
                                    AnimatedFileDrawable fileDrawable = new AnimatedFileDrawable(cacheFileFinal, true, 0, 0, null, null, null, 0, 0, true, null);
                                    image = fileDrawable.getFrameAtTime(0, true);
                                    fileDrawable.recycle();
                                } else {
                                    image = MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                                }
                            } else {
                                image = MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                            }
                        }
                        if (image == null) {
                            if (useNativeWebpLoader && secureDocumentKey == null) {
                                RandomAccessFile file = new RandomAccessFile(cacheFileFinal, "r");
                                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal.length());

                                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                bmOptions.inJustDecodeBounds = true;
                                Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                                image = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);

                                Utilities.loadWebpImage(image, buffer, buffer.limit(), null, !opts.inPurgeable);
                                file.close();
                            } else {
                                if (image == null) {
                                    FileInputStream is;
                                    if (secureDocumentKey != null) {
                                        is = new EncryptedFileInputStream(cacheFileFinal, secureDocumentKey);
                                    } else if (inEncryptedFile) {
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
                                        if (secureDocumentKey != null || cacheImage.encryptionKeyPath != null) {
                                            is.close();
                                            if (secureDocumentKey != null) {
                                                is = new EncryptedFileInputStream(cacheFileFinal, secureDocumentKey);
                                            } else if (inEncryptedFile) {
                                                is = new EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath);
                                            }
                                        } else {
                                            is.getChannel().position(0);
                                        }
                                    }
                                    image = BitmapFactory.decodeStream(is, null, opts);
                                    is.close();
                                }

                                if (image == null) {
                                    try {
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
                                    } catch (Throwable e) {
                                        FileLog.e(e);
                                    }
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
                                        if (scaleFactor > 1) {
                                            scaledBitmap = Bitmaps.createScaledBitmap(image, (int) w_filter, (int) (bitmapH / scaleFactor), true);
                                        } else {
                                            scaledBitmap = image;
                                        }
                                    } else {
                                        float scaleFactor = bitmapH / h_filter;
                                        if (scaleFactor > 1) {
                                            scaledBitmap = Bitmaps.createScaledBitmap(image, (int) (bitmapW / scaleFactor), (int) h_filter, true);
                                        } else {
                                            scaledBitmap = image;
                                        }
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
                                    if (blurType != 0 && (bitmapH > 100 || bitmapW > 100)) {
                                        image = Bitmaps.createScaledBitmap(image, 80, 80, false);
                                        bitmapH = 80;
                                        bitmapW = 80;
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
                    } catch (Throwable e) {
                        FileLog.e(e, !(e instanceof FileNotFoundException));
                    }
                }
                Thread.interrupted();
                if (BuildVars.LOGS_ENABLED && inEncryptedFile) {
                    FileLog.e("Image Loader image is empty = " + (image == null) + " " + cacheFileFinal);
                }
                if (needInvert || orientation != 0) {
                    onPostExecute(image != null ? new ExtendedBitmapDrawable(image, needInvert, orientation) : null);
                } else {
                    onPostExecute(image != null ? new BitmapDrawable(image) : null);
                }
            }
        }

        private void loadLastFrame(RLottieDrawable lottieDrawable, int w, int h, boolean lastFrame, boolean reaction) {
            Bitmap bitmap;
            Canvas canvas;
            if (lastFrame && reaction) {
                bitmap = Bitmap.createBitmap((int) (w * ImageReceiver.ReactionLastFrame.LAST_FRAME_SCALE), (int) (h * ImageReceiver.ReactionLastFrame.LAST_FRAME_SCALE), Bitmap.Config.ARGB_8888);
                canvas = new Canvas(bitmap);
                canvas.scale(2f, 2f, w * ImageReceiver.ReactionLastFrame.LAST_FRAME_SCALE / 2f, h * ImageReceiver.ReactionLastFrame.LAST_FRAME_SCALE / 2f);
            } else {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                canvas = new Canvas(bitmap);
            }

            lottieDrawable.prepareForGenerateCache();
            Bitmap currentBitmap = Bitmap.createBitmap(lottieDrawable.getIntrinsicWidth(), lottieDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            lottieDrawable.setGeneratingFrame(lastFrame ? lottieDrawable.getFramesCount() - 1 : 0);
            lottieDrawable.getNextFrame(currentBitmap);
            lottieDrawable.releaseForGenerateCache();
            canvas.save();
            if (!(lastFrame && reaction)) {
                canvas.scale(currentBitmap.getWidth() / w, currentBitmap.getHeight() / h, w / 2f, h / 2f);
            }
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);
            BitmapDrawable bitmapDrawable = null;
            if (lastFrame && reaction) {
                canvas.drawBitmap(currentBitmap, (bitmap.getWidth() - currentBitmap.getWidth()) / 2f, (bitmap.getHeight() - currentBitmap.getHeight()) / 2f, paint);
                bitmapDrawable = new ImageReceiver.ReactionLastFrame(bitmap);
            } else {
                canvas.drawBitmap(currentBitmap, 0, 0, paint);
                bitmapDrawable = new BitmapDrawable(bitmap);
            }

            lottieDrawable.recycle(false);
            currentBitmap.recycle();
            onPostExecute(bitmapDrawable);
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
                        lottieDrawable.recycle(false);
                    }
                    if (toSet != null) {
                        incrementUseCount(cacheImage.key);
                        decrementKey = cacheImage.key;
                    }
                } else if (drawable instanceof AnimatedFileDrawable) {
                    AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                    if (animatedFileDrawable.isWebmSticker) {
                        toSet = getFromLottieCache(cacheImage.key);
                        if (toSet == null) {
                            lottieMemCache.put(cacheImage.key, animatedFileDrawable);
                            toSet = animatedFileDrawable;
                        } else {
                            animatedFileDrawable.recycle();
                        }
                        incrementUseCount(cacheImage.key);
                        decrementKey = cacheImage.key;
                    } else {
                        toSet = drawable;
                    }
                } else if (drawable instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                    toSet = getFromMemCache(cacheImage.key);
                    boolean incrementUseCount = true;
                    if (toSet == null) {
                        if (cacheImage.key.endsWith("_f")) {
                            wallpaperMemCache.put(cacheImage.key, bitmapDrawable);
                            incrementUseCount = false;
                        } else if (!cacheImage.key.endsWith("_isc") && bitmapDrawable.getBitmap().getWidth() <= 80 * AndroidUtilities.density && bitmapDrawable.getBitmap().getHeight() <= 80 * AndroidUtilities.density) {
                            smallImagesMemCache.put(cacheImage.key, bitmapDrawable);
                        } else {
                            memCache.put(cacheImage.key, bitmapDrawable);
                        }
                        toSet = bitmapDrawable;
                    } else {
                        Bitmap image = bitmapDrawable.getBitmap();
                        AndroidUtilities.recycleBitmap(image);
                    }
                    if (toSet != null && incrementUseCount) {
                        incrementUseCount(cacheImage.key);
                        decrementKey = cacheImage.key;
                    }
                }
                final Drawable toSetFinal = toSet;
                final String decrementKetFinal = decrementKey;
                imageLoadQueue.postRunnable(() -> cacheImage.setImageAndClear(toSetFinal, decrementKetFinal), cacheImage.priority);
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

    private boolean isAnimatedAvatar(String filter) {
        return filter != null && filter.endsWith("avatar");
    }

    public BitmapDrawable getFromMemCache(String key) {
        BitmapDrawable drawable = memCache.get(key);
        if (drawable == null) {
            drawable = smallImagesMemCache.get(key);
        }
        if (drawable == null) {
            drawable = wallpaperMemCache.get(key);
        }
        if (drawable == null) {
            drawable = getFromLottieCache(key);
        }
        return drawable;
    }

    public static Bitmap getStrippedPhotoBitmap(byte[] photoBytes, String filter) {
        int len = photoBytes.length - 3 + Bitmaps.header.length + Bitmaps.footer.length;
        byte[] bytes = bytesLocal.get();
        byte[] data = bytes != null && bytes.length >= len ? bytes : null;
        if (data == null) {
            bytes = data = new byte[len];
            bytesLocal.set(bytes);
        }
        System.arraycopy(Bitmaps.header, 0, data, 0, Bitmaps.header.length);
        System.arraycopy(photoBytes, 3, data, Bitmaps.header.length, photoBytes.length - 3);
        System.arraycopy(Bitmaps.footer, 0, data, Bitmaps.header.length + photoBytes.length - 3, Bitmaps.footer.length);

        data[164] = photoBytes[1];
        data[166] = photoBytes[2];

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, len);
        if (bitmap != null && !TextUtils.isEmpty(filter) && filter.contains("b")) {
            Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
        }
        return bitmap;
    }

    private class CacheImage {

        public int priority = 1;
        public Runnable runningTask;
        protected String key;
        protected String url;
        protected String filter;
        protected String ext;
        protected SecureDocument secureDocument;
        protected ImageLocation imageLocation;
        protected Object parentObject;
        protected long size;
        protected int imageType;
        protected int type;

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
        protected ArrayList<Integer> types = new ArrayList<>();

        public void addImageReceiver(ImageReceiver imageReceiver, String key, String filter, int type, int guid) {
            int index = imageReceiverArray.indexOf(imageReceiver);
            if (index >= 0 && Objects.equals(imageReceiverArray.get(index).getImageKey(), key)) {
                imageReceiverGuidsArray.set(index, guid);
                return;
            }
            imageReceiverArray.add(imageReceiver);
            imageReceiverGuidsArray.add(guid);
            keys.add(key);
            filters.add(filter);
            types.add(type);
            imageLoadingByTag.put(imageReceiver.getTag(type), this);
        }

        public void replaceImageReceiver(ImageReceiver imageReceiver, String key, String filter, int type, int guid) {
            int index = imageReceiverArray.indexOf(imageReceiver);
            if (index == -1) {
                return;
            }
            if (types.get(index) != type) {
                index = imageReceiverArray.subList(index + 1, imageReceiverArray.size()).indexOf(imageReceiver);
                if (index == -1) {
                    return;
                }
            }
            imageReceiverGuidsArray.set(index, guid);
            keys.set(index, key);
            filters.set(index, filter);
        }

        public void setImageReceiverGuid(ImageReceiver imageReceiver, int guid) {
            int index = imageReceiverArray.indexOf(imageReceiver);
            if (index == -1) {
                return;
            }
            imageReceiverGuidsArray.set(index, guid);
        }

        public void removeImageReceiver(ImageReceiver imageReceiver) {
            int currentMediaType = type;
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver obj = imageReceiverArray.get(a);
                if (obj == null || obj == imageReceiver) {
                    imageReceiverArray.remove(a);
                    imageReceiverGuidsArray.remove(a);
                    keys.remove(a);
                    filters.remove(a);
                    currentMediaType = types.remove(a);
                    if (obj != null) {
                        imageLoadingByTag.remove(obj.getTag(currentMediaType));
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
                    if (currentMediaType == ImageReceiver.TYPE_THUMB) {
                        cacheThumbOutQueue.cancelRunnable(cacheTask);
                    } else {
                        cacheOutQueue.cancelRunnable(cacheTask);
                        cacheOutQueue.cancelRunnable(runningTask);
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
                    if (image instanceof AnimatedFileDrawable && !((AnimatedFileDrawable) image).isWebmSticker) {
                        boolean imageSet = false;
                        AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                        for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                            ImageReceiver imgView = finalImageReceiverArray.get(a);
                            AnimatedFileDrawable toSet = (a == 0 ? fileDrawable : fileDrawable.makeCopy());
                            if (imgView.setImageBitmapByKey(toSet, key, type, false, finalImageReceiverGuidsArray.get(a))) {
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
                            imgView.setImageBitmapByKey(image, key, types.get(a), false, finalImageReceiverGuidsArray.get(a));
                        }
                    }
                    if (decrementKey != null) {
                        decrementUseCount(decrementKey);
                    }
                });
            }
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver imageReceiver = imageReceiverArray.get(a);
                imageLoadingByTag.remove(imageReceiver.getTag(type));
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

        int commonCacheSize = (int) (cacheSize * 0.8f);
        int smallImagesCacheSize =  (int) (cacheSize * 0.2f);

        memCache = new LruCache<BitmapDrawable>(commonCacheSize) {
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
                        ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
                        bitmapToRecycle.add(b);
                        AndroidUtilities.recycleBitmaps(bitmapToRecycle);
                    }
                }
            }
        };
        smallImagesMemCache = new LruCache<BitmapDrawable>(smallImagesCacheSize) {
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
                        ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
                        bitmapToRecycle.add(b);
                        AndroidUtilities.recycleBitmaps(bitmapToRecycle);
                    }
                }
            }
        };
        wallpaperMemCache = new LruCache<BitmapDrawable>(cacheSize / 4) {
            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                return value.getBitmap().getByteCount();
            }
        };

        lottieMemCache = new LruCache<BitmapDrawable>(512 * 512 * 2 * 4 * 5) {

            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                return value.getIntrinsicWidth() * value.getIntrinsicHeight() * 4 * 2;
            }

            @Override
            public BitmapDrawable put(String key, BitmapDrawable value) {
                if (value instanceof AnimatedFileDrawable) {
                    cachedAnimatedFileDrawables.add((AnimatedFileDrawable) value);
                }
                return super.put(key, value);
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, final BitmapDrawable oldValue, BitmapDrawable newValue) {
                final Integer count = bitmapUseCounts.get(key);
                if (oldValue instanceof AnimatedFileDrawable) {
                    cachedAnimatedFileDrawables.remove((AnimatedFileDrawable) oldValue);
                }
                if (count == null || count == 0) {
                    if (oldValue instanceof AnimatedFileDrawable) {
                        ((AnimatedFileDrawable) oldValue).recycle();
                    }
                    if (oldValue instanceof RLottieDrawable) {
                        ((RLottieDrawable) oldValue).recycle(false);
                    }
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
        AndroidUtilities.createEmptyFile(new File(cachePath, ".nomedia"));
        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final int currentAccount = a;
            FileLoader.getInstance(a).setDelegate(new FileLoader.FileLoaderDelegate() {
                @Override
                public void fileUploadProgressChanged(FileUploadOperation operation, final String location, long uploadedSize, long totalSize, final boolean isEncrypted) {
                    fileProgresses.put(location, new long[]{uploadedSize, totalSize});
                    long currentTime = SystemClock.elapsedRealtime();
                    if (operation.lastProgressUpdateTime == 0 || operation.lastProgressUpdateTime < currentTime - 100 || uploadedSize == totalSize) {
                        operation.lastProgressUpdateTime = currentTime;

                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileUploadProgressChanged, location, uploadedSize, totalSize, isEncrypted));
                    }
                }

                @Override
                public void fileDidUploaded(final String location, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv, final long totalFileSize) {
                    Utilities.stageQueue.postRunnable(() -> {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileUploaded, location, inputFile, inputEncryptedFile, key, iv, totalFileSize));
                        fileProgresses.remove(location);
                    });
                }

                @Override
                public void fileDidFailedUpload(final String location, final boolean isEncrypted) {
                    Utilities.stageQueue.postRunnable(() -> {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileUploadFailed, location, isEncrypted));
                        fileProgresses.remove(location);
                    });
                }

                @Override
                public void fileDidLoaded(final String location, final File finalFile, Object parentObject, final int type) {
                    fileProgresses.remove(location);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (finalFile != null && (location.endsWith(".mp4") || location.endsWith(".jpg"))) {
                            FilePathDatabase.FileMeta meta = FileLoader.getFileMetadataFromParent(currentAccount, parentObject);
                            if (meta != null) {
                                MessageObject messageObject = null;
                                if (parentObject instanceof MessageObject) {
                                    messageObject = (MessageObject) parentObject;
                                }
                                long dialogId = meta.dialogId;
                                int flag;
                                if (dialogId >= 0) {
                                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;
                                } else {
                                    if (ChatObject.isChannelAndNotMegaGroup(MessagesController.getInstance(currentAccount).getChat(-dialogId))) {
                                        flag = SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
                                    } else {
                                        flag = SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
                                    }
                                }
                                if (SaveToGallerySettingsHelper.needSave(flag, meta, messageObject, currentAccount)) {
                                    AndroidUtilities.addMediaToGallery(finalFile.toString());
                                }
                            }
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileLoaded, location, finalFile);
                        ImageLoader.this.fileDidLoaded(location, finalFile, type);
                    });
                }

                @Override
                public void fileDidFailedLoad(final String location, final int canceled) {
                    fileProgresses.remove(location);
                    AndroidUtilities.runOnUIThread(() -> {
                        ImageLoader.this.fileDidFailedLoad(location, canceled);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileLoadFailed, location, canceled);
                    });
                }

                @Override
                public void fileLoadProgressChanged(FileLoadOperation operation, final String location, long uploadedSize, long totalSize) {
                    fileProgresses.put(location, new long[]{uploadedSize, totalSize});
                    long currentTime = SystemClock.elapsedRealtime();
                    if (operation.lastProgressUpdateTime == 0 || operation.lastProgressUpdateTime < currentTime - 500 || uploadedSize == 0) {
                        operation.lastProgressUpdateTime = currentTime;
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileLoadProgressChanged, location, uploadedSize, totalSize));
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
        checkMediaPaths(null);
    }

    public void checkMediaPaths(Runnable after) {
        cacheOutQueue.postRunnable(() -> {
            final SparseArray<File> paths = createMediaPaths();
            AndroidUtilities.runOnUIThread(() -> {
                FileLoader.setMediaDirs(paths);
                if (after != null) {
                    after.run();
                }
            });
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

    @TargetApi(26)
    private static void moveDirectory(File source, File target) {
        if (!source.exists() || (!target.exists() && !target.mkdir())) {
            return;
        }
        try (Stream<Path> files = Files.list(source.toPath())) {
            files.forEach(path -> {
                File dest = new File(target, path.getFileName().toString());
                if (Files.isDirectory(path)) {
                    moveDirectory(path.toFile(), dest);
                } else {
                    try {
                        Files.move(path, dest.toPath());
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
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
        AndroidUtilities.createEmptyFile(new File(cachePath, ".nomedia"));

        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("cache path = " + cachePath);
        }

        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File path = Environment.getExternalStorageDirectory();
                if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                    ArrayList<File> dirs = AndroidUtilities.getRootDirs();
                    if (dirs != null) {
                        for (int a = 0, N = dirs.size(); a < N; a++) {
                            File dir = dirs.get(a);
                            if (dir.getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
                                path = dir;
                                break;
                            }
                        }
                    }
                }

                File publicMediaDir = null;
                if (Build.VERSION.SDK_INT >= 30) {
                    File newPath;
                    try {
                        if (ApplicationLoader.applicationContext.getExternalMediaDirs().length > 0) {
                            publicMediaDir = getPublicStorageDir();
                            publicMediaDir = new File(publicMediaDir, "Telegram");
                            publicMediaDir.mkdirs();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    newPath = ApplicationLoader.applicationContext.getExternalFilesDir(null);
                    telegramPath = new File(newPath, "Telegram");
                } else {
                    if (!(path.exists() ? path.isDirectory() : path.mkdirs()) || !path.canWrite()) {
                        path = ApplicationLoader.applicationContext.getExternalFilesDir(null);
                    }
                    telegramPath = new File(path, "Telegram");
                }
                telegramPath.mkdirs();

                if (Build.VERSION.SDK_INT >= 19 && !telegramPath.isDirectory()) {
                    ArrayList<File> dirs = AndroidUtilities.getDataDirs();
                    for (int a = 0, N = dirs.size(); a < N; a++) {
                        File dir = dirs.get(a);
                        if (dir != null && !TextUtils.isEmpty(SharedConfig.storageCacheDir) && dir.getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
                            path = dir;
                            telegramPath = new File(path, "Telegram");
                            telegramPath.mkdirs();
                            break;
                        }
                    }
                }

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
                            AndroidUtilities.createEmptyFile(new File(audioPath, ".nomedia"));
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
                            AndroidUtilities.createEmptyFile(new File(documentPath, ".nomedia"));
                            mediaDirs.put(FileLoader.MEDIA_DIR_DOCUMENT, documentPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("documents path = " + documentPath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    try {
                        File normalNamesPath = new File(telegramPath, "Telegram Files");
                        normalNamesPath.mkdir();
                        if (normalNamesPath.isDirectory() && canMoveFiles(cachePath, normalNamesPath, FileLoader.MEDIA_DIR_FILES)) {
                            AndroidUtilities.createEmptyFile(new File(normalNamesPath, ".nomedia"));
                            mediaDirs.put(FileLoader.MEDIA_DIR_FILES, normalNamesPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("files path = " + normalNamesPath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (publicMediaDir != null && publicMediaDir.isDirectory()) {
                    try {
                        File imagePath = new File(publicMediaDir, "Telegram Images");
                        imagePath.mkdir();
                        if (imagePath.isDirectory() && canMoveFiles(cachePath, imagePath, FileLoader.MEDIA_DIR_IMAGE)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE_PUBLIC, imagePath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("image path = " + imagePath);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    try {
                        File videoPath = new File(publicMediaDir, "Telegram Video");
                        videoPath.mkdir();
                        if (videoPath.isDirectory() && canMoveFiles(cachePath, videoPath, FileLoader.MEDIA_DIR_VIDEO)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO_PUBLIC, videoPath);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("video path = " + videoPath);
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private File getPublicStorageDir() {
        File publicMediaDir = ApplicationLoader.applicationContext.getExternalMediaDirs()[0];
        if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
            for (int i = 0; i < ApplicationLoader.applicationContext.getExternalMediaDirs().length; i++) {
                File f = ApplicationLoader.applicationContext.getExternalMediaDirs()[i];
                if (f != null && f.getPath().startsWith(SharedConfig.storageCacheDir)) {
                    publicMediaDir = ApplicationLoader.applicationContext.getExternalMediaDirs()[i];
                }
            }
        }
        return publicMediaDir;
    }

    private boolean canMoveFiles(File from, File to, int type) {
        RandomAccessFile file = null;
        try {
            File srcFile = null;
            File dstFile = null;
            if (type == FileLoader.MEDIA_DIR_IMAGE) {
                srcFile = new File(from, "000000000_999999_temp.f");
                dstFile = new File(to, "000000000_999999.f");
            } else if (type == FileLoader.MEDIA_DIR_DOCUMENT || type == FileLoader.MEDIA_DIR_FILES) {
                srcFile = new File(from, "000000000_999999_temp.f");
                dstFile = new File(to, "000000000_999999.f");
            } else if (type == FileLoader.MEDIA_DIR_AUDIO) {
                srcFile = new File(from, "000000000_999999_temp.f");
                dstFile = new File(to, "000000000_999999.f");
            } else if (type == FileLoader.MEDIA_DIR_VIDEO) {
                srcFile = new File(from, "000000000_999999_temp.f");
                dstFile = new File(to, "000000000_999999.f");
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
        long[] progress = fileProgresses.get(location);
        if (progress == null) {
            return null;
        }
        if (progress[1] == 0) {
            return 0.0f;
        }
        return Math.min(1f, progress[0] / (float) progress[1]);
    }

    public long[] getFileProgressSizes(String location) {
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
        LruCache<BitmapDrawable> currentCache = memCache;
        BitmapDrawable b = currentCache.get(oldKey);
        if (b == null) {
            currentCache = smallImagesMemCache;
            b = currentCache.get(oldKey);
        }
        replacedBitmaps.put(oldKey, newKey);
        if (b != null) {
            BitmapDrawable oldBitmap = currentCache.get(newKey);
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
                currentCache.remove(oldKey);
                currentCache.put(newKey, b);
                ignoreRemoval = null;
            } else {
                currentCache.remove(oldKey);
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
        smallImagesMemCache.remove(key);
    }

    public boolean isInMemCache(String key, boolean animated) {
        if (animated) {
            return getFromLottieCache(key) != null;
        } else {
            return getFromMemCache(key) != null;
        }
    }

    public void clearMemory() {
        smallImagesMemCache.evictAll();
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
        ArrayList<Runnable> runnables = imageReceiver.getLoadingOperations();
        if (!runnables.isEmpty()) {
            for (int i = 0; i < runnables.size(); i++) {
                imageLoadQueue.cancelRunnable(runnables.get(i));
            }
            runnables.clear();
        }
        imageReceiver.addLoadingImageRunnable(null);
        imageLoadQueue.postRunnable(() -> {
            for (int a = 0; a < 3; a++) {
                int type;
                if (a > 0 && !cancelAll) {
                    return;
                }
                if (a == 0) {
                    type = ImageReceiver.TYPE_THUMB;
                } else if (a == 1) {
                    type = ImageReceiver.TYPE_IMAGE;
                } else {
                    type = ImageReceiver.TYPE_MEDIA;
                }
                int TAG = imageReceiver.getTag(type);
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
        return getFromMemCache(key);
    }

    private void replaceImageInCacheInternal(final String oldKey, final String newKey, final ImageLocation newLocation) {
        for (int i = 0; i < 2; i++) {
            ArrayList<String> arr;
            if (i == 0) {
                arr = memCache.getFilterKeys(oldKey);
            } else {
                arr = smallImagesMemCache.getFilterKeys(oldKey);
            }
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
    }

    public void replaceImageInCache(final String oldKey, final String newKey, final ImageLocation newLocation, boolean post) {
        if (post) {
            AndroidUtilities.runOnUIThread(() -> replaceImageInCacheInternal(oldKey, newKey, newLocation));
        } else {
            replaceImageInCacheInternal(oldKey, newKey, newLocation);
        }
    }

    public void putImageToCache(BitmapDrawable bitmap, String key, boolean smallImage) {
        if (smallImage) {
            smallImagesMemCache.put(key, bitmap);
        } else {
            memCache.put(key, bitmap);
        }
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

    private void createLoadOperationForImageReceiver(final ImageReceiver imageReceiver, final String key, final String url, final String ext, final ImageLocation imageLocation, final String filter, final long size, final int cacheType, final int type, final int thumb, int guid) {
        if (imageReceiver == null || url == null || key == null || imageLocation == null) {
            return;
        }
        int TAG = imageReceiver.getTag(type);
        if (TAG == 0) {
            imageReceiver.setTag(TAG = lastImageNum, type);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE) {
                lastImageNum = 0;
            }
        }

        final int finalTag = TAG;
        final boolean finalIsNeedsQualityThumb = imageReceiver.isNeedsQualityThumb();
        final Object parentObject = imageReceiver.getParentObject();
        final TLRPC.Document qualityDocument = imageReceiver.getQualityThumbDocument();
        final boolean shouldGenerateQualityThumb = imageReceiver.isShouldGenerateQualityThumb();
        final int currentAccount = imageReceiver.getCurrentAccount();
        final boolean currentKeyQuality = type == ImageReceiver.TYPE_IMAGE && imageReceiver.isCurrentKeyQuality();

        final Runnable loadOperationRunnable = () -> {
            boolean added = false;
            if (thumb != 2) {
                CacheImage alreadyLoadingUrl = imageLoadingByUrl.get(url);
                CacheImage alreadyLoadingCache = imageLoadingByKeys.get(key);
                CacheImage alreadyLoadingImage = imageLoadingByTag.get(finalTag);
                if (alreadyLoadingImage != null) {
                    if (alreadyLoadingImage == alreadyLoadingCache) {
                        alreadyLoadingImage.setImageReceiverGuid(imageReceiver, guid);
                        added = true;
                    } else if (alreadyLoadingImage == alreadyLoadingUrl) {
                        if (alreadyLoadingCache == null) {
                            alreadyLoadingImage.replaceImageReceiver(imageReceiver, key, filter, type, guid);
                        }
                        added = true;
                    } else {
                        alreadyLoadingImage.removeImageReceiver(imageReceiver);
                    }
                }

                if (!added && alreadyLoadingCache != null) {
                    alreadyLoadingCache.addImageReceiver(imageReceiver, key, filter, type, guid);
                    added = true;
                }
                if (!added && alreadyLoadingUrl != null) {
                    alreadyLoadingUrl.addImageReceiver(imageReceiver, key, filter, type, guid);
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
                    int mediaType;
                    boolean bigThumb;
                    if (parentObject instanceof MessageObject) {
                        MessageObject parentMessageObject = (MessageObject) parentObject;
                        parentDocument = parentMessageObject.getDocument();
                        localPath = parentMessageObject.messageOwner.attachPath;
                        cachePath = FileLoader.getInstance(currentAccount).getPathToMessage(parentMessageObject.messageOwner);
                        mediaType = parentMessageObject.getMediaType();
                        bigThumb = false;
                    } else if (qualityDocument != null) {
                        parentDocument = qualityDocument;
                        cachePath = FileLoader.getInstance(currentAccount).getPathToAttach(parentDocument, true);
                        if (MessageObject.isVideoDocument(parentDocument)) {
                            mediaType = FileLoader.MEDIA_DIR_VIDEO;
                        } else {
                            mediaType = FileLoader.MEDIA_DIR_DOCUMENT;
                        }
                        localPath = null;
                        bigThumb = true;
                    } else {
                        parentDocument = null;
                        localPath = null;
                        cachePath = null;
                        mediaType = 0;
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
                                generateThumb(mediaType, attachPath, info);
                            }
                            return;
                        }
                    }
                }

                if (thumb != 2) {
                    boolean isEncrypted = imageLocation.isEncrypted();
                    CacheImage img = new CacheImage();
                    img.priority = imageReceiver.getFileLoadingPriority() == FileLoader.PRIORITY_LOW ? 0 : 1;
                    if (!currentKeyQuality) {
                        if (imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION || MessageObject.isGifDocument(imageLocation.webFile) || MessageObject.isGifDocument(imageLocation.document) || MessageObject.isRoundVideoDocument(imageLocation.document) || MessageObject.isVideoSticker(imageLocation.document)) {
                            img.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
                        } else if (imageLocation.path != null) {
                            String location = imageLocation.path;
                            if (!location.startsWith("vthumb") && !location.startsWith("thumb")) {
                                String trueExt = getHttpUrlExtension(location, "jpg");
                                if (trueExt.equals("webm") || trueExt.equals("mp4") || trueExt.equals("gif")) {
                                    img.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
                                } else if ("tgs".equals(ext)) {
                                    img.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
                                }
                            }
                        }
                    }

                    if (cacheFile == null) {
                        long fileSize = 0;
                        if (imageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize || imageLocation.photoSize instanceof TLRPC.TL_photoPathSize) {
                            onlyCache = true;
                        } else if (imageLocation.secureDocument != null) {
                            img.secureDocument = imageLocation.secureDocument;
                            onlyCache = img.secureDocument.secureFile.dc_id == Integer.MIN_VALUE;
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                        } else if (!(AUTOPLAY_FILTER.equals(filter) || isAnimatedAvatar(filter)) && (cacheType != 0 || size <= 0 || imageLocation.path != null || isEncrypted)) {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                            if (cacheFile.exists()) {
                                cacheFileExists = true;
                            } else if (cacheType == 2) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url + ".enc");
                            }
                            if (imageLocation.document != null) {
                                if (imageLocation.document instanceof DocumentObject.ThemeDocument) {
                                    DocumentObject.ThemeDocument themeDocument = (DocumentObject.ThemeDocument) imageLocation.document;
                                    if (themeDocument.wallpaper == null) {
                                        onlyCache = true;
                                    }
                                    img.imageType = FileLoader.IMAGE_TYPE_THEME_PREVIEW;
                                } else if ("application/x-tgsdice".equals(imageLocation.document.mime_type)) {
                                    img.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
                                    onlyCache = true;
                                } else if ("application/x-tgsticker".equals(imageLocation.document.mime_type)) {
                                    img.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
                                } else if ("application/x-tgwallpattern".equals(imageLocation.document.mime_type)) {
                                    img.imageType = FileLoader.IMAGE_TYPE_SVG;
                                } else {
                                    String name = FileLoader.getDocumentFileName(imageLocation.document);
                                    if (name.endsWith(".svg")) {
                                        img.imageType = FileLoader.IMAGE_TYPE_SVG;
                                    }
                                }
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
                            if ((isAnimatedAvatar(filter) || AUTOPLAY_FILTER.equals(filter)) && !cacheFile.exists()) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document.dc_id + "_" + document.id + ".temp");
                            }
                            if (document instanceof DocumentObject.ThemeDocument) {
                                DocumentObject.ThemeDocument themeDocument = (DocumentObject.ThemeDocument) document;
                                if (themeDocument.wallpaper == null) {
                                    onlyCache = true;
                                }
                                img.imageType = FileLoader.IMAGE_TYPE_THEME_PREVIEW;
                            } else if ("application/x-tgsdice".equals(imageLocation.document.mime_type)) {
                                img.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
                                onlyCache = true;
                            } else if ("application/x-tgsticker".equals(document.mime_type)) {
                                img.imageType = FileLoader.IMAGE_TYPE_LOTTIE;
                            } else if ("application/x-tgwallpattern".equals(document.mime_type)) {
                                img.imageType = FileLoader.IMAGE_TYPE_SVG;
                            } else {
                                String name = FileLoader.getDocumentFileName(imageLocation.document);
                                if (name.endsWith(".svg")) {
                                    img.imageType = FileLoader.IMAGE_TYPE_SVG;
                                }
                            }
                            fileSize = document.size;
                        } else if (imageLocation.webFile != null) {
                            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url);
                        } else {
                            if (cacheType == 1) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                            } else {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE), url);
                            }
                            if (isAnimatedAvatar(filter) || AUTOPLAY_FILTER.equals(filter) && imageLocation.location != null && !cacheFile.exists()) {
                                cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), imageLocation.location.volume_id + "_" + imageLocation.location.local_id + ".temp");
                            }
                        }
                        if (hasAutoplayFilter(filter) || isAnimatedAvatar(filter)) {
                            img.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
                            img.size = fileSize;
                            if (AUTOPLAY_FILTER.equals(filter) || isAnimatedAvatar(filter)) {
                                onlyCache = true;
                            }
                        }
                    }

                    img.type = type;
                    img.key = key;
                    img.filter = filter;
                    img.imageLocation = imageLocation;
                    img.ext = ext;
                    img.currentAccount = currentAccount;
                    img.parentObject = parentObject;
                    if (imageLocation.imageType != 0) {
                        img.imageType = imageLocation.imageType;
                    }
                    if (cacheType == 2) {
                        img.encryptionKeyPath = new File(FileLoader.getInternalCacheDir(), url + ".enc.key");
                    }
                    img.addImageReceiver(imageReceiver, key, filter, type, guid);

                    if (onlyCache || cacheFileExists || cacheFile.exists()) {
                        img.finalFilePath = cacheFile;
                        img.imageLocation = imageLocation;
                        img.cacheTask = new CacheOutTask(img);

                        imageLoadingByKeys.put(key, img);
                        if (thumb != 0) {
                            cacheThumbOutQueue.postRunnable(img.cacheTask);
                        } else {
                            img.runningTask = cacheOutQueue.postRunnable(img.cacheTask, img.priority);
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
                            int loadingPriority = thumb != 0 ? FileLoader.PRIORITY_HIGH : imageReceiver.getFileLoadingPriority();
                            if (imageLocation.location != null) {
                                int localCacheType = cacheType;
                                if (localCacheType == 0 && (size <= 0 || imageLocation.key != null)) {
                                    localCacheType = 1;
                                }
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation, parentObject, ext, loadingPriority, localCacheType);
                            } else if (imageLocation.document != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.document, parentObject, loadingPriority, cacheType);
                            } else if (imageLocation.secureDocument != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.secureDocument, loadingPriority);
                            } else if (imageLocation.webFile != null) {
                                FileLoader.getInstance(currentAccount).loadFile(imageLocation.webFile, loadingPriority, cacheType);
                            }
                            if (imageReceiver.isForceLoding()) {
                                forceLoadingImages.put(img.key, 0);
                            }
                        }
                    }
                }
            }
        };
        imageLoadQueue.postRunnable(loadOperationRunnable, imageReceiver.getFileLoadingPriority() == FileLoader.PRIORITY_LOW ? 0 : 1);
        imageReceiver.addLoadingImageRunnable(loadOperationRunnable);
    }

    public void preloadArtwork(String athumbUrl) {
        imageLoadQueue.postRunnable(() -> {
            String ext = getHttpUrlExtension(athumbUrl, "jpg");
            String url = Utilities.MD5(athumbUrl) + "." + ext;
            File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
            if (cacheFile.exists()) {
                return;
            }
            ImageLocation imageLocation = ImageLocation.getForPath(athumbUrl);
            CacheImage img = new CacheImage();
            img.type = ImageReceiver.TYPE_THUMB;
            img.key = Utilities.MD5(athumbUrl);
            img.filter = null;
            img.imageLocation = imageLocation;
            img.ext = ext;
            img.parentObject = null;
            if (imageLocation.imageType != 0) {
                img.imageType = imageLocation.imageType;
            }
            img.url = url;
            imageLoadingByUrl.put(url, img);
            String file = Utilities.MD5(imageLocation.path);
            File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            img.tempFilePath = new File(cacheDir, file + "_temp.jpg");
            img.finalFilePath = cacheFile;
            img.artworkTask = new ArtworkLoadTask(img);
            artworkTasks.add(img.artworkTask);
            runArtworkTasks(false);
        });
    }

    public void loadImageForImageReceiver(ImageReceiver imageReceiver) {
        if (imageReceiver == null) {
            return;
        }
        boolean imageSet = false;
        boolean mediaSet = false;
        String mediaKey = imageReceiver.getMediaKey();
        int guid = imageReceiver.getNewGuid();
        if (mediaKey != null) {
            ImageLocation mediaLocation = imageReceiver.getMediaLocation();
            Drawable drawable;
            if (useLottieMemCache(mediaLocation, mediaKey)) {
                drawable = getFromLottieCache(mediaKey);
            } else {
                drawable = memCache.get(mediaKey);
                if (drawable != null) {
                    memCache.moveToFront(mediaKey);
                }
                if (drawable == null) {
                    drawable = smallImagesMemCache.get(mediaKey);
                    if (drawable != null) {
                        smallImagesMemCache.moveToFront(mediaKey);
                    }
                }
                if (drawable == null) {
                    drawable = wallpaperMemCache.get(mediaKey);
                    if (drawable != null) {
                        wallpaperMemCache.moveToFront(mediaKey);
                    }
                }
            }
            boolean hasBitmap = true;
            if (drawable instanceof RLottieDrawable) {
                hasBitmap = ((RLottieDrawable) drawable).hasBitmap();
            } else if (drawable instanceof AnimatedFileDrawable) {
                hasBitmap = ((AnimatedFileDrawable) drawable).hasBitmap();
            }
            if (hasBitmap && drawable != null) {
                cancelLoadingForImageReceiver(imageReceiver, true);
                imageReceiver.setImageBitmapByKey(drawable, mediaKey, ImageReceiver.TYPE_MEDIA, true, guid);
                imageSet = true;
                if (!imageReceiver.isForcePreview()) {
                    return;
                }
            } else if (drawable != null) {
                mediaSet = true;
                imageReceiver.setImageBitmapByKey(drawable, mediaKey, ImageReceiver.TYPE_MEDIA, true, guid);
            }
        }
        String imageKey = imageReceiver.getImageKey();
        if (!imageSet && imageKey != null) {
            ImageLocation imageLocation = imageReceiver.getImageLocation();
            Drawable drawable = null;
            if (useLottieMemCache(imageLocation, imageKey)) {
                drawable = getFromLottieCache(imageKey);
            }
            if (drawable == null) {
                drawable = memCache.get(imageKey);
                if (drawable != null) {
                    memCache.moveToFront(imageKey);
                }
                if (drawable == null) {
                    drawable = smallImagesMemCache.get(imageKey);
                    if (drawable != null) {
                        smallImagesMemCache.moveToFront(imageKey);
                    }
                }
                if (drawable == null) {
                    drawable = wallpaperMemCache.get(imageKey);
                    if (drawable != null) {
                        wallpaperMemCache.moveToFront(imageKey);
                    }
                }
            }
            if (drawable != null) {
                cancelLoadingForImageReceiver(imageReceiver, true);
                imageReceiver.setImageBitmapByKey(drawable, imageKey, ImageReceiver.TYPE_IMAGE, true, guid);
                imageSet = true;
                if (!imageReceiver.isForcePreview() && (mediaKey == null || mediaSet)) {
                    return;
                }
            }
        }
        boolean thumbSet = false;
        String thumbKey = imageReceiver.getThumbKey();
        if (thumbKey != null) {
            ImageLocation thumbLocation = imageReceiver.getThumbLocation();
            Drawable drawable;
            if (useLottieMemCache(thumbLocation, thumbKey)) {
                drawable = getFromLottieCache(thumbKey);
            } else {
                drawable = memCache.get(thumbKey);
                if (drawable != null) {
                    memCache.moveToFront(thumbKey);
                }
                if (drawable == null) {
                    drawable = smallImagesMemCache.get(thumbKey);
                    if (drawable != null) {
                        smallImagesMemCache.moveToFront(thumbKey);
                    }
                }
                if (drawable == null) {
                    drawable = wallpaperMemCache.get(thumbKey);
                    if (drawable != null) {
                        wallpaperMemCache.moveToFront(thumbKey);
                    }
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
        TLRPC.Document qualityDocument = imageReceiver.getQualityThumbDocument();
        ImageLocation thumbLocation = imageReceiver.getThumbLocation();
        String thumbFilter = imageReceiver.getThumbFilter();
        ImageLocation mediaLocation = imageReceiver.getMediaLocation();
        String mediaFilter = imageReceiver.getMediaFilter();
        ImageLocation originalImageLocation = imageReceiver.getImageLocation();
        String imageFilter = imageReceiver.getImageFilter();
        ImageLocation imageLocation = originalImageLocation;
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
        String imageExt;
        if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
            imageExt = "mp4";
        } else {
            imageExt = null;
        }
        String mediaExt;
        if (mediaLocation != null && mediaLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
            mediaExt = "mp4";
        } else {
            mediaExt = null;
        }
        String thumbExt = imageReceiver.getExt();
        if (thumbExt == null) {
            thumbExt = "jpg";
        }
        if (imageExt == null) {
            imageExt = thumbExt;
        }
        if (mediaExt == null) {
            mediaExt = thumbExt;
        }

        for (int a = 0; a < 2; a++) {
            ImageLocation object;
            String ext;
            if (a == 0) {
                object = imageLocation;
                ext = imageExt;
            } else {
                object = mediaLocation;
                ext = mediaExt;
            }
            if (object == null) {
                continue;
            }
            String key = object.getKey(parentObject, mediaLocation != null ? mediaLocation : imageLocation, false);
            if (key == null) {
                continue;
            }
            String url = object.getKey(parentObject, mediaLocation != null ? mediaLocation : imageLocation, true);
            if (object.path != null) {
                url = url + "." + getHttpUrlExtension(object.path, "jpg");
            } else if (object.photoSize instanceof TLRPC.TL_photoStrippedSize || object.photoSize instanceof TLRPC.TL_photoPathSize) {
                url = url + "." + ext;
            } else if (object.location != null) {
                url = url + "." + ext;
                if (imageReceiver.getExt() != null || object.location.key != null || object.location.volume_id == Integer.MIN_VALUE && object.location.local_id < 0) {
                    saveImageToCache = true;
                }
            } else if (object.webFile != null) {
                String defaultExt = FileLoader.getMimeTypePart(object.webFile.mime_type);
                url = url + "." + getHttpUrlExtension(object.webFile.url, defaultExt);
            } else if (object.secureDocument != null) {
                url = url + "." + ext;
            } else if (object.document != null) {
                if (a == 0 && qualityThumb) {
                    key = "q_" + key;
                }
                String docExt = FileLoader.getDocumentFileName(object.document);
                int idx;
                if ((idx = docExt.lastIndexOf('.')) == -1) {
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
                url = url + docExt;
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
                strippedLoc = mediaLocation != null ? mediaLocation : originalImageLocation;
            }
            thumbKey = thumbLocation.getKey(parentObject, strippedLoc, false);
            thumbUrl = thumbLocation.getKey(parentObject, strippedLoc, true);
            if (thumbLocation.path != null) {
                thumbUrl = thumbUrl + "." + getHttpUrlExtension(thumbLocation.path, "jpg");
            } else if (thumbLocation.photoSize instanceof TLRPC.TL_photoStrippedSize || thumbLocation.photoSize instanceof TLRPC.TL_photoPathSize) {
                thumbUrl = thumbUrl + "." + thumbExt;
            } else if (thumbLocation.location != null) {
                thumbUrl = thumbUrl + "." + thumbExt;
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

        if (imageReceiver.getUniqKeyPrefix() != null && imageKey != null) {
            imageKey = imageReceiver.getUniqKeyPrefix() + imageKey;
        }

        if (imageReceiver.getUniqKeyPrefix() != null && mediaKey != null) {
            mediaKey = imageReceiver.getUniqKeyPrefix() + mediaKey;
        }


        if (imageLocation != null && imageLocation.path != null) {
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, 1, ImageReceiver.TYPE_THUMB, thumbSet ? 2 : 1, guid);
            createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, imageReceiver.getSize(), 1, ImageReceiver.TYPE_IMAGE, 0, guid);
        } else if (mediaLocation != null) {
            int mediaCacheType = imageReceiver.getCacheType();
            int imageCacheType = 1;
            if (mediaCacheType == 0 && saveImageToCache) {
                mediaCacheType = 1;
            }
            int thumbCacheType = mediaCacheType == 0 ? 1 : mediaCacheType;
            if (!thumbSet) {
                createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, 1, guid);
            }
            if (!imageSet) {
                createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, 0, imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid);
            }
            if (!mediaSet) {
                createLoadOperationForImageReceiver(imageReceiver, mediaKey, mediaUrl, mediaExt, mediaLocation, mediaFilter, imageReceiver.getSize(), mediaCacheType, ImageReceiver.TYPE_MEDIA, 0, guid);
            }
        } else {
            int imageCacheType = imageReceiver.getCacheType();
            if (imageCacheType == 0 && saveImageToCache) {
                imageCacheType = 1;
            }
            int thumbCacheType = imageCacheType == 0 ? 1 : imageCacheType;
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, thumbSet ? 2 : 1, guid);
            createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, imageReceiver.getSize(), imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid);
        }
    }

    private BitmapDrawable getFromLottieCache(String imageKey) {
        BitmapDrawable drawable = lottieMemCache.get(imageKey);
        if (drawable instanceof AnimatedFileDrawable) {
            if (((AnimatedFileDrawable) drawable).isRecycled()) {
                lottieMemCache.remove(imageKey);
                drawable = null;
            }
        }
        return drawable;
    }

    private boolean useLottieMemCache(ImageLocation imageLocation, String key) {
        if (key.endsWith("_firstframe") || key.endsWith("_lastframe")) {
            return false;
        }
        return imageLocation != null && (MessageObject.isAnimatedStickerDocument(imageLocation.document, true) || imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE || MessageObject.isVideoSticker(imageLocation.document)) || isAnimatedAvatar(key);
    }

    public boolean hasLottieMemCache(String key) {
        return lottieMemCache != null && lottieMemCache.contains(key);
    }

    private void httpFileLoadError(final String location) {
        imageLoadQueue.postRunnable(() -> {
            CacheImage img = imageLoadingByUrl.get(location);
            if (img == null) {
                return;
            }
            HttpImageTask oldTask = img.httpTask;
            if (oldTask != null) {
                img.httpTask = new HttpImageTask(oldTask.cacheImage, oldTask.imageSize);
                httpTasks.add(img.httpTask);
            }
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
            if (oldTask != null) {
                img.artworkTask = new ArtworkLoadTask(oldTask.cacheImage);
                artworkTasks.add(img.artworkTask);
            }
            runArtworkTasks(false);
        });
    }

    private void fileDidLoaded(final String location, final File finalFile, final int mediaType) {
        imageLoadQueue.postRunnable(() -> {
            ThumbGenerateInfo info = waitingForQualityThumb.get(location);
            if (info != null && info.parentDocument != null) {
                generateThumb(mediaType, finalFile, info);
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
                int type = img.types.get(a);
                ImageReceiver imageReceiver = img.imageReceiverArray.get(a);
                int guid = img.imageReceiverGuidsArray.get(a);
                CacheImage cacheImage = imageLoadingByKeys.get(key);
                if (cacheImage == null) {
                    cacheImage = new CacheImage();
                    cacheImage.priority = img.priority;
                    cacheImage.secureDocument = img.secureDocument;
                    cacheImage.currentAccount = img.currentAccount;
                    cacheImage.finalFilePath = finalFile;
                    cacheImage.parentObject = img.parentObject;
                    cacheImage.key = key;
                    cacheImage.imageLocation = img.imageLocation;
                    cacheImage.type = type;
                    cacheImage.ext = img.ext;
                    cacheImage.encryptionKeyPath = img.encryptionKeyPath;
                    cacheImage.cacheTask = new CacheOutTask(cacheImage);
                    cacheImage.filter = filter;
                    cacheImage.imageType = img.imageType;
                    imageLoadingByKeys.put(key, cacheImage);
                    tasks.add(cacheImage.cacheTask);
                }
                cacheImage.addImageReceiver(imageReceiver, key, filter, type, guid);
            }
            for (int a = 0; a < tasks.size(); a++) {
                CacheOutTask task = tasks.get(a);
                if (task.cacheImage.type == ImageReceiver.TYPE_THUMB) {
                    cacheThumbOutQueue.postRunnable(task);
                } else {
                    cacheOutQueue.postRunnable(task, task.cacheImage.priority);
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
            } else if (Build.VERSION.SDK_INT < 30 || !"content".equals(uri.getScheme())) {
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

        Matrix matrix = null;
        try {
            int orientation = 0;
            if (path != null) {
                ExifInterface exif = new ExifInterface(path);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } else if (uri != null) {
                try (InputStream stream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                    ExifInterface exif = new ExifInterface(stream);
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                } catch (Throwable ignore) {

                }
            }
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix = new Matrix();
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix = new Matrix();
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix = new Matrix();
                    matrix.postRotate(270);
                    break;
            }
        } catch (Throwable ignore) {

        }

        scaleFactor /= bmOptions.inSampleSize;
        if (scaleFactor > 1) {
            if (matrix == null) {
                matrix = new Matrix();
            }
            matrix.postScale(1.0f / scaleFactor, 1.0f / scaleFactor);
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
        File file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true);
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

    private static TLRPC.PhotoSize scaleAndSaveImageInternal(TLRPC.PhotoSize photoSize, Bitmap bitmap, Bitmap.CompressFormat compressFormat, boolean progressive, int w, int h, float photoW, float photoH, float scaleFactor, int quality, boolean cache, boolean scaleAnyway, boolean forceCacheDir) throws Exception {
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

            photoSize = new TLRPC.TL_photoSize_layer127();
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
        File fileDir;
        if (forceCacheDir) {
            fileDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
        } else {
            fileDir = location.volume_id != Integer.MIN_VALUE ? FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE) : FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
        }
        final File cacheFile = new File(fileDir, fileName);
        //TODO was crash in DEBUG_PRIVATE
//        if (compressFormat == Bitmap.CompressFormat.JPEG && progressive && BuildVars.DEBUG_VERSION) {
//            photoSize.size = Utilities.saveProgressiveJpeg(scaledBitmap, scaledBitmap.getWidth(), scaledBitmap.getHeight(), scaledBitmap.getRowBytes(), quality, cacheFile.getAbsolutePath());
//        } else {
        FileOutputStream stream = new FileOutputStream(cacheFile);
        scaledBitmap.compress(compressFormat, quality, stream);
        if (!cache) {
            photoSize.size = (int) stream.getChannel().size();
        }
        stream.close();
        // }
        if (cache) {
            ByteArrayOutputStream stream2 = new ByteArrayOutputStream();
            scaledBitmap.compress(compressFormat, quality, stream2);
            photoSize.bytes = stream2.toByteArray();
            photoSize.size = photoSize.bytes.length;
            stream2.close();
        }
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }
        return photoSize;
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache) {
        return scaleAndSaveImage(null, bitmap, Bitmap.CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, 0, 0, false);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(TLRPC.PhotoSize photoSize, Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache, boolean forceCacheDir) {
        return scaleAndSaveImage(photoSize, bitmap, Bitmap.CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, 0, 0, forceCacheDir);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight) {
        return scaleAndSaveImage(null, bitmap, Bitmap.CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, boolean progressive, int quality, boolean cache, int minWidth, int minHeight) {
        return scaleAndSaveImage(null, bitmap, Bitmap.CompressFormat.JPEG, progressive, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, Bitmap.CompressFormat compressFormat, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight) {
        return scaleAndSaveImage(null, bitmap, compressFormat, false, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(TLRPC.PhotoSize photoSize, Bitmap bitmap, Bitmap.CompressFormat compressFormat, boolean progressive, float maxWidth, float maxHeight, int quality, boolean cache, int minWidth, int minHeight, boolean forceCacheDir) {
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
            return scaleAndSaveImageInternal(photoSize, bitmap, compressFormat, progressive, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway, forceCacheDir);
        } catch (Throwable e) {
            FileLog.e(e);
            ImageLoader.getInstance().clearMemory();
            System.gc();
            try {
                return scaleAndSaveImageInternal(photoSize, bitmap, compressFormat, progressive, w, h, photoW, photoH, scaleFactor, quality, cache, scaleAnyway, forceCacheDir);
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
        if (message.media == null) {
            return;
        }
        TLRPC.PhotoSize photoSize = findPhotoCachedSize(message);

        if (photoSize != null && photoSize.bytes != null && photoSize.bytes.length != 0) {
            TLRPC.PhotoSize newPhotoSize;
            if (photoSize.location == null || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable) {
                photoSize.location = new TLRPC.TL_fileLocationToBeDeprecated();
                photoSize.location.volume_id = Integer.MIN_VALUE;
                photoSize.location.local_id = SharedConfig.getLastLocalId();
            }
            if (photoSize.h <= 50 && photoSize.w <= 50) {
                newPhotoSize = new TLRPC.TL_photoStrippedSize();
                newPhotoSize.location = photoSize.location;
                newPhotoSize.bytes = photoSize.bytes;
                newPhotoSize.h = photoSize.h;
                newPhotoSize.w = photoSize.w;
            } else {
                File file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true);
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

                newPhotoSize = new TLRPC.TL_photoSize_layer127();
                newPhotoSize.w = photoSize.w;
                newPhotoSize.h = photoSize.h;
                newPhotoSize.location = photoSize.location;
                newPhotoSize.size = photoSize.size;
                newPhotoSize.type = photoSize.type;
            }

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

    private static TLRPC.PhotoSize findPhotoCachedSize(TLRPC.Message message) {
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
        } else if (message.media instanceof TLRPC.TL_messageMediaInvoice && message.media.extended_media instanceof TLRPC.TL_messageExtendedMediaPreview) {
            photoSize = ((TLRPC.TL_messageExtendedMediaPreview) message.media.extended_media).thumb;
        }
        return photoSize;
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

    public static MessageThumb generateMessageThumb(TLRPC.Message message) {
        TLRPC.PhotoSize photoSize = findPhotoCachedSize(message);

        if (photoSize != null && photoSize.bytes != null && photoSize.bytes.length != 0) {
            File file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true);

            TLRPC.TL_photoSize newPhotoSize = new TLRPC.TL_photoSize_layer127();
            newPhotoSize.w = photoSize.w;
            newPhotoSize.h = photoSize.h;
            newPhotoSize.location = photoSize.location;
            newPhotoSize.size = photoSize.size;
            newPhotoSize.type = photoSize.type;

            if (file.exists() && message.grouped_id == 0) {
                int h = photoSize.h;
                int w = photoSize.w;
                Point point = ChatMessageCell.getMessageSize(w, h);
                String key = String.format(Locale.US, "%d_%d@%d_%d_b", photoSize.location.volume_id, photoSize.location.local_id, (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density));
                if (!getInstance().isInMemCache(key, false)) {
                    Bitmap bitmap = ImageLoader.loadBitmap(file.getPath(), null, (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density), false);
                    if (bitmap != null) {
                        Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
                        Bitmap scaledBitmap = Bitmaps.createScaledBitmap(bitmap, (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density), true);
                        if (scaledBitmap != bitmap) {
                            bitmap.recycle();
                            bitmap = scaledBitmap;
                        }
                        return new MessageThumb(key, new BitmapDrawable(bitmap));
                    }
                }
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            for (int a = 0, count = message.media.document.thumbs.size(); a < count; a++) {
                TLRPC.PhotoSize size = message.media.document.thumbs.get(a);
                if (size instanceof TLRPC.TL_photoStrippedSize) {
                    TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(message.media.document.thumbs, 320);
                    int h = 0;
                    int w = 0;
                    if (thumbSize != null) {
                        h = thumbSize.h;
                        w = thumbSize.w;
                    } else {
                        for (int k = 0; k < message.media.document.attributes.size(); k++) {
                            if (message.media.document.attributes.get(k) instanceof TLRPC.TL_documentAttributeVideo) {
                                TLRPC.TL_documentAttributeVideo videoAttribute = (TLRPC.TL_documentAttributeVideo) message.media.document.attributes.get(k);
                                h = videoAttribute.h;
                                w = videoAttribute.w;
                                break;
                            }
                        }
                    }

                    Point point = ChatMessageCell.getMessageSize(w, h);
                    String key = String.format(Locale.US, "%s_false@%d_%d_b", ImageLocation.getStrippedKey(message, message, size), (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density));
                    if (!getInstance().isInMemCache(key, false)) {
                        Bitmap b = getStrippedPhotoBitmap(size.bytes, null);
                        if (b != null) {
                            Utilities.blurBitmap(b, 3, 1, b.getWidth(), b.getHeight(), b.getRowBytes());
                            Bitmap scaledBitmap = Bitmaps.createScaledBitmap(b, (int) (point.x / AndroidUtilities.density), (int) (point.y / AndroidUtilities.density), true);
                            if (scaledBitmap != b) {
                                b.recycle();
                                b = scaledBitmap;
                            }
                            return new MessageThumb(key, new BitmapDrawable(b));
                        }
                    }
                }
            }
        }
        return null;
    }

    public void onFragmentStackChanged() {
        for (int i = 0; i < cachedAnimatedFileDrawables.size(); i++) {
            cachedAnimatedFileDrawables.get(i).repeatCount = 0;
        }
    }

    public DispatchQueuePriority getCacheOutQueue() {
        return cacheOutQueue;
    }

    public static class MessageThumb {
        BitmapDrawable drawable;
        String key;

        public MessageThumb(String key, BitmapDrawable bitmapDrawable) {
            this.key = key;
            this.drawable = bitmapDrawable;
        }
    }
}
