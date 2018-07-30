/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.SparseArray;

import java.io.File;

public class ClearCacheService extends IntentService {

    public ClearCacheService() {
        super("ClearCacheService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ApplicationLoader.postInitApplication();

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        final int keepMedia = preferences.getInt("keep_media", 2);
        if (keepMedia == 2) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                int days;
                if (keepMedia == 0) {
                    days = 7;
                } else if (keepMedia == 1) {
                    days = 30;
                } else {
                    days = 3;
                }
                long currentTime = System.currentTimeMillis() / 1000 - 60 * 60 * 24 * days;
                final SparseArray<File> paths = ImageLoader.getInstance().createMediaPaths();
                for (int a = 0; a < paths.size(); a++) {
                    if (paths.keyAt(a) == FileLoader.MEDIA_DIR_CACHE) {
                        continue;
                    }
                    try {
                        Utilities.clearDir(paths.valueAt(a).getAbsolutePath(), 0, currentTime);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }
        });
    }
}
