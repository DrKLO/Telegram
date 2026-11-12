package org.telegram.utils.twidget;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The widget engine's isolated process (`:widgets`, android:isolatedProcess): the widget's JavaScript (QuickJS), the
 * validator and the renderer (TwidgetCore on litehtml) run here, from libtwidget.so alone. The process has no
 * permissions, no network and no access to the app's files, so code that escapes the sandbox finds no account, no
 * messages and no keys. The client (WidgetRemoteEngine) paints what it sends and keeps every request and action.
 */
public final class WidgetEngineService extends Service {
    private static final String TAG = "WidgetEngine";
    private static final String PROCESS_SUFFIX = ":widgets";
    private static volatile String processName;
    private static String resources;
    // Native destroy joins the session's threads: off the binder, whose oneway calls of every session run one by one.
    private static final ExecutorService DESTROY = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "widget-engine-destroy");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * This is an engine's process: ApplicationLoader starts nothing of the client here. The app has no other isolated
     * processes; the name covers Android before 9, where an isolated process cannot tell. Any thread.
     */
    public static boolean isEngineProcess() {
        if (Build.VERSION.SDK_INT >= 28 && android.os.Process.isIsolated()) return true;
        String name = processName;
        if (name == null) {
            try (InputStream input = new FileInputStream("/proc/self/cmdline")) {
                byte[] buffer = new byte[256];
                int length = input.read(buffer);
                int end = 0;
                while (end < Math.max(0, length) && buffer[end] != 0) end++;
                name = new String(buffer, 0, end, StandardCharsets.UTF_8);
            } catch (Exception error) {
                name = "";
            }
            processName = name;
        }
        return name.contains(PROCESS_SUFFIX);
    }

    private final IWidgetEngine.Stub engine = new IWidgetEngine.Stub() {
        @Override
        public long create(byte[] html, byte[] theme, float width, float density, int cpuMultiplier, byte[] locale,
                byte[] storage, byte[] sdk, byte[] stoppedLabel, boolean visible, IWidgetEngineCallback callback) {
            long start = SystemClock.uptimeMillis();
            String key = resources(getAssets());
            long bundles = SystemClock.uptimeMillis() - start;
            long session = WidgetSandboxNative.create(key.getBytes(StandardCharsets.UTF_8), html, theme, width, density,
                    cpuMultiplier, locale, storage, sdk, stoppedLabel, visible, new WidgetEngineBackend(callback, density));
            Log.i(TAG, "session in " + (SystemClock.uptimeMillis() - start) + " ms (bundles " + bundles + " ms)");
            return session;
        }

        @Override
        public void destroy(long session) {
            DESTROY.execute(() -> WidgetSandboxNative.destroy(session));
        }

        @Override
        public void visible(long session, boolean visible) {
            WidgetSandboxNative.visible(session, visible);
        }

        @Override
        public void theme(long session, byte[] theme) {
            WidgetSandboxNative.theme(session, theme);
        }

        @Override
        public void width(long session, float width) {
            WidgetSandboxNative.width(session, width);
        }

        @Override
        public void data(long session, byte[] data) {
            WidgetSandboxNative.data(session, data);
        }

        @Override
        public void safeArea(long session, byte[] area) {
            WidgetSandboxNative.safeArea(session, area);
        }

        @Override
        public void answerRequest(long session, long request, int failure, byte[] result) {
            WidgetSandboxNative.answerRequest(session, request, failure, result);
        }

        @Override
        public void repaint(long session) {
            WidgetSandboxNative.repaint(session);
        }

        @Override
        public void tick(long session, float timeMs, boolean frame, boolean animate, boolean paint) {
            WidgetSandboxNative.tick(session, timeMs, frame, animate, paint);
        }

        @Override
        public void touch(long session, int action, float x, float y, int pointer, boolean primary, boolean mouse,
                int modifiers, int button) {
            WidgetSandboxNative.touch(session, action, x, y, pointer, primary, mouse, modifiers, button);
        }

        @Override
        public void hover(long session, int action, float x, float y, int modifiers) {
            WidgetSandboxNative.hover(session, action, x, y, modifiers);
        }

        @Override
        public void warmup() {
            long start = SystemClock.uptimeMillis();
            try {
                resources(getAssets());
                Log.i(TAG, "warm: bundles in " + (SystemClock.uptimeMillis() - start) + " ms");
            } catch (RuntimeException error) {
                Log.e(TAG, "warmup failed", error);  // the first session reports it
            }
        }

        @Override
        public void debug(boolean conformance, boolean attackScan, byte[] limits) {
            WidgetSandboxNative.conformance(conformance);
            WidgetSandboxNative.attackScan(attackScan);
            WidgetSandboxNative.limits(limits);
        }
    };

    /**
     * The process's seccomp filter (twidget-seccomp.cpp): audit logs the calls the filter is for ("TwidgetSeccomp" in
     * logcat) and changes nothing else; enforce kills the process on them. Audit until the logs from the devices say
     * the engine never makes them.
     */
    private static final boolean SECCOMP_ENFORCE = false;

    private static native int installSeccomp(boolean enforce);
    private static native void seccompSelfTest();

    @Override
    public void onCreate() {
        super.onCreate();
        long start = SystemClock.uptimeMillis();
        if (!WidgetSandboxNative.available()) throw new IllegalStateException("libtwidget is missing");
        long library = SystemClock.uptimeMillis() - start;
        // Before the first session: everything widget code could reach runs under it.
        if (installSeccomp(SECCOMP_ENFORCE) == 0 && org.telegram.messenger.BuildConfig.DEBUG_PRIVATE_VERSION) seccompSelfTest();
        WidgetEngineBackend.Stats.start();
        Log.i(TAG, "engine up: " + (Build.VERSION.SDK_INT >= 24 ? start - android.os.Process.getStartUptimeMillis() : -1)
                + " ms from fork to onCreate, library " + library + " ms, seccomp "
                + (SystemClock.uptimeMillis() - start - library) + " ms");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return engine;
    }

    /**
     * The bundles from the APK's assets, checked against their manifest as WidgetSandboxResources does, registered
     * once under a key that names them (WidgetSandboxNative#registerResources).
     */
    private static synchronized String resources(AssetManager assets) {
        if (resources != null) return resources;
        try {
            String manifest;
            try (InputStream stream = assets.open("WidgetSandbox/manifest.txt")) {
                manifest = WidgetSandboxResources.read(stream);
            }
            byte[] sandbox = null, validator = null, renderer = null, sandboxBytecode = null, validatorBytecode = null;
            for (String line : manifest.split("\n")) {
                if (line.isEmpty()) continue;
                String[] fields = line.split(" ", 2);
                byte[] value = asset(assets, fields[1]);
                StringBuilder actual = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(value)) actual.append(String.format("%02x", b & 255));
                if (!fields[0].equals(actual.toString()))
                    throw new IllegalStateException("Bundle checksum mismatch: " + fields[1]);
                switch (fields[1]) {
                    case "sandbox-bundle.js": sandbox = value; break;
                    case "validator-bundle.js": validator = value; break;
                    case "renderer-bundle.js": renderer = value; break;
                    case "sandbox-bundle.qbc": sandboxBytecode = value; break;
                    case "validator-bundle.qbc": validatorBytecode = value; break;
                }
            }
            if (sandbox == null || validator == null) throw new IllegalStateException("Widget bundles are missing");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("assets:");
            for (int i = 0; i < 12; i++) key.append(String.format("%02x", hash[i] & 255));
            WidgetSandboxNative.registerResources(key.toString().getBytes(StandardCharsets.UTF_8), sandbox, validator,
                    renderer, sandboxBytecode, validatorBytecode);
            resources = key.toString();
            return resources;
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Widget bundles: " + error);
        }
    }

    private static byte[] asset(AssetManager assets, String path) throws Exception {
        if (path.contains("..") || path.startsWith("/")) throw new IllegalArgumentException("Invalid bundle path");
        try (InputStream input = assets.open("WidgetSandbox/WidgetJS/" + path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
}
