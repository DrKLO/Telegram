/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BuffersStorage {
    public static BuffersStorage Instance = new BuffersStorage();

    private List<ByteBufferDesc> freeBuffers128;
    private List<ByteBufferDesc> freeBuffers1024;
    private List<ByteBufferDesc> freeBuffers4096;
    private List<ByteBufferDesc> freeBuffers16384;
    private List<ByteBufferDesc> freeBuffers32768;

    private ReentrantLock lock = new ReentrantLock();

    public BuffersStorage() {
        freeBuffers128 = new ArrayList<ByteBufferDesc>();
        freeBuffers1024 = new ArrayList<ByteBufferDesc>();
        freeBuffers4096 = new ArrayList<ByteBufferDesc>();
        freeBuffers16384 = new ArrayList<ByteBufferDesc>();
        freeBuffers32768 = new ArrayList<ByteBufferDesc>();

        for (int i = 0; i < 5; i++) {
            freeBuffers128.add(new ByteBufferDesc(128));
            freeBuffers1024.add(new ByteBufferDesc(1024 + 200));
        }

        for (int i = 0; i < 2; i++) {
            freeBuffers4096.add(new ByteBufferDesc(4096 + 200));
            freeBuffers16384.add(new ByteBufferDesc(16384 + 200));
            freeBuffers32768.add(new ByteBufferDesc(40000));
        }
    }

    public ByteBufferDesc getFreeBuffer(int size) {
        if (size <= 128) {
            return getFreeBuffer(freeBuffers128, 128);
        } else if (size <= 1024 + 200) {
            return getFreeBuffer(freeBuffers1024, 1024, 200);
        } else if (size <= 4096 + 200) {
            return getFreeBuffer(freeBuffers4096, 4096, 200);
        } else if (size <= 16384 + 200) {
            return getFreeBuffer(freeBuffers16384, 16384, 200);
        } else if (size <= 40000) {
            return getFreeBuffer(freeBuffers32768, 40000);
        } else {
            ByteBufferDesc buffer = new ByteBufferDesc(size);
            buffer.buffer.limit(size).rewind();
            return buffer;
        }
    }

    public void reuseFreeBuffer(ByteBufferDesc buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.buffer.capacity() == 128) {
            reuseFreeBuffer(freeBuffers128, buffer);
        } else if (buffer.buffer.capacity() == 1024 + 200) {
            reuseFreeBuffer(freeBuffers1024, buffer);
        } else if (buffer.buffer.capacity() == 4096 + 200) {
            reuseFreeBuffer(freeBuffers4096, buffer);
        } else if (buffer.buffer.capacity() == 16384 + 200) {
            reuseFreeBuffer(freeBuffers16384, buffer);
        } else if (buffer.buffer.capacity() == 40000) {
            reuseFreeBuffer(freeBuffers32768, buffer);
        }
    }

    private ByteBufferDesc getFreeBuffer(List<ByteBufferDesc> freeBuffersOfSize, int size) {
        return getFreeBuffer(freeBuffersOfSize, size, 0);
    }

    private ByteBufferDesc getFreeBuffer(List<ByteBufferDesc> freeBuffersOfSize, int size, int overhead) {
        lock.lock();
        try {
            ByteBufferDesc buffer;
            if (freeBuffersOfSize.size() > 0) {
                buffer = freeBuffersOfSize.get(0);
                freeBuffersOfSize.remove(0);
            } else {
                buffer = new ByteBufferDesc(size + overhead);
                FileLog.e("tmessages", "create new " + size + "buffer");
            }
            buffer.buffer.limit(size).rewind();
            return buffer;
        } finally {
            lock.unlock();
        }
    }

    private void reuseFreeBuffer(List<ByteBufferDesc> freeBuffersOfSize, ByteBufferDesc buffer) {
        lock.lock();
        try {
            freeBuffersOfSize.add(buffer);
        } finally {
            lock.unlock();
        }
    }
}
