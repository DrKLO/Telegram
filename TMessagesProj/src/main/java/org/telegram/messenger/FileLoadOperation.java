/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.messenger.utils.ImmutableByteArrayOutputStream;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Storage.CacheModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

public class FileLoadOperation {

    private final boolean FULL_LOGS = false;
    private final static int FINISH_CODE_DEFAULT = 0;
    private final static int FINISH_CODE_FILE_ALREADY_EXIST = 1;
    public boolean preFinished;

    FileLoadOperationStream stream;
    boolean streamPriority;
    long streamOffset;

    public static volatile DispatchQueue filesQueue = new DispatchQueue("writeFileQueue");
    public static ImmutableByteArrayOutputStream filesQueueByteBuffer;
    private boolean forceSmallChunk;
    private Runnable fileWriteRunnable;
    public boolean isStory;

    public void setStream(FileLoadOperationStream stream, boolean streamPriority, long streamOffset) {
        this.stream = stream;
        this.streamOffset = streamOffset;
        this.streamPriority = streamPriority;
        Utilities.stageQueue.postRunnable(() -> {
            if (streamListeners == null) {
                streamListeners = new ArrayList<>();
            }
            if (stream != null && !streamListeners.contains(stream)) {
                streamListeners.add(stream);
            }
            if (stream != null && state != stateDownloading && state != stateIdle) {
                stream.newDataAvailable();
            }
        });
    }

    public int getPositionInQueue() {
        return getQueue().getPosition(this);
    }

