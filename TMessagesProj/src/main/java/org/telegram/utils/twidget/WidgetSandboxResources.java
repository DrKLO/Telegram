package org.telegram.utils.twidget;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Makes the repository's exact JS/bytecode files available to SandboxResources::fromDirectory. */
public final class WidgetSandboxResources {
    static final ExecutorService IO =
            Executors.newFixedThreadPool(
                    2,
                    runnable -> {
                        Thread thread =
                                new Thread(
                                        () -> {
                                            android.os.Process.setThreadPriority(
                                                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
                                            runnable.run();
                                        },
                                        "widget-sandbox-io");
                        thread.setDaemon(true);
                        return thread;
                    });
    private static File prepared;

    // Worker only. A content hash names each complete, immutable bundle directory.
    public static synchronized File prepare(Context context) throws Exception {
        if (prepared != null) return prepared;
        AssetManager assets = context.getApplicationContext().getAssets();
        String manifest;
        try (InputStream stream = assets.open("WidgetSandbox/manifest.txt")) {
            manifest = read(stream);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(manifest.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 12; i++) name.append(String.format("%02x", hash[i] & 255));
        File directory = new File(context.getFilesDir(), "widget-sandbox/" + name);
        File ready = new File(directory, "ready");
        if (!ready.isFile()) {
            for (String line : manifest.split("\n")) {
                if (line.isEmpty()) continue;
                String[] fields = line.split(" ", 2);
                String path = fields[1];
                if (path.contains("..") || path.startsWith("/"))
                    throw new IllegalArgumentException("Invalid bundle path");
                File file = new File(directory, path);
                if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
                    throw new IllegalStateException("Cannot create bundle directory");
                MessageDigest fileHash = MessageDigest.getInstance("SHA-256");
                try (InputStream input = assets.open("WidgetSandbox/WidgetJS/" + path);
                        FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        fileHash.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                }
                StringBuilder actual = new StringBuilder();
                for (byte value : fileHash.digest())
                    actual.append(String.format("%02x", value & 255));
                if (!fields[0].equals(actual.toString()))
                    throw new IllegalStateException("Bundle checksum mismatch: " + path);
            }
            try (FileOutputStream output = new FileOutputStream(ready)) {
                output.write(1);
            }
        }
        prepared = directory;
        return directory;
    }

    static String read(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private WidgetSandboxResources() {}
}
