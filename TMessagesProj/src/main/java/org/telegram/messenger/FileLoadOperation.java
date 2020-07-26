/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.util.SparseArray;
import android.util.SparseIntArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.io.File;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

public class FileLoadOperation {

    protected static class RequestInfo {
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

    private static class PreloadRange {
        private int fileOffset;
        private int length;

        private PreloadRange(int o, int l) {
            fileOffset = o;
            length = l;
        }
    }

    private ArrayList<FileLoadOperationStream> streamListeners;

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
    private final static int maxCdnParts = (int) (FileLoader.MAX_FILE_SIZE / downloadChunkSizeBig);

    private final static int preloadMaxBytes = 2 * 1024 * 1024;

    private SparseArray<PreloadRange> preloadedBytesRanges;
    private SparseIntArray requestedPreloadedBytesRanges;
    private RandomAccessFile preloadStream;
    private int preloadStreamFileOffset;
    private int totalPreloadedBytes;
    private boolean isPreloadVideoOperation;
    private boolean preloadFinished;
    private File cacheFilePreload;
    private boolean supportsPreloading;
    private int nextPreloadDownloadOffset;
    private int nextAtomOffset;
    private int foundMoovSize;
    private int preloadNotRequestedBytesCount;
    private int moovFound;
    private byte[] preloadTempBuffer = new byte[16];
    private int preloadTempBufferCount;

    private boolean nextPartWasPreloaded;

    private ArrayList<Range> notLoadedBytesRanges;
    private volatile ArrayList<Range> notLoadedBytesRangesCopy;
    private ArrayList<Range> notRequestedBytesRanges;
    private ArrayList<Range> notCheckedCdnRanges;
    private int requestedBytesCount;

    private int currentAccount;
    private boolean started;
    private int datacenterId;
    private int initialDatacenterId;
    protected TLRPC.InputFileLocation location;
    private TLRPC.InputWebFileLocation webLocation;
    private WebFile webFile;
    private volatile int state = stateIdle;
    private volatile boolean paused;
    private int downloadedBytes;
    private int totalBytesCount;
    private int bytesCountPadding;
    private int streamStartOffset;
    private int streamPriorityStartOffset;
    private RequestInfo priorityRequestInfo;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;
    private int currentDownloadChunkSize;
    private int currentMaxDownloadRequests;
    private int requestsCount;
    private int renameRetryCount;

    private boolean encryptFile;
    private boolean allowDisordererFileSave;

    private Object parentObject;

    private SparseArray<TLRPC.TL_fileHash> cdnHashes;

    private byte[] encryptKey;
    private byte[] encryptIv;

    private boolean isCdn;
    private byte[] cdnIv;
    private byte[] cdnKey;
    private byte[] cdnToken;
    private int cdnDatacenterId;
    private boolean reuploadingCdn;
    protected boolean requestingReference;
    private RandomAccessFile fileReadStream;
    private byte[] cdnCheckBytes;
    private boolean requestingCdnOffsets;

    private ArrayList<RequestInfo> requestInfos;
    private ArrayList<RequestInfo> delayedRequestInfos;

    private File cacheFileTemp;
    private File cacheFileGzipTemp;
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
    private int priority;

    private boolean ungzip;

    private int currentType;

