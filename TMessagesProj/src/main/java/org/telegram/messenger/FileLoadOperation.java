/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

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
import java.util.HashMap;
import java.util.Scanner;

public class FileLoadOperation {

    private static class RequestInfo {
        private int requestToken;
        private int offset;
        private TLRPC.TL_upload_file response;
        private TLRPC.TL_upload_webFile responseWeb;
        private TLRPC.TL_upload_cdnFile responseCdn;
    }

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;

    private final static int downloadChunkSize = 1024 * 32;
    private final static int downloadChunkSizeBig = 1024 * 128;
    private final static int cdnChunkCheckSize = 1024 * 128;
    private final static int maxDownloadRequests = 4;
    private final static int maxDownloadRequestsBig = 2;
    private final static int bigFileSizeFrom = 1024 * 1024;

    private boolean started;
    private int datacenter_id;
    private TLRPC.InputFileLocation location;
    private TLRPC.TL_inputWebFileLocation webLocation;
    private volatile int state = stateIdle;
    private int downloadedBytes;
    private int totalBytesCount;
    private int bytesCountPadding;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;
    private int currentDownloadChunkSize;
    private int currentMaxDownloadRequests;
    private int requestsCount;
    private int renameRetryCount;

    private boolean encryptFile;

    private HashMap<Integer, TLRPC.TL_cdnFileHash> cdnHashes;

    private byte[] encryptKey;
    private byte[] encryptIv;

    private boolean isCdn;
    private byte[] cdnIv;
    private byte[] cdnKey;
    private byte[] cdnToken;
    private int cdnDatacenterId;
    private boolean reuploadingCdn;
    private int lastCheckedCdnPart;
    private RandomAccessFile fileReadStream;
    private byte[] cdnCheckBytes;
    private boolean requestingCdnOffsets;

    private int nextDownloadOffset;
    private ArrayList<RequestInfo> requestInfos;
    private ArrayList<RequestInfo> delayedRequestInfos;

    private File cacheFileTemp;
    private File cacheFileFinal;
    private File cacheIvTemp;

