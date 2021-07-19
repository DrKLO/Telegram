/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef TLOBJECT_H
#define TLOBJECT_H

#include <stdint.h>
#include "Defines.h"

class NativeByteBuffer;

class TLObject {

public:
    virtual ~TLObject();
    virtual void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    virtual void serializeToStream(NativeByteBuffer *stream);
    virtual TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    uint32_t getObjectSize();
    virtual bool isNeedLayer();

    fillParamsFunc initFunc;
};

#endif
