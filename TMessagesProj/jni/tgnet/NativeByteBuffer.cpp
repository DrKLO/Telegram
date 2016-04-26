/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <memory.h>
#include <stdlib.h>
#include "NativeByteBuffer.h"
#include "FileLog.h"
#include "ByteArray.h"
#include "ConnectionsManager.h"
#include "BuffersStorage.h"

NativeByteBuffer::NativeByteBuffer(uint32_t size) {
#ifdef ANDROID
    if (jclass_ByteBuffer != nullptr) {
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            DEBUG_E("can't get jnienv");
            exit(1);
        }
        javaByteBuffer = env->CallStaticObjectMethod(jclass_ByteBuffer, jclass_ByteBuffer_allocateDirect, size);
        if (javaByteBuffer == nullptr) {
            DEBUG_E("can't create javaByteBuffer");
            exit(1);
        }
        jobject globalRef = env->NewGlobalRef(javaByteBuffer);
        env->DeleteLocalRef(javaByteBuffer);
        javaByteBuffer = globalRef;
        buffer = (uint8_t *) env->GetDirectBufferAddress(javaByteBuffer);
        bufferOwner = false;
    } else {
#endif
        buffer = new uint8_t[size];
        bufferOwner = true;
#ifdef ANDROID
    }
#endif
    if (buffer == nullptr) {
        DEBUG_E("can't allocate NativeByteBuffer buffer");
        exit(1);
    }
    _limit = _capacity = size;
}

NativeByteBuffer::NativeByteBuffer(bool calculate) {
    calculateSizeOnly = calculate;
}

NativeByteBuffer::NativeByteBuffer(uint8_t *buff, uint32_t length) {
    buffer = buff;
    sliced = true;
    _limit = _capacity = length;
}

NativeByteBuffer::~NativeByteBuffer() {
#ifdef ANDROID
    if (javaByteBuffer != nullptr) {
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		    DEBUG_E("can't get jnienv");
            exit(1);
	    }
        env->DeleteGlobalRef(javaByteBuffer);
        javaByteBuffer = nullptr;
    }
#endif
    if (bufferOwner && !sliced && buffer != nullptr) {
        delete[] buffer;
        buffer = nullptr;
    }
}

uint32_t NativeByteBuffer::position() {
    return _position;
}

void NativeByteBuffer::position(uint32_t position) {
    if (position > _limit) {
        return;
    }
    _position = position;
}

uint32_t NativeByteBuffer::capacity() {
    return _capacity;
}

uint32_t NativeByteBuffer::limit() {
    return _limit;
}

uint32_t NativeByteBuffer::remaining() {
    return _limit - _position;
}

void NativeByteBuffer::clearCapacity() {
    if (!calculateSizeOnly) {
        return;
    }
    _capacity = 0;
}

void NativeByteBuffer::limit(uint32_t limit) {
    if (limit > _capacity) {
        return;
    }
    if (_position > limit) {
        _position = limit;
    }
    _limit = limit;
}

void NativeByteBuffer::flip() {
    _limit = _position;
    _position = 0;
}

void NativeByteBuffer::clear() {
    _position = 0;
    _limit = _capacity;
}

uint8_t *NativeByteBuffer::bytes() {
    return buffer;
}

void NativeByteBuffer::rewind() {
    _position = 0;
}

void NativeByteBuffer::compact() {
    if (_position == _limit) {
        return;
    }
    memmove(buffer, buffer + _position, sizeof(uint8_t) * (_limit - _position));
    _position = (_limit - _position);
    _limit = _capacity;
}

bool NativeByteBuffer::hasRemaining() {
    return _position < _limit;
}

void NativeByteBuffer::skip(uint32_t length) {
    if (!calculateSizeOnly) {
        if (_position + length > _limit) {
            return;
        }
        _position += length;
    } else {
        _capacity += length;
    }
}

void NativeByteBuffer::writeInt32(int32_t x, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + 4 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write int32 error");
            return;
        }
        buffer[_position++] = (uint8_t) x;
        buffer[_position++] = (uint8_t) (x >> 8);
        buffer[_position++] = (uint8_t) (x >> 16);
        buffer[_position++] = (uint8_t) (x >> 24);
    } else {
        _capacity += 4;
    }
}

