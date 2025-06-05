package org.telegram.messenger.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFileDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class VideoFramesRewinder {

    private int maxFramesCount;
    private int maxFrameSide;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private View parentView;
    int w, h;

    public VideoFramesRewinder() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                maxFramesCount = 400;
                maxFrameSide = 720;
                break;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                maxFramesCount = 200;
                maxFrameSide = 580;
                break;
            default:
                maxFramesCount = 100;
                maxFrameSide = 480;
                break;
        }
    }

    public void draw(Canvas canvas, int w, int h) {
        this.w = w;
        this.h = h;
        if (ptr != 0 && currentFrame != null) {
            canvas.save();
            canvas.scale(w / (float) currentFrame.bitmap.getWidth(), h / (float) currentFrame.bitmap.getHeight());
            canvas.drawBitmap(currentFrame.bitmap, 0, 0, paint);
            canvas.restore();
        }
    }

    private long ptr;
    private final int[] meta = new int[6];

    public boolean isReady() {
        return ptr != 0;
    }

    public void setup(File file) {
        if (file == null) {
            release();
            return;
        }
        stop.set(false);
        ptr = AnimatedFileDrawable.createDecoder(file.getAbsolutePath(), meta, UserConfig.selectedAccount, 0, null, true);
    }

    private final ArrayList<Frame> freeFrames = new ArrayList<>();
    private final TreeSet<Frame> frames = new TreeSet<Frame>((a, b) -> {
        return (int) (a.position - b.position);
    });
    private Frame currentFrame;

    private class Frame {
        long position;
        Bitmap bitmap;
    }

    private AtomicBoolean stop = new AtomicBoolean(false);
    private AtomicLong until = new AtomicLong(0);
    private boolean isPreparing;
    private long lastSeek;
    private float lastSpeed = 1.0f;
    private long prepareToMs;
    private float prepareWithSpeed;
    private boolean destroyAfterPrepare;
    private Runnable prepareRunnable = () -> {
        final ArrayList<Frame> newFrames = new ArrayList<>();

        final long start = System.currentTimeMillis();

        final int fps = meta[4];
        int w = Math.min(this.w / 4, meta[0]), h = Math.min(this.h / 4, meta[1]);
        if (w > maxFrameSide || h > maxFrameSide) {
            final float scale = (float) maxFrameSide / Math.max(w, h);
            w = (int) (w * scale);
            h = (int) (h * scale);
        }
        final long toMs = prepareToMs;
        AnimatedFileDrawable.seekToMs(ptr, toMs - (long) (350 * prepareWithSpeed), meta, false);
        long ms = meta[3];
        int triesCount = 0;
        for (int i = 0; meta[3] <= until.get() && i < maxFramesCount && !stop.get(); ++i) {
            long nextms = (long) (ms + (1000.0f / fps) * prepareWithSpeed);
            Frame frame;
            if (!freeFrames.isEmpty()) {
                frame = freeFrames.remove(0);
            } else {
                frame = new Frame();
            }
            if (frame.bitmap == null || frame.bitmap.getWidth() != w || frame.bitmap.getHeight() != h) {
                AndroidUtilities.recycleBitmap(frame.bitmap);
                try {
                    frame.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError e) {
                    FileLog.d("[VideoFramesRewinder] failed to create bitmap: out of memory");
                    break;
                }
            }
            while (meta[3] + (long) Math.ceil(1000.0f / fps) < nextms) {
                AnimatedFileDrawable.getVideoFrame(ptr, null, meta, 0, true, 0, meta[4], false);
            }
            if (0 == AnimatedFileDrawable.getVideoFrame(ptr, frame.bitmap, meta, frame.bitmap.getRowBytes(), true, 0, meta[4], false)) {
                triesCount++;
                if (triesCount > 6) break;
                continue;
            }
            ms = frame.position = meta[3];
            newFrames.add(frame);
        }

        AndroidUtilities.runOnUIThread(() -> {
            FileLog.d("[VideoFramesRewinder] total prepare of " + newFrames.size() + " took " + (System.currentTimeMillis() - start) + "ms");
            if (!newFrames.isEmpty()) {
                FileLog.d("[VideoFramesRewinder] prepared from " + newFrames.get(0).position + "ms to " + newFrames.get(newFrames.size() - 1).position + "ms (requested up to "+prepareToMs+"ms)");
            }
            isPreparing = false;
            final Iterator<Frame> i = frames.iterator();
            while (i.hasNext()) {
                final Frame f = i.next();
                if (currentFrame != f && f.position > lastSeek) {
                    if (freeFrames.size() > 20) {
                        AndroidUtilities.recycleBitmap(f.bitmap);
                    } else {
                        freeFrames.add(f);
                    }
                    i.remove();
                }
            }
            while (!newFrames.isEmpty() && frames.size() < maxFramesCount) {
                frames.add(newFrames.remove(newFrames.size() - 1));
            }
            if (newFrames.size() > 0) {
                FileLog.d("[VideoFramesRewinder] prepared "+newFrames.size()+" more frames than I could fit :(");
            }

            if (destroyAfterPrepare) {
                release();
                stop.set(false);
            }
        });
    };
    private void prepare(long toMs) {
        if (isPreparing) {
            return;
        }
        FileLog.d("[VideoFramesRewinder] starting preparing " + toMs + "ms");
        isPreparing = true;
        prepareToMs = toMs;
        prepareWithSpeed = lastSpeed;
        Utilities.themeQueue.postRunnable(prepareRunnable);
    }

    public void seek(long position, float currentSpeed) {
        if (ptr == 0) return;

        lastSeek = position;
        lastSpeed = currentSpeed;
        until.set(position);

        final Iterator<Frame> i = frames.iterator();
        final ArrayList<Long> pastPositions = new ArrayList<>();
        while (i.hasNext()) {
            final Frame f = i.next();
            pastPositions.add(f.position);
            if (Math.abs(f.position - position) < 25 * currentSpeed) {
                if (currentFrame != f) {
                    FileLog.d("[VideoFramesRewinder] found a frame " + f.position + "ms to fit to "+position+"ms from " + frames.size() + " frames");
                    currentFrame = f;
                    invalidate();

                    int deleted = 0;
                    while (i.hasNext()) {
                        i.next();
                        i.remove();
                        deleted++;
                    }
                    if (deleted > 0) {
                        FileLog.d("[VideoFramesRewinder] also deleted " + deleted + " frames after this frame");
                    }
                }
                for (int j = pastPositions.size() - 2; j >= 0; --j) {
                    final long next = pastPositions.get(j + 1);
                    final long pos = pastPositions.get(j);
                    if (Math.abs(next - pos) > 25 * currentSpeed) {
                        prepare(pos);
                        return;
                    }
                }
                prepare(Math.max(0, frames.first().position - 20));
                return;
            }
        }
        FileLog.d("[VideoFramesRewinder] didn't find a frame, wanting to prepare " + position + "ms");
        prepare(Math.max(0, position));
    }

    public void clearCurrent() {
        if (currentFrame != null) {
            currentFrame = null;
            invalidate();
        }
    }

    public void release() {
        if (isPreparing) {
            stop.set(true);
            destroyAfterPrepare = true;
            return;
        }
        AnimatedFileDrawable.destroyDecoder(ptr);
        ptr = 0;
        destroyAfterPrepare = false;
        clearCurrent();
        until.set(0);

        for (Frame f : frames) {
            AndroidUtilities.recycleBitmap(f.bitmap);
        }
        frames.clear();
        for (Frame f : freeFrames) {
            AndroidUtilities.recycleBitmap(f.bitmap);
        }
        freeFrames.clear();
    }

    public void setParentView(View view) {
        parentView = view;
    }

    private void invalidate() {
        if (parentView != null) {
            parentView.invalidate();
        }
    }
}
