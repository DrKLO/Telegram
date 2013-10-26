/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;

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
    private long currentUploaded = 0;
    private byte[] key;
    private byte[] iv;
    private int fingerprint;
    FileInputStream stream;
    MessageDigest mdEnc = null;

    public static interface FileUploadOperationDelegate {
        public abstract void didFinishUploadingFile(FileUploadOperation operation, TLRPC.TL_inputFile inputFile, TLRPC.TL_inputEncryptedFileUploaded inputEncryptedFile);
        public abstract void didFailedUploadingFile(FileUploadOperation operation);
        public abstract void didChangedUploadProgress(FileUploadOperation operation, float progress);
    }

    public FileUploadOperation(String location, byte[] keyarr, byte[] ivarr) {
        uploadingFilePath = location;
        if (ivarr != null && keyarr != null) {
            iv = new byte[32];
            key = keyarr;
            System.arraycopy(ivarr, 0, iv, 0, 32);
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] arr = new byte[64];
                System.arraycopy(key, 0, arr, 0, 32);
                System.arraycopy(iv, 0, arr, 32, 32);
                byte[] digest = md.digest(arr);
                byte[] fingerprintBytes = new byte[4];
                for (int a = 0; a < 4; a++) {
                    fingerprintBytes[a] = (byte)(digest[a] ^ digest[a + 4]);
                }
                fingerprint = Utilities.bytesToInt(fingerprintBytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        currentFileId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);
        try {
            mdEnc = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
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
            ConnectionsManager.Instance.cancelRpc(requestToken, true);
        }
        delegate.didFailedUploadingFile(this);
    }

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }

        TLRPC.TL_upload_saveFilePart req = new TLRPC.TL_upload_saveFilePart();
        req.file_part = currentPartNum;
        req.file_id = currentFileId;
        try {
            if (stream == null) {
                File cacheFile = new File(uploadingFilePath);
                stream = new FileInputStream(cacheFile);
                totalFileSize = cacheFile.length();

                uploadChunkSize = (int)Math.max(32, Math.ceil(totalFileSize / (1024.0f * 3000))) * 1024;
                readBuffer = new byte[uploadChunkSize];
            }
            int readed = stream.read(readBuffer);
            int toAdd = 0;
            if (key != null && readed % 16 != 0) {
                toAdd += 16 - readed % 16;
            }
            byte[] sendBuffer = new byte[readed + toAdd];
            if (readed != uploadChunkSize) {
                isLastPart = true;
            }
            System.arraycopy(readBuffer, 0, sendBuffer, 0, readed);
            if (key != null) {
                sendBuffer = Utilities.aesIgeEncryption(sendBuffer, key, iv, true, true);
            }
            mdEnc.update(sendBuffer, 0, readed + toAdd);
            req.bytes = sendBuffer;
            currentUploaded += readed;
        } catch (Exception e) {
            e.printStackTrace();
            delegate.didFailedUploadingFile(this);
            return;
        }
        requestToken = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        requestToken = 0;
                        if (error == null) {
                            if (response instanceof TLRPC.TL_boolTrue) {
                                currentPartNum++;
                                delegate.didChangedUploadProgress(FileUploadOperation.this, (float)currentUploaded / (float)totalFileSize);
                                if (isLastPart) {
                                    state = 3;
                                    if (key == null) {
                                        TLRPC.TL_inputFile result = new TLRPC.TL_inputFile();
                                        result.md5_checksum = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');
                                        result.parts = currentPartNum;
                                        result.id = currentFileId;
                                        result.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1);
                                        delegate.didFinishUploadingFile(FileUploadOperation.this, result, null);
                                    } else {
                                        TLRPC.TL_inputEncryptedFileUploaded result = new TLRPC.TL_inputEncryptedFileUploaded();
                                        result.md5_checksum = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');
                                        result.parts = currentPartNum;
                                        result.id = currentFileId;
                                        result.key_fingerprint = fingerprint;
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
                }, new RPCRequest.RPCProgressDelegate() {
                    @Override
                    public void progress(int length, int progress) {

                    }
                }, null, true, RPCRequest.RPCRequestClassUploadMedia, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }
}