void NativeByteBuffer::writeInt64(int64_t x, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + 8 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write int64 error");
            return;
        }
        buffer[_position++] = (uint8_t) x;
        buffer[_position++] = (uint8_t) (x >> 8);
        buffer[_position++] = (uint8_t) (x >> 16);
        buffer[_position++] = (uint8_t) (x >> 24);
        buffer[_position++] = (uint8_t) (x >> 32);
        buffer[_position++] = (uint8_t) (x >> 40);
        buffer[_position++] = (uint8_t) (x >> 48);
        buffer[_position++] = (uint8_t) (x >> 56);
    } else {
        _capacity += 8;
    }
}

void NativeByteBuffer::writeBool(bool value, bool *error) {
    if (!calculateSizeOnly) {
        if (value) {
            writeInt32(0x997275b5, error);
        } else {
            writeInt32(0xbc799737, error);
        }
    } else {
        _capacity += 4;
    }
}

void NativeByteBuffer::writeBytes(uint8_t *b, uint32_t length, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + length > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write bytes error");
            return;
        }
        writeBytesInternal(b, 0, length);
    } else {
        _capacity += length;
    }
}

void NativeByteBuffer::writeBytes(uint8_t *b, uint32_t offset, uint32_t length, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + length > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write bytes error");
            return;
        }
        writeBytesInternal(b, offset, length);
    } else {
        _capacity += length;
    }
}

void NativeByteBuffer::writeBytes(NativeByteBuffer *b, bool *error) {
    uint32_t length = b->_limit - b->_position;
    if (length == 0) {
        return;
    }
    if (!calculateSizeOnly) {
        if (_position + length > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write bytes error");
            return;
        }
        writeBytesInternal(b->buffer + b->_position, 0, length);
        b->position(b->limit());
    } else {
        _capacity += length;
    }
}

void NativeByteBuffer::writeBytes(ByteArray *b, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + b->length > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write bytes error");
            return;
        }
        writeBytesInternal(b->bytes, 0, b->length);
    } else {
        _capacity += b->length;
    }
}

void NativeByteBuffer::writeBytesInternal(uint8_t *b, uint32_t offset, uint32_t length) {
    memcpy(buffer + _position, b + offset, sizeof(uint8_t) * length);
    _position += length;
}

void NativeByteBuffer::writeByte(uint8_t i, bool *error) {
    if (!calculateSizeOnly) {
        if (_position + 1 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write byte error");
            return;
        }
        buffer[_position++] = i;
    } else {
        _capacity += 1;
    }
}

void NativeByteBuffer::writeString(std::string s, bool *error) {
    writeByteArray((uint8_t *) s.c_str(), (uint32_t) s.length(), error);
}

void NativeByteBuffer::writeByteArray(uint8_t *b, uint32_t offset, uint32_t length, bool *error) {
    if (length <= 253) {
        if (!calculateSizeOnly) {
            if (_position + 1 > _limit) {
                if (error != nullptr) {
                    *error = true;
                }
                DEBUG_E("write byte array error");
                return;
            }
            buffer[_position++] = (uint8_t) length;
        } else {
            _capacity += 1;
        }
    } else {
        if (!calculateSizeOnly) {
            if (_position + 4 > _limit) {
                if (error != nullptr) {
                    *error = true;
                }
                DEBUG_E("write byte array error");
                return;
            }
            buffer[_position++] = (uint8_t) 254;
            buffer[_position++] = (uint8_t) length;
            buffer[_position++] = (uint8_t) (length >> 8);
            buffer[_position++] = (uint8_t) (length >> 16);
        } else {
            _capacity += 4;
        }
    }
    if (!calculateSizeOnly) {
        if (_position + length > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("write byte array error");
            return;
        }
        writeBytesInternal(b, offset, length);
    } else {
        _capacity += length;
    }
    uint32_t addition = (length + (length <= 253 ? 1 : 4)) % 4;
    if (addition != 0) {
        addition = 4 - addition;
    }
    if (!calculateSizeOnly && _position + addition > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("write byte array error");
        return;
    }
    for (uint32_t a = 0; a < addition; a++) {
        if (!calculateSizeOnly) {
            buffer[_position++] = (uint8_t) 0;
        } else {
            _capacity += 1;
        }
    }
}

void NativeByteBuffer::writeByteArray(uint8_t *b, uint32_t length, bool *error) {
    writeByteArray(b, 0, length, error);
}

void NativeByteBuffer::writeByteArray(NativeByteBuffer *b, bool *error) {
    b->rewind();
    writeByteArray(b->buffer, 0, b->limit(), error);
}

