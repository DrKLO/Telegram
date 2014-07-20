/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

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
    private byte[] key;
    private byte[] iv;
    private byte[] ivChange;
    private int fingerprint = 0;
    private boolean isBigFile = false;
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
                    FileLog.e("tmessages", "file is big!");
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
                            }
                        } else {
                            startUploadRequest();
                        }
                    } else {
                        delegate.didFailedUploadingFile(FileUploadOperation.this);
                    }
                } else {
                    delegate.didFailedUploadingFile(FileUploadOperation.this);
                }
            }
        }, null, true, RPCRequest.RPCRequestClassUploadMedia, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }
}
