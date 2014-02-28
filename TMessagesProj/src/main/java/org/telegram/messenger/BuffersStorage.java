/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BuffersStorage {
    public static BuffersStorage Instance = new BuffersStorage();

    private final ConcurrentLinkedQueue<ByteBufferDesc> freeBuffers128;
    private final ConcurrentLinkedQueue<ByteBufferDesc> freeBuffers1024;
    private final ConcurrentLinkedQueue<ByteBufferDesc> freeBuffers4096;
    private final ConcurrentLinkedQueue<ByteBufferDesc> freeBuffers16384;
    private final ConcurrentLinkedQueue<ByteBufferDesc> freeBuffers32768;

    public BuffersStorage() {
        freeBuffers128 = new ConcurrentLinkedQueue<ByteBufferDesc>();
        freeBuffers1024 = new ConcurrentLinkedQueue<ByteBufferDesc>();
        freeBuffers4096 = new ConcurrentLinkedQueue<ByteBufferDesc>();
        freeBuffers16384 = new ConcurrentLinkedQueue<ByteBufferDesc>();
        freeBuffers32768 = new ConcurrentLinkedQueue<ByteBufferDesc>();

        for (int a = 0; a < 5; a++) {
            freeBuffers128.add(new ByteBufferDesc(128));
        }
        for (int a = 0; a < 5; a++) {
            freeBuffers1024.add(new ByteBufferDesc(1024 + 200));
        }
        for (int a = 0; a < 2; a++) {
            freeBuffers4096.add(new ByteBufferDesc(4096 + 200));
        }
        for (int a = 0; a < 2; a++) {
            freeBuffers16384.add(new ByteBufferDesc(16384 + 200));
        }
        for (int a = 0; a < 2; a++) {
            freeBuffers32768.add(new ByteBufferDesc(40000));
        }
    }

    public ByteBufferDesc getFreeBuffer(int size) {
        ByteBufferDesc buffer = null;
        if (size <= 128) {
            synchronized (freeBuffers128) {
                buffer = freeBuffers128.poll();
            }
            if (buffer == null) {
                buffer = new ByteBufferDesc(128);
                FileLog.e("tmessages", "create new 128 buffer");
            }
        } else if (size <= 1024 + 200) {
            synchronized (freeBuffers1024) {
                buffer = freeBuffers1024.poll();
            }
            if (buffer == null) {
                buffer = new ByteBufferDesc(1024 + 200);
                FileLog.e("tmessages", "create new 1024 buffer");
            }
        } else if (size <= 4096 + 200) {
            synchronized (freeBuffers4096) {
                buffer = freeBuffers4096.poll();
            }
            if (buffer == null) {
                buffer = new ByteBufferDesc(4096 + 200);
                FileLog.e("tmessages", "create new 4096 buffer");
            }
        } else if (size <= 16384 + 200) {
            synchronized (freeBuffers16384) {
                buffer = freeBuffers16384.poll();
            }
            if (buffer == null) {
                buffer = new ByteBufferDesc(16384 + 200);
                FileLog.e("tmessages", "create new 16384 buffer");
            }
        } else if (size <= 40000) {
            synchronized (freeBuffers32768) {
                buffer = freeBuffers32768.poll();
            }
            if (buffer == null) {
                buffer = new ByteBufferDesc(40000);
                FileLog.e("tmessages", "create new 40000 buffer");
            }
        } else {
            buffer = new ByteBufferDesc(size);
        }
        buffer.buffer.limit(size).rewind();
        return buffer;
    }

    public void reuseFreeBuffer(ByteBufferDesc buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.buffer.capacity() == 128) {
            synchronized (freeBuffers128) {
                if (freeBuffers128.contains(buffer)) {
                    throw new RuntimeException("already containing buffer! 0");
                }
                freeBuffers128.add(buffer);
            }
        } else if (buffer.buffer.capacity() == 1024 + 200) {
            synchronized (freeBuffers1024) {
                if (freeBuffers1024.contains(buffer)) {
                    throw new RuntimeException("already containing buffer! 1");
                }
                freeBuffers1024.add(buffer);
            }
        } else if (buffer.buffer.capacity() == 4096 + 200) {
            synchronized (freeBuffers4096) {
                if (freeBuffers4096.contains(buffer)) {
                    throw new RuntimeException("already containing buffer! 2");
                }
                freeBuffers4096.add(buffer);
            }
        } else if (buffer.buffer.capacity() == 16384 + 200) {
            synchronized (freeBuffers16384) {
                if (freeBuffers16384.contains(buffer)) {
                    throw new RuntimeException("already containing buffer! 3");
                }
                freeBuffers16384.add(buffer);
            }
        } else if (buffer.buffer.capacity() == 40000) {
            synchronized (freeBuffers32768) {
                if (freeBuffers32768.contains(buffer)) {
                    throw new RuntimeException("already containing buffer! 4");
                }
                freeBuffers32768.add(buffer);
            }
        }
    }
}
