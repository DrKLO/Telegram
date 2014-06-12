/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLoader {

    private static final long sizes[] = new long[] {
            922256,     //armeabi
            991908,     //armeabi-v7a
            1713204,    //x86
            0,          //mips
    };

    private static volatile boolean nativeLoaded = false;

    private static File getNativeLibraryDir(Context context) {
        File f = null;
        if (context != null) {
            try {
                f = new File((String)ApplicationInfo.class.getField("nativeLibraryDir").get(context.getApplicationInfo()));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (f == null) {
            f = new File(context.getApplicationInfo().dataDir, "lib");
        }
        if (f != null && f.isDirectory()) {
            return f;
        }
        return null;
    }


    public static synchronized void initNativeLibs(Context context) {
        if (nativeLoaded) {
            return;
        }

        try {
            String folder = null;
            long libSize = 0;
            long libSize2 = 0;

            try {
                if (Build.CPU_ABI.equalsIgnoreCase("armeabi-v7a")) {
                    folder = "armeabi-v7a";
                    libSize = sizes[1];
                    libSize2 = sizes[0];
                } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi")) {
                    folder = "armeabi";
                    libSize = sizes[0];
                    libSize2 = sizes[1];
                } else if (Build.CPU_ABI.equalsIgnoreCase("x86")) {
                    folder = "x86";
                    libSize = sizes[2];
                } else if (Build.CPU_ABI.equalsIgnoreCase("mips")) {
                    folder = "mips";
                    libSize = sizes[3];
                } else {
                    folder = "armeabi";
                    libSize = sizes[0];
                    libSize2 = sizes[1];
                    FileLog.e("tmessages", "Unsupported arch: " + Build.CPU_ABI);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                folder = "armeabi";
                libSize = sizes[0];
                libSize2 = sizes[1];
            }

            File destFile = getNativeLibraryDir(context);
            if (destFile != null) {
                destFile = new File(destFile, "libtmessages.so");
                if (destFile.exists() && (destFile.length() == libSize || libSize2 != 0 && destFile.length() == libSize2)) {
                    FileLog.d("tmessages", "Load normal lib");
                    try {
                        System.loadLibrary("tmessages");
                        nativeLoaded = true;
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            File destLocalFile = new File(context.getFilesDir().getAbsolutePath() + "/libtmessages.so");
            if (destLocalFile.exists()) {
                if (destLocalFile.length() == libSize) {
                    try {
                        FileLog.d("tmessages", "Load local lib");
                        System.load(destLocalFile.getAbsolutePath());
                        nativeLoaded = true;
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    destLocalFile.delete();
                }
            }

            FileLog.e("tmessages", "Library not found, arch = " + folder);

            ZipFile zipFile = null;
            InputStream stream = null;
            try {
                zipFile = new ZipFile(context.getApplicationInfo().sourceDir);
                ZipEntry entry = zipFile.getEntry("lib/" + folder + "/libtmessages.so");
                if (entry == null) {
                    throw new Exception("Unable to find file in apk:" + "lib/" + folder + "/libtmessages.so");
                }
                stream = zipFile.getInputStream(entry);

                OutputStream out = new FileOutputStream(destLocalFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = stream.read(buf)) > 0) {
                    Thread.yield();
                    out.write(buf, 0, len);
                }
                out.close();

                System.load(destLocalFile.getAbsolutePath());
                nativeLoaded = true;
                return;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        System.loadLibrary("tmessages");
        nativeLoaded = true;
    }
}
