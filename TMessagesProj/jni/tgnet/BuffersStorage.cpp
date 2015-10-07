/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include "BuffersStorage.h"
#include "FileLog.h"
#include "NativeByteBuffer.h"

BuffersStorage &BuffersStorage::getInstance() {
    static BuffersStorage instance(true);
    return instance;
}

BuffersStorage::BuffersStorage(bool threadSafe) {
    isThreadSafe = threadSafe;
    if (isThreadSafe) {
        pthread_mutex_init(&mutex, NULL);
    }
    for (uint32_t a = 0; a < 4; a++) {
        freeBuffers8.push_back(new NativeByteBuffer((uint32_t) 8));
    }
    for (uint32_t a = 0; a < 5; a++) {
        freeBuffers128.push_back(new NativeByteBuffer((uint32_t) 128));
    }
}

NativeByteBuffer *BuffersStorage::getFreeBuffer(uint32_t size) {
    uint32_t byteCount = 0;
    std::vector<NativeByteBuffer *> *arrayToGetFrom = nullptr;
    NativeByteBuffer *buffer = nullptr;
    if (size <= 8) {
        arrayToGetFrom = &freeBuffers8;
        byteCount = 8;
    } else if (size <= 128) {
        arrayToGetFrom = &freeBuffers128;
        byteCount = 128;
    } else if (size <= 1024 + 200) {
        arrayToGetFrom = &freeBuffers1024;
        byteCount = 1024 + 200;
    } else if (size <= 4096 + 200) {
        arrayToGetFrom = &freeBuffers4096;
        byteCount = 4096 + 200;
    } else if (size <= 16384 + 200) {
        arrayToGetFrom = &freeBuffers16384;
        byteCount = 16384 + 200;
    } else if (size <= 40000) {
        arrayToGetFrom = &freeBuffers32768;
        byteCount = 40000;
    } else if (size <= 160000) {
        arrayToGetFrom = &freeBuffersBig;
        byteCount = 160000;
    } else {
        buffer = new NativeByteBuffer(size);
    }

    if (arrayToGetFrom != nullptr) {
        if (isThreadSafe) {
            pthread_mutex_lock(&mutex);
        }
        if (arrayToGetFrom->size() > 0) {
            buffer = (*arrayToGetFrom)[0];
            arrayToGetFrom->erase(arrayToGetFrom->begin());
        }
        if (isThreadSafe) {
            pthread_mutex_unlock(&mutex);
        }
        if (buffer == nullptr) {
            buffer = new NativeByteBuffer(byteCount);
            DEBUG_D("create new %u buffer", byteCount);
        }
    }
    if (buffer != nullptr) {
        buffer->limit(size);
        buffer->rewind();
    }
    return buffer;
}

void BuffersStorage::reuseFreeBuffer(NativeByteBuffer *buffer) {
    if (buffer == nullptr) {
        return;
    }
    std::vector<NativeByteBuffer *> *arrayToReuse = nullptr;
    uint32_t capacity = buffer->capacity();
    uint32_t maxCount = 10;
    if (capacity == 8) {
        arrayToReuse = &freeBuffers8;
        maxCount = 80;
    } else if (capacity == 128) {
        arrayToReuse = &freeBuffers128;
        maxCount = 80;
    } else if (capacity == 1024 + 200) {
        arrayToReuse = &freeBuffers1024;
    } else if (capacity == 4096 + 200) {
        arrayToReuse = &freeBuffers4096;
    } else if (capacity == 16384 + 200) {
        arrayToReuse = &freeBuffers16384;
    } else if (capacity == 40000) {
        arrayToReuse = &freeBuffers32768;
    } else if (capacity == 160000) {
        arrayToReuse = &freeBuffersBig;
    }
    if (arrayToReuse != nullptr) {
        if (isThreadSafe) {
            pthread_mutex_lock(&mutex);
        }
        if (arrayToReuse->size() < maxCount) {
            arrayToReuse->push_back(buffer);
        } else {
            DEBUG_D("too more %d buffers", capacity);
            delete buffer;
        }
        if (isThreadSafe) {
            pthread_mutex_unlock(&mutex);
        }
    } else {
        delete buffer;
    }
}
