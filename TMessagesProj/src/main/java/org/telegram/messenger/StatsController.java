/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.RandomAccessFile;

public class StatsController extends BaseController {

    public static final int TYPE_MOBILE = 0;
    public static final int TYPE_WIFI = 1;
    public static final int TYPE_ROAMING = 2;

    public static final int TYPE_CALLS = 0;
    public static final int TYPE_MESSAGES = 1;
    public static final int TYPE_VIDEOS = 2;
    public static final int TYPE_AUDIOS = 3;
    public static final int TYPE_PHOTOS = 4;
    public static final int TYPE_FILES = 5;
    public static final int TYPE_TOTAL = 6;
    private static final int TYPES_COUNT = 7;

    private byte[] buffer = new byte[8];

    private long lastInternalStatsSaveTime;
    private long[][] sentBytes = new long[3][TYPES_COUNT];
    private long[][] receivedBytes = new long[3][TYPES_COUNT];
    private int[][] sentItems = new int[3][TYPES_COUNT];
    private int[][] receivedItems = new int[3][TYPES_COUNT];
    private long[] resetStatsDate = new long[3];
    private int[] callsTotalTime = new int[3];
    private RandomAccessFile statsFile;
    private static DispatchQueue statsSaveQueue = new DispatchQueue("statsSaveQueue");

