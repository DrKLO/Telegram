/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NativeLoader {

    private final static int LIB_VERSION = 30;
    private final static String LIB_NAME = "tmessages." + LIB_VERSION;
    private final static String SHARED_LIB_NAME = "c++_shared";

    private static volatile boolean nativeLoaded = false;

    private NativeLoader() {
    }

    public static synchronized void initNativeLibs(Context context) {
        if (!nativeLoaded) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (!loadNativeLib(context, SHARED_LIB_NAME)) {
                    throw new IllegalStateException("unable to load shared c++ library: " + SHARED_LIB_NAME);
                }
            }
            if (!loadNativeLib(context, LIB_NAME)) {
                throw new IllegalStateException("unable to load native library: " + LIB_NAME);
            }
            nativeLoaded = true;
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private static boolean loadNativeLib(Context context, String libName) {
        try {
            try {
                System.loadLibrary(libName);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("loaded normal lib: " + libName);
                }
                return true;
            } catch (Error e) {
                FileLog.e(e);
            }

            String folder;
            try {
                String str = Build.CPU_ABI;
                if (Build.CPU_ABI.equalsIgnoreCase("x86_64")) {
                    folder = "x86_64";
                } else if (Build.CPU_ABI.equalsIgnoreCase("arm64-v8a")) {
                    folder = "arm64-v8a";
                } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi-v7a")) {
                    folder = "armeabi-v7a";
                } else if (Build.CPU_ABI.equalsIgnoreCase("armeabi")) {
                    folder = "armeabi";
                } else if (Build.CPU_ABI.equalsIgnoreCase("x86")) {
                    folder = "x86";
                } else if (Build.CPU_ABI.equalsIgnoreCase("mips")) {
                    folder = "mips";
                } else {
                    folder = "armeabi";
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("Unsupported arch: " + Build.CPU_ABI);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                folder = "armeabi";
            }

            String javaArch = System.getProperty("os.arch");
            if (javaArch != null && javaArch.contains("686")) {
                folder = "x86";
            }

            File destDir = new File(context.getFilesDir(), "lib");
            destDir.mkdirs();

            File destLocalFile = new File(destDir, "lib" + libName + "loc.so");
            if (destLocalFile.exists()) {
                try {
                    System.load(destLocalFile.getAbsolutePath());
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("loaded local lib: " + libName);
                    }
                    return true;
                } catch (Error e) {
                    FileLog.e(e);
                }
                destLocalFile.delete();
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(String.format(Locale.US, "library %s not found, arch = %s", libName, folder));
            }

            if (loadFromZip(context, destDir, destLocalFile, folder, libName)) {
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            System.loadLibrary(libName);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("loaded lib: " + libName);
            }
            return true;
        } catch (Error e) {
            FileLog.e(e);
        }

        return false;
    }

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "SetWorldReadable"})
    private static boolean loadFromZip(Context context, File destDir, File destLocalFile, String folder, String libName) {
        try {
            for (File file : destDir.listFiles()) {
                file.delete();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        ZipFile zipFile = null;
        InputStream stream = null;
        try {
            zipFile = new ZipFile(context.getApplicationInfo().sourceDir);
            ZipEntry entry = zipFile.getEntry("lib/" + folder + "/lib" + libName + ".so");
            if (entry == null) {
                throw new Exception("Unable to find file in apk:" + "lib/" + folder + "/" + libName);
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

            destLocalFile.setReadable(true, false);
            destLocalFile.setExecutable(true, false);
            destLocalFile.setWritable(true);

            try {
                System.load(destLocalFile.getAbsolutePath());
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("loaded lib from zip: " + libName);
                }
                return true;
            } catch (Error e) {
                FileLog.e(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return false;
    }
}
