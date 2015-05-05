/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.io.RandomAccessFile;
import java.io.File;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Scanner;

public class FileLoadOperation {

    private static class RequestInfo {
        private long requestToken = 0;
        private int offset = 0;
        private TLRPC.TL_upload_file response = null;
    }

    private final static int stateIdle = 0;
    private final static int stateDownloading = 1;
    private final static int stateFailed = 2;
    private final static int stateFinished = 3;

    private final static int downloadChunkSize = 1024 * 32;
    private final static int maxDownloadRequests = 3;

    private int datacenter_id;
    private TLRPC.InputFileLocation location;
    private volatile int state = stateIdle;
    private int downloadedBytes;
    private int totalBytesCount;
    private FileLoadOperationDelegate delegate;
    private byte[] key;
    private byte[] iv;

    private int nextDownloadOffset = 0;
    private ArrayList<RequestInfo> requestInfos = new ArrayList<>(maxDownloadRequests);
    private ArrayList<RequestInfo> delayedRequestInfos = new ArrayList<>(maxDownloadRequests - 1);

    private File cacheFileTemp;
    private File cacheFileFinal;
    private File cacheIvTemp;

    private String ext;
    private RandomAccessFile fileOutputStream;
    private RandomAccessFile fiv;
    private File storePath = null;
    private File tempPath = null;
    private boolean isForceRequest = false;