    private static final ThreadLocal<Long> lastStatsSaveTime = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            return System.currentTimeMillis() - 1000;
        }
    };

    private byte[] intToBytes(int value) {
        buffer[0] = (byte) (value >>> 24);
        buffer[1] = (byte) (value >>> 16);
        buffer[2] = (byte) (value >>> 8);
        buffer[3] = (byte) (value);
        return buffer;
    }

    private int bytesToInt(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    private byte[] longToBytes(long value) {
        buffer[0] = (byte) (value >>> 56);
        buffer[1] = (byte) (value >>> 48);
        buffer[2] = (byte) (value >>> 40);
        buffer[3] = (byte) (value >>> 32);
        buffer[4] = (byte) (value >>> 24);
        buffer[5] = (byte) (value >>> 16);
        buffer[6] = (byte) (value >>> 8);
        buffer[7] = (byte) (value);
        return buffer;
    }

    private long bytesToLong(byte[] bytes) {
        return ((long) bytes[0] & 0xFF) << 56 | ((long) bytes[1] & 0xFF) << 48 | ((long) bytes[2] & 0xFF) << 40 | ((long) bytes[3] & 0xFF) << 32 | ((long) bytes[4] & 0xFF) << 24 | ((long) bytes[5] & 0xFF) << 16 | ((long) bytes[6] & 0xFF) << 8 | ((long) bytes[7] & 0xFF);
    }

    private Runnable saveRunnable = new Runnable() {
        @Override
        public void run() {
            long newTime = System.currentTimeMillis();
            if (Math.abs(newTime - lastInternalStatsSaveTime) < 2000) {
                return;
            }
            lastInternalStatsSaveTime = newTime;
            try {
                statsFile.seek(0);
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < TYPES_COUNT; b++) {
                        statsFile.write(longToBytes(sentBytes[a][b]), 0, 8);
                        statsFile.write(longToBytes(receivedBytes[a][b]), 0, 8);
                        statsFile.write(intToBytes(sentItems[a][b]), 0, 4);
                        statsFile.write(intToBytes(receivedItems[a][b]), 0, 4);
                    }
                    statsFile.write(intToBytes(callsTotalTime[a]), 0, 4);
                    statsFile.write(longToBytes(resetStatsDate[a]), 0, 8);
                }
                statsFile.getFD().sync();
            } catch (Exception ignore) {

            }
        }
    };

    private static volatile StatsController[] Instance = new StatsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static StatsController getInstance(int num) {
        StatsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (StatsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new StatsController(num);
                }
            }
        }
        return localInstance;
    }

    private StatsController(int account) {
        super(account);
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (account != 0) {
            filesDir = new File(ApplicationLoader.getFilesDirFixed(), "account" + account + "/");
            filesDir.mkdirs();
        }

        boolean needConvert = true;
        try {
            statsFile = new RandomAccessFile(new File(filesDir, "stats2.dat"), "rw");
            if (statsFile.length() > 0) {
                boolean save = false;
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < TYPES_COUNT; b++) {
                        statsFile.readFully(buffer, 0, 8);
                        sentBytes[a][b] = bytesToLong(buffer);
                        statsFile.readFully(buffer, 0, 8);
                        receivedBytes[a][b] = bytesToLong(buffer);
                        statsFile.readFully(buffer, 0, 4);
                        sentItems[a][b] = bytesToInt(buffer);
                        statsFile.readFully(buffer, 0, 4);
                        receivedItems[a][b] = bytesToInt(buffer);
                    }
                    statsFile.readFully(buffer, 0, 4);
                    callsTotalTime[a] = bytesToInt(buffer);
                    statsFile.readFully(buffer, 0, 8);
                    resetStatsDate[a] = bytesToLong(buffer);
                    if (resetStatsDate[a] == 0) {
                        save = true;
                        resetStatsDate[a] = System.currentTimeMillis();
                    }
                }
                if (save) {
                    saveStats();
                }
                needConvert = false;
            }
        } catch (Exception ignore) {

        }
        if (needConvert) {
            SharedPreferences sharedPreferences;
            if (account == 0) {
                sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("stats", Context.MODE_PRIVATE);
            } else {
                sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("stats" + account, Context.MODE_PRIVATE);
            }
            boolean save = false;
            for (int a = 0; a < 3; a++) {
                callsTotalTime[a] = sharedPreferences.getInt("callsTotalTime" + a, 0);
                resetStatsDate[a] = sharedPreferences.getLong("resetStatsDate" + a, 0);
                for (int b = 0; b < TYPES_COUNT; b++) {
                    sentBytes[a][b] = sharedPreferences.getLong("sentBytes" + a + "_" + b, 0);
                    receivedBytes[a][b] = sharedPreferences.getLong("receivedBytes" + a + "_" + b, 0);
                    sentItems[a][b] = sharedPreferences.getInt("sentItems" + a + "_" + b, 0);
                    receivedItems[a][b] = sharedPreferences.getInt("receivedItems" + a + "_" + b, 0);
                }
                if (resetStatsDate[a] == 0) {
                    save = true;
                    resetStatsDate[a] = System.currentTimeMillis();
                }
            }
            if (save) {
                saveStats();
            }
        }
    }

    public void incrementReceivedItemsCount(int networkType, int dataType, int value) {
        receivedItems[networkType][dataType] += value;
        saveStats();
    }

    public void incrementSentItemsCount(int networkType, int dataType, int value) {
        sentItems[networkType][dataType] += value;
        saveStats();
    }

    public void incrementReceivedBytesCount(int networkType, int dataType, long value) {
        receivedBytes[networkType][dataType] += value;
        saveStats();
    }

    public void incrementSentBytesCount(int networkType, int dataType, long value) {
        sentBytes[networkType][dataType] += value;
        saveStats();
    }

    public void incrementTotalCallsTime(int networkType, int value) {
        callsTotalTime[networkType] += value;
        saveStats();
    }

    public int getRecivedItemsCount(int networkType, int dataType) {
        return receivedItems[networkType][dataType];
    }

    public int getSentItemsCount(int networkType, int dataType) {
        return sentItems[networkType][dataType];
    }

    public long getSentBytesCount(int networkType, int dataType) {
        if (dataType == TYPE_MESSAGES) {
            return sentBytes[networkType][TYPE_TOTAL] - sentBytes[networkType][TYPE_FILES] - sentBytes[networkType][TYPE_AUDIOS] - sentBytes[networkType][TYPE_VIDEOS] - sentBytes[networkType][TYPE_PHOTOS];
        }
        return sentBytes[networkType][dataType];
    }

    public long getReceivedBytesCount(int networkType, int dataType) {
        if (dataType == TYPE_MESSAGES) {
            return receivedBytes[networkType][TYPE_TOTAL] - receivedBytes[networkType][TYPE_FILES] - receivedBytes[networkType][TYPE_AUDIOS] - receivedBytes[networkType][TYPE_VIDEOS] - receivedBytes[networkType][TYPE_PHOTOS];
        }
        return receivedBytes[networkType][dataType];
    }

    public int getCallsTotalTime(int networkType) {
        return callsTotalTime[networkType];
    }

    public long getResetStatsDate(int networkType) {
        return resetStatsDate[networkType];
    }

    public void resetStats(int networkType) {
        resetStatsDate[networkType] = System.currentTimeMillis();
        for (int a = 0; a < TYPES_COUNT; a++) {
            sentBytes[networkType][a] = 0;
            receivedBytes[networkType][a] = 0;
            sentItems[networkType][a] = 0;
            receivedItems[networkType][a] = 0;
        }
        callsTotalTime[networkType] = 0;
        saveStats();
    }

    private void saveStats() {
        long newTime = System.currentTimeMillis();
        if (Math.abs(newTime - lastStatsSaveTime.get()) >= 2000) {
            lastStatsSaveTime.set(newTime);
            statsSaveQueue.postRunnable(saveRunnable);
        }
    }
}
