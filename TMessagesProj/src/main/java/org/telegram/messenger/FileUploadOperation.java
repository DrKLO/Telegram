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
    private boolean isEncrypted = false;
    private int fingerprint = 0;
    private boolean isBigFile = false;
    private String fileKey;
    private int estimatedSize = 0;
    private int uploadStartTime = 0;
    private FileInputStream stream;
    private MessageDigest mdEnc = null;
    private boolean started = false;

    public static interface FileUploadOperationDelegate {
        public abstract void didFinishUploadingFile(FileUploadOperation operation, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile);
        public abstract void didFailedUploadingFile(FileUploadOperation operation);
        public abstract void didChangedUploadProgress(FileUploadOperation operation, float progress);
    }

    public FileUploadOperation(String location, boolean encrypted, int estimated) {
        uploadingFilePath = location;
        isEncrypted = encrypted;
        estimatedSize = estimated;
    }

    public void start() {
        if (state != 0) {
            return;
        }
        state = 1;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                startUploadRequest();
            }
        });
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
        preferences.edit().remove(fileKey + "_time").
                remove(fileKey + "_size").
                remove(fileKey + "_uploaded").
                remove(fileKey + "_id").
                remove(fileKey + "_iv").
                remove(fileKey + "_key").
                remove(fileKey + "_ivc").commit();
    }

    protected void checkNewDataAvailable(final long finalSize) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (estimatedSize != 0 && finalSize != 0) {
                    estimatedSize = 0;
                    totalFileSize = finalSize;
                    totalPartsCount = (int) Math.ceil((float) totalFileSize / (float) uploadChunkSize);
                    if (started) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
                        storeFileUploadInfo(preferences);
                    }
                }
                if (requestToken == 0) {
                    startUploadRequest();
                }
            }
        });
    }

    private void storeFileUploadInfo(SharedPreferences preferences) {
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

    private void startUploadRequest() {
        if (state != 1) {
            return;
        }

        TLObject finalRequest;

        try {
            started = true;
            if (stream == null) {
                File cacheFile = new File(uploadingFilePath);
                stream = new FileInputStream(cacheFile);
                if (estimatedSize != 0) {
                    totalFileSize = estimatedSize;
                } else {
                    totalFileSize = cacheFile.length();
                }
                if (totalFileSize > 10 * 1024 * 1024) {
                    isBigFile = true;
                } else {
                    try {
                        mdEnc = MessageDigest.getInstance("MD5");
                    } catch (NoSuchAlgorithmException e) {
                        FileLog.e("tmessages", e);
                    }
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

                fileKey = Utilities.MD5(uploadingFilePath + (isEncrypted ? "enc" : ""));
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
                long fileSize = preferences.getLong(fileKey + "_size", 0);
                uploadStartTime = (int)(System.currentTimeMillis() / 1000);
                boolean rewrite = false;
                if (estimatedSize == 0 && fileSize == totalFileSize) {
                    currentFileId = preferences.getLong(fileKey + "_id", 0);
                    int date = preferences.getInt(fileKey + "_time", 0);
                    long uploadedSize = preferences.getLong(fileKey + "_uploaded", 0);
                    if (isEncrypted) {
                        String ivString = preferences.getString(fileKey + "_iv", null);
                        String keyString = preferences.getString(fileKey + "_key", null);
                        if (ivString != null && keyString != null) {
                            key = Utilities.hexToBytes(keyString);
                            iv = Utilities.hexToBytes(ivString);
                            ivChange = new byte[32];
                            System.arraycopy(iv, 0, ivChange, 0, 32);
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
                                currentUploaded = uploadedSize;
                                currentPartNum = (int) (uploadedSize / uploadChunkSize);
                                if (!isBigFile) {
                                    for (int b = 0; b < currentUploaded / uploadChunkSize; b++) {
                                        int read = stream.read(readBuffer);
                                        int toAdd = 0;
                                        if (isEncrypted && read % 16 != 0) {
                                            toAdd += 16 - read % 16;
                                        }
                                        ByteBufferDesc sendBuffer = BuffersStorage.getInstance().getFreeBuffer(read + toAdd);
                                        if (read != uploadChunkSize || totalPartsCount == currentPartNum + 1) {
                                            isLastPart = true;
                                        }
                                        sendBuffer.writeRaw(readBuffer, 0, read);
                                        if (isEncrypted) {
                                            for (int a = 0; a < toAdd; a++) {
                                                sendBuffer.writeByte(0);
                                            }
                                            Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, read + toAdd);
                                        }
                                        sendBuffer.rewind();
                                        mdEnc.update(sendBuffer.buffer);
                                        BuffersStorage.getInstance().reuseFreeBuffer(sendBuffer);
                                    }
                                } else {
                                    stream.skip(uploadedSize);
                                    if (isEncrypted) {
                                        String ivcString = preferences.getString(fileKey + "_ivc", null);
                                        if (ivcString != null) {
                                            ivChange = Utilities.hexToBytes(ivcString);
                                        } else {
                                            rewrite = true;
                                            currentUploaded = 0;
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
                    if (estimatedSize == 0) {
                        storeFileUploadInfo(preferences);
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
                        FileLog.e("tmessages", e);
                    }
                }
            } else if (estimatedSize == 0) {
                if (saveInfoTimes >= 4) {
                    saveInfoTimes = 0;
                }
                if (isBigFile && currentUploaded % (1024 * 1024) == 0 || !isBigFile && saveInfoTimes == 0) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putLong(fileKey + "_uploaded", currentUploaded);
                    if (isEncrypted) {
                        editor.putString(fileKey + "_ivc", Utilities.bytesToHex(ivChange));
                    }
                    editor.commit();
                }
                saveInfoTimes++;
            }

            if (estimatedSize != 0) {
                long size = stream.getChannel().size();
                if (currentUploaded + uploadChunkSize > size) {
                    return;
                }
            }

            int read = stream.read(readBuffer);
            int toAdd = 0;
            if (isEncrypted && read % 16 != 0) {
                toAdd += 16 - read % 16;
            }
            ByteBufferDesc sendBuffer = BuffersStorage.getInstance().getFreeBuffer(read + toAdd);
            if (read != uploadChunkSize || estimatedSize == 0 && totalPartsCount == currentPartNum + 1) {
                isLastPart = true;
            }
            sendBuffer.writeRaw(readBuffer, 0, read);
            if (isEncrypted) {
                for (int a = 0; a < toAdd; a++) {
                    sendBuffer.writeByte(0);
                }
                Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, read + toAdd);
            }
            sendBuffer.rewind();
            if (!isBigFile) {
                mdEnc.update(sendBuffer.buffer);
            }
            if (isBigFile) {
                TLRPC.TL_upload_saveBigFilePart req = new TLRPC.TL_upload_saveBigFilePart();
                req.file_part = currentPartNum;
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
                req.file_part = currentPartNum;
                req.file_id = currentFileId;
                req.bytes = sendBuffer;
                finalRequest = req;
            }
            currentUploaded += read;
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
