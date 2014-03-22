/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.io.RandomAccessFile;
import java.net.URL;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.Scanner;

public class FileLoadOperation {
    private int downloadChunkSize = 1024 * 32;

    public int datacenter_id;
    public TLRPC.InputFileLocation location;
    public volatile int state = 0;
    private int downloadedBytes;
    public int totalBytesCount;
    public FileLoadOperationDelegate delegate;
    public Bitmap image;
    public String filter;
    private byte[] key;
    private byte[] iv;
    private long requestToken = 0;

    private File cacheFileTemp;
    private File cacheFileFinal;
    private File cacheIvTemp;

    private String ext;
    private String httpUrl;
    private URLConnection httpConnection;
    public boolean needBitmapCreate = true;
    private InputStream httpConnectionStream;
    private RandomAccessFile fileOutputStream;
    RandomAccessFile fiv;

    public static interface FileLoadOperationDelegate {
        public abstract void didFinishLoadingFile(FileLoadOperation operation);
        public abstract void didFailedLoadingFile(FileLoadOperation operation);
        public abstract void didChangedLoadProgress(FileLoadOperation operation, float progress);
    }

    public FileLoadOperation(TLRPC.FileLocation fileLocation) {
        if (fileLocation instanceof TLRPC.TL_fileEncryptedLocation) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = fileLocation.volume_id;
            location.volume_id = fileLocation.volume_id;
            location.access_hash = fileLocation.secret;
            location.local_id = fileLocation.local_id;
            iv = new byte[32];
            System.arraycopy(fileLocation.iv, 0, iv, 0, iv.length);
            key = fileLocation.key;
            datacenter_id = fileLocation.dc_id;
        } else if (fileLocation instanceof TLRPC.TL_fileLocation) {
            location = new TLRPC.TL_inputFileLocation();
            location.volume_id = fileLocation.volume_id;
            location.secret = fileLocation.secret;
            location.local_id = fileLocation.local_id;
            datacenter_id = fileLocation.dc_id;
        }
    }

    public FileLoadOperation(TLRPC.Video videoLocation) {
        if (videoLocation instanceof TLRPC.TL_video) {
            location = new TLRPC.TL_inputVideoFileLocation();
            datacenter_id = videoLocation.dc_id;
            location.id = videoLocation.id;
            location.access_hash = videoLocation.access_hash;
        } else if (videoLocation instanceof TLRPC.TL_videoEncrypted) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = videoLocation.id;
            location.access_hash = videoLocation.access_hash;
            datacenter_id = videoLocation.dc_id;
            iv = new byte[32];
            System.arraycopy(videoLocation.iv, 0, iv, 0, iv.length);
            key = videoLocation.key;
        }
        ext = ".mp4";
    }

    public FileLoadOperation(TLRPC.Audio audioLocation) {
        if (audioLocation instanceof TLRPC.TL_audio) {
            location = new TLRPC.TL_inputAudioFileLocation();
            datacenter_id = audioLocation.dc_id;
            location.id = audioLocation.id;
            location.access_hash = audioLocation.access_hash;
        } else if (audioLocation instanceof TLRPC.TL_audioEncrypted) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = audioLocation.id;
            location.access_hash = audioLocation.access_hash;
            datacenter_id = audioLocation.dc_id;
            iv = new byte[32];
            System.arraycopy(audioLocation.iv, 0, iv, 0, iv.length);
            key = audioLocation.key;
        }
        ext = ".m4a";
    }

    public FileLoadOperation(TLRPC.Document documentLocation) {
        if (documentLocation instanceof TLRPC.TL_document) {
            location = new TLRPC.TL_inputDocumentFileLocation();
            datacenter_id = documentLocation.dc_id;
            location.id = documentLocation.id;
            location.access_hash = documentLocation.access_hash;
        } else if (documentLocation instanceof TLRPC.TL_documentEncrypted) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = documentLocation.id;
            location.access_hash = documentLocation.access_hash;
            datacenter_id = documentLocation.dc_id;
            iv = new byte[32];
            System.arraycopy(documentLocation.iv, 0, iv, 0, iv.length);
            key = documentLocation.key;
        }
        ext = documentLocation.file_name;
        int idx = -1;
        if (ext == null || (idx = ext.lastIndexOf(".")) == -1) {
            ext = "";
        } else {
            ext = ext.substring(idx);
            if (ext.length() <= 1) {
                ext = "";
            }
        }
    }

    public FileLoadOperation(String url) {
        httpUrl = url;
    }

    public void start() {
        if (state != 0) {
            return;
        }
        state = 1;
        if (location == null && httpUrl == null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.didFailedLoadingFile(FileLoadOperation.this);
                }
            });
            return;
        }
        boolean ignoreCache = false;
        boolean onlyCache = false;
        boolean isLocalFile = false;
        String fileNameFinal = null;
        String fileNameTemp = null;
        String fileNameIv = null;
        if (httpUrl != null) {
            if (!httpUrl.startsWith("http")) {
                onlyCache = true;
                isLocalFile = true;
                fileNameFinal = httpUrl;
            } else {
                fileNameFinal = Utilities.MD5(httpUrl);
                fileNameTemp = fileNameFinal + "_temp.jpg";
                fileNameFinal += ".jpg";
            }
        } else if (location.volume_id != 0 && location.local_id != 0) {
            fileNameTemp = location.volume_id + "_" + location.local_id + "_temp.jpg";
            fileNameFinal = location.volume_id + "_" + location.local_id + ".jpg";
            if (key != null) {
                fileNameIv = location.volume_id + "_" + location.local_id + ".iv";
            }
            if (datacenter_id == Integer.MIN_VALUE || location.volume_id == Integer.MIN_VALUE) {
                onlyCache = true;
            }
        } else {
            ignoreCache = true;
            needBitmapCreate = false;
            fileNameTemp = datacenter_id + "_" + location.id + "_temp" + ext;
            fileNameFinal = datacenter_id + "_" + location.id + ext;
            if (key != null) {
                fileNameIv = datacenter_id + "_" + location.id + ".iv";
            }
        }

        boolean exist;
        if (isLocalFile) {
            cacheFileFinal = new File(fileNameFinal);
        } else {
            cacheFileFinal = new File(Utilities.getCacheDir(), fileNameFinal);
        }
        final boolean dontDelete = isLocalFile;
        if ((exist = cacheFileFinal.exists()) && !ignoreCache) {
            Utilities.cacheOutQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        int delay = 20;
                        if (FileLoader.getInstance().runtimeHack != null) {
                            delay = 60;
                        }
                        if (FileLoader.lastCacheOutTime != 0 && FileLoader.lastCacheOutTime > System.currentTimeMillis() - delay) {
                            Thread.sleep(delay);
                        }
                        FileLoader.lastCacheOutTime = System.currentTimeMillis();
                        if (state != 1) {
                            return;
                        }
                        if (needBitmapCreate) {
                            FileInputStream is = new FileInputStream(cacheFileFinal);
                            BitmapFactory.Options opts = new BitmapFactory.Options();

                            float w_filter = 0;
                            float h_filter;
                            if (filter != null) {
                                String args[] = filter.split("_");
                                w_filter = Float.parseFloat(args[0]) * Utilities.density;
                                h_filter = Float.parseFloat(args[1]) * Utilities.density;

                                opts.inJustDecodeBounds = true;
                                BitmapFactory.decodeFile(cacheFileFinal.getAbsolutePath(), opts);
                                float photoW = opts.outWidth;
                                float photoH = opts.outHeight;
                                float scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                                if (scaleFactor < 1) {
                                    scaleFactor = 1;
                                }
                                opts.inJustDecodeBounds = false;
                                opts.inSampleSize = (int)scaleFactor;
                            }

                            if (filter == null) {
                                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            } else {
                                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            }
                            opts.inDither = false;
                            image = BitmapFactory.decodeStream(is, null, opts);
                            is.close();
                            if (image == null) {
                                if (!dontDelete && (cacheFileFinal.length() == 0 || filter == null)) {
                                   cacheFileFinal.delete();
                                }
                            } else {
                                if (filter != null && image != null) {
                                    float bitmapW = image.getWidth();
                                    float bitmapH = image.getHeight();
                                    if (bitmapW != w_filter && bitmapW > w_filter) {
                                        float scaleFactor = bitmapW / w_filter;
                                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, (int)w_filter, (int)(bitmapH / scaleFactor), true);
                                        if (image != scaledBitmap) {
                                            if (Build.VERSION.SDK_INT < 11) {
                                                image.recycle();
                                            }
                                            image = scaledBitmap;
                                        }
                                    }

                                }
                                if (FileLoader.getInstance().runtimeHack != null) {
                                    FileLoader.getInstance().runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                                }
                            }
                        }
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                if (image == null) {
                                    delegate.didFailedLoadingFile(FileLoadOperation.this);
                                } else {
                                    delegate.didFinishLoadingFile(FileLoadOperation.this);
                                }
                            }
                        });
                    } catch (Exception e) {
                        if (!dontDelete && cacheFileFinal.length() == 0) {
                            cacheFileFinal.delete();
                        }
                        FileLog.e("tmessages", e);
                    }
                }
            });
        } else {
            if (onlyCache) {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                });
                return;
            }
            cacheFileTemp = new File(Utilities.getCacheDir(), fileNameTemp);
            if (cacheFileTemp.exists()) {
                downloadedBytes = (int)cacheFileTemp.length();
                downloadedBytes = downloadedBytes / 1024 * 1024;
            }
            if (fileNameIv != null) {
                cacheIvTemp = new File(Utilities.getCacheDir(), fileNameIv);
                try {
                    fiv = new RandomAccessFile(cacheIvTemp, "rws");
                    long len = cacheIvTemp.length();
                    if (len > 0 && len % 32 == 0) {
                        fiv.read(iv, 0, 32);
                    } else {
                        downloadedBytes = 0;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    downloadedBytes = 0;
                }
            }
            if (exist) {
                cacheFileFinal.delete();
            }
            try {
                fileOutputStream = new RandomAccessFile(cacheFileTemp, "rws");
                if (downloadedBytes != 0) {
                    fileOutputStream.seek(downloadedBytes);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (fileOutputStream == null) {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                });
                return;
            }
            if (httpUrl != null) {
                startDownloadHTTPRequest();
            } else {
                startDownloadRequest();
            }
        }
    }

    public void cancel() {
        if (state != 1) {
            return;
        }
        state = 2;
        cleanup();
        if (httpUrl == null && requestToken != 0) {
            ConnectionsManager.getInstance().cancelRpc(requestToken, true);
        }
        delegate.didFailedLoadingFile(FileLoadOperation.this);
    }

    private void cleanup() {
        if (httpUrl != null) {
            try {
                if (httpConnectionStream != null) {
                    httpConnectionStream.close();
                }
                httpConnection = null;
                httpConnectionStream = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                    fileOutputStream = null;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            try {
                if (fiv != null) {
                    fiv.close();
                    fiv = null;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    private void onFinishLoadingFile() throws Exception {
        if (state != 1) {
            return;
        }
        state = 3;
        cleanup();
        if (cacheIvTemp != null) {
            cacheIvTemp.delete();
        }
        final boolean renamed = cacheFileTemp.renameTo(cacheFileFinal);

        if (needBitmapCreate) {
            Utilities.cacheOutQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    int delay = 20;
                    if (FileLoader.getInstance().runtimeHack != null) {
                        delay = 60;
                    }
                    if (FileLoader.lastCacheOutTime != 0 && FileLoader.lastCacheOutTime > System.currentTimeMillis() - delay) {
                        try {
                            Thread.sleep(delay);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                    BitmapFactory.Options opts = new BitmapFactory.Options();

                    float w_filter = 0;
                    float h_filter;
                    if (filter != null) {
                        String args[] = filter.split("_");
                        w_filter = Float.parseFloat(args[0]) * Utilities.density;
                        h_filter = Float.parseFloat(args[1]) * Utilities.density;

                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(cacheFileFinal.getAbsolutePath(), opts);
                        float photoW = opts.outWidth;
                        float photoH = opts.outHeight;
                        float scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                        if (scaleFactor < 1) {
                            scaleFactor = 1;
                        }
                        opts.inJustDecodeBounds = false;
                        opts.inSampleSize = (int) scaleFactor;
                    }

                    if (filter == null) {
                        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    } else {
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    }

                    opts.inDither = false;
                    try {
                        if (renamed) {
                            image = BitmapFactory.decodeStream(new FileInputStream(cacheFileFinal), null, opts);
                        } else {
                            try {
                                image = BitmapFactory.decodeStream(new FileInputStream(cacheFileTemp), null, opts);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                                image = BitmapFactory.decodeStream(new FileInputStream(cacheFileFinal), null, opts);
                            }
                        }
                        if (filter != null && image != null) {
                            float bitmapW = image.getWidth();
                            float bitmapH = image.getHeight();
                            if (bitmapW != w_filter && bitmapW > w_filter) {
                                float scaleFactor = bitmapW / w_filter;
                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(image, (int) w_filter, (int) (bitmapH / scaleFactor), true);
                                if (image != scaledBitmap) {
                                    if (Build.VERSION.SDK_INT < 11) {
                                        image.recycle();
                                    }
                                    image = scaledBitmap;
                                }
                            }

                        }
                        if (image != null && FileLoader.getInstance().runtimeHack != null) {
                            FileLoader.getInstance().runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                        }
                        if (image != null) {
                            delegate.didFinishLoadingFile(FileLoadOperation.this);
                        } else {
                            delegate.didFailedLoadingFile(FileLoadOperation.this);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                }
            });
        } else {
            delegate.didFinishLoadingFile(FileLoadOperation.this);
        }
    }

    private void startDownloadHTTPRequest() {
        if (state != 1) {
            return;
        }
        if (httpConnection == null) {
            try {
                URL downloadUrl = new URL(httpUrl);
                httpConnection = downloadUrl.openConnection();
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                });
                return;
            }
        }

        try {
            byte[] data = new byte[1024 * 2];
            int readed = httpConnectionStream.read(data);
            if (readed > 0) {
                fileOutputStream.write(data, 0, readed);
                Utilities.imageLoadQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        startDownloadHTTPRequest();
                    }
                });
            } else if (readed == -1) {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            onFinishLoadingFile();
                        } catch (Exception e) {
                            delegate.didFailedLoadingFile(FileLoadOperation.this);
                        }
                    }
                });
            } else {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                });
            }
        } catch (Exception e) {
            cleanup();
            FileLog.e("tmessages", e);
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.didFailedLoadingFile(FileLoadOperation.this);
                }
            });
        }
    }

    private void startDownloadRequest() {
        if (state != 1) {
            return;
        }
        TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
        req.location = location;
        //if (totalBytesCount == -1) {
        //    req.offset = 0;
        //    req.limit = 0;
        //} else {
            req.offset = downloadedBytes;
            req.limit = downloadChunkSize;
        //}
        requestToken = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                requestToken = 0;
                if (error == null) {
                    TLRPC.TL_upload_file res = (TLRPC.TL_upload_file)response;
                    try {
                        if (res.bytes.limit() == 0) {
                            onFinishLoadingFile();
                            return;
                        }
                        if (key != null) {
                            Utilities.aesIgeEncryption2(res.bytes.buffer, key, iv, false, true, res.bytes.limit());
                        }
                        if (fileOutputStream != null) {
                            FileChannel channel = fileOutputStream.getChannel();
                            channel.write(res.bytes.buffer);
                        }
                        if (fiv != null) {
                            fiv.seek(0);
                            fiv.write(iv);
                        }
                        downloadedBytes += res.bytes.limit();
                        if (totalBytesCount > 0) {
                            delegate.didChangedLoadProgress(FileLoadOperation.this,  Math.min(1.0f, (float)downloadedBytes / (float)totalBytesCount));
                        }
                        if (downloadedBytes % downloadChunkSize == 0 || totalBytesCount > 0 && totalBytesCount != downloadedBytes) {
                            startDownloadRequest();
                        } else {
                            onFinishLoadingFile();
                        }
                    } catch (Exception e) {
                        cleanup();
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                        FileLog.e("tmessages", e);
                    }
                } else {
                    if (error.text.contains("FILE_MIGRATE_")) {
                        String errorMsg = error.text.replace("FILE_MIGRATE_", "");
                        Scanner scanner = new Scanner(errorMsg);
                        scanner.useDelimiter("");
                        Integer val;
                        try {
                            val = scanner.nextInt();
                        } catch (Exception e) {
                            val = null;
                        }
                        if (val == null) {
                            cleanup();
                            delegate.didFailedLoadingFile(FileLoadOperation.this);
                        } else {
                            datacenter_id = val;
                            startDownloadRequest();
                        }
                    } else if (error.text.contains("OFFSET_INVALID")) {
                        if (downloadedBytes % downloadChunkSize == 0) {
                            try {
                                onFinishLoadingFile();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                                cleanup();
                                delegate.didFailedLoadingFile(FileLoadOperation.this);
                            }
                        } else {
                            cleanup();
                            delegate.didFailedLoadingFile(FileLoadOperation.this);
                        }
                    } else {
                        cleanup();
                        delegate.didFailedLoadingFile(FileLoadOperation.this);
                    }
                }
            }
        }, new RPCRequest.RPCProgressDelegate() {
            @Override
            public void progress(int length, int progress) {
                if (totalBytesCount > 0) {
                    delegate.didChangedLoadProgress(FileLoadOperation.this,  Math.min(1.0f, (float)(downloadedBytes + progress) / (float)totalBytesCount));
                } else if (totalBytesCount == -1) {
                    delegate.didChangedLoadProgress(FileLoadOperation.this,  Math.min(1.0f, (float)(progress) / (float)length));
                }
            }
        }, null, true, RPCRequest.RPCRequestClassDownloadMedia, datacenter_id);
    }
}