    public boolean checkPrefixPreloadFinished() {
        if (preloadPrefixSize > 0 && downloadedBytes > preloadPrefixSize) {
            long minStart = Long.MAX_VALUE;
            ArrayList<Range> array = notLoadedBytesRanges;
            if (array == null) {
                return true;
            }
            try {
                for (int b = 0; b < array.size(); b++) {
                    Range range = array.get(b);
                    minStart = Math.min(minStart, range.start);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                return true;
            }
            if (minStart > preloadPrefixSize) {
                return true;
            }
        }
        return false;
    }

    protected static class RequestInfo {
        public long requestStartTime;
        public int chunkSize;
        public int connectionType;
        private int requestToken;
        private long offset;
        private TLRPC.TL_upload_file response;
        private TLRPC.TL_upload_webFile responseWeb;
        private TLRPC.TL_upload_cdnFile responseCdn;
        private boolean forceSmallChunk;
    }

    public static class Range {
        private long start;
        private long end;

        private Range(long s, long e) {
            start = s;
            end = e;
        }

        @Override
        public String toString() {
            return "Range{" +
                    "start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    private static final Object lockObject = new Object();

    private static class PreloadRange {
        private long fileOffset;
        private long length;

        private PreloadRange(long o, long l) {
            fileOffset = o;
            length = l;
        }
    }

    private ArrayList<FileLoadOperationStream> streamListeners;

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;
    private final static int stateCanceled = 4;

    private int downloadChunkSize = 1024 * 32;
    private int downloadChunkSizeBig = 1024 * 128;
    private int cdnChunkCheckSize = 1024 * 128;
    private int maxDownloadRequests = 4;
    private int maxDownloadRequestsBig = 4;
    private int bigFileSizeFrom = 10 * 1024 * 1024;
    private int maxCdnParts = (int) (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);

    //load small parts for stream
    private int downloadChunkSizeAnimation = 1024 * 128;
    private int maxDownloadRequestsAnimation = 4;

    private final static int preloadMaxBytes = 2 * 1024 * 1024;

    private String fileName;
    private String storeFileName;

    private HashMap<Long, PreloadRange> preloadedBytesRanges;
    private HashMap<Long, Integer> requestedPreloadedBytesRanges;
    private RandomAccessFile preloadStream;
    private int preloadStreamFileOffset;
    private int totalPreloadedBytes;
    private boolean isPreloadVideoOperation;
    private boolean preloadFinished;
    private File cacheFilePreload;
    private boolean supportsPreloading;
    private long nextPreloadDownloadOffset;
    private long nextAtomOffset;
    private long foundMoovSize;
    private long preloadNotRequestedBytesCount;
    private int moovFound;
    private byte[] preloadTempBuffer = new byte[24];
    private int preloadTempBufferCount;
    private int preloadPrefixSize;

    private boolean nextPartWasPreloaded;

    protected long lastProgressUpdateTime;

    private ArrayList<Range> notLoadedBytesRanges;
    private volatile ArrayList<Range> notLoadedBytesRangesCopy;
    private ArrayList<Range> notRequestedBytesRanges;
    private ArrayList<Range> notCheckedCdnRanges;
    private long requestedBytesCount;

    public int currentAccount;
    private boolean started;
    private int datacenterId;
    private int initialDatacenterId;
    protected TLRPC.InputFileLocation location;
    private TLRPC.InputWebFileLocation webLocation;
    private WebFile webFile;
    private volatile int state = stateIdle;
    private volatile boolean paused;
    private long downloadedBytes;
    public long totalBytesCount;
    private long bytesCountPadding;
    private long streamStartOffset;
    private long streamPriorityStartOffset;
    private RequestInfo priorityRequestInfo;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;
    private int currentDownloadChunkSize;
    private int currentMaxDownloadRequests;
    private int requestsCount;
    private int renameRetryCount;
    private static int globalRequestPointer;

    private boolean encryptFile;
    private boolean allowDisordererFileSave;

    public Object parentObject;

    private HashMap<Long, TLRPC.TL_fileHash> cdnHashes;

    private boolean isStream;

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
    private FilePathDatabase.FileMeta fileMetadata;

    private boolean ungzip;

    private int currentType;
    public FilePathDatabase.PathData pathSaveData;
    private long startTime;
    private FileLoaderPriorityQueue priorityQueue;

    public interface FileLoadOperationDelegate {
        void didPreFinishLoading(FileLoadOperation operation, File finalFile);
        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int state);
        void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize);
        void saveFilePath(FilePathDatabase.PathData pathSaveData, File cacheFileFinal);
        boolean hasAnotherRefOnFile(String path);
        boolean isLocallyCreatedFile(String path);
    }

    private void updateParams() {
        if ((preloadPrefixSize > 0 || MessagesController.getInstance(currentAccount).getfileExperimentalParams) && !forceSmallChunk) {
            downloadChunkSizeBig = 1024 * 512;
            maxDownloadRequests = 8;
            maxDownloadRequestsBig = 8;
        } else {
            downloadChunkSizeBig = 1024 * 128;
            maxDownloadRequests = 4;
            maxDownloadRequestsBig = 4;
        }
        maxCdnParts = (int) (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);
    }

    public FileLoadOperation(ImageLocation imageLocation, Object parent, String extension, long size) {
        updateParams();
        parentObject = parent;
        fileMetadata = FileLoader.getFileMetadataFromParent(currentAccount, parentObject);
        isStream = imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION;
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
            TLRPC.TL_inputPeerPhotoFileLocation inputPeerPhotoFileLocation = new TLRPC.TL_inputPeerPhotoFileLocation();
            inputPeerPhotoFileLocation.id = imageLocation.location.volume_id;
            inputPeerPhotoFileLocation.volume_id = imageLocation.location.volume_id;
            inputPeerPhotoFileLocation.local_id = imageLocation.location.local_id;
            inputPeerPhotoFileLocation.photo_id = imageLocation.photoId;
            inputPeerPhotoFileLocation.big = imageLocation.photoPeerType == ImageLocation.TYPE_BIG;
            inputPeerPhotoFileLocation.peer = imageLocation.photoPeer;
            location = inputPeerPhotoFileLocation;
        } else if (imageLocation.stickerSet != null) {
            TLRPC.TL_inputStickerSetThumb inputStickerSetThumb = new TLRPC.TL_inputStickerSetThumb();
            inputStickerSetThumb.id = imageLocation.location.volume_id;
            inputStickerSetThumb.volume_id = imageLocation.location.volume_id;
            inputStickerSetThumb.local_id = imageLocation.location.local_id;
            inputStickerSetThumb.thumb_version = imageLocation.thumbVersion;
            inputStickerSetThumb.stickerset = imageLocation.stickerSet;
            location = inputStickerSetThumb;
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
        updateParams();
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
        updateParams();
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
        updateParams();
        try {
            parentObject = parent;
            fileMetadata = FileLoader.getFileMetadataFromParent(currentAccount, parentObject);
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
                        preloadPrefixSize = documentLocation.attributes.get(a).preload_prefix_size;
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

    public void setPaths(int instance, String name, FileLoaderPriorityQueue priorityQueue, File store, File temp, String finalName) {
        this.storePath = store;
        this.tempPath = temp;
        this.currentAccount = instance;
        this.fileName = name;
        this.storeFileName = finalName;
        this.priorityQueue = priorityQueue;
    }

    public FileLoaderPriorityQueue getQueue() {
        return priorityQueue;
    }

    public boolean wasStarted() {
        return started && !paused;
    }

    public int getCurrentType() {
        return currentType;
    }

    private void removePart(ArrayList<Range> ranges, long start, long end) {
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

    long totalTime;

    private void addPart(ArrayList<Range> ranges, long start, long end, boolean save) {
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
                ArrayList<FileLoadOperation.Range> rangesFinal = new ArrayList<>(ranges);
                if (fileWriteRunnable != null) {
                    filesQueue.cancelRunnable(fileWriteRunnable);
                }
                filesQueue.postRunnable(fileWriteRunnable = () -> {
                    long time = System.currentTimeMillis();
                    try {
                        if (filePartsStream == null) {
                            return;
                        }
                        int countFinal = rangesFinal.size();
                        int bufferSize = 4 + 8 * 2 * countFinal;
                        if (filesQueueByteBuffer == null) {
                            filesQueueByteBuffer = new ImmutableByteArrayOutputStream(bufferSize);
                        } else {
                            filesQueueByteBuffer.reset();
                        }
                        filesQueueByteBuffer.writeInt(countFinal);
                        for (int a = 0; a < countFinal; a++) {
                            Range rangeFinal = rangesFinal.get(a);
                            filesQueueByteBuffer.writeLong(rangeFinal.start);
                            filesQueueByteBuffer.writeLong(rangeFinal.end);
                        }
                        synchronized (FileLoadOperation.this) {
                            if (filePartsStream == null) {
                                return;
                            }
                            filePartsStream.seek(0);
                            filePartsStream.write(filesQueueByteBuffer.buf, 0, bufferSize);
                        }
                    } catch (Exception e) {
                        FileLog.e(e, false);
                        if (AndroidUtilities.isENOSPC(e)) {
                            LaunchActivity.checkFreeDiscSpaceStatic(1);
                        } else if (AndroidUtilities.isEROFS(e)) {
                            SharedConfig.checkSdCard(cacheFileFinal);
                        }
                    }
                    totalTime += System.currentTimeMillis() - time;
                });
                notifyStreamListeners();
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e(cacheFileFinal + " downloaded duplicate file part " + start + " - " + end);
                }
            }
        }
    }

    private void notifyStreamListeners() {
        if (streamListeners != null) {
            int count = streamListeners.size();
            for (int a = 0; a < count; a++) {
                streamListeners.get(a).newDataAvailable();
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
            if (state == stateFinished && !preloadFinished) {
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

    protected File getCurrentFileFast() {
        if (state == stateFinished && !preloadFinished) {
            return cacheFileFinal;
        } else {
            return cacheFileTemp;
        }
    }

    private long getDownloadedLengthFromOffsetInternal(ArrayList<Range> ranges, final long offset, final long length) {
        if (ranges == null || state == stateFinished || ranges.isEmpty()) {
            if (state == stateFinished) {
                return length;
            }
            if (downloadedBytes == 0) {
                return 0;
            } else {
                return Math.min(length, Math.max(downloadedBytes - offset, 0));
            }
        } else {
            int count = ranges.size();
            Range range;
            Range minRange = null;
            long availableLength = length;
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

    protected long[] getDownloadedLengthFromOffset(final long offset, final long length) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final long[] result = new long[2];
        Utilities.stageQueue.postRunnable(() -> {
            try {
                result[0] = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, offset, length);
            } catch (Throwable e) {
                FileLog.e(e);
                result[0] = 0;
            }
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
        return fileName;
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
        Utilities.stageQueue.postRunnable(() -> {
            if (isStory) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("debug_loading:" + cacheFileFinal.getName() + " pause operation, clear requests");
                }
                clearOperaion(null, false);
            } else {
                for (int i = 0; i < requestInfos.size(); i++) {
                    ConnectionsManager.getInstance(currentAccount).failNotRunningRequest(requestInfos.get(i).requestToken);
                }
            }
        });
    }

    public boolean start() {
        return start(stream, streamOffset, streamPriority);
    }

    public boolean start(final FileLoadOperationStream stream, final long streamOffset, final boolean streamPriority) {
        startTime = System.currentTimeMillis();
        updateParams();
        isStory = parentObject instanceof TL_stories.TL_storyItem;
        if (currentDownloadChunkSize == 0) {
            if (forceSmallChunk) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("debug_loading: restart with small chunk");
                }
                currentDownloadChunkSize =  1024 * 32;
                currentMaxDownloadRequests = 4;
            } else if (isStory) {
                currentDownloadChunkSize = downloadChunkSizeBig;
                currentMaxDownloadRequests = maxDownloadRequestsBig;
            } else if (isStream) {
                currentDownloadChunkSize = downloadChunkSizeAnimation;
                currentMaxDownloadRequests = maxDownloadRequestsAnimation;
            } else {
                boolean bigChunk = totalBytesCount >= bigFileSizeFrom;
                currentDownloadChunkSize = bigChunk ? downloadChunkSizeBig : downloadChunkSize;
                currentMaxDownloadRequests = bigChunk ? maxDownloadRequestsBig : maxDownloadRequests;
            }
        }
        final boolean alreadyStarted = state != stateIdle;
        final boolean wasPaused = paused;
        paused = false;
        if (stream != null) {
            Utilities.stageQueue.postRunnable(() -> {
                if (streamListeners == null) {
                    streamListeners = new ArrayList<>();
                }
                if (streamPriority) {
                    long offset = (streamOffset / (long) currentDownloadChunkSize) * (long) currentDownloadChunkSize;
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
//                if (!streamListeners.contains(stream)) {
//                    streamListeners.add(stream);
//                }
                if (alreadyStarted) {
                    if (preloadedBytesRanges != null && getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, streamStartOffset, 1) == 0) {
                        if (preloadedBytesRanges.get(streamStartOffset) != null) {
                            nextPartWasPreloaded = true;
                        }
                    }
                    startDownloadRequest(-1);
                    nextPartWasPreloaded = false;
                }
            });
        } else if (alreadyStarted) {
            Utilities.stageQueue.postRunnable(() -> {
                startDownloadRequest(-1);
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
        String fileNamePreload = null;
        String fileNameIv = null;
        if (webLocation != null) {
            String md5 = Utilities.MD5(webFile.url);
            if (encryptFile) {
                fileNameTemp = md5 + ".temp.enc";
                fileNameFinal = md5 + "." + ext + ".enc";
                if (key != null) {
                    fileNameIv = md5 + "_64.iv.enc";
                }
            } else {
                fileNameTemp = md5 + ".temp";
                fileNameFinal = md5 + "." + ext;
                if (key != null) {
                    fileNameIv = md5 + "_64.iv";
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
                        fileNameIv = location.volume_id + "_" + location.local_id + "_64.iv.enc";
                    }
                } else {
                    fileNameTemp = location.volume_id + "_" + location.local_id + ".temp";
                    fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext;
                    if (key != null) {
                        fileNameIv = location.volume_id + "_" + location.local_id + "_64.iv";
                    }
                    if (notLoadedBytesRanges != null) {
                        fileNameParts = location.volume_id + "_" + location.local_id + "_64.pt";
                    }
                    fileNamePreload = location.volume_id + "_" + location.local_id + "_64.preload";
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
                        fileNameIv = datacenterId + "_" + location.id + "_64.iv.enc";
                    }
                } else {
                    fileNameTemp = datacenterId + "_" + location.id + ".temp";
                    fileNameFinal = datacenterId + "_" + location.id + ext;
                    if (key != null) {
                        fileNameIv = datacenterId + "_" + location.id + "_64.iv";
                    }
                    if (notLoadedBytesRanges != null) {
                        fileNameParts = datacenterId + "_" + location.id + "_64.pt";
                    }
                    fileNamePreload = datacenterId + "_" + location.id + "_64.preload";
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
            if (!encryptFile) {
                cacheFileFinal = new File(storePath, storeFileName);
            } else {
                cacheFileFinal = new File(storePath, fileNameFinal);
            }
        }
        boolean finalFileExist = cacheFileFinal.exists();
        if (finalFileExist && (parentObject instanceof TLRPC.TL_theme || (totalBytesCount != 0 && !ungzip && totalBytesCount != cacheFileFinal.length())) && !delegate.isLocallyCreatedFile(cacheFileFinal.toString())) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("debug_loading: delete existing file cause file size mismatch " + cacheFileFinal.getName() + " totalSize=" + totalBytesCount + " existingFileSize=" + cacheFileFinal.length());
            }
            if (!delegate.hasAnotherRefOnFile(cacheFileFinal.toString())) {
                cacheFileFinal.delete();
            }
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
                    if (AndroidUtilities.isENOSPC(e)) {
                        LaunchActivity.checkFreeDiscSpaceStatic(1);
                        FileLog.e(e, false);
                    } else if (AndroidUtilities.isEROFS(e)) {
                        SharedConfig.checkSdCard(cacheFileFinal);
                        FileLog.e(e, false);
                    } else {
                        FileLog.e(e);
                    }
                }
            }

            boolean[] preloaded = new boolean[]{false};
            if (supportsPreloading && fileNamePreload != null) {
                cacheFilePreload = new File(tempPath, fileNamePreload);
                boolean closeStream = false;
                try {
                    preloadStream = new RandomAccessFile(cacheFilePreload, "rws");
                    long len = preloadStream.length();
                    long readOffset = 0;
                    preloadStreamFileOffset = 1;
                    if (len - readOffset > 1) {
                        preloaded[0] = preloadStream.readByte() != 0;
                        readOffset += 1;
                        while (readOffset < len) {
                            if (len - readOffset < 8) {
                                break;
                            }
                            long offset = preloadStream.readLong();
                            readOffset += 8;
                            if (len - readOffset < 8 || offset < 0 || offset > totalBytesCount) {
                                break;
                            }
                            long size = preloadStream.readLong();
                            readOffset += 8;
                            if (len - readOffset < size || size > currentDownloadChunkSize) {
                                break;
                            }
                            PreloadRange range = new PreloadRange(readOffset, size);
                            readOffset += size;
                            preloadStream.seek(readOffset);
                            if (len - readOffset < 24) {
                                break;
                            }
                            foundMoovSize = preloadStream.readLong();
                            if (foundMoovSize != 0) {
                                moovFound = nextPreloadDownloadOffset > totalBytesCount / 2 ? 2 : 1;
                                preloadNotRequestedBytesCount = foundMoovSize;
                            }
                            nextPreloadDownloadOffset = preloadStream.readLong();
                            nextAtomOffset = preloadStream.readLong();
                            readOffset += 24;

                            if (preloadedBytesRanges == null) {
                                preloadedBytesRanges = new HashMap<>();
                            }
                            if (requestedPreloadedBytesRanges == null) {
                                requestedPreloadedBytesRanges = new HashMap<>();
                            }
                            preloadedBytesRanges.put(offset, range);
                            requestedPreloadedBytesRanges.put(offset, 1);

                            totalPreloadedBytes += size;
                            preloadStreamFileOffset += 36 + size;
                        }
                    }
                    preloadStream.seek(preloadStreamFileOffset);
                } catch (Exception e) {
                    FileLog.e(e, false);
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
                if (!cacheFileTemp.exists()) {
                    cacheFileParts.delete();
                }
                try {
                    filePartsStream = new RandomAccessFile(cacheFileParts, "rws");
                    long len = filePartsStream.length();
                    if (len % 8 == 4) {
                        len -= 4;
                        int count = filePartsStream.readInt();
                        if (count <= len / 2) {
                            for (int a = 0; a < count; a++) {
                                long start = filePartsStream.readLong();
                                long end = filePartsStream.readLong();
                                notLoadedBytesRanges.add(new Range(start, end));
                                notRequestedBytesRanges.add(new Range(start, end));
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e, !AndroidUtilities.isFilNotFoundException(e));
                }
            }

            if (fileMetadata != null) {
                FileLoader.getInstance(currentAccount).getFileDatabase().saveFileDialogId(cacheFileParts, fileMetadata);
                FileLoader.getInstance(currentAccount).getFileDatabase().saveFileDialogId(cacheFileTemp, fileMetadata);
            }


            if (cacheFileTemp.exists()) {
                if (newKeyGenerated) {
                    cacheFileTemp.delete();
                } else {
                    long totalDownloadedLen = cacheFileTemp.length();
                    if (fileNameIv != null && (totalDownloadedLen % currentDownloadChunkSize) != 0) {
                        requestedBytesCount = 0;
                    } else {
                        requestedBytesCount = downloadedBytes = floorDiv(cacheFileTemp.length(), currentDownloadChunkSize) * currentDownloadChunkSize;
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
                    FileLog.d("start loading file to temp = " + cacheFileTemp + " final = " + cacheFileFinal + " priority" + priority);
                }
            }

            if (fileNameIv != null) {
                cacheIvTemp = new File(tempPath, fileNameIv);
                try {
                    fiv = new RandomAccessFile(cacheIvTemp, "rws");
                    if (downloadedBytes != 0 && !newKeyGenerated) {
                        long len = cacheIvTemp.length();
                        if (len > 0 && len % 64 == 0) {
                            fiv.read(iv, 0, 64);
                        } else {
                            requestedBytesCount = downloadedBytes = 0;
                        }
                    }
                } catch (Exception e) {
                    requestedBytesCount = downloadedBytes = 0;
                    if (AndroidUtilities.isENOSPC(e)) {
                        LaunchActivity.checkFreeDiscSpaceStatic(1);
                        FileLog.e(e, false);
                    } else if (AndroidUtilities.isEROFS(e)) {
                        SharedConfig.checkSdCard(cacheFileFinal);
                        FileLog.e(e, false);
                    } else {
                        FileLog.e(e);
                    }
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
                FileLog.e(e, false);
                if (AndroidUtilities.isENOSPC(e)) {
                    LaunchActivity.checkFreeDiscSpaceStatic(1);
                    onFail(true, -1);
                    return false;
                } else if (AndroidUtilities.isEROFS(e)) {
                    SharedConfig.checkSdCard(cacheFileFinal);
                    FileLog.e(e, false);
                    onFail(true, -1);
                    return false;
                }
            }
            if (fileOutputStream == null) {
                onFail(true, 0);
                return false;
            }
            started = true;
            Utilities.stageQueue.postRunnable(() -> {
                boolean videoPreloaded = isPreloadVideoOperation && preloaded[0];
                boolean preloadedByPrefixSize = preloadPrefixSize > 0 && downloadedBytes >= preloadPrefixSize && canFinishPreload();
                if (totalBytesCount != 0 && (videoPreloaded || downloadedBytes == totalBytesCount || preloadedByPrefixSize)) {
                    try {
                        onFinishLoadingFile(false, FINISH_CODE_FILE_ALREADY_EXIST, true);
                    } catch (Exception e) {
                        onFail(true, 0);
                    }
                } else {
                    startDownloadRequest(-1);
                }
            });
        } else {
            started = true;
            try {
                onFinishLoadingFile(false, FINISH_CODE_FILE_ALREADY_EXIST, false);
                if (pathSaveData != null) {
                    delegate.saveFilePath(pathSaveData, cacheFileFinal);
                }
            } catch (Exception e) {
                FileLog.e(e, false);
                if (AndroidUtilities.isENOSPC(e)) {
                    LaunchActivity.checkFreeDiscSpaceStatic(1);
                    onFail(true, -1);
                } if (AndroidUtilities.isEROFS(e)) {
                    SharedConfig.checkSdCard(cacheFileFinal);
                    onFail(true, -1);
                    return false;
                } else {
                    onFail(true, 0);
                }
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
                    startDownloadRequest(-1);
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
        cancel(false);
    }

    private void cancel(boolean deleteFiles) {
        Utilities.stageQueue.postRunnable(() -> {
            if (state != stateFinished && state != stateFailed) {
                cancelRequests();
                onFail(false, 1);
            }
            if (deleteFiles) {
                if (cacheFileFinal != null) {
                    try {
                        if (!cacheFileFinal.delete()) {
                            cacheFileFinal.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheFileTemp != null) {
                    try {
                        if (!cacheFileTemp.delete()) {
                            cacheFileTemp.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheFileParts != null) {
                    try {
                        if (!cacheFileParts.delete()) {
                            cacheFileParts.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheIvTemp != null) {
                    try {
                        if (!cacheIvTemp.delete()) {
                            cacheIvTemp.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cacheFilePreload != null) {
                    try {
                        if (!cacheFilePreload.delete()) {
                            cacheFilePreload.deleteOnExit();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });
    }

    private void cancelRequests() {
        if (requestInfos != null) {
            int[] waitingDownloadSize = new int[2];
            for (int a = 0; a < requestInfos.size(); a++) {
                RequestInfo requestInfo = requestInfos.get(a);
                if (requestInfo.requestToken != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(requestInfo.requestToken, true);
                    int index = requestInfo.connectionType == ConnectionsManager.ConnectionTypeDownload ? 0 : 1;
                    waitingDownloadSize[index] += requestInfo.chunkSize;
                }
            }
            for (int i = 0; i < 2; i++) {
                int connectionType = i == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
                if (waitingDownloadSize[i] > 512 * 1024 * 2)  {
                    int datacenterId = isCdn ? cdnDatacenterId : this.datacenterId;
                    ConnectionsManager.getInstance(currentAccount).discardConnection(datacenterId, connectionType);
                }
            }

        }
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
                synchronized (FileLoadOperation.this) {
                    try {
                        filePartsStream.getChannel().close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    filePartsStream.close();
                    filePartsStream = null;
                }
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

    private void onFinishLoadingFile(final boolean increment, int finishCode, boolean preload) {
        if (state != stateDownloading) {
            return;
        }
        state = stateFinished;
        notifyStreamListeners();
        cleanup();
        if (isPreloadVideoOperation || preload) {
            preloadFinished = true;
            if (BuildVars.DEBUG_VERSION) {
                if (finishCode == FINISH_CODE_FILE_ALREADY_EXIST) {
                    FileLog.d("file already exist " + cacheFileTemp);
                } else {
                    FileLog.d("finished preloading file to " + cacheFileTemp + " loaded " + downloadedBytes + " of " + totalBytesCount + " prefSize=" + preloadPrefixSize);
                }
            }
            if (fileMetadata != null) {
                if (cacheFileTemp != null) {
                    FileLoader.getInstance(currentAccount).getFileDatabase().removeFiles(Collections.singletonList(new CacheModel.FileInfo(cacheFileTemp)));
                }
                if (cacheFileParts != null) {
                    FileLoader.getInstance(currentAccount).getFileDatabase().removeFiles(Collections.singletonList(new CacheModel.FileInfo(cacheFileParts)));
                }
            }
            delegate.didPreFinishLoading(FileLoadOperation.this, cacheFileFinal);
            delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
        } else {
            final File cacheIvTempFinal = cacheIvTemp;
            final File cacheFilePartsFinal = cacheFileParts;
            final File cacheFilePreloadFinal = cacheFilePreload;
            final File cacheFileTempFinal = cacheFileTemp;
            filesQueue.postRunnable(() -> {
                if (cacheIvTempFinal != null) {
                    cacheIvTempFinal.delete();
                }
                if (cacheFilePartsFinal != null) {
                    cacheFilePartsFinal.delete();
                }
                if (cacheFilePreloadFinal != null) {
                    cacheFilePreloadFinal.delete();
                }
                File cacheFileTempLocal = cacheFileTempFinal;
                if (cacheFileTempLocal != null) {
                    if (ungzip) {
                        try {
                            GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(cacheFileTempLocal));
                            FileLoader.copyFile(gzipInputStream, cacheFileGzipTemp, 1024 * 1024 * 2);
                            gzipInputStream.close();
                            cacheFileTempLocal.delete();
                            cacheFileTempLocal = cacheFileGzipTemp;
                            ungzip = false;
                        } catch (ZipException zipException) {
                            ungzip = false;
                        } catch (Throwable e) {
                            FileLog.e(e, !AndroidUtilities.isFilNotFoundException(e));
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.e("unable to ungzip temp = " + cacheFileTempFinal + " to final = " + cacheFileFinal);
                            }
                        }
                    }
                    if (!ungzip) {
                        boolean renameResult;
                        if (parentObject instanceof TLRPC.TL_theme) {
                            try {
                                renameResult = AndroidUtilities.copyFile(cacheFileTempLocal, cacheFileFinal);
                            } catch (Exception e) {
                                renameResult = false;
                                FileLog.e(e);
                            }
                        } else {
                            try {
                                if (pathSaveData != null) {
                                    synchronized (lockObject) {
                                        cacheFileFinal = new File(storePath, storeFileName);
                                        int count = 1;
                                        while (cacheFileFinal.exists()) {
                                            int lastDotIndex = storeFileName.lastIndexOf('.');
                                            String newFileName;
                                            if (lastDotIndex > 0) {
                                                newFileName = storeFileName.substring(0, lastDotIndex) + " (" + count + ")" + storeFileName.substring(lastDotIndex);
                                            } else {
                                                newFileName = storeFileName + " (" + count + ")";
                                            }
                                            cacheFileFinal = new File(storePath, newFileName);
                                            count++;
                                        }
                                    }
                                }
                                renameResult = cacheFileTempLocal.renameTo(cacheFileFinal);
                            } catch (Exception e) {
                                renameResult = false;
                                FileLog.e(e);
                            }
                        }
                        if (!renameResult && renameRetryCount == 3) {
                            try {
                                renameResult = AndroidUtilities.copyFile(cacheFileTempLocal, cacheFileFinal);
                                if (renameResult) {
                                    cacheFileFinal.delete();
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }
                        if (!renameResult) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.e("unable to rename temp = " + cacheFileTempLocal + " to final = " + cacheFileFinal + " retry = " + renameRetryCount);
                            }
                            renameRetryCount++;
                            if (renameRetryCount < 3) {
                                state = stateDownloading;
                                Utilities.stageQueue.postRunnable(() -> {
                                    try {
                                        onFinishLoadingFile(increment, FINISH_CODE_DEFAULT, false);
                                    } catch (Exception e) {
                                        onFail(false, 0);
                                    }
                                }, 200);
                                return;
                            }
                            cacheFileFinal = cacheFileTempLocal;
                        } else {
                            if (pathSaveData != null && cacheFileFinal.exists()) {
                                delegate.saveFilePath(pathSaveData, cacheFileFinal);
                            }
                        }
                    } else {
                        Utilities.stageQueue.postRunnable(() -> {
                            onFail(false, 0);
                        });
                        return;
                    }
                }
                Utilities.stageQueue.postRunnable(() -> {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("finished downloading file to " + cacheFileFinal + " time = " + (System.currentTimeMillis() - startTime) + " dc = " + datacenterId + " size = " + AndroidUtilities.formatFileSize(totalBytesCount));
                    }
                    if (increment) {
                        if (currentType == ConnectionsManager.FileTypeAudio) {
                            StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypeVideo) {
                            StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypePhoto) {
                            StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypeFile) {
                            if (ext != null && (ext.toLowerCase().endsWith("mp3") || ext.toLowerCase().endsWith("m4a"))) {
                                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_MUSIC, 1);
                            } else {
                                StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
                            }
                        }
                    }
                    delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
                });
            });
            cacheIvTemp = null;
            cacheFileParts = null;
            cacheFilePreload = null;
            delegate.didPreFinishLoading(FileLoadOperation.this, cacheFileFinal);
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

    private long findNextPreloadDownloadOffset(long atomOffset, long partOffset, NativeByteBuffer partBuffer) {
        int partSize = partBuffer.limit();
        while (true) {
            if (atomOffset < partOffset - (preloadTempBuffer != null ? 16 : 0) || atomOffset >= partOffset + partSize) {
                return 0;
            }
            if (atomOffset >= partOffset + partSize - 16) {
                long count = partOffset + partSize - atomOffset;
                if (count > Integer.MAX_VALUE) {
                    throw new RuntimeException("!!!");
                }
                preloadTempBufferCount = (int) count;
                long position = partBuffer.limit() - preloadTempBufferCount;

                partBuffer.position((int) position);
                partBuffer.readBytes(preloadTempBuffer, 0, preloadTempBufferCount, false);
                return partOffset + partSize;
            }
            if (preloadTempBufferCount != 0) {
                partBuffer.position(0);
                partBuffer.readBytes(preloadTempBuffer, preloadTempBufferCount, 16 - preloadTempBufferCount, false);
                preloadTempBufferCount = 0;
            } else {
                long count = atomOffset - partOffset;
                if (count > Integer.MAX_VALUE) {
                    throw new RuntimeException("!!!");
                }
                partBuffer.position((int) count);
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

    private void requestFileOffsets(long offset) {
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
                        cdnHashes = new HashMap<>();
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
            if (BuildVars.DEBUG_VERSION && state == stateFinished) {
                FileLog.e(new FileLog.IgnoreSentException("trying to write to finished file " + fileName + " offset " + requestInfo.offset + " " + totalBytesCount));
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
                    onFinishLoadingFile(true, FINISH_CODE_DEFAULT, false);
                    return false;
                }
                int currentBytesSize = bytes.limit();
                if (isCdn) {
                    long cdnCheckPart = requestInfo.offset / cdnChunkCheckSize;
                    long fileOffset = cdnCheckPart * cdnChunkCheckSize;
                    TLRPC.TL_fileHash hash = cdnHashes != null ? cdnHashes.get(fileOffset) : null;
                    if (hash == null) {
                        delayRequestInfo(requestInfo);
                        requestFileOffsets(fileOffset);
                        return true;
                    }
                }

                if (requestInfo.responseCdn != null) {
                    long offset = requestInfo.offset / 16;
                    cdnIv[15] = (byte) (offset & 0xff);
                    cdnIv[14] = (byte) ((offset >> 8) & 0xff);
                    cdnIv[13] = (byte) ((offset >> 16) & 0xff);
                    cdnIv[12] = (byte) ((offset >> 24) & 0xff);
                    Utilities.aesCtrDecryption(bytes.buffer, cdnKey, cdnIv, 0, bytes.limit());
                }

                boolean finishedDownloading;
                boolean finishPreload = false;
                if (isPreloadVideoOperation) {
                    preloadStream.writeLong(requestInfo.offset);
                    preloadStream.writeLong(currentBytesSize);
                    preloadStreamFileOffset += 16;
                    FileChannel channel = preloadStream.getChannel();
                    channel.write(bytes.buffer);
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("save preload file part " + cacheFilePreload + " offset " + requestInfo.offset + " size " + currentBytesSize);
                    }
                    if (preloadedBytesRanges == null) {
                        preloadedBytesRanges = new HashMap<>();
                    }
                    preloadedBytesRanges.put(requestInfo.offset, new PreloadRange(preloadStreamFileOffset, currentBytesSize));

                    totalPreloadedBytes += currentBytesSize;
                    preloadStreamFileOffset += currentBytesSize;

                    if (moovFound == 0) {
                        long offset = findNextPreloadDownloadOffset(nextAtomOffset, requestInfo.offset, bytes);
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
                            nextPreloadDownloadOffset += currentDownloadChunkSize;
                        }
                        nextAtomOffset = offset;
                    }
                    preloadStream.writeLong(foundMoovSize);
                    preloadStream.writeLong(nextPreloadDownloadOffset);
                    preloadStream.writeLong(nextAtomOffset);
                    preloadStreamFileOffset += 24;
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
                        finishedDownloading = downloadedBytes >= totalBytesCount || (preloadPrefixSize > 0 && downloadedBytes >= preloadPrefixSize && canFinishPreload() && requestInfos.isEmpty());
                        if (downloadedBytes < totalBytesCount) {
                            finishPreload = true;
                        }
                    } else {
                        finishedDownloading = currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes);
                    }
                    if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                        FileLog.d(cacheFileFinal.getName() + " downloadedBytes=" + downloadedBytes + " total=" + totalBytesCount + " " + finishedDownloading + " " + finishPreload);
                    }
                    if (key != null) {
                        Utilities.aesIgeEncryption(bytes.buffer, key, iv, false, true, 0, bytes.limit());
                        if (finishedDownloading && bytesCountPadding != 0) {
                            long limit = bytes.limit() - bytesCountPadding;
                            if (BuildVars.DEBUG_VERSION && limit > Integer.MAX_VALUE) {
                                throw new RuntimeException("Out of limit" + limit);
                            }
                            bytes.limit((int) (limit));
                        }
                    }
                    if (encryptFile) {
                        long offset = requestInfo.offset / 16;
                        encryptIv[15] = (byte) (offset & 0xff);
                        encryptIv[14] = (byte) ((offset >> 8) & 0xff);
                        encryptIv[13] = (byte) ((offset >> 16) & 0xff);
                        encryptIv[12] = (byte) ((offset >> 24) & 0xff);
                        Utilities.aesCtrDecryption(bytes.buffer, encryptKey, encryptIv, 0, bytes.limit());
                    }

                    if (notLoadedBytesRanges != null) {
                        fileOutputStream.seek(requestInfo.offset);
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("save file part " + fileName + " offset=" + requestInfo.offset + " chunk_size=" + currentDownloadChunkSize + " isCdn=" + isCdn);
                        }
                    }
                    FileChannel channel = fileOutputStream.getChannel();
                    channel.write(bytes.buffer);
                    addPart(notLoadedBytesRanges, requestInfo.offset, requestInfo.offset + currentBytesSize, true);
                    if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                        FileLog.d(fileName + " add part " + requestInfo.offset + " " + (requestInfo.offset + currentBytesSize));
                        FileLog.d(fileName + " notLoadedBytesRanges=" + notLoadedBytesRanges);
                    }
                    if (isCdn) {
                        long cdnCheckPart = requestInfo.offset / cdnChunkCheckSize;

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
                            long fileOffset = cdnCheckPart * cdnChunkCheckSize;
                            long availableSize = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, fileOffset, cdnChunkCheckSize);
                            if (availableSize != 0 && (availableSize == cdnChunkCheckSize || totalBytesCount > 0 && availableSize == totalBytesCount - fileOffset || totalBytesCount <= 0 && finishedDownloading)) {
                                TLRPC.TL_fileHash hash = cdnHashes.get(fileOffset);
                                if (fileReadStream == null) {
                                    cdnCheckBytes = new byte[cdnChunkCheckSize];
                                    fileReadStream = new RandomAccessFile(cacheFileTemp, "r");
                                }
                                fileReadStream.seek(fileOffset);
                                if (BuildVars.DEBUG_VERSION && availableSize > Integer.MAX_VALUE) {
                                    throw new RuntimeException("!!!");
                                }
                                fileReadStream.readFully(cdnCheckBytes, 0, (int) availableSize);

                                if (encryptFile) {
                                    long offset = fileOffset / 16;
                                    encryptIv[15] = (byte) (offset & 0xff);
                                    encryptIv[14] = (byte) ((offset >> 8) & 0xff);
                                    encryptIv[13] = (byte) ((offset >> 16) & 0xff);
                                    encryptIv[12] = (byte) ((offset >> 24) & 0xff);
                                    Utilities.aesCtrDecryptionByteArray(cdnCheckBytes, encryptKey, encryptIv, 0, availableSize, 0);
                                }

                                byte[] sha256 = Utilities.computeSHA256(cdnCheckBytes, 0, availableSize);
                                if (!Arrays.equals(sha256, hash.hash)) {
                                    if (BuildVars.LOGS_ENABLED) {
                                        if (location != null) {
                                            FileLog.e("invalid cdn hash " + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                                        } else if (webLocation != null) {
                                            FileLog.e("invalid cdn hash  " + webLocation + " id = " + fileName);
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
                    onFinishLoadingFile(true, FINISH_CODE_DEFAULT, finishPreload);
                } else if (state != stateCanceled) {
                    startDownloadRequest(requestInfo.connectionType);
                }
            } catch (Exception e) {
                FileLog.e(e, !AndroidUtilities.isFilNotFoundException(e) && !AndroidUtilities.isENOSPC(e));
                if (AndroidUtilities.isENOSPC(e)) {
                    onFail(false, -1);
                } else if (AndroidUtilities.isEROFS(e)) {
                    SharedConfig.checkSdCard(cacheFileFinal);
                    onFail(true, -1);
                } else {
                    onFail(false, 0);
                }
            }
        } else {
            if (error.text.contains("LIMIT_INVALID") && !requestInfo.forceSmallChunk) {
                removePart(notRequestedBytesRanges, requestInfo.offset, requestInfo.offset + requestInfo.chunkSize);
                if (!forceSmallChunk) {
                    forceSmallChunk = true;
                    currentDownloadChunkSize =  1024 * 32;
                    currentMaxDownloadRequests = 4;
                }
                startDownloadRequest(requestInfo.connectionType);
            } else if (error.text.contains("FILE_MIGRATE_")) {
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
                    startDownloadRequest(requestInfo.connectionType);
                }
            } else if (error.text.contains("OFFSET_INVALID")) {
                if (downloadedBytes % currentDownloadChunkSize == 0) {
                    try {
                        onFinishLoadingFile(true, FINISH_CODE_DEFAULT, false);
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
                        if (location instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
                            FileLog.e(error.text + " " + location + " peer_did = " + DialogObject.getPeerDialogId(((TLRPC.TL_inputPeerPhotoFileLocation) location).peer) + " peer_access_hash=" + ((TLRPC.TL_inputPeerPhotoFileLocation) location).peer.access_hash + " photo_id=" + ((TLRPC.TL_inputPeerPhotoFileLocation) location).photo_id + " big=" + ((TLRPC.TL_inputPeerPhotoFileLocation) location).big);
                        } else {
                            FileLog.e(error.text + " " + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                        }
                    } else if (webLocation != null) {
                        FileLog.e(error.text + " " + webLocation + " id = " + fileName);
                    }
                }
                onFail(false, 0);
            }
        }
        return false;
    }

    private boolean canFinishPreload() {
        return isStory && priority < FileLoader.PRIORITY_HIGH;
    }

    protected void onFail(boolean thread, final int reason) {
        cleanup();
        state = reason == 1 ? stateCanceled : stateFailed;
        if (delegate != null) {
            if (BuildVars.LOGS_ENABLED) {
                long time = startTime == 0 ? 0 : (System.currentTimeMillis() - startTime);
                if (reason == 1) {
                    FileLog.d("cancel downloading file to " + cacheFileFinal + " time = " + time + " dc = " + datacenterId + " size = " + AndroidUtilities.formatFileSize(totalBytesCount));
                } else {
                    FileLog.d("failed downloading file to " + cacheFileFinal + " reason = " + reason + " time = " + time + " dc = " + datacenterId + " size = " + AndroidUtilities.formatFileSize(totalBytesCount));
                }
            }
        }
        if (thread) {
            Utilities.stageQueue.postRunnable(() -> {
                if (delegate != null) {
                    delegate.didFailedLoadingFile(FileLoadOperation.this, reason);
                }
                notifyStreamListeners();
            });
        } else {
            if (delegate != null) {
                delegate.didFailedLoadingFile(FileLoadOperation.this, reason);
            }
            notifyStreamListeners();
        }
    }

    private void clearOperaion(RequestInfo currentInfo, boolean preloadChanged) {
        long minOffset = Long.MAX_VALUE;
        int[] waitingDownloadSize = new int[2];
        for (int a = 0; a < requestInfos.size(); a++) {
            RequestInfo info = requestInfos.get(a);
            minOffset = Math.min(info.offset, minOffset);
            if (isPreloadVideoOperation) {
                requestedPreloadedBytesRanges.remove(info.offset);
            } else {
                removePart(notRequestedBytesRanges, info.offset, info.offset + info.chunkSize);
            }
            if (currentInfo == info) {
                continue;
            }
            if (info.requestToken != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(info.requestToken, true);
            }
        }
        for (int i = 0; i < 2; i++) {
            int connectionType = i == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
            if (waitingDownloadSize[i] > 512 * 1024 * 2)  {
                int datacenterId = isCdn ? cdnDatacenterId : this.datacenterId;
                ConnectionsManager.getInstance(currentAccount).discardConnection(datacenterId, connectionType);
            }
        }
        requestInfos.clear();
        for (int a = 0; a < delayedRequestInfos.size(); a++) {
            RequestInfo info = delayedRequestInfos.get(a);
            if (isPreloadVideoOperation) {
                requestedPreloadedBytesRanges.remove(info.offset);
            } else {
                removePart(notRequestedBytesRanges, info.offset, info.offset + info.chunkSize);
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
        clearOperaion(null, false);
        requestingReference = true;
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            if (messageObject.getId() < 0 && messageObject.messageOwner != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                parentObject = messageObject.messageOwner.media.webpage;
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("debug_loading: " + cacheFileFinal.getName() + " file reference expired ");
        }
        FileRefController.getInstance(currentAccount).requestReference(parentObject, location, this, requestInfo);
    }

    protected void startDownloadRequest(int useConnectionType) {
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            if (Utilities.stageQueue != null && Utilities.stageQueue.getHandler() != null && Thread.currentThread() != Utilities.stageQueue.getHandler().getLooper().getThread()) {
                throw new RuntimeException("Wrong thread!!!");
            }
        }
        if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
            FileLog.d(fileName + " startDownloadRequest");
        }
        if (paused || reuploadingCdn || state != stateDownloading || requestingReference ||
                (!isStory && streamPriorityStartOffset == 0 && (!nextPartWasPreloaded && (requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests))) ||
                (isPreloadVideoOperation && (requestedBytesCount > preloadMaxBytes || moovFound != 0 && requestInfos.size() > 0))) {
            if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                FileLog.d(fileName + "can't start request wrong state: paused=" + paused + " reuploadingCdn=" + reuploadingCdn + " state=" + state + " requestingReference=" + requestingReference);
            }
            return;
        }
        int count = 1;
        if (isStory) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
        } else {
            if (streamPriorityStartOffset == 0 && !nextPartWasPreloaded && (!isPreloadVideoOperation || moovFound != 0) && totalBytesCount > 0) {
                count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
            }
        }

        for (int a = 0; a < count; a++) {
            long downloadOffset;
            if (isPreloadVideoOperation) {
                if (moovFound != 0 && preloadNotRequestedBytesCount <= 0) {
                    if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                        FileLog.d(fileName + " can't start request: waiting moov");
                    }
                    return;
                }
                if (nextPreloadDownloadOffset == -1) {
                    downloadOffset = 0;
                    boolean found = false;
                    int tries = preloadMaxBytes / currentDownloadChunkSize + 2;
                    while (tries != 0) {
                        if (!requestedPreloadedBytesRanges.containsKey(downloadOffset)) {
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
                        onFinishLoadingFile(false, FINISH_CODE_DEFAULT, false);
                    }
                } else {
                    downloadOffset = nextPreloadDownloadOffset;
                }
                if (requestedPreloadedBytesRanges == null) {
                    requestedPreloadedBytesRanges = new HashMap<>();
                }
                requestedPreloadedBytesRanges.put(downloadOffset, 1);
                if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("start next preload from " + downloadOffset + " size " + totalBytesCount + " for " + cacheFilePreload);
                }
                preloadNotRequestedBytesCount -= currentDownloadChunkSize;
            } else {
                if (notRequestedBytesRanges != null) {
                    long streamOffset = streamPriorityStartOffset != 0 ? streamPriorityStartOffset : streamStartOffset;
                    int size = notRequestedBytesRanges.size();
                    long minStart = Long.MAX_VALUE;
                    long minStreamStart = Long.MAX_VALUE;
                    for (int b = 0; b < size; b++) {
                        Range range = notRequestedBytesRanges.get(b);
                        if (streamOffset != 0) {
                            if (range.start <= streamOffset && range.end > streamOffset) {
                                minStreamStart = streamOffset;
                                minStart = Long.MAX_VALUE;
                                break;
                            }
                            if (streamOffset < range.start && range.start < minStreamStart) {
                                minStreamStart = range.start;
                            }
                        }
                        minStart = Math.min(minStart, range.start);
                    }
                    if (minStreamStart != Long.MAX_VALUE) {
                        downloadOffset = minStreamStart;
                    } else if (minStart != Long.MAX_VALUE) {
                        downloadOffset = minStart;
                    } else {
                        if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                            FileLog.d(fileName + " can't start request ranges finished" + notRequestedBytesRanges);
                        }
                        break;
                    }
                } else {
                    downloadOffset = requestedBytesCount;
                }
            }
            if (preloadPrefixSize > 0 && downloadOffset >= preloadPrefixSize && canFinishPreload()) {
                if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                    FileLog.d(fileName + " can't start request: preload finished");
                }
                break;
            }
            if (totalBytesCount > 0 && downloadOffset > 0 && downloadOffset >= totalBytesCount) {
                if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                    FileLog.d(fileName + " can't start request: loading finished");
                }
                break;
            }
            if (!isPreloadVideoOperation && notRequestedBytesRanges != null) {
                addPart(notRequestedBytesRanges, downloadOffset, downloadOffset + currentDownloadChunkSize, false);
                if (BuildVars.LOGS_ENABLED && FULL_LOGS) {
                    FileLog.d(fileName + " add part " + downloadOffset + " " + (downloadOffset + currentDownloadChunkSize));
                    FileLog.d(fileName + " notRequestedBytesRanges=" + notRequestedBytesRanges);
                }
            }

            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && downloadOffset + currentDownloadChunkSize >= totalBytesCount;
            final TLObject request;
            int connectionType;
            if (useConnectionType == -1) {
                connectionType = requestsCount % 2 == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
                //globalRequestPointer++;
            } else {
                connectionType = useConnectionType;
            }

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
                    req.offset = (int) downloadOffset;
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
            requestInfo.chunkSize = currentDownloadChunkSize;
            requestInfo.forceSmallChunk = forceSmallChunk;
            requestInfo.connectionType = connectionType;

            if (!isPreloadVideoOperation && supportsPreloading && preloadStream != null && preloadedBytesRanges != null) {
                PreloadRange range = preloadedBytesRanges.get(requestInfo.offset);
                if (range != null) {
                    requestInfo.response = new TLRPC.TL_upload_file();
                    try {
                        if (BuildVars.DEBUG_VERSION && range.length > Integer.MAX_VALUE) {
                            throw new RuntimeException("cast long to integer");
                        }
                        NativeByteBuffer buffer = new NativeByteBuffer((int) range.length);
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
            if (location instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
                TLRPC.TL_inputPeerPhotoFileLocation inputPeerPhotoFileLocation = (TLRPC.TL_inputPeerPhotoFileLocation) location;
                if (inputPeerPhotoFileLocation.photo_id == 0) {
                    requestReference(requestInfo);
                    continue;
                }
            }
            requestInfo.forceSmallChunk = forceSmallChunk;
            if (BuildVars.LOGS_ENABLED) {
                requestInfo.requestStartTime = System.currentTimeMillis();
            }
            int datacenterId = isCdn ? cdnDatacenterId : this.datacenterId;
            requestInfo.requestToken = ConnectionsManager.getInstance(currentAccount).sendRequestSync(request, (response, error) -> {
                if (!requestInfos.contains(requestInfo)) {
                    return;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("debug_loading: " + cacheFileFinal.getName() + " time=" + (System.currentTimeMillis() - requestInfo.requestStartTime) + " dcId=" + datacenterId + " cdn=" + isCdn + " conType=" + connectionType + " reqId" + requestInfo.requestToken);
                }
                if (requestInfo == priorityRequestInfo) {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("frame get request completed " + priorityRequestInfo.offset);
                    }
                    priorityRequestInfo = null;
                }
                if (error != null) {
                    if (error.code == -2000) {
                        requestInfos.remove(requestInfo);
                        requestedBytesCount -= requestInfo.chunkSize;
                        removePart(notRequestedBytesRanges, requestInfo.offset, requestInfo.offset + requestInfo.chunkSize);
                        return;
                    } else if (FileRefController.isFileRefError(error.text)) {
                        requestReference(requestInfo);
                        return;
                    } else if (request instanceof TLRPC.TL_upload_getCdnFile) {
                        if (error.text.equals("FILE_TOKEN_INVALID")) {
                            isCdn = false;
                            clearOperaion(requestInfo, false);
                            startDownloadRequest(connectionType);
                            return;
                        }
                    }
                }
                if (response instanceof TLRPC.TL_upload_fileCdnRedirect) {
                    TLRPC.TL_upload_fileCdnRedirect res = (TLRPC.TL_upload_fileCdnRedirect) response;
                    if (!res.file_hashes.isEmpty()) {
                        if (cdnHashes == null) {
                            cdnHashes = new HashMap<>();
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
                        startDownloadRequest(connectionType);
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
                                        cdnHashes = new HashMap<>();
                                    }
                                    for (int a1 = 0; a1 < vector.objects.size(); a1++) {
                                        TLRPC.TL_fileHash hash = (TLRPC.TL_fileHash) vector.objects.get(a1);
                                        cdnHashes.put(hash.offset, hash);
                                    }
                                }
                                startDownloadRequest(connectionType);
                            } else {
                                if (error1.text.equals("FILE_TOKEN_INVALID") || error1.text.equals("REQUEST_TOKEN_INVALID")) {
                                    isCdn = false;
                                    clearOperaion(requestInfo, false);
                                    startDownloadRequest(connectionType);
                                } else {
                                    onFail(false, 0);
                                }
                            }
                        }, null, null, 0, this.datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
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
                            if (ext != null && (ext.toLowerCase().endsWith("mp3") || ext.toLowerCase().endsWith("m4a"))) {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_MUSIC, response.getObjectSize() + 4);
                            } else {
                                StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_FILES, response.getObjectSize() + 4);
                            }
                        }
                    }
                    processRequestResult(requestInfo, error);
                }
            }, null, null, flags, datacenterId, connectionType, isLast);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("debug_loading: " + cacheFileFinal.getName() + " dc=" + datacenterId + " send reqId " + requestInfo.requestToken + " offset=" + requestInfo.offset + " conType=" + connectionType + " priority=");
            }
            requestsCount++;
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }

    public static long floorDiv(long x, long y) {
        long r = x / y;
        // if the signs are different and modulo not zero, round down
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    public boolean isFinished() {
        return state == stateFinished;
    }
}
