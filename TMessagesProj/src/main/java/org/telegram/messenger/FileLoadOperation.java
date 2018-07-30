/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.util.SparseArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.RandomAccessFile;
import java.io.File;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class FileLoadOperation {

    private static class RequestInfo {
        private int requestToken;
        private int offset;
        private TLRPC.TL_upload_file response;
        private TLRPC.TL_upload_webFile responseWeb;
        private TLRPC.TL_upload_cdnFile responseCdn;
    }

    public static class Range {
        private int start;
        private int end;

        private Range(int s, int e) {
            start = s;
            end = e;
        }
    }

    private ArrayList<FileStreamLoadOperation> streamListeners;

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;

    private final static int downloadChunkSize = 1024 * 32;
    private final static int downloadChunkSizeBig = 1024 * 128;
    private final static int cdnChunkCheckSize = 1024 * 128;
    private final static int maxDownloadRequests = 4;
    private final static int maxDownloadRequestsBig = 4;
    private final static int bigFileSizeFrom = 1024 * 1024;
    private final static int maxCdnParts = 1024 * 1024 * 1536 / downloadChunkSizeBig;

    private ArrayList<Range> notLoadedBytesRanges;
    private volatile ArrayList<Range> notLoadedBytesRangesCopy;
    private ArrayList<Range> notRequestedBytesRanges;
    private ArrayList<Range> notCheckedCdnRanges;
    private int requestedBytesCount;

    private int currentAccount;
    private boolean started;
    private int datacenterId;
    private int initialDatacenterId;
    private TLRPC.InputFileLocation location;
    private TLRPC.InputWebFileLocation webLocation;
    private WebFile webFile;
    private volatile int state = stateIdle;
    private volatile boolean paused;
    private volatile int downloadedBytes;
    private int totalBytesCount;
    private int bytesCountPadding;
    private int streamStartOffset;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;
    private int currentDownloadChunkSize;
    private int currentMaxDownloadRequests;
    private int requestsCount;
    private int renameRetryCount;

    private boolean encryptFile;
    private boolean allowDisordererFileSave;

    private SparseArray<TLRPC.TL_fileHash> cdnHashes;

    private byte[] encryptKey;
    private byte[] encryptIv;

    private boolean isCdn;
    private byte[] cdnIv;
    private byte[] cdnKey;
    private byte[] cdnToken;
    private int cdnDatacenterId;
    private boolean reuploadingCdn;
    private RandomAccessFile fileReadStream;
    private byte[] cdnCheckBytes;
    private boolean requestingCdnOffsets;

    private ArrayList<RequestInfo> requestInfos;
    private ArrayList<RequestInfo> delayedRequestInfos;

    private File cacheFileTemp;
    private File cacheFileFinal;
    private File cacheIvTemp;
    private File cacheFileParts;

    private String ext;
    private RandomAccessFile fileOutputStream;
    private RandomAccessFile fiv;
    private RandomAccessFile filePartsStream;
    private File storePath;
    private File tempPath;
    private boolean isForceRequest;

    private int currentType;

    public interface FileLoadOperationDelegate {
        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int state);
        void didChangedLoadProgress(FileLoadOperation operation, float progress);
    }

    public FileLoadOperation(TLRPC.FileLocation photoLocation, String extension, int size) {
        if (photoLocation instanceof TLRPC.TL_fileEncryptedLocation) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = photoLocation.volume_id;
            location.volume_id = photoLocation.volume_id;
            location.access_hash = photoLocation.secret;
            location.local_id = photoLocation.local_id;
            iv = new byte[32];
            System.arraycopy(photoLocation.iv, 0, iv, 0, iv.length);
            key = photoLocation.key;
            initialDatacenterId = datacenterId = photoLocation.dc_id;
        } else if (photoLocation instanceof TLRPC.TL_fileLocation) {
            location = new TLRPC.TL_inputFileLocation();
            location.volume_id = photoLocation.volume_id;
            location.secret = photoLocation.secret;
            location.local_id = photoLocation.local_id;
            initialDatacenterId = datacenterId = photoLocation.dc_id;
            allowDisordererFileSave = true;
        }
        currentType = ConnectionsManager.FileTypePhoto;
        totalBytesCount = size;
        ext = extension != null ? extension : "jpg";
    }

    public FileLoadOperation(SecureDocument secureDocument) {
        location = new TLRPC.TL_inputDocumentFileLocation();
        location.id = secureDocument.secureFile.id;
        location.access_hash = secureDocument.secureFile.access_hash;
        datacenterId = secureDocument.secureFile.dc_id;
        totalBytesCount = secureDocument.secureFile.size;
        allowDisordererFileSave = true;
        currentType = ConnectionsManager.FileTypeFile;
        ext = ".jpg";
    }

    public FileLoadOperation(int instance, WebFile webDocument) {
        currentAccount = instance;
        webFile = webDocument;
        webLocation = webDocument.location;
        totalBytesCount = webDocument.size;
        initialDatacenterId = datacenterId = MessagesController.getInstance(currentAccount).webFileDatacenterId;
        String defaultExt = FileLoader.getExtensionByMime(webDocument.mime_type);
        if (webDocument.mime_type.startsWith("image/")) {
            currentType = ConnectionsManager.FileTypePhoto;
        } else if (webDocument.mime_type.equals("audio/ogg")) {
            currentType = ConnectionsManager.FileTypeAudio;
        } else if (webDocument.mime_type.startsWith("video/")) {
            currentType = ConnectionsManager.FileTypeVideo;
        } else {
            currentType = ConnectionsManager.FileTypeFile;
        }
        allowDisordererFileSave = true;
        ext = ImageLoader.getHttpUrlExtension(webDocument.url, defaultExt);
    }

    public FileLoadOperation(TLRPC.Document documentLocation) {
        try {
            if (documentLocation instanceof TLRPC.TL_documentEncrypted) {
                location = new TLRPC.TL_inputEncryptedFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                initialDatacenterId = datacenterId = documentLocation.dc_id;
                iv = new byte[32];
                System.arraycopy(documentLocation.iv, 0, iv, 0, iv.length);
                key = documentLocation.key;
            } else if (documentLocation instanceof TLRPC.TL_document) {
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                initialDatacenterId = datacenterId = documentLocation.dc_id;
                allowDisordererFileSave = true;
            }
            totalBytesCount = documentLocation.size;
            if (key != null) {
                int toAdd = 0;
                if (totalBytesCount % 16 != 0) {
                    bytesCountPadding = 16 - totalBytesCount % 16;
                    totalBytesCount += bytesCountPadding;
                }
            }
            ext = FileLoader.getDocumentFileName(documentLocation);
            int idx;
            if (ext == null || (idx = ext.lastIndexOf('.')) == -1) {
                ext = "";
            } else {
                ext = ext.substring(idx);
            }
            if ("audio/ogg".equals(documentLocation.mime_type)) {
                currentType = ConnectionsManager.FileTypeAudio;
            } else if ("video/mp4".equals(documentLocation.mime_type)) {
                currentType = ConnectionsManager.FileTypeVideo;
            } else {
                currentType = ConnectionsManager.FileTypeFile;
            }
            if (ext.length() <= 1) {
                if (documentLocation.mime_type != null) {
                    switch (documentLocation.mime_type) {
                        case "video/mp4":
                            ext = ".mp4";
                            break;
                        case "audio/ogg":
                            ext = ".ogg";
                            break;
                        default:
                            ext = "";
                            break;
                    }
                } else {
                    ext = "";
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            onFail(true, 0);
        }
    }

    public void setEncryptFile(boolean value) {
        encryptFile = value;
        if (encryptFile) {
            allowDisordererFileSave = false;
        }
    }

    public int getDatacenterId() {
        return initialDatacenterId;
    }

    public void setForceRequest(boolean forceRequest) {
        isForceRequest = forceRequest;
    }

    public boolean isForceRequest() {
        return isForceRequest;
    }

    public void setPaths(int instance, File store, File temp) {
        storePath = store;
        tempPath = temp;
        currentAccount = instance;
    }

    public boolean wasStarted() {
        return started;
    }

    public int getCurrentType() {
        return currentType;
    }

    private void removePart(ArrayList<Range> ranges, int start, int end) {
        if (ranges == null || end < start) {
            return;
        }
        int count = ranges.size();
        Range range;
        boolean modified = false;
        for (int a = 0; a < count; a++) {
            range = ranges.get(a);
            if (start == range.end) {
                range.end = end;
                modified = true;
                break;
            } else if (end == range.start) {
                range.start = start;
                modified = true;
                break;
            }
        }
        if (!modified) {
            ranges.add(new Range(start, end));
        }
    }

    private void addPart(ArrayList<Range> ranges, int start, int end, boolean save) {
        if (ranges == null || end < start) {
            return;
        }
        boolean modified = false;
        int count = ranges.size();
        Range range;
        for (int a = 0; a < count; a++) {
            range = ranges.get(a);
            if (start <= range.start) {
                if (end >= range.end) {
                    ranges.remove(a);
                    modified = true;
                    break;
                } else if (end > range.start) {
                    range.start = end;
                    modified = true;
                    break;
                }
            } else {
                if (end < range.end) {
                    Range newRange = new Range(range.start, start);
                    ranges.add(0, newRange);
                    modified = true;
                    range.start = end;
                    break;
                } else if (start < range.end) {
                    range.end = start;
                    modified = true;
                    break;
                }
            }
        }
        if (save) {
            if (modified) {
                try {
                    filePartsStream.seek(0);
                    count = ranges.size();
                    filePartsStream.writeInt(count);
                    for (int a = 0; a < count; a++) {
                        range = ranges.get(a);
                        /*if (BuildVars.LOGS_ENABLED) {
                            FileLog.d(cacheFileFinal + " save not loaded part " + range.start + " - " + range.end);
                        }*/
                        filePartsStream.writeInt(range.start);
                        filePartsStream.writeInt(range.end);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (streamListeners != null) {
                    count = streamListeners.size();
                    for (int a = 0; a < count; a++) {
                        streamListeners.get(a).newDataAvailable();
                    }
                }
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e(cacheFileFinal + " downloaded duplicate file part " + start + " - " + end);
                }
            }
        }
    }

    protected File getCurrentFile() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final File result[] = new File[1];
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (state == stateFinished) {
                    result[0] = cacheFileFinal;
                } else {
                    result[0] = cacheFileTemp;
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    private int getDownloadedLengthFromOffsetInternal(ArrayList<Range> ranges, final int offset, final int length) {
        if (ranges == null || state == stateFinished || ranges.isEmpty()) {
            if (downloadedBytes == 0) {
                return length;
            } else {
                return Math.min(length, Math.max(downloadedBytes - offset, 0));
            }
        } else {
            int count = ranges.size();
            Range range;
            Range minRange = null;
            int availableLength = length;
            for (int a = 0; a < count; a++) {
                range = ranges.get(a);
                if (offset <= range.start && (minRange == null || range.start < minRange.start)) {
                    minRange = range;
                }
                if (range.start <= offset && range.end > offset) {
                    availableLength = 0;
                }
            }
            if (availableLength == 0) {
                return 0;
            } else if (minRange != null) {
                return Math.min(length, minRange.start - offset);
            } else {
                return Math.min(length, Math.max(totalBytesCount - offset, 0));
            }
        }
    }

    protected float getDownloadedLengthFromOffset(final float progress) {
        ArrayList<Range> ranges = notLoadedBytesRangesCopy;
        if (totalBytesCount == 0 || ranges == null) {
            return 0;
        }
        return progress + getDownloadedLengthFromOffsetInternal(ranges, (int) (totalBytesCount * progress), totalBytesCount) / (float) totalBytesCount;
    }

    protected int getDownloadedLengthFromOffset(final int offset, final int length) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final int result[] = new int[1];
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                result[0] = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, offset, length);
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public String getFileName() {
        if (location != null) {
            return location.volume_id + "_" + location.local_id + "." + ext;
        } else {
            return Utilities.MD5(webFile.url) + "." + ext;
        }
    }

    protected void removeStreamListener(final FileStreamLoadOperation operation) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (streamListeners == null) {
                    return;
                }
                streamListeners.remove(operation);
            }
        });
    }

    private void copytNotLoadedRanges() {
        if (notLoadedBytesRanges == null) {
            return;
        }
        notLoadedBytesRangesCopy = new ArrayList<>(notLoadedBytesRanges);
    }

    public void pause() {
        if (state != stateDownloading) {
            return;
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                paused = true;
            }
        });
    }

    public boolean start() {
        return start(null, 0);
    }

    public boolean start(final FileStreamLoadOperation stream, final int streamOffset) {
        if (currentDownloadChunkSize == 0) {
            currentDownloadChunkSize = totalBytesCount >= bigFileSizeFrom ? downloadChunkSizeBig : downloadChunkSize;
            currentMaxDownloadRequests = totalBytesCount >= bigFileSizeFrom ? maxDownloadRequestsBig : maxDownloadRequests;
        }
        final boolean alreadyStarted = state != stateIdle;
        final boolean wasPaused = paused;
        paused = false;
        if (stream != null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (streamListeners == null) {
                        streamListeners = new ArrayList<>();
                    }
                    streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;
                    streamListeners.add(stream);
                    if (alreadyStarted) {
                        //clearOperaion(null);
                        startDownloadRequest();
                    }
                }
            });
        } else if (wasPaused && alreadyStarted) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    startDownloadRequest();
                }
            });
        }
        if (alreadyStarted) {
            return wasPaused;
        }
        if (location == null && webLocation == null) {
            onFail(true, 0);
            return false;
        }

        streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;

        if (allowDisordererFileSave && totalBytesCount > 0 && totalBytesCount > currentDownloadChunkSize) {
            notLoadedBytesRanges = new ArrayList<>();
            notRequestedBytesRanges = new ArrayList<>();
        }

        String fileNameFinal;
        String fileNameTemp;
        String fileNameParts = null;
        String fileNameIv = null;
        if (webLocation != null) {
            String md5 = Utilities.MD5(webFile.url);
            if (encryptFile) {
                fileNameTemp = md5 + ".temp.enc";
                fileNameFinal = md5 + "." + ext + ".enc";
                if (key != null) {
                    fileNameIv = md5 + ".iv.enc";
                }
            } else {
                fileNameTemp = md5 + ".temp";
                fileNameFinal = md5 + "." + ext;
                if (key != null) {
                    fileNameIv = md5 + ".iv";
                }
            }
        } else {
            if (location.volume_id != 0 && location.local_id != 0) {
                if (datacenterId == Integer.MIN_VALUE || location.volume_id == Integer.MIN_VALUE || datacenterId == 0) {
                    onFail(true, 0);
                    return false;
                }

                if (encryptFile) {
                    fileNameTemp = location.volume_id + "_" + location.local_id + ".temp.enc";
                    fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext + ".enc";
                    if (key != null) {
                        fileNameIv = location.volume_id + "_" + location.local_id + ".iv.enc";
                    }
                } else {
                    fileNameTemp = location.volume_id + "_" + location.local_id + ".temp";
                    fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext;
                    if (key != null) {
                        fileNameIv = location.volume_id + "_" + location.local_id + ".iv";
                    }
                    if (notLoadedBytesRanges != null) {
                        fileNameParts = location.volume_id + "_" + location.local_id + ".pt";
                    }
                }
            } else {
                if (datacenterId == 0 || location.id == 0) {
                    onFail(true, 0);
                    return false;
                }
                if (encryptFile) {
                    fileNameTemp = datacenterId + "_" + location.id + ".temp.enc";
                    fileNameFinal = datacenterId + "_" + location.id + ext + ".enc";
                    if (key != null) {
                        fileNameIv = datacenterId + "_" + location.id + ".iv.enc";
                    }
                } else {
                    fileNameTemp = datacenterId + "_" + location.id + ".temp";
                    fileNameFinal = datacenterId + "_" + location.id + ext;
                    if (key != null) {
                        fileNameIv = datacenterId + "_" + location.id + ".iv";
                    }
                    if (notLoadedBytesRanges != null) {
                        fileNameParts = datacenterId + "_" + location.id + ".pt";
                    }
                }
            }
        }

        requestInfos = new ArrayList<>(currentMaxDownloadRequests);
        delayedRequestInfos = new ArrayList<>(currentMaxDownloadRequests - 1);
        state = stateDownloading;

        cacheFileFinal = new File(storePath, fileNameFinal);
        boolean finalFileExist = cacheFileFinal.exists();
        if (finalFileExist && totalBytesCount != 0 && totalBytesCount != cacheFileFinal.length()) {
            cacheFileFinal.delete();
            finalFileExist = false;
        }

        if (!finalFileExist) {
            cacheFileTemp = new File(tempPath, fileNameTemp);
            boolean newKeyGenerated = false;

            if (encryptFile) {
                File keyFile = new File(FileLoader.getInternalCacheDir(), fileNameFinal + ".key");
                try {
                    RandomAccessFile file = new RandomAccessFile(keyFile, "rws");
                    long len = keyFile.length();
                    encryptKey = new byte[32];
                    encryptIv = new byte[16];
                    if (len > 0 && len % 48 == 0) {
                        file.read(encryptKey, 0, 32);
                        file.read(encryptIv, 0, 16);
                    } else {
                        Utilities.random.nextBytes(encryptKey);
                        Utilities.random.nextBytes(encryptIv);
                        file.write(encryptKey);
                        file.write(encryptIv);
                        newKeyGenerated = true;
                    }
                    try {
                        file.getChannel().close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    file.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (fileNameParts != null) {
                cacheFileParts = new File(tempPath, fileNameParts);
                try {
                    filePartsStream = new RandomAccessFile(cacheFileParts, "rws");
                    long len = filePartsStream.length();
                    if (len % 8 == 4) {
                        len -= 4;
                        int count = filePartsStream.readInt();
                        if (count <= len / 2) {
                            for (int a = 0; a < count; a++) {
                                int start = filePartsStream.readInt();
                                int end = filePartsStream.readInt();
                                notLoadedBytesRanges.add(new Range(start, end));
                                notRequestedBytesRanges.add(new Range(start, end));
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (cacheFileTemp.exists()) {
                if (newKeyGenerated) {
                    cacheFileTemp.delete();
                } else {
                    long totalDownloadedLen = cacheFileTemp.length();
                    if (fileNameIv != null && (totalDownloadedLen % currentDownloadChunkSize) != 0) {
                        requestedBytesCount = downloadedBytes = 0;
                    } else {
                        requestedBytesCount = downloadedBytes = ((int) cacheFileTemp.length()) / currentDownloadChunkSize * currentDownloadChunkSize;
                    }
                    if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
                        notLoadedBytesRanges.add(new Range(downloadedBytes, totalBytesCount));
                        notRequestedBytesRanges.add(new Range(downloadedBytes, totalBytesCount));
                    }
                }
            } else if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
                notLoadedBytesRanges.add(new Range(0, totalBytesCount));
                notRequestedBytesRanges.add(new Range(0, totalBytesCount));
            }
            if (notLoadedBytesRanges != null) {
                downloadedBytes = totalBytesCount;
                int size = notLoadedBytesRanges.size();
                Range range;
                for (int a = 0; a < size; a++) {
                    range = notLoadedBytesRanges.get(a);
                    downloadedBytes -= (range.end - range.start);
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("start loading file to temp = " + cacheFileTemp + " final = " + cacheFileFinal);
            }

            if (fileNameIv != null) {
                cacheIvTemp = new File(tempPath, fileNameIv);
                try {
                    fiv = new RandomAccessFile(cacheIvTemp, "rws");
                    if (downloadedBytes != 0 && !newKeyGenerated) {
                        long len = cacheIvTemp.length();
                        if (len > 0 && len % 32 == 0) {
                            fiv.read(iv, 0, 32);
                        } else {
                            requestedBytesCount = downloadedBytes = 0;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    requestedBytesCount = downloadedBytes = 0;
                }
            }
            if (downloadedBytes != 0 && totalBytesCount > 0) {
                copytNotLoadedRanges();
                delegate.didChangedLoadProgress(FileLoadOperation.this, Math.min(1.0f, (float) downloadedBytes / (float) totalBytesCount));
            }
            try {
                fileOutputStream = new RandomAccessFile(cacheFileTemp, "rws");
                if (downloadedBytes != 0) {
                    fileOutputStream.seek(downloadedBytes);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (fileOutputStream == null) {
                onFail(true, 0);
                return false;
            }
            started = true;
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (totalBytesCount != 0 && downloadedBytes == totalBytesCount) {
                        try {
                            onFinishLoadingFile(false);
                        } catch (Exception e) {
                            onFail(true, 0);
                        }
                    } else {
                        startDownloadRequest();
                    }
                }
            });
        } else {
            started = true;
            try {
                onFinishLoadingFile(false);
            } catch (Exception e) {
                onFail(true, 0);
            }
        }
        return true;
    }

    public boolean isPaused() {
        return paused;
    }

    public void cancel() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (state == stateFinished || state == stateFailed) {
                    return;
                }
                if (requestInfos != null) {
                    for (int a = 0; a < requestInfos.size(); a++) {
                        RequestInfo requestInfo = requestInfos.get(a);
                        if (requestInfo.requestToken != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestInfo.requestToken, true);
                        }
                    }
                }
                onFail(false, 1);
            }
        });
    }

    private void cleanup() {
        try {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                fileOutputStream.close();
                fileOutputStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (fileReadStream != null) {
                try {
                    fileReadStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                fileReadStream.close();
                fileReadStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (filePartsStream != null) {
                try {
                    filePartsStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                filePartsStream.close();
                filePartsStream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            if (fiv != null) {
                fiv.close();
                fiv = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (delayedRequestInfos != null) {
            for (int a = 0; a < delayedRequestInfos.size(); a++) {
                RequestInfo requestInfo = delayedRequestInfos.get(a);
                if (requestInfo.response != null) {
                    requestInfo.response.disableFree = false;
                    requestInfo.response.freeResources();
                } else if (requestInfo.responseWeb != null) {
                    requestInfo.responseWeb.disableFree = false;
                    requestInfo.responseWeb.freeResources();
                } else if (requestInfo.responseCdn != null) {
                    requestInfo.responseCdn.disableFree = false;
                    requestInfo.responseCdn.freeResources();
                }
            }
            delayedRequestInfos.clear();
        }
    }

    private void onFinishLoadingFile(final boolean increment) {
        if (state != stateDownloading) {
            return;
        }
        state = stateFinished;
        cleanup();
        if (cacheIvTemp != null) {
            cacheIvTemp.delete();
            cacheIvTemp = null;
        }
        if (cacheFileParts != null) {
            cacheFileParts.delete();
            cacheFileParts = null;
        }
        if (cacheFileTemp != null) {
            boolean renameResult = cacheFileTemp.renameTo(cacheFileFinal);
            if (!renameResult) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("unable to rename temp = " + cacheFileTemp + " to final = " + cacheFileFinal + " retry = " + renameRetryCount);
                }
                renameRetryCount++;
                if (renameRetryCount < 3) {
                    state = stateDownloading;
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onFinishLoadingFile(increment);
                            } catch (Exception e) {
                                onFail(false, 0);
                            }
                        }
                    }, 200);
                    return;
                }
                cacheFileFinal = cacheFileTemp;
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("finished downloading file to " + cacheFileFinal);
        }
        delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
        if (increment) {
            if (currentType == ConnectionsManager.FileTypeAudio) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeVideo) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
            } else if (currentType == ConnectionsManager.FileTypePhoto) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeFile) {
                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
            }
        }
    }

    private void delayRequestInfo(RequestInfo requestInfo) {
        delayedRequestInfos.add(requestInfo);
        if (requestInfo.response != null) {
            requestInfo.response.disableFree = true;
        } else if (requestInfo.responseWeb != null) {
            requestInfo.responseWeb.disableFree = true;
        } else if (requestInfo.responseCdn != null) {
            requestInfo.responseCdn.disableFree = true;
        }
    }

    private void requestFileOffsets(int offset) {
        if (requestingCdnOffsets) {
            return;
        }
        requestingCdnOffsets = true;
        TLRPC.TL_upload_getCdnFileHashes req = new TLRPC.TL_upload_getCdnFileHashes();
        req.file_token = cdnToken;
        req.offset = offset;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    onFail(false, 0);
                } else {
                    requestingCdnOffsets = false;
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    if (!vector.objects.isEmpty()) {
                        if (cdnHashes == null) {
                            cdnHashes = new SparseArray<>();
                        }
                        for (int a = 0; a < vector.objects.size(); a++) {
                            TLRPC.TL_fileHash hash = (TLRPC.TL_fileHash) vector.objects.get(a);
                            cdnHashes.put(hash.offset, hash);
                        }
                    }
                    for (int a = 0; a < delayedRequestInfos.size(); a++) {
                        RequestInfo delayedRequestInfo = delayedRequestInfos.get(a);
                        if (notLoadedBytesRanges != null || downloadedBytes == delayedRequestInfo.offset) {
                            delayedRequestInfos.remove(a);
                            if (!processRequestResult(delayedRequestInfo, null)) {
                                if (delayedRequestInfo.response != null) {
                                    delayedRequestInfo.response.disableFree = false;
                                    delayedRequestInfo.response.freeResources();
                                } else if (delayedRequestInfo.responseWeb != null) {
                                    delayedRequestInfo.responseWeb.disableFree = false;
                                    delayedRequestInfo.responseWeb.freeResources();
                                } else if (delayedRequestInfo.responseCdn != null) {
                                    delayedRequestInfo.responseCdn.disableFree = false;
                                    delayedRequestInfo.responseCdn.freeResources();
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }, null, null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
    }

    private boolean processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error) {
        if (state != stateDownloading) {
            return false;
        }
        requestInfos.remove(requestInfo);
        if (error == null) {
            try {
                if (notLoadedBytesRanges == null && downloadedBytes != requestInfo.offset) {
                    delayRequestInfo(requestInfo);
                    return false;
                }
                NativeByteBuffer bytes;
                if (requestInfo.response != null) {
                    bytes = requestInfo.response.bytes;
                } else if (requestInfo.responseWeb != null) {
                    bytes = requestInfo.responseWeb.bytes;
                } else if (requestInfo.responseCdn != null) {
                    bytes = requestInfo.responseCdn.bytes;
                } else {
                    bytes = null;
                }
                if (bytes == null || bytes.limit() == 0) {
                    onFinishLoadingFile(true);
                    return false;
                }
                int currentBytesSize = bytes.limit();
                if (isCdn) {
                    int cdnCheckPart = requestInfo.offset / cdnChunkCheckSize;
                    int fileOffset = cdnCheckPart * cdnChunkCheckSize;
                    TLRPC.TL_fileHash hash = cdnHashes != null ? cdnHashes.get(fileOffset) : null;
                    if (hash == null) {
                        delayRequestInfo(requestInfo);
                        requestFileOffsets(fileOffset);
                        return true;
                    }
                }

                if (requestInfo.responseCdn != null) {
                    int offset = requestInfo.offset / 16;
                    cdnIv[15] = (byte) (offset & 0xff);
                    cdnIv[14] = (byte) ((offset >> 8) & 0xff);
                    cdnIv[13] = (byte) ((offset >> 16) & 0xff);
                    cdnIv[12] = (byte) ((offset >> 24) & 0xff);
                    Utilities.aesCtrDecryption(bytes.buffer, cdnKey, cdnIv, 0, bytes.limit());
                }

                downloadedBytes += currentBytesSize;
                boolean finishedDownloading;
                if (totalBytesCount > 0) {
                    finishedDownloading = downloadedBytes >= totalBytesCount;
                } else {
                    finishedDownloading = currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes);
                }
                if (key != null) {
                    Utilities.aesIgeEncryption(bytes.buffer, key, iv, false, true, 0, bytes.limit());
                    if (finishedDownloading && bytesCountPadding != 0) {
                        bytes.limit(bytes.limit() - bytesCountPadding);
                    }
                }
                if (encryptFile) {
                    int offset = requestInfo.offset / 16;
                    encryptIv[15] = (byte) (offset & 0xff);
                    encryptIv[14] = (byte) ((offset >> 8) & 0xff);
                    encryptIv[13] = (byte) ((offset >> 16) & 0xff);
                    encryptIv[12] = (byte) ((offset >> 24) & 0xff);
                    Utilities.aesCtrDecryption(bytes.buffer, encryptKey, encryptIv, 0, bytes.limit());
                }
                if (notLoadedBytesRanges != null) {
                    fileOutputStream.seek(requestInfo.offset);
                }
                FileChannel channel = fileOutputStream.getChannel();
                channel.write(bytes.buffer);
                addPart(notLoadedBytesRanges, requestInfo.offset, requestInfo.offset + currentBytesSize, true);
                if (isCdn) {
                    int cdnCheckPart = requestInfo.offset / cdnChunkCheckSize;

                    int size = notCheckedCdnRanges.size();
                    Range range;
                    boolean checked = true;
                    for (int a = 0; a < size; a++) {
                        range = notCheckedCdnRanges.get(a);
                        if (range.start <= cdnCheckPart && cdnCheckPart <= range.end) {
                            checked = false;
                            break;
                        }
                    }
                    if (!checked) {
                        int fileOffset = cdnCheckPart * cdnChunkCheckSize;
                        int availableSize = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, fileOffset, cdnChunkCheckSize);
                        if (availableSize != 0 && (availableSize == cdnChunkCheckSize || totalBytesCount > 0 && availableSize == totalBytesCount - fileOffset || totalBytesCount <= 0 && finishedDownloading)) {
                            TLRPC.TL_fileHash hash = cdnHashes.get(fileOffset);
                            if (fileReadStream == null) {
                                cdnCheckBytes = new byte[cdnChunkCheckSize];
                                fileReadStream = new RandomAccessFile(cacheFileTemp, "r");
                            }
                            fileReadStream.seek(fileOffset);
                            fileReadStream.readFully(cdnCheckBytes, 0, availableSize);
                            byte[] sha256 = Utilities.computeSHA256(cdnCheckBytes, 0, availableSize);
                            if (!Arrays.equals(sha256, hash.hash)) {
                                if (BuildVars.LOGS_ENABLED) {
                                    if (location != null) {
                                        FileLog.e("invalid cdn hash " + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                                    } else if (webLocation != null) {
                                        FileLog.e("invalid cdn hash  " + webLocation + " id = " + getFileName());
                                    }
                                }
                                onFail(false, 0);
                                cacheFileTemp.delete();
                                return false;
                            }
                            cdnHashes.remove(fileOffset);
                            addPart(notCheckedCdnRanges, cdnCheckPart, cdnCheckPart + 1, false);
                        }
                    }
                }
                if (fiv != null) {
                    fiv.seek(0);
                    fiv.write(iv);
                }
                if (totalBytesCount > 0 && state == stateDownloading) {
                    copytNotLoadedRanges();
                    delegate.didChangedLoadProgress(FileLoadOperation.this, Math.min(1.0f, (float) downloadedBytes / (float) totalBytesCount));
                }

                for (int a = 0; a < delayedRequestInfos.size(); a++) {
                    RequestInfo delayedRequestInfo = delayedRequestInfos.get(a);
                    if (notLoadedBytesRanges != null || downloadedBytes == delayedRequestInfo.offset) {
                        delayedRequestInfos.remove(a);
                        if (!processRequestResult(delayedRequestInfo, null)) {
                            if (delayedRequestInfo.response != null) {
                                delayedRequestInfo.response.disableFree = false;
                                delayedRequestInfo.response.freeResources();
                            } else if (delayedRequestInfo.responseWeb != null) {
                                delayedRequestInfo.responseWeb.disableFree = false;
                                delayedRequestInfo.responseWeb.freeResources();
                            } else if (delayedRequestInfo.responseCdn != null) {
                                delayedRequestInfo.responseCdn.disableFree = false;
                                delayedRequestInfo.responseCdn.freeResources();
                            }
                        }
                        break;
                    }
                }

                if (finishedDownloading) {
                    onFinishLoadingFile(true);
                } else {
                    startDownloadRequest();
                }
            } catch (Exception e) {
                onFail(false, 0);
                FileLog.e(e);
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
                    onFail(false, 0);
                } else {
                    datacenterId = val;
                    requestedBytesCount = downloadedBytes = 0;
                    startDownloadRequest();
                }
            } else if (error.text.contains("OFFSET_INVALID")) {
                if (downloadedBytes % currentDownloadChunkSize == 0) {
                    try {
                        onFinishLoadingFile(true);
                    } catch (Exception e) {
                        FileLog.e(e);
                        onFail(false, 0);
                    }
                } else {
                    onFail(false, 0);
                }
            } else if (error.text.contains("RETRY_LIMIT")) {
                onFail(false, 2);
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    if (location != null) {
                        FileLog.e("" + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                    } else if (webLocation != null) {
                        FileLog.e("" + webLocation + " id = " + getFileName());
                    }
                }
                onFail(false, 0);
            }
        }
        return false;
    }

    private void onFail(boolean thread, final int reason) {
        cleanup();
        state = stateFailed;
        if (thread) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.didFailedLoadingFile(FileLoadOperation.this, reason);
                }
            });
        } else {
            delegate.didFailedLoadingFile(FileLoadOperation.this, reason);
        }
    }

    private void clearOperaion(RequestInfo currentInfo) {
        int minOffset = Integer.MAX_VALUE;
        for (int a = 0; a < requestInfos.size(); a++) {
            RequestInfo info = requestInfos.get(a);
            minOffset = Math.min(info.offset, minOffset);
            removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            if (currentInfo == info) {
                continue;
            }
            if (info.requestToken != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(info.requestToken, true);
            }
        }
        requestInfos.clear();
        for (int a = 0; a < delayedRequestInfos.size(); a++) {
            RequestInfo info = delayedRequestInfos.get(a);
            removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            if (info.response != null) {
                info.response.disableFree = false;
                info.response.freeResources();
            } else if (info.responseWeb != null) {
                info.responseWeb.disableFree = false;
                info.responseWeb.freeResources();
            } else if (info.responseCdn != null) {
                info.responseCdn.disableFree = false;
                info.responseCdn.freeResources();
            }
            minOffset = Math.min(info.offset, minOffset);
        }
        delayedRequestInfos.clear();
        requestsCount = 0;
        if (notLoadedBytesRanges == null) {
            requestedBytesCount = downloadedBytes = minOffset;
        }
    }

    private void startDownloadRequest() {
        if (paused || state != stateDownloading || requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests) {
            return;
        }
        int count = 1;
        if (totalBytesCount > 0) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
        }

        for (int a = 0; a < count; a++) {
            int downloadOffset;
            if (notRequestedBytesRanges != null) {
                int size = notRequestedBytesRanges.size();
                int minStart = Integer.MAX_VALUE;
                int minStreamStart = Integer.MAX_VALUE;
                for (int b = 0; b < size; b++) {
                    Range range = notRequestedBytesRanges.get(b);
                    if (streamStartOffset != 0) {
                        if (range.start <= streamStartOffset && range.end > streamStartOffset) {
                            minStreamStart = streamStartOffset;
                            minStart = Integer.MAX_VALUE;
                            break;
                        }
                        if (streamStartOffset < range.start && range.start < minStreamStart) {
                            minStreamStart = range.start;
                        }
                    }
                    minStart = Math.min(minStart, range.start);
                }
                if (minStreamStart != Integer.MAX_VALUE) {
                    downloadOffset = minStreamStart;
                } else if (minStart != Integer.MAX_VALUE) {
                    downloadOffset = minStart;
                } else {
                    break;
                }
            } else {
                downloadOffset = requestedBytesCount;
            }
            if (notRequestedBytesRanges != null) {
                addPart(notRequestedBytesRanges, downloadOffset, downloadOffset + currentDownloadChunkSize, false);
            }

            if (totalBytesCount > 0 && downloadOffset >= totalBytesCount) {
                break;
            }
            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && downloadOffset + currentDownloadChunkSize >= totalBytesCount;
            final TLObject request;
            int connectionType = requestsCount % 2 == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
            int flags = (isForceRequest ? ConnectionsManager.RequestFlagForceDownload : 0);
            if (!(webLocation instanceof TLRPC.TL_inputWebFileGeoPointLocation)) {
                flags |= ConnectionsManager.RequestFlagFailOnServerErrors;
            }
            if (isCdn) {
                TLRPC.TL_upload_getCdnFile req = new TLRPC.TL_upload_getCdnFile();
                req.file_token = cdnToken;
                req.offset = downloadOffset;
                req.limit = currentDownloadChunkSize;
                request = req;
                flags |= ConnectionsManager.RequestFlagEnableUnauthorized;
            } else {
                if (webLocation != null) {
                    TLRPC.TL_upload_getWebFile req = new TLRPC.TL_upload_getWebFile();
                    req.location = webLocation;
                    req.offset = downloadOffset;
                    req.limit = currentDownloadChunkSize;
                    request = req;
                } else {
                    TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
                    req.location = location;
                    req.offset = downloadOffset;
                    req.limit = currentDownloadChunkSize;
                    request = req;
                }
            }
            requestedBytesCount += currentDownloadChunkSize;
            final RequestInfo requestInfo = new RequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = downloadOffset;
            requestInfo.requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(request, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (!requestInfos.contains(requestInfo)) {
                        return;
                    }
                    if (error != null) {
                        if (request instanceof TLRPC.TL_upload_getCdnFile) {
                            if (error.text.equals("FILE_TOKEN_INVALID")) {
                                isCdn = false;
                                clearOperaion(requestInfo);
                                startDownloadRequest();
                                return;
                            }
                        }
                    }
                    if (response instanceof TLRPC.TL_upload_fileCdnRedirect) {
                        TLRPC.TL_upload_fileCdnRedirect res = (TLRPC.TL_upload_fileCdnRedirect) response;
                        if (!res.file_hashes.isEmpty()) {
                            if (cdnHashes == null) {
                                cdnHashes = new SparseArray<>();
                            }
                            for (int a = 0; a < res.file_hashes.size(); a++) {
                                TLRPC.TL_fileHash hash = res.file_hashes.get(a);
                                cdnHashes.put(hash.offset, hash);
                            }
                        }
                        if (res.encryption_iv == null || res.encryption_key == null || res.encryption_iv.length != 16 || res.encryption_key.length != 32) {
                            error = new TLRPC.TL_error();
                            error.text = "bad redirect response";
                            error.code = 400;
                            processRequestResult(requestInfo, error);
                        } else {
                            isCdn = true;
                            if (notCheckedCdnRanges == null) {
                                notCheckedCdnRanges = new ArrayList<>();
                                notCheckedCdnRanges.add(new Range(0, maxCdnParts));
                            }
                            cdnDatacenterId = res.dc_id;
                            cdnIv = res.encryption_iv;
                            cdnKey = res.encryption_key;
                            cdnToken = res.file_token;
                            clearOperaion(requestInfo);
                            startDownloadRequest();
                        }
                    } else if (response instanceof TLRPC.TL_upload_cdnFileReuploadNeeded) {
                        if (!reuploadingCdn) {
                            clearOperaion(requestInfo);
                            reuploadingCdn = true;
                            TLRPC.TL_upload_cdnFileReuploadNeeded res = (TLRPC.TL_upload_cdnFileReuploadNeeded) response;
                            TLRPC.TL_upload_reuploadCdnFile req = new TLRPC.TL_upload_reuploadCdnFile();
                            req.file_token = cdnToken;
                            req.request_token = res.request_token;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    reuploadingCdn = false;
                                    if (error == null) {
                                        TLRPC.Vector vector = (TLRPC.Vector) response;
                                        if (!vector.objects.isEmpty()) {
                                            if (cdnHashes == null) {
                                                cdnHashes = new SparseArray<>();
                                            }
                                            for (int a = 0; a < vector.objects.size(); a++) {
                                                TLRPC.TL_fileHash hash = (TLRPC.TL_fileHash) vector.objects.get(a);
                                                cdnHashes.put(hash.offset, hash);
                                            }
                                        }
                                        startDownloadRequest();
                                    } else {
                                        if (error.text.equals("FILE_TOKEN_INVALID") || error.text.equals("REQUEST_TOKEN_INVALID")) {
                                            isCdn = false;
                                            clearOperaion(requestInfo);
                                            startDownloadRequest();
                                        } else {
                                            onFail(false, 0);
                                        }
                                    }
                                }
                            }, null, null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
                        }
                    } else {
                        if (response instanceof TLRPC.TL_upload_file) {
                            requestInfo.response = (TLRPC.TL_upload_file) response;
                        } else if (response instanceof TLRPC.TL_upload_webFile) {
                            requestInfo.responseWeb = (TLRPC.TL_upload_webFile) response;
                            if (totalBytesCount == 0 && requestInfo.responseWeb.size != 0) {
                                totalBytesCount = requestInfo.responseWeb.size;
                            }
                        } else {
                            requestInfo.responseCdn = (TLRPC.TL_upload_cdnFile) response;
                        }
                        if (response != null) {
                            if (currentType == ConnectionsManager.FileTypeAudio) {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_AUDIOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypeVideo) {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_VIDEOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypePhoto) {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_PHOTOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypeFile) {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_FILES, response.getObjectSize() + 4);
                            }
                        }
                        processRequestResult(requestInfo, error);
                    }
                }
            }, null, null, flags, isCdn ? cdnDatacenterId : datacenterId, connectionType, isLast);
            requestsCount++;
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}
