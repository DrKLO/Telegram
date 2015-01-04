/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import java.util.ArrayList;

public class BuffersStorage {

    private final ArrayList<ByteBufferDesc> freeBuffers128;
    private final ArrayList<ByteBufferDesc> freeBuffers1024;
    private final ArrayList<ByteBufferDesc> freeBuffers4096;
    private final ArrayList<ByteBufferDesc> freeBuffers16384;
    private final ArrayList<ByteBufferDesc> freeBuffers32768;
    private final ArrayList<ByteBufferDesc> freeBuffersBig;
    private boolean isThreadSafe;
    private final static Object sync = new Object();

    private static volatile BuffersStorage Instance = null;
    public static BuffersStorage getInstance() {
        BuffersStorage localInstance = Instance;
        if (localInstance == null) {
            synchronized (BuffersStorage.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new BuffersStorage(true);
                }
            }
        }
        return localInstance;
    }

    public BuffersStorage(boolean threadSafe) {
        isThreadSafe = threadSafe;
        freeBuffers128 = new ArrayList<ByteBufferDesc>();
        freeBuffers1024 = new ArrayList<ByteBufferDesc>();
        freeBuffers4096 = new ArrayList<ByteBufferDesc>();
        freeBuffers16384 = new ArrayList<ByteBufferDesc>();
        freeBuffers32768 = new ArrayList<ByteBufferDesc>();
        freeBuffersBig = new ArrayList<ByteBufferDesc>();

        for (int a = 0; a < 5; a++) {
            freeBuffers128.add(new ByteBufferDesc(128));
        }
    }

    public ByteBufferDesc getFreeBuffer(int size) {
        if (size <= 0) {
            return null;
        }
        int byteCount = 0;
        ArrayList<ByteBufferDesc> arrayToGetFrom = null;
        ByteBufferDesc buffer = null;
        if (size <= 128) {
            arrayToGetFrom = freeBuffers128;
            byteCount = 128;
        } else if (size <= 1024 + 200) {
            arrayToGetFrom = freeBuffers1024;
            byteCount = 1024 + 200;
        } else if (size <= 4096 + 200) {
            arrayToGetFrom = freeBuffers4096;
            byteCount = 4096 + 200;
        } else if (size <= 16384 + 200) {
            arrayToGetFrom = freeBuffers16384;
            byteCount = 16384 + 200;
        } else if (size <= 40000) {
            arrayToGetFrom = freeBuffers32768;
            byteCount = 40000;
        } else if (size <= 280000) {
            arrayToGetFrom = freeBuffersBig;
            byteCount = 280000;
        } else {
            buffer = new ByteBufferDesc(size);
        }

        if (arrayToGetFrom != null) {
            if (isThreadSafe) {
                synchronized (sync) {
                    if (arrayToGetFrom.size() > 0) {
                        buffer = arrayToGetFrom.get(0);
                        arrayToGetFrom.remove(0);
                    }
                }
            } else {
                if (arrayToGetFrom.size() > 0) {
                    buffer = arrayToGetFrom.get(0);
                    arrayToGetFrom.remove(0);
                }
            }

            if (buffer == null) {
                buffer = new ByteBufferDesc(byteCount);
                FileLog.e("tmessages", "create new " + byteCount + " buffer");
            }
        }

        buffer.buffer.limit(size).rewind();
        return buffer;
    }

    public void reuseFreeBuffer(ByteBufferDesc buffer) {
        if (buffer == null) {
            return;
        }
        int maxCount = 10;
        ArrayList<ByteBufferDesc> arrayToReuse = null;
        if (buffer.buffer.capacity() == 128) {
            arrayToReuse = freeBuffers128;
        } else if (buffer.buffer.capacity() == 1024 + 200) {
            arrayToReuse = freeBuffers1024;
        } if (buffer.buffer.capacity() == 4096 + 200) {
            arrayToReuse = freeBuffers4096;
        } else if (buffer.buffer.capacity() == 16384 + 200) {
            arrayToReuse = freeBuffers16384;
        } else if (buffer.buffer.capacity() == 40000) {
            arrayToReuse = freeBuffers32768;
        } else if (buffer.buffer.capacity() == 280000) {
            arrayToReuse = freeBuffersBig;
            maxCount = 10;
        }
        if (arrayToReuse != null) {
            if (isThreadSafe) {
                synchronized (sync) {
                    if (arrayToReuse.size() < maxCount) {
                        arrayToReuse.add(buffer);
                    } else {
                        FileLog.e("tmessages", "too more");
                    }
                }
            } else {
                if (arrayToReuse.size() < maxCount) {
                    arrayToReuse.add(buffer);
                }
            }
        }
    }
}
