package org.telegram.utils.twidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The engine in its isolated process (WidgetEngineService), one for every widget: each session runs on threads of its
 * own there, with a QuickJS runtime of its own. The process is bound while sessions live and for a while after the
 * last. When it dies, its sessions are told once (WidgetSandboxSession#onEngineDied) and calls for them go nowhere;
 * the next session starts a new one.
 */
final class WidgetRemoteEngine extends WidgetEngine {
    static final WidgetRemoteEngine INSTANCE = new WidgetRemoteEngine();

    private static final long BIND_TIMEOUT_MS = 10_000, IDLE_UNBIND_MS = 30_000;
    // What one large argument may add up to (IWidgetEngineCallback#part): a display list of the node limit is far less.
    private static final int MAX_LARGE = 16 * 1024 * 1024;
    // One textWidth call; the engine measures longer text in pieces (WidgetEngineBackend#TEXT_PIECE).
    static final int MAX_TEXT = 256 * 1024;

    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    // The bound process, null when none; guarded by `lock`.
    private Connection connection;
    private final ConcurrentHashMap<Long, Client> clients = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private Context context;
    // prewarm() was asked for: every binding gets the app's hold (hold()); guarded by `lock`.
    private boolean kept;

    private WidgetRemoteEngine() {}

    /**
     * The engine's process started now and kept for the app's lifetime, its bundles loaded, so that a widget's first
     * frame does not wait for the process (a few hundred ms on a weak phone). An idle engine takes no CPU, only its
     * memory. After a death the binding is given up (drop()); the next session, or this called again on the next
     * return to the foreground, binds afresh. Cheap when the engine is up. Any thread but the main one.
     */
    void prewarm(Context context) {
        synchronized (lock) {
            if (this.context == null) this.context = context.getApplicationContext();
            kept = true;
            hold(connection());
        }
    }

    // Guarded by `lock`: the app's own hold on `binding` (prewarm), never released, so no idle unbind.
    private void hold(Connection binding) {
        if (binding.held) return;
        binding.held = true;
        binding.live++;
        main.removeCallbacks(binding.idleUnbind);
        if (binding.engine != null) warmup(binding.engine);
    }

    private static void warmup(IWidgetEngine engine) {
        try {
            engine.warmup();
        } catch (RemoteException ignored) {
            // Dead: the binding brings a new one, warmed in onServiceConnected.
        }
    }

    /** The engine process's binding; guarded by `lock`. */
    private final class Connection implements ServiceConnection {
        IWidgetEngine engine;
        int generation, live;
        boolean bound, held;
        final Runnable idleUnbind = () -> unbindIfIdle(this);

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (lock) {
                if (!bound) return;
                int connected = ++generation;
                try {
                    binder.linkToDeath(() -> lost(this, connected), 0);
                } catch (RemoteException error) {
                    return;
                }
                engine = IWidgetEngine.Stub.asInterface(binder);
                if (held) warmup(engine);
                lock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The process died (lost() gives the binding up).
            int dead;
            synchronized (lock) {
                dead = generation;
            }
            lost(this, dead);
        }
    }

    // Guarded by `lock`: the binding, made when there is none.
    private Connection connection() {
        if (connection != null) return connection;
        Connection created = new Connection();
        Intent intent = new Intent(context, WidgetEngineService.class);
        // BIND_IMPORTANT: the engine runs at the client's scheduling group (top-app while the chat is shown), not
        // in `foreground`, where a weak phone keeps it on its slow cores and a press's claim comes too late.
        if (!context.bindService(intent, created, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT)) {
            context.unbindService(created);
            throw new IllegalStateException("Widget engine service is unavailable");
        }
        created.bound = true;
        connection = created;
        if (kept) hold(created);
        return created;
    }

    @Override
    long create(Context context, String html, String theme, float width, float density,
            int cpuMultiplier, String locale, String storage, String sdk, String stoppedLabel, boolean visible,
            WidgetSandboxBackend backend) throws Exception {
        Connection binding;
        IWidgetEngine engine;
        int generation;
        long start = SystemClock.uptimeMillis();
        boolean cold;
        synchronized (lock) {
            if (this.context == null) this.context = context.getApplicationContext();
            binding = connection();
            main.removeCallbacks(binding.idleUnbind);
            binding.live++;
            cold = binding.engine == null;
            try {
                long deadline = SystemClock.elapsedRealtime() + BIND_TIMEOUT_MS;
                while (binding.engine == null) {
                    long left = deadline - SystemClock.elapsedRealtime();
                    if (left <= 0) {
                        drop(binding); // a stuck binding is not waited on again: the next session binds afresh
                        throw new IllegalStateException("Widget engine did not start");
                    }
                    lock.wait(left);
                }
            } catch (Exception error) {
                released(binding);
                throw error;
            }
            engine = binding.engine;
            generation = binding.generation;
        }
        long bound = SystemClock.uptimeMillis() - start;
        Client client = new Client(binding, generation, backend);
        long nativeId;
        try {
            engine.debug(conformance, attackScan, limits);
            nativeId = engine.create(utf8(html), utf8(theme), width, density, cpuMultiplier, utf8(locale),
                    utf8(storage), utf8(sdk), utf8(stoppedLabel), visible, client);
        } catch (Exception error) {
            synchronized (lock) {
                released(binding);
            }
            throw error;
        }
        client.nativeId = nativeId;
        if (org.telegram.messenger.BuildVars.LOGS_ENABLED)
            android.util.Log.d("WidgetEngine", "create " + (SystemClock.uptimeMillis() - start) + " ms"
                    + (cold ? " (engine started: " + bound + " ms to bind)" : ""));
        long id = nextId.incrementAndGet();
        clients.put(id, client);
        // Died between the call and the put: lost() did not see this session.
        synchronized (lock) {
            if (binding.generation != generation || binding.engine == null) lost(binding, generation);
        }
        return id;
    }

    // Guarded by `lock`: one session of `binding` ended.
    private void released(Connection binding) {
        if (--binding.live > 0) return;
        main.removeCallbacks(binding.idleUnbind);
        main.postDelayed(binding.idleUnbind, IDLE_UNBIND_MS);
    }

    private void lost(Connection binding, int generation) {
        ArrayList<Client> dead = new ArrayList<>();
        synchronized (lock) {
            Iterator<Map.Entry<Long, Client>> it = clients.entrySet().iterator();
            while (it.hasNext()) {
                Client client = it.next().getValue();
                if (client.binding == binding && client.generation == generation) {
                    dead.add(client);
                    it.remove();
                    released(binding);
                }
            }
            if (binding.generation == generation) drop(binding);
        }
        for (Client client : dead) client.backend.session().onEngineDied();
    }

    /**
     * Guarded by `lock`: `binding` given up, its process dead or never up. The system does not bring an isolated
     * service back for a binding it keeps on every device: on MIUI 14 and One UI, once the engine's process died (killed
     * while the app was in the background, or crashed) no widget started again until the app restarted. The next
     * session, or prewarm on the next return to the foreground, binds afresh instead.
     */
    private void drop(Connection binding) {
        binding.engine = null;
        if (!binding.bound) return;
        binding.bound = false;
        main.removeCallbacks(binding.idleUnbind);
        if (connection == binding) connection = null;
        try {
            context.unbindService(binding);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void unbindIfIdle(Connection binding) {
        synchronized (lock) {
            if (binding.live > 0 || !binding.bound) return;
            binding.bound = false;
            binding.engine = null;
            if (connection == binding) connection = null;
            context.unbindService(binding);
        }
    }

    @Override
    void destroy(long session) {
        Client client = clients.remove(session);
        if (client == null) return;
        IWidgetEngine engine = client.engine();
        if (engine != null) {
            try {
                engine.destroy(client.nativeId);
            } catch (RemoteException ignored) {
            }
        }
        synchronized (lock) {
            released(client.binding);
        }
    }

    private interface Call {
        void run(IWidgetEngine engine, long session) throws RemoteException;
    }

    private interface Body<T> {
        T run() throws Exception;
    }

    // Oneway: never waits for the engine. A dead one is told through lost().
    private void call(long session, Call call) {
        Client client = clients.get(session);
        IWidgetEngine engine = client == null ? null : client.engine();
        if (engine == null) return;
        try {
            call.run(engine, client.nativeId);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    void visible(long session, boolean visible) {
        call(session, (e, id) -> e.visible(id, visible));
    }

    @Override
    void theme(long session, byte[] theme) {
        call(session, (e, id) -> e.theme(id, theme));
    }

    @Override
    void width(long session, float width) {
        call(session, (e, id) -> e.width(id, width));
    }

    @Override
    void data(long session, byte[] data) {
        call(session, (e, id) -> e.data(id, data));
    }

    @Override
    void safeArea(long session, byte[] area) {
        call(session, (e, id) -> e.safeArea(id, area));
    }

    @Override
    void answerRequest(long session, long request, int failure, byte[] result) {
        call(session, (e, id) -> e.answerRequest(id, request, failure, result));
    }

    @Override
    void repaint(long session) {
        call(session, IWidgetEngine::repaint);
    }

    @Override
    void tick(long session, float timeMs, boolean frame, boolean animate, boolean paint) {
        call(session, (e, id) -> e.tick(id, timeMs, frame, animate, paint));
    }

    @Override
    void touch(long session, int action, float x, float y, int pointer, boolean primary, boolean mouse, int modifiers,
            int button) {
        call(session, (e, id) -> e.touch(id, action, x, y, pointer, primary, mouse, modifiers, button));
    }

    @Override
    void hover(long session, int action, float x, float y, int modifiers) {
        call(session, (e, id) -> e.hover(id, action, x, y, modifiers));
    }

    /**
     * One session's calls from the engine, on binder threads, into its WidgetSandboxBackend as the native engine makes
     * them in-process. The other side may be compromised widget code: every argument is checked or fails safely here
     * (and in WidgetSandboxBackend, WidgetGate), and failures go back as IllegalStateException, which fails the
     * engine's call and nothing else.
     */
    private final class Client extends IWidgetEngineCallback.Stub {
        final Connection binding;
        final int generation;
        final WidgetSandboxBackend backend;
        volatile long nativeId;
        private final ByteArrayOutputStream large = new ByteArrayOutputStream();

        Client(Connection binding, int generation, WidgetSandboxBackend backend) {
            this.binding = binding;
            this.generation = generation;
            this.backend = backend;
        }

        /** The engine this session lives in, null once its process is gone. */
        IWidgetEngine engine() {
            synchronized (lock) {
                return binding.generation == generation ? binding.engine : null;
            }
        }

        private <T> T guard(Body<T> body) {
            try {
                return body.run();
            } catch (Exception error) {
                throw new IllegalStateException(error.toString());
            }
        }

        // The large argument sent in parts before this call, or `value` itself.
        private synchronized byte[] large(byte[] value) {
            if (value != null) return value;
            byte[] whole = large.toByteArray();
            large.reset();
            return whole;
        }

        @Override
        public synchronized void part(byte[] value) {
            if (value == null || large.size() + value.length > MAX_LARGE) {
                large.reset();
                throw new IllegalStateException("Widget engine: argument too large");
            }
            large.write(value, 0, value.length);
        }

        @Override
        public float[] defineFont(int id, byte[] families, float size, int weight, boolean italic, byte[] features) {
            return guard(() -> backend.defineFont(id, families, size, weight, italic, features));
        }

        @Override
        public float textWidth(int id, byte[] text) {
            return guard(() -> {
                if (text == null || text.length > MAX_TEXT) throw new IllegalArgumentException("Text too long");
                return backend.textWidth(id, text);
            });
        }

        @Override
        public void deleteFont(int id) {
            backend.deleteFont(id);
        }

        @Override
        public void defineImage(int id, Bitmap image) {
            guard(() -> {
                backend.defineImage(id, image);
                return null;
            });
        }

        @Override
        public void record(byte[] list, byte[] composition, double clock, int width, int height, float density,
                float layoutMs, int passes, int builds) {
            byte[] whole = large(list);
            guard(() -> {
                if (width < 0 || height < 0 || width > 16384 || height > 16384 || !(density > 0 && density <= 10))
                    throw new IllegalArgumentException("Frame size");
                backend.record(whole, composition == null ? new byte[] {'{', '}'} : composition, clock, width, height,
                        density, layoutMs, passes, builds);
                return null;
            });
        }

        @Override
        public void recordLayer(int index, int version, byte[] list) {
            byte[] whole = large(list);
            guard(() -> {
                backend.recordLayer(index, version, whole);
                return null;
            });
        }

        @Override
        public void selectableText(byte[] runs) {
            byte[] whole = large(runs);
            guard(() -> {
                backend.selectableText(whole);
                return null;
            });
        }

        @Override
        public void signal(int kind, byte[] message) {
            byte[] whole = large(message);
            guard(() -> {
                backend.signal(kind, whole);
                return null;
            });
        }

        @Override
        public void canvasRun(int id, int width, int height, int[] ops) {
            guard(() -> {
                backend.canvasRun(id, width, height, ops == null ? new int[0] : ops);
                return null;
            });
        }

        @Override
        public byte[] canvasRead(int id, int x, int y, int width, int height) {
            return guard(() -> backend.canvasRead(id, x, y, width, height));
        }

        @Override
        public void canvasRelease(int id) {
            guard(() -> {
                backend.canvasRelease(id);
                return null;
            });
        }
    }
}
