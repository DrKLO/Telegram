package org.telegram.messenger.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import com.google.common.util.concurrent.AtomicDouble;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueuePoolBackground;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BitmapsCache {

    public final static int FRAME_RESULT_OK = 0;
    public final static int FRAME_RESULT_NO_FRAME = -1;
    public static final int COMPRESS_QUALITY_DEFAULT = 60;
    private final Cacheable source;
    private static boolean mkdir;
    String fileName;
    int w;
    int h;
    public final AtomicInteger framesProcessed = new AtomicInteger(0);

    ArrayList<FrameOffset> frameOffsets = new ArrayList<>();

    final boolean useSharedBuffers;
    final static ConcurrentHashMap<Thread, byte[]> sharedBuffers = new ConcurrentHashMap<>();
    static volatile boolean cleanupScheduled;
    byte[] bufferTmp;

    private final static int N = Utilities.clamp(Runtime.getRuntime().availableProcessors() - 2, 6, 1);
    private static ThreadPoolExecutor bitmapCompressExecutor;
    private final Object mutex = new Object();
    private int frameIndex;
    boolean error;
    volatile boolean fileExist;
    int compressQuality;

    final File file;

    private int tryCount;

    public AtomicBoolean cancelled = new AtomicBoolean(false);
    private Runnable cleanupSharedBuffers = new Runnable() {
        @Override
        public void run() {
            for (Thread thread : sharedBuffers.keySet()) {
                if (!thread.isAlive()) {
                    sharedBuffers.remove(thread);
                }
            }
            if (!sharedBuffers.isEmpty()) {
                AndroidUtilities.runOnUIThread(cleanupSharedBuffers, 5000);
            } else {
                cleanupScheduled = false;
            }
        }
    };

    public BitmapsCache(File sourceFile, Cacheable source, CacheOptions options, int w, int h, boolean noLimit) {
        this.source = source;
        this.w = w;
        this.h = h;
        compressQuality = options.compressQuality;
        fileName = sourceFile.getName();
        if (bitmapCompressExecutor == null) {
            bitmapCompressExecutor = new ThreadPoolExecutor(N, N, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }

        File fileTmo = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
        if (!mkdir) {
            fileTmo.mkdir();
            mkdir = true;
        }
        file = new File(fileTmo, fileName + "_" + w + "_" + h + (noLimit ? "_nolimit" : " ") + ".pcache2");
        useSharedBuffers = w < AndroidUtilities.dp(60) && h < AndroidUtilities.dp(60);

        // check cache created in file load queue only for high devices
        if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
            fileExist = file.exists();
            if (fileExist) {
                RandomAccessFile randomAccessFile = null;
                try {
                    randomAccessFile = new RandomAccessFile(file, "r");
                    cacheCreated = randomAccessFile.readBoolean();
                    if (cacheCreated && frameOffsets.isEmpty()) {
                        randomAccessFile.seek(randomAccessFile.readInt());
                        int count = randomAccessFile.readInt();
                        if (count > 10_000) {
                            count = 0;
                        }
                        fillFrames(randomAccessFile, count);
                        if (frameOffsets.size() == 0) {
                            cacheCreated = false;
                            fileExist = false;
                            checked = true;
                            file.delete();
                        } else {
                            if (cachedFile != randomAccessFile) {
                                closeCachedFile();
                            }
                            cachedFile = randomAccessFile;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    file.delete();
                    fileExist = false;
                    checked = true;
                } finally {
                    try {
                        if (cachedFile != randomAccessFile && randomAccessFile != null) {
                            randomAccessFile.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            checked = true;
        } else {
            fileExist = false;
            cacheCreated = false;
        }
    }

    public volatile boolean checked;
    volatile boolean checkCache;
    volatile boolean cacheCreated;
    volatile boolean recycled;

    RandomAccessFile cachedFile;
    BitmapFactory.Options options;

    private static int taskCounter;
    private static CacheGeneratorSharedTools sharedTools;

    public static void incrementTaskCounter() {
        taskCounter++;
    }

    public static void decrementTaskCounter() {
        taskCounter--;
        if (taskCounter <= 0) {
            taskCounter = 0;
            RLottieDrawable.lottieCacheGenerateQueue.postRunnable(() -> {
                if (sharedTools != null) {
                    sharedTools.release();
                    sharedTools = null;
                }
            });
        }

    }

    public void createCache() {
        try {
            if (file.exists()) {
                RandomAccessFile randomAccessFile = null;
                try {
                    randomAccessFile = new RandomAccessFile(file, "r");
                    cacheCreated = randomAccessFile.readBoolean();
                    if (cacheCreated) {
                        frameOffsets.clear();
                        randomAccessFile.seek(randomAccessFile.readInt());
                        int count = randomAccessFile.readInt();
                        if (count > 10_000) {
                            count = 0;
                        }
                        if (count > 0) {
                            fillFrames(randomAccessFile, count);
                            randomAccessFile.seek(0);
                            if (cachedFile != randomAccessFile) {
                                closeCachedFile();
                            }
                            cachedFile = randomAccessFile;
                            fileExist = true;
                            checked = true;
                            return;
                        } else {
                            fileExist = false;
                            cacheCreated = false;
                            checked = true;
                        }
                    }
                    if (!cacheCreated) {
                        file.delete();
                    }
                } catch (Throwable e) {
                    try {
                        file.delete();
                    } catch (Throwable e2) {

                    }
                } finally {
                    if (cachedFile != randomAccessFile && randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (Throwable e2) {

                        }
                    }
                }
            }

            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

            if (sharedTools == null) {
                sharedTools = new CacheGeneratorSharedTools();
            }
            sharedTools.allocate(h, w);
            Bitmap[] bitmap = sharedTools.bitmap;
            ImmutableByteArrayOutputStream[] byteArrayOutputStream = sharedTools.byteArrayOutputStream;
            CountDownLatch[] countDownLatch = new CountDownLatch[N];

            ArrayList<FrameOffset> frameOffsets = new ArrayList<>();
            RandomAccessFile finalRandomAccessFile = randomAccessFile;

            finalRandomAccessFile.writeBoolean(false);
            finalRandomAccessFile.writeInt(0);

            int index = 0;
            int framePosition = 0;

            AtomicBoolean closed = new AtomicBoolean(false);
            source.prepareForGenerateCache();

            while (true) {
                if (countDownLatch[index] != null) {
                    try {
                        countDownLatch[index].await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (cancelled.get() || closed.get()) {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("cancelled cache generation");
                    }
                    closed.set(true);
                    for (int i = 0; i < N; i++) {
                        if (countDownLatch[i] != null) {
                            try {
                                countDownLatch[i].await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (bitmap[i] != null) {
                            try {
                                bitmap[i].recycle();
                            } catch (Exception e) {

                            }
                        }
                    }
                    randomAccessFile.close();
                    source.releaseForGenerateCache();
                    return;
                }

                if (source.getNextFrame(bitmap[index]) != 1) {
                    break;
                }
                countDownLatch[index] = new CountDownLatch(1);


                int finalIndex = index;
                int finalFramePosition = framePosition;
                RandomAccessFile finalRandomAccessFile1 = randomAccessFile;
                bitmapCompressExecutor.execute(() -> {
                    if (cancelled.get() || closed.get()) {
                        return;
                    }

                    Bitmap.CompressFormat format = Bitmap.CompressFormat.WEBP;
                    if (Build.VERSION.SDK_INT <= 28) {
                        format = Bitmap.CompressFormat.PNG;
                    }
                    bitmap[finalIndex].compress(format, compressQuality, byteArrayOutputStream[finalIndex]);
                    int size = byteArrayOutputStream[finalIndex].count;

                    try {
                        synchronized (mutex) {
                            FrameOffset frameOffset = new FrameOffset(finalFramePosition);
                            frameOffset.frameOffset = (int) finalRandomAccessFile1.length();

                            frameOffsets.add(frameOffset);

                            finalRandomAccessFile1.write(byteArrayOutputStream[finalIndex].buf, 0, size);
                            frameOffset.frameSize = size;
                            byteArrayOutputStream[finalIndex].reset();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            finalRandomAccessFile1.close();
                        } catch (Exception e2) {
                        } finally {
                            closed.set(true);
                        }
                    }

                    countDownLatch[finalIndex].countDown();
                });

                index++;
                framePosition++;
                if (index >= N) {
                    index = 0;
                }
                framesProcessed.set(framePosition);
            }
            for (int i = 0; i < N; i++) {
                if (countDownLatch[i] != null) {
                    try {
                        countDownLatch[i].await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            int arrayOffset = (int) randomAccessFile.length();

            Collections.sort(frameOffsets, Comparator.comparingInt(o -> o.index));
            byteArrayOutputStream[0].reset();
            int count = frameOffsets.size();
            byteArrayOutputStream[0].writeInt(count);
            for (int i = 0; i < frameOffsets.size(); i++) {
                byteArrayOutputStream[0].writeInt(frameOffsets.get(i).frameOffset);
                byteArrayOutputStream[0].writeInt(frameOffsets.get(i).frameSize);
            }
            randomAccessFile.write(byteArrayOutputStream[0].buf, 0, 4 + 4 * 2 * count);
            byteArrayOutputStream[0].reset();
            randomAccessFile.seek(0);
            randomAccessFile.writeBoolean(true);
            randomAccessFile.writeInt(arrayOffset);
            closed.set(true);
            randomAccessFile.close();

            this.frameOffsets.clear();
            this.frameOffsets.addAll(frameOffsets);
            closeCachedFile();
            cachedFile = new RandomAccessFile(file, "r");
            cacheCreated = true;
            fileExist = true;
            checked = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            source.releaseForGenerateCache();
        }
    }

    private void fillFrames(RandomAccessFile randomAccessFile, int count) throws Throwable {
        if (count == 0) {
            return;
        }
        byte[] bytes = new byte[4 * 2 * count];
        randomAccessFile.read(bytes);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        for (int i = 0; i < count; i++) {
            FrameOffset frameOffset = new FrameOffset(i);
            frameOffset.frameOffset = byteBuffer.getInt();
            frameOffset.frameSize = byteBuffer.getInt();
            frameOffsets.add(frameOffset);
        }
    }

    public void cancelCreate() {
//        cancelled.set(true);
    }

    public int getFrame(Bitmap bitmap, Metadata metadata) {
        int res = getFrame(frameIndex, bitmap);
        metadata.frame = frameIndex;
        if (cacheCreated && !frameOffsets.isEmpty()) {
            frameIndex++;
            if (frameIndex >= frameOffsets.size()) {
                frameIndex = 0;
            }
        }
        return res;
    }

    public boolean cacheExist() {
        if (checkCache) {
            return cacheCreated;
        }
        RandomAccessFile randomAccessFile = null;
        int framesCount;
        try {
            synchronized (mutex) {
                randomAccessFile = new RandomAccessFile(file, "r");
                cacheCreated = randomAccessFile.readBoolean();
                randomAccessFile.seek(randomAccessFile.readInt());
                framesCount = randomAccessFile.readInt();
                if (framesCount <= 0) {
                    cacheCreated = false;
                    checked = true;
                }
            }
        } catch (Exception e) {

        } finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        checkCache = true;
        return cacheCreated;
    }

    public int getFrame(int index, Bitmap bitmap) {
        if (error) {
            return FRAME_RESULT_NO_FRAME;
        }
        RandomAccessFile randomAccessFile = null;
        try {
            FrameOffset selectedFrame;
            if (!cacheCreated && !fileExist) {
                return FRAME_RESULT_NO_FRAME;
            }
            byte[] bufferTmp;
            if (!cacheCreated || cachedFile == null) {
                randomAccessFile = new RandomAccessFile(file, "r");
                cacheCreated = randomAccessFile.readBoolean();
                if (cacheCreated && frameOffsets.isEmpty()) {
                    randomAccessFile.seek(randomAccessFile.readInt());
                    int count = randomAccessFile.readInt();
                    fillFrames(randomAccessFile, count);
                }
                if (frameOffsets.size() == 0) {
                    cacheCreated = false;
                    checked = true;
                }

                if (!cacheCreated) {
                    randomAccessFile.close();
                    randomAccessFile = null;
                    return FRAME_RESULT_NO_FRAME;
                }
            } else {
                randomAccessFile = cachedFile;
            }
            if (frameOffsets.size() == 0) {
                return FRAME_RESULT_NO_FRAME;
            }
            index = Utilities.clamp(index, frameOffsets.size() - 1, 0);
            selectedFrame = frameOffsets.get(index);
            randomAccessFile.seek(selectedFrame.frameOffset);

            bufferTmp = getBuffer(selectedFrame);
            randomAccessFile.readFully(bufferTmp, 0, selectedFrame.frameSize);
            if (!recycled) {
                if (cachedFile != randomAccessFile) {
                    closeCachedFile();
                }
                cachedFile = randomAccessFile;
            } else {
                cachedFile = null;
                randomAccessFile.close();
            }

            if (options == null) {
                options = new BitmapFactory.Options();
            }
            options.inBitmap = bitmap;
            BitmapFactory.decodeByteArray(bufferTmp, 0, selectedFrame.frameSize, options);
            options.inBitmap = null;
            return FRAME_RESULT_OK;
        } catch (FileNotFoundException e) {

        } catch (Throwable e) {
            FileLog.e(e, false);
            tryCount++;
            if (tryCount > 10) {
                error = true;
            }
        }

        if (error && randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // source.getFirstFrame(bitmap);
        return FRAME_RESULT_NO_FRAME;
    }

    private void closeCachedFile() {
        if (cachedFile != null) {
            try {
                cachedFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] getBuffer(FrameOffset selectedFrame) {
        boolean useSharedBuffers = this.useSharedBuffers && Thread.currentThread().getName().startsWith(DispatchQueuePoolBackground.THREAD_PREFIX);
        byte[] bufferTmp;
        if (useSharedBuffers) {
            bufferTmp = sharedBuffers.get(Thread.currentThread());
        } else {
            bufferTmp = this.bufferTmp;
        }

        if (bufferTmp == null || bufferTmp.length < selectedFrame.frameSize) {
            bufferTmp = new byte[(int) (selectedFrame.frameSize * 1.3f)];
            if (useSharedBuffers) {
                sharedBuffers.put(Thread.currentThread(), bufferTmp);
                if (!cleanupScheduled) {
                    cleanupScheduled = true;
                    AndroidUtilities.runOnUIThread(cleanupSharedBuffers, 5000);
                }
            } else {
                this.bufferTmp = bufferTmp;
            }
        }
        return bufferTmp;
    }

    public boolean needGenCache() {
        return !cacheCreated || !fileExist;
    }

    public void recycle() {
        if (cachedFile != null) {
            try {
                cachedFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cachedFile = null;
        }
        recycled = true;
    }

    public int getFrameCount() {
        return frameOffsets.size();
    }

    public boolean isCreated() {
        return cacheCreated && fileExist;
    }

    private class FrameOffset {
        final int index;
        int frameSize;
        int frameOffset;

        private FrameOffset(int index) {
            this.index = index;
        }
    }

    public interface Cacheable {
        void prepareForGenerateCache();

        int getNextFrame(Bitmap bitmap);

        void releaseForGenerateCache();

        Bitmap getFirstFrame(Bitmap bitmap);
    }

    public static class Metadata {
        public int frame;
    }

    public static class CacheOptions {
        public int compressQuality = 100;
        public boolean fallback = false;
        public boolean firstFrame;
    }

    private static class CacheGeneratorSharedTools {
        ImmutableByteArrayOutputStream[] byteArrayOutputStream = new ImmutableByteArrayOutputStream[N];
        private Bitmap[] bitmap = new Bitmap[N];

        private int lastSize;

        void allocate(int h, int w) {
            int size = (w << 16) + h;
            boolean recreateBitmaps = false;
            if (lastSize != size) {
                recreateBitmaps = true;
            }
            lastSize = size;
            for (int i = 0; i < N; i++) {
                if (recreateBitmaps || bitmap[i] == null) {
                    if (bitmap[i] != null) {
                        Bitmap bitmapToRecycle = bitmap[i];
                        Utilities.globalQueue.postRunnable(() -> {
                            try {
                                bitmapToRecycle.recycle();
                            } catch (Exception e) {

                            }
                        });
                    }
                    bitmap[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                }
                if (byteArrayOutputStream[i] == null) {
                    byteArrayOutputStream[i] = new ImmutableByteArrayOutputStream(w * h * 2);
                }
            }
        }

        void release() {
            ArrayList<Bitmap> bitmapsToRecycle = null;
            for (int i = 0; i < N; i++) {
                if (bitmap[i] != null) {
                    if (bitmapsToRecycle == null) {
                        bitmapsToRecycle = new ArrayList<>();
                    }
                    bitmapsToRecycle.add(bitmap[i]);
                }
                bitmap[i] = null;
                byteArrayOutputStream[i] = null;
            }
            if (!bitmapsToRecycle.isEmpty()) {
                ArrayList<Bitmap> finalBitmapsToRecycle = bitmapsToRecycle;
                Utilities.globalQueue.postRunnable(() -> {
                    for (Bitmap bitmap : finalBitmapsToRecycle) {
                        bitmap.recycle();
                    }
                });
            }
        }

    }
}
