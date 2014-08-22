/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class FileUploadOperation {
    private int uploadChunkSize = 1024 * 32;
    private String uploadingFilePath;
    public int state = 0;
    private byte[] readBuffer;
    public FileUploadOperationDelegate delegate;
    private long requestToken = 0;
    private int currentPartNum = 0;
    private long currentFileId;
    private boolean isLastPart = false;
    private long totalFileSize = 0;
    private int totalPartsCount = 0;
    private long currentUploaded = 0;
    private int saveInfoTimes = 0;
    private byte[] key;
    private byte[] iv;
    private byte[] ivChange;
    private int fingerprint = 0;
    private boolean isBigFile = false;
    private String fileKey;
    FileInputStream stream;
    MessageDigest mdEnc = null;

    public static interface FileUploadOperationDelegate {
        public abstract void didFinishUploadingFile(FileUploadOperation operation, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile);
        public abstract void didFailedUploadingFile(FileUploadOperation operation);
        public abstract void didChangedUploadProgress(FileUploadOperation operation, float progress);
    }

    public FileUploadOperation(String location, boolean encrypted) {
        uploadingFilePath = location;
        if (encrypted) {
            iv = new byte[32];
            key = new byte[32];
            ivChange = new byte[32];
            Utilities.random.nextBytes(iv);
            Utilities.random.nextBytes(key);
            System.arraycopy(iv, 0, ivChange, 0, 32);
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
                FileLog.e("tmessages", e);
            }
        }
        currentFileId = Utilities.random.nextLong();
        try {
            mdEnc = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            FileLog.e("tmessages", e);
        }
    }

    public void start() {
        if (state != 0) {
            return;
        }
        state = 1;
        startUploadRequest();
    }

    public void cancel() {
        if (state != 1) {
            return;
        }
        state = 2;
        if (requestToken != 0) {
            ConnectionsManager.getInstance().cancelRpc(requestToken, true);
        }
        delegate.didFailedUploadingFile(this);
        cleanup();
    }

    private void cleanup() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
        preferences.edit().remove(fileKey + "_time").remove(fileKey + "_size").remove(fileKey + "_uploaded").commit();
    }

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }

        TLObject finalRequest;

        try {
            if (stream == null) {
                File cacheFile = new File(uploadingFilePath);
                stream = new FileInputStream(cacheFile);
                totalFileSize = cacheFile.length();
                if (totalFileSize > 10 * 1024 * 1024) {
                    isBigFile = true;
                }

                uploadChunkSize = (int) Math.max(32, Math.ceil(totalFileSize / (1024.0f * 3000)));
                if (1024 % uploadChunkSize != 0) {
                    int chunkSize = 64;
                    while (uploadChunkSize > chunkSize) {
                        chunkSize *= 2;
                    }
                    uploadChunkSize = chunkSize;
                }

                uploadChunkSize *= 1024;
                totalPartsCount = (int) Math.ceil((float) totalFileSize / (float) uploadChunkSize);
                readBuffer = new byte[uploadChunkSize];

                fileKey = Utilities.MD5(uploadingFilePath);
                /*SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE); TODO
                long fileSize = preferences.getLong(fileKey + "_size", 0);
                int currentTime = (int)(System.currentTimeMillis() / 1000);
                boolean rewrite = false;
                if (fileSize == totalFileSize) {
                    int date = preferences.getInt(fileKey + "_time", 0);
                    long uploadedSize = preferences.getLong(fileKey + "_uploaded", 0);
                    if (date != 0) {
                        if (isBigFile && date < currentTime - 60 * 60 * 24) {
                            date = 0;
                        } else if (!isBigFile && date < currentTime - 60 * 60 * 1.5f) {
                            date = 0;
                        }
                        if (date != 0) {
                            if (isBigFile) {
                                uploadedSize = uploadedSize / (1024 * 1024) * (1024 * 1024);
                            }
                            if (uploadedSize > 0) {
                                currentUploaded = uploadedSize;
                                stream.skip(uploadedSize);
                                currentPartNum = (int) (uploadedSize / uploadChunkSize);
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
                    preferences.edit().putInt(fileKey + "_time", currentTime).putLong(fileKey + "_size", totalFileSize).commit();
                }*/
            } else {
                /*if (saveInfoTimes >= 4) {
                    saveInfoTimes = 0;
                }
                if (saveInfoTimes == 0) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
                    preferences.edit().putLong(fileKey + "_uploaded", currentUploaded).commit();
                }
                saveInfoTimes++;*/
            }

            int readed = stream.read(readBuffer);
            int toAdd = 0;
            if (key != null && readed % 16 != 0) {
                toAdd += 16 - readed % 16;
            }
            ByteBufferDesc sendBuffer = BuffersStorage.getInstance().getFreeBuffer(readed + toAdd);
            if (readed != uploadChunkSize || totalPartsCount == currentPartNum + 1) {
                isLastPart = true;
            }
            sendBuffer.writeRaw(readBuffer, 0, readed);
            if (key != null) {
                for (int a = 0; a < toAdd; a++) {
                    sendBuffer.writeByte(0);
                }
                Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, readed + toAdd);
            }
            sendBuffer.rewind();
            if (!isBigFile) {
                mdEnc.update(sendBuffer.buffer);
            }
            if (isBigFile) {
                TLRPC.TL_upload_saveBigFilePart req = new TLRPC.TL_upload_saveBigFilePart();
                req.file_part = currentPartNum;
                req.file_id = currentFileId;
                req.file_total_parts = totalPartsCount;
                req.bytes = sendBuffer;
                finalRequest = req;
            } else {
                TLRPC.TL_upload_saveFilePart req = new TLRPC.TL_upload_saveFilePart();
                req.file_part = currentPartNum;
                req.file_id = currentFileId;
                req.bytes = sendBuffer;
                finalRequest = req;
            }
            currentUploaded += readed;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            delegate.didFailedUploadingFile(this);
            cleanup();
            return;
        }
        requestToken = ConnectionsManager.getInstance().performRpc(finalRequest, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                requestToken = 0;
                if (error == null) {
                    if (response instanceof TLRPC.TL_boolTrue) {
                        currentPartNum++;
                        delegate.didChangedUploadProgress(FileUploadOperation.this, (float) currentUploaded / (float) totalFileSize);
                        if (isLastPart) {
                            state = 3;
                            if (key == null) {
                                TLRPC.InputFile result;
                                if (isBigFile) {
                                    result = new TLRPC.TL_inputFileBig();
                                } else {
                                    result = new TLRPC.TL_inputFile();
                                    result.md5_checksum = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');
                                }
                                result.parts = currentPartNum;
                                result.id = currentFileId;
                                result.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1);
                                delegate.didFinishUploadingFile(FileUploadOperation.this, result, null);
                                cleanup();
                            } else {
                                TLRPC.InputEncryptedFile result;
                                if (isBigFile) {
                                    result = new TLRPC.TL_inputEncryptedFileBigUploaded();
                                } else {
                                    result = new TLRPC.TL_inputEncryptedFileUploaded();
                                    result.md5_checksum = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');
                                }
                                result.parts = currentPartNum;
                                result.id = currentFileId;
                                result.key_fingerprint = fingerprint;
                                result.iv = iv;
                                result.key = key;
                                delegate.didFinishUploadingFile(FileUploadOperation.this, null, result);
                                cleanup();
                            }
                        } else {
                            startUploadRequest();
                        }
                    } else {
                        delegate.didFailedUploadingFile(FileUploadOperation.this);
                        cleanup();
                    }
                } else {
                    delegate.didFailedUploadingFile(FileUploadOperation.this);
                    cleanup();
                }
            }
        }, null, true, RPCRequest.RPCRequestClassUploadMedia, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }
}
