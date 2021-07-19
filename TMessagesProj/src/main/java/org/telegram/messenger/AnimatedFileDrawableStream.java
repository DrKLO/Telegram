package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CountDownLatch;

public class AnimatedFileDrawableStream implements FileLoadOperationStream {

    private FileLoadOperation loadOperation;
    private CountDownLatch countDownLatch;
    private TLRPC.Document document;
    private ImageLocation location;
    private Object parentObject;
    private int currentAccount;
    private volatile boolean canceled;
    private final Object sync = new Object();
    private int lastOffset;
    private boolean waitingForLoad;
    private boolean preview;
    private boolean finishedLoadingFile;
    private String finishedFilePath;

    private boolean ignored;

    public AnimatedFileDrawableStream(TLRPC.Document d, ImageLocation l, Object p, int a, boolean prev) {
        document = d;
        location = l;
        parentObject = p;
        currentAccount = a;
        preview = prev;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, preview);
    }

    public boolean isFinishedLoadingFile() {
        return finishedLoadingFile;
    }

    public String getFinishedFilePath() {
        return finishedFilePath;
    }

    public int read(int offset, int readLength) {
        synchronized (sync) {
            if (canceled) {
                return 0;
            }
        }
        if (readLength == 0) {
            return 0;
        } else {
            int availableLength = 0;
            try {
                while (availableLength == 0) {
                    int[] result = loadOperation.getDownloadedLengthFromOffset(offset, readLength);
                    availableLength = result[0];
                    if (!finishedLoadingFile && result[1] != 0) {
                        finishedLoadingFile = true;
                        finishedFilePath = loadOperation.getCacheFileFinal().getAbsolutePath();
                    }
                    if (availableLength == 0) {
                        if (loadOperation.isPaused() || lastOffset != offset || preview) {
                            FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset, preview);
                        }
                        synchronized (sync) {
                            if (canceled) {
                                return 0;
                            }
                            countDownLatch = new CountDownLatch(1);
                        }
                        if (!preview) {
                            FileLoader.getInstance(currentAccount).setLoadingVideo(document, false, true);
                        }
                        waitingForLoad = true;
                        countDownLatch.await();
                        waitingForLoad = false;
                    }
                }
                lastOffset = offset + availableLength;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return availableLength;
        }
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean removeLoading) {
        synchronized (sync) {
            if (countDownLatch != null) {
                countDownLatch.countDown();
                if (removeLoading && !canceled && !preview) {
                    FileLoader.getInstance(currentAccount).removeLoadingVideo(document, false, true);
                }
            }
            canceled = true;
        }
    }

    public void reset() {
        synchronized (sync) {
            canceled = false;
        }
    }

    public TLRPC.Document getDocument() {
        return document;
    }

    public ImageLocation getLocation() {
        return location;
    }

    public Object getParentObject() {
        return document;
    }

    public boolean isPreview() {
        return preview;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public boolean isWaitingForLoad() {
        return waitingForLoad;
    }

    @Override
    public void newDataAvailable() {
        if (countDownLatch != null) {
            countDownLatch.countDown();
        }
    }
}
