/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef BYTESTREAM_H
#define BYTESTREAM_H

#include <vector>
#include <stdint.h>

class NativeByteBuffer;

class ByteStream {

public:
    ByteStream();
    ~ByteStream();
    void append(NativeByteBuffer *buffer);
    bool hasData();
    void get(NativeByteBuffer *dst);
    void discard(uint32_t count);
    void clean();

private:
    std::vector<NativeByteBuffer *> buffersQueue;
};

#endif
