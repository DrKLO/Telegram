/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Log;

import org.telegram.tgnet.TLRPC;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class FileStreamLoadOperation extends BaseDataSource implements FileLoadOperationStream {

    private FileLoadOperation loadOperation;

    private Uri uri;
    private long bytesRemaining;
    private boolean opened;
    private long currentOffset;
    private CountDownLatch countDownLatch;
    private RandomAccessFile file;
    private TLRPC.Document document;
    private Object parentObject;
    private int currentAccount;
    File currentFile;

    private static final ConcurrentHashMap<Long, Integer> priorityMap = new ConcurrentHashMap<>();

    public FileStreamLoadOperation() {
        super(/* isNetwork= */ false);
    }

    @Deprecated
    public FileStreamLoadOperation(@Nullable TransferListener listener) {
        this();
        if (listener != null) {
            addTransferListener(listener);
        }
    }

    public static int getStreamPrioriy(TLRPC.Document document) {
        if (document == null) {
            return FileLoader.PRIORITY_HIGH;
        }
        Integer integer = priorityMap.get(document.id);
        if (integer == null) {
            return FileLoader.PRIORITY_HIGH;
        }
        return integer;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);
        currentAccount = Utilities.parseInt(uri.getQueryParameter("account"));
        parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(uri.getQueryParameter("rid")));
        document = new TLRPC.TL_document();
        document.access_hash = Utilities.parseLong(uri.getQueryParameter("hash"));
        document.id = Utilities.parseLong(uri.getQueryParameter("id"));
        document.size = Utilities.parseLong(uri.getQueryParameter("size"));
        document.dc_id = Utilities.parseInt(uri.getQueryParameter("dc"));
        document.mime_type = uri.getQueryParameter("mime");
        document.file_reference = Utilities.hexToBytes(uri.getQueryParameter("reference"));
        TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
        filename.file_name = uri.getQueryParameter("name");
        document.attributes.add(filename);
        if (document.mime_type.startsWith("video")) {
            document.attributes.add(new TLRPC.TL_documentAttributeVideo());
        } else if (document.mime_type.startsWith("audio")) {
            document.attributes.add(new TLRPC.TL_documentAttributeAudio());
        }
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null, parentObject, currentOffset = dataSpec.position, false, getCurrentPriority());
        bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? document.size - dataSpec.position : dataSpec.length;
        if (bytesRemaining < 0) {
            throw new EOFException();
        }
        opened = true;
        transferStarted(dataSpec);
        if (loadOperation != null) {
            currentFile = loadOperation.getCurrentFile();
            if (currentFile != null) {
                try {
                    file = new RandomAccessFile(currentFile, "r");
                    file.seek(currentOffset);
                } catch (Throwable e) {
                }
            }
        }
        return bytesRemaining;
    }

    private int getCurrentPriority() {
        Integer priority = priorityMap.getOrDefault(document.id, null);
        if (priority != null) {
            return priority;
        }
        return FileLoader.PRIORITY_HIGH;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int availableLength = 0;
            int bytesRead;
            try {
                if (bytesRemaining < readLength) {
                    readLength = (int) bytesRemaining;
                }
                while ((availableLength == 0 && opened) || file == null) {
                    availableLength = (int) loadOperation.getDownloadedLengthFromOffset(currentOffset, readLength)[0];
                    if (availableLength == 0) {
                        countDownLatch = new CountDownLatch(1);
                        FileLoadOperation loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null, parentObject, currentOffset, false, getCurrentPriority());
                        if (this.loadOperation != loadOperation) {
                            this.loadOperation.removeStreamListener(this);
                            this.loadOperation = loadOperation;
                        }
                        if (countDownLatch != null) {
                            countDownLatch.await();
                            countDownLatch = null;
                        }
                    }
                    File currentFileFast = loadOperation.getCurrentFileFast();
                    if (file == null || !Objects.equals(currentFile, currentFileFast)) {
                        if (file != null) {
                            try {
                                file.close();
                            } catch (Exception ignore) {

                            }
                        }
                        currentFile = currentFileFast;
                        if (currentFile != null) {
                            try {
                                file = new RandomAccessFile(currentFile, "r");
                                file.seek(currentOffset);
                            } catch (Throwable e) {

                            }
                        }
                    }
                }
                if (!opened) {
                    return 0;
                }
                bytesRead = file.read(buffer, offset, availableLength);
                if (bytesRead > 0) {
                    currentOffset += bytesRead;
                    bytesRemaining -= bytesRead;
                    bytesTransferred(bytesRead);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
            return bytesRead;
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        if (loadOperation != null) {
            loadOperation.removeStreamListener(this);
        }
        if (file != null) {
            try {
                file.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
            file = null;
        }
        uri = null;
        if (opened) {
            opened = false;
            transferEnded();
        }
        if (countDownLatch != null) {
         //   FileLog.d("FileStreamLoadOperation count down");
            countDownLatch.countDown();
            countDownLatch = null;
        }
    }

    @Override
    public void newDataAvailable() {
        if (countDownLatch != null) {
            countDownLatch.countDown();
            countDownLatch = null;
        }
    }

    public static void setPriorityForDocument(TLRPC.Document document, int priority) {
        if (document != null) {
            priorityMap.put(document.id, priority);
        }
    }
}
