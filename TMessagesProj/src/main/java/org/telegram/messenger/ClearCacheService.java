/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.system.Os;
import android.system.StructStat;

import java.io.File;
import java.util.HashMap;

public class ClearCacheService extends IntentService {

    public ClearCacheService() {
        super("ClearCacheService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ApplicationLoader.postInitApplication();

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        final int keepMedia = preferences.getInt("keep_media", 2);
        if (keepMedia == 2) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long diff = 60 * 60 * 1000 * 24 * (keepMedia == 0 ? 7 : 30);
                final HashMap<Integer, File> paths = ImageLoader.getInstance().createMediaPaths();
                for (HashMap.Entry<Integer, File> entry : paths.entrySet()) {
                    if (entry.getKey() == FileLoader.MEDIA_DIR_CACHE) {
                        continue;
                    }
                    try {
                        File[] array = entry.getValue().listFiles();
                        if (array != null) {
                            for (int b = 0; b < array.length; b++) {
                                File f = array[b];
                                if (f.isFile()) {
                                    if (f.getName().equals(".nomedia")) {
                                        continue;
                                    }
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        try {
                                            StructStat stat = Os.stat(f.getPath());
                                            if (stat.st_atime != 0) {
                                                if (stat.st_atime + diff < currentTime) {
                                                    f.delete();
                                                }
                                            } else if (stat.st_mtime + diff < currentTime) {
                                                f.delete();
                                            }
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                    } else if (f.lastModified() + diff < currentTime) {
                                        f.delete();
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
        });
    }
}
