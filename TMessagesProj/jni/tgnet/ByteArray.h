/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef BYTEARRAY_H
#define BYTEARRAY_H

#include <stdint.h>

class ByteArray {

public:
    ByteArray();
    ByteArray(uint32_t len);
    ByteArray(ByteArray *byteArray);
    ByteArray(uint8_t *buffer, uint32_t len);
    ~ByteArray();
    void alloc(uint32_t len);

    uint32_t length;
    uint8_t *bytes;

    bool isEqualTo(ByteArray *byteArray);

};

#endif