    private String ext;
    private RandomAccessFile fileOutputStream;
    private RandomAccessFile fiv;
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
            datacenter_id = photoLocation.dc_id;
        } else if (photoLocation instanceof TLRPC.TL_fileLocation) {
            location = new TLRPC.TL_inputFileLocation();
            location.volume_id = photoLocation.volume_id;
            location.secret = photoLocation.secret;
            location.local_id = photoLocation.local_id;
            datacenter_id = photoLocation.dc_id;
        }
        currentType = ConnectionsManager.FileTypePhoto;
        totalBytesCount = size;
        ext = extension != null ? extension : "jpg";
    }

    public FileLoadOperation(TLRPC.TL_webDocument webDocument) {
        webLocation = new TLRPC.TL_inputWebFileLocation();
        webLocation.url = webDocument.url;
        webLocation.access_hash = webDocument.access_hash;
        totalBytesCount = webDocument.size;
        datacenter_id = webDocument.dc_id;
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
        ext = ImageLoader.getHttpUrlExtension(webDocument.url, defaultExt);
    }

    public FileLoadOperation(TLRPC.Document documentLocation) {
        try {
            if (documentLocation instanceof TLRPC.TL_documentEncrypted) {
                location = new TLRPC.TL_inputEncryptedFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                datacenter_id = documentLocation.dc_id;
                iv = new byte[32];
                System.arraycopy(documentLocation.iv, 0, iv, 0, iv.length);
                key = documentLocation.key;
            } else if (documentLocation instanceof TLRPC.TL_document) {
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = documentLocation.id;
                location.access_hash = documentLocation.access_hash;
                datacenter_id = documentLocation.dc_id;
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
    }

    public void setForceRequest(boolean forceRequest) {
        isForceRequest = forceRequest;
    }

    public boolean isForceRequest() {
        return isForceRequest;
    }

    public void setPaths(File store, File temp) {
        storePath = store;
        tempPath = temp;
    }

    public boolean wasStarted() {
        return started;
    }

    public int getCurrentType() {
        return currentType;
    }

    public String getFileName() {
        if (location != null) {
            return location.volume_id + "_" + location.local_id + "." + ext;
        } else {
            return Utilities.MD5(webLocation.url) + "." + ext;
        }
    }

    public boolean start() {
        if (state != stateIdle) {
            return false;
        }
        if (location == null && webLocation == null) {
            onFail(true, 0);
            return false;
        }

        String fileNameFinal;
        String fileNameTemp;
        String fileNameIv = null;
        if (webLocation != null) {
            String md5 = Utilities.MD5(webLocation.url);
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
                if (datacenter_id == Integer.MIN_VALUE || location.volume_id == Integer.MIN_VALUE || datacenter_id == 0) {
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
                }
            } else {
                if (datacenter_id == 0 || location.id == 0) {
                    onFail(true, 0);
                    return false;
                }
                if (encryptFile) {
                    fileNameTemp = datacenter_id + "_" + location.id + ".temp.enc";
                    fileNameFinal = datacenter_id + "_" + location.id + ext + ".enc";
                    if (key != null) {
                        fileNameIv = datacenter_id + "_" + location.id + ".iv.enc";
                    }
                } else {
                    fileNameTemp = datacenter_id + "_" + location.id + ".temp";
                    fileNameFinal = datacenter_id + "_" + location.id + ext;
                    if (key != null) {
                        fileNameIv = datacenter_id + "_" + location.id + ".iv";
                    }
                }
            }
        }
        currentDownloadChunkSize = totalBytesCount >= bigFileSizeFrom ? downloadChunkSizeBig : downloadChunkSize;
        currentMaxDownloadRequests = totalBytesCount >= bigFileSizeFrom ? maxDownloadRequestsBig : maxDownloadRequests;
        requestInfos = new ArrayList<>(currentMaxDownloadRequests);
        delayedRequestInfos = new ArrayList<>(currentMaxDownloadRequests - 1);
        state = stateDownloading;

        cacheFileFinal = new File(storePath, fileNameFinal);
        boolean exist = cacheFileFinal.exists();
        if (exist && totalBytesCount != 0 && totalBytesCount != cacheFileFinal.length()) {
            cacheFileFinal.delete();
        }

        if (!cacheFileFinal.exists()) {
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

            if (cacheFileTemp.exists()) {
                if (newKeyGenerated) {
                    cacheFileTemp.delete();
                } else {
                    downloadedBytes = (int) cacheFileTemp.length();
                    nextDownloadOffset = downloadedBytes = downloadedBytes / currentDownloadChunkSize * currentDownloadChunkSize;
                }
            }

            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("start loading file to temp = " + cacheFileTemp + " final = " + cacheFileFinal);
            }

            if (fileNameIv != null) {
                cacheIvTemp = new File(tempPath, fileNameIv);
                try {
                    fiv = new RandomAccessFile(cacheIvTemp, "rws");
                    if (!newKeyGenerated) {
                        long len = cacheIvTemp.length();
                        if (len > 0 && len % 32 == 0) {
                            fiv.read(iv, 0, 32);
                        } else {
                            downloadedBytes = 0;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    downloadedBytes = 0;
                }
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
                            ConnectionsManager.getInstance().cancelRequest(requestInfo.requestToken, true);
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

    private void onFinishLoadingFile(final boolean increment) throws Exception {
        if (state != stateDownloading) {
            return;
        }
        state = stateFinished;
        cleanup();
        if (cacheIvTemp != null) {
            cacheIvTemp.delete();
            cacheIvTemp = null;
        }
        if (cacheFileTemp != null) {
            boolean renameResult = cacheFileTemp.renameTo(cacheFileFinal);
            if (!renameResult) {
                if (BuildVars.DEBUG_VERSION) {
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
        if (BuildVars.DEBUG_VERSION) {
            FileLog.e("finished downloading file to " + cacheFileFinal);
        }
        delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
        if (increment) {
            if (currentType == ConnectionsManager.FileTypeAudio) {
                StatsController.getInstance().incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeVideo) {
                StatsController.getInstance().incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
            } else if (currentType == ConnectionsManager.FileTypePhoto) {
                StatsController.getInstance().incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
            } else if (currentType == ConnectionsManager.FileTypeFile) {
                StatsController.getInstance().incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
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
                            TLRPC.TL_cdnFileHash hash = (TLRPC.TL_cdnFileHash) vector.objects.get(a);
                            cdnHashes.put(hash.offset, hash);
                        }
                    }
                    for (int a = 0; a < delayedRequestInfos.size(); a++) {
                        RequestInfo delayedRequestInfo = delayedRequestInfos.get(a);
                        if (downloadedBytes == delayedRequestInfo.offset) {
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
        }, null, null, 0, datacenter_id, ConnectionsManager.ConnectionTypeGeneric, true);
    }

    private boolean processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error) {
        if (state != stateDownloading) {
            return false;
        }
        requestInfos.remove(requestInfo);
        if (error == null) {
            try {
                if (downloadedBytes != requestInfo.offset) {
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
                    int cdnCheckPart = (downloadedBytes + currentBytesSize) / cdnChunkCheckSize;
                    int fileOffset = (cdnCheckPart - (lastCheckedCdnPart != cdnCheckPart ? 1 : 0)) * cdnChunkCheckSize;
                    TLRPC.TL_cdnFileHash hash = cdnHashes != null ? cdnHashes.get(fileOffset) : null;
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
                boolean finishedDownloading = currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes);

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
                FileChannel channel = fileOutputStream.getChannel();
                channel.write(bytes.buffer);
                if (isCdn) {
                    int cdnCheckPart = downloadedBytes / cdnChunkCheckSize;
                    if (cdnCheckPart != lastCheckedCdnPart || finishedDownloading) {
                        fileOutputStream.getFD().sync();
                        int fileOffset = (cdnCheckPart - (lastCheckedCdnPart != cdnCheckPart ? 1 : 0)) * cdnChunkCheckSize;
                        TLRPC.TL_cdnFileHash hash = cdnHashes.get(fileOffset);
                        if (fileReadStream == null) {
                            cdnCheckBytes = new byte[1024 * 128];
                            fileReadStream = new RandomAccessFile(cacheFileTemp, "r");
                            if (fileOffset != 0) {
                                fileReadStream.seek(fileOffset);
                            }
                        }
                        int count;
                        if (lastCheckedCdnPart != cdnCheckPart) {
                            count = cdnChunkCheckSize;
                        } else {
                            count = downloadedBytes - cdnCheckPart * cdnChunkCheckSize;
                        }
                        fileReadStream.readFully(cdnCheckBytes, 0, count);
                        byte[] sha256 = Utilities.computeSHA256(cdnCheckBytes, 0, count);
                        if (!Arrays.equals(sha256, hash.hash)) {
                            if (location != null) {
                                FileLog.e("invalid cdn hash " + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                            } else if (webLocation != null) {
                                FileLog.e("invalid cdn hash  " + webLocation + " id = " + webLocation.url + " access_hash = " + webLocation.access_hash);
                            }
                            onFail(false, 0);
                            cacheFileTemp.delete();
                            return false;
                        }
                        lastCheckedCdnPart = cdnCheckPart;
                    }
                }
                if (fiv != null) {
                    fiv.seek(0);
                    fiv.write(iv);
                }
                if (totalBytesCount > 0 && state == stateDownloading) {
                    delegate.didChangedLoadProgress(FileLoadOperation.this, Math.min(1.0f, (float) downloadedBytes / (float) totalBytesCount));
                }

                for (int a = 0; a < delayedRequestInfos.size(); a++) {
                    RequestInfo delayedRequestInfo = delayedRequestInfos.get(a);
                    if (downloadedBytes == delayedRequestInfo.offset) {
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
                    datacenter_id = val;
                    nextDownloadOffset = 0;
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
                if (location != null) {
                    FileLog.e("" + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                } else if (webLocation != null) {
                    FileLog.e("" + webLocation + " id = " + webLocation.url + " access_hash = " + webLocation.access_hash);
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
            if (currentInfo == info) {
                continue;
            }
            if (info.requestToken != 0) {
                ConnectionsManager.getInstance().cancelRequest(info.requestToken, true);
            }
        }
        requestInfos.clear();
        for (int a = 0; a < delayedRequestInfos.size(); a++) {
            RequestInfo info = delayedRequestInfos.get(a);
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
        nextDownloadOffset = minOffset;
    }

    private void startDownloadRequest() {
        if (state != stateDownloading || totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount || requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests) {
            return;
        }
        int count = 1;
        if (totalBytesCount > 0) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size());
        }

        for (int a = 0; a < count; a++) {
            if (totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount) {
                break;
            }
            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && nextDownloadOffset + currentDownloadChunkSize >= totalBytesCount;
            final TLObject request;
            int offset;
            int connectionType = requestsCount % 2 == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2;
            int flags = (isForceRequest ? ConnectionsManager.RequestFlagForceDownload : 0) | ConnectionsManager.RequestFlagFailOnServerErrors;
            if (isCdn) {
                TLRPC.TL_upload_getCdnFile req = new TLRPC.TL_upload_getCdnFile();
                req.file_token = cdnToken;
                req.offset = offset = nextDownloadOffset;
                req.limit = currentDownloadChunkSize;
                request = req;
                flags |= ConnectionsManager.RequestFlagEnableUnauthorized;
            } else {
                if (webLocation != null) {
                    TLRPC.TL_upload_getWebFile req = new TLRPC.TL_upload_getWebFile();
                    req.location = webLocation;
                    req.offset = offset = nextDownloadOffset;
                    req.limit = currentDownloadChunkSize;
                    request = req;
                } else {
                    TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
                    req.location = location;
                    req.offset = offset = nextDownloadOffset;
                    req.limit = currentDownloadChunkSize;
                    request = req;
                }
            }
            nextDownloadOffset += currentDownloadChunkSize;
            final RequestInfo requestInfo = new RequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = offset;
            requestInfo.requestToken = ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
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
                        if (!res.cdn_file_hashes.isEmpty()) {
                            if (cdnHashes == null) {
                                cdnHashes = new HashMap<>();
                            }
                            for (int a = 0; a < res.cdn_file_hashes.size(); a++) {
                                TLRPC.TL_cdnFileHash hash = res.cdn_file_hashes.get(a);
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
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    reuploadingCdn = false;
                                    if (error == null) {
                                        TLRPC.Vector vector = (TLRPC.Vector) response;
                                        if (!vector.objects.isEmpty()) {
                                            if (cdnHashes == null) {
                                                cdnHashes = new HashMap<>();
                                            }
                                            for (int a = 0; a < vector.objects.size(); a++) {
                                                TLRPC.TL_cdnFileHash hash = (TLRPC.TL_cdnFileHash) vector.objects.get(a);
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
                            }, null, null, 0, datacenter_id, ConnectionsManager.ConnectionTypeGeneric, true);
                        }
                    } else {
                        if (response instanceof TLRPC.TL_upload_file) {
                            requestInfo.response = (TLRPC.TL_upload_file) response;
                        } else if (response instanceof TLRPC.TL_upload_webFile) {
                            requestInfo.responseWeb = (TLRPC.TL_upload_webFile) response;
                        } else {
                            requestInfo.responseCdn = (TLRPC.TL_upload_cdnFile) response;
                        }
                        if (response != null) {
                            if (currentType == ConnectionsManager.FileTypeAudio) {
                                StatsController.getInstance().incrementReceivedBytesCount(response.networkType, StatsController.TYPE_AUDIOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypeVideo) {
                                StatsController.getInstance().incrementReceivedBytesCount(response.networkType, StatsController.TYPE_VIDEOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypePhoto) {
                                StatsController.getInstance().incrementReceivedBytesCount(response.networkType, StatsController.TYPE_PHOTOS, response.getObjectSize() + 4);
                            } else if (currentType == ConnectionsManager.FileTypeFile) {
                                StatsController.getInstance().incrementReceivedBytesCount(response.networkType, StatsController.TYPE_FILES, response.getObjectSize() + 4);
                            }
                        }
                        processRequestResult(requestInfo, error);
                    }
                }
            }, null, null, flags, isCdn ? cdnDatacenterId : datacenter_id, connectionType, isLast);
            requestsCount++;
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}
