/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.RandomAccessFile;
import java.io.File;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Scanner;

public class FileLoadOperation {

    private static class RequestInfo {
        private int requestToken;
        private int offset;
        private TLRPC.TL_upload_file response;
    }

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;

    private final static int downloadChunkSize = 1024 * 32;
    private final static int downloadChunkSizeBig = 1024 * 128;
    private final static int maxDownloadRequests = 4;
    private final static int maxDownloadRequestsBig = 2;
    private final static int bigFileSizeFrom = 1024 * 1024;

    private boolean started;
    private int datacenter_id;
    private TLRPC.InputFileLocation location;
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
        totalBytesCount = size;
        ext = extension != null ? extension : "jpg";
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
            FileLog.e("tmessages", e);
            onFail(true, 0);
        }
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

    public String getFileName() {
        return location.volume_id + "_" + location.local_id + "." + ext;
    }

    public boolean start() {
        if (state != stateIdle) {
            return false;
        }
        if (location == null) {
            onFail(true, 0);
            return false;
        }

        String fileNameFinal;
        String fileNameTemp;
        String fileNameIv = null;
        if (location.volume_id != 0 && location.local_id != 0) {
            if (datacenter_id == Integer.MIN_VALUE || location.volume_id == Integer.MIN_VALUE || datacenter_id == 0) {
                onFail(true, 0);
                return false;
            }

            fileNameTemp = location.volume_id + "_" + location.local_id + ".temp";
            fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext;
            if (key != null) {
                fileNameIv = location.volume_id + "_" + location.local_id + ".iv";
            }
        } else {
            if (datacenter_id == 0 || location.id == 0) {
                onFail(true, 0);
                return false;
            }

            fileNameTemp = datacenter_id + "_" + location.id + ".temp";
            fileNameFinal = datacenter_id + "_" + location.id + ext;
            if (key != null) {
                fileNameIv = datacenter_id + "_" + location.id + ".iv";
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
            if (cacheFileTemp.exists()) {
                downloadedBytes = (int) cacheFileTemp.length();
                nextDownloadOffset = downloadedBytes = downloadedBytes / currentDownloadChunkSize * currentDownloadChunkSize;
            }

            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("tmessages", "start loading file to temp = " + cacheFileTemp + " final = " + cacheFileFinal);
            }

            if (fileNameIv != null) {
                cacheIvTemp = new File(tempPath, fileNameIv);
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
            try {
                fileOutputStream = new RandomAccessFile(cacheFileTemp, "rws");
                if (downloadedBytes != 0) {
                    fileOutputStream.seek(downloadedBytes);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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
                            onFinishLoadingFile();
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
                onFinishLoadingFile();
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
                    FileLog.e("tmessages", e);
                }
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
        if (delayedRequestInfos != null) {
            for (int a = 0; a < delayedRequestInfos.size(); a++) {
                RequestInfo requestInfo = delayedRequestInfos.get(a);
                if (requestInfo.response != null) {
                    requestInfo.response.disableFree = false;
                    requestInfo.response.freeResources();
                }
            }
            delayedRequestInfos.clear();
        }
    }

    private void onFinishLoadingFile() throws Exception {
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
                    FileLog.e("tmessages", "unable to rename temp = " + cacheFileTemp + " to final = " + cacheFileFinal + " retry = " + renameRetryCount);
                }
                renameRetryCount++;
                if (renameRetryCount < 3) {
                    state = stateDownloading;
                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onFinishLoadingFile();
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
            FileLog.e("tmessages", "finished downloading file to " + cacheFileFinal);
        }
        delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
    }

    private void processRequestResult(RequestInfo requestInfo, TLRPC.TL_error error) {
        requestInfos.remove(requestInfo);
        if (error == null) {
            try {
                if (downloadedBytes != requestInfo.offset) {
                    if (state == stateDownloading) {
                        delayedRequestInfos.add(requestInfo);
                        requestInfo.response.disableFree = true;
                    }
                    return;
                }

                if (requestInfo.response.bytes == null || requestInfo.response.bytes.limit() == 0) {
                    onFinishLoadingFile();
                    return;
                }
                int currentBytesSize = requestInfo.response.bytes.limit();
                downloadedBytes += currentBytesSize;
                boolean finishedDownloading = currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes);

                if (key != null) {
                    Utilities.aesIgeEncryption(requestInfo.response.bytes.buffer, key, iv, false, true, 0, requestInfo.response.bytes.limit());
                    if (finishedDownloading && bytesCountPadding != 0) {
                        requestInfo.response.bytes.limit(requestInfo.response.bytes.limit() - bytesCountPadding);
                    }
                }
                if (fileOutputStream != null) {
                    FileChannel channel = fileOutputStream.getChannel();
                    channel.write(requestInfo.response.bytes.buffer);
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
                        processRequestResult(delayedRequestInfo, null);
                        delayedRequestInfo.response.disableFree = false;
                        delayedRequestInfo.response.freeResources();
                        break;
                    }
                }

                if (finishedDownloading) {
                    onFinishLoadingFile();
                } else {
                    startDownloadRequest();
                }
            } catch (Exception e) {
                onFail(false, 0);
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
                    onFail(false, 0);
                } else {
                    datacenter_id = val;
                    nextDownloadOffset = 0;
                    startDownloadRequest();
                }
            } else if (error.text.contains("OFFSET_INVALID")) {
                if (downloadedBytes % currentDownloadChunkSize == 0) {
                    try {
                        onFinishLoadingFile();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        onFail(false, 0);
                    }
                } else {
                    onFail(false, 0);
                }
            } else if (error.text.contains("RETRY_LIMIT")) {
                onFail(false, 2);
            } else {
                if (location != null) {
                    FileLog.e("tmessages", "" + location + " id = " + location.id + " local_id = " + location.local_id + " access_hash = " + location.access_hash + " volume_id = " + location.volume_id + " secret = " + location.secret);
                }
                onFail(false, 0);
            }
        }
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

    private void startDownloadRequest() {
        if (state != stateDownloading || totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount || requestInfos.size() + delayedRequestInfos.size() >= currentMaxDownloadRequests) {
            return;
        }
        int count = 1;
        if (totalBytesCount > 0) {
            count = Math.max(0, currentMaxDownloadRequests - requestInfos.size()/* - delayedRequestInfos.size()*/);
        }

        for (int a = 0; a < count; a++) {
            if (totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount) {
                break;
            }
            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && nextDownloadOffset + currentDownloadChunkSize >= totalBytesCount;
            TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
            req.location = location;
            req.offset = nextDownloadOffset;
            req.limit = currentDownloadChunkSize;
            nextDownloadOffset += currentDownloadChunkSize;

            final RequestInfo requestInfo = new RequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = req.offset;
            requestInfo.requestToken = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    requestInfo.response = (TLRPC.TL_upload_file) response;
                    processRequestResult(requestInfo, error);
                }
            }, null, (isForceRequest ? ConnectionsManager.RequestFlagForceDownload : 0) | ConnectionsManager.RequestFlagFailOnServerErrors, datacenter_id, requestsCount % 2 == 0 ? ConnectionsManager.ConnectionTypeDownload : ConnectionsManager.ConnectionTypeDownload2, isLast);
            requestsCount++;
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}
