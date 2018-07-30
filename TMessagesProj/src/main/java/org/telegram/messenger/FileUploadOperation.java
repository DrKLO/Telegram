/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.WriteToSocketDelegate;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class FileUploadOperation {

    private class UploadCachedResult {
        private long bytesOffset;
        private byte[] iv;
    }

    private int currentAccount;
    private boolean isLastPart;
    private boolean nextPartFirst;
    private int operationGuid;
    private static final int minUploadChunkSize = 128;
    private static final int minUploadChunkSlowNetworkSize = 32;
    private static final int initialRequestsCount = 8;
    private static final int initialRequestsSlowNetworkCount = 1;
    private static final int maxUploadingKBytes = 1024 * 2;
    private static final int maxUploadingSlowNetworkKBytes = 32;
    private int maxRequestsCount;
    private int uploadChunkSize = 64 * 1024;
    private boolean slowNetwork;
    private ArrayList<byte[]> freeRequestIvs;
    private int requestNum;
    private String uploadingFilePath;
    private int state;
    private byte[] readBuffer;
    private FileUploadOperationDelegate delegate;
    private SparseIntArray requestTokens = new SparseIntArray();
    private int currentPartNum;
    private long currentFileId;
    private long totalFileSize;
    private int totalPartsCount;
    private long readBytesCount;
    private long uploadedBytesCount;
    private int saveInfoTimes;
    private byte[] key;
    private byte[] iv;
    private byte[] ivChange;
    private boolean isEncrypted;
    private int fingerprint;
    private boolean isBigFile;
    private String fileKey;
    private int estimatedSize;
    private int uploadStartTime;
    private RandomAccessFile stream;
    private boolean started;
    private int currentUploadRequetsCount;
    private SharedPreferences preferences;
    private int currentType;
    private int lastSavedPartNum;
    private long availableSize;
    private boolean uploadFirstPartLater;
    private SparseArray<UploadCachedResult> cachedResults = new SparseArray<>();

    public interface FileUploadOperationDelegate {
        void didFinishUploadingFile(FileUploadOperation operation, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv);
        void didFailedUploadingFile(FileUploadOperation operation);
        void didChangedUploadProgress(FileUploadOperation operation, float progress);
    }

    public FileUploadOperation(int instance, String location, boolean encrypted, int estimated, int type) {
        currentAccount = instance;
        uploadingFilePath = location;
        isEncrypted = encrypted;
        estimatedSize = estimated;
        currentType = type;
        uploadFirstPartLater = estimated != 0 && !isEncrypted;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public void setDelegate(FileUploadOperationDelegate fileUploadOperationDelegate) {
        delegate = fileUploadOperationDelegate;
    }

    public void start() {
        if (state != 0) {
            return;
        }
        state = 1;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
                slowNetwork = ConnectionsManager.isConnectionSlow();
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("start upload on slow network = " + slowNetwork);
                }
                for (int a = 0, count = (slowNetwork ? initialRequestsSlowNetworkCount : initialRequestsCount); a < count; a++) {
                    startUploadRequest();
                }
            }
        });
    }

    protected void onNetworkChanged(final boolean slow) {
        if (state != 1) {
            return;
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (slowNetwork != slow) {
                    slowNetwork = slow;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("network changed to slow = " + slowNetwork);
                    }
                    for (int a = 0; a < requestTokens.size(); a++) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(requestTokens.valueAt(a), true);
                    }
                    requestTokens.clear();
                    cleanup();
                    isLastPart = false;
                    nextPartFirst = false;
                    requestNum = 0;
                    currentPartNum = 0;
                    readBytesCount = 0;
                    uploadedBytesCount = 0;
                    saveInfoTimes = 0;
                    key = null;
                    iv = null;
                    ivChange = null;
                    currentUploadRequetsCount = 0;
                    lastSavedPartNum = 0;
                    uploadFirstPartLater = false;
                    cachedResults.clear();

                    operationGuid++;
                    for (int a = 0, count = (slowNetwork ? initialRequestsSlowNetworkCount : initialRequestsCount); a < count; a++) {
                        startUploadRequest();
                    }
                }
            }
        });
    }

    public void cancel() {
        if (state == 3) {
            return;
        }
        state = 2;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (int a = 0; a < requestTokens.size(); a++) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(requestTokens.valueAt(a), true);
                }
            }
        });
        delegate.didFailedUploadingFile(this);
        cleanup();
    }

    private void cleanup() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
        }
        preferences.edit().remove(fileKey + "_time").
                remove(fileKey + "_size").
                remove(fileKey + "_uploaded").
                remove(fileKey + "_id").
                remove(fileKey + "_iv").
                remove(fileKey + "_key").
                remove(fileKey + "_ivc").commit();
        try {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    protected void checkNewDataAvailable(final long newAvailableSize, final long finalSize) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (estimatedSize != 0 && finalSize != 0) {
                    estimatedSize = 0;
                    totalFileSize = finalSize;
                    calcTotalPartsCount();
                    if (!uploadFirstPartLater && started) {
                        storeFileUploadInfo();
                    }
                }
                availableSize = newAvailableSize;
                if (currentUploadRequetsCount < maxRequestsCount) {
                    startUploadRequest();
                }
            }
        });
    }

    private void storeFileUploadInfo() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(fileKey + "_time", uploadStartTime);
        editor.putLong(fileKey + "_size", totalFileSize);
        editor.putLong(fileKey + "_id", currentFileId);
        editor.remove(fileKey + "_uploaded");
        if (isEncrypted) {
            editor.putString(fileKey + "_iv", Utilities.bytesToHex(iv));
            editor.putString(fileKey + "_ivc", Utilities.bytesToHex(ivChange));
            editor.putString(fileKey + "_key", Utilities.bytesToHex(key));
        }
        editor.commit();
    }

    private void calcTotalPartsCount() {
        if (uploadFirstPartLater) {
            if (isBigFile) {
                totalPartsCount = 1 + (int) ((totalFileSize - uploadChunkSize) + uploadChunkSize - 1) / uploadChunkSize;
            } else {
                totalPartsCount = 1 + (int) ((totalFileSize - 1024) + uploadChunkSize - 1) / uploadChunkSize;
            }
        } else {
            totalPartsCount = (int) (totalFileSize + uploadChunkSize - 1) / uploadChunkSize;
        }
    }

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }

        final TLObject finalRequest;
        final int currentRequestPartNum;

        final int currentRequestBytes;
        final byte[] currentRequestIv;
        try {
            started = true;
            if (stream == null) {
                File cacheFile = new File(uploadingFilePath);
                if (AndroidUtilities.isInternalUri(Uri.fromFile(cacheFile))) {
                    throw new Exception("trying to upload internal file");
                }
                stream = new RandomAccessFile(cacheFile, "r");
                if (estimatedSize != 0) {
                    totalFileSize = estimatedSize;
                } else {
                    totalFileSize = cacheFile.length();
                }
                if (totalFileSize > 10 * 1024 * 1024) {
                    isBigFile = true;
                }

                uploadChunkSize = (int) Math.max(slowNetwork ? minUploadChunkSlowNetworkSize : minUploadChunkSize, (totalFileSize + 1024 * 3000 - 1) / (1024 * 3000));
                if (1024 % uploadChunkSize != 0) {
                    int chunkSize = 64;
                    while (uploadChunkSize > chunkSize) {
                        chunkSize *= 2;
                    }
                    uploadChunkSize = chunkSize;
                }
                maxRequestsCount = Math.max(1, (slowNetwork ? maxUploadingSlowNetworkKBytes : maxUploadingKBytes) / uploadChunkSize);

                if (isEncrypted) {
                    freeRequestIvs = new ArrayList<>(maxRequestsCount);
                    for (int a = 0; a < maxRequestsCount; a++) {
                        freeRequestIvs.add(new byte[32]);
                    }
                }

                uploadChunkSize *= 1024;
                calcTotalPartsCount();
                readBuffer = new byte[uploadChunkSize];

                fileKey = Utilities.MD5(uploadingFilePath + (isEncrypted ? "enc" : ""));
                long fileSize = preferences.getLong(fileKey + "_size", 0);
                uploadStartTime = (int)(System.currentTimeMillis() / 1000);
                boolean rewrite = false;
                if (!uploadFirstPartLater && !nextPartFirst && estimatedSize == 0 && fileSize == totalFileSize) {
                    currentFileId = preferences.getLong(fileKey + "_id", 0);
                    int date = preferences.getInt(fileKey + "_time", 0);
                    long uploadedSize = preferences.getLong(fileKey + "_uploaded", 0);
                    if (isEncrypted) {
                        String ivString = preferences.getString(fileKey + "_iv", null);
                        String keyString = preferences.getString(fileKey + "_key", null);
                        if (ivString != null && keyString != null) {
                            key = Utilities.hexToBytes(keyString);
                            iv = Utilities.hexToBytes(ivString);
                            if (key != null && iv != null && key.length == 32 && iv.length == 32) {
                                ivChange = new byte[32];
                                System.arraycopy(iv, 0, ivChange, 0, 32);
                            } else {
                                rewrite = true;
                            }
                        } else {
                            rewrite = true;
                        }
                    }
                    if (!rewrite && date != 0) {
                        if (isBigFile && date < uploadStartTime - 60 * 60 * 24) {
                            date = 0;
                        } else if (!isBigFile && date < uploadStartTime - 60 * 60 * 1.5f) {
                            date = 0;
                        }
                        if (date != 0) {
                            if (uploadedSize > 0) {
                                readBytesCount = uploadedSize;
                                currentPartNum = (int) (uploadedSize / uploadChunkSize);
                                if (!isBigFile) {
                                    for (int b = 0; b < readBytesCount / uploadChunkSize; b++) {
                                        int bytesRead = stream.read(readBuffer);
                                        int toAdd = 0;
                                        if (isEncrypted && bytesRead % 16 != 0) {
                                            toAdd += 16 - bytesRead % 16;
                                        }
                                        NativeByteBuffer sendBuffer = new NativeByteBuffer(bytesRead + toAdd);
                                        if (bytesRead != uploadChunkSize || totalPartsCount == currentPartNum + 1) {
                                            isLastPart = true;
                                        }
                                        sendBuffer.writeBytes(readBuffer, 0, bytesRead);
                                        if (isEncrypted) {
                                            for (int a = 0; a < toAdd; a++) {
                                                sendBuffer.writeByte(0);
                                            }
                                            Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, bytesRead + toAdd);
                                        }
                                        sendBuffer.reuse();
                                    }
                                } else {
                                    stream.seek(uploadedSize);
                                    if (isEncrypted) {
                                        String ivcString = preferences.getString(fileKey + "_ivc", null);
                                        if (ivcString != null) {
                                            ivChange = Utilities.hexToBytes(ivcString);
                                            if (ivChange == null || ivChange.length != 32) {
                                                rewrite = true;
                                                readBytesCount = 0;
                                                currentPartNum = 0;
                                            }
                                        } else {
                                            rewrite = true;
                                            readBytesCount = 0;
                                            currentPartNum = 0;
                                        }
                                    }
                                }
                            } else {
                                rewrite = true;
                            }
                        }
                    } else {
                        rewrite = true;
                    }
                } else {
                    rewrite = true;
                }
                if (rewrite) {
                    if (isEncrypted) {
                        iv = new byte[32];
                        key = new byte[32];
                        ivChange = new byte[32];
                        Utilities.random.nextBytes(iv);
                        Utilities.random.nextBytes(key);
                        System.arraycopy(iv, 0, ivChange, 0, 32);
                    }
                    currentFileId = Utilities.random.nextLong();
                    if (!nextPartFirst && !uploadFirstPartLater && estimatedSize == 0) {
                        storeFileUploadInfo();
                    }
                }

                if (isEncrypted) {
                    try {
                        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                        byte[] arr = new byte[64];
                        System.arraycopy(key, 0, arr, 0, 32);
                        System.arraycopy(iv, 0, arr, 32, 32);
                        byte[] digest = md.digest(arr);
                        for (int a = 0; a < 4; a++) {
                            fingerprint |= ((digest[a] ^ digest[a + 4]) & 0xFF) << (a * 8);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                uploadedBytesCount = readBytesCount;
                lastSavedPartNum = currentPartNum;

                if (uploadFirstPartLater) {
                    if (isBigFile) {
                        stream.seek(uploadChunkSize);
                        readBytesCount = uploadChunkSize;
                    } else {
                        stream.seek(1024);
                        readBytesCount = 1024;
                    }
                    currentPartNum = 1;
                }
            }

            if (estimatedSize != 0) {
                if (readBytesCount + uploadChunkSize > availableSize) {
                    return;
                }
            }

            if (nextPartFirst) {
                stream.seek(0);
                if (isBigFile) {
                    currentRequestBytes = stream.read(readBuffer);
                } else {
                    currentRequestBytes = stream.read(readBuffer, 0, 1024);
                }
                currentPartNum = 0;
            } else {
                currentRequestBytes = stream.read(readBuffer);
            }
            if (currentRequestBytes == -1) {
                return;
            }
            int toAdd = 0;
            if (isEncrypted && currentRequestBytes % 16 != 0) {
                toAdd += 16 - currentRequestBytes % 16;
            }
            NativeByteBuffer sendBuffer = new NativeByteBuffer(currentRequestBytes + toAdd);
            if (nextPartFirst || currentRequestBytes != uploadChunkSize || estimatedSize == 0 && totalPartsCount == currentPartNum + 1) {
                if (uploadFirstPartLater) {
                    nextPartFirst = true;
                    uploadFirstPartLater = false;
                } else {
                    isLastPart = true;
                }
            }
            sendBuffer.writeBytes(readBuffer, 0, currentRequestBytes);
            if (isEncrypted) {
                for (int a = 0; a < toAdd; a++) {
                    sendBuffer.writeByte(0);
                }
                Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, currentRequestBytes + toAdd);
                currentRequestIv = freeRequestIvs.get(0);
                System.arraycopy(ivChange, 0, currentRequestIv, 0, 32);
                freeRequestIvs.remove(0);
            } else {
                currentRequestIv = null;
            }
            if (isBigFile) {
                TLRPC.TL_upload_saveBigFilePart req = new TLRPC.TL_upload_saveBigFilePart();
                req.file_part = currentRequestPartNum = currentPartNum;
                req.file_id = currentFileId;
                if (estimatedSize != 0) {
                    req.file_total_parts = -1;
                } else {
                    req.file_total_parts = totalPartsCount;
                }
                req.bytes = sendBuffer;
                finalRequest = req;
            } else {
                TLRPC.TL_upload_saveFilePart req = new TLRPC.TL_upload_saveFilePart();
                req.file_part = currentRequestPartNum = currentPartNum;
                req.file_id = currentFileId;
                req.bytes = sendBuffer;
                finalRequest = req;
            }
            if (isLastPart && nextPartFirst) {
                nextPartFirst = false;
                currentPartNum = totalPartsCount - 1;
                stream.seek(totalFileSize);
            }
            readBytesCount += currentRequestBytes;
        } catch (Exception e) {
            FileLog.e(e);
            state = 4;
            delegate.didFailedUploadingFile(this);
            cleanup();
            return;
        }
        currentPartNum++;
        currentUploadRequetsCount++;
        final int requestNumFinal = requestNum++;
        final long currentRequestBytesOffset = currentRequestPartNum + currentRequestBytes;
        final int requestSize = finalRequest.getObjectSize() + 4;
        final int currentOperationGuid = operationGuid;

        int connectionType;
        if (slowNetwork) {
            connectionType = ConnectionsManager.ConnectionTypeUpload;
        } else {
            connectionType = ConnectionsManager.ConnectionTypeUpload | ((requestNumFinal % 4) << 16);
        }

        int requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(finalRequest, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (currentOperationGuid != operationGuid) {
                    return;
                }
                int networkType = response != null ? response.networkType : ConnectionsManager.getCurrentNetworkType();
                if (currentType == ConnectionsManager.FileTypeAudio) {
                    StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_AUDIOS, requestSize);
                } else if (currentType == ConnectionsManager.FileTypeVideo) {
                    StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_VIDEOS, requestSize);
                } else if (currentType == ConnectionsManager.FileTypePhoto) {
                    StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_PHOTOS, requestSize);
                } else if (currentType == ConnectionsManager.FileTypeFile) {
                    StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_FILES, requestSize);
                }
                if (currentRequestIv != null) {
                    freeRequestIvs.add(currentRequestIv);
                }
                requestTokens.delete(requestNumFinal);
                if (response instanceof TLRPC.TL_boolTrue) {
                    if (state != 1) {
                        return;
                    }
                    uploadedBytesCount += currentRequestBytes;
                    long size;
                    if (estimatedSize != 0) {
                        size = Math.max(availableSize, estimatedSize);
                    } else {
                        size = totalFileSize;
                    }
                    delegate.didChangedUploadProgress(FileUploadOperation.this, uploadedBytesCount / (float) size);
                    currentUploadRequetsCount--;
                    if (isLastPart && currentUploadRequetsCount == 0 && state == 1) {
                        state = 3;
                        if (key == null) {
                            TLRPC.InputFile result;
                            if (isBigFile) {
                                result = new TLRPC.TL_inputFileBig();
                            } else {
                                result = new TLRPC.TL_inputFile();
                                result.md5_checksum = "";
                            }
                            result.parts = currentPartNum;
                            result.id = currentFileId;
                            result.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1);
                            delegate.didFinishUploadingFile(FileUploadOperation.this, result, null, null, null);
                            cleanup();
                        } else {
                            TLRPC.InputEncryptedFile result;
                            if (isBigFile) {
                                result = new TLRPC.TL_inputEncryptedFileBigUploaded();
                            } else {
                                result = new TLRPC.TL_inputEncryptedFileUploaded();
                                result.md5_checksum = "";
                            }
                            result.parts = currentPartNum;
                            result.id = currentFileId;
                            result.key_fingerprint = fingerprint;
                            delegate.didFinishUploadingFile(FileUploadOperation.this, null, result, key, iv);
                            cleanup();
                        }
                        if (currentType == ConnectionsManager.FileTypeAudio) {
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_AUDIOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypeVideo) {
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_VIDEOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypePhoto) {
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_PHOTOS, 1);
                        } else if (currentType == ConnectionsManager.FileTypeFile) {
                            StatsController.getInstance(currentAccount).incrementSentItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_FILES, 1);
                        }
                    } else if (currentUploadRequetsCount < maxRequestsCount) {
                        if (estimatedSize == 0 && !uploadFirstPartLater && !nextPartFirst) {
                            if (saveInfoTimes >= 4) {
                                saveInfoTimes = 0;
                            }
                            if (currentRequestPartNum == lastSavedPartNum) {
                                lastSavedPartNum++;
                                long offsetToSave = currentRequestBytesOffset;
                                byte[] ivToSave = currentRequestIv;
                                UploadCachedResult result;
                                while ((result = cachedResults.get(lastSavedPartNum)) != null) {
                                    offsetToSave = result.bytesOffset;
                                    ivToSave = result.iv;
                                    cachedResults.remove(lastSavedPartNum);
                                    lastSavedPartNum++;
                                }
                                if (isBigFile && offsetToSave % (1024 * 1024) == 0 || !isBigFile && saveInfoTimes == 0) {
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putLong(fileKey + "_uploaded", offsetToSave);
                                    if (isEncrypted) {
                                        editor.putString(fileKey + "_ivc", Utilities.bytesToHex(ivToSave));
                                    }
                                    editor.commit();
                                }
                            } else {
                                UploadCachedResult result = new UploadCachedResult();
                                result.bytesOffset = currentRequestBytesOffset;
                                if (currentRequestIv != null) {
                                    result.iv = new byte[32];
                                    System.arraycopy(currentRequestIv, 0, result.iv, 0, 32);
                                }
                                cachedResults.put(currentRequestPartNum, result);
                            }
                            saveInfoTimes++;
                        }
                        startUploadRequest();
                    }
                } else {
                    if (finalRequest != null) {
                        FileLog.e("23123");
                    }
                    state = 4;
                    delegate.didFailedUploadingFile(FileUploadOperation.this);
                    cleanup();
                }
            }
        }, null, new WriteToSocketDelegate() {
            @Override
            public void run() {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (currentUploadRequetsCount < maxRequestsCount) {
                            startUploadRequest();
                        }
                    }
                });

            }
        }, 0, ConnectionsManager.DEFAULT_DATACENTER_ID, connectionType, true);
        requestTokens.put(requestNumFinal, requestToken);
    }
}
