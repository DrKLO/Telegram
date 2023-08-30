package org.telegram.messenger;

public class MessageLoaderLogger {

    final long dialogId;
    final int count;
    final int loadIndex;
    final long startTime;

    long moveToStorageQueueTime;
    long getFromDatabaseTime;
    long moveToStageQueueTime;
    long stageQueueProccessing;

    boolean reload;

    public MessageLoaderLogger(long dialogId, int loadIndex, int count) {
        this.dialogId = dialogId;
        this.count = count;
        this.loadIndex = loadIndex;
        startTime = System.currentTimeMillis();
    }

    public void logStorageQueuePost() {
        moveToStorageQueueTime =  System.currentTimeMillis() - startTime;
    }

    public void logStorageProccessing() {
        getFromDatabaseTime = System.currentTimeMillis() - startTime;
    }

    public void logStageQueuePost() {
        moveToStageQueueTime = System.currentTimeMillis() - startTime;
    }

    public void reload() {
        reload = true;
    }

    public void logStageQueueProcessing() {
        stageQueueProccessing = System.currentTimeMillis() - startTime;
    }

    public void finish() {
        long totalTime = System.currentTimeMillis() - startTime;
        FileLog.d("MessageLoaderLogger dialogId=" + dialogId + " index=" + loadIndex + " count=" + count + " " +
                " moveToStorageQueueTime=" + moveToStorageQueueTime +
                " getFromDatabaseTime=" + getFromDatabaseTime +
                " moveToStageQueueTime=" + moveToStageQueueTime +
                " stageQueueProccessing=" + stageQueueProccessing +
                " wasReload=" + reload + " totalTime=" + totalTime
        );
    }
}
