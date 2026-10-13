package org.telegram.utils.code.highlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One tokenizer, one worker, coalesced requests and a bounded cache independent of the theme. */
public final class PrismaHighlightingService implements AutoCloseable {
    public interface Tokenizer extends AutoCloseable {
        PrismaHighlighter.Tokenization tokenize(String text, String language);

        String[] getLanguages();

        @Override
        void close();
    }

    public interface Factory {
        Tokenizer create() throws Exception;
    }

    public interface Callback {
        void complete(Result result);
    }

    public interface FailureHandler {
        void failed(Throwable error);
    }

    public static final class Result {
        public final PrismaHighlighter.Tokenization tokens;
        public final Throwable error;

        Result(PrismaHighlighter.Tokenization tokens, Throwable error) {
            this.tokens = tokens;
            this.error = error;
        }
    }

    public static final class Request {
        private volatile Callback callback;

        Request(Callback callback) {
            this.callback = callback;
        }

        /** Cancels this consumer. A shared tokenization may still serve other consumers. */
        public void cancel() {
            callback = null;
        }

        void deliver(Result result) {
            Callback consumer = callback;
            callback = null;
            if (consumer != null) consumer.complete(result);
        }

        boolean isCancelled() {
            return callback == null;
        }
    }

    private static final class Key {
        final String text;
        final String language;
        final int hash;

        Key(String text, String language) {
            this.text = text;
            this.language = language;
            hash = 31 * text.hashCode() + language.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return text.equals(key.text) && language.equals(key.language);
        }
    }

    private static final class Cached {
        final Result result;
        final long bytes;

        Cached(Key key, Result result) {
            this.result = result;
            bytes =
                    128L
                            + 2L * (key.text.length() + key.language.length())
                            + (result.tokens == null ? 1024 : result.tokens.estimatedBytes());
        }
    }

    private final Object lock = new Object();
    private final Factory factory;
    private final FailureHandler failures;
    private final ExecutorService worker;
    private final int entryLimit;
    private final long byteLimit;
    private final LinkedHashMap<Key, Cached> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final HashMap<Key, ArrayList<Request>> pending = new HashMap<>();
    private long cachedBytes;
    private boolean closed;
    private Tokenizer tokenizer;
    private Throwable initializationError;
    private volatile Set<String> languages;

    public PrismaHighlightingService(Factory factory, FailureHandler failures) {
        this(factory, failures, 256, 8L * 1024 * 1024);
    }

    public PrismaHighlightingService(
            Factory factory, FailureHandler failures, int entryLimit, long byteLimit) {
        if (factory == null) throw new NullPointerException("factory");
        if (entryLimit < 0 || byteLimit < 0) throw new IllegalArgumentException("Cache limits");
        this.factory = factory;
        this.failures = failures;
        this.entryLimit = entryLimit;
        this.byteLimit = byteLimit;
        worker =
                Executors.newSingleThreadExecutor(
                        task -> {
                            Thread thread = new Thread(task, "PrismaHighlight");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    public static String normalizeLanguage(String language) {
        if (language == null) return "";
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "fc":
                return "func";
            case "fif":
                return "fift";
            case "tl-b":
                return "tlb";
            case "c++":
                return "cpp";
            case "c#":
                return "csharp";
            default:
                return normalized;
        }
    }

    /** Completion runs on the worker, including cache hits. The UI adapter dispatches to main. */
    public Request request(String text, String language, Callback callback) {
        if (text == null || callback == null) throw new NullPointerException();
        Key key = new Key(text, normalizeLanguage(language));
        Request request = new Request(callback);
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Service is closed");
            ArrayList<Request> consumers = pending.get(key);
            if (consumers != null) {
                consumers.add(request);
                return request;
            }
            consumers = new ArrayList<>();
            consumers.add(request);
            pending.put(key, consumers);
            worker.execute(() -> process(key));
        }
        return request;
    }

    public void prepare() {
        synchronized (lock) {
            if (!closed) worker.execute(this::initialize);
        }
    }

    /** Null until prepare or the first request finishes initialization. */
    public Set<String> getLanguages() {
        return languages;
    }

    private boolean initialize() {
        synchronized (lock) {
            if (closed) return false;
        }
        if (tokenizer != null) return true;
        if (initializationError != null) return false;
        try {
            Tokenizer created = factory.create();
            if (created == null) throw new IllegalStateException("Tokenizer factory returned null");
            try {
                LinkedHashSet<String> identifiers = new LinkedHashSet<>();
                Collections.addAll(identifiers, created.getLanguages());
                if (identifiers.contains("func")) identifiers.add("fc");
                if (identifiers.contains("fift")) identifiers.add("fif");
                if (identifiers.contains("tlb")) identifiers.add("tl-b");
                languages = Collections.unmodifiableSet(identifiers);
                tokenizer = created;
            } catch (RuntimeException | Error error) {
                created.close();
                throw error;
            }
            return true;
        } catch (Exception | LinkageError | OutOfMemoryError error) {
            initializationError = error;
            report(error);
            return false;
        }
    }

    private void process(Key key) {
        Cached hit;
        synchronized (lock) {
            if (closed) return;
            ArrayList<Request> consumers = pending.get(key);
            boolean interested = false;
            if (consumers != null) {
                for (Request consumer : consumers)
                    if (!consumer.isCancelled()) {
                        interested = true;
                        break;
                    }
            }
            if (!interested) {
                pending.remove(key);
                return;
            }
            hit = cache.get(key);
        }
        Result result;
        if (hit != null) {
            result = hit.result;
        } else if (!initialize()) {
            result = new Result(null, initializationError);
        } else {
            try {
                PrismaHighlighter.Tokenization tokens = tokenizer.tokenize(key.text, key.language);
                // Resolve once before weighing the cache entry; no colors are stored.
                tokens.styledRanges();
                result = new Result(tokens, null);
            } catch (RuntimeException | LinkageError | OutOfMemoryError error) {
                report(error);
                result = new Result(null, error);
            }
        }
        ArrayList<Request> consumers;
        synchronized (lock) {
            if (closed) return;
            if (hit == null && result.error == null) put(key, result);
            consumers = pending.remove(key);
        }
        if (consumers == null) return;
        for (Request consumer : consumers) {
            try {
                consumer.deliver(result);
            } catch (RuntimeException | LinkageError error) {
                report(error);
            }
        }
    }

    private void put(Key key, Result result) {
        Cached value = new Cached(key, result);
        if (entryLimit == 0 || value.bytes > byteLimit) return;
        cache.put(key, value);
        cachedBytes += value.bytes;
        while (cache.size() > entryLimit || cachedBytes > byteLimit) {
            Map.Entry<Key, Cached> oldest = cache.entrySet().iterator().next();
            cachedBytes -= oldest.getValue().bytes;
            cache.remove(oldest.getKey());
        }
    }

    private void report(Throwable error) {
        if (failures == null) return;
        try {
            failures.failed(error);
        } catch (RuntimeException | LinkageError ignored) {
            // Logging must not prevent completion/unlocking of consumers.
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            for (ArrayList<Request> consumers : pending.values()) {
                for (Request consumer : consumers) consumer.cancel();
            }
            pending.clear();
            cache.clear();
            cachedBytes = 0;
            worker.execute(
                    () -> {
                        if (tokenizer != null) tokenizer.close();
                    });
            worker.shutdown();
        }
    }
}
