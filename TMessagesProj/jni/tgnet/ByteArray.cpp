/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <stdlib.h>
#include <memory.h>
#include "ByteArray.h"
#include "FileLog.h"

ByteArray::ByteArray() {
    bytes = nullptr;
    length = 0;
}

ByteArray::ByteArray(uint32_t len) {

	//using malloc(), as the ::operator new() throws.
    bytes = (uint8_t*)malloc(len);
    if (bytes == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("unable to allocate byte buffer %u", len);
        exit(1);
    }
    length = len;
}


ByteArray::ByteArray(ByteArray *byteArray) {
    bytes = (uint8_t*)malloc(byteArray->length);
    if (bytes == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("unable to allocate byte buffer %u", byteArray->length);
        exit(1);
    }
    length = byteArray->length;
    memcpy(bytes, byteArray->bytes, length);
}

ByteArray::ByteArray(uint8_t *buffer, uint32_t len) {
	ByteArray temp{buffer,len};
	(ByteArray)(&temp);
}

ByteArray::~ByteArray() {

	//freeing nullptr is a no-op.
	free(bytes);
}

void ByteArray::alloc(uint32_t len) {
    this->~ByteArray();
    (ByteArray)(len);
}

bool ByteArray::isEqualTo(ByteArray *byteArray) {
    return byteArray->length == length && !memcmp(byteArray->bytes, bytes, length);
}