    public interface FileLoadOperationDelegate {
        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int state);
        void didChangedLoadProgress(FileLoadOperation operation, float progress);
    }

    public FileLoadOperation(TLRPC.FileLocation photoLocation, int size) {
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
        ext = photoLocation.ext;
        if (ext == null) {
            ext = "jpg";
        }
    }

    public FileLoadOperation(TLRPC.Video videoLocation) {
        if (videoLocation instanceof TLRPC.TL_videoEncrypted) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = videoLocation.id;
            location.access_hash = videoLocation.access_hash;
            datacenter_id = videoLocation.dc_id;
            iv = new byte[32];
            System.arraycopy(videoLocation.iv, 0, iv, 0, iv.length);
            key = videoLocation.key;
        } else if (videoLocation instanceof TLRPC.TL_video) {
            location = new TLRPC.TL_inputVideoFileLocation();
            datacenter_id = videoLocation.dc_id;
            location.id = videoLocation.id;
            location.access_hash = videoLocation.access_hash;
        }
        totalBytesCount = videoLocation.size;
        ext = ".mp4";
    }

    public FileLoadOperation(TLRPC.Audio audioLocation) {
        if (audioLocation instanceof TLRPC.TL_audioEncrypted) {
            location = new TLRPC.TL_inputEncryptedFileLocation();
            location.id = audioLocation.id;
            location.access_hash = audioLocation.access_hash;
            datacenter_id = audioLocation.dc_id;
            iv = new byte[32];
            System.arraycopy(audioLocation.iv, 0, iv, 0, iv.length);
            key = audioLocation.key;
        } else if (audioLocation instanceof TLRPC.TL_audio) {
            location = new TLRPC.TL_inputAudioFileLocation();
            datacenter_id = audioLocation.dc_id;
            location.id = audioLocation.id;
            location.access_hash = audioLocation.access_hash;
        }
        totalBytesCount = audioLocation.size;
        ext = ".ogg";
    }

    public FileLoadOperation(TLRPC.Document documentLocation) {
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
            datacenter_id = documentLocation.dc_id;
            location.id = documentLocation.id;
            location.access_hash = documentLocation.access_hash;
        }
        totalBytesCount = documentLocation.size;
        ext = FileLoader.getDocumentFileName(documentLocation);
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

    public void start() {
        if (state != stateIdle) {
            return;
        }
        state = stateDownloading;
        if (location == null) {
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                }
            });
            return;
        }
        Long mediaId = null;
        String fileNameFinal = null;
        String fileNameTemp = null;
        String fileNameIv = null;
        if (location.volume_id != 0 && location.local_id != 0) {
            fileNameTemp = location.volume_id + "_" + location.local_id + "_temp." + ext;
            fileNameFinal = location.volume_id + "_" + location.local_id + "." + ext;
            if (key != null) {
                fileNameIv = location.volume_id + "_" + location.local_id + ".iv";
            }
            if (datacenter_id == Integer.MIN_VALUE || location.volume_id == Integer.MIN_VALUE || datacenter_id == 0) {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                    }
                });
                return;
            }
        } else {
            fileNameTemp = datacenter_id + "_" + location.id + "_temp" + ext;
            fileNameFinal = datacenter_id + "_" + location.id + ext;
            if (key != null) {
                fileNameIv = datacenter_id + "_" + location.id + ".iv";
            }
            if (datacenter_id == 0 || location.id == 0) {
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                    }
                });
                return;
            }
        }

        cacheFileFinal = new File(storePath, fileNameFinal);
        boolean exist = cacheFileFinal.exists();
        if (exist && totalBytesCount != 0 && totalBytesCount != cacheFileFinal.length()) {
            exist = false;
            cacheFileFinal.delete();
        }

        if (!cacheFileFinal.exists()) {
            cacheFileTemp = new File(tempPath, fileNameTemp);
            if (cacheFileTemp.exists()) {
                downloadedBytes = (int)cacheFileTemp.length();
                nextDownloadOffset = downloadedBytes = downloadedBytes / 1024 * 1024;
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
                cleanup();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                    }
                });
                return;
            }
            Utilities.stageQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (totalBytesCount != 0 && downloadedBytes == totalBytesCount) {
                        try {
                            onFinishLoadingFile();
                        } catch (Exception e) {
                            delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                        }
                    } else {
                        startDownloadRequest();
                    }
                }
            });
        } else {
            try {
                onFinishLoadingFile();
            } catch (Exception e) {
                delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
            }
        }
    }

    public void cancel() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (state == stateFinished || state == stateFailed) {
                    return;
                }
                state = stateFailed;
                cleanup();
                for (RequestInfo requestInfo : requestInfos) {
                    if (requestInfo.requestToken != 0) {
                        ConnectionsManager.getInstance().cancelRpc(requestInfo.requestToken, true, true);
                    }
                }
                delegate.didFailedLoadingFile(FileLoadOperation.this, 1);
            }
        });
    }

    private void cleanup() {
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
        for (RequestInfo requestInfo : delayedRequestInfos) {
            if (requestInfo.response != null) {
                requestInfo.response.disableFree = false;
                requestInfo.response.freeResources();
            }
        }
        delayedRequestInfos.clear();
    }

    private void onFinishLoadingFile() throws Exception {
        if (state != stateDownloading) {
            return;
        }
        state = stateFinished;
        cleanup();
        if (cacheIvTemp != null) {
            cacheIvTemp.delete();
        }
        if (cacheFileTemp != null) {
            if (!cacheFileTemp.renameTo(cacheFileFinal)) {
                cacheFileFinal = cacheFileTemp;
            }
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
                if (key != null) {
                    Utilities.aesIgeEncryption(requestInfo.response.bytes.buffer, key, iv, false, true, 0, requestInfo.response.bytes.limit());
                }
                if (fileOutputStream != null) {
                    FileChannel channel = fileOutputStream.getChannel();
                    channel.write(requestInfo.response.bytes.buffer);
                }
                if (fiv != null) {
                    fiv.seek(0);
                    fiv.write(iv);
                }
                int currentBytesSize = requestInfo.response.bytes.limit();
                downloadedBytes += currentBytesSize;
                if (totalBytesCount > 0 && state == stateDownloading) {
                    delegate.didChangedLoadProgress(FileLoadOperation.this,  Math.min(1.0f, (float)downloadedBytes / (float)totalBytesCount));
                }

                for (int a = 0; a < delayedRequestInfos.size(); a++) {
                    RequestInfo delayedRequestInfo = delayedRequestInfos.get(a);
                    if (downloadedBytes == delayedRequestInfo.offset) {
                        delayedRequestInfos.remove(a);
                        processRequestResult(delayedRequestInfo, null);
                        delayedRequestInfo.response.disableFree = false;
                        delayedRequestInfo.response.freeResources();
                        delayedRequestInfo = null;
                        break;
                    }
                }

                if (currentBytesSize != downloadChunkSize) {
                    onFinishLoadingFile();
                } else {
                    if (totalBytesCount != downloadedBytes && downloadedBytes % downloadChunkSize == 0 || totalBytesCount > 0 && totalBytesCount > downloadedBytes) {
                        startDownloadRequest();
                    } else {
                        onFinishLoadingFile();
                    }
                }
            } catch (Exception e) {
                cleanup();
                delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
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
                    delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                } else {
                    datacenter_id = val;
                    nextDownloadOffset = 0;
                    startDownloadRequest();
                }
            } else if (error.text.contains("OFFSET_INVALID")) {
                if (downloadedBytes % downloadChunkSize == 0) {
                    try {
                        onFinishLoadingFile();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        cleanup();
                        delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                    }
                } else {
                    cleanup();
                    delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
                }
            } else if (error.text.contains("RETRY_LIMIT")) {
                cleanup();
                delegate.didFailedLoadingFile(FileLoadOperation.this, 2);
            } else {
                if (location != null) {
                    FileLog.e("tmessages", "" + location + " id = " + location.id + " access_hash = " + location.access_hash + " volume_id = " + location.local_id + " secret = " + location.secret);
                }
                cleanup();
                delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
            }
        }
    }

    private void startDownloadRequest() {
        if (state != stateDownloading || totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount || requestInfos.size() + delayedRequestInfos.size() >= maxDownloadRequests) {
            return;
        }
        int count = 1;
        if (totalBytesCount > 0) {
            count = Math.max(0, maxDownloadRequests - requestInfos.size() - delayedRequestInfos.size());
        }

        for (int a = 0; a < count; a++) {
            if (totalBytesCount > 0 && nextDownloadOffset >= totalBytesCount) {
                break;
            }
            boolean isLast = totalBytesCount <= 0 || a == count - 1 || totalBytesCount > 0 && nextDownloadOffset + downloadChunkSize >= totalBytesCount;
            TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
            req.location = location;
            req.offset = nextDownloadOffset;
            req.limit = downloadChunkSize;
            nextDownloadOffset += downloadChunkSize;

            final RequestInfo requestInfo = new RequestInfo();
            requestInfos.add(requestInfo);
            requestInfo.offset = req.offset;
            requestInfo.requestToken = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    requestInfo.response = (TLRPC.TL_upload_file) response;
                    processRequestResult(requestInfo, error);
                }
            }, null, true, RPCRequest.RPCRequestClassDownloadMedia | (isForceRequest ? RPCRequest.RPCRequestClassForceDownload : 0), datacenter_id, isLast);
        }
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }
}