void NativeByteBuffer::writeByteArray(ByteArray *b, bool *error) {
    writeByteArray(b->bytes, 0, b->length, error);
}

void NativeByteBuffer::writeDouble(double d, bool *error) {
    int64_t value;
    memcpy(&value, &d, sizeof(int64_t));
    writeInt64(value, error);
}

void NativeByteBuffer::writeInt32(int32_t x) {
    writeInt32(x, nullptr);
}

void NativeByteBuffer::writeInt64(int64_t x) {
    writeInt64(x, nullptr);
}

void NativeByteBuffer::writeBool(bool value) {
    writeBool(value, nullptr);
}

void NativeByteBuffer::writeBytes(uint8_t *b, uint32_t length) {
    writeBytes(b, length, nullptr);
}

void NativeByteBuffer::writeBytes(uint8_t *b, uint32_t offset, uint32_t length) {
    writeBytes(b, offset, length, nullptr);
}

void NativeByteBuffer::writeBytes(ByteArray *b) {
    writeBytes(b, nullptr);
}

void NativeByteBuffer::writeBytes(NativeByteBuffer *b) {
    writeBytes(b, nullptr);
}

void NativeByteBuffer::writeByte(uint8_t i) {
    writeByte(i, nullptr);
}

void NativeByteBuffer::writeString(std::string s) {
    writeString(s, nullptr);
}

void NativeByteBuffer::writeByteArray(uint8_t *b, uint32_t offset, uint32_t length) {
    writeByteArray(b, offset, length, nullptr);
}

void NativeByteBuffer::writeByteArray(uint8_t *b, uint32_t length) {
    writeByteArray(b, length, nullptr);
}

void NativeByteBuffer::writeByteArray(NativeByteBuffer *b) {
    writeByteArray(b, nullptr);
}

void NativeByteBuffer::writeByteArray(ByteArray *b) {
    writeByteArray(b->bytes, b->length, nullptr);
}

void NativeByteBuffer::writeDouble(double d) {
    writeDouble(d, nullptr);
}

int32_t NativeByteBuffer::readInt32(bool *error) {
    if (_position + 4 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read int32 error");
        return 0;
    }
    int32_t result = ((buffer[_position] & 0xff)) |
                     ((buffer[_position + 1] & 0xff) << 8) |
                     ((buffer[_position + 2] & 0xff) << 16) |
                     ((buffer[_position + 3] & 0xff) << 24);
    _position += 4;
    return result;
}

uint32_t NativeByteBuffer::readUint32(bool *error) {
    return (uint32_t) readInt32(error);
}

uint64_t NativeByteBuffer::readUint64(bool *error) {
    return (uint64_t) readInt64(error);
}

int32_t NativeByteBuffer::readBigInt32(bool *error) {
    if (_position + 4 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read big int32 error");
        return 0;
    }
    int32_t result = ((buffer[_position] & 0xff) << 24) |
                     ((buffer[_position + 1] & 0xff) << 16) |
                     ((buffer[_position + 2] & 0xff) << 8) |
                     ((buffer[_position + 3] & 0xff));
    _position += 4;
    return result;
}

int64_t NativeByteBuffer::readInt64(bool *error) {
    if (_position + 4 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read int64 error");
        return 0;
    }
    int64_t result = ((int64_t) (buffer[_position] & 0xff)) |
                     ((int64_t) (buffer[_position + 1] & 0xff) << 8) |
                     ((int64_t) (buffer[_position + 2] & 0xff) << 16) |
                     ((int64_t) (buffer[_position + 3] & 0xff) << 24) |
                     ((int64_t) (buffer[_position + 4] & 0xff) << 32) |
                     ((int64_t) (buffer[_position + 5] & 0xff) << 40) |
                     ((int64_t) (buffer[_position + 6] & 0xff) << 48) |
                     ((int64_t) (buffer[_position + 7] & 0xff) << 56);
    _position += 8;
    return result;
}

uint8_t NativeByteBuffer::readByte(bool *error) {
    if (_position + 1 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read byte error");
        return 0;
    }
    return buffer[_position++];
}

bool NativeByteBuffer::readBool(bool *error) {
    uint32_t consructor = readUint32(error);
    if (consructor == 0x997275b5) {
        return true;
    } else if (consructor == 0xbc799737) {
        return false;
    }
    if (error != nullptr) {
        *error = true;
        DEBUG_E("read bool error");
    }
    return false;
}

