/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef BUFFERSSTORAGE_H
#define BUFFERSSTORAGE_H

#include <vector>
#include <pthread.h>
#include <stdint.h>

class NativeByteBuffer;

class BuffersStorage {

public:
    BuffersStorage(bool threadSafe);
    NativeByteBuffer *getFreeBuffer(uint32_t size);
    void reuseFreeBuffer(NativeByteBuffer *buffer);
    static BuffersStorage &getInstance();

private:
    std::vector<NativeByteBuffer *> freeBuffers8;
    std::vector<NativeByteBuffer *> freeBuffers128;
    std::vector<NativeByteBuffer *> freeBuffers1024;
    std::vector<NativeByteBuffer *> freeBuffers4096;
    std::vector<NativeByteBuffer *> freeBuffers16384;
    std::vector<NativeByteBuffer *> freeBuffers32768;
    std::vector<NativeByteBuffer *> freeBuffersBig;
    bool isThreadSafe = true;
    pthread_mutex_t mutex;
};

#endif
