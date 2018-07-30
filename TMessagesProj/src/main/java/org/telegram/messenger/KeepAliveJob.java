/*
 * This is the source code of Telegram for Android v. 4.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.Intent;

import org.telegram.messenger.support.JobIntentService;

import java.util.concurrent.CountDownLatch;

public class KeepAliveJob extends JobIntentService {

    private static volatile CountDownLatch countDownLatch;
    private static volatile boolean startingJob;
    private static final Object sync = new Object();

    public static void startJob() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (startingJob || countDownLatch != null) {
                    return;
                }
                try {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("starting keep-alive job");
                    }
                    synchronized (sync) {
                        startingJob = true;
                    }
                    enqueueWork(ApplicationLoader.applicationContext, KeepAliveJob.class, 1000, new Intent());
                } catch (Exception ignore) {

                }
            }
        });
    }

    private static void finishJobInternal() {
        synchronized (sync) {
            if (countDownLatch != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("finish keep-alive job");
                }
                countDownLatch.countDown();
            }
            if (startingJob) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("finish queued keep-alive job");
                }
                startingJob = false;
            }
        }
    }

    public static void finishJob() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                finishJobInternal();
            }
        });
    }

    private static Runnable finishJobByTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            finishJobInternal();
        }
    };

    @Override
    protected void onHandleWork(Intent intent) {
        synchronized (sync) {
            if (!startingJob) {
                return;
            }
            countDownLatch = new CountDownLatch(1);
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("started keep-alive job");
        }
        Utilities.globalQueue.postRunnable(finishJobByTimeoutRunnable, 60 * 1000);
        try {
            countDownLatch.await();
        } catch (Throwable ignore) {

        }
        Utilities.globalQueue.cancelRunnable(finishJobByTimeoutRunnable);
        synchronized (sync) {
            countDownLatch = null;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("ended keep-alive job");
        }
    }
}