void NativeByteBuffer::readBytes(uint8_t *b, uint32_t length, bool *error) {
    if (_position + length > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read bytes error");
        return;
    }
    memcpy(b, buffer + _position, length);
    _position += length;
}

ByteArray *NativeByteBuffer::readBytes(uint32_t length, bool *error) {
    if (_position + length > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read bytes error");
        return nullptr;
    }
    ByteArray *byteArray = new ByteArray(length);
    memcpy(byteArray->bytes, buffer + _position, sizeof(uint8_t) * length);
    _position += length;
    return byteArray;
}

std::string NativeByteBuffer::readString(bool *error) {
    uint32_t sl = 1;
    if (_position + 1 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read string error");
        return std::string("");
    }
    uint32_t l = buffer[_position++];
    if (l >= 254) {
        if (_position + 3 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("read string error");
            return std::string("");
        }
        l = buffer[_position] | (buffer[_position + 1] << 8) | (buffer[_position + 2] << 16);
        _position += 3;
        sl = 4;
    }
    uint32_t addition = (l + sl) % 4;
    if (addition != 0) {
        addition = 4 - addition;
    }
    if (_position + l + addition > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read string error");
        return std::string("");
    }
    std::string result = std::string((const char *) (buffer + _position), l);
    _position += l + addition;
    return result;
}

ByteArray *NativeByteBuffer::readByteArray(bool *error) {
    uint32_t sl = 1;
    if (_position + 1 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read byte array error");
        return nullptr;
    }
    uint32_t l = buffer[_position++];
    if (l >= 254) {
        if (_position + 3 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("read byte array error");
            return nullptr;
        }
        l = buffer[_position] | (buffer[_position + 1] << 8) | (buffer[_position + 2] << 16);
        _position += 3;
        sl = 4;
    }
    uint32_t addition = (l + sl) % 4;
    if (addition != 0) {
        addition = 4 - addition;
    }
    if (_position + l + addition > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read byte array error");
        return nullptr;
    }
    ByteArray *result = new ByteArray(l);
    memcpy(result->bytes, buffer + _position, sizeof(uint8_t) * l);
    _position += l + addition;
    return result;
}

NativeByteBuffer *NativeByteBuffer::readByteBuffer(bool copy, bool *error) {
    uint32_t sl = 1;
    if (_position + 1 > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read byte buffer error");
        return nullptr;
    }
    uint32_t l = buffer[_position++];
    if (l >= 254) {
        if (_position + 3 > _limit) {
            if (error != nullptr) {
                *error = true;
            }
            DEBUG_E("read byte buffer error");
            return nullptr;
        }
        l = buffer[_position] | (buffer[_position + 1] << 8) | (buffer[_position + 2] << 16);
        _position += 3;
        sl = 4;
    }
    uint32_t addition = (l + sl) % 4;
    if (addition != 0) {
        addition = 4 - addition;
    }
    if (_position + l + addition > _limit) {
        if (error != nullptr) {
            *error = true;
        }
        DEBUG_E("read byte buffer error");
        return nullptr;
    }
    NativeByteBuffer *result = nullptr;
    if (copy) {
        result = BuffersStorage::getInstance().getFreeBuffer(l);
        memcpy(result->buffer, buffer + _position, sizeof(uint8_t) * l);
    } else {
        result = new NativeByteBuffer(buffer + _position, l);
    }
    _position += l + addition;
    return result;
}

double NativeByteBuffer::readDouble(bool *error) {
    double value;
    int64_t value2 = readInt64(error);
    memcpy(&value, &value2, sizeof(double));
    return value;
}

void NativeByteBuffer::reuse() {
    if (sliced) {
        return;
    }
    BuffersStorage::getInstance().reuseFreeBuffer(this);
}

#ifdef ANDROID
jobject NativeByteBuffer::getJavaByteBuffer() {
    if (javaByteBuffer == nullptr && javaVm != nullptr) {
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		    DEBUG_E("can't get jnienv");
            exit(1);
	    }
        javaByteBuffer = env->NewDirectByteBuffer(buffer, _capacity);
        if (javaByteBuffer == nullptr) {
            DEBUG_E("can't allocate NativeByteBuffer buffer");
            exit(1);
        }
        jobject globalRef = env->NewGlobalRef(javaByteBuffer);
        env->DeleteLocalRef(javaByteBuffer);
        javaByteBuffer = globalRef;
    }
    return javaByteBuffer;
}
#endif
