package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CountDownLatch;

public class AnimatedFileDrawableStream implements FileLoadOperationStream {

    private final FileLoadOperation loadOperation;
    private CountDownLatch countDownLatch;
    private TLRPC.Document document;
    private ImageLocation location;
    private Object parentObject;
    private int currentAccount;
    private volatile boolean canceled;
    private final Object sync = new Object();
    private long lastOffset;
    private boolean waitingForLoad;
    private boolean preview;
    private boolean finishedLoadingFile;
    private String finishedFilePath;
    private int loadingPriority;

    private int debugCanceledCount;
    private boolean debugReportSend;

    public AnimatedFileDrawableStream(TLRPC.Document d, ImageLocation l, Object p, int a, boolean prev, int loadingPriority) {
        document = d;
        location = l;
        parentObject = p;
        currentAccount = a;
        preview = prev;
        this.loadingPriority = loadingPriority;
        loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, preview, loadingPriority);
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
                debugCanceledCount++;
                if (!debugReportSend && debugCanceledCount > 100) {
                    debugReportSend = true;
                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                        throw new RuntimeException("infinity stream reading!!!");
                    } else {
                        FileLog.e(new RuntimeException("infinity stream reading!!!"));
                    }
                }
                return 0;
            }
        }
        if (readLength == 0) {
            return 0;
        } else {
            long availableLength = 0;
            try {
                while (availableLength == 0) {
                    long[] result = loadOperation.getDownloadedLengthFromOffset(offset, readLength);
                    availableLength = result[0];
                    if (!finishedLoadingFile && result[1] != 0) {
                        finishedLoadingFile = true;
                        finishedFilePath = loadOperation.getCacheFileFinal().getAbsolutePath();
                    }
                    if (availableLength == 0) {
                        synchronized (sync) {
                            if (canceled) {
                                cancelLoadingInternal();
                                return 0;
                            }
                        }
                        if (loadOperation.isPaused() || lastOffset != offset || preview) {
                            FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset, preview, loadingPriority);
                        }
                        synchronized (sync) {
                            if (canceled) {
                                cancelLoadingInternal();
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
                FileLog.e(e, false);
            }
            return (int) availableLength;
        }
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean removeLoading) {
        if (canceled) {
            return;
        }
        synchronized (sync) {
            if (countDownLatch != null) {
                countDownLatch.countDown();
                if (removeLoading && !canceled && !preview) {
                    FileLoader.getInstance(currentAccount).removeLoadingVideo(document, false, true);
                }
            }
            if (removeLoading) {
                cancelLoadingInternal();
            }
            canceled = true;
        }
    }

    private void cancelLoadingInternal() {
        FileLoader.getInstance(currentAccount).cancelLoadFile(document);
        if (location != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(location.location, "mp4");
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