    public interface FileLoadOperationDelegate {
        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int state);
        void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize);
    }

    public FileLoadOperation(ImageLocation imageLocation, Object parent, String extension, int size) {
        parentObject = parent;
        if (imageLocation.isEncrypted()) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = imageLocation.location.volume_id;
            location.volume_id = imageLocation.location.volume_id;
            location.local_id = imageLocation.location.local_id;
            location.access_hash = imageLocation.access_hash;
            iv = new byte[32];
            System.arraycopy(imageLocation.iv, 0, iv, 0, iv.length);
            key = imageLocation.key;
        } else if (imageLocation.photoPeer != null) {
            location = new TLRPC.TL_inputPeerPhotoFileLocation();
            location.id = imageLocation.location.volume_id;
            location.volume_id = imageLocation.location.volume_id;
            location.local_id = imageLocation.location.local_id;
            location.big = imageLocation.photoPeerBig;
            location.peer = imageLocation.photoPeer;
        } else if (imageLocation.stickerSet != null) {
            location = new TLRPC.TL_inputStickerSetThumb();
            location.id = imageLocation.location.volume_id;
            location.volume_id = imageLocation.location.volume_id;
            location.local_id = imageLocation.location.local_id;
            location.stickerset = imageLocation.stickerSet;
        } else if (imageLocation.thumbSize != null) {
            if (imageLocation.photoId != 0) {
                location = new TLRPC.TL_inputPhotoFileLocation();
                location.id = imageLocation.photoId;
                location.volume_id = imageLocation.location.volume_id;
                location.local_id = imageLocation.location.local_id;
                location.access_hash = imageLocation.access_hash;
                location.file_reference = imageLocation.file_reference;
                location.thumb_size = imageLocation.thumbSize;
                if (imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
                    allowDisordererFileSave = true;
                }
            } else {
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = imageLocation.documentId;
                location.volume_id = imageLocation.location.volume_id;
                location.local_id = imageLocation.location.local_id;
                location.access_hash = imageLocation.access_hash;
                location.file_reference = imageLocation.file_reference;
                location.thumb_size = imageLocation.thumbSize;
            }
            if (location.file_reference == null) {
                location.file_reference = new byte[0];
            }
        } else {
            location = new TLRPC.TL_inputFileLocation();
            location.volume_id = imageLocation.location.volume_id;
            location.local_id = imageLocation.location.local_id;
            location.secret = imageLocation.access_hash;
            location.file_reference = imageLocation.file_reference;
            if (location.file_reference == null) {
                location.file_reference = new byte[0];
            }
            allowDisordererFileSave = true;
        }
        ungzip = imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE || imageLocation.imageType == FileLoader.IMAGE_TYPE_SVG;
        initialDatacenterId = datacenterId = imageLocation.dc_id;
        currentType = ConnectionsManager.FileTypePhoto;
        totalBytesCount = size;
        ext = extension != null ? extension : "jpg";
    }

    public FileLoadOperation(SecureDocument secureDocument) {
        location = new TLRPC.TL_inputSecureFileLocation();
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
        String defaultExt = FileLoader.getMimeTypePart(webDocument.mime_type);
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

    public FileLoadOperation(TLRPC.Document documentLocation, Object parent) {
        try {
            parentObject = parent;
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
                location.file_reference = documentLocation.file_reference;
                location.thumb_size = "";
                if (location.file_reference == null) {
                    location.file_reference = new byte[0];
                }
                initialDatacenterId = datacenterId = documentLocation.dc_id;
                allowDisordererFileSave = true;
                for (int a = 0, N = documentLocation.attributes.size(); a < N; a++) {
                    if (documentLocation.attributes.get(a) instanceof TLRPC.TL_documentAttributeVideo) {
                        supportsPreloading = true;
                        break;
                    }
                }
            }
            ungzip = "application/x-tgsticker".equals(documentLocation.mime_type) || "application/x-tgwallpattern".equals(documentLocation.mime_type);
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
            } else if (FileLoader.isVideoMimeType(documentLocation.mime_type)) {
                currentType = ConnectionsManager.FileTypeVideo;
            } else {
                currentType = ConnectionsManager.FileTypeFile;
            }
            if (ext.length() <= 1) {
                ext = FileLoader.getExtensionByMimeType(documentLocation.mime_type);
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

    public void setPriority(int value) {
        priority = value;
    }

    public int getPriority() {
        return priority;
    }

    public void setPaths(int instance, File store, File temp) {
        storePath = store;
        tempPath = temp;
        currentAccount = instance;
    }

    public boolean wasStarted() {
        return started && !paused;
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
        Collections.sort(ranges, (o1, o2) -> {
            if (o1.start > o2.start) {
                return 1;
            } else if (o1.start < o2.start) {
                return -1;
            }
            return 0;
        });
        for (int a = 0; a < ranges.size() - 1; a++) {
            Range r1 = ranges.get(a);
            Range r2 = ranges.get(a + 1);
            if (r1.end == r2.start) {
                r1.end = r2.end;
                ranges.remove(a + 1);
                a--;
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

    protected File getCacheFileFinal() {
        return cacheFileFinal;
    }

    protected File getCurrentFile() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final File[] result = new File[1];
        Utilities.stageQueue.postRunnable(() -> {
            if (state == stateFinished) {
                result[0] = cacheFileFinal;
            } else {
                result[0] = cacheFileTemp;
            }
            countDownLatch.countDown();
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
                    break;
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

    protected int[] getDownloadedLengthFromOffset(final int offset, final int length) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final int[] result = new int[2];
        Utilities.stageQueue.postRunnable(() -> {
            result[0] = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, offset, length);
            if (state == stateFinished) {
                result[1] = 1;
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception ignore) {

        }
        return result;
    }

    public String getFileName() {
        if (location != null) {
            return location.volume_id + "_" + location.local_id + "." + ext;
        } else {
            return Utilities.MD5(webFile.url) + "." + ext;
        }
    }

    protected void removeStreamListener(final FileLoadOperationStream operation) {
        Utilities.stageQueue.postRunnable(() -> {
            if (streamListeners == null) {
                return;
            }
            streamListeners.remove(operation);
        });
    }

    private void copyNotLoadedRanges() {
        if (notLoadedBytesRanges == null) {
            return;
        }
        notLoadedBytesRangesCopy = new ArrayList<>(notLoadedBytesRanges);
    }

    public void pause() {
        if (state != stateDownloading) {
            return;
        }
        paused = true;
    }

    public boolean start() {
        return start(null, 0, false);
    }

    public boolean start(final FileLoadOperationStream stream, final int streamOffset, final boolean steamPriority) {
        if (currentDownloadChunkSize == 0) {
            currentDownloadChunkSize = totalBytesCount >= bigFileSizeFrom ? downloadChunkSizeBig : downloadChunkSize;
            currentMaxDownloadRequests = totalBytesCount >= bigFileSizeFrom ? maxDownloadRequestsBig : maxDownloadRequests;
        }
        final boolean alreadyStarted = state != stateIdle;
        final boolean wasPaused = paused;
        paused = false;
        if (stream != null) {
            Utilities.stageQueue.postRunnable(() -> {
                if (streamListeners == null) {
                    streamListeners = new ArrayList<>();
                }
                if (steamPriority) {
                    int offset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;
                    if (priorityRequestInfo != null && priorityRequestInfo.offset != offset) {
                        requestInfos.remove(priorityRequestInfo);
                        requestedBytesCount -= currentDownloadChunkSize;
                        removePart(notRequestedBytesRanges, priorityRequestInfo.offset, priorityRequestInfo.offset + currentDownloadChunkSize);
                        if (priorityRequestInfo.requestToken != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(priorityRequestInfo.requestToken, true);
                            requestsCount--;
                        }
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("frame get cancel request at offset " + priorityRequestInfo.offset);
                        }
                        priorityRequestInfo = null;
                    }
                    if (priorityRequestInfo == null) {
                        streamPriorityStartOffset = offset;
                    }
                } else {
                    streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize;
                }
                streamListeners.add(stream);
                if (alreadyStarted) {
                    if (preloadedBytesRanges != null && getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, streamStartOffset, 1) == 0) {
                        if (preloadedBytesRanges.get(streamStartOffset) != null) {
                            nextPartWasPreloaded = true;
                        }
                    }
                    startDownloadRequest();
                    nextPartWasPreloaded = false;
                }
            });
        } else if (wasPaused && alreadyStarted) {
            Utilities.stageQueue.postRunnable(this::startDownloadRequest);
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
        String fileNamePreload = null;
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
                    fileNamePreload = location.volume_id + "_" + location.local_id + ".preload";
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
                    fileNamePreload = datacenterId + "_" + location.id + ".preload";
                }
            }
        }

        requestInfos = new ArrayList<>(currentMaxDownloadRequests);
        delayedRequestInfos = new ArrayList<>(currentMaxDownloadRequests - 1);
        state = stateDownloading;

        if (parentObject instanceof TLRPC.TL_theme) {
            TLRPC.TL_theme theme = (TLRPC.TL_theme) parentObject;
            cacheFileFinal = new File(ApplicationLoader.getFilesDirFixed(), "remote" + theme.id + ".attheme");
        } else {
            cacheFileFinal = new File(storePath, fileNameFinal);
        }
        boolean finalFileExist = cacheFileFinal.exists();
        if (finalFileExist && (parentObject instanceof TLRPC.TL_theme || totalBytesCount != 0 && totalBytesCount != cacheFileFinal.length())) {
            cacheFileFinal.delete();
            finalFileExist = false;
        }

        if (!finalFileExist) {
            cacheFileTemp = new File(tempPath, fileNameTemp);
            if (ungzip) {
                cacheFileGzipTemp = new File(tempPath, fileNameTemp + ".gz");
            }
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

            boolean[] preloaded = new boolean[]{false};
            if (supportsPreloading && fileNamePreload != null) {
                cacheFilePreload = new File(tempPath, fileNamePreload);
                boolean closeStream = false;
                try {
                    preloadStream = new RandomAccessFile(cacheFilePreload, "rws");
                    long len = preloadStream.length();
                    int readOffset = 0;
                    preloadStreamFileOffset = 1;
                    if (len - readOffset > 1) {
                        preloaded[0] = preloadStream.readByte() != 0;
                        readOffset += 1;
                        while (readOffset < len) {
                            if (len - readOffset < 4) {
                                break;
                            }
                            int offset = preloadStream.readInt();
                            readOffset += 4;
                            if (len - readOffset < 4 || offset < 0 || offset > totalBytesCount) {
                                break;
                            }
                            int size = preloadStream.readInt();
                            readOffset += 4;
                            if (len - readOffset < size || size > currentDownloadChunkSize) {
                                break;
                            }
                            PreloadRange range = new PreloadRange(readOffset, size);
                            readOffset += size;
                            preloadStream.seek(readOffset);
                            if (len - readOffset < 12) {
                                break;
                            }
                            foundMoovSize = preloadStream.readInt();
                            if (foundMoovSize != 0) {
                                moovFound = nextPreloadDownloadOffset > totalBytesCount / 2 ? 2 : 1;
                                preloadNotRequestedBytesCount = foundMoovSize;
                            }
                            nextPreloadDownloadOffset = preloadStream.readInt();
                            nextAtomOffset = preloadStream.readInt();
                            readOffset += 12;

                            if (preloadedBytesRanges == null) {
                                preloadedBytesRanges = new SparseArray<>();
                            }
                            if (requestedPreloadedBytesRanges == null) {
                                requestedPreloadedBytesRanges = new SparseIntArray();
                            }
                            preloadedBytesRanges.put(offset, range);
                            requestedPreloadedBytesRanges.put(offset, 1);

                            totalPreloadedBytes += size;
                            preloadStreamFileOffset += 20 + size;
                        }
                    }
                    preloadStream.seek(preloadStreamFileOffset);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (!isPreloadVideoOperation && preloadedBytesRanges == null) {
                    cacheFilePreload = null;
                    try {
                        if (preloadStream != null) {
                            try {
                                preloadStream.getChannel().close();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            preloadStream.close();
                            preloadStream = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
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
                requestedBytesCount = downloadedBytes;
            }

            if (BuildVars.LOGS_ENABLED) {
                if (isPreloadVideoOperation) {
                    FileLog.d("start preloading file to temp = " + cacheFileTemp);
                } else {
                    FileLog.d("start loading file to temp = " + cacheFileTemp + " final = " + cacheFileFinal);
                }
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
            if (!isPreloadVideoOperation && downloadedBytes != 0 && totalBytesCount > 0) {
                copyNotLoadedRanges();
            }
            updateProgress();
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
            Utilities.stageQueue.postRunnable(() -> {
                if (totalBytesCount != 0 && (isPreloadVideoOperation && preloaded[0] || downloadedBytes == totalBytesCount)) {
                    try {
                        onFinishLoadingFile(false);
                    } catch (Exception e) {
                        onFail(true, 0);
                    }
                } else {
                    startDownloadRequest();
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

    public void updateProgress() {
        if (delegate != null && downloadedBytes != totalBytesCount && totalBytesCount > 0) {
            delegate.didChangedLoadProgress(FileLoadOperation.this, downloadedBytes, totalBytesCount);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setIsPreloadVideoOperation(boolean value) {
        if (isPreloadVideoOperation == value || value && totalBytesCount <= preloadMaxBytes) {
            return;
        }
        if (!value && isPreloadVideoOperation) {
            if (state == stateFinished) {
                isPreloadVideoOperation = value;
                state = stateIdle;
                preloadFinished = false;
                start();
            } else if (state == stateDownloading) {
                Utilities.stageQueue.postRunnable(() -> {
                    requestedBytesCount = 0;
                    clearOperaion(null, true);
                    isPreloadVideoOperation = value;
                    startDownloadRequest();
                });
            } else {
                isPreloadVideoOperation = value;
            }
        } else {
            isPreloadVideoOperation = value;
        }
    }

    public boolean isPreloadVideoOperation() {
        return isPreloadVideoOperation;
    }

    public boolean isPreloadFinished() {
        return preloadFinished;
    }

    public void cancel() {
        Utilities.stageQueue.postRunnable(() -> {
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
            if (preloadStream != null) {
                try {
                    preloadStream.getChannel().close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                preloadStream.close();
                preloadStream = null;
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
        if (isPreloadVideoOperation) {
            preloadFinished = true;
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("finished preloading file to " + cacheFileTemp + " loaded " + totalPreloadedBytes + " of " + totalBytesCount);
            }
        } else {
            if (cacheIvTemp != null) {
                cacheIvTemp.delete();
                cacheIvTemp = null;
            }
            if (cacheFileParts != null) {
                cacheFileParts.delete();
                cacheFileParts = null;
            }
            if (cacheFilePreload != null) {
                cacheFilePreload.delete();
                cacheFilePreload = null;
            }
            if (cacheFileTemp != null) {
                if (ungzip) {
                    try {
                        GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(cacheFileTemp));
                        FileLoader.copyFile(gzipInputStream, cacheFileGzipTemp, 1024 * 1024 * 2);
                        gzipInputStream.close();
                        cacheFileTemp.delete();
                        cacheFileTemp = cacheFileGzipTemp;
                        ungzip = false;
                    } catch (ZipException zipException) {
                        ungzip = false;
                    } catch (Throwable e) {
                        FileLog.e(e);
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("unable to ungzip temp = " + cacheFileTemp + " to final = " + cacheFileFinal);
                        }
                    }
                }
                if (!ungzip) {
                    boolean renameResult;
                    if (parentObject instanceof TLRPC.TL_theme) {
                        try {
                            renameResult = AndroidUtilities.copyFile(cacheFileTemp, cacheFileFinal);
                        } catch (Exception e) {
                            renameResult = false;
                            FileLog.e(e);
                        }
                    } else {
                        renameResult = cacheFileTemp.renameTo(cacheFileFinal);
                    }
                    if (!renameResult) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("unable to rename temp = " + cacheFileTemp + " to final = " + cacheFileFinal + " retry = " + renameRetryCount);
                        }
                        renameRetryCount++;
                        if (renameRetryCount < 3) {
                            state = stateDownloading;
                            Utilities.stageQueue.postRunnable(() -> {
                                try {
                                    onFinishLoadingFile(increment);
                                } catch (Exception e) {
                                    onFail(false, 0);
                                }
                            }, 200);
                            return;
                        }
                        cacheFileFinal = cacheFileTemp;
                    }
                } else {
                    onFail(false, 0);
                    return;
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("finished downloading file to " + cacheFileFinal);
            }
            if (increment) {
                if (currentType == ConnectionsManager.FileTypeAudio) {
                    StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
                } else if (currentType == ConnectionsManager.FileTypeVideo) {
                    StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
                } else if (currentType == ConnectionsManager.FileTypePhoto) {
                    StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
                } else if (currentType == ConnectionsManager.FileTypeFile) {
                    StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
                }
            }
        }
        delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
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

    private int findNextPreloadDownloadOffset(int atomOffset, int partOffset, NativeByteBuffer partBuffer) {
        int partSize = partBuffer.limit();
        while (true) {
            if (atomOffset < partOffset - (preloadTempBuffer != null ? 16 : 0) || atomOffset >= partOffset + partSize) {
                return 0;
            }
            if (atomOffset >= partOffset + partSize - 16) {
                preloadTempBufferCount = partOffset + partSize - atomOffset;
                partBuffer.position(partBuffer.limit() - preloadTempBufferCount);
                partBuffer.readBytes(preloadTempBuffer, 0, preloadTempBufferCount, false);
                return partOffset + partSize;
            }
            if (preloadTempBufferCount != 0) {
                partBuffer.position(0);
                partBuffer.readBytes(preloadTempBuffer, preloadTempBufferCount, 16 - preloadTempBufferCount, false);
                preloadTempBufferCount = 0;
            } else {
                partBuffer.position(atomOffset - partOffset);
                partBuffer.readBytes(preloadTempBuffer, 0, 16, false);
            }
            int atomSize = (((int) preloadTempBuffer[0] & 0xFF) << 24) + (((int) preloadTempBuffer[1] & 0xFF) << 16) + (((int) preloadTempBuffer[2] & 0xFF) << 8) + ((int) preloadTempBuffer[3] & 0xFF);
            if (atomSize == 0) {
                return 0;
            } else if (atomSize == 1) {
                atomSize = (((int) preloadTempBuffer[12] & 0xFF) << 24) + (((int) preloadTempBuffer[13] & 0xFF) << 16) + (((int) preloadTempBuffer[14] & 0xFF) << 8) + ((int) preloadTempBuffer[15] & 0xFF);
            }
            if (preloadTempBuffer[4] == 'm' && preloadTempBuffer[5] == 'o' && preloadTempBuffer[6] == 'o' && preloadTempBuffer[7] == 'v') {
                return -atomSize;
            }
            if (atomSize + atomOffset >= partOffset + partSize) {
                return atomSize + atomOffset;
            }
            atomOffset += atomSize;
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
        }, null, null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
    }

    protected boolean processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error) {
        if (state != stateDownloading) {
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("trying to write to finished file " + cacheFileFinal + " offset " + requestInfo.offset);
            }
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

                boolean finishedDownloading;
                if (isPreloadVideoOperation) {
                    preloadStream.writeInt(requestInfo.offset);
                    preloadStream.writeInt(currentBytesSize);
                    preloadStreamFileOffset += 8;
                    FileChannel channel = preloadStream.getChannel();
                    channel.write(bytes.buffer);
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("save preload file part " + cacheFilePreload + " offset " + requestInfo.offset + " size " + currentBytesSize);
                    }
                    if (preloadedBytesRanges == null) {
                        preloadedBytesRanges = new SparseArray<>();
                    }
                    preloadedBytesRanges.put(requestInfo.offset, new PreloadRange(preloadStreamFileOffset, currentBytesSize));

                    totalPreloadedBytes += currentBytesSize;
                    preloadStreamFileOffset += currentBytesSize;

                    if (moovFound == 0) {
                        int offset = findNextPreloadDownloadOffset(nextAtomOffset, requestInfo.offset, bytes);
                        if (offset < 0) {
                            offset *= -1;
                            nextPreloadDownloadOffset += currentDownloadChunkSize;
                            if (nextPreloadDownloadOffset < totalBytesCount / 2) {
                                preloadNotRequestedBytesCount = foundMoovSize = preloadMaxBytes / 2 + offset;
                                moovFound = 1;
                            } else {
                                preloadNotRequestedBytesCount = foundMoovSize = preloadMaxBytes;
                                moovFound = 2;
                            }
                            nextPreloadDownloadOffset = -1;
                        } else {
                            nextPreloadDownloadOffset = offset / currentDownloadChunkSize * currentDownloadChunkSize;
                        }
                        nextAtomOffset = offset;
                    }
                    preloadStream.writeInt(foundMoovSize);
                    preloadStream.writeInt(nextPreloadDownloadOffset);
                    preloadStream.writeInt(nextAtomOffset);
                    preloadStreamFileOffset += 12;
                    finishedDownloading = nextPreloadDownloadOffset == 0 || moovFound != 0 && foundMoovSize < 0 || totalPreloadedBytes > preloadMaxBytes || nextPreloadDownloadOffset >= totalBytesCount;
                    if (finishedDownloading) {
                        preloadStream.seek(0);
                        preloadStream.write((byte) 1);
                    } else if (moovFound != 0) {
                        foundMoovSize -= currentDownloadChunkSize;
                    }
                } else {
                    downloadedBytes += currentBytesSize;
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
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("save file part " + cacheFileFinal + " offset " + requestInfo.offset);
                        }
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
                        copyNotLoadedRanges();
                        delegate.didChangedLoadProgress(FileLoadOperation.this, downloadedBytes, totalBytesCount);
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
                        FileLog.e(error.text + " " + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                    } else if (webLocation != null) {
                        FileLog.e(error.text + " " + webLocation + " id = " + getFileName());
                    }
                }
                onFail(false, 0);
            }
        }
        return false;
    }

    protected void onFail(boolean thread, final int reason) {
        cleanup();
        state = stateFailed;
        if (thread) {
            Utilities.stageQueue.postRunnable(() -> delegate.didFailedLoadingFile(FileLoadOperation.this, reason));
        } else {
            delegate.didFailedLoadingFile(FileLoadOperation.this, reason);
        }
    }

    private void clearOperaion(RequestInfo currentInfo, boolean preloadChanged) {
        int minOffset = Integer.MAX_VALUE;
        for (int a = 0; a < requestInfos.size(); a++) {
            RequestInfo info = requestInfos.get(a);
            minOffset = Math.min(info.offset, minOffset);
            if (isPreloadVideoOperation) {
                requestedPreloadedBytesRanges.delete(info.offset);
            } else {
                removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            }
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
            if (isPreloadVideoOperation) {
                requestedPreloadedBytesRanges.delete(info.offset);
            } else {
                removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize);
            }
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
        if (!preloadChanged && isPreloadVideoOperation) {
            requestedBytesCount = totalPreloadedBytes;
        } else if (notLoadedBytesRanges == null) {
            requestedBytesCount = downloadedBytes = minOffset;
        }
    }

    private void requestReference(RequestInfo requestInfo) {
        if (requestingReference) {
            return;
        }
        clearOperaion(requestInfo, false);
        requestingReference = true;
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            if (messageObject.getId() < 0 && messageObject.messageOwner.media.webpage != null) {
                parentObject = messageObject.messageOwner.media.webpage;
            }
        }
        FileRefController.getInstance(currentAccount).requestReference(parentObject, location, this, requestInfo);
    }

    protected void startDownloadRequest() {
        if (paused || reuploadingCdn ||
                state != stateDownloading ||
                streamPriorityStartOffset == 0 && (
                        !nextPartWasPreloaded && (requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests) ||
                        isPreloadVideoOperation && (requestedBytesCount > preloadMaxBytes || moovFound != 0 && requestInfos.size() > 0))) {
            return;
        }
        int count = 1;
        if (streamPriorityStartOffset == 0 && !nextPartWasPreloaded && (!isPreloadVideoOperation || moovFound != 0) && totalBytesCount > 0) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
        }

        for (int a = 0; a < count; a++) {
            int downloadOffset;
            if (isPreloadVideoOperation) {
                if (moovFound != 0 && preloadNotRequestedBytesCount <= 0) {
                    return;
                }
                if (nextPreloadDownloadOffset == -1) {
                    downloadOffset = 0;
                    boolean found = false;
                    int tries = preloadMaxBytes / currentDownloadChunkSize + 2;
                    while (tries != 0) {
                        if (requestedPreloadedBytesRanges.get(downloadOffset, 0) == 0) {
                            found = true;
                            break;
                        }
                        downloadOffset += currentDownloadChunkSize;
                        if (downloadOffset > totalBytesCount) {
                            break;
                        }
                        if (moovFound == 2 && downloadOffset == currentDownloadChunkSize * 8) {
                            downloadOffset = (totalBytesCount - preloadMaxBytes / 2) / currentDownloadChunkSize * currentDownloadChunkSize;
                        }
                        tries--;
                    }
                    if (!found && requestInfos.isEmpty()) {
                        onFinishLoadingFile(false);
                    }
                } else {
                    downloadOffset = nextPreloadDownloadOffset;
                }
                if (requestedPreloadedBytesRanges == null) {
                    requestedPreloadedBytesRanges = new SparseIntArray();
                }
                requestedPreloadedBytesRanges.put(downloadOffset, 1);
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("start next preload from " + downloadOffset + " size " + totalBytesCount + " for " + cacheFilePreload);
                }
                preloadNotRequestedBytesCount -= currentDownloadChunkSize;
            } else {
                if (notRequestedBytesRanges != null) {
                    int sreamOffset = streamPriorityStartOffset != 0 ? streamPriorityStartOffset : streamStartOffset;
                    int size = notRequestedBytesRanges.size();
                    int minStart = Integer.MAX_VALUE;
                    int minStreamStart = Integer.MAX_VALUE;
                    for (int b = 0; b < size; b++) {
                        Range range = notRequestedBytesRanges.get(b);
                        if (sreamOffset != 0) {
                            if (range.start <= sreamOffset && range.end > sreamOffset) {
                                minStreamStart = sreamOffset;
                                minStart = Integer.MAX_VALUE;
                                break;
                            }
                            if (sreamOffset < range.start && range.start < minStreamStart) {
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
            }
            if (!isPreloadVideoOperation && notRequestedBytesRanges != null) {
                addPart(notRequestedBytesRanges, downloadOffset, downloadOffset + currentDownloadChunkSize, false);
            }

            if (totalBytesCount > 0 && downloadOffset >= totalBytesCount) {
                break;
            }
            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && downloadOffset + currentDownloadChunkSize >= totalBytesCount;
            final TLObject request;
            int connectionType = requestsCount % 2 == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
            int flags = (isForceRequest ? ConnectionsManager.RequestFlagForceDownload : 0);
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
                    req.cdn_supported = true;
                    request = req;
                }
            }
            requestedBytesCount += currentDownloadChunkSize;
            final RequestInfo requestInfo = new RequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = downloadOffset;

            if (!isPreloadVideoOperation && supportsPreloading && preloadStream != null && preloadedBytesRanges != null) {
                PreloadRange range = preloadedBytesRanges.get(requestInfo.offset);
                if (range != null) {
                    requestInfo.response = new TLRPC.TL_upload_file();
                    try {
                        NativeByteBuffer buffer = new NativeByteBuffer(range.length);
                        preloadStream.seek(range.fileOffset);
                        preloadStream.getChannel().read(buffer.buffer);
                        buffer.buffer.position(0);
                        requestInfo.response.bytes = buffer;
                        Utilities.stageQueue.postRunnable(() -> {
                            processRequestResult(requestInfo, null);
                            requestInfo.response.freeResources();
                        });
                        continue;
                    } catch (Exception ignore) {

                    }
                }
            }
            if (streamPriorityStartOffset != 0) {
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("frame get offset = " + streamPriorityStartOffset);
                }
                streamPriorityStartOffset = 0;
                priorityRequestInfo = requestInfo;
            }

            requestInfo.requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                if (!requestInfos.contains(requestInfo)) {
                    return;
                }
                if (requestInfo == priorityRequestInfo) {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("frame get request completed " + priorityRequestInfo.offset);
                    }
                    priorityRequestInfo = null;
                }
                if (error != null) {
                    if (FileRefController.isFileRefError(error.text)) {
                        requestReference(requestInfo);
                        return;
                    } else if (request instanceof TLRPC.TL_upload_getCdnFile) {
                        if (error.text.equals("FILE_TOKEN_INVALID")) {
                            isCdn = false;
                            clearOperaion(requestInfo, false);
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
                        for (int a1 = 0; a1 < res.file_hashes.size(); a1++) {
                            TLRPC.TL_fileHash hash = res.file_hashes.get(a1);
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
                        clearOperaion(requestInfo, false);
                        startDownloadRequest();
                    }
                } else if (response instanceof TLRPC.TL_upload_cdnFileReuploadNeeded) {
                    if (!reuploadingCdn) {
                        clearOperaion(requestInfo, false);
                        reuploadingCdn = true;
                        TLRPC.TL_upload_cdnFileReuploadNeeded res = (TLRPC.TL_upload_cdnFileReuploadNeeded) response;
                        TLRPC.TL_upload_reuploadCdnFile req = new TLRPC.TL_upload_reuploadCdnFile();
                        req.file_token = cdnToken;
                        req.request_token = res.request_token;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response1, error1) -> {
                            reuploadingCdn = false;
                            if (error1 == null) {
                                TLRPC.Vector vector = (TLRPC.Vector) response1;
                                if (!vector.objects.isEmpty()) {
                                    if (cdnHashes == null) {
                                        cdnHashes = new SparseArray<>();
                                    }
                                    for (int a1 = 0; a1 < vector.objects.size(); a1++) {
                                        TLRPC.TL_fileHash hash = (TLRPC.TL_fileHash) vector.objects.get(a1);
                                        cdnHashes.put(hash.offset, hash);
                                    }
                                }
                                startDownloadRequest();
                            } else {
                                if (error1.text.equals("FILE_TOKEN_INVALID") || error1.text.equals("REQUEST_TOKEN_INVALID")) {
                                    isCdn = false;
                                    clearOperaion(requestInfo, false);
                                    startDownloadRequest();
                                } else {
                                    onFail(false, 0);
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
            }, null, null, flags, isCdn ? cdnDatacenterId : datacenterId, connectionType, isLast);
            requestsCount++;
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}
