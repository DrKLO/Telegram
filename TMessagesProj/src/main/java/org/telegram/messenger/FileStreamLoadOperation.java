/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.telegram.tgnet.TLRPC;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;

public class FileStreamLoadOperation implements DataSource {

    private final TransferListener listener;
    private FileLoadOperation loadOperation;

    private Uri uri;
    private DataSpec dataSpec;
    private long bytesRemaining;
    private boolean opened;
    private int currentOffset;
    private CountDownLatch countDownLatch;
    private RandomAccessFile file;
    private TLRPC.Document document;
    private int currentAccount;

    public FileStreamLoadOperation() {
        this(null);
    }

    public FileStreamLoadOperation(TransferListener listener) {
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        this.dataSpec = dataSpec;
        currentAccount = Utilities.parseInt(uri.getQueryParameter("account"));
        document = new TLRPC.TL_document();
        document.access_hash = Utilities.parseLong(uri.getQueryParameter("hash"));
        document.id = Utilities.parseLong(uri.getQueryParameter("id"));
        document.size = Utilities.parseInt(uri.getQueryParameter("size"));
        document.dc_id = Utilities.parseInt(uri.getQueryParameter("dc"));
        document.mime_type = uri.getQueryParameter("mime");
        TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
        filename.file_name = uri.getQueryParameter("name");
        document.attributes.add(filename);
        if (document.mime_type.startsWith("video")) {
            document.attributes.add(new TLRPC.TL_documentAttributeVideo());
        } else if (document.mime_type.startsWith("audio")) {
            document.attributes.add(new TLRPC.TL_documentAttributeAudio());
        }
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, currentOffset = (int) dataSpec.position);
        bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? document.size - dataSpec.position : dataSpec.length;
        if (bytesRemaining < 0) {
            throw new EOFException();
        }
        opened = true;
        if (listener != null) {
            listener.onTransferStart(this, dataSpec, false);
        }
        file = new RandomAccessFile(loadOperation.getCurrentFile(), "r");
        file.seek(currentOffset);
        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int availableLength = 0;
            try {
                if (bytesRemaining < readLength) {
                    readLength = (int) bytesRemaining;
                }
                while (availableLength == 0) {
                    availableLength = loadOperation.getDownloadedLengthFromOffset(currentOffset, readLength);
                    if (availableLength == 0) {
                        if (loadOperation.isPaused()) {
                            FileLoader.getInstance(currentAccount).loadStreamFile(this, document, currentOffset);
                        }
                        countDownLatch = new CountDownLatch(1);
                        countDownLatch.await();
                    }
                }
                file.readFully(buffer, offset, availableLength);
                currentOffset += availableLength;
                bytesRemaining -= availableLength;
                if (listener != null) {
                    listener.onBytesTransferred(this, dataSpec, false, availableLength);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
            return availableLength;
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
        if (countDownLatch != null) {
            countDownLatch.countDown();
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
            if (listener != null) {
                listener.onTransferEnd(this, dataSpec, false);
            }
        }
    }

    protected void newDataAvailable() {
        if (countDownLatch != null) {
            countDownLatch.countDown();
        }
    }
}
